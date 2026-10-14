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
import java.math.RoundingMode
import java.util.Calendar

/**
 * Thin tax estimates (APQC 9.9 subset, F-S6).
 * OI-F23 D10: when invoices carry line tax, sum invoice taxAmountEur; else flat % × revenue.
 */
@Slf4j
class FinanceTaxService {

    FinanceLedgerService financeLedgerService
    steakos.configuration.CompanyTaxService companyTaxService

    /**
     * Period tax estimate. Prefers sum of invoice tax when line tax is in use (D10).
     */
    @Transactional
    Tax estimatePeriodTax(String npubOwner, String period, Map opts = [:]) {
        String npub = requireNpub(npubOwner)
        if (!(period ==~ /^\d{4}-\d{2}$/)) {
            throw new IllegalArgumentException('period must be yyyy-MM')
        }
        BigDecimal rate = toBd(opts.rate) ?: new BigDecimal('0.19')
        String jurisdiction = (opts.jurisdiction ?: 'DE') as String

        BigDecimal amount
        String description
        if (useDocumentTaxSum(npub, opts)) {
            Map summed = sumInvoiceTaxForPeriod(npub, period)
            amount = summed.amount as BigDecimal
            description = "Sum of invoice tax ${period}: ${summed.count} invoice(s), net ${summed.net}"
        } else {
            Map tb = financeLedgerService.trialBalance(npub, period)
            BigDecimal revenue = BigDecimal.ZERO
            (tb.lines as List).each { Map row ->
                if (row.accountType == 'REVENUE') {
                    revenue = revenue.add(row.creditsEur as BigDecimal ?: BigDecimal.ZERO)
                }
            }
            amount = revenue.multiply(rate).setScale(2, RoundingMode.HALF_UP)
            description = "Flat estimate ${rate} × revenue ${revenue}"
        }

        Tax tax = Tax.findByNpubOwnerAndPeriodAndJurisdiction(npub, period, jurisdiction)
        if (!tax) {
            tax = new Tax(
                    npubOwner   : npub,
                    name        : "Tax ${period} ${jurisdiction}",
                    period      : period,
                    jurisdiction: jurisdiction,
                    taxStatus   : 'ESTIMATE'
            )
        }
        tax.amountEur = amount
        tax.description = description
        tax.status = 'ESTIMATE'
        tax.save(failOnError: true, flush: true)
        return tax
    }

    private boolean useDocumentTaxSum(String npub, Map opts) {
        if (opts.forceFlat == true || opts.forceFlat == Boolean.TRUE) return false
        if (opts.forceDocument == true || opts.forceDocument == Boolean.TRUE) return true
        if (companyTaxService == null) return false
        try {
            return companyTaxService.companyHasLineTax(npub)
        } catch (ignored) {
            return false
        }
    }

    private Map sumInvoiceTaxForPeriod(String npub, String period) {
        // period yyyy-MM → issueDate within calendar month (Groovy 4: no Date.parse(fmt, str))
        Calendar cal = Calendar.getInstance()
        cal.clear()
        String[] parts = period.split('-')
        cal.set(Calendar.YEAR, parts[0] as int)
        cal.set(Calendar.MONTH, (parts[1] as int) - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        Date start = cal.time
        cal.add(Calendar.MONTH, 1)
        Date end = cal.time

        BigDecimal taxSum = BigDecimal.ZERO
        BigDecimal netSum = BigDecimal.ZERO
        int count = 0
        Invoice.findAllByNpubOwner(npub).each { Invoice inv ->
            if (inv.invoiceStatus == 'VOID') return
            if (!inv.issueDate) return
            if (inv.issueDate.before(start) || !inv.issueDate.before(end)) return
            if (inv.taxAmountEur == null) return
            taxSum = taxSum.add(inv.taxAmountEur)
            netSum = netSum.add(inv.netAmountEur ?: BigDecimal.ZERO)
            count++
        }
        [amount: taxSum.setScale(2, RoundingMode.HALF_UP), net: netSum, count: count]
    }

    @Transactional(readOnly = true)
    List<Tax> listTaxPeriods(String npubOwner) {
        listTaxPeriodsPage(npubOwner, 200, 0).items as List<Tax>
    }

    /** Paged tax period estimates (LIST-PAGINATION). */
    @Transactional(readOnly = true)
    Map listTaxPeriodsPage(String npubOwner, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'period', order: 'desc', max: max, offset: offset]
        [
                items: Tax.findAllByNpubOwner(npub, opts),
                total: Tax.countByNpubOwner(npub) as int
        ]
    }

    Map toMap(Tax t) {
        if (!t) return null
        [
                id          : t.id,
                name        : t.name,
                period      : t.period,
                jurisdiction: t.jurisdiction,
                amountEur   : t.amountEur,
                taxStatus   : t.taxStatus,
                description : t.description,
                npubOwner   : t.npubOwner
        ]
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') return null
        if (v instanceof BigDecimal) return v
        new BigDecimal(v.toString())
    }
}
