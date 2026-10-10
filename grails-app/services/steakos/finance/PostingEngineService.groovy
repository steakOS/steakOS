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
 * PDR-027 — resolve company posting rules and post balanced journals via {@link FinanceLedgerService}.
 * Cash defaults match SBM settle/pay/payroll when the pack is seeded.
 * Missing / invalid rules block the money action (D8) — no hardcoded account fallback.
 */
@Slf4j
class PostingEngineService {

    static final String EVENT_INVOICE_SETTLE = 'invoice.settle'
    static final String EVENT_PAYABLE_PAY = 'payable.pay'
    static final String EVENT_PAYROLL_RECOGNIZE = 'payroll.recognize'
    static final String EVENT_PAYROLL_PAY = 'payroll.pay'

    static final String CODE_MISSING = 'POSTING_RULES_MISSING'
    static final String CODE_INVALID = 'POSTING_RULES_INVALID'
    static final String CODE_UNBALANCED = 'POSTING_RULES_UNBALANCED'

    PostingRulesetService postingRulesetService
    FinanceLedgerService financeLedgerService

    /**
     * Load ruleset (seed if needed), resolve legs for {@code eventKey}, post JE.
     *
     * @param amounts keys match leg {@code amountFrom} (e.g. payment.functional, payable.functional)
     * @param meta journal fields: name, description, sourceType, sourceId, eventId, memo, entryDate, period,
     *             rail, amountSats, lineMemos (optional List&lt;String&gt;)
     */
    @Transactional
    JournalEntry postEvent(String npubOwner, String eventKey, Map amounts, Map meta = [:]) {
        if (!postingRulesetService) {
            throw new PostingRulesException(CODE_MISSING, 'PostingRulesetService is not available')
        }
        if (!financeLedgerService) {
            throw new IllegalStateException('FinanceLedgerService is required')
        }
        String npub = requireNpub(npubOwner)
        String key = eventKey?.toString()?.trim()
        if (!key) {
            throw new PostingRulesException(CODE_INVALID, 'posting event key is required')
        }

        financeLedgerService.ensureChartSeeded(npub)
        Map ruleset = loadRuleset(npub)
        Map eventDef = requireEvent(ruleset, key)
        List legs = eventDef.legs as List

        String rail = (meta.rail ?: null)?.toString()?.trim()
        Long amountSats = meta.amountSats != null && meta.amountSats != '' ? meta.amountSats as Long : null
        List lineMemos = (meta.lineMemos instanceof List) ? (meta.lineMemos as List) : null

        List<PostJournalLineCommand> lines = []
        BigDecimal totalDebit = BigDecimal.ZERO
        BigDecimal totalCredit = BigDecimal.ZERO

        legs.eachWithIndex { Object legObj, int idx ->
            if (!(legObj instanceof Map)) {
                throw new PostingRulesException(CODE_INVALID, "event ${key} leg ${idx + 1} must be an object")
            }
            Map leg = legObj as Map
            boolean optional = leg.optional as Boolean ?: false
            BigDecimal amt = resolveAmount(amounts, leg.amountFrom as String)
            if (amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) {
                if (optional) {
                    return
                }
                throw new PostingRulesException(CODE_INVALID,
                        "amount '${leg.amountFrom}' missing or zero for event ${key}")
            }

            String accountCode = resolveAccountCode(ruleset, leg, rail, key, idx)
            if (!financeLedgerService.findAccount(npub, accountCode)) {
                throw new PostingRulesException(CODE_INVALID,
                        "account ${accountCode} not on chart of accounts (event ${key})")
            }

            String side = (leg.side as String)?.toUpperCase()
            String memo = lineMemos && idx < lineMemos.size() && lineMemos[idx] != null ?
                    lineMemos[idx].toString() : (leg.memo as String)

            if (side == 'DEBIT') {
                lines << new PostJournalLineCommand(
                        accountCode: accountCode,
                        debitEur   : amt,
                        creditEur  : BigDecimal.ZERO,
                        amountSats : amountSats,
                        memo       : memo
                )
                totalDebit = totalDebit.add(amt)
            } else if (side == 'CREDIT') {
                lines << new PostJournalLineCommand(
                        accountCode: accountCode,
                        debitEur   : BigDecimal.ZERO,
                        creditEur  : amt,
                        amountSats : amountSats,
                        memo       : memo
                )
                totalCredit = totalCredit.add(amt)
            } else {
                throw new PostingRulesException(CODE_INVALID,
                        "leg.side must be DEBIT or CREDIT (event ${key} leg ${idx + 1})")
            }
        }

        if (lines.size() < 2) {
            throw new PostingRulesException(CODE_INVALID,
                    "event ${key} resolved fewer than 2 legs (optional amounts skipped?)")
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new PostingRulesException(CODE_UNBALANCED,
                    "event ${key} unbalanced: debits=${totalDebit} credits=${totalCredit}")
        }

        JournalEntry je = financeLedgerService.postEntry(new PostJournalCommand(
                npubOwner  : npub,
                name       : meta.name as String,
                description: meta.description as String,
                entryDate  : meta.entryDate instanceof Date ? (meta.entryDate as Date) : null,
                period     : meta.period as String,
                sourceType : meta.sourceType as String,
                sourceId   : meta.sourceId != null ? meta.sourceId.toString() : null,
                eventId    : meta.eventId as String,
                memo       : meta.memo as String,
                lines      : lines
        ))
        log.info('PostingEngine posted event={} je={} npub={}', key, je.id, npub)
        return je
    }

