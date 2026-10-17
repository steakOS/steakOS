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

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import java.math.RoundingMode
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
import steakos.supplychain.ScmInventoryService
import steakos.supplychain.StockMove

/**
 * Live domain aggregations for Silver ship KPIs (PR-13 / WP-K2–K5).
 * Honest {@code insufficient} when a ratio has no sample; DEFER / stretch stay non-ready.
 */
@Slf4j
@Transactional(readOnly = true)
class KpiFeedService {

    static final int DEFAULT_PERIOD_DAYS = 90
    static final int STOCK_LOOKBACK_DAYS = 30

    /** Optional — PART_PAID remaining via settled payments. */
    FinanceCollectionsService financeCollectionsService
    /** Optional — on-hand summary for stock-at-risk. */
    ScmInventoryService scmInventoryService

    /**
     * Compute value envelope for a catalogue row.
     * @return map with amount/count/ratio/unit/status/message (+ optional buckets/breakdown)
     */
    Map compute(Map defn, String npubOwner, Date from = null, Date to = null) {
        String id = KpiCatalogue.normalizeId(defn?.id as String)
        if (!id || !defn) {
            return envelope(defn, KpiCatalogue.STATUS_PLANNED, null, null, null, 'unknown kpi')
        }
        if (defn.deferred) {
            return envelope(defn, 'deferred', null, null, null,
                    "Deferred: ${defn.blocker ?: 'not claimed for Silver v1'}")
        }
        // Silver-new stretch (I02/F06) — not in ship set; leave planned until polish PRs
        if (!KpiCatalogue.isShip(id) && defn.maturity == KpiCatalogue.MATURITY_SILVER) {
            return envelope(defn, KpiCatalogue.STATUS_PLANNED, null, null, null,
                    'Silver-new stretch — feed not claimed in PR-13 ship set')
        }

        String npub = requireNpub(npubOwner)
        Period period = resolvePeriod(from, to)

        try {
            switch (id) {
                case KpiCatalogue.S01: return feedS01(defn, npub, period)
                case KpiCatalogue.S02: return feedS02(defn, npub)
                case KpiCatalogue.S03: return feedS03(defn, npub, period)
                case KpiCatalogue.D01: return feedD01(defn, npub)
                case KpiCatalogue.D02: return feedD02(defn, npub)
                case KpiCatalogue.I01: return feedI01(defn, npub)
                case KpiCatalogue.F01: return feedF01(defn, npub)
                case KpiCatalogue.F02: return feedF02(defn, npub)
                case KpiCatalogue.F03: return feedF03(defn, npub)
                case KpiCatalogue.F04: return feedF04(defn, npub, period)
                case KpiCatalogue.H01: return feedH01(defn, npub)
                case KpiCatalogue.H02: return feedH02(defn, npub, period)
                case KpiCatalogue.Q01: return feedQ01(defn, npub)
                case KpiCatalogue.Q02: return feedQ02(defn, npub)
                case KpiCatalogue.Q04: return feedQ04(defn, npub, period)
                case KpiCatalogue.W01: return feedW01(defn, npub)
                default:
                    return envelope(defn, KpiCatalogue.STATUS_PLANNED, null, null, null,
                            'Feed not wired yet')
            }
        } catch (Exception e) {
            log.warn('KPI feed {} failed for {}: {}', id, npub, e.message)
            return envelope(defn, KpiCatalogue.STATUS_INSUFFICIENT, null, null, null,
                    "Could not compute: ${e.message}")
        }
    }

    /** AR aging money buckets for Finance hub charts. */
    Map arAging(String npubOwner, Date asOf = new Date()) {
        String npub = requireNpub(npubOwner)
        Map value = feedF01([
                id  : KpiCatalogue.F01,
                unit: 'money'
        ], npub, asOf)
        [
                npubOwner: npub,
                asOf     : startOfDay(asOf),
                value    : value
        ]
    }

    // -------------------------------------------------------------------------
    // Sales
    // -------------------------------------------------------------------------

