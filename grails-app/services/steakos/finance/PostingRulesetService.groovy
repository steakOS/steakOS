// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.finance

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import steakos.configuration.CompanyConfigService

/**
 * PDR-027 PR1 — company posting ruleset (CompanySetting JSON) + SBM default pack.
 * PR2–PR3 {@link PostingEngineService} posts settle/pay/payroll.recognize/payroll.pay from these rules.
 */
@Slf4j
@Transactional
class PostingRulesetService {

    static final String KEY_RULES = 'finance.postingRules'
    static final String KEY_MODE = 'finance.accountingMode'
    static final String MODE_CASH = 'CASH'
    static final String MODE_ACCRUAL = 'ACCRUAL'
    static final int RULES_VERSION = 1

    CompanyConfigService companyConfigService
    FinanceLedgerService financeLedgerService

    /** Default SBM / restaurant cash pack (matches today’s hardcoded settle/pay behaviour + payroll D10). */
    static Map defaultSbmPack() {
        [
                version        : RULES_VERSION,
                accountingMode : MODE_CASH,
                events         : [
                        'invoice.settle'    : [
                                legs: [
                                        [side: 'DEBIT', accountFrom: 'railWallet', amountFrom: 'payment.functional'],
                                        [side: 'CREDIT', accountCode: '4000', amountFrom: 'payment.functional'],
                                ]
                        ],
                        'payable.pay'       : [
                                legs: [
                                        [side: 'DEBIT', accountCode: '5000', amountFrom: 'payable.functional'],
                                        [side: 'CREDIT', accountFrom: 'railWallet', amountFrom: 'payable.functional', defaultRail: 'SEPA'],
                                ]
                        ],
                        'payroll.recognize' : [
                                legs: [
                                        [side: 'DEBIT', accountCode: '5200', amountFrom: 'payroll.gross'],
                                        [side: 'CREDIT', accountCode: '2200', amountFrom: 'payroll.net'],
                                        [side: 'CREDIT', accountCode: '2210', amountFrom: 'payroll.tax', optional: true],
                                ]
                        ],
                        'payroll.pay'       : [
                                legs: [
                                        [side: 'DEBIT', accountCode: '2200', amountFrom: 'payroll.net'],
                                        [side: 'CREDIT', accountFrom: 'railWallet', amountFrom: 'payroll.net', defaultRail: 'SEPA'],
                                ]
                        ],
                ],
                railWallet     : [
                        SEPA              : '1000',
                        BTC_ONCHAIN       : '1010',
                        BTC_LIGHTNING     : '1020',
                        BTC_WALLET_ADDRESS: '1010',
                        OTHER             : '1000',
                ],
        ]
    }

    /**
     * Ensure COA + posting rules exist for tenant. Idempotent.
     * When {@link CompanyConfigService} is unavailable (unit tests), returns the SBM pack without persisting.
     */
    Map ensureSeeded(String npubOwner) {
        String npub = requireNpub(npubOwner)
        if (financeLedgerService != null) {
            financeLedgerService.ensureChartSeeded(npub)
        }
        if (companyConfigService == null) {
            Map pack = defaultSbmPack()
            return [npubOwner: npub, created: false, ruleset: pack, mode: MODE_CASH, ephemeral: true]
        }
        boolean created = false
        Object existing = companyConfigService.getJson(npub, KEY_RULES, null)
        if (!(existing instanceof Map) || !(existing as Map).events) {
            Map pack = defaultSbmPack()
            companyConfigService.putJson(npub, KEY_RULES, pack, 'finance')
            companyConfigService.put(npub, KEY_MODE, MODE_CASH, 'finance', 'string')
            created = true
            log.info('Seeded posting ruleset for {}', npub)
        } else if (!companyConfigService.getString(npub, KEY_MODE, null)) {
            String mode = ((existing as Map).accountingMode as String) ?: MODE_CASH
            companyConfigService.put(npub, KEY_MODE, mode, 'finance', 'string')
        }
        [
                npubOwner: npub,
                created  : created,
                ruleset  : getRuleset(npub),
                mode     : getAccountingMode(npub),
        ]
    }

    /**
     * Force-replace rules with SBM defaults (Admin reseed). Does not delete journals.
     */
    Map reseedDefaults(String npubOwner) {
        String npub = requireNpub(npubOwner)
        if (financeLedgerService != null) {
            financeLedgerService.ensureChartSeeded(npub)
        }
        Map pack = defaultSbmPack()
        companyConfigService.putJson(npub, KEY_RULES, pack, 'finance')
        companyConfigService.put(npub, KEY_MODE, MODE_CASH, 'finance', 'string')
        [
                npubOwner: npub,
                reseeded : true,
                ruleset  : pack,
                mode     : MODE_CASH,
                message  : 'Posting rules reset to SBM defaults (journals untouched)',
        ]
    }

