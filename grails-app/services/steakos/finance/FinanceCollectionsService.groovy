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

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames

/**
 * FinanceAdvanced collections queue over existing Invoice / AR (APQC 9.2.4).
 * Company-scoped by npubOwner. Does not claim three-way match (PR-5b) or multi-entity.
 */
@Slf4j
class FinanceCollectionsService {

    static final List OPEN_STATUSES = ['ISSUED', 'PART_PAID'].asImmutable()
    static final List ACTIVITY_KINDS = ['PROMISE', 'CONTACT', 'NOTE', 'REMINDER', 'CREDIT_HOLD'].asImmutable()
    static final List CHANNELS = ['PHONE', 'EMAIL', 'IN_PERSON', 'OTHER', 'SYSTEM'].asImmutable()

    DomainEventBusService domainEventBusService

    // -------------------------------------------------------------------------
    // Overdue / open queue
    // -------------------------------------------------------------------------

    /**
     * Overdue AR queue: open invoices with dueDate before start of {@code asOf} day.
     * Sorted by daysOverdue desc, then dueDate asc.
     */
    @Transactional(readOnly = true)
    Map listOverdueQueue(String npubOwner, Date asOf = new Date()) {
        String npub = requireNpub(npubOwner)
        Date dayStart = startOfDay(asOf)
        List<Invoice> open = Invoice.findAllByNpubOwnerAndInvoiceStatusInList(
                npub, OPEN_STATUSES, [sort: 'dueDate', order: 'asc'])
        List<Map> items = []
        open.each { Invoice inv ->
            if (!inv.dueDate || !inv.dueDate.before(dayStart)) {
                return
            }
            items << queueItem(inv, dayStart)
        }
        items.sort { Map a, Map b ->
            int byDays = ((b.daysOverdue as Integer) <=> (a.daysOverdue as Integer))
            byDays != 0 ? byDays : ((a.dueDate as Date) <=> (b.dueDate as Date))
        }
        [
                npubOwner: npub,
                asOf     : dayStart,
                total    : items.size(),
                items    : items
        ]
    }

    /**
     * All open AR with aging buckets (includes current / not-yet-due).
     */
    @Transactional(readOnly = true)
    Map listOpenQueue(String npubOwner, Date asOf = new Date()) {
        String npub = requireNpub(npubOwner)
        Date dayStart = startOfDay(asOf)
        List<Invoice> open = Invoice.findAllByNpubOwnerAndInvoiceStatusInList(
                npub, OPEN_STATUSES, [sort: 'dueDate', order: 'asc'])
        List<Map> items = open.collect { Invoice inv -> queueItem(inv, dayStart) }
        Map buckets = [
                'CURRENT': 0,
                '1-30'   : 0,
                '31-60'  : 0,
                '61+'    : 0
        ]
        BigDecimal openTotal = BigDecimal.ZERO
        items.each { Map row ->
            String b = row.agingBucket as String
            if (buckets.containsKey(b)) {
                buckets[b] = (buckets[b] as Integer) + 1
            }
            openTotal = openTotal.add(row.amountOpenEur as BigDecimal ?: BigDecimal.ZERO)
        }
        [
                npubOwner      : npub,
                asOf           : dayStart,
                total          : items.size(),
                amountOpenTotal: openTotal,
                buckets        : buckets,
                items          : items
        ]
    }

    @Transactional(readOnly = true)
    Map showQueueItem(Long invoiceId, String npubOwner, Date asOf = new Date()) {
        Invoice inv = loadOpenOrAnyInvoice(invoiceId, npubOwner)
        Date dayStart = startOfDay(asOf)
        Map item = queueItem(inv, dayStart)
        item.activities = listActivities(npubOwner, invoiceId).items
        item
    }

    // -------------------------------------------------------------------------
    // Promise / activity
    // -------------------------------------------------------------------------

