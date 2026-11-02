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
 * KPI registry read API (Silver WP-K0–K1 + PR-13 feeds).
 * Tenant-scoped; money/HR sensitivity via role matrix. Bronze hero stays counts-only.
 * Aggregations are transactional on {@link KpiFeedService}.
 */
@Slf4j
class KpiService {

    SpringSecurityService springSecurityService
    CompanyConfigService companyConfigService
    RoleProfileService roleProfileService
    KpiFeedService kpiFeedService

    /**
     * Live catalogue visible to the caller (RBAC-filtered).
     * Ship KPIs only — deferred / planned stretch rows stay out of the desk until a feed exists.
     */
    Map catalogue(String npubOwner) {
        String tier = resolveTier(npubOwner)
        AccessCtx access = resolveAccess(npubOwner, tier)
        List<Map> items = KpiCatalogue.all().findAll { Map row ->
            row.ship && !row.deferred && access.maySee(row)
        }.collect { Map row ->
            enrichMeta(row, access, tier)
        }
        [
                meta : KpiCatalogue.catalogueMeta() + [
                        npubOwner   : npubOwner,
                        templateTier: tier,
                        silverRich  : access.silverRich,
                        visibleCount: items.size(),
                        note        : 'Analytics desk lists ship KPIs with live feeds; deferred/planned stay hidden until backend exists'
                ],
                items: items
        ]
    }

    /**
     * Single KPI definition + live (or honest empty) value envelope.
     * @throws SecurityException when sensitivity denied
     * @throws IllegalArgumentException when id unknown or not a live ship KPI
     */
    Map show(String npubOwner, String id, Date from = null, Date to = null) {
        Map defn = KpiCatalogue.get(id)
        if (!defn) {
            throw new IllegalArgumentException("unknown kpi: ${id}")
        }
        if (!defn.ship || defn.deferred) {
            throw new IllegalArgumentException("kpi not available: ${defn.id}")
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
        body.value = computeValue(defn, npubOwner, from, to)
        body.series = []
        body
    }

    /**
     * CEO hero strip. Bronze → counts-only / empty rich set (decision 2026-10-17).
     * Silver → rich hero tiles with live feeds.
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
                    tile.value = computeValue(defn, npubOwner, null, null)
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
                        ? 'Silver rich hero with live KPI feeds'
                        : 'Bronze hero stays counts-only — use /api/dashboard/ceo for APQC pulse',
                tiles       : tiles,
                countsPulse : '/api/dashboard/ceo'
        ]
    }

    /**
     * AR aging bucket map for Finance hub charts (money RBAC).
     */
    Map arAging(String npubOwner, Date asOf = null) {
        Map defn = KpiCatalogue.get(KpiCatalogue.F01)
        String tier = resolveTier(npubOwner)
        AccessCtx access = resolveAccess(npubOwner, tier)
        if (!access.maySee(defn)) {
            throw new SecurityException("kpi forbidden: ${defn.id}")
        }
        Date when = asOf ?: new Date()
        Map body = kpiFeedService != null
                ? kpiFeedService.arAging(npubOwner, when)
                : [npubOwner: npubOwner, asOf: when, value: placeholderValue(defn)]
        body.templateTier = tier
        body.id = KpiCatalogue.F01
        body
    }

    private Map computeValue(Map defn, String npubOwner, Date from, Date to) {
        if (kpiFeedService == null) {
            return placeholderValue(defn)
        }
        kpiFeedService.compute(defn, npubOwner, from, to)
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
