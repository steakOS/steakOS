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

package steakos.human

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.finance.FinancePayrollService
import steakos.finance.PayrollItem

/**
 * Payroll period (APQC 7.5.4). HR3 B: hand off lines to Finance; JE via Finance recognize/pay.
 */
@Slf4j
class HrPayrollService {

    HrFileService hrFileService
    FinancePayrollService financePayrollService

    @Transactional
    Payroll openPeriod(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Map f = fields ?: [:]
        // Accept period / periodCode / YYYY-MM from month input
        String period = (f.period ?: f.periodCode ?: f.payrollPeriod)?.toString()?.trim()
        if (period && period.length() > 7) {
            // e.g. 2026-08-01 → 2026-08
            period = period.substring(0, 7)
        }
        if (!period) {
            period = new java.text.SimpleDateFormat('yyyy-MM').format(new Date())
        }
        // Normalize "2026-8" → "2026-08"
        if (period ==~ /^\d{4}-\d{1,2}$/) {
            def parts = period.split('-')
            period = String.format('%s-%02d', parts[0], parts[1] as int)
        }
        if (!(period ==~ /^\d{4}-\d{2}$/)) {
            throw new IllegalArgumentException("period must be YYYY-MM (got: ${period})")
        }
        if (Payroll.findByNpubOwnerAndPeriod(npub, period)) {
            throw new IllegalArgumentException("payroll period already exists: ${period}")
        }
        // Explicit property sets — map ctor can leave fields null under @GrailsCompileStatic domains
        Payroll p = new Payroll()
        p.npubOwner = npub
        p.period = period
        p.name = (f.name ?: "Payroll ${period}") as String
        p.description = f.description as String
        p.periodStart = parseDate(f.periodStart)
        p.periodEnd = parseDate(f.periodEnd)
        p.totalEur = BigDecimal.ZERO
        p.totalSats = null
        p.currencyCode = (f.currencyCode ?: 'EUR') as String
        p.payrollStatus = 'OPEN'
        p.status = 'OPEN'
        p.save(failOnError: true, flush: true)
        return p
    }

    /**
     * Add/replace conceptual lines by creating Finance PayrollItems and updating totals.
     * fields.lines: [{ hrFileId, amountEur, amountSats?, name? }]
     */
    @Transactional
    Map setLines(Long payrollId, String npubOwner, List lines) {
        Payroll p = load(payrollId, npubOwner)
        if (!(p.payrollStatus in ['OPEN', 'READY'])) {
            throw new IllegalStateException("cannot set lines when ${p.payrollStatus}")
        }
        // Remove existing finance lines for this period tagged with payroll id in description
        List<PayrollItem> existing = financePayrollService.listLines(npubOwner).findAll {
            it.period == p.period && it.description?.contains("hrPayrollId=${p.id}")
        }
        existing.each { financePayrollService.deleteLine(it.id, npubOwner) }

        BigDecimal total = BigDecimal.ZERO
        long sats = 0L
        List<Map> created = []
        (lines ?: []).each { Object raw ->
            Map lm = raw as Map
            Long hrFileId = longOrNull(lm.hrFileId)
            HRFile file = hrFileId ? hrFileService.get(hrFileId, npubOwner) : null
            BigDecimal amt = toBd(lm.amountEur) ?: BigDecimal.ZERO
            Long lineSats = longOrNull(lm.amountSats)
            PayrollItem item = financePayrollService.createLine(npubOwner, [
                    name        : lm.name ?: (file ? "Pay ${file.name}" : 'Pay line'),
                    period      : p.period,
                    personRef   : file ? "hrFile:${file.id}" : lm.personRef,
                    amountEur   : amt,
                    amountSats  : lineSats,
                    currencyCode: p.currencyCode,
                    description : "hrPayrollId=${p.id}"
            ])
            total = total.add(amt)
            if (lineSats) sats += lineSats
            created << financePayrollService.toMap(item)
        }
        p.totalEur = total
        p.totalSats = sats > 0 ? sats : null
        p.save(failOnError: true, flush: true)
        return [payroll: toMap(p), lines: created]
    }