    private Map feedS01(Map defn, String npub, Period period) {
        List<CustomerQuote> quotes = CustomerQuote.findAllByNpubOwner(npub)
        int accepted = 0, rejected = 0, expired = 0
        quotes.each { CustomerQuote q ->
            if (!inPeriod(q.issueDate, period)) {
                return
            }
            switch (q.quoteStatus) {
                case 'ACCEPTED': accepted++; break
                case 'REJECTED': rejected++; break
                case 'EXPIRED': expired++; break
            }
        }
        int decided = accepted + rejected + expired
        if (decided == 0) {
            return envelope(defn, KpiCatalogue.STATUS_INSUFFICIENT, null, 0, null,
                    'Not enough decided quotes in period (need ACCEPTED/REJECTED/EXPIRED)') + [
                    sampleSize: 0,
                    from      : period.from.time,
                    to        : period.to.time
            ]
        }
        BigDecimal ratio = bd(accepted).divide(bd(decided), 8, RoundingMode.HALF_UP)
        envelope(defn, KpiCatalogue.STATUS_READY, null, decided, ratio,
                "Won ${accepted} of ${decided} decided quotes") + [
                accepted  : accepted,
                rejected  : rejected,
                expired   : expired,
                sampleSize: decided,
                from      : period.from.time,
                to        : period.to.time
        ]
    }

    private Map feedS02(Map defn, String npub) {
        List<CustomerQuote> open = CustomerQuote.findAllByNpubOwnerAndQuoteStatusInList(
                npub, ['DRAFT', 'SENT'])
        BigDecimal sum = BigDecimal.ZERO
        open.each { sum = sum.add(it.amountEur ?: BigDecimal.ZERO) }
        envelope(defn, KpiCatalogue.STATUS_READY, sum, open.size(), null,
                open ? "${open.size()} open quotes" : 'No open quotes')
    }

    private Map feedS03(Map defn, String npub, Period period) {
        List<SalesOrder> orders = SalesOrder.findAllByNpubOwnerAndOrderStatusInList(
                npub, ['CONFIRMED', 'CLOSED'])
        BigDecimal sum = BigDecimal.ZERO
        int count = 0
        orders.each { SalesOrder o ->
            Date when = o.confirmedAt ?: o.orderDate
            if (!inPeriod(when, period)) {
                return
            }
            sum = sum.add(o.amountEur ?: BigDecimal.ZERO)
            count++
        }
        envelope(defn, KpiCatalogue.STATUS_READY, sum, count, null,
                count ? "${count} confirmed orders in period" : 'No confirmed orders in period') + [
                from: period.from.time,
                to  : period.to.time
        ]
    }

    // -------------------------------------------------------------------------
    // Delivery
    // -------------------------------------------------------------------------

    private Map feedD01(Map defn, String npub) {
        List<ServiceOrder> completed = ServiceOrder.findAllByNpubOwnerAndFulfillStatus(npub, 'COMPLETED')
        int withSchedule = 0
        int onTime = 0
        completed.each { ServiceOrder o ->
            if (!o.scheduledAt || !o.completedAt) {
                return
            }
            withSchedule++
            if (!o.completedAt.after(endOfDay(o.scheduledAt))) {
                onTime++
            }
        }
        if (withSchedule == 0) {
            return envelope(defn, KpiCatalogue.STATUS_INSUFFICIENT, null, 0, null,
                    'Not enough completed jobs with scheduledAt to measure on-time') + [
                    sampleSize: 0
            ]
        }
        BigDecimal ratio = bd(onTime).divide(bd(withSchedule), 8, RoundingMode.HALF_UP)
        envelope(defn, KpiCatalogue.STATUS_READY, null, withSchedule, ratio,
                "${onTime} of ${withSchedule} completed on or before scheduled day") + [
                onTime    : onTime,
                sampleSize: withSchedule
        ]
    }

    private Map feedD02(Map defn, String npub) {
        Date now = new Date()
        List<ServiceOrder> open = ServiceOrder.findAllByNpubOwnerAndFulfillStatusInList(
                npub, ['DRAFT', 'SCHEDULED', 'IN_PROGRESS'])
        int overdue = 0
        open.each { ServiceOrder o ->
            if (o.scheduledAt && o.scheduledAt.before(now)) {
                overdue++
            }
        }
        envelope(defn, KpiCatalogue.STATUS_READY, null, overdue, null,
                overdue ? "${overdue} open jobs past scheduledAt" : 'No overdue open jobs')
    }

    // -------------------------------------------------------------------------
    // Stock
    // -------------------------------------------------------------------------

