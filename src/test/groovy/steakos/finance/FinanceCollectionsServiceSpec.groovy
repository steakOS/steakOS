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
import steakos.sales.Customer

/**
 * FinanceAdvanced PR-5a — overdue queue + promise/activity over Invoice AR.
 */
class FinanceCollectionsServiceSpec extends Specification implements DataTest {

    FinanceCollectionsService service
    DomainEventBusService bus
    List<DomainEvent> published

    Class[] getDomainClassesToMock() {
        [Invoice, Payment, CollectionActivity, Customer] as Class[]
    }

    def setup() {
        published = []
        bus = new DomainEventBusService()
        bus.subscribe(DomainEventNames.FINANCE_INVOICE_OVERDUE) { DomainEvent e ->
            published << e
        }
        service = new FinanceCollectionsService(domainEventBusService: bus)
    }

    private Invoice issued(String npub, Map fields) {
        Date due = fields.dueDate as Date
        Invoice inv = new Invoice(
                npubOwner    : npub,
                name         : fields.name ?: 'Inv',
                invoiceNumber: fields.invoiceNumber ?: "INV-${System.nanoTime()}",
                customerName : fields.customerName ?: 'Acme',
                customerId   : fields.customerId as Long,
                issueDate    : fields.issueDate as Date ?: new Date(),
                dueDate      : due,
                amountEur    : fields.amountEur as BigDecimal ?: 100G,
                currencyCode : fields.currencyCode ?: 'EUR',
                invoiceStatus: fields.invoiceStatus ?: 'ISSUED',
                status       : fields.invoiceStatus ?: 'ISSUED'
        )
        inv.save(failOnError: true, flush: true)
        inv
    }

    private static Date daysAgo(int n, Date asOf = new Date()) {
        Calendar c = Calendar.getInstance()
        c.time = FinanceCollectionsService.startOfDay(asOf)
        c.add(Calendar.DAY_OF_MONTH, -n)
        c.time
    }

    private static Date daysFrom(int n, Date asOf = new Date()) {
        Calendar c = Calendar.getInstance()
        c.time = FinanceCollectionsService.startOfDay(asOf)
        c.add(Calendar.DAY_OF_MONTH, n)
        c.time
    }

    def "overdue queue excludes current and paid; sorts by daysOverdue desc"() {
        given:
        Date asOf = FinanceCollectionsService.startOfDay(new Date())
        Invoice old = issued('npub1c', [dueDate: daysAgo(45, asOf), amountEur: 200G, invoiceNumber: 'OLD'])
        Invoice mid = issued('npub1c', [dueDate: daysAgo(10, asOf), amountEur: 50G, invoiceNumber: 'MID'])
        issued('npub1c', [dueDate: daysFrom(5, asOf), amountEur: 80G, invoiceNumber: 'FUTURE'])
        issued('npub1c', [dueDate: daysAgo(20, asOf), amountEur: 90G, invoiceStatus: 'PAID', invoiceNumber: 'PAID'])
        issued('npub1other', [dueDate: daysAgo(99, asOf), amountEur: 999G, invoiceNumber: 'OTHER'])

        when:
        Map q = service.listOverdueQueue('npub1c', asOf)

        then:
        q.total == 2
        q.items[0].invoiceNumber == 'OLD'
        q.items[0].daysOverdue == 45
        q.items[0].agingBucket == '31-60'
        q.items[0].amountOpenEur == 200G
        q.items[1].invoiceNumber == 'MID'
        q.items[1].daysOverdue == 10
        q.items[1].agingBucket == '1-30'
        !q.items.any { it.invoiceNumber == 'OTHER' }
        old.id
        mid.id
    }

    def "open queue buckets and PART_PAID amountOpenEur"() {
        given:
        Date asOf = FinanceCollectionsService.startOfDay(new Date())
        Invoice part = issued('npub1p', [
                dueDate       : daysAgo(5, asOf),
                amountEur     : 100G,
                invoiceStatus : 'PART_PAID',
                invoiceNumber : 'PART'
        ])
        new Payment(
                npubOwner    : 'npub1p',
                name         : 'part-pay',
                invoice      : part,
                rail         : 'SEPA',
                amountEur    : 40G,
                paymentStatus: 'SETTLED',
                receivedAt   : new Date()
        ).save(failOnError: true, flush: true)
        issued('npub1p', [dueDate: daysFrom(3, asOf), amountEur: 25G, invoiceNumber: 'CUR'])

        when:
        Map q = service.listOpenQueue('npub1p', asOf)

        then:
        q.total == 2
        q.buckets['CURRENT'] == 1
        q.buckets['1-30'] == 1
        q.amountOpenTotal == 85G
        Map partRow = q.items.find { it.invoiceNumber == 'PART' }
        partRow.paidEur == 40G
        partRow.amountOpenEur == 60G
    }

