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

/**
 * Payroll lines + PDR-027 PR3 recognize/pay via {@link PostingEngineService}.
 * v1 amounts: sum of {@code amountEur} as gross≈net; tax leg omitted until HR fields exist.
 */
@Slf4j
class FinancePayrollService {

    PostingEngineService postingEngineService
    FinanceLedgerService financeLedgerService
    FinanceTreasuryService financeTreasuryService

    @Transactional
    PayrollItem createLine(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        BigDecimal amt = toBd(fields.amountEur) ?: BigDecimal.ZERO
        PayrollItem item = new PayrollItem(
                npubOwner    : npub,
                name         : (fields.name ?: fields.personRef ?: 'Payroll line') as String,
                description  : fields.description as String,
                period       : fields.period as String,
                personRef    : fields.personRef as String,
                amountEur    : amt,
                amountSats   : fields.amountSats != null && fields.amountSats != '' ? fields.amountSats as Long : null,
                currencyCode : (fields.currencyCode ?: 'EUR') as String,
                payrollStatus: 'DRAFT',
                status       : 'DRAFT'
        )
        item.save(failOnError: true, flush: true)
        return item
    }

    @Transactional
    PayrollItem markReady(Long id, String npubOwner) {
        PayrollItem item = load(id, npubOwner)
        item.payrollStatus = 'READY'
        item.status = 'READY'
        item.save(failOnError: true, flush: true)
        return item
    }

    /**
     * Recognize payroll run for a period — event {@code payroll.recognize}
     * (Dr 5200 / Cr 2200; optional tax skipped when no tax amount).
     * Alias of historical {@link #postPayroll}.
     */
    @Transactional
    Map recognizePayroll(String npubOwner, String period, Map fields = [:]) {
        String npub = requireNpub(npubOwner)
        String per = requirePeriod(period)
        List<PayrollItem> lines = linesForPeriod(npub, per)
        if (!lines) {
            throw new IllegalArgumentException("no payroll lines for period ${per}")
        }

        List<PayrollItem> already = lines.findAll { it.payrollStatus in ['POSTED', 'PAID'] && it.journalEntryId }
        if (already && already.size() == lines.size()) {
            return periodResult(npub, per, lines, already[0].journalEntryId, null, true, 'recognize')
        }
        if (lines.any { it.payrollStatus == 'PAID' }) {
            throw new IllegalStateException("period ${per} already paid — cannot re-recognize")
        }

        BigDecimal gross = sumEur(lines)
        if (gross.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("payroll period ${per} total must be > 0")
        }
        // Stub-honest: no employer/employee tax fields yet → net = gross; tax leg optional/skipped
        BigDecimal net = gross
        BigDecimal tax = BigDecimal.ZERO
        Long sats = sumSats(lines)

        if (!postingEngineService) {
            throw new PostingRulesException(PostingEngineService.CODE_MISSING,
                    'PostingEngineService is required for payroll recognize')
        }
        if (financeLedgerService) {
            financeLedgerService.ensureChartSeeded(npub)
        }

        String eventId = (fields.eventId ?: "payroll-recognize:${npub}:${per}").toString().trim()
        JournalEntry je = postingEngineService.postEvent(
                npub,
                PostingEngineService.EVENT_PAYROLL_RECOGNIZE,
                [
                        'payroll.gross': gross,
                        'payroll.net'  : net,
                        // omit / zero → optional 2210 leg skipped
                        'payroll.tax'  : tax,
                ],
                [
                        name      : "Payroll recognize ${per}",
                        description: 'Gross≈net until HR tax/deduction fields exist',
                        sourceType: 'PAYROLL',
                        sourceId  : per,
                        eventId   : eventId,
                        period    : per,
                        amountSats: sats,
                ]
        )

        lines.each { PayrollItem i ->
            i.payrollStatus = 'POSTED'
            i.status = 'POSTED'
            i.journalEntryId = je.id
            i.save(failOnError: true)
        }
        PayrollItem.withSession { it.flush() }

        log.info('Payroll recognize period={} je={} lines={} gross={}', per, je.id, lines.size(), gross)
        return periodResult(npub, per, lines, je.id, null, false, 'recognize')
    }