    private Map feedI01(Map defn, String npub) {
        Date since = daysAgo(STOCK_LOOKBACK_DAYS)
        Map<Long, BigDecimal> onHand = [:]
        Map<Long, BigDecimal> issueVol = [:]

        if (scmInventoryService != null) {
            scmInventoryService.onHandSummary(npub).each { Map row ->
                Long cid = row.catalogueItemId as Long
                onHand[cid] = row.onHand as BigDecimal
            }
        } else {
            StockMove.findAllByNpubOwner(npub, [max: 10000]).each { StockMove m ->
                onHand[m.catalogueItemId] = (onHand[m.catalogueItemId] ?: BigDecimal.ZERO)
                        .add(m.qtyDelta ?: BigDecimal.ZERO)
            }
        }

        StockMove.findAllByNpubOwnerAndReason(npub, 'ISSUE', [max: 10000]).each { StockMove m ->
            if (m.movedAt && m.movedAt.before(since)) {
                return
            }
            // ISSUE qtyDelta is typically negative; volume = abs
            BigDecimal vol = (m.qtyDelta ?: BigDecimal.ZERO).abs()
            issueVol[m.catalogueItemId] = (issueVol[m.catalogueItemId] ?: BigDecimal.ZERO).add(vol)
        }

        List<Map> atRisk = []
        issueVol.each { Long cid, BigDecimal vol ->
            BigDecimal oh = onHand[cid] ?: BigDecimal.ZERO
            if (oh.compareTo(BigDecimal.ZERO) <= 0 && vol.compareTo(BigDecimal.ZERO) > 0) {
                atRisk << [catalogueItemId: cid, onHand: oh, issueQty30d: vol]
            }
        }
        atRisk.sort { Map a, Map b ->
            ((b.issueQty30d as BigDecimal) <=> (a.issueQty30d as BigDecimal))
        }

        envelope(defn, KpiCatalogue.STATUS_READY, null, atRisk.size(), null,
                atRisk ? "${atRisk.size()} GOODS at risk (onHand≤0 with recent ISSUE)" :
                        'No stock-out risk items') + [
                items: atRisk.take(20)
        ]
    }

    // -------------------------------------------------------------------------
    // Finance
    // -------------------------------------------------------------------------

    private Map feedF01(Map defn, String npub, Date asOf = new Date()) {
        Date dayStart = startOfDay(asOf)
        List<Invoice> open = Invoice.findAllByNpubOwnerAndInvoiceStatusInList(
                npub, ['ISSUED', 'PART_PAID'])
        Map<String, BigDecimal> buckets = [
                'CURRENT': BigDecimal.ZERO,
                '1-30'   : BigDecimal.ZERO,
                '31-60'  : BigDecimal.ZERO,
                '61+'    : BigDecimal.ZERO
        ]
        Map<String, Integer> counts = [
                'CURRENT': 0, '1-30': 0, '31-60': 0, '61+': 0
        ]
        BigDecimal totalOpen = BigDecimal.ZERO
        BigDecimal overdueTotal = BigDecimal.ZERO
        open.each { Invoice inv ->
            BigDecimal openAmt = amountOpen(inv)
            int daysOverdue = 0
            if (inv.dueDate && inv.dueDate.before(dayStart)) {
                daysOverdue = FinanceCollectionsService.daysBetween(inv.dueDate, dayStart)
            }
            String bucket = FinanceCollectionsService.agingBucket(daysOverdue)
            buckets[bucket] = buckets[bucket].add(openAmt)
            counts[bucket] = (counts[bucket] as Integer) + 1
            totalOpen = totalOpen.add(openAmt)
            if (daysOverdue > 0) {
                overdueTotal = overdueTotal.add(openAmt)
            }
        }
        envelope(defn, KpiCatalogue.STATUS_READY, totalOpen, open.size(), null,
                open ? "Open AR ${totalOpen} (${open.size()} invoices)" : 'No open customer invoices') + [
                overdueAmount: overdueTotal,
                buckets      : buckets,
                bucketCounts : counts,
                asOf         : dayStart.time,
                note         : 'PART_PAID uses amount minus SETTLED payments when available'
        ]
    }

    private Map feedF02(Map defn, String npub) {
        List<Treasury> pots = Treasury.findAllByNpubOwner(npub).findAll {
            it.active == null || it.active
        }
        BigDecimal fiat = BigDecimal.ZERO
        long btcSats = 0L
        pots.each { Treasury t ->
            String kind = t.accountKind ?: ''
            if (kind.startsWith('FIAT') || kind == 'OTHER') {
                fiat = fiat.add(t.balanceEur ?: BigDecimal.ZERO)
            } else if (kind in ['BTC_WALLET', 'LN_WALLET']) {
                btcSats += (t.balanceSats ?: 0L)
            }
        }
        envelope(defn, KpiCatalogue.STATUS_READY, fiat, pots.size(), null,
                "Fiat ${fiat} EUR; BTC rails ${btcSats} sats (separate)") + [
                fiatEur   : fiat,
                btcSats   : btcSats,
                breakdown : [
                        fiatEur: fiat,
                        btcSats: btcSats
                ]
        ]
    }

