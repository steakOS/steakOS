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

package steakos

import grails.plugin.springsecurity.SecurityFilterPosition
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.util.Environment
import steakos.generic.ContentProfileService
import steakos.generic.ContentType
import steakos.generic.ContentTypeCodes
import steakos.helper.Countries
import steakos.helper.Currencies
import steakos.helper.CurrencyCatalogCodes
import steakos.helper.SimpleStatus
import steakos.helper.SimpleStatusCodes
import steakos.helper.SupportedLocale
import steakos.helper.SupportedLocaleCodes
import steakos.finance.TaxCatalogCodes
import steakos.finance.TaxCode
import steakos.configuration.SetupService
import steakos.configuration.SetupState
import steakos.configuration.HelpService
import steakos.configuration.HelpTopic
import steakos.configuration.CompanyContextService
import steakos.configuration.CompanyCurrencyService
import steakos.configuration.CompanyTaxService
import steakos.configuration.OrangeSteakHouseSeedService
import steakos.configuration.HeardCookArchitectsSeedService
import steakos.security.RoleLevel
import steakos.security.RoleProfileService

class BootStrap {

    SpringSecurityService springSecurityService
    ContentProfileService contentProfileService
    SetupService setupService
    HelpService helpService
    RoleProfileService roleProfileService
    CompanyContextService companyContextService
    CompanyCurrencyService companyCurrencyService
    CompanyTaxService companyTaxService
    OrangeSteakHouseSeedService orangeSteakHouseSeedService
    HeardCookArchitectsSeedService heardCookArchitectsSeedService
    steakos.payments.BtcIntegrationLevelService btcIntegrationLevelService
    steakos.configuration.CompanyConfigService companyConfigService
    steakos.agents.AgAgentService agAgentService
    steakos.configuration.DevelopmentModeService developmentModeService
    steakos.human.HrFileService hrFileService

    def init = {
        // ADR-037: Bearer token filter after security context is established
        try {
            SpringSecurityUtils.clientRegisterFilter(
                    'agentBearerTokenFilter',
                    SecurityFilterPosition.BEARER_TOKEN_AUTH_FILTER.order
            )
        } catch (Exception ignored) {
            // already registered or plugin order timing — non-fatal on reload
        }
        // Read-only demo users: block POST/PUT/PATCH/DELETE after auth
        try {
            SpringSecurityUtils.clientRegisterFilter(
                    'readOnlyAccessFilter',
                    SecurityFilterPosition.LAST.order
            )
        } catch (Exception ignored) {
        }
        // PDR-025: matrix write verbs on /api/**
        try {
            SpringSecurityUtils.clientRegisterFilter(
                    'matrixAccessFilter',
                    SecurityFilterPosition.LAST.order - 5
            )
        } catch (Exception ignored) {
        }
        try {
            SpringSecurityUtils.clientRegisterFilter(
                    'totpGateFilter',
                    SecurityFilterPosition.LAST.order - 10
            )
        } catch (Exception ignored) {
        }

        seedContentTypes()
        seedContentProfiles()
        seedCountriesAndCurrencies()
        seedTaxCodes()
        seedSupportedLocales()
        seedSimpleStatuses()
        seedDevSecurity()
        seedDevHrEmployment()
        seedSetupState()
        seedHelpTopics()
        seedRoleProfiles()
        seedCompanyContext()
        seedCoderAgentIfDevMode()
        backfillCompanyCurrencies()
        backfillCompanyTax()
        backfillBtcIntegrationLevels()
    }

    /**
     * Task assignees (humans) sync from ACTIVE HR employment files.
     * Seed admin + ceo only (human operators). Never seed agent principals
     * (Bookkeeper, Manager, Coder, …) — those live only in the Agents desk.
     */
    private void seedDevHrEmployment() {
        if (!(Environment.current in [Environment.DEVELOPMENT, Environment.TEST])) {
            return
        }
        if (hrFileService == null) {
            println 'BootStrap: HrFileService not wired — skip seed HR employment'
            return
        }
        String npub = 'user:admin'
        // Humans only — do not add Hive*:agentKey / ROLE_AI users here
        [
                [username: 'admin', name: 'Admin', email: 'admin@local'],
                [username: 'ceo', name: 'CEO', email: 'ceo@local'],
        ].each { Map row ->
            try {
                User u = User.findByUsername(row.username as String)
                if (!u) return
                if (steakos.human.HRFile.findByNpubOwnerAndUserId(npub, u.id)) return
                hrFileService.create(npub, [
                        name            : row.name,
                        username        : u.username,
                        userId          : u.id,
                        personRef       : u.username,
                        email           : row.email,
                        employmentKind  : 'EMPLOYEE',
                        employmentStatus: 'ACTIVE',
                ])
                println "BootStrap: HR employment seeded for ${u.username}"
            } catch (Exception e) {
                println "BootStrap: HR employment seed skipped for ${row.username}: ${e.message}"
            }
        }
    }

