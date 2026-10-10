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
import jakarta.servlet.http.HttpSession
import steakos.User

/**
 * PDR-026 PR1 — company instances, memberships, session active company.
 */
@Transactional
class CompanyContextService {

    static final String SESSION_ACTIVE_COMPANY_ID = 'STEAKOS_ACTIVE_COMPANY_ID'
    static final String DEMO_NAME = 'Orange Steak House'
    static final String HEARD_COOK_NAME = 'Heard & Cook Architects'
    /** Distinct tenant keys for DEMO rows (PDR-026 npub-per-company). */
    static final String DEMO_NPUB = 'company:orange-steak-house'
    static final String HEARD_COOK_NPUB = 'company:heard-cook-architects'
    static final String DEFAULT_NPUB = 'user:admin'

    steakos.finance.PostingRulesetService postingRulesetService
    CompanyCurrencyService companyCurrencyService

    /**
     * Ensure LIVE primary + demo shells exist; grant all enabled users membership (D5).
     * Also seeds posting rules / payroll COA per company npub (PDR-027).
     */
    void ensureBootstrapped() {
        LegalEntity primary = ensurePrimaryLive()
        LegalEntity demo = ensureDemoShell(primary)
        LegalEntity heard = ensureHeardCookShell()
        User.findAllByEnabled(true).each { User u ->
            ensureMembership(u, primary, true)
            ensureMembership(u, demo, false)
            ensureMembership(u, heard, false)
        }
        // PDR-027 — posting rules + payroll COA for each company tenant npub
        try {
            [primary, demo, heard].each { LegalEntity le ->
                if (le?.npubOwner) {
                    postingRulesetService?.ensureSeeded(le.npubOwner)
                }
            }
        } catch (Exception e) {
            // non-fatal at bootstrap
        }
    }

    LegalEntity ensurePrimaryLive() {
        LegalEntity byCode = LegalEntity.findByCode(LegalEntity.CODE_PRIMARY)
        if (byCode) {
            if (!byCode.kind) {
                byCode.kind = LegalEntity.KIND_LIVE
                byCode.save(failOnError: true)
            }
            return byCode
        }
        LegalEntity existing = LegalEntity.findByNpubOwner(DEFAULT_NPUB)
        if (!existing) {
            List list = LegalEntity.list(max: 1)
            existing = list ? list[0] as LegalEntity : null
        }
        if (existing) {
            existing.code = existing.code ?: LegalEntity.CODE_PRIMARY
            existing.kind = LegalEntity.KIND_LIVE
            if (!existing.name?.trim()) {
                existing.name = 'My Company'
            }
            existing.save(failOnError: true)
            return existing
        }
        LegalEntity created = new LegalEntity(
                npubOwner: DEFAULT_NPUB,
                name: 'My Company',
                code: LegalEntity.CODE_PRIMARY,
                kind: LegalEntity.KIND_LIVE,
                countryCode: 'DE',
                functionalCurrency: 'EUR',
                dualSats: Boolean.FALSE
        )
        created.markCreatedBy(DEFAULT_NPUB)
        created.markUpdatedBy(DEFAULT_NPUB)
        created.save(failOnError: true)
        created
    }

    LegalEntity ensureDemoShell(LegalEntity primary) {
        LegalEntity demo = LegalEntity.findByCode(LegalEntity.CODE_ORANGE_STEAK)
        if (demo) {
            boolean dirty = false
            // PR2: isolate demo data under its own npub
            if (demo.npubOwner != DEMO_NPUB) {
                demo.npubOwner = DEMO_NPUB
                dirty = true
            }
            // L5 / OI-F01 — showcase must stay DEMO (mock settle only)
            if (demo.kind != LegalEntity.KIND_DEMO) {
                demo.kind = LegalEntity.KIND_DEMO
                dirty = true
            }
            if (dirty) {
                demo.save(failOnError: true)
            }
            return demo
        }
        demo = new LegalEntity(
                npubOwner: DEMO_NPUB,
                name: DEMO_NAME,
                code: LegalEntity.CODE_ORANGE_STEAK,
                kind: LegalEntity.KIND_DEMO,
                description: 'Sales / eval showcase restaurant (demo). Admin can Reset anytime.',
                countryCode: primary?.countryCode ?: 'DE',
                functionalCurrency: primary?.functionalCurrency ?: 'EUR',
                dualSats: Boolean.FALSE,
                legalForm: 'OTHER'
        )
        demo.markCreatedBy(DEMO_NPUB)
        demo.markUpdatedBy(DEMO_NPUB)
        demo.save(failOnError: true)
        demo
    }

    /** Second DEMO — architecture / coastal Florida firm. */
    LegalEntity ensureHeardCookShell() {
        LegalEntity le = LegalEntity.findByCode(LegalEntity.CODE_HEARD_COOK)
        if (le) {
            boolean dirty = false
            if (le.npubOwner != HEARD_COOK_NPUB) {
                le.npubOwner = HEARD_COOK_NPUB
                dirty = true
            }
            if (le.kind != LegalEntity.KIND_DEMO) {
                le.kind = LegalEntity.KIND_DEMO
                dirty = true
            }
            if (dirty) {
                le.save(failOnError: true)
            }
            return le
        }
        le = new LegalEntity(
                npubOwner: HEARD_COOK_NPUB,
                name: HEARD_COOK_NAME,
                code: LegalEntity.CODE_HEARD_COOK,
                kind: LegalEntity.KIND_DEMO,
                description: 'Architecture & design firm — residential, commercial, and ultra-high-end kitchen studios. HQ Indian Rocks Beach, FL (demo).',
                countryCode: 'US',
                functionalCurrency: 'USD',
                dualSats: Boolean.FALSE,
                legalForm: 'LLC',
                registeredAddressText: 'Indian Rocks Beach, Florida, USA'
        )
        le.markCreatedBy(HEARD_COOK_NPUB)
        le.markUpdatedBy(HEARD_COOK_NPUB)
        le.save(failOnError: true)
        le
    }