    private Map feedF03(Map defn, String npub) {
        Date now = startOfDay(new Date())
        Date in7 = daysFrom(7, now)
        Date in30 = daysFrom(30, now)
        List<Payable> open = Payable.findAllByNpubOwnerAndPayableStatus(npub, 'OPEN')
        BigDecimal sum7 = BigDecimal.ZERO
        BigDecimal sum30 = BigDecimal.ZERO
        int c7 = 0, c30 = 0
        open.each { Payable p ->
            if (!p.dueDate) {
                return
            }
            Date due = startOfDay(p.dueDate)
            if (due.before(now)) {
                // already due — include in both windows as "soon" pressure
                sum7 = sum7.add(p.amountEur ?: BigDecimal.ZERO)
                sum30 = sum30.add(p.amountEur ?: BigDecimal.ZERO)
                c7++; c30++
                return
            }
            if (!due.after(in7)) {
                sum7 = sum7.add(p.amountEur ?: BigDecimal.ZERO)
                c7++
            }
            if (!due.after(in30)) {
                sum30 = sum30.add(p.amountEur ?: BigDecimal.ZERO)
                c30++
            }
        }
        envelope(defn, KpiCatalogue.STATUS_READY, sum30, c30, null,
                "Due in 7d: ${c7} / ${sum7}; in 30d: ${c30} / ${sum30}") + [
                due7 : [count: c7, amount: sum7],
                due30: [count: c30, amount: sum30]
        ]
    }

    private Map feedF04(Map defn, String npub, Period period) {
        List<Payment> settled = Payment.findAllByNpubOwnerAndPaymentStatus(npub, 'SETTLED')
        Map<String, BigDecimal> byRail = [:]
        BigDecimal total = BigDecimal.ZERO
        int count = 0
        settled.each { Payment p ->
            Date when = p.receivedAt
            if (when && !inPeriod(when, period)) {
                return
            }
            // If no receivedAt, still count (honest inclusion of undated settled)
            String rail = (p.rail ?: 'OTHER').toString()
            BigDecimal amt = p.amountEur ?: BigDecimal.ZERO
            byRail[rail] = (byRail[rail] ?: BigDecimal.ZERO).add(amt)
            total = total.add(amt)
            count++
        }
        if (count == 0) {
            return envelope(defn, KpiCatalogue.STATUS_INSUFFICIENT, null, 0, null,
                    'No settled payments in period') + [
                    breakdown: [:],
                    from     : period.from.time,
                    to       : period.to.time
            ]
        }
        envelope(defn, KpiCatalogue.STATUS_READY, total, count, null,
                "Settled ${total} across ${byRail.size()} rails") + [
                breakdown: byRail,
                from     : period.from.time,
                to       : period.to.time
        ]
    }

    // -------------------------------------------------------------------------
    // People
    // -------------------------------------------------------------------------

    private Map feedH01(Map defn, String npub) {
        int active = HRFile.countByNpubOwnerAndEmploymentStatus(npub, 'ACTIVE') as int
        envelope(defn, KpiCatalogue.STATUS_READY, null, active, null,
                "${active} active employment files")
    }

    private Map feedH02(Map defn, String npub, Period period) {
        List<TimeTrack> rows = TimeTrack.findAllByNpubOwnerAndTimeKindAndTimeStatus(
                npub, 'WORK', 'POSTED')
        BigDecimal hours = BigDecimal.ZERO
        int n = 0
        rows.each { TimeTrack t ->
            Date when = t.startedAt ?: t.endedAt
            // Undated posted rows count (sparse shops); dated rows must fall in period
            if (when && !inPeriod(when, period)) {
                return
            }
            hours = hours.add(t.hours ?: BigDecimal.ZERO)
            n++
        }
        envelope(defn, KpiCatalogue.STATUS_READY, hours, n, null,
                n ? "${hours} WORK hours posted in period" : 'No posted WORK hours in period') + [
                from: period.from.time,
                to  : period.to.time
        ]
    }

    // -------------------------------------------------------------------------
    // Quality
    // -------------------------------------------------------------------------

    private Map feedQ01(Map defn, String npub) {
        int open = Inquiry.countByNpubOwnerAndCaseStatusInList(
                npub, ['OPEN', 'IN_PROGRESS']) as int
        envelope(defn, KpiCatalogue.STATUS_READY, null, open, null,
                open ? "${open} open support cases" : 'No open support cases')
    }

    private Map feedQ02(Map defn, String npub) {
        Date now = new Date()
        List<Inquiry> open = Inquiry.findAllByNpubOwnerAndCaseStatusInList(
                npub, ['OPEN', 'IN_PROGRESS'])
        int past = 0
        open.each { Inquiry inq ->
            if (inq.resolveDueAt && inq.resolveDueAt.before(now)) {
                past++
            }
        }
        envelope(defn, KpiCatalogue.STATUS_READY, null, past, null,
                past ? "${past} cases past resolve-by" : 'No cases past resolve-by')
    }