    @Transactional
    CollectionActivity recordActivity(String npubOwner, Long invoiceId, Map fields) {
        String npub = requireNpub(npubOwner)
        Invoice inv = loadOpenOrAnyInvoice(invoiceId, npub)
        if (inv.invoiceStatus == 'VOID' || inv.invoiceStatus == 'DRAFT') {
            throw new IllegalStateException("cannot collect on invoice status=${inv.invoiceStatus}")
        }

        String kind = (fields.kind ?: 'NOTE').toString().trim().toUpperCase()
        if (!(kind in ACTIVITY_KINDS)) {
            throw new IllegalArgumentException("kind must be one of ${ACTIVITY_KINDS}")
        }

        String channel = fields.channel?.toString()?.trim()?.toUpperCase()
        if (channel && !(channel in CHANNELS)) {
            throw new IllegalArgumentException("channel must be one of ${CHANNELS}")
        }

        Date activityAt = parseDate(fields.activityAt) ?: new Date()
        BigDecimal promisedAmount = toBd(fields.promisedAmountEur ?: fields.promisedAmount)
        Date promisedPayBy = parseDate(fields.promisedPayBy)

        if (kind == 'PROMISE') {
            if (!promisedPayBy) {
                throw new IllegalArgumentException('promisedPayBy is required for PROMISE')
            }
            if (promisedAmount == null) {
                promisedAmount = amountOpenEur(inv)
            }
        }

        String name = fields.name?.toString()?.trim()
        if (!name) {
            name = "${kind} ${inv.invoiceNumber ?: inv.id}"
        }

        CollectionActivity act = new CollectionActivity(
                npubOwner         : npub,
                name              : name,
                description       : fields.description?.toString() ?: fields.note?.toString(),
                invoiceId         : inv.id,
                kind              : kind,
                channel           : channel,
                activityAt        : activityAt,
                promisedAmountEur : promisedAmount,
                promisedPayBy     : promisedPayBy,
                promiseStatus     : kind == 'PROMISE' ? 'OPEN' : 'OPEN',
                status            : 'OPEN',
                createdByNpub     : fields.createdByNpub?.toString()
        )
        act.save(failOnError: true, flush: true)
        log.info('collections activity {} kind={} invoice={}', act.id, kind, inv.id)

        if (kind == 'REMINDER' && domainEventBusService != null) {
            try {
                domainEventBusService.publish(
                        DomainEventNames.FINANCE_INVOICE_OVERDUE,
                        'finance.collections',
                        npub,
                        [
                                invoiceId     : inv.id,
                                invoiceNumber : inv.invoiceNumber,
                                amountOpenEur : amountOpenEur(inv),
                                dueDate       : inv.dueDate,
                                activityId    : act.id
                        ]
                )
            } catch (Exception e) {
                log.warn('finance.invoice.overdue publish failed: {}', e.message)
            }
        }

        return act
    }

    /** Shorthand for kind=PROMISE. */
    @Transactional
    CollectionActivity recordPromise(String npubOwner, Long invoiceId, Map fields) {
        Map body = new LinkedHashMap(fields ?: [:])
        body.kind = 'PROMISE'
        recordActivity(npubOwner, invoiceId, body)
    }

    @Transactional(readOnly = true)
    Map listActivities(String npubOwner, Long invoiceId) {
        String npub = requireNpub(npubOwner)
        loadOpenOrAnyInvoice(invoiceId, npub)
        List<CollectionActivity> rows = CollectionActivity.findAllByNpubOwnerAndInvoiceId(
                npub, invoiceId, [sort: 'activityAt', order: 'desc'])
        [
                npubOwner : npub,
                invoiceId : invoiceId,
                total     : rows.size(),
                items     : rows.collect { activityToMap(it) }
        ]
    }

    @Transactional
    CollectionActivity updatePromiseStatus(String npubOwner, Long activityId, String promiseStatus) {
        String npub = requireNpub(npubOwner)
        CollectionActivity act = CollectionActivity.get(activityId)
        if (!act || act.npubOwner != npub) {
            throw new IllegalArgumentException("collection activity not found: ${activityId}")
        }
        if (act.kind != 'PROMISE') {
            throw new IllegalStateException('promiseStatus only applies to PROMISE activities')
        }
        String st = promiseStatus?.trim()?.toUpperCase()
        if (!(st in ['OPEN', 'KEPT', 'BROKEN', 'CANCELLED'])) {
            throw new IllegalArgumentException('promiseStatus must be OPEN|KEPT|BROKEN|CANCELLED')
        }
        act.promiseStatus = st
        act.status = st
        act.save(failOnError: true, flush: true)
        return act
    }

    // -------------------------------------------------------------------------
    // Maps / helpers
    // -------------------------------------------------------------------------

