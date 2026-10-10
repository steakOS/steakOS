// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.configuration

import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityService
import jakarta.servlet.http.HttpSession
import steakos.User

/**
 * PDR-026 PR2 — resolve business-data tenant npub from active company (session).
 * Desk APIs call this instead of raw user.npub so DEMO and LIVE stay isolated.
 */
@Transactional(readOnly = true)
class TenantNpubService {

    CompanyContextService companyContextService
    SpringSecurityService springSecurityService

    /**
     * @param session HTTP session (active company)
     * @param source optional request body / params that may carry npubOwner
     */
    String resolve(HttpSession session, Object source = null) {
        String fromBody = extractNpub(source)
        User u = currentUser()

        if (fromBody) {
            // Honor body only if it matches a company the user may access
            if (u && companyContextService != null) {
                boolean allowed = companyContextService.companiesFor(u).any { LegalEntity le ->
                    le?.npubOwner == fromBody
                }
                if (allowed) return fromBody
            }
        }

        if (companyContextService != null && u != null && session != null) {
            try {
                LegalEntity active = companyContextService.activeCompany(u, session)
                if (active?.npubOwner?.trim()) {
                    return active.npubOwner.trim()
                }
                // No pick yet: prefer LIVE primary
                LegalEntity primary = LegalEntity.findByCode(LegalEntity.CODE_PRIMARY)
                if (primary?.npubOwner?.trim()) {
                    return primary.npubOwner.trim()
                }
            } catch (ignored) {
            }
        }

        if (u?.npub?.trim()) return u.npub.trim()
        if (u?.username) return "user:${u.username}"
        return SetupService.DEFAULT_NPUB
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

    private static String extractNpub(Object source) {
        if (source == null) return null
        String fromBody = null
        if (source instanceof Map) {
            fromBody = source.npubOwner as String
        } else {
            try {
                fromBody = source['npubOwner'] as String
            } catch (ignored) {
            }
        }
        fromBody?.trim() ?: null
    }
}