    UserCompanyMembership ensureMembership(User user, LegalEntity company, boolean isDefault) {
        if (!user || !company) return null
        UserCompanyMembership m = UserCompanyMembership.findByUserAndLegalEntity(user, company)
        if (m) {
            if (isDefault && !m.isDefault) {
                m.isDefault = true
                m.save(failOnError: true)
            }
            return m
        }
        m = new UserCompanyMembership(user: user, legalEntity: company, isDefault: isDefault)
        m.save(failOnError: true)
        m
    }

    List<LegalEntity> companiesFor(User user) {
        if (!user) return []
        UserCompanyMembership.findAllByUser(user)*.legalEntity.findAll { it != null }.unique { it.id }
                .sort { a, b ->
                    int ka = a.kind == LegalEntity.KIND_DEMO ? 1 : 0
                    int kb = b.kind == LegalEntity.KIND_DEMO ? 1 : 0
                    ka <=> kb ?: (a.name ?: '') <=> (b.name ?: '')
                }
    }

    List<Map> companyMapsFor(User user) {
        companiesFor(user).collect { companyMap(it) }
    }

    /** Showcase codes always present as DEMO for picker honesty (OI-F01). */
    boolean isShowcaseDemo(LegalEntity le) {
        if (!le) return false
        if (le.isDemo()) return true
        String code = le.code?.toString()
        return code == LegalEntity.CODE_ORANGE_STEAK || code == LegalEntity.CODE_HEARD_COOK ||
                le.npubOwner == DEMO_NPUB || le.npubOwner == HEARD_COOK_NPUB
    }

    Map companyMap(LegalEntity le) {
        if (!le) return null
        String companyCurrency = null
        try {
            if (companyCurrencyService != null && le.npubOwner) {
                companyCurrency = companyCurrencyService.companyCurrencyCode(le.npubOwner)
            }
        } catch (ignored) {
        }
        if (!companyCurrency) {
            companyCurrency = le.functionalCurrency ?: 'EUR'
            if (companyCurrency == 'BTC') companyCurrency = 'EUR'
        }
        boolean demo = isShowcaseDemo(le)
        [
                id              : le.id,
                code            : le.code,
                name            : le.name,
                kind            : demo ? LegalEntity.KIND_DEMO : (le.kind ?: LegalEntity.KIND_LIVE),
                demo            : demo,
                companyCurrency : companyCurrency
        ]
    }

    Long resolveActiveCompanyId(User user, HttpSession session) {
        List<LegalEntity> cos = companiesFor(user)
        if (!cos) return null
        if (cos.size() == 1) {
            Long only = cos[0].id as Long
            session?.setAttribute(SESSION_ACTIVE_COMPANY_ID, only)
            return only
        }
        Object raw = session?.getAttribute(SESSION_ACTIVE_COMPANY_ID)
        Long sid = raw instanceof Number ? (raw as Number).longValue() : (raw ? raw.toString().toLong() : null)
        if (sid != null && cos.any { it.id == sid }) {
            return sid
        }
        // Prefer default membership, else leave unset so picker shows (D3)
        UserCompanyMembership defM = UserCompanyMembership.findByUserAndIsDefault(user, true)
        if (defM?.legalEntity?.id && cos.any { it.id == defM.legalEntity.id }) {
            // Do not auto-bind when 2+ — picker required once per session until chosen
            return null
        }
        null
    }

    boolean needsCompanyPick(User user, HttpSession session) {
        List<LegalEntity> cos = companiesFor(user)
        if (cos.size() < 2) return false
        resolveActiveCompanyId(user, session) == null
    }

    LegalEntity activeCompany(User user, HttpSession session) {
        Long id = resolveActiveCompanyId(user, session)
        if (id == null) return null
        LegalEntity.get(id)
    }

    LegalEntity selectCompany(User user, HttpSession session, Long companyId) {
        if (!user || companyId == null) {
            throw new IllegalArgumentException('companyId required')
        }
        LegalEntity le = LegalEntity.get(companyId)
        if (!le) {
            throw new IllegalArgumentException('company not found')
        }
        UserCompanyMembership m = UserCompanyMembership.findByUserAndLegalEntity(user, le)
        if (!m) {
            throw new SecurityException('No access to that company')
        }
        session.setAttribute(SESSION_ACTIVE_COMPANY_ID, le.id as Long)
        le
    }

    Map contextPayload(User user, HttpSession session) {
        ensureBootstrapped()
        List<Map> companies = companyMapsFor(user)
        Long activeId = resolveActiveCompanyId(user, session)
        boolean needPick = companies.size() >= 2 && activeId == null
        [
                companies       : companies,
                activeCompanyId : activeId,
                activeCompany   : activeId ? companyMap(LegalEntity.get(activeId)) : null,
                needCompanyPick : needPick
        ]
    }
}