    Map getRuleset(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Object raw = companyConfigService?.getJson(npub, KEY_RULES, null)
        if (raw instanceof Map && (raw as Map).events) {
            Map m = new LinkedHashMap(raw as Map)
            if (!m.accountingMode) {
                m.accountingMode = getAccountingMode(npub)
            }
            return m
        }
        // Not seeded yet — return defaults without persisting (caller may ensureSeeded)
        defaultSbmPack()
    }

    String getAccountingMode(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String mode = companyConfigService?.getString(npub, KEY_MODE, null)
        if (mode in [MODE_CASH, MODE_ACCRUAL]) return mode
        Object rules = companyConfigService?.getJson(npub, KEY_RULES, null)
        if (rules instanceof Map) {
            String fromRules = (rules as Map).accountingMode as String
            if (fromRules in [MODE_CASH, MODE_ACCRUAL]) return fromRules
        }
        MODE_CASH
    }

    /**
     * Persist ruleset. Validates structure lightly (PR1); engine balance checks in PR2+.
     * Audit / IT ticket = later PR — still Admin-gated at controller.
     */
    Map saveRuleset(String npubOwner, Map body) {
        String npub = requireNpub(npubOwner)
        Map rules = body?.ruleset instanceof Map ? (body.ruleset as Map) :
                (body?.events ? body : null)
        if (!rules) {
            throw new IllegalArgumentException('ruleset object required')
        }
        validateStructure(rules)
        String mode = (body.accountingMode ?: rules.accountingMode ?: getAccountingMode(npub)) as String
        if (!(mode in [MODE_CASH, MODE_ACCRUAL])) {
            throw new IllegalArgumentException("accountingMode must be ${MODE_CASH} or ${MODE_ACCRUAL}")
        }
        rules = new LinkedHashMap(rules)
        rules.accountingMode = mode
        if (!rules.version) {
            rules.version = RULES_VERSION
        }
        companyConfigService.putJson(npub, KEY_RULES, rules, 'finance')
        companyConfigService.put(npub, KEY_MODE, mode, 'finance', 'string')
        [
                npubOwner: npub,
                saved    : true,
                ruleset  : getRuleset(npub),
                mode     : mode,
        ]
    }

    Map saveAccountingMode(String npubOwner, String mode) {
        String npub = requireNpub(npubOwner)
        String m = mode?.trim()?.toUpperCase()
        if (!(m in [MODE_CASH, MODE_ACCRUAL])) {
            throw new IllegalArgumentException("accountingMode must be ${MODE_CASH} or ${MODE_ACCRUAL}")
        }
        companyConfigService.put(npub, KEY_MODE, m, 'finance', 'string')
        Map rules = getRuleset(npub)
        if (rules && companyConfigService.getJson(npub, KEY_RULES, null) instanceof Map) {
            rules = new LinkedHashMap(rules)
            rules.accountingMode = m
            companyConfigService.putJson(npub, KEY_RULES, rules, 'finance')
        }
        [npubOwner: npub, mode: m, ruleset: getRuleset(npub)]
    }

    /** Structural validation only — does not require COA rows to exist. */
    void validateStructure(Map rules) {
        validateStructureStatic(rules)
    }

    /** Resolve wallet COA code for a payment rail from ruleset. */
    String walletAccountForRail(Map ruleset, String rail) {
        walletAccountForRailStatic(ruleset, rail)
    }

    static void validateStructureStatic(Map rules) {
        if (!(rules.events instanceof Map) || !(rules.events as Map)) {
            throw new IllegalArgumentException('ruleset.events required')
        }
        Map events = rules.events as Map
        ['invoice.settle', 'payable.pay', 'payroll.recognize', 'payroll.pay'].each { String key ->
            Object ev = events[key]
            if (ev == null) return // allow partial custom packs
            if (!(ev instanceof Map)) {
                throw new IllegalArgumentException("event ${key} must be an object")
            }
            Object legs = (ev as Map).legs
            if (!(legs instanceof List) || (legs as List).size() < 2) {
                throw new IllegalArgumentException("event ${key} needs at least 2 legs")
            }
            (legs as List).each { Object leg ->
                if (!(leg instanceof Map)) {
                    throw new IllegalArgumentException("legs must be objects in ${key}")
                }
                Map L = leg as Map
                if (!(L.side in ['DEBIT', 'CREDIT'])) {
                    throw new IllegalArgumentException("leg.side must be DEBIT or CREDIT (${key})")
                }
                boolean hasCode = !!L.accountCode?.toString()?.trim()
                boolean hasFrom = !!L.accountFrom?.toString()?.trim()
                if (!hasCode && !hasFrom) {
                    throw new IllegalArgumentException("leg needs accountCode or accountFrom (${key})")
                }
            }
        }
    }

    static String walletAccountForRailStatic(Map ruleset, String rail) {
        Map rw = (ruleset?.railWallet instanceof Map) ? (ruleset.railWallet as Map) : (defaultSbmPack().railWallet as Map)
        String r = (rail ?: 'SEPA').toString()
        (rw[r] ?: rw.OTHER ?: '1000') as String
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