    /** Lab Coder agent when STEAKOS_DEVELOPMENT_MODE / steakos.developmentMode is true. */
    private void seedCoderAgentIfDevMode() {
        try {
            if (!developmentModeService?.isEnabled()) return
            def a = agAgentService?.seedCoderAgent('user:admin')
            if (a) {
                println "BootStrap: Coder agent ready id=${a.id} key=${a.agentKey}"
            }
        } catch (Exception e) {
            log.warn("Coder agent seed skipped: ${e.message}")
        }
    }

    /** PDR-026 — LIVE + DEMO shell + memberships; seed Orange Steak House if empty. */
    private void seedCompanyContext() {
        if (companyContextService == null) return
        try {
            User.withTransaction {
                companyContextService.ensureBootstrapped()
                orangeSteakHouseSeedService?.ensureAndSeed()
                heardCookArchitectsSeedService?.ensureAndSeed()
            }
        } catch (Exception e) {
            log.warn("Company / demo seed skipped: ${e.message}")
        }
    }

    private void seedHelpTopics() {
        if (helpService != null) {
            HelpTopic.withTransaction {
                helpService.ensureSeeded()
            }
        }
    }

    private void seedRoleProfiles() {
        if (roleProfileService == null) return
        try {
            User.withTransaction {
                roleProfileService.ensureSeeded()
            }
        } catch (Exception e) {
            log.warn("RoleProfile seed skipped: ${e.message}")
        }
    }

    /** Ensure SetupState exists (legacy tenants marked complete as Iron). */
    private void seedSetupState() {
        if (setupService != null) {
            SetupState.withTransaction {
                setupService.ensureState(SetupService.DEFAULT_NPUB)
            }
        }
    }

    def destroy = {
    }

    /**
     * Idempotent system catalog seed (D10 + CT1): Note in, Attachment out.
     */
    private void seedContentTypes() {
        ContentType.withTransaction {
            ContentTypeCodes.REMOVED.each { String code ->
                ContentType row = ContentType.findByCode(code)
                if (row) {
                    row.delete(flush: true)
                }
            }
            ContentTypeCodes.ALL.each { String code ->
                if (!ContentType.findByCode(code)) {
                    new ContentType(
                            code: code,
                            name: code,
                            description: "System content type: ${code}"
                    ).save(failOnError: true)
                }
            }
        }
    }

    /** System ObjectContentProfile allowlists (CT4). */
    private void seedContentProfiles() {
        if (contentProfileService != null) {
            contentProfileService.ensureSystemProfilesSeeded()
        }
    }

    /**
     * Unique countries from SupportedLocale; currencies from CurrencyCatalogCodes (OI-CUR01 D3).
     * Seeded before locales so reference data exists first. Idempotent upsert of descriptions.
     */
    private void seedCountriesAndCurrencies() {
        Countries.withTransaction {
            SupportedLocaleCodes.uniqueCountries().each { Map<String, String> row ->
                if (!Countries.findByCode(row.code)) {
                    new Countries(
                            code: row.code,
                            name: row.name,
                            description: "ISO country ${row.code}",
                            enabled: true
                    ).save(failOnError: true)
                }
            }
        }

        Currencies.withTransaction {
            CurrencyCatalogCodes.ALL.each { Map row ->
                String code = row.code as String
                Currencies existing = Currencies.findByCode(code)
                if (!existing) {
                    new Currencies(
                            code: code,
                            name: row.name as String,
                            description: row.description as String,
                            decimals: (row.decimals as Integer) ?: 2,
                            enabled: true
                    ).save(failOnError: true)
                } else {
                    // Enrich thin legacy seed rows (EUR/CHF/USD/SGD)
                    boolean dirty = false
                    if (row.name && existing.name != row.name) {
                        existing.name = row.name as String
                        dirty = true
                    }
                    if (row.description && existing.description != row.description) {
                        existing.description = row.description as String
                        dirty = true
                    }
                    Integer dec = row.decimals as Integer
                    if (dec != null && existing.decimals != dec) {
                        existing.decimals = dec
                        dirty = true
                    }
                    if (dirty) existing.save(failOnError: true)
                }
            }
        }
    }

    /** OI-CUR01 D7 backfill: enable functional (+ displayFiat) for every LegalEntity. */
    private void backfillCompanyCurrencies() {
        if (companyCurrencyService == null) return
        try {
            int n = companyCurrencyService.backfillAllCompanies()
            if (n > 0) {
                println "BootStrap: company currency in-use backfill for ${n} LegalEntity row(s)"
            }
        } catch (Exception e) {
            println "BootStrap: company currency backfill skipped: ${e.message}"
        }
    }

