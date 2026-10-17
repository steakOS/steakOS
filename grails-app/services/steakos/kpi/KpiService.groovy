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

import grails.plugin.springsecurity.SpringSecurityService
import groovy.util.logging.Slf4j
import steakos.User
import steakos.configuration.CompanyConfigService
import steakos.configuration.TemplateTiers
import steakos.security.RoleProfileService

/**
 * KPI registry read API (Silver WP-K0–K1). Placeholder values until PR-13 feeds.
 * Tenant-scoped; money/HR sensitivity stubs via role matrix.
 * Not @Transactional yet — no GORM aggregates until PR-13 domain feeds.
 */
@Slf4j
class KpiService {

    SpringSecurityService springSecurityService
    CompanyConfigService companyConfigService
    RoleProfileService roleProfileService

    /**
     * Full catalogue visible to the caller (RBAC-filtered).
     */
    Map catalogue(String npubOwner) {
        String tier = resolveTier(npubOwner)
        AccessCtx access = resolveAccess(npubOwner, tier)
        List<Map> items = KpiCatalogue.all().findAll { Map row ->
            access.maySee(row)
        }.collect { Map row ->
            enrichMeta(row, access, tier)
        }
        [
                meta : KpiCatalogue.catalogueMeta() + [
                        npubOwner   : npubOwner,
                        templateTier: tier,
                        silverRich  : access.silverRich
                ],
                items: items
        ]
    }

    /**
     * Single KPI definition + placeholder value envelope.
     * @throws SecurityException when sensitivity denied
     * @throws IllegalArgumentException when id unknown
     */
    Map show(String npubOwner, String id, Date from = null, Date to = null) {
        Map defn = KpiCatalogue.get(id)
        if (!defn) {
            throw new IllegalArgumentException("unknown kpi: ${id}")
        }
        String tier = resolveTier(npubOwner)
        AccessCtx access = resolveAccess(npubOwner, tier)
        if (!access.maySee(defn)) {
            throw new SecurityException("kpi forbidden: ${defn.id}")
        }
        Map body = enrichMeta(defn, access, tier)
        body.npubOwner = npubOwner
        body.from = from?.time
        body.to = to?.time
        body.value = placeholderValue(defn)
        body.series = []
        body
    }

    /**
     * CEO hero strip. Bronze → counts-only / empty rich set (decision 2026-10-17).
     * Silver → rich hero tile stubs (status planned until feeds).
     */
    Map dashboardHero(String npubOwner) {
        String tier = resolveTier(npubOwner)
        AccessCtx access = resolveAccess(npubOwner, tier)
        boolean silverRich = access.silverRich

        List<Map> tiles = []
        if (silverRich) {
            KpiCatalogue.HERO_IDS.each { String hid ->
                Map defn = KpiCatalogue.get(hid)
                if (defn && access.maySee(defn)) {
                    Map tile = enrichMeta(defn, access, tier)
                    tile.value = placeholderValue(defn)
                    tile.series = []
                    tiles << tile
                }
            }
        }

        [
                npubOwner   : npubOwner,
                templateTier: tier,
                mode        : silverRich ? 'rich' : 'counts-only',
                silverRich  : silverRich,
                note        : silverRich
                        ? 'Silver rich hero; values planned until PR-13 feeds'
                        : 'Bronze hero stays counts-only — use /api/dashboard/ceo for APQC pulse',
                tiles       : tiles,
                // Pointer for Bronze clients that still want Action-NOW counts
                countsPulse : '/api/dashboard/ceo'
        ]
    }

    private Map enrichMeta(Map row, AccessCtx access, String tier) {
        Map m = new LinkedHashMap(row)
        m.templateTier = tier
        m.allowed = access.maySee(row)
        m.redacted = false
        m
    }

    private Map placeholderValue(Map defn) {
        [
                amount : null,
                count  : null,
                ratio  : null,
                unit   : defn.unit,
                status : defn.status,
                message: defn.deferred
                        ? "Deferred: ${defn.blocker ?: 'not claimed for Silver v1'}"
                        : 'Feed not wired yet (PR-13+)'
        ]
    }

    private String resolveTier(String npubOwner) {
        String raw = companyConfigService?.getString(npubOwner, 'template.tier', TemplateTiers.DEFAULT_OPERATOR_TIER)
        TemplateTiers.normalize(raw)
    }

    private AccessCtx resolveAccess(String npubOwner, String tier) {
        Collection authorities = springSecurityService?.authentication?.authorities*.authority ?: []
        User user = currentUser()
        boolean isAdmin = authorities.contains('ROLE_ADMIN')
        boolean isCeo = authorities.contains('ROLE_CEO')
        boolean isAiOnly = authorities.contains('ROLE_AI') && !isAdmin && !isCeo

        boolean financeSee = isAdmin || isCeo
        boolean hrSee = isAdmin || isCeo
        try {
            if (user && roleProfileService != null) {
                List enabled = companyConfigService?.enabledFamilies(npubOwner) as List
                Map eff = roleProfileService.effectivePermissions(user, enabled, tier)
                Map desks = eff.desks as Map ?: [:]
                Map specials = eff.specials as Map ?: [:]
                financeSee = financeSee || !!(desks.finance?.see)
                hrSee = hrSee || !!(specials.hrSee)
            }
        } catch (Exception e) {
            log.warn('KPI RBAC matrix resolve failed: {}', e.message)
        }

        // Agents: default deny HR bulk (plan §6.4)
        if (isAiOnly) {
            hrSee = false
        }

        boolean silverRich = tier == TemplateTiers.SILVER || tier == TemplateTiers.GOLD
        new AccessCtx(financeSee: financeSee, hrSee: hrSee, isAiOnly: isAiOnly, silverRich: silverRich)
    }

    private User currentUser() {
        try {
            def p = springSecurityService?.principal
            if (p && !(p instanceof String)) {
                return User.get(p.id as Long)
            }
        } catch (ignored) {
        }
        null
    }

    private static class AccessCtx {
        boolean financeSee
        boolean hrSee
        boolean isAiOnly
        boolean silverRich

        boolean maySee(Map row) {
            String sens = (row.sensitivity as String) ?: KpiCatalogue.SENSITIVITY_NONE
            if (sens == KpiCatalogue.SENSITIVITY_MONEY) {
                return financeSee
            }
            if (sens == KpiCatalogue.SENSITIVITY_HR) {
                return hrSee
            }
            true
        }
    }
}