    @Transactional
    Payroll markReady(Long id, String npubOwner) {
        Payroll p = load(id, npubOwner)
        if (p.payrollStatus == 'CLOSED') throw new IllegalStateException('cannot ready closed payroll')
        p.payrollStatus = 'READY'
        p.status = 'READY'
        p.save(failOnError: true, flush: true)
        // Mark finance lines READY
        financePayrollService.listLines(npubOwner).findAll {
            it.period == p.period && it.description?.contains("hrPayrollId=${p.id}")
        }.each { financePayrollService.markReady(it.id, npubOwner) }
        return p
    }

    /**
     * HR3 B: submit — lines already in Finance; mark SUBMITTED.
     * Ledger post is Finance {@code recognizePayroll} / {@code payPayroll} (PDR-027).
     */
    @Transactional
    Map submitToFinance(Long id, String npubOwner) {
        Payroll p = load(id, npubOwner)
        if (!(p.payrollStatus in ['OPEN', 'READY'])) {
            if (p.payrollStatus == 'SUBMITTED') {
                return [payroll: toMap(p), alreadySubmitted: true, lines: listFinanceLines(p, npubOwner)]
            }
            throw new IllegalStateException("cannot submit from ${p.payrollStatus}")
        }
        if (p.payrollStatus == 'OPEN') {
            markReady(id, npubOwner)
            p = load(id, npubOwner)
        }
        p.payrollStatus = 'SUBMITTED'
        p.status = 'SUBMITTED'
        p.save(failOnError: true, flush: true)
        List lines = listFinanceLines(p, npubOwner)
        log.info('Payroll {} submitted to Finance ({} lines; JE via Finance recognize/pay)', p.period, lines.size())
        return [
                payroll         : toMap(p),
                alreadySubmitted: false,
                lines           : lines,
                event           : 'hr.payroll.submitted'
        ]
    }

    @Transactional
    Payroll close(Long id, String npubOwner) {
        Payroll p = load(id, npubOwner)
        p.payrollStatus = 'CLOSED'
        p.status = 'CLOSED'
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional(readOnly = true)
    List<Payroll> list(String npubOwner) {
        listPage(npubOwner, 100, 0).items as List<Payroll>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        return [
                items: Payroll.findAllByNpubOwner(npub, opts),
                total: Payroll.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    Payroll get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(Payroll p) {
        if (!p) return null
        [
                id           : p.id,
                name         : p.name,
                description  : p.description,
                period       : p.period,
                periodStart  : p.periodStart,
                periodEnd    : p.periodEnd,
                totalEur     : p.totalEur,
                totalSats    : p.totalSats,
                currencyCode : p.currencyCode,
                payrollStatus: p.payrollStatus,
                npubOwner    : p.npubOwner
        ]
    }

    private List listFinanceLines(Payroll p, String npub) {
        financePayrollService.listLines(npub).findAll {
            it.period == p.period && it.description?.contains("hrPayrollId=${p.id}")
        }.collect { financePayrollService.toMap(it) }
    }

    private Payroll load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Payroll p = Payroll.get(id)
        if (!p || p.npubOwner != npub) throw new IllegalArgumentException("payroll not found: ${id}")
        p
    }

    private static Long longOrNull(Object v) {
        if (v == null || v == '') return null
        return v as Long
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') return null
        if (v instanceof BigDecimal) return v
        new BigDecimal(v.toString())
    }

    private static Date parseDate(Object raw) {
        if (raw == null || raw == '') return null
        if (raw instanceof Date) return raw
        String s = raw.toString().trim()
        if (s.length() >= 10) {
            try { return Date.parse('yyyy-MM-dd', s.substring(0, 10)) } catch (ignored) {}
        }
        null
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
