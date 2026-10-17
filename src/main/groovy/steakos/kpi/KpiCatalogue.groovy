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

/**
 * Silver light KPI catalogue (plan-kpi-analytics.md WP-K0).
 * 16 ship + 3 DEFER stable ids; I02/F06/Q03 also registered (silver-new / extra DEFER).
 * Values stay {@code planned} until PR-13 domain feeds.
 */
final class KpiCatalogue {

    static final String MATURITY_BRONZE = 'bronze-already'
    static final String MATURITY_SILVER = 'silver-new'
    static final String MATURITY_DEFER = 'defer'

    static final String SENSITIVITY_NONE = 'none'
    static final String SENSITIVITY_MONEY = 'money'
    static final String SENSITIVITY_HR = 'hr'

    static final String STATUS_PLANNED = 'planned'
    static final String STATUS_READY = 'ready'

    // --- Sales ---
    static final String S01 = 'KPI-S01'
    static final String S02 = 'KPI-S02'
    static final String S03 = 'KPI-S03'
    static final String S04 = 'KPI-S04'
    // --- Delivery ---
    static final String D01 = 'KPI-D01'
    static final String D02 = 'KPI-D02'
    // --- Stock ---
    static final String I01 = 'KPI-I01'
    static final String I02 = 'KPI-I02'
    static final String I03 = 'KPI-I03'
    // --- Finance ---
    static final String F01 = 'KPI-F01'
    static final String F02 = 'KPI-F02'
    static final String F03 = 'KPI-F03'
    static final String F04 = 'KPI-F04'
    static final String F05 = 'KPI-F05'
    static final String F06 = 'KPI-F06'
    // --- People ---
    static final String H01 = 'KPI-H01'
    static final String H02 = 'KPI-H02'
    // --- Quality ---
    static final String Q01 = 'KPI-Q01'
    static final String Q02 = 'KPI-Q02'
    static final String Q03 = 'KPI-Q03'
    static final String Q04 = 'KPI-Q04'
    // --- Work ---
    static final String W01 = 'KPI-W01'

    /**
     * Silver v1 DoD ship set (16) — PR-13/14 feed targets.
     * Excludes silver-new stretch I02/F06 and all DEFER rows.
     */
    static final List<String> SHIP_IDS = [
            S01, S02, S03,
            D01, D02,
            I01,
            F01, F02, F03, F04,
            H01, H02,
            Q01, Q02, Q04,
            W01
    ].asImmutable()

    /** Plan headline DEFER trio (S04 opportunity, I03 value turns, F05 margin). */
    static final List<String> DEFER_IDS = [
            S04, I03, F05
    ].asImmutable()

    /** Extra DEFER in catalogue table (Satisfaction skeleton) — documented, not blank UI. */
    static final List<String> EXTRA_DEFER_IDS = [
            Q03
    ].asImmutable()

    /** CEO hero candidates (Silver rich strip). */
    static final List<String> HERO_IDS = [
            F02, F01, S01, D02, W01
    ].asImmutable()

    private static final List<Map> ENTRIES = buildEntries().asImmutable()
    private static final Map<String, Map> BY_ID = ENTRIES.collectEntries { [(it.id): it] }.asImmutable()

    private KpiCatalogue() {}

    static List<Map> all() {
        ENTRIES.collect { copyOf(it) }
    }

    static Map get(String id) {
        Map row = BY_ID[normalizeId(id)]
        row ? copyOf(row) : null
    }

    static boolean contains(String id) {
        BY_ID.containsKey(normalizeId(id))
    }

    static boolean isDeferred(String id) {
        Map row = BY_ID[normalizeId(id)]
        row != null && row.deferred
    }

    static boolean isShip(String id) {
        SHIP_IDS.contains(normalizeId(id))
    }

    static String normalizeId(String id) {
        id?.trim()?.toUpperCase()
    }

    static Map catalogueMeta() {
        [
                version       : 1,
                metal         : 'SILVER',
                shipCount     : SHIP_IDS.size(),
                deferCount    : DEFER_IDS.size(),
                extraDeferCount: EXTRA_DEFER_IDS.size(),
                totalCount    : ENTRIES.size(),
                heroIds       : new ArrayList<>(HERO_IDS),
                note          : 'Values status=planned until PR-13 domain feeds; Bronze hero stays counts-only'
        ]
    }

    private static Map copyOf(Map row) {
        new LinkedHashMap(row)
    }

