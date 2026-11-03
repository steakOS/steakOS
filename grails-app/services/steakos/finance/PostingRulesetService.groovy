// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.finance

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import steakos.configuration.CompanyConfigService

/**
 * PDR-027 — company posting ruleset (CompanySetting JSON) + SBM default pack.
 * PR2–PR3 {@link PostingEngineService} posts settle/pay/payroll.recognize/payroll.pay from these rules.
 * PR4–PR5 Admin save/reseed appends change log + opens a Digital IT ticket (D6).
 * PR7 — invoice.issue / payable.create live under ACCRUAL; settle/pay use legsAccrual to clear AR/AP.
 */
@Slf4j
@Transactional
class PostingRulesetService {

    static final String KEY_RULES = 'finance.postingRules'
    static final String KEY_MODE = 'finance.accountingMode'
    static final String KEY_CHANGE_LOG = 'finance.postingRules.changeLog'
    static final String MODE_CASH = 'CASH'
    static final String MODE_ACCRUAL = 'ACCRUAL'
    static final int RULES_VERSION = 1
    static final int CHANGE_LOG_MAX = 50

    /** Events the engine may post (cash + accrual document legs). */
    static final List<String> LIVE_EVENTS = [
            'invoice.settle', 'payable.pay', 'payroll.recognize', 'payroll.pay',
            'invoice.issue', 'payable.create'
    ].asImmutable()

    /**
     * ACCRUAL document events (issue/create). Empty stub list — PR7 cleared stub flags;
     * kept for API compatibility ({@code accrualStubEvents} → []).
     */
    static final List<String> ACCRUAL_STUB_EVENTS = [].asImmutable()

    /** Events posted only when accountingMode=ACCRUAL (document recognition). */
    static final List<String> ACCRUAL_DOCUMENT_EVENTS = [
            'invoice.issue', 'payable.create'
    ].asImmutable()

    CompanyConfigService companyConfigService
    FinanceLedgerService financeLedgerService
    steakos.digital.DigSupportService digSupportService

