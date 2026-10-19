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

package steakos.configuration

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import steakos.User
import steakos.finance.Account
import steakos.finance.FinanceLedgerService
import steakos.finance.FinanceTreasuryService
import steakos.generic.ObjectContent
import steakos.capability.CapProcessService

/**
 * Config wizard apply / complete / reset (ADR-026, OI-W01–W03).
 */
@Slf4j
@Transactional
class SetupService {

    FinanceLedgerService financeLedgerService
    FinanceTreasuryService financeTreasuryService
    CompanyConfigService companyConfigService
    CapProcessService capProcessService
    CompanyContextService companyContextService
    CompanyCurrencyService companyCurrencyService
    CompanyTaxService companyTaxService
    steakos.payments.BtcIntegrationLevelService btcIntegrationLevelService
    steakos.nostr.NostrIntegrationService nostrIntegrationService
    steakos.nostr.NostrPublishService nostrPublishService

    static final String DEFAULT_NPUB = 'user:admin'
    /** Bumped for Silver depth + messaging payload (plan-wizard-versioning P2). */
    static final int WIZARD_VERSION = 2

    /** CompanySetting: JSON map {sales,finance,…} → BASICS|ADVANCED|PRO */
    static final String KEY_PLUGIN_DEPTH = 'template.pluginDepth'
    /** CompanySetting: outbound email adapter opt-in (default false). */
    static final String KEY_MESSAGING_EMAIL = 'messaging.emailEnabled'
    /** CompanySetting: Telegram notifies opt-in (default false). */
    static final String KEY_MESSAGING_TELEGRAM = 'messaging.telegramEnabled'
    /**
     * Nested secret / config keys (Integrations only — never written by wizard draft plaintext):
     * messaging.email.smtpHost, messaging.email.smtpPort, messaging.email.username,
     * messaging.email.password (encrypted), messaging.email.fromAddress;
     * messaging.telegram.botToken (encrypted), messaging.telegram.chatId, messaging.telegram.apiBase.
     */

    SetupState ensureState(String npubOwner) {
        String npub = resolveNpub(npubOwner)
        SetupState state = SetupState.findByNpubOwner(npub)
        if (state) return state

        // Legacy golden installs: already have COA → treat as complete Iron
        boolean legacy = Account.countByNpubOwner(npub) > 0
        state = new SetupState(
                npubOwner     : npub,
                setupComplete : legacy,
                templateTier  : TemplateTiers.DEFAULT_OPERATOR_TIER,
                draftStep     : 0,
                wizardVersion : WIZARD_VERSION,
                notes         : legacy ? 'legacy-bootstrap-complete' : null
        )
        if (legacy) {
            state.completedAt = new Date()
            state.completedBy = 'SYSTEM'
            companyConfigService.put(npub, 'template.tier', TemplateTiers.DEFAULT_OPERATOR_TIER, 'template', 'string')
            companyConfigService.putJson(npub, 'template.enabledFamilies',
                    TemplateTiers.defaultFamilies(TemplateTiers.DEFAULT_OPERATOR_TIER), 'template')
        }
        state.save(failOnError: true)
        // Best-effort SYSTEM process seed for legacy-complete tenants
        if (legacy) {
            try {
                if (capProcessService != null) {
                    capProcessService.seedSystemProcesses(npub)
                }
            } catch (Exception ignored) {
            }
        }
        state
    }

    Map statusMap(String npubOwner) {
        SetupState s = ensureState(npubOwner)
        String npub = s.npubOwner
        String tier = companyConfigService.getString(npub, 'template.tier', s.templateTier ?: TemplateTiers.DEFAULT_OPERATOR_TIER)
        [
                setupComplete  : s.setupComplete,
                templateTier   : s.templateTier,
                draftStep      : s.draftStep,
                wizardVersion  : s.wizardVersion ?: WIZARD_VERSION,
                completedAt    : s.completedAt?.time,
                completedBy    : s.completedBy,
                enabledFamilies: companyConfigService.enabledFamilies(npub),
                // Silver config (exposed even while Silver card locked — for future wizard)
                pluginDepth    : readPluginDepth(npub, tier),
                messaging      : readMessagingFlags(npub),
                tiers          : TemplateTiers.catalog(),
                npubOwner      : npub,
                // Bronze BR-R6 — privacy ack when HR (human) is in pack
                privacy        : [
                        hrAcknowledged: companyConfigService.getBoolean(npub, 'privacy.hrAcknowledged', false),
                        hrAcknowledgedAt: companyConfigService.getString(npub, 'privacy.hrAcknowledgedAt', null),
                        hrAcknowledgedBy: companyConfigService.getString(npub, 'privacy.hrAcknowledgedBy', null)
                ]
        ]
    }