    Map activityToMap(CollectionActivity act) {
        if (!act) {
            return null
        }
        [
                id                : act.id,
                name              : act.name,
                description       : act.description,
                invoiceId         : act.invoiceId,
                kind              : act.kind,
                channel           : act.channel,
                activityAt        : act.activityAt,
                promisedAmountEur : act.promisedAmountEur,
                promisedPayBy     : act.promisedPayBy,
                promiseStatus     : act.promiseStatus,
                status            : act.status,
                npubOwner         : act.npubOwner,
                createdByNpub     : act.createdByNpub,
                dateCreated       : act.dateCreated
        ]
    }

    BigDecimal amountOpenEur(Invoice inv) {
        BigDecimal total = inv?.amountEur ?: BigDecimal.ZERO
        BigDecimal paid = sumSettled(inv)
        BigDecimal open = total.subtract(paid)
        open.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : open
    }

    BigDecimal sumSettled(Invoice inv) {
        if (!inv) {
            return BigDecimal.ZERO
        }
        List<Payment> settled = Payment.findAllByInvoiceAndPaymentStatus(inv, 'SETTLED')
        settled.inject(BigDecimal.ZERO) { BigDecimal acc, Payment p ->
            acc.add(p.amountEur ?: BigDecimal.ZERO)
        } as BigDecimal
    }

    static String agingBucket(int daysOverdue) {
        if (daysOverdue <= 0) {
            return 'CURRENT'
        }
        if (daysOverdue <= 30) {
            return '1-30'
        }
        if (daysOverdue <= 60) {
            return '31-60'
        }
        return '61+'
    }

    static int daysBetween(Date from, Date to) {
        if (!from || !to) {
            return 0
        }
        long ms = startOfDay(to).time - startOfDay(from).time
        (int) (ms / (24L * 60L * 60L * 1000L))
    }

    static Date startOfDay(Date d) {
        Calendar c = Calendar.getInstance()
        c.time = d ?: new Date()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.time
    }

    private Map queueItem(Invoice inv, Date dayStart) {
        BigDecimal paid = sumSettled(inv)
        BigDecimal open = amountOpenEur(inv)
        int daysOverdue = 0
        if (inv.dueDate && inv.dueDate.before(dayStart)) {
            daysOverdue = daysBetween(inv.dueDate, dayStart)
        }
        List<CollectionActivity> promises = CollectionActivity.findAllByNpubOwnerAndInvoiceIdAndKind(
                inv.npubOwner, inv.id, 'PROMISE', [sort: 'activityAt', order: 'desc', max: 1])
        CollectionActivity latestPromise = promises ? promises[0] : null
        int activityCount = CollectionActivity.countByNpubOwnerAndInvoiceId(inv.npubOwner, inv.id) as int
        [
                invoiceId         : inv.id,
                invoiceNumber     : inv.invoiceNumber,
                customerId        : inv.customerId,
                customerName      : inv.customerName,
                customerContact   : inv.customerContact,
                issueDate         : inv.issueDate,
                dueDate           : inv.dueDate,
                amountEur         : inv.amountEur,
                paidEur           : paid,
                amountOpenEur     : open,
                currencyCode      : inv.currencyCode,
                invoiceStatus     : inv.invoiceStatus,
                daysOverdue       : daysOverdue,
                agingBucket       : agingBucket(daysOverdue),
                activityCount     : activityCount,
                latestPromise     : latestPromise ? activityToMap(latestPromise) : null,
                npubOwner         : inv.npubOwner
        ]
    }

    private Invoice loadOpenOrAnyInvoice(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Invoice inv = Invoice.get(id)
        if (!inv || inv.npubOwner != npub) {
            throw new IllegalArgumentException("invoice not found: ${id}")
        }
        return inv
    }

    private static String requireNpub(String npubOwner) {
        String n = npubOwner?.trim()
        if (!n) {
            throw new IllegalArgumentException('npubOwner is required')
        }
        return n
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') {
            return null
        }
        if (v instanceof BigDecimal) {
            return v as BigDecimal
        }
        try {
            return new BigDecimal(v.toString().trim())
        } catch (Exception ignored) {
            return null
        }
    }

    private static Date parseDate(Object raw) {
        if (raw == null || raw == '') {
            return null
        }
        if (raw instanceof Date) {
            return raw as Date
        }
        String s = raw.toString().trim()
        if (!s) {
            return null
        }
        try {
            if (s.isLong()) {
                return new Date(s.toLong())
            }
            if (s.length() >= 10) {
                return Date.parse('yyyy-MM-dd', s.take(10))
            }
        } catch (Exception ignored) {
        }
        return null
    }
}