    /** Load persisted rules (seed SBM pack when absent). Never falls back to service hardcodes. */
    private Map loadRuleset(String npub) {
        Map seeded = postingRulesetService.ensureSeeded(npub)
        Map ruleset = (seeded?.ruleset instanceof Map) ? (seeded.ruleset as Map) :
                postingRulesetService.getRuleset(npub)
        if (!(ruleset?.events instanceof Map)) {
            throw new PostingRulesException(CODE_MISSING, 'company posting ruleset has no events')
        }
        return ruleset
    }

    private static Map requireEvent(Map ruleset, String eventKey) {
        Map events = ruleset.events as Map
        if (!events.containsKey(eventKey) || events[eventKey] == null) {
            throw new PostingRulesException(CODE_MISSING,
                    "posting rule missing for event ${eventKey}")
        }
        Object ev = events[eventKey]
        if (!(ev instanceof Map)) {
            throw new PostingRulesException(CODE_INVALID,
                    "event ${eventKey} must be an object")
        }
        Object legs = (ev as Map).legs
        if (!(legs instanceof List) || (legs as List).size() < 2) {
            throw new PostingRulesException(CODE_INVALID,
                    "event ${eventKey} needs at least 2 legs")
        }
        return ev as Map
    }

    private String resolveAccountCode(Map ruleset, Map leg, String rail, String eventKey, int idx) {
        String code = leg.accountCode?.toString()?.trim()
        if (code) {
            return code
        }
        String from = leg.accountFrom?.toString()?.trim()
        if (from == 'railWallet') {
            String useRail = rail
            if (!useRail) {
                useRail = leg.defaultRail?.toString()?.trim() ?: 'SEPA'
            }
            return postingRulesetService.walletAccountForRail(ruleset, useRail)
        }
        throw new PostingRulesException(CODE_INVALID,
                "leg needs accountCode or accountFrom=railWallet (event ${eventKey} leg ${idx + 1})")
    }

    private static BigDecimal resolveAmount(Map amounts, String amountFrom) {
        if (!amountFrom?.trim()) {
            return null
        }
        if (!(amounts instanceof Map)) {
            return null
        }
        Object v = amounts[amountFrom]
        if (v == null || v == '') {
            return null
        }
        if (v instanceof BigDecimal) {
            return v as BigDecimal
        }
        return new BigDecimal(v.toString())
    }

    private static String requireNpub(String npubOwner) {
        String n = npubOwner?.trim()
        if (!n) {
            throw new IllegalArgumentException('npubOwner is required')
        }
        return n
    }
}