    def "record promise and list activities"() {
        given:
        Date asOf = FinanceCollectionsService.startOfDay(new Date())
        Invoice inv = issued('npub1a', [dueDate: daysAgo(12, asOf), amountEur: 150G])

        when:
        CollectionActivity promise = service.recordPromise('npub1a', inv.id, [
                promisedPayBy     : daysFrom(7, asOf),
                promisedAmountEur : 150G,
                channel           : 'PHONE',
                description       : 'Will pay Friday'
        ])
        CollectionActivity note = service.recordActivity('npub1a', inv.id, [
                kind   : 'CONTACT',
                channel: 'EMAIL',
                note   : 'Sent statement'
        ])
        Map listed = service.listActivities('npub1a', inv.id)
        Map item = service.showQueueItem(inv.id, 'npub1a', asOf)

        then:
        promise.kind == 'PROMISE'
        promise.promiseStatus == 'OPEN'
        promise.promisedAmountEur == 150G
        note.kind == 'CONTACT'
        listed.total == 2
        item.activityCount == 2
        item.latestPromise.id == promise.id
        item.daysOverdue == 12
    }

    def "promise requires promisedPayBy; draft rejected"() {
        given:
        Invoice draft = issued('npub1d', [dueDate: daysAgo(1), invoiceStatus: 'DRAFT'])
        Invoice open = issued('npub1d', [dueDate: daysAgo(1), amountEur: 10G])

        when:
        service.recordPromise('npub1d', open.id, [promisedAmountEur: 10G])

        then:
        thrown(IllegalArgumentException)

        when:
        service.recordActivity('npub1d', draft.id, [kind: 'NOTE', description: 'x'])

        then:
        thrown(IllegalStateException)
    }

    def "REMINDER publishes finance.invoice.overdue"() {
        given:
        Invoice inv = issued('npub1e', [dueDate: daysAgo(3), amountEur: 70G])

        when:
        service.recordActivity('npub1e', inv.id, [kind: 'REMINDER', channel: 'EMAIL'])

        then:
        published.size() == 1
        published[0].name == DomainEventNames.FINANCE_INVOICE_OVERDUE
        published[0].payload.invoiceId == inv.id
    }

    def "REMINDER overdue payload includes customerId and partner email"() {
        given:
        Customer c = new Customer(
                npubOwner: 'npub1e',
                name: 'Acme Billing',
                email: 'ar@acme.example',
                currencyCode: 'EUR'
        ).save(failOnError: true, flush: true)
        Invoice inv = issued('npub1e', [
                dueDate      : daysAgo(3),
                amountEur    : 70G,
                customerId   : c.id,
                customerName : c.name
        ])

        when:
        service.recordActivity('npub1e', inv.id, [kind: 'REMINDER', channel: 'EMAIL'])

        then:
        published.size() == 1
        published[0].payload.customerId == c.id
        published[0].payload.customerName == 'Acme Billing'
        published[0].payload.email == 'ar@acme.example'
        published[0].payload.to == 'ar@acme.example'
    }

    def "updatePromiseStatus KEPT"() {
        given:
        Invoice inv = issued('npub1f', [dueDate: daysAgo(2), amountEur: 30G])
        CollectionActivity p = service.recordPromise('npub1f', inv.id, [
                promisedPayBy: daysFrom(2)
        ])

        when:
        CollectionActivity updated = service.updatePromiseStatus('npub1f', p.id, 'KEPT')

        then:
        updated.promiseStatus == 'KEPT'
        updated.status == 'KEPT'
    }

    def "agingBucket boundaries"() {
        expect:
        FinanceCollectionsService.agingBucket(0) == 'CURRENT'
        FinanceCollectionsService.agingBucket(-1) == 'CURRENT'
        FinanceCollectionsService.agingBucket(1) == '1-30'
        FinanceCollectionsService.agingBucket(30) == '1-30'
        FinanceCollectionsService.agingBucket(31) == '31-60'
        FinanceCollectionsService.agingBucket(60) == '31-60'
        FinanceCollectionsService.agingBucket(61) == '61+'
    }

    def "company scope: activity on foreign invoice fails"() {
        given:
        Invoice inv = issued('npubA', [dueDate: daysAgo(5), amountEur: 10G])

        when:
        service.recordActivity('npubB', inv.id, [kind: 'NOTE'])

        then:
        thrown(IllegalArgumentException)
    }
}