    /** OI-F23 D1 — system TaxCode seed (DE/AT full + US stubs). Idempotent upsert. */
    private void seedTaxCodes() {
        TaxCode.withTransaction {
            TaxCatalogCodes.ALL.each { Map row ->
                String code = row.code as String
                TaxCode existing = TaxCode.findByCode(code)
                if (!existing) {
                    new TaxCode(
                            code           : code,
                            jurisdiction   : row.jurisdiction as String,
                            name           : row.name as String,
                            description    : row.description as String,
                            ratePercent    : row.ratePercent as BigDecimal,
                            taxKind        : row.taxKind as String,
                            reverseCharge  : row.reverseCharge as Boolean ?: Boolean.FALSE,
                            intraCommunity : row.intraCommunity as Boolean ?: Boolean.FALSE,
                            enabled        : true
                    ).save(failOnError: true)
                } else {
                    boolean dirty = false
                    if (row.name && existing.name != row.name) {
                        existing.name = row.name as String
                        dirty = true
                    }
                    if (row.description && existing.description != row.description) {
                        existing.description = row.description as String
                        dirty = true
                    }
                    BigDecimal rate = row.ratePercent as BigDecimal
                    if (rate != null && existing.ratePercent != rate) {
                        existing.ratePercent = rate
                        dirty = true
                    }
                    if (row.taxKind && existing.taxKind != row.taxKind) {
                        existing.taxKind = row.taxKind as String
                        dirty = true
                    }
                    Boolean rc = row.reverseCharge as Boolean
                    if (rc != null && existing.reverseCharge != rc) {
                        existing.reverseCharge = rc
                        dirty = true
                    }
                    Boolean ig = row.intraCommunity as Boolean
                    if (ig != null && existing.intraCommunity != ig) {
                        existing.intraCommunity = ig
                        dirty = true
                    }
                    if (dirty) existing.save(failOnError: true)
                }
            }
        }
    }

    /** OI-F23 D2/D3 — regime + in-use pack from LegalEntity country. */
    private void backfillCompanyTax() {
        if (companyTaxService == null) return
        try {
            int n = companyTaxService.backfillAllCompanies()
            if (n > 0) {
                println "BootStrap: company tax in-use backfill for ${n} LegalEntity row(s)"
            }
        } catch (Exception e) {
            println "BootStrap: company tax backfill skipped: ${e.message}"
        }
    }

    /** OI-BTC01: if btcpay live → BTCPAY else DEFERRED when level unset. */
    private void backfillBtcIntegrationLevels() {
        if (btcIntegrationLevelService == null) return
        try {
            int n = btcIntegrationLevelService.migrateAllCompanies()
            if (n > 0) {
                println "BootStrap: BTC integration level migrate for ${n} LegalEntity row(s)"
            }
        } catch (Exception e) {
            println "BootStrap: BTC integration level migrate skipped: ${e.message}"
        }
    }

    /**
     * Idempotent UI locale catalog (L1–L4-A). Underscore codes for Grails/Java Locale.
     */
    private void seedSupportedLocales() {
        SupportedLocale.withTransaction {
            SupportedLocaleCodes.ALL.each { Map<String, String> row ->
                if (!SupportedLocale.findByCode(row.code)) {
                    new SupportedLocale(
                            code: row.code,
                            name: row.name,
                            description: "Supported UI locale ${row.code} (${row.currencyCode})",
                            languageCode: row.languageCode,
                            countryCode: row.countryCode,
                            currencyCode: row.currencyCode,
                            enabled: true
                    ).save(failOnError: true)
                }
            }
        }
    }

    /**
     * Lifecycle status catalog: NEW → WORKING → CHECKED → COMPLETED / CANCELLED.
     */
    private void seedSimpleStatuses() {
        SimpleStatus.withTransaction {
            int order = 10
            SimpleStatusCodes.ALL.each { Map<String, String> row ->
                SimpleStatus existing = SimpleStatus.findByCode(row.code)
                if (!existing) {
                    new SimpleStatus(
                            code: row.code,
                            name: row.name,
                            description: row.description,
                            sortOrder: order,
                            enabled: true
                    ).save(failOnError: true)
                } else {
                    // Keep catalog labels in sync (e.g. COMPLETED display name → Done)
                    boolean dirty = false
                    if (existing.name != row.name) {
                        existing.name = row.name
                        dirty = true
                    }
                    if (row.description && existing.description != row.description) {
                        existing.description = row.description
                        dirty = true
                    }
                    if (existing.sortOrder == null) {
                        existing.sortOrder = order
                        dirty = true
                    }
                    if (dirty) {
                        existing.save(failOnError: true)
                    }
                }
                order += 10
            }
        }
    }

