// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package steakos.finance

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.events.DomainEvent
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames
import steakos.procurement.PurchaseOrder
import steakos.procurement.PurchaseOrderLine
import steakos.procurement.Receipt
import steakos.procurement.ReceiptLine

import java.math.RoundingMode

/**
 * FinanceAdvanced PR-5b — light three-way match PO ↔ receipt ↔ Payable.
 */
class FinanceMatchServiceSpec extends Specification implements DataTest {

    FinanceMatchService service
    DomainEventBusService bus
    List<DomainEvent> published

    Class[] getDomainClassesToMock() {
        [PurchaseOrder, PurchaseOrderLine, Receipt, ReceiptLine, Payable, ThreeWayMatch] as Class[]
    }

    def setup() {
        published = []
        bus = new DomainEventBusService()
        bus.subscribe(DomainEventNames.FINANCE_MATCH_COMPLETED) { DomainEvent e ->
            published << e
        }
        service = new FinanceMatchService(domainEventBusService: bus)
    }

    private Map fixture(String npub, Map opts = [:]) {
        BigDecimal poAmt = (opts.amountPoEur as BigDecimal) ?: 100G
        BigDecimal payAmt = (opts.amountPayableEur as BigDecimal) ?: poAmt
        BigDecimal qty = (opts.quantity as BigDecimal) ?: 2G
        BigDecimal qtyRec = (opts.quantityReceived as BigDecimal) ?: qty
        String poStatus = (opts.poStatus as String) ?: 'RECEIVED'

        Payable pay = new Payable(
                npubOwner    : npub,
                name         : 'AP Vendor Inv',
                vendorName   : 'Vendor Co',
                amountEur    : payAmt,
                currencyCode : 'EUR',
                issueDate    : new Date(),
                payableStatus: 'OPEN',
                status       : 'OPEN'
        )
        pay.save(failOnError: true, flush: true)

        PurchaseOrder po = new PurchaseOrder(
                npubOwner       : npub,
                name            : 'PO-1',
                poNumber        : opts.poNumber ?: "PO-${System.nanoTime()}",
                vendorName      : 'Vendor Co',
                amountEur       : poAmt,
                currencyCode    : 'EUR',
                poStatus        : poStatus,
                status          : poStatus,
                financePayableId: pay.id,
                orderDate       : new Date()
        )
        po.save(failOnError: true, flush: true)

        PurchaseOrderLine pol = new PurchaseOrderLine(
                npubOwner        : npub,
                purchaseOrder    : po,
                lineNo           : 1,
                name             : 'Widget',
                quantity         : qty,
                unitPriceEur     : poAmt.divide(qty, 8, RoundingMode.HALF_UP),
                lineTotalEur     : poAmt,
                quantityReceived : qtyRec
        )
        pol.save(failOnError: true, flush: true)

        Receipt receipt = null
        if (opts.withReceipt != false) {
            receipt = new Receipt(
                    npubOwner     : npub,
                    name          : 'RCV-1',
                    receiptNumber : "RCV-${System.nanoTime()}",
                    purchaseOrder : po,
                    receivedDate  : new Date(),
                    receiptStatus : 'POSTED',
                    status        : 'POSTED'
            )
            receipt.save(failOnError: true, flush: true)
            new ReceiptLine(
                    npubOwner        : npub,
                    receipt          : receipt,
                    lineNo           : 1,
                    name             : 'Widget',
                    purchaseOrderLine: pol,
                    quantityReceived : qtyRec
            ).save(failOnError: true, flush: true)
        }

        [po: po, pay: pay, receipt: receipt, line: pol]
    }

    def "candidates lists matchable PO with payable and receipt"() {
        given:
        Map f = fixture('npub-m1')
        fixture('npub-other') // other tenant

        when:
        Map c = service.listCandidates('npub-m1')

        then:
        c.total == 1
        c.items[0].purchaseOrderId == f.po.id
        c.items[0].payableId == f.pay.id
        c.items[0].receipts.size() == 1
        c.gaps
    }

