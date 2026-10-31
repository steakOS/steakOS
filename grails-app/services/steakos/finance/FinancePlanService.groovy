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
 * Budgets + variance vs trial balance (APQC 9.1 subset, F-S5).
 */
@Slf4j
class FinancePlanService {

    FinanceLedgerService financeLedgerService

    @Transactional
    Budget createBudget(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String period = fields.period?.toString()?.trim()
        if (!(period ==~ /^\d{4}-\d{2}$/)) {
            throw new IllegalArgumentException('period must be yyyy-MM')
        }
        BigDecimal amt = toBd(fields.amountEur)
        if (amt == null || amt < BigDecimal.ZERO) {
            throw new IllegalArgumentException('amountEur required')
        }
        Budget b = new Budget(
                npubOwner  : npub,
                name       : (fields.name ?: "Budget ${period}") as String,
                description: fields.description as String,
                period     : period,
                amountEur  : amt,
                currencyCode: (fields.currencyCode ?: 'EUR') as String,
                accountCode: fields.accountCode as String
        )
        b.save(failOnError: true, flush: true)
        return b
    }

    @Transactional
    Budget reviseBudget(Long id, String npubOwner, Map fields) {
        Budget b = load(id, npubOwner)
        if (fields.name) b.name = fields.name as String
        if (fields.description != null) b.description = fields.description as String
        if (fields.amountEur != null) b.amountEur = toBd(fields.amountEur)
        if (fields.accountCode != null) b.accountCode = fields.accountCode as String
        if (b.respondsTo('bumpRevision')) {
            b.bumpRevision('revise')
        }
        b.save(failOnError: true, flush: true)
        return b
    }

    @Transactional(readOnly = true)
    List<Budget> listBudgets(String npubOwner, String period = null) {
        listBudgetsPage(npubOwner, period, 200, 0).items as List<Budget>
    }

    static final List BUDGET_SORT_FIELDS = [
            'id', 'name', 'period', 'amountEur', 'accountCode', 'currencyCode',
            'dateCreated', 'lastUpdated'
    ].asImmutable()

    /** Paged budgets (LIST-PAGINATION). Optional period filter. */
    @Transactional(readOnly = true)
    Map listBudgetsPage(String npubOwner, String period = null, int max = 25, int offset = 0,
                        Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        String defaultSort = period ? 'id' : 'period'
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], defaultSort, 'desc', BUDGET_SORT_FIELDS)
        List items = Budget.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (period) eq('period', period)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Budget.createCriteria().count {
            eq('npubOwner', npub)
            if (period) eq('period', period)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    /**
     * Compare budget amounts for a period to expense/revenue actuals from trial balance.
     */
    @Transactional(readOnly = true)
    Map compareToActuals(String npubOwner, String period) {
        String npub = requireNpub(npubOwner)
        List<Budget> budgets = Budget.findAllByNpubOwnerAndPeriod(npub, period)
        Map tb = financeLedgerService.trialBalance(npub, period)

        BigDecimal budgetTotal = budgets.inject(BigDecimal.ZERO) { BigDecimal a, Budget b ->
            a.add(b.amountEur ?: BigDecimal.ZERO)
        } as BigDecimal

        // Sum expense debits as "actual spend" for simple variance
        BigDecimal actualExpense = BigDecimal.ZERO
        (tb.lines as List).each { Map row ->
            if (row.accountType == 'EXPENSE') {
                actualExpense = actualExpense.add(row.debitsEur as BigDecimal ?: BigDecimal.ZERO)
            }
        }

        BigDecimal variance = budgetTotal.subtract(actualExpense)

        return [
                period      : period,
                npubOwner   : npub,
                budgetTotal : budgetTotal,
                actualExpense: actualExpense,
                variance    : variance,
                underBudget : variance >= BigDecimal.ZERO,
                budgets     : budgets.collect { toMap(it) },
                trialBalance: tb
        ]
    }

    Map toMap(Budget b) {
        if (!b) return null
        [
                id         : b.id,
                name       : b.name,
                description: b.description,
                period     : b.period,
                amountEur  : b.amountEur,
                currencyCode: b.currencyCode,
                accountCode: b.accountCode,
                npubOwner  : b.npubOwner
        ]
    }

    private Budget load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Budget b = Budget.get(id)
        if (!b || b.npubOwner != npub) throw new IllegalArgumentException("budget not found: ${id}")
        b
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
