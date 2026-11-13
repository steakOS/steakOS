// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.api

import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import groovy.util.logging.Slf4j
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import steakos.User
import steakos.security.AuthAccountService
import steakos.security.NostrAuthService
import steakos.security.RoleProfileService
import steakos.security.TotpGateFilter
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * AUTH01–04 + NIP-07 + PDR-025 role profiles.
 */
@Slf4j
class AuthApiController {

    static allowedMethods = [
            totpStatus    : 'GET',
            totpBegin     : 'POST',
            totpConfirm   : 'POST',
            totpVerify    : 'POST',
            totpClear     : 'POST',
            totpBeginFor  : 'POST',
            totpConfirmFor: 'POST',
            adminTotpBypassGet : 'GET',
            adminTotpBypassPut : 'PUT',
            nostrChallenge: 'POST',
            nostrVerify   : 'POST',
            nostrLink     : 'POST',
            usersList     : 'GET',
            usersCreate   : 'POST',
            usersUpdate   : 'PUT',
            roleProfilesList  : 'GET',
            roleProfileShow   : 'GET',
            roleProfileUpdate : 'PUT',
            roleProfileReset  : 'POST',
            roleProfileApply  : 'POST'
    ]

    AuthAccountService authAccountService
    NostrAuthService nostrAuthService
    SpringSecurityService springSecurityService
    RoleProfileService roleProfileService
    steakos.configuration.CompanyConfigService companyConfigService

    // ----- TOTP (AUTH01) -----

    @Secured(['IS_AUTHENTICATED_REMEMBERED'])
    def totpStatus() {
        User u = currentUser()
        render([
                totpEnabled : !!u?.totpEnabled,
                totpVerified: session.getAttribute(TotpGateFilter.SESSION_TOTP_OK) == Boolean.TRUE,
                authViaNostr: session.getAttribute(TotpGateFilter.SESSION_AUTH_VIA_NOSTR) == Boolean.TRUE
        ] as JSON)
    }

