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

package steakos.kpi

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.catalogue.CatalogueItem
import steakos.core.Task
import steakos.delivery.ServiceOrder
import steakos.finance.FinanceCollectionsService
import steakos.finance.Invoice
import steakos.finance.Payable
import steakos.finance.Payment
import steakos.finance.Treasury
import steakos.human.HRFile
import steakos.human.TimeTrack
import steakos.production.Lot
import steakos.sales.CustomerQuote
import steakos.sales.SalesOrder
import steakos.service.Inquiry
import steakos.supplychain.StockMove

/**
 * PR-13 live aggregations for ship KPIs — honest insufficient when sample empty.
 */
class KpiFeedServiceSpec extends Specification implements DataTest {

    KpiFeedService service

    Class[] getDomainClassesToMock() {
        [
                CustomerQuote, SalesOrder, ServiceOrder, StockMove,
                Invoice, Payment, Payable, Treasury,
                HRFile, TimeTrack, Inquiry, Lot, CatalogueItem, Task
        ] as Class[]
    }

    def setup() {
        service = new KpiFeedService(
                financeCollectionsService: new FinanceCollectionsService(),
                scmInventoryService      : null
        )
    }

    private static Map defn(String id) {
        KpiCatalogue.get(id)
    }

    private static Date daysAgo(int n) {
        Calendar c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, -n)
        c.time
    }

    def "S01 win rate and insufficient when no decided quotes"() {
        given:
        new CustomerQuote(npubOwner: 'n1', name: 'Q1', quoteStatus: 'ACCEPTED',
                amountEur: 100G, issueDate: daysAgo(5)).save(failOnError: true, flush: true)
        new CustomerQuote(npubOwner: 'n1', name: 'Q2', quoteStatus: 'REJECTED',
                amountEur: 50G, issueDate: daysAgo(3)).save(failOnError: true, flush: true)
        new CustomerQuote(npubOwner: 'n1', name: 'Draft', quoteStatus: 'DRAFT',
                amountEur: 9G, issueDate: daysAgo(1)).save(failOnError: true, flush: true)

        when:
        Map v = service.compute(defn('KPI-S01'), 'n1')
        Map empty = service.compute(defn('KPI-S01'), 'n-empty')

        then:
        v.status == 'ready'
        v.ratio == 0.5G
        v.accepted == 1
        v.rejected == 1
        empty.status == 'insufficient'
        empty.ratio == null
    }

    def "S02 open quote pipeline sums DRAFT and SENT"() {
        given:
        new CustomerQuote(npubOwner: 'n2', name: 'D', quoteStatus: 'DRAFT', amountEur: 10G)
                .save(failOnError: true, flush: true)
        new CustomerQuote(npubOwner: 'n2', name: 'S', quoteStatus: 'SENT', amountEur: 25G)
                .save(failOnError: true, flush: true)
        new CustomerQuote(npubOwner: 'n2', name: 'A', quoteStatus: 'ACCEPTED', amountEur: 99G)
                .save(failOnError: true, flush: true)

        when:
        Map v = service.compute(defn('KPI-S02'), 'n2')

        then:
        v.status == 'ready'
        v.amount == 35G
        v.count == 2
    }

    def "S03 confirmed order intake uses confirmedAt in period"() {
        given:
        new SalesOrder(npubOwner: 'n3', name: 'O1', orderStatus: 'CONFIRMED',
                amountEur: 200G, confirmedAt: daysAgo(2)).save(failOnError: true, flush: true)
        new SalesOrder(npubOwner: 'n3', name: 'Old', orderStatus: 'CONFIRMED',
                amountEur: 500G, confirmedAt: daysAgo(200)).save(failOnError: true, flush: true)
        new SalesOrder(npubOwner: 'n3', name: 'Draft', orderStatus: 'DRAFT',
                amountEur: 10G).save(failOnError: true, flush: true)

        when:
        Map v = service.compute(defn('KPI-S03'), 'n3')

        then:
        v.status == 'ready'
        v.amount == 200G
        v.count == 1
    }

    def "D01 on-time and D02 overdue open jobs"() {
        given:
        Date sched = daysAgo(2)
        new ServiceOrder(npubOwner: 'nd', name: 'Late', fulfillStatus: 'COMPLETED',
                scheduledAt: sched, completedAt: daysAgo(0)).save(failOnError: true, flush: true)
        new ServiceOrder(npubOwner: 'nd', name: 'OnTime', fulfillStatus: 'COMPLETED',
                scheduledAt: sched, completedAt: daysAgo(3)).save(failOnError: true, flush: true)
        new ServiceOrder(npubOwner: 'nd', name: 'OpenLate', fulfillStatus: 'IN_PROGRESS',
                scheduledAt: daysAgo(5)).save(failOnError: true, flush: true)

        when:
        Map d01 = service.compute(defn('KPI-D01'), 'nd')
        Map d02 = service.compute(defn('KPI-D02'), 'nd')

        then:
        d01.status == 'ready'
        d01.onTime == 1
        d01.count == 2
        d01.ratio == 0.5G
        d02.status == 'ready'
        d02.count == 1
    }

    def "I01 stockout risk needs ISSUE and onHand <= 0"() {
        given:
        // net onHand 0 with recent ISSUE
        new StockMove(npubOwner: 'ni', name: 'in', catalogueItemId: 7L, qtyDelta: 5G,
                reason: 'RECEIPT', movedAt: daysAgo(40)).save(failOnError: true, flush: true)
        new StockMove(npubOwner: 'ni', name: 'out', catalogueItemId: 7L, qtyDelta: -5G,
                reason: 'ISSUE', movedAt: daysAgo(2)).save(failOnError: true, flush: true)
        // positive onHand — not at risk
        new StockMove(npubOwner: 'ni', name: 'ok', catalogueItemId: 8L, qtyDelta: 3G,
                reason: 'RECEIPT', movedAt: daysAgo(1)).save(failOnError: true, flush: true)
        new StockMove(npubOwner: 'ni', name: 'ok-out', catalogueItemId: 8L, qtyDelta: -1G,
                reason: 'ISSUE', movedAt: daysAgo(1)).save(failOnError: true, flush: true)

        when:
        Map v = service.compute(defn('KPI-I01'), 'ni')

        then:
        v.status == 'ready'
        v.count == 1
        v.items[0].catalogueItemId == 7L
    }

    def "F01 AR aging buckets and F02 cash dual rail"() {
        given:
        Date asOf = new Date()
        new Invoice(npubOwner: 'nf', name: 'Cur', invoiceNumber: 'C1', invoiceStatus: 'ISSUED',
                amountEur: 40G, dueDate: daysAgo(-5)).save(failOnError: true, flush: true)
        new Invoice(npubOwner: 'nf', name: 'Old', invoiceNumber: 'O1', invoiceStatus: 'ISSUED',
                amountEur: 100G, dueDate: daysAgo(45)).save(failOnError: true, flush: true)
        new Treasury(npubOwner: 'nf', name: 'Bank', accountKind: 'FIAT_BANK',
                balanceEur: 500G, active: true).save(failOnError: true, flush: true)
        new Treasury(npubOwner: 'nf', name: 'BTC', accountKind: 'BTC_WALLET',
                balanceEur: 0G, balanceSats: 12_000L, active: true).save(failOnError: true, flush: true)

        when:
        Map f01 = service.compute(defn('KPI-F01'), 'nf')
        Map f02 = service.compute(defn('KPI-F02'), 'nf')

        then:
        f01.status == 'ready'
        f01.buckets['CURRENT'] == 40G
        f01.buckets['31-60'] == 100G
        f01.overdueAmount == 100G
        f02.status == 'ready'
        f02.fiatEur == 500G
        f02.btcSats == 12_000L
    }

    def "F03 payables due windows and F04 settled by rail"() {
        given:
        new Payable(npubOwner: 'np', name: 'Soon', payableStatus: 'OPEN',
                amountEur: 30G, dueDate: daysAgo(-3)).save(failOnError: true, flush: true)
        Invoice inv = new Invoice(npubOwner: 'np', name: 'Inv', invoiceNumber: 'P1',
                invoiceStatus: 'PAID', amountEur: 80G).save(failOnError: true, flush: true)
        new Payment(npubOwner: 'np', name: 'sepa', invoice: inv, rail: 'SEPA',
                amountEur: 50G, paymentStatus: 'SETTLED', receivedAt: daysAgo(1))
                .save(failOnError: true, flush: true)
        new Payment(npubOwner: 'np', name: 'btc', invoice: inv, rail: 'BTC_ONCHAIN',
                amountEur: 30G, paymentStatus: 'SETTLED', receivedAt: daysAgo(2))
                .save(failOnError: true, flush: true)

        when:
        Map f03 = service.compute(defn('KPI-F03'), 'np')
        Map f04 = service.compute(defn('KPI-F04'), 'np')

        then:
        f03.status == 'ready'
        f03.due7.count == 1
        f03.due7.amount == 30G
        f04.status == 'ready'
        f04.breakdown['SEPA'] == 50G
        f04.breakdown['BTC_ONCHAIN'] == 30G
        f04.amount == 80G
    }

    def "H01 headcount and H02 hours logged"() {
        given:
        HRFile active = new HRFile(npubOwner: 'nh', name: 'Ada', userId: 101L,
                employmentStatus: 'ACTIVE').save(failOnError: true, flush: true)
        new HRFile(npubOwner: 'nh', name: 'Gone', userId: 102L,
                employmentStatus: 'EXITED').save(failOnError: true, flush: true)
        new TimeTrack(npubOwner: 'nh', name: 'day', hrFile: active, hours: 8G,
                timeKind: 'WORK', timeStatus: 'POSTED', startedAt: daysAgo(1))
                .save(failOnError: true, flush: true)
        new TimeTrack(npubOwner: 'nh', name: 'leave', hrFile: active, hours: 8G,
                timeKind: 'LEAVE', timeStatus: 'POSTED', startedAt: daysAgo(1))
                .save(failOnError: true, flush: true)

        when:
        Map h01 = service.compute(defn('KPI-H01'), 'nh')
        Map h02 = service.compute(defn('KPI-H02'), 'nh')

        then:
        h01.status == 'ready'
        h01.count == 1
        h02.status == 'ready'
        h02.amount == 8G
        h02.count == 1
    }

    def "Q01 open cases Q02 past due Q04 reject rate"() {
        given:
        new Inquiry(npubOwner: 'nq', name: 'Open', caseStatus: 'OPEN').save(failOnError: true, flush: true)
        new Inquiry(npubOwner: 'nq', name: 'Late', caseStatus: 'IN_PROGRESS',
                resolveDueAt: daysAgo(2)).save(failOnError: true, flush: true)
        CatalogueItem goods = new CatalogueItem(npubOwner: 'nq', name: 'Widget',
                nature: CatalogueItem.NATURE_GOODS).save(failOnError: true, flush: true)
        new Lot(npubOwner: 'nq', name: 'L1', lotCode: 'L1', catalogueItem: goods,
                lotStatus: 'RELEASED', manufacturedOn: daysAgo(1), quantity: 1G)
                .save(failOnError: true, flush: true)
        new Lot(npubOwner: 'nq', name: 'L2', lotCode: 'L2', catalogueItem: goods,
                lotStatus: 'REJECTED', manufacturedOn: daysAgo(1), quantity: 1G)
                .save(failOnError: true, flush: true)

        when:
        Map q01 = service.compute(defn('KPI-Q01'), 'nq')
        Map q02 = service.compute(defn('KPI-Q02'), 'nq')
        Map q04 = service.compute(defn('KPI-Q04'), 'nq')
        Map q04empty = service.compute(defn('KPI-Q04'), 'nq-empty')

        then:
        q01.count == 2
        q02.count == 1
        q04.status == 'ready'
        q04.ratio == 0.5G
        q04empty.status == 'insufficient'
    }

    def "W01 overdue tasks ignore DONE"() {
        given:
        new Task(npubOwner: 'nw', name: 'Late', status: 'OPEN',
                deadlineAt: daysAgo(1)).save(failOnError: true, flush: true)
        new Task(npubOwner: 'nw', name: 'Done late', status: 'DONE',
                deadlineAt: daysAgo(1)).save(failOnError: true, flush: true)

        when:
        Map v = service.compute(defn('KPI-W01'), 'nw')

        then:
        v.status == 'ready'
        v.count == 1
    }

    def "DEFER and silver-new stretch stay non-ready"() {
        expect:
        service.compute(defn('KPI-S04'), 'n').status == 'deferred'
        service.compute(defn('KPI-F05'), 'n').status == 'deferred'
        service.compute(defn('KPI-I02'), 'n').status == 'planned'
        service.compute(defn('KPI-F06'), 'n').status == 'planned'
    }
}
