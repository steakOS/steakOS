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

package steakos.document

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.finance.Invoice
import steakos.finance.Payable
import steakos.finance.PayrollItem
import steakos.finance.ThreeWayMatch
import steakos.procurement.PurchaseOrder
import steakos.procurement.Receipt
import steakos.sales.CustomerQuote
import steakos.sales.SalesOrder

/**
 * ADR-049 — DocumentChainLockService lock reasons across P0–P4 families.
 */
class DocumentChainLockServiceSpec extends Specification implements DataTest {

    DocumentChainLockService service

    Class[] getDomainClassesToMock() {
        [
                CustomerQuote, SalesOrder, Invoice,
                PurchaseOrder, Receipt, Payable, ThreeWayMatch,
                PayrollItem
        ] as Class[]
    }

    def setup() {
        service = new DocumentChainLockService()
    }

    def "order unlocked when no live issued invoice"() {
        given:
        SalesOrder o = new SalesOrder(
                npubOwner: 'npub-a', name: 'SO', orderNumber: 'SO-1',
                amountEur: 10G, currencyCode: 'EUR',
                orderStatus: 'CONFIRMED', status: 'CONFIRMED'
        ).save(failOnError: true, flush: true)

        expect:
        service.lockReasons('SalesOrder', o.id, 'npub-a').empty
        !service.lockReasons('SalesOrder', o.id, 'npub-other')
    }

    def "order locked by ISSUED invoice; unlocked after VOID"() {
        given:
        Invoice inv = new Invoice(
                npubOwner: 'npub-b', name: 'INV', invoiceNumber: 'INV-1',
                amountEur: 100G, currencyCode: 'EUR',
                invoiceStatus: 'ISSUED', status: 'ISSUED'
        ).save(failOnError: true, flush: true)
        SalesOrder o = new SalesOrder(
                npubOwner: 'npub-b', name: 'SO', orderNumber: 'SO-2',
                amountEur: 100G, currencyCode: 'EUR',
                orderStatus: 'CONFIRMED', status: 'CONFIRMED',
                financeInvoiceId: inv.id
        ).save(failOnError: true, flush: true)

        when:
        List locked = service.lockReasons('SalesOrder', o.id, 'npub-b')

        then:
        locked.size() >= 1
        locked.any { it.downstreamType == 'Invoice' && it.status == 'ISSUED' }

        when:
        service.assertUnlocked('SalesOrder', o.id, 'npub-b')

        then:
        def ex = thrown(DocumentChainLockedException)
        ex.code == DocumentChainLockedException.CODE
        ex.reasons

        when:
        inv.invoiceStatus = 'VOID'
        inv.status = 'VOID'
        inv.save(failOnError: true, flush: true)

        then:
        service.lockReasons('SalesOrder', o.id, 'npub-b').empty
    }

    def "quote locked when resulting order is CONFIRMED"() {
        given:
        SalesOrder o = new SalesOrder(
                npubOwner: 'npub-q', name: 'SO', orderNumber: 'SO-Q',
                amountEur: 50G, currencyCode: 'EUR',
                orderStatus: 'CONFIRMED', status: 'CONFIRMED'
        ).save(failOnError: true, flush: true)
        CustomerQuote q = new CustomerQuote(
                npubOwner: 'npub-q', name: 'Q', quoteNumber: 'Q-1',
                amountEur: 50G, currencyCode: 'EUR',
                quoteStatus: 'ACCEPTED', status: 'ACCEPTED',
                resultingOrderId: o.id
        ).save(failOnError: true, flush: true)

        expect:
        service.lockReasons('CustomerQuote', q.id, 'npub-q').any {
            it.downstreamType == 'SalesOrder'
        }
    }

    def "PO locked by live payable and by CONFIRMED three-way match"() {
        given:
        Payable pay = new Payable(
                npubOwner: 'npub-p', name: 'AP', vendorName: 'V',
                amountEur: 80G, currencyCode: 'EUR',
                payableStatus: 'OPEN', status: 'OPEN', issueDate: new Date()
        ).save(failOnError: true, flush: true)
        PurchaseOrder po = new PurchaseOrder(
                npubOwner: 'npub-p', name: 'PO', poNumber: 'PO-1',
                vendorName: 'V', amountEur: 80G, currencyCode: 'EUR',
                poStatus: 'CONFIRMED', status: 'CONFIRMED',
                financePayableId: pay.id, orderDate: new Date()
        ).save(failOnError: true, flush: true)

        expect:
        service.lockReasons('PurchaseOrder', po.id, 'npub-p').any {
            it.downstreamType == 'Payable'
        }

        when:
        Receipt rcv = new Receipt(
                npubOwner: 'npub-p', name: 'RCV', receiptNumber: 'RCV-1',
                purchaseOrder: po, receivedDate: new Date(),
                receiptStatus: 'POSTED', status: 'POSTED'
        ).save(failOnError: true, flush: true)
        ThreeWayMatch m = new ThreeWayMatch(
                npubOwner: 'npub-p', name: 'M1',
                purchaseOrderId: po.id, receiptId: rcv.id, payableId: pay.id,
                matchStatus: 'CONFIRMED', status: 'CONFIRMED',
                amountPoEur: 80G, amountPayableEur: 80G, varianceEur: 0G,
                amountOk: true, qtyOk: true, proposedAt: new Date()
        ).save(failOnError: true, flush: true)
        List reasons = service.lockReasons('PurchaseOrder', po.id, 'npub-p')

        then:
        reasons.any { it.downstreamType == 'ThreeWayMatch' && it.downstreamId == m.id }
    }

    def "payroll item locked when POSTED or PAID"() {
        given:
        PayrollItem draft = new PayrollItem(
                npubOwner: 'npub-hr', name: 'Wage draft', period: '2026-10',
                amountEur: 1000G, currencyCode: 'EUR',
                payrollStatus: 'DRAFT', status: 'DRAFT'
        ).save(failOnError: true, flush: true)
        PayrollItem posted = new PayrollItem(
                npubOwner: 'npub-hr', name: 'Wage posted', period: '2026-10',
                amountEur: 1000G, currencyCode: 'EUR',
                payrollStatus: 'POSTED', status: 'POSTED', journalEntryId: 99L
        ).save(failOnError: true, flush: true)

        expect:
        service.lockReasons('PayrollItem', draft.id, 'npub-hr').empty
        service.lockReasons('PayrollItem', posted.id, 'npub-hr').size() == 1

        when:
        service.assertDomainUnlocked(PayrollItem, posted.id, 'npub-hr')

        then:
        thrown(DocumentChainLockedException)
    }
}