    @Secured(['IS_AUTHENTICATED_REMEMBERED'])
    def totpBegin() {
        try {
            User u = currentUser()
            render(authAccountService.beginTotpEnrol(u) as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    @Secured(['IS_AUTHENTICATED_REMEMBERED'])
    def totpConfirm() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            User u = currentUser()
            Map result = authAccountService.confirmTotpEnrol(u, body.code as String)
            markStepUp()
            render(result as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'INVALID_CODE'] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** After password login — verify code (AUTH01 gate). */
    @Secured(['IS_AUTHENTICATED_REMEMBERED'])
    def totpVerify() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            User u = currentUser()
            if (!authAccountService.verifyTotpOrRecovery(u, body.code as String)) {
                render(status: 401, text: ([error: 'Invalid code', code: 'INVALID_CODE'] as JSON))
                return
            }
            markStepUp()
            render([ok: true] as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CEO'])
    def totpClear() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String username = (body.username as String)?.trim()
            User target = username ? authAccountService.findByUsername(username) : currentUser()
            if (!target) {
                render(status: 404, text: ([error: 'user not found'] as JSON))
                return
            }
            authAccountService.clearTotp(target)
            render([ok: true] as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /**
     * Install-wide testing switch: when enabled, ROLE_ADMIN skips the TOTP gate.
     * Stored as CompanySetting SYSTEM / security.adminTotpBypass.
     */
    @Secured(['ROLE_ADMIN'])
    def adminTotpBypassGet() {
        if (steakos.security.ProductionSecrets.productionFlag()) {
            render(status: 404, text: '{"error":"Not found"}')
            return
        }
        boolean enabled = !!companyConfigService?.isAdminTotpBypassEnabled()
        render([
                enabled    : enabled,
                settingKey : steakos.configuration.CompanyConfigService.KEY_ADMIN_TOTP_BYPASS,
                warning    : 'Testing only — re-enable authenticator requirement before invite / LIVE use.',
        ] as JSON)
    }

    @Secured(['ROLE_ADMIN'])
    def adminTotpBypassPut() {
        if (steakos.security.ProductionSecrets.productionFlag()) {
            render(status: 404, text: '{"error":"Not found"}')
            return
        }
        try {
            Map body = (request.JSON as Map) ?: [:]
            boolean enabled = body.enabled == true || body.enabled == 'true' || body.enabled == 1
            companyConfigService.setAdminTotpBypass(enabled)
            if (enabled) {
                session.setAttribute(TotpGateFilter.SESSION_TOTP_OK, Boolean.TRUE)
            }
            render([
                    ok         : true,
                    enabled    : enabled,
                    settingKey : steakos.configuration.CompanyConfigService.KEY_ADMIN_TOTP_BYPASS,
            ] as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** AUTH02 — Admin enrols TOTP for another user (e.g. CEO in wizard). */
    @Secured(['ROLE_ADMIN', 'ROLE_CEO'])
    def totpBeginFor() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String username = (body.username as String)?.trim()
            User target = authAccountService.findByUsername(username)
            if (!target) {
                render(status: 404, text: ([error: 'user not found'] as JSON))
                return
            }
            if (target.totpEnabled) {
                render(status: 400, text: ([error: 'Authenticator already enabled for this user'] as JSON))
                return
            }
            render(authAccountService.beginTotpEnrol(target) as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CEO'])
    def totpConfirmFor() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String username = (body.username as String)?.trim()
            User target = authAccountService.findByUsername(username)
            if (!target) {
                render(status: 404, text: ([error: 'user not found'] as JSON))
                return
            }
            Map result = authAccountService.confirmTotpEnrol(target, body.code as String)
            render(result as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'INVALID_CODE'] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    // ----- Nostr NIP-07 (public challenge/verify; link requires session) -----

    @Secured(['permitAll'])
    def nostrChallenge() {
        if (!nostrAuthService) {
            nostrAuthService = new NostrAuthService()
        }
        render([challenge: nostrAuthService.issueChallenge(), expiresInSec: 300] as JSON)
    }

    @Secured(['permitAll'])
    def nostrVerify() {
        try {
            if (!nostrAuthService) {
                nostrAuthService = new NostrAuthService()
            }
            Map body = (request.JSON as Map) ?: [:]
            String challenge = body.challenge as String
            Map event = body.event as Map
            String pubkey = nostrAuthService.verifyLoginEvent(event, challenge)
            // Prefer hex pubkey match; also try npub field if stored as hex
            User user = User.findByNpub(pubkey)
            if (!user) {
                // scan — encrypted npub may not findBy; use list for small installs
                user = User.list().find { User u ->
                    u.npub && (u.npub.equalsIgnoreCase(pubkey) || u.npub == pubkey)
                } as User
            }
            if (!user || !user.enabled) {
                render(status: 403, text: ([
                        error: 'This login key is not linked to a steakOS user. Ask your admin to invite you.',
                        code : 'NPUB_NOT_LINKED'
                ] as JSON))
                return
            }
            establishSession(user, true)
            render([ok: true, username: user.username, authorities: user.authorities*.authority] as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 401, text: ([error: e.message, code: 'NOSTR_AUTH_FAILED'] as JSON))
        } catch (Exception e) {
            log.error('nostrVerify', e)
            render(status: 500, text: ([error: e.message] as JSON))
        }
    }

    @Secured(['IS_AUTHENTICATED_REMEMBERED'])
    def nostrLink() {
        try {
            if (!nostrAuthService) {
                nostrAuthService = new NostrAuthService()
            }
            Map body = (request.JSON as Map) ?: [:]
            String challenge = body.challenge as String
            Map event = body.event as Map
            String pubkey = nostrAuthService.verifyLoginEvent(event, challenge)
            User other = User.list().find { User u ->
                u.id != currentUser()?.id && u.npub && u.npub.equalsIgnoreCase(pubkey)
            } as User
            if (other) {
                render(status: 409, text: ([error: 'That login key is already linked to another user', code: 'NPUB_IN_USE'] as JSON))
                return
            }
            User u = currentUser()
            u.npub = pubkey
            u.save(failOnError: true, flush: true)
            render([ok: true, pubkey: pubkey] as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 500, text: ([error: e.message] as JSON))
        }
    }

    // ----- AUTH04 users -----

    @Secured(['ROLE_ADMIN', 'ROLE_CEO'])
    def usersList() {
        render([items: authAccountService.listUsers()] as JSON)
    }

    /** POST /api/auth/users/purge-disabled — permanently delete all enabled=false logins. */
    @Secured(['ROLE_ADMIN'])
    def usersPurgeDisabled() {
        try {
            User me = currentUser()
            Map result = authAccountService.deleteDisabledUsers(me?.id)
            render(result as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message, code: 'PURGE_DISABLED_FAILED'] as JSON))
        }
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CEO'])
    def usersCreate() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String profileId = (body.roleProfileId ?: body.profileId ?: 'user') as String
            if (profileId == 'admin' && !isTrulyAdmin()) {
                render(status: 403, text: ([error: 'Only Admin can assign the Admin profile', code: 'ADMIN_PROFILE_FORBIDDEN'] as JSON))
                return
            }
            List roles
            if (body.authorities instanceof List && !body.roleProfileId && !body.profileId) {
                roles = (body.authorities as List).collect { it.toString() }
                if (!isTrulyAdmin() && roles.contains(AuthAccountService.ROLE_ADMIN) && !roles.contains('ROLE_CEO')) {
                    render(status: 403, text: ([error: 'Only Admin can create Admin users'] as JSON))
                    return
                }
            } else {
                def profile = roleProfileService.findByProfileId(profileId)
                if (!profile) {
                    render(status: 400, text: ([error: "unknown role profile: ${profileId}"] as JSON))
                    return
                }
                roles = new JsonSlurper().parseText(profile.springRolesJson) as List
            }
            User u = authAccountService.createOrUpdateUser(
                    body.username as String,
                    body.password as String,
                    roles.collect { it.toString() },
                    body.displayName as String
            )
            u.roleProfileId = profileId
            u.save(failOnError: true, flush: true)
            render(status: 201, text: (authAccountService.userToMap(u) as JSON))
        } catch (SecurityException e) {
            render(status: 403, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CEO'])
    def usersUpdate() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            User u = authAccountService.findByUsername(body.username as String)
            if (!u) {
                render(status: 404, text: ([error: 'not found'] as JSON))
                return
            }
            if (body.containsKey('enabled')) {
                if (body.enabled == false && isLastAdmin(u)) {
                    render(status: 400, text: ([error: 'Cannot disable the last Admin', code: 'LAST_ADMIN'] as JSON))
                    return
                }
                authAccountService.setEnabled(u, body.enabled as Boolean)
            }
            if (body.roleProfileId || body.profileId) {
                String profileId = (body.roleProfileId ?: body.profileId) as String
                roleProfileService.assignProfile(u, profileId, isTrulyAdmin())
            } else if (body.authorities instanceof List) {
                List roles = body.authorities.collect { it.toString() }
                if (!isTrulyAdmin() && roles.contains(AuthAccountService.ROLE_ADMIN) && !roles.contains('ROLE_CEO')) {
                    render(status: 403, text: ([error: 'Only Admin can assign Admin'] as JSON))
                    return
                }
                authAccountService.setUserRoles(u, roles)
            }
            if (body.password) {
                authAccountService.changePassword(u, body.password as String)
            }
            if (body.clearTotp) {
                authAccountService.clearTotp(u)
            }
            if (body.containsKey('permissionOverrideJson')) {
                authAccountService.setPermissionOverride(u, body.permissionOverrideJson as String)
            }
            render(authAccountService.userToMap(User.findByUsername(u.username)) as JSON)
        } catch (SecurityException e) {
            render(status: 403, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    // ----- PDR-025 role profiles -----

    @Secured(['ROLE_ADMIN', 'ROLE_CEO'])
    def roleProfilesList() {
        render([items: roleProfileService.listProfiles()] as JSON)
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CEO'])
    def roleProfileShow() {
        def p = roleProfileService.findByProfileId(params.profileId as String)
        if (!p) {
            render(status: 404, text: ([error: 'not found'] as JSON))
            return
        }
        render(roleProfileService.toMap(p) as JSON)
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CEO'])
    def roleProfileUpdate() {
        try {
            String profileId = params.profileId as String
            // CEO may edit non-admin profiles; Admin may edit all
            if (profileId == 'admin' && !isTrulyAdmin()) {
                render(status: 403, text: ([error: 'Only Admin can edit the Admin profile'] as JSON))
                return
            }
            Map body = (request.JSON as Map) ?: [:]
            def p = roleProfileService.updateProfile(profileId, body)
            render(roleProfileService.toMap(p) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 404, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CEO'])
    def roleProfileReset() {
        try {
            String profileId = params.profileId as String
            if (profileId == 'admin' && !isTrulyAdmin()) {
                render(status: 403, text: ([error: 'Only Admin can reset the Admin profile'] as JSON))
                return
            }
            def p = roleProfileService.resetToDefault(profileId)
            render(roleProfileService.toMap(p) as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CEO'])
    def roleProfileApply() {
        try {
            String profileId = params.profileId as String
            if (profileId == 'admin' && !isTrulyAdmin()) {
                render(status: 403, text: ([error: 'Only Admin can apply the Admin profile'] as JSON))
                return
            }
            int n = roleProfileService.applyProfileToUsers(profileId, isTrulyAdmin())
            render([ok: true, updated: n, profileId: profileId] as JSON)
        } catch (SecurityException e) {
            render(status: 403, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    private User currentUser() {
        springSecurityService.currentUser as User
    }

    /** True Admin profile (not merely CEO who also has ROLE_ADMIN). */
    private boolean isTrulyAdmin() {
        User u = currentUser()
        if (u?.roleProfileId == 'admin') return true
        Collection auths = springSecurityService.authentication?.authorities*.authority ?: []
        auths.contains('ROLE_ADMIN') && !auths.contains('ROLE_CEO')
    }

    private boolean isAdmin() {
        springSecurityService.authentication?.authorities*.authority?.contains('ROLE_ADMIN')
    }

    private boolean isLastAdmin(User target) {
        boolean targetIsAdmin = target?.roleProfileId == 'admin' ||
                (target?.authorities*.authority?.contains('ROLE_ADMIN') &&
                        !target?.authorities*.authority?.contains('ROLE_CEO'))
        if (!targetIsAdmin) return false
        int admins = User.list().count { User x ->
            x.enabled && (x.roleProfileId == 'admin' ||
                    (x.authorities*.authority?.contains('ROLE_ADMIN') &&
                            !x.authorities*.authority?.contains('ROLE_CEO')))
        } as int
        admins <= 1
    }

    private void establishSession(User user, boolean viaNostr) {
        try {
            request?.changeSessionId()
        } catch (Exception ignored) {
        }
        List authorities = user.authorities.collect { new SimpleGrantedAuthority(it.authority) }
        def token = new UsernamePasswordAuthenticationToken(user.username, null, authorities)
        SecurityContextHolder.context.authentication = token
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.context)
        if (viaNostr) {
            session.setAttribute(TotpGateFilter.SESSION_AUTH_VIA_NOSTR, Boolean.TRUE)
        } else if (!user.totpEnabled) {
            session.setAttribute(TotpGateFilter.SESSION_TOTP_OK, Boolean.TRUE)
        }
    }

    /** Rotate the session and record a fresh authenticator step-up. */
    private void markStepUp() {
        try {
            request?.changeSessionId()
        } catch (Exception ignored) {
        }
        session.setAttribute(TotpGateFilter.SESSION_TOTP_OK, Boolean.TRUE)
        session.setAttribute(TotpGateFilter.SESSION_TOTP_AT, System.currentTimeMillis())
    }
}