    /**
     * Default SBM / restaurant pack.
     * Cash settle/pay legs credit revenue / debit expense; {@code legsAccrual} clear AR/AP (1100/2000).
     * invoice.issue / payable.create are live (no stub) for ACCRUAL document posts.
     */
    static Map defaultSbmPack() {
        [
                version        : RULES_VERSION,
                accountingMode : MODE_CASH,
                events         : [
                        'invoice.settle'    : [
                                legs: [
                                        [side: 'DEBIT', accountFrom: 'railWallet', amountFrom: 'payment.functional'],
                                        [side: 'CREDIT', accountCode: '4000', amountFrom: 'payment.functional'],
                                ],
                                // ACCRUAL: cash in, clear AR — do not re-credit revenue
                                legsAccrual: [
                                        [side: 'DEBIT', accountFrom: 'railWallet', amountFrom: 'payment.functional'],
                                        [side: 'CREDIT', accountCode: '1100', amountFrom: 'payment.functional'],
                                ],
                        ],
                        'payable.pay'       : [
                                legs: [
                                        [side: 'DEBIT', accountCode: '5000', amountFrom: 'payable.functional'],
                                        [side: 'CREDIT', accountFrom: 'railWallet', amountFrom: 'payable.functional', defaultRail: 'SEPA'],
                                ],
                                // ACCRUAL: clear AP, cash out — do not re-debit expense
                                legsAccrual: [
                                        [side: 'DEBIT', accountCode: '2000', amountFrom: 'payable.functional'],
                                        [side: 'CREDIT', accountFrom: 'railWallet', amountFrom: 'payable.functional', defaultRail: 'SEPA'],
                                ],
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
                        'invoice.issue'     : [
                                legs: [
                                        [side: 'DEBIT', accountCode: '1100', amountFrom: 'invoice.functional'],
                                        [side: 'CREDIT', accountCode: '4000', amountFrom: 'invoice.functional'],
                                ]
                        ],
                        'payable.create'    : [
                                legs: [
                                        [side: 'DEBIT', accountCode: '5000', amountFrom: 'payable.functional'],
                                        [side: 'CREDIT', accountCode: '2000', amountFrom: 'payable.functional'],
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
        boolean merged = false
        Object existing = companyConfigService.getJson(npub, KEY_RULES, null)
        if (!(existing instanceof Map) || !(existing as Map).events) {
            Map pack = defaultSbmPack()
            companyConfigService.putJson(npub, KEY_RULES, pack, 'finance')
            companyConfigService.put(npub, KEY_MODE, MODE_CASH, 'finance', 'string')
            created = true
            log.info('Seeded posting ruleset for {}', npub)
        } else {
            Map cur = new LinkedHashMap(existing as Map)
            Map events = cur.events instanceof Map ? new LinkedHashMap(cur.events as Map) : [:]
            Map defaults = defaultSbmPack()
            // PR7: ensure document events exist, clear legacy stub flags, merge legsAccrual on settle/pay
            ACCRUAL_DOCUMENT_EVENTS.each { String key ->
                if (!events.containsKey(key) && defaults.events[key]) {
                    events[key] = defaults.events[key]
                    merged = true
                } else if (events[key] instanceof Map && (events[key] as Map).stub) {
                    Map ev = new LinkedHashMap(events[key] as Map)
                    ev.remove('stub')
                    if (!(ev.legs instanceof List) && defaults.events[key]?.legs) {
                        ev.legs = defaults.events[key].legs
                    }
                    events[key] = ev
                    merged = true
                }
            }
            ['invoice.settle', 'payable.pay'].each { String key ->
                Map defEv = defaults.events[key] as Map
                if (!(events[key] instanceof Map) || !(defEv?.legsAccrual instanceof List)) {
                    return
                }
                Map ev = new LinkedHashMap(events[key] as Map)
                if (!(ev.legsAccrual instanceof List)) {
                    ev.legsAccrual = defEv.legsAccrual
                    events[key] = ev
                    merged = true
                }
            }
            if (merged) {
                cur.events = events
                companyConfigService.putJson(npub, KEY_RULES, cur, 'finance')
                log.info('Merged ACCRUAL document / legsAccrual into posting ruleset for {}', npub)
            }
            if (!companyConfigService.getString(npub, KEY_MODE, null)) {
                String mode = (cur.accountingMode as String) ?: MODE_CASH
                companyConfigService.put(npub, KEY_MODE, mode, 'finance', 'string')
            }
        }
        [
                npubOwner: npub,
                created  : created,
                merged   : merged,
                ruleset  : getRuleset(npub),
                mode     : getAccountingMode(npub),
        ]
    }

    /**
     * Force-replace rules with SBM defaults (Admin reseed). Does not delete journals.
     * @param meta optional actor / reason for change log + IT ticket
     */
    Map reseedDefaults(String npubOwner, Map meta = [:]) {
        String npub = requireNpub(npubOwner)
        if (financeLedgerService != null) {
            financeLedgerService.ensureChartSeeded(npub)
        }
        Map before = snapshotRules(npub)
        Map pack = defaultSbmPack()
        companyConfigService.putJson(npub, KEY_RULES, pack, 'finance')
        companyConfigService.put(npub, KEY_MODE, MODE_CASH, 'finance', 'string')
        Map audit = recordPolicyChange(npub, 'reseed', before, pack, MODE_CASH, meta)
        [
                npubOwner: npub,
                reseeded : true,
                ruleset  : pack,
                mode     : MODE_CASH,
                message  : 'Posting rules reset to SBM defaults (journals untouched)',
                changeLogId: audit?.id,
                itTicketId : audit?.itTicketId,
                itTicketNumber: audit?.itTicketNumber,
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
     * Persist ruleset. Validates structure lightly; engine balance checks at post time.
     * Appends change log + opens IT ticket (D6). Admin-gated at controller.
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
        Map before = snapshotRules(npub)
        rules = new LinkedHashMap(rules)
        rules.accountingMode = mode
        if (!rules.version) {
            rules.version = RULES_VERSION
        }
        companyConfigService.putJson(npub, KEY_RULES, rules, 'finance')
        companyConfigService.put(npub, KEY_MODE, mode, 'finance', 'string')
        Map audit = recordPolicyChange(npub, 'save', before, rules, mode, body ?: [:])
        [
                npubOwner: npub,
                saved    : true,
                ruleset  : getRuleset(npub),
                mode     : mode,
                changeLogId: audit?.id,
                itTicketId : audit?.itTicketId,
                itTicketNumber: audit?.itTicketNumber,
        ]
    }

    Map saveAccountingMode(String npubOwner, String mode, Map meta = [:]) {
        String npub = requireNpub(npubOwner)
        String m = mode?.trim()?.toUpperCase()
        if (!(m in [MODE_CASH, MODE_ACCRUAL])) {
            throw new IllegalArgumentException("accountingMode must be ${MODE_CASH} or ${MODE_ACCRUAL}")
        }
        Map before = snapshotRules(npub)
        companyConfigService.put(npub, KEY_MODE, m, 'finance', 'string')
        Map rules = getRuleset(npub)
        if (rules && companyConfigService.getJson(npub, KEY_RULES, null) instanceof Map) {
            rules = new LinkedHashMap(rules)
            rules.accountingMode = m
            companyConfigService.putJson(npub, KEY_RULES, rules, 'finance')
        }
        Map after = snapshotRules(npub)
        Map audit = recordPolicyChange(npub, 'mode', before, after.ruleset ?: after, m, meta)
        [npubOwner: npub, mode: m, ruleset: getRuleset(npub),
         changeLogId: audit?.id, itTicketId: audit?.itTicketId, itTicketNumber: audit?.itTicketNumber]
    }

    /** Newest-first change log (capped). */
    List listChangeLog(String npubOwner, int max = 20) {
        String npub = requireNpub(npubOwner)
        Object raw = companyConfigService?.getJson(npub, KEY_CHANGE_LOG, null)
        List entries = (raw instanceof List) ? (raw as List) : []
        int limit = Math.max(1, Math.min(max, CHANGE_LOG_MAX))
        entries.take(limit)
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
        LIVE_EVENTS.each { String key ->
            Object ev = events[key]
            if (ev == null) return // allow partial custom packs
            if (!(ev instanceof Map)) {
                throw new IllegalArgumentException("event ${key} must be an object")
            }
            Map evMap = ev as Map
            validateLegsList(evMap.legs, key)
            if (evMap.legsAccrual != null) {
                validateLegsList(evMap.legsAccrual, "${key}.legsAccrual")
            }
        }
    }

    private static void validateLegsList(Object legs, String label) {
        if (!(legs instanceof List) || (legs as List).size() < 2) {
            throw new IllegalArgumentException("event ${label} needs at least 2 legs")
        }
        (legs as List).each { Object leg ->
            if (!(leg instanceof Map)) {
                throw new IllegalArgumentException("legs must be objects in ${label}")
            }
            Map L = leg as Map
            if (!(L.side in ['DEBIT', 'CREDIT'])) {
                throw new IllegalArgumentException("leg.side must be DEBIT or CREDIT (${label})")
            }
            boolean hasCode = !!L.accountCode?.toString()?.trim()
            boolean hasFrom = !!L.accountFrom?.toString()?.trim()
            if (!hasCode && !hasFrom) {
                throw new IllegalArgumentException("leg needs accountCode or accountFrom (${label})")
            }
        }
    }

    static String walletAccountForRailStatic(Map ruleset, String rail) {
        Map rw = (ruleset?.railWallet instanceof Map) ? (ruleset.railWallet as Map) : (defaultSbmPack().railWallet as Map)
        String r = (rail ?: 'SEPA').toString()
        (rw[r] ?: rw.OTHER ?: '1000') as String
    }

    private Map snapshotRules(String npub) {
        [
                accountingMode: getAccountingMode(npub),
                ruleset       : getRuleset(npub),
        ]
    }

    /**
     * Append-only change log + Digital IT ticket (REQUEST/CHANGE with [Finance policy] prefix).
     * Soft-fails ticket open so a Dig outage never blocks Admin save.
     */
    private Map recordPolicyChange(String npub, String action, Object before, Object after,
                                   String mode, Map meta) {
        if (companyConfigService == null) {
            return [id: null, skipped: true]
        }
        String actor = (meta?.actor ?: meta?.username ?: 'admin') as String
        String reason = (meta?.reason ?: '')?.toString()?.trim()
        String id = "prchg-${System.currentTimeMillis()}-${Math.abs((npub + action).hashCode())}"
        String at = java.time.OffsetDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        Map entry = [
                id            : id,
                at            : at,
                action        : action,
                actor         : actor,
                accountingMode: mode,
                reason        : reason ?: null,
                beforeMode    : (before instanceof Map) ? (before as Map).accountingMode : null,
                afterMode     : mode,
                summary       : summarizeDiff(before, after, action, mode),
        ]
        Object raw = companyConfigService.getJson(npub, KEY_CHANGE_LOG, null)
        List entries = (raw instanceof List) ? new ArrayList(raw as List) : []
        entries.add(0, entry)
        if (entries.size() > CHANGE_LOG_MAX) {
            entries = entries.take(CHANGE_LOG_MAX)
        }
        companyConfigService.putJson(npub, KEY_CHANGE_LOG, entries, 'finance')

        Long ticketId = null
        String ticketNumber = null
        if (digSupportService != null) {
            try {
                String ticketName = "[Finance policy] Posting rules ${action} (${mode})"
                String desc = """Posting ruleset policy change (PDR-027 / D6).

Action: ${action}
Actor: ${actor}
Mode: ${mode}
Change log id: ${id}
${reason ? "Reason: ${reason}\n" : ''}
Summary: ${entry.summary}

Before / after JSON retained in company setting ${KEY_CHANGE_LOG}.
Journals were not modified."""
                def ticket = digSupportService.openTicket(npub, [
                        name       : ticketName,
                        description: desc,
                        ticketKind : 'CHANGE',
                        priority   : 'NORMAL',
                ])
                ticketId = ticket?.id as Long
                ticketNumber = ticket?.ticketNumber as String
                entry.itTicketId = ticketId
                entry.itTicketNumber = ticketNumber
                entries[0] = entry
                companyConfigService.putJson(npub, KEY_CHANGE_LOG, entries, 'finance')
            } catch (Exception e) {
                log.warn('Posting rules IT ticket failed for {}: {}', npub, e.message)
            }
        }
        [id: id, itTicketId: ticketId, itTicketNumber: ticketNumber]
    }

    private static String summarizeDiff(Object before, Object after, String action, String mode) {
        if (action == 'reseed') {
            return "Reseeded SBM defaults; mode=${mode}"
        }
        String beforeMode = (before instanceof Map) ? ((before as Map).accountingMode ?: '') : ''
        if (action == 'mode' || (beforeMode && beforeMode != mode)) {
            return "Accounting mode ${beforeMode ?: '?'} → ${mode}"
        }
        try {
            String b = JsonOutput.toJson(before instanceof Map && (before as Map).ruleset ?
                    (before as Map).ruleset : before)
            String a = JsonOutput.toJson(after instanceof Map && (after as Map).events ?
                    after : ((after instanceof Map) ? (after as Map).ruleset ?: after : after))
            if (b == a) {
                return "Saved ruleset (no structural JSON change); mode=${mode}"
            }
            return "Updated posting ruleset JSON; mode=${mode}"
        } catch (Exception ignored) {
            return "Updated posting rules; mode=${mode}"
        }
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