    /**
     * Pay net wages for a recognized period — event {@code payroll.pay}
     * (Dr 2200 / Cr rail wallet). Debits treasury fiat by default.
     */
    @Transactional
    Map payPayroll(String npubOwner, String period, Map fields = [:]) {
        String npub = requireNpub(npubOwner)
        String per = requirePeriod(period)
        List<PayrollItem> lines = linesForPeriod(npub, per)
        if (!lines) {
            throw new IllegalArgumentException("no payroll lines for period ${per}")
        }

        List<PayrollItem> paid = lines.findAll { it.payrollStatus == 'PAID' && it.payJournalEntryId }
        if (paid && paid.size() == lines.size()) {
            return periodResult(npub, per, lines, lines[0].journalEntryId, paid[0].payJournalEntryId, true, 'pay')
        }

        List<PayrollItem> recognized = lines.findAll { it.payrollStatus in ['POSTED', 'PAID'] && it.journalEntryId }
        if (recognized.size() != lines.size()) {
            throw new IllegalStateException(
                    "period ${per} not fully recognized — call payroll recognize/post first")
        }

        BigDecimal net = sumEur(lines)
        if (net.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("payroll period ${per} total must be > 0")
        }
        Long sats = sumSats(lines)
        String rail = (fields.rail ?: 'SEPA').toString().trim()

        if (!postingEngineService) {
            throw new PostingRulesException(PostingEngineService.CODE_MISSING,
                    'PostingEngineService is required for payroll pay')
        }
        if (financeLedgerService) {
            financeLedgerService.ensureChartSeeded(npub)
        }

        String eventId = (fields.eventId ?: "payroll-pay:${npub}:${per}").toString().trim()
        JournalEntry je = postingEngineService.postEvent(
                npub,
                PostingEngineService.EVENT_PAYROLL_PAY,
                ['payroll.net': net],
                [
                        name      : "Payroll pay ${per}",
                        description: 'Net wages (gross≈net stub until HR deductions)',
                        sourceType: 'PAYROLL',
                        sourceId  : per,
                        eventId   : eventId,
                        period    : per,
                        rail      : rail,
                        amountSats: sats,
                ]
        )

        if (financeTreasuryService) {
            String kind = FinanceTreasuryService.RAIL_TO_KIND[rail] ?: 'FIAT_BANK'
            financeTreasuryService.recordOutbound(npub, [
                    accountKind: kind,
                    amountEur  : net,
                    amountSats : sats
            ])
        }

        lines.each { PayrollItem i ->
            i.payrollStatus = 'PAID'
            i.status = 'PAID'
            i.payJournalEntryId = je.id
            i.save(failOnError: true)
        }
        PayrollItem.withSession { it.flush() }

        log.info('Payroll pay period={} je={} lines={} net={}', per, je.id, lines.size(), net)
        return periodResult(npub, per, lines, lines[0].journalEntryId, je.id, false, 'pay')
    }

    /**
     * Historical alias — recognizes the period (no longer a no-op JE stub).
     */
    @Transactional
    Map postPayroll(String npubOwner, String period, Map fields = [:]) {
        recognizePayroll(npubOwner, period, fields ?: [:])
    }

    @Transactional(readOnly = true)
    List<PayrollItem> listLines(String npubOwner) {
        listLinesPage(npubOwner, 200, 0).items as List<PayrollItem>
    }

    /** Paged payroll lines (LIST-PAGINATION). */
    @Transactional(readOnly = true)
    Map listLinesPage(String npubOwner, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        [
                items: PayrollItem.findAllByNpubOwner(npub, opts),
                total: PayrollItem.countByNpubOwner(npub) as int
        ]
    }

    Map toMap(PayrollItem i) {
        if (!i) return null
        [
                id               : i.id,
                name             : i.name,
                period           : i.period,
                personRef        : i.personRef,
                amountEur        : i.amountEur,
                amountSats       : i.amountSats,
                currencyCode     : i.currencyCode,
                payrollStatus    : i.payrollStatus,
                journalEntryId   : i.journalEntryId,
                payJournalEntryId: i.payJournalEntryId,
                description      : i.description,
                npubOwner        : i.npubOwner
        ]
    }

    @Transactional
    void deleteLine(Long id, String npubOwner) {
        PayrollItem item = load(id, npubOwner)
        if (item.payrollStatus in ['POSTED', 'PAID']) {
            throw new IllegalStateException('cannot delete recognized/paid payroll line')
        }
        item.delete(flush: true)
    }

    private Map periodResult(String npub, String period, List<PayrollItem> lines,
                             Long recognizeJeId, Long payJeId, boolean idempotent, String action) {
        BigDecimal total = sumEur(lines)
        [
                stub              : false,
                action            : action,
                message           : action == 'recognize'
                        ? 'Payroll recognized (gross≈net; tax remittance deferred until HR fields).'
                        : 'Payroll wages paid (net≈gross stub).',
                npubOwner         : npub,
                period            : period,
                lineCount         : lines.size(),
                totalEur          : total,
                payrollGross      : total,
                payrollNet        : total,
                payrollTax        : BigDecimal.ZERO,
                journalEntryId    : recognizeJeId,
                payJournalEntryId : payJeId,
                idempotent        : idempotent,
                lines             : lines.collect { toMap(it) },
        ]
    }

    private List<PayrollItem> linesForPeriod(String npub, String period) {
        PayrollItem.findAllByNpubOwnerAndPeriod(npub, period, [sort: 'id', order: 'asc'])
    }

    private static BigDecimal sumEur(List<PayrollItem> lines) {
        lines.inject(BigDecimal.ZERO) { BigDecimal a, PayrollItem i ->
            a.add(i.amountEur ?: BigDecimal.ZERO)
        } as BigDecimal
    }

    private static Long sumSats(List<PayrollItem> lines) {
        long s = 0L
        lines.each { if (it.amountSats) s += it.amountSats }
        s > 0 ? s : null
    }

    private PayrollItem load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        PayrollItem i = PayrollItem.get(id)
        if (!i || i.npubOwner != npub) throw new IllegalArgumentException("payroll item not found: ${id}")
        i
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    private static String requirePeriod(String period) {
        String p = period?.trim()
        if (!p) throw new IllegalArgumentException('period is required (YYYY-MM)')
        p
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') return null
        if (v instanceof BigDecimal) return v
        new BigDecimal(v.toString())
    }
}