    /**
     * Bronze BR-R6: operator acknowledges HR personal-data duties.
     */
    Map acknowledgeHrPrivacy(String npubOwner, String actor) {
        String npub = resolveNpub(npubOwner)
        String when = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").with {
            timeZone = TimeZone.getTimeZone('UTC')
            format(new Date())
        }
        companyConfigService.put(npub, 'privacy.hrAcknowledged', 'true', 'privacy', 'boolean')
        companyConfigService.put(npub, 'privacy.hrAcknowledgedAt', when, 'privacy', 'string')
        companyConfigService.put(npub, 'privacy.hrAcknowledgedBy', (actor ?: 'operator') as String, 'privacy', 'string')
        statusMap(npub)
    }

    Map getDraft(String npubOwner) {
        SetupState s = ensureState(npubOwner)
        Map draft = [:]
        if (s.draftJson) {
            try {
                draft = (Map) new JsonSlurper().parseText(s.draftJson)
            } catch (Exception ignored) {
                draft = [:]
            }
        }
        [draftStep: s.draftStep, draft: draft, setupComplete: s.setupComplete]
    }

    SetupState saveDraft(String npubOwner, Integer step, Map draft) {
        SetupState s = ensureState(npubOwner)
        if (s.setupComplete) {
            throw new IllegalStateException('Setup already complete — use Settings or admin reset')
        }
        if (step != null) s.draftStep = step
        s.draftJson = draft != null ? JsonOutput.toJson(draft) : s.draftJson
        s.save(failOnError: true)
        s
    }

    /**
     * Apply draft: LegalEntity, Site, BankAccount, settings, optional COA seed.
     */
    Map apply(String npubOwner, Map draft, String actor) {
        String npub = resolveNpub(npubOwner)
        SetupState s = ensureState(npub)
        if (s.setupComplete) {
            throw new IllegalStateException('Setup already complete — reset first to re-apply')
        }
        Map d = draft ?: [:]
        // IR-02: only productized tiers may be applied (today: Bronze only).
        // Explicit closed operator metals → hard 400 (clearer than silent Bronze fallback).
        String requestedTier = d.templateTier as String
        if (requestedTier?.trim()) {
            String normalizedReq = TemplateTiers.normalize(requestedTier)
            if (TemplateTiers.isOperatorFacing(normalizedReq) && !TemplateTiers.isOpenForMetalSelection(normalizedReq)) {
                throw new IllegalArgumentException("metal not open for selection: ${normalizedReq}")
            }
        }
        String tier = TemplateTiers.normalizeMetalSelection(requestedTier ?: s.templateTier)

        LegalEntity le = LegalEntity.findByNpubOwner(npub)
        if (!le) {
            le = new LegalEntity(npubOwner: npub)
        }
        le.name = (d.companyName as String)?.trim() ?: le.name ?: 'My Company'
        // PDR-026 — operator company is LIVE primary
        if (!le.code) {
            le.code = LegalEntity.CODE_PRIMARY
        }
        le.kind = LegalEntity.KIND_LIVE
        le.legalForm = d.legalForm as String
        le.taxId = d.taxId as String
        le.countryCode = (d.countryCode as String) ?: 'DE'
        // PDR-023: primary value rail = functional currency (T1)
        Map money = TenantMoneyService.normalizeDraft(d)
        String rail = money.primaryValueRail as String
        le.functionalCurrency = money.functionalCurrency as String
        le.dualSats = Boolean.FALSE // hints replace dual persist on documents
        if (rail == 'BTC') {
            String unit = money.bitcoinDisplayUnit as String
            String fiat = money.displayFiat as String
            if (!unit || !fiat) {
                throw new IllegalArgumentException('bitcoinDisplayUnit and displayFiat required when primaryValueRail=BTC')
            }
        }
        le.registeredAddressText = d.address as String
        le.description = d.companyDescription as String
        le.markCreatedBy(actor ?: npub)
        le.markUpdatedBy(actor ?: npub)
        le.save(failOnError: true)

        try {
            companyContextService?.ensureBootstrapped()
        } catch (ignored) {
        }

        // New/configured company becomes the actor's active company (Settings preference).
        try {
            User actorUser = User.findByUsername(actor ?: 'admin')
            if (actorUser && le) {
                companyContextService?.ensureMembership(actorUser, le, true)
                companyContextService?.persistPreferredCompany(actorUser, le.id as Long)
            }
        } catch (ignored) {
        }

        String siteName = (d.siteName as String)?.trim() ?: 'Main'
        Site site = Site.findByNpubOwnerAndName(npub, siteName)
        if (!site) {
            site = new Site(npubOwner: npub, name: siteName, legalEntity: le)
        }
        site.legalEntity = le
        site.siteType = (d.siteType as String) ?: 'HQ'
        site.addressText = d.siteAddress as String ?: d.address as String
        site.active = Boolean.TRUE
        site.markCreatedBy(actor ?: npub)
        site.save(failOnError: true)

        // OI-BTC01 D1: banking is fiat only — do not treat btcpayUrl as "connected"
        boolean hasBank = d.bankName || d.iban || d.bic || d.accountNumber || d.routingNumber ||
                d.branchName || d.paymentScheme
        if (hasBank) {
            String bankName = (d.bankName as String)?.trim() ?: 'Primary'
            BankAccount ba = BankAccount.findByNpubOwnerAndName(npub, bankName)
            if (!ba) {
                ba = new BankAccount(npubOwner: npub, name: bankName, legalEntity: le)
            }
            ba.legalEntity = le
            ba.accountKind = (d.accountKind as String) ?: 'BANK'
            String scheme = (d.paymentScheme as String)?.trim()?.toUpperCase()
            if (!(scheme in ['SEPA', 'ACH'])) {
                // Default from company country when operator did not pick a scheme
                String cc = (le.countryCode ?: d.countryCode ?: '').toString().trim().toUpperCase()
                scheme = (cc == 'US' || cc == 'USA') ? 'ACH' : 'SEPA'
            }
            ba.paymentScheme = scheme
            ba.iban = d.iban as String
            ba.bic = d.bic as String
            ba.branchName = d.branchName as String
            ba.accountNumber = d.accountNumber as String
            ba.routingNumber = d.routingNumber as String
            // Fiat bank rail: use displayFiat when books are BTC, else functional fiat
            ba.currencyCode = rail == 'BTC' ? (money.displayFiat as String) : le.functionalCurrency
            ba.active = Boolean.TRUE
            ba.markCreatedBy(actor ?: npub)
            ba.save(failOnError: true)
        }

        // OI-BTC01: Bitcoin integration level (wizard step after banking)
        Map btcLevel = [:]
        try {
            if (btcIntegrationLevelService != null) {
                btcLevel = btcIntegrationLevelService.applyFromWizard(npub, d, actor)
            }
        } catch (IllegalArgumentException e) {
            throw e
        } catch (Exception e) {
            // non-fatal only when level omitted; invalid Level 2 address must fail apply
            if (d.btcIntegrationLevel || d.btcReceiveAddress || d.btcpayBaseUrl || d.btcpayApiKey) {
                throw e
            }
        }

        // OI-NOSTR01: Nostr step after Bitcoin (micro-decision: banking → Bitcoin → Nostr)
        Map nostrLevel = [:]
        try {
            if (nostrIntegrationService != null) {
                nostrLevel = nostrIntegrationService.applyFromWizard(npub, d, actor)
            }
        } catch (IllegalArgumentException e) {
            throw e
        } catch (Exception e) {
            if (d.nostrIntegrationLevel || d.nostrOperatorNpub || d.nostrRelayUrl) {
                throw e
            }
        }

        List<String> families
        if (d.enabledFamilies instanceof List) {
            families = TemplateTiers.sanitizeEnabledFamilies(tier, d.enabledFamilies as List)
        } else {
            families = TemplateTiers.defaultFamilies(tier)
        }
        companyConfigService.put(npub, 'template.tier', tier, 'template', 'string')
        companyConfigService.putJson(npub, 'template.enabledFamilies', families, 'template')
        // Silver config keys (defaults: Advanced when Silver pack; messaging off)
        Map depthRaw = extractPluginDepthFromDraft(d)
        Map<String, String> pluginDepth = TemplateTiers.sanitizePluginDepth(tier, depthRaw)
        persistPluginDepth(npub, pluginDepth)
        Map messagingFlags = extractMessagingFromDraft(d)
        persistMessagingFlags(npub, messagingFlags)
        companyConfigService.put(npub, 'finance.primaryValueRail', rail, 'finance', 'string')
        companyConfigService.put(npub, 'finance.functionalCurrency', le.functionalCurrency, 'finance', 'string')
        companyConfigService.put(npub, 'finance.dualSats', 'false', 'finance', 'boolean')
        if (rail == 'BTC') {
            companyConfigService.put(npub, 'finance.bitcoinDisplayUnit', money.bitcoinDisplayUnit as String, 'finance', 'string')
            companyConfigService.put(npub, 'finance.displayFiat', money.displayFiat as String, 'finance', 'string')
        }
        if (d.defaultTaxRate != null) {
            companyConfigService.put(npub, 'finance.default_tax_rate', d.defaultTaxRate.toString(), 'finance', 'number')
        }

        // OI-F23 D3/D7 — tax regime from wizard or LegalEntity country
        try {
            if (companyTaxService != null) {
                String regime = (d.taxRegime as String)?.trim()
                if (!regime) {
                    regime = CompanyTaxService.regimeForCountry(le.countryCode)
                }
                if (d.kleinunternehmer == true || d.kleinunternehmer == Boolean.TRUE ||
                        d.smallBusiness == true || d.smallBusiness == Boolean.TRUE) {
                    regime = CompanyTaxService.REGIME_DE_SMALL
                }
                companyTaxService.applyRegimeSettings(npub, regime, d.defaultTaxCode as String)
            }
        } catch (Exception e) {
            log.warn("tax regime apply skipped: ${e.message}")
        }

        // OI-CUR01 D7: auto-enable functional (+ displayFiat if distinct)
        try {
            companyCurrencyService?.ensureCompanyDefaults(npub)
        } catch (Exception ignored) {
        }

        if (d.locale) {
            UserPreference pref = UserPreference.findByUsernameAndPrefKey(actor ?: 'admin', 'ui.locale')
            if (!pref) {
                pref = new UserPreference(username: actor ?: 'admin', prefKey: 'ui.locale')
            }
            pref.prefValue = d.locale as String
            pref.category = 'ui'
            pref.lastUpdatedBy = actor
            pref.save(failOnError: true)
        }

        boolean seedCoa = d.seedCoa == null || Boolean.valueOf(d.seedCoa as String)
        List coa = []
        int treasuryCount = 0
        if (seedCoa && financeLedgerService != null) {
            coa = financeLedgerService.ensureChartSeeded(npub)
            // PDR-023 T2: dual treasury (fiat + BTC/LN) under one COA
            if (financeTreasuryService != null) {
                try {
                    treasuryCount = financeTreasuryService.ensureDefaultPositions(npub)?.size() ?: 0
                } catch (Exception e) {
                    // non-fatal for setup
                }
            }
        }

        s.templateTier = tier
        s.draftJson = JsonOutput.toJson(d)
        s.draftStep = (d.step as Integer) ?: s.draftStep
        s.save(failOnError: true)

        [
                legalEntityId : le.id,
                siteId        : site.id,
                templateTier  : tier,
                families      : families,
                pluginDepth   : pluginDepth,
                messaging     : readMessagingFlags(npub),
                coaCount      : coa?.size() ?: 0,
                treasuryCount : treasuryCount,
                btcIntegration: btcLevel ?: null,
                nostrIntegration: nostrLevel ?: null
        ]
    }

    SetupState complete(String npubOwner, String actor) {
        String npub = resolveNpub(npubOwner)
        SetupState s = ensureState(npub)
        if (s.setupComplete) return s

        // Require company name applied
        LegalEntity le = LegalEntity.findByNpubOwner(npub)
        if (!le?.name) {
            throw new IllegalStateException('Apply company identity before completing setup')
        }

        s.setupComplete = Boolean.TRUE
        s.completedAt = new Date()
        s.completedBy = actor ?: 'admin'
        s.save(failOnError: true)

        // OI-NOSTR01: dual-write setup-complete audit when Level ≥ 1 (stub-safe)
        try {
            nostrPublishService?.publishAuditEvent(npub, "setup.complete by ${actor ?: 'admin'}", [
                    event: 'setup.complete',
                    templateTier: s.templateTier
            ])
        } catch (Exception ignored) {
        }

        // OI-CUR01 D7: ensure company currency in-use on complete
        try {
            companyCurrencyService?.ensureCompanyDefaults(npub)
        } catch (Exception ignored) {
        }

        // SYSTEM process catalogue (PROCESS-CATALOG D4)
        try {
            if (capProcessService != null) {
                capProcessService.seedSystemProcesses(npub)
            }
        } catch (Exception e) {
            // non-fatal for setup completion
        }

        // Birth certificate companion note
        try {
            if (!ObjectContent.findByNpubOwnerAndHostPackageAndHostResourceAndTitle(
                    'SYSTEM', 'system', 'Tenant', "Setup complete — ${s.templateTier}")) {
                new ObjectContent(
                        name            : "Setup ${s.templateTier}",
                        npubOwner       : 'SYSTEM',
                        hostPackage     : 'system',
                        hostResource    : 'Tenant',
                        hostId          : le.id ?: 1L,
                        channel         : 'BODY',
                        contentTypeCode : 'Note',
                        title           : "Setup complete — ${s.templateTier}",
                        bodyFormat      : 'MARKDOWN',
                        bodyText        : "Tenant setup finished as **${s.templateTier}** on ${s.completedAt}.",
                        pinned          : Boolean.TRUE
                ).save(failOnError: false)
            }
        } catch (Exception ignored) {
            // non-fatal
        }
        s
    }

    /**
     * Switch template tier + default families for an existing tenant (Copper pack E2).
     * Only metals open for metal selection are accepted (OI-A07 / metal selection honesty).
     * Does not wipe seed data or reopen the wizard.
     * When Silver opens: upgrades write Advanced pluginDepth + messaging defaults (off).
     *
     * @param options optional map: pluginDepth (Map), messaging (Map with emailEnabled/telegramEnabled)
     */
    Map setTemplateTier(String npubOwner, String tierCode, List enabledFamiliesOverride = null, Map options = null) {
        String npub = resolveNpub(npubOwner)
        // Reject closed metals before normalize fallback (explicit SILVER while locked → 400)
        if (tierCode?.trim()) {
            String normalizedReq = TemplateTiers.normalize(tierCode)
            if (TemplateTiers.isOperatorFacing(normalizedReq) && !TemplateTiers.isOpenForMetalSelection(normalizedReq)) {
                throw new IllegalArgumentException("metal not open for selection: ${normalizedReq}")
            }
        }
        String tier = TemplateTiers.normalizeMetalSelection(tierCode)
        if (!TemplateTiers.isOpenForMetalSelection(tier)) {
            throw new IllegalArgumentException("metal not open for selection: ${tierCode}")
        }
        List<String> families
        if (enabledFamiliesOverride instanceof List && enabledFamiliesOverride) {
            families = TemplateTiers.sanitizeEnabledFamilies(tier, enabledFamiliesOverride)
        } else {
            families = TemplateTiers.defaultFamilies(tier)
        }
        // Bronze BR-R6: enabling human requires privacy ack first
        if (families.contains('human') && !companyConfigService.getBoolean(npub, 'privacy.hrAcknowledged', false)) {
            throw new IllegalArgumentException(
                    'HR privacy acknowledgement required before enabling people data (POST /api/setup/privacy/hr-ack)')
        }
        companyConfigService.put(npub, 'template.tier', tier, 'template', 'string')
        companyConfigService.putJson(npub, 'template.enabledFamilies', families, 'template')
        // Depth: use override when provided, else pack defaults (Silver → Advanced)
        Map depthRaw = options?.pluginDepth instanceof Map ? (options.pluginDepth as Map) : null
        persistPluginDepth(npub, TemplateTiers.sanitizePluginDepth(tier, depthRaw))
        // Messaging: override when provided; else keep existing or default false
        if (options?.messaging instanceof Map) {
            persistMessagingFlags(npub, options.messaging as Map)
        } else if (companyConfigService.getString(npub, KEY_MESSAGING_EMAIL, null) == null) {
            persistMessagingFlags(npub, [emailEnabled: false, telegramEnabled: false])
        }
        SetupState s = ensureState(npub)
        s.templateTier = tier
        s.save(failOnError: true)
        statusMap(npub)
    }

    /**
     * Settings → Pack options: update messaging toggles (and optional pluginDepth) without
     * changing template tier or requiring metal-open. Silver+ depth stays pack-mandatory Advanced.
     *
     * @param options map: messaging {emailEnabled, telegramEnabled}, optional pluginDepth
     */
    Map setPackOptions(String npubOwner, Map options) {
        String npub = resolveNpub(npubOwner)
        SetupState s = ensureState(npub)
        String tier = companyConfigService.getString(npub, 'template.tier', s.templateTier ?: TemplateTiers.DEFAULT_OPERATOR_TIER)
        tier = TemplateTiers.normalize(tier)

        if (options?.messaging instanceof Map) {
            persistMessagingFlags(npub, options.messaging as Map)
        } else if (options != null && (options.containsKey('emailEnabled') || options.containsKey('telegramEnabled'))) {
            persistMessagingFlags(npub, options)
        }

        Map depthRaw = options?.pluginDepth instanceof Map ? (options.pluginDepth as Map) : null
        if (depthRaw != null || TemplateTiers.advancedFamilies(tier)) {
            // Re-sanitize so Silver+ cannot drop below Advanced even if UI sends Basics
            persistPluginDepth(npub, TemplateTiers.sanitizePluginDepth(tier, depthRaw))
        }

        statusMap(npub)
    }

    /**
     * Admin full reset. wipeSeed=true removes company settings + entities for re-demo.
     * Memberships must go before LegalEntity (FK user_company_membership.legal_entity_id).
     */
    SetupState reset(String npubOwner, boolean wipeSeed, String actor) {
        String npub = resolveNpub(npubOwner)
        SetupState s = ensureState(npub)

        if (wipeSeed) {
            List<LegalEntity> entities = LegalEntity.findAllByNpubOwner(npub)
            entities.each { LegalEntity le ->
                UserCompanyMembership.findAllByLegalEntity(le).each { it.delete(flush: false) }
            }
            BankAccount.findAllByNpubOwner(npub).each { it.delete(flush: false) }
            Site.findAllByNpubOwner(npub).each { it.delete(flush: false) }
            // flush membership/site/bank deletes before removing legal entities
            BankAccount.withSession { session -> session.flush() }
            entities.each { it.delete(flush: false) }
            CompanySetting.findAllByNpubOwner(npub).each { it.delete(flush: false) }
            CompanySetting.withSession { session -> session.flush() }
        }

        s.setupComplete = Boolean.FALSE
        s.completedAt = null
        s.completedBy = null
        s.draftStep = 0
        s.draftJson = null
        s.templateTier = TemplateTiers.DEFAULT_OPERATOR_TIER
        s.notes = "reset by ${actor ?: 'admin'}${wipeSeed ? ' (wipe)' : ''}"
        s.save(failOnError: true)
        s
    }

    Map consistencyReport(String npubOwner) {
        String npub = resolveNpub(npubOwner)
        LegalEntity le = LegalEntity.findByNpubOwner(npub)
        int sites = Site.countByNpubOwner(npub)
        int coa = Account.countByNpubOwner(npub)
        List issues = []
        if (!le) issues << [code: 'NO_LEGAL_ENTITY', message: 'No legal entity configured']
        if (sites < 1) issues << [code: 'NO_SITE', message: 'No site configured']
        if (coa < 1) issues << [code: 'NO_COA', message: 'Chart of accounts empty — seed recommended']
        [
                ok    : issues.isEmpty(),
                issues: issues,
                counts: [legalEntity: le ? 1 : 0, sites: sites, accounts: coa]
        ]
    }

    /**
     * Read-only tenant profile for System → Company profile (display-only).
     * Live domains + CompanySetting — not draftJson as primary truth.
     */
    Map profile(String npubOwner, String username) {
        String npub = resolveNpub(npubOwner)
        SetupState s = ensureState(npub)
        LegalEntity le = LegalEntity.findByNpubOwner(npub)
        List sites = Site.findAllByNpubOwner(npub, [sort: 'name', order: 'asc'])
        List banks = BankAccount.findAllByNpubOwner(npub, [sort: 'name', order: 'asc'])

        String tier = companyConfigService.getString(npub, 'template.tier', s.templateTier ?: TemplateTiers.DEFAULT_OPERATOR_TIER)
        List families = companyConfigService.enabledFamilies(npub)
        Map moneyPrefs = TenantMoneyService.normalizeDraft([
                primaryValueRail   : companyConfigService.getString(npub, 'finance.primaryValueRail', null),
                functionalCurrency : companyConfigService.getString(npub, 'finance.functionalCurrency',
                        le?.functionalCurrency ?: 'EUR'),
                bitcoinDisplayUnit : companyConfigService.getString(npub, 'finance.bitcoinDisplayUnit', 'SATS'),
                displayFiat        : companyConfigService.getString(npub, 'finance.displayFiat', 'EUR')
        ])
        String finCur = moneyPrefs.functionalCurrency as String
        Boolean dualSats = Boolean.FALSE
        String taxRate = companyConfigService.getString(npub, 'finance.default_tax_rate', null)
        String taxRegime = companyConfigService.getString(npub, CompanyTaxService.KEY_REGIME, null)
                ?: (companyTaxService != null ? companyTaxService.taxRegime(npub) : null)
        String defaultTaxCode = companyConfigService.getString(npub, CompanyTaxService.KEY_DEFAULT_CODE, null)
                ?: (companyTaxService != null ? companyTaxService.defaultTaxCode(npub) : null)

        String locale = null
        if (username?.trim()) {
            UserPreference pref = UserPreference.findByUsernameAndPrefKey(username.trim(), 'ui.locale')
            locale = pref?.prefValue
        }

        [
                setup        : [
                        setupComplete : s.setupComplete,
                        templateTier  : s.templateTier,
                        wizardVersion : s.wizardVersion,
                        completedAt   : s.completedAt?.time,
                        completedBy   : s.completedBy,
                        draftStep     : s.draftStep,
                        npubOwner     : s.npubOwner
                ],
                legalEntity  : le ? mapLegalEntity(le) : null,
                sites        : sites.collect { mapSite(it as Site) },
                bankAccounts : banks.collect { mapBankAccount(it as BankAccount) },
                template     : [
                        tier            : tier,
                        enabledFamilies : families,
                        pluginDepth     : readPluginDepth(npub, tier)
                ],
                messaging    : readMessagingFlags(npub),
                finance      : [
                        primaryValueRail   : moneyPrefs.primaryValueRail,
                        functionalCurrency : finCur,
                        bitcoinDisplayUnit : moneyPrefs.bitcoinDisplayUnit,
                        displayFiat        : moneyPrefs.displayFiat,
                        dualSats           : dualSats,
                        defaultTaxRate     : taxRate,
                        taxRegime          : taxRegime,
                        defaultTaxCode     : defaultTaxCode,
                        btcIntegration     : (btcIntegrationLevelService != null
                                ? btcIntegrationLevelService.publicLevel(npub) : null)
                ],
                preferences  : [
                        locale: locale
                ]
        ]
    }

    private static Map mapLegalEntity(LegalEntity le) {
        [
                id                    : le.id,
                name                  : le.name,
                description           : le.description,
                legalForm             : le.legalForm,
                taxId                 : le.taxId,
                countryCode           : le.countryCode,
                functionalCurrency    : le.functionalCurrency,
                dualSats              : le.dualSats,
                registeredAddressText : le.registeredAddressText,
                incorporationDate     : le.incorporationDate?.time
        ]
    }

    private static Map mapSite(Site site) {
        [
                id          : site.id,
                name        : site.name,
                description : site.description,
                siteType    : site.siteType,
                addressText : site.addressText,
                active      : site.active
        ]
    }

    private static Map mapBankAccount(BankAccount ba) {
        [
                id            : ba.id,
                name          : ba.name,
                description   : ba.description,
                accountKind   : ba.accountKind,
                paymentScheme : ba.paymentScheme ?: 'SEPA',
                iban          : ba.iban,
                bic           : ba.bic,
                branchName    : ba.branchName,
                accountNumber : ba.accountNumber,
                routingNumber : ba.routingNumber,
                currencyCode  : ba.currencyCode,
                externalRef   : ba.externalRef,
                active        : ba.active
        ]
    }

    /** Read pluginDepth from CompanySetting; fall back to tier defaults. */
    Map<String, String> readPluginDepth(String npub, String tier) {
        Object raw = companyConfigService.getJson(npub, KEY_PLUGIN_DEPTH, null)
        if (raw instanceof Map) {
            return TemplateTiers.sanitizePluginDepth(tier, raw as Map)
        }
        return TemplateTiers.defaultPluginDepth(tier)
    }

    /** Messaging toggles only — never secrets. Defaults off. */
    Map readMessagingFlags(String npub) {
        [
                emailEnabled   : companyConfigService.getBoolean(npub, KEY_MESSAGING_EMAIL, false),
                telegramEnabled: companyConfigService.getBoolean(npub, KEY_MESSAGING_TELEGRAM, false)
        ]
    }

    private void persistPluginDepth(String npub, Map<String, String> depth) {
        companyConfigService.putJson(npub, KEY_PLUGIN_DEPTH, depth, 'template')
    }

    private void persistMessagingFlags(String npub, Map flags) {
        boolean email = coerceBool(flags?.emailEnabled, false)
        boolean telegram = coerceBool(flags?.telegramEnabled, false)
        companyConfigService.put(npub, KEY_MESSAGING_EMAIL, email ? 'true' : 'false', 'messaging', 'boolean')
        companyConfigService.put(npub, KEY_MESSAGING_TELEGRAM, telegram ? 'true' : 'false', 'messaging', 'boolean')
    }

    private static Map extractPluginDepthFromDraft(Map d) {
        if (d.pluginDepth instanceof Map) return d.pluginDepth as Map
        return null
    }

    private static Map extractMessagingFromDraft(Map d) {
        if (d.messaging instanceof Map) return d.messaging as Map
        Map out = [:]
        if (d.emailEnabled != null) out.emailEnabled = d.emailEnabled
        if (d.telegramEnabled != null) out.telegramEnabled = d.telegramEnabled
        if (d.containsKey('messaging.emailEnabled')) out.emailEnabled = d['messaging.emailEnabled']
        if (d.containsKey('messaging.telegramEnabled')) out.telegramEnabled = d['messaging.telegramEnabled']
        return out
    }

    private static boolean coerceBool(Object v, boolean defaultValue) {
        if (v == null) return defaultValue
        if (v instanceof Boolean) return (Boolean) v
        String s = v.toString().trim()
        if (!s) return defaultValue
        return s.equalsIgnoreCase('true') || s == '1'
    }

    private String resolveNpub(String npubOwner) {
        if (npubOwner?.trim()) return npubOwner.trim()
        return DEFAULT_NPUB
    }
}