    private static List<Map> buildEntries() {
        [
                entry(S01, 'Quote win rate', 'sales', MATURITY_BRONZE, false, SENSITIVITY_NONE,
                        'ACCEPTED / (ACCEPTED + REJECTED + EXPIRED) in period; exclude DRAFT',
                        'ratio', '/sales/quotes', true),
                entry(S02, 'Open quotes (money)', 'sales', MATURITY_BRONZE, false, SENSITIVITY_NONE,
                        'SUM(amountEur) where quoteStatus in {DRAFT, SENT}',
                        'money', '/sales/quotes?status=open', false),
                entry(S03, 'Orders confirmed (period)', 'sales', MATURITY_BRONZE, false, SENSITIVITY_NONE,
                        'SUM(amountEur), COUNT where orderStatus in {CONFIRMED, CLOSED} and confirmedAt in period',
                        'money', '/sales/orders', false),
                entry(S04, 'Opportunity pipeline', 'sales', MATURITY_DEFER, true, SENSITIVITY_NONE,
                        'DEFER until SalesAdvanced CRM (leads/opportunities)',
                        'money', '/sales/crm', false, 'No Opportunity domain yet'),

                entry(D01, 'Jobs finished on time', 'delivery', MATURITY_BRONZE, false, SENSITIVITY_NONE,
                        'COMPLETED with completedAt <= endOfDay(scheduledAt) / completed with scheduledAt',
                        'ratio', '/delivery/orders?filter=late', false),
                entry(D02, 'Overdue open jobs', 'delivery', MATURITY_BRONZE, false, SENSITIVITY_NONE,
                        'COUNT open fulfillStatus with scheduledAt < now',
                        'count', '/delivery/orders?filter=overdue', true),

                entry(I01, 'Items at risk of running out', 'stock', MATURITY_BRONZE, false, SENSITIVITY_NONE,
                        'GOODS onHand<=0 with ISSUE activity last 30d',
                        'count', '/scm/inventory', false),
                entry(I02, 'Stock movement pace', 'stock', MATURITY_SILVER, false, SENSITIVITY_NONE,
                        'ABS(SUM ISSUE qty) 30d / AVG(onHand) — proxy, not accounting turns',
                        'ratio', '/scm/inventory', false),
                entry(I03, 'Inventory turns (value)', 'stock', MATURITY_DEFER, true, SENSITIVITY_NONE,
                        'DEFER — needs COGS / inventory value ledger',
                        'ratio', '/scm/inventory', false, 'Stock moves are qty-only'),

                entry(F01, 'Money customers still owe (by age)', 'finance', MATURITY_BRONZE, false, SENSITIVITY_MONEY,
                        'Open invoices bucketed by dueDate: Current / 1-30 / 31-60 / 61+',
                        'money', '/finance/revenue?filter=aging', true),
                entry(F02, 'Cash on hand', 'finance', MATURITY_BRONZE, false, SENSITIVITY_MONEY,
                        'Sum Treasury FIAT balanceEur + BTC/LN balanceSats (rails separate)',
                        'money', '/finance/treasury', true),
                entry(F03, 'Bills due soon', 'finance', MATURITY_BRONZE, false, SENSITIVITY_MONEY,
                        'SUM/COUNT Payable OPEN with dueDate in 7/30 day window',
                        'money', '/finance/payables', false),
                entry(F04, 'How we got paid', 'finance', MATURITY_BRONZE, false, SENSITIVITY_MONEY,
                        'SUM SETTLED Payment amountEur group by rail',
                        'money', '/finance/payments', false),
                entry(F05, 'Gross margin', 'finance', MATURITY_DEFER, true, SENSITIVITY_MONEY,
                        'DEFER — no unit cost on order/invoice lines',
                        'ratio', '/finance', false, 'Sell price only; no honest COGS'),
                entry(F06, 'Cash runway (hint)', 'finance', MATURITY_SILVER, false, SENSITIVITY_MONEY,
                        'cash_fiat_EUR / max(avg monthly net outflow, eps); n/a if history < 60d',
                        'months', '/finance/treasury', false),

                entry(H01, 'People on the books (active)', 'people', MATURITY_BRONZE, false, SENSITIVITY_HR,
                        'COUNT HRFile where employmentStatus = ACTIVE',
                        'count', '/human/files', false),
                entry(H02, 'Hours logged', 'people', MATURITY_BRONZE, false, SENSITIVITY_HR,
                        'SUM TimeTrack WORK/POSTED hours in period; util % optional',
                        'hours', '/human/time', false),

                entry(Q01, 'Open support cases', 'quality', MATURITY_BRONZE, false, SENSITIVITY_NONE,
                        'COUNT Inquiry OPEN/IN_PROGRESS',
                        'count', '/aftersales/inquiries?status=open', false),
                entry(Q02, 'Cases past due', 'quality', MATURITY_BRONZE, false, SENSITIVITY_NONE,
                        'Open/in-progress with resolveDueAt < now',
                        'count', '/aftersales/inquiries?filter=overdue', false),
                entry(Q03, 'Satisfaction score', 'quality', MATURITY_DEFER, true, SENSITIVITY_NONE,
                        'DEFER — Satisfaction domain is skeleton (no score field)',
                        'score', '/aftersales', false, 'Satisfaction skeleton only'),
                entry(Q04, 'QA reject rate', 'quality', MATURITY_BRONZE, false, SENSITIVITY_NONE,
                        'REJECTED / (RELEASED + REJECTED) lots in period',
                        'ratio', '/scm/lots', false),

                entry(W01, 'Overdue tasks', 'work', MATURITY_BRONZE, false, SENSITIVITY_NONE,
                        'Open core.Task with deadlineAt < now',
                        'count', '/dashboard', true)
        ]
    }

    private static Map entry(String id, String name, String area, String maturity, boolean deferred,
                             String sensitivity, String formula, String unit, String drillRoute,
                             boolean hero, String blocker = null) {
        Map m = [
                id          : id,
                name        : name,
                area        : area,
                maturity    : maturity,
                deferred    : deferred,
                ship        : SHIP_IDS.contains(id),
                sensitivity : sensitivity,
                formula     : formula,
                unit        : unit,
                drillRoute  : drillRoute,
                hero        : hero,
                // PR-2 registry only — feeds land in PR-13+
                status      : deferred ? 'deferred' : STATUS_PLANNED
        ]
        if (blocker) {
            m.blocker = blocker
        }
        m
    }
}