    def "propose and confirm clean match publishes finance.match.completed"() {
        given:
        Map f = fixture('npub-m2')

        when:
        ThreeWayMatch proposed = service.propose('npub-m2', [purchaseOrderId: f.po.id])
        Map listed = service.listMatches('npub-m2', 'PROPOSED')

        then:
        proposed.matchStatus == 'PROPOSED'
        proposed.amountOk
        proposed.qtyOk
        proposed.varianceEur == 0G
        listed.total == 1
        listed.items[0].id == proposed.id

        when:
        ThreeWayMatch confirmed = service.confirm('npub-m2', proposed.id)

        then:
        confirmed.matchStatus == 'CONFIRMED'
        confirmed.confirmedAt
        published.size() == 1
        published[0].name == DomainEventNames.FINANCE_MATCH_COMPLETED
        published[0].payload.matchId == confirmed.id
        published[0].payload.payableId == f.pay.id
        service.listCandidates('npub-m2').total == 0
    }

    def "amount variance blocks confirm unless force"() {
        given:
        Map f = fixture('npub-m3', [amountPoEur: 100G, amountPayableEur: 120G])
        ThreeWayMatch m = service.propose('npub-m3', [
                purchaseOrderId: f.po.id,
                receiptId      : f.receipt.id,
                payableId      : f.pay.id
        ])

        expect:
        !m.amountOk
        m.varianceEur == 20G

        when:
        service.confirm('npub-m3', m.id)

        then:
        thrown(IllegalStateException)

        when:
        ThreeWayMatch forced = service.confirm('npub-m3', m.id, [force: true])

        then:
        forced.matchStatus == 'CONFIRMED'
        published.size() == 1
        published[0].payload.forced == true
    }

    def "propose requires receipt; reject then cancel paths"() {
        given:
        Map noRcv = fixture('npub-m4', [withReceipt: false])
        Map ok = fixture('npub-m4b')

        when:
        service.propose('npub-m4', [purchaseOrderId: noRcv.po.id])

        then:
        thrown(IllegalStateException)

        when:
        ThreeWayMatch m = service.propose('npub-m4b', [purchaseOrderId: ok.po.id])
        service.reject('npub-m4b', m.id, [description: 'wrong invoice'])
        ThreeWayMatch rejected = ThreeWayMatch.get(m.id)

        then:
        rejected.matchStatus == 'REJECTED'

        when:
        ThreeWayMatch m2 = service.propose('npub-m4b', [
                purchaseOrderId: ok.po.id,
                receiptId      : ok.receipt.id
        ])
        service.cancel('npub-m4b', m2.id)

        then:
        ThreeWayMatch.get(m2.id).matchStatus == 'CANCELLED'
    }

    def "company scope: foreign npub cannot propose"() {
        given:
        Map f = fixture('npubA')

        when:
        service.propose('npubB', [purchaseOrderId: f.po.id])

        then:
        thrown(IllegalArgumentException)
    }

    def "amountWithinTolerance and Advanced route"() {
        expect:
        FinanceMatchService.amountWithinTolerance(0G)
        FinanceMatchService.amountWithinTolerance(new BigDecimal('0.01'))
        !FinanceMatchService.amountWithinTolerance(new BigDecimal('0.02'))
        steakos.configuration.AdvancedRouteCatalog.isAdvancedPath('/api/finance/match')
        steakos.configuration.AdvancedRouteCatalog.isAdvancedPath('/api/finance/match/candidates')
        steakos.configuration.AdvancedRouteCatalog.isAdvancedPath('/finance/match')
    }

    def "unmatch rolls back CONFIRMED match; cancel rejects CONFIRMED"() {
        given:
        Map f = fixture('npub-um')
        ThreeWayMatch m = service.propose('npub-um', [purchaseOrderId: f.po.id])
        service.confirm('npub-um', m.id)

        when:
        service.cancel('npub-um', m.id)

        then:
        thrown(IllegalStateException)

        when:
        ThreeWayMatch rolled = service.unmatch('npub-um', m.id, [description: 'test rollback'])

        then:
        rolled.matchStatus == 'CANCELLED'
        ThreeWayMatch.get(m.id).matchStatus == 'CANCELLED'
        ThreeWayMatch.get(m.id).description.contains('Unmatched')
    }
}