    private Map feedQ04(Map defn, String npub, Period period) {
        List<Lot> lots = Lot.findAllByNpubOwner(npub)
        int released = 0, rejected = 0
        lots.each { Lot lot ->
            if (!(lot.lotStatus in ['RELEASED', 'REJECTED'])) {
                return
            }
            Date when = lot.manufacturedOn
            if (when && !inPeriod(when, period)) {
                return
            }
            if (lot.lotStatus == 'RELEASED') {
                released++
            } else {
                rejected++
            }
        }
        int decided = released + rejected
        if (decided == 0) {
            return envelope(defn, KpiCatalogue.STATUS_INSUFFICIENT, null, 0, null,
                    'No RELEASED/REJECTED lots in period') + [
                    sampleSize: 0,
                    from      : period.from.time,
                    to        : period.to.time
            ]
        }
        BigDecimal ratio = bd(rejected).divide(bd(decided), 8, RoundingMode.HALF_UP)
        envelope(defn, KpiCatalogue.STATUS_READY, null, decided, ratio,
                "${rejected} rejected of ${decided} decided lots") + [
                released  : released,
                rejected  : rejected,
                sampleSize: decided,
                from      : period.from.time,
                to        : period.to.time
        ]
    }

    // -------------------------------------------------------------------------
    // Work
    // -------------------------------------------------------------------------

    private Map feedW01(Map defn, String npub) {
        Date now = new Date()
        List<Task> tasks = Task.findAllByNpubOwner(npub)
        int overdue = 0
        tasks.each { Task t ->
            String st = t.status ?: 'OPEN'
            if (st in ['DONE', 'CANCELLED']) {
                return
            }
            if (t.deadlineAt && t.deadlineAt.before(now)) {
                overdue++
            }
        }
        envelope(defn, KpiCatalogue.STATUS_READY, null, overdue, null,
                overdue ? "${overdue} overdue tasks" : 'No overdue tasks')
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BigDecimal amountOpen(Invoice inv) {
        if (financeCollectionsService != null) {
            return financeCollectionsService.amountOpenEur(inv)
        }
        BigDecimal total = inv?.amountEur ?: BigDecimal.ZERO
        List<Payment> settled = Payment.findAllByInvoiceAndPaymentStatus(inv, 'SETTLED')
        BigDecimal paid = settled.inject(BigDecimal.ZERO) { BigDecimal acc, Payment p ->
            acc.add(p.amountEur ?: BigDecimal.ZERO)
        } as BigDecimal
        BigDecimal open = total.subtract(paid)
        open.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : open
    }

    private static Map envelope(Map defn, String status, BigDecimal amount, Integer count,
                                BigDecimal ratio, String message) {
        [
                amount : amount,
                count  : count,
                ratio  : ratio,
                unit   : defn?.unit,
                status : status,
                message: message
        ]
    }

    private static Period resolvePeriod(Date from, Date to) {
        Date end = to ? endOfDay(to) : endOfDay(new Date())
        Date start = from ? startOfDay(from) : daysAgo(DEFAULT_PERIOD_DAYS, end)
        if (start.after(end)) {
            Date tmp = start
            start = startOfDay(end)
            end = endOfDay(tmp)
        }
        new Period(from: start, to: end)
    }

    private static boolean inPeriod(Date d, Period period) {
        if (!d) {
            return false
        }
        !d.before(period.from) && !d.after(period.to)
    }

    private static Date startOfDay(Date d) {
        Calendar c = Calendar.getInstance()
        c.time = d ?: new Date()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.time
    }

    private static Date endOfDay(Date d) {
        Calendar c = Calendar.getInstance()
        c.time = d ?: new Date()
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        c.time
    }

    private static Date daysAgo(int n, Date asOf = new Date()) {
        Calendar c = Calendar.getInstance()
        c.time = startOfDay(asOf)
        c.add(Calendar.DAY_OF_MONTH, -n)
        c.time
    }

    private static Date daysFrom(int n, Date asOf = new Date()) {
        Calendar c = Calendar.getInstance()
        c.time = startOfDay(asOf)
        c.add(Calendar.DAY_OF_MONTH, n)
        c.time
    }

    private static BigDecimal bd(Number n) {
        n == null ? BigDecimal.ZERO : new BigDecimal(n.toString())
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) {
            throw new IllegalArgumentException('npubOwner is required')
        }
        n.trim()
    }

    private static class Period {
        Date from
        Date to
    }
}