    private void seedDevSecurity() {
        // DEVELOPMENT + TEST (HTTP E2E / integrationTest on steakos_test needs admin)
        if (!(Environment.current in [Environment.DEVELOPMENT, Environment.TEST])) {
            return
        }

        User.withTransaction {
            Role userRole = Role.findByAuthority('ROLE_USER')
            if (!userRole) {
                userRole = new Role(authority: 'ROLE_USER').save(failOnError: true)
            }

            Role adminRole = Role.findByAuthority('ROLE_ADMIN')
            if (!adminRole) {
                adminRole = new Role(authority: 'ROLE_ADMIN').save(failOnError: true)
            }

            // AUTH02/03 — CEO elevated business role
            Role ceoRole = Role.findByAuthority('ROLE_CEO')
            if (!ceoRole) {
                ceoRole = new Role(authority: 'ROLE_CEO').save(failOnError: true)
            }

            // ADR-007 / ADR-037 — AI-Level for Hermes agent principals
            Role aiRole = Role.findByAuthority(RoleLevel.AI.authority)
            if (!aiRole) {
                aiRole = new Role(authority: RoleLevel.AI.authority).save(failOnError: true)
            }

            // AUTH password policy — shared dev default for admin + ceo
            String seedPw = steakos.security.PasswordPolicy.DEV_DEFAULT_PASSWORD

            User admin = User.findByUsername('admin')
            if (!admin) {
                admin = new User(
                        username: 'admin',
                        password: seedPw,
                        enabled: true,
                        npub: 'user:admin',
                        displayName: 'Admin'
                )
                admin.springSecurityService = springSecurityService
                admin.save(failOnError: true)
                UserRole.create(admin, userRole, true)
                UserRole.create(admin, adminRole, true)
            } else {
                // Keep local/dev credentials in sync with PasswordPolicy default
                admin.password = seedPw
                admin.springSecurityService = springSecurityService
                admin.save(failOnError: true)
            }

            User ceo = User.findByUsername('ceo')
            if (!ceo) {
                ceo = new User(
                        username: 'ceo',
                        password: seedPw,
                        enabled: true,
                        npub: 'user:admin',
                        displayName: 'CEO'
                )
                ceo.springSecurityService = springSecurityService
                ceo.save(failOnError: true)
                UserRole.create(ceo, userRole, true)
                UserRole.create(ceo, ceoRole, true)
                // Elevated like Admin for ops (AUTH02)
                UserRole.create(ceo, adminRole, true)
            } else {
                ceo.password = seedPw
                ceo.springSecurityService = springSecurityService
                ceo.save(failOnError: true)
                if (!UserRole.findByUserAndRole(ceo, ceoRole)) {
                    UserRole.create(ceo, ceoRole, true)
                }
                if (!UserRole.findByUserAndRole(ceo, adminRole)) {
                    UserRole.create(ceo, adminRole, true)
                }
            }

            // Read-only demo visitor: full UI/API read, same tenant as admin, no writes
            Role readOnlyRole = Role.findByAuthority('ROLE_READ_ONLY')
            if (!readOnlyRole) {
                readOnlyRole = new Role(authority: 'ROLE_READ_ONLY').save(failOnError: true)
            }
            if (!User.findByUsername('demo')) {
                User demo = new User(
                        username: 'demo',
                        password: seedPw,
                        enabled: true,
                        // Share admin tenant data so demo sees the same company / desks
                        npub: 'user:admin'
                )
                demo.springSecurityService = springSecurityService
                demo.save(failOnError: true)
                UserRole.create(demo, userRole, true)
                UserRole.create(demo, readOnlyRole, true)
            } else {
                // Ensure existing demo stays read-only + shared tenant + policy password
                User demo = User.findByUsername('demo')
                if (demo) {
                    demo.password = seedPw
                    demo.springSecurityService = springSecurityService
                    if (demo.npub != 'user:admin') {
                        demo.npub = 'user:admin'
                    }
                    demo.save(failOnError: true)
                    if (!UserRole.findByUserAndRole(demo, readOnlyRole)) {
                        UserRole.create(demo, readOnlyRole, true)
                    }
                    if (!UserRole.findByUserAndRole(demo, userRole)) {
                        UserRole.create(demo, userRole, true)
                    }
                }
            }
        }

        // Testing convenience: Admin may skip TOTP gate in DEVELOPMENT/TEST.
        // Toggle off from Security Settings before invite / LIVE demos.
        try {
            if (companyConfigService != null &&
                    !companyConfigService.isAdminTotpBypassEnabled()) {
                companyConfigService.setAdminTotpBypass(true)
                println 'BootStrap: security.adminTotpBypass=true (DEVELOPMENT/TEST)'
            }
        } catch (Exception e) {
            println "BootStrap: could not seed adminTotpBypass: ${e.message}"
        }
    }
}

