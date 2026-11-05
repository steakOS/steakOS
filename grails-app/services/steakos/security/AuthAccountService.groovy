// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityService
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import steakos.Role
import steakos.User
import steakos.UserRole
import steakos.agent.AgentApiToken
import steakos.configuration.UserCompanyMembership

/**
 * AUTH01–04 account helpers: TOTP enrol, users for CEO, roles.
 */
@Slf4j
@Transactional
class AuthAccountService {

    static final String ROLE_CEO = 'ROLE_CEO'
    static final String ROLE_ADMIN = 'ROLE_ADMIN'
    static final String ROLE_USER = 'ROLE_USER'

    TotpService totpService = new TotpService()
    SpringSecurityService springSecurityService
    steakos.configuration.CompanyContextService companyContextService

    Role ensureRole(String authority) {
        Role r = Role.findByAuthority(authority)
        if (!r) {
            r = new Role(authority: authority).save(failOnError: true)
        }
        r
    }

    User findByUsername(String username) {
        User.findByUsername(username?.trim())
    }

    User findByNpub(String npub) {
        if (!npub?.trim()) return null
        User.findByNpub(npub.trim())
    }

    Map beginTotpEnrol(User user) {
        String secret = totpService.generateSecret()
        user.totpSecret = secret
        user.totpEnabled = false
        user.save(failOnError: true, flush: true)
        String uri = totpService.otpAuthUri(secret, user.username)
        [
                secret   : secret,
                otpauth  : uri,
                qrDataUrl: totpService.qrDataUrl(uri),
                username : user.username
        ]
    }

    Map confirmTotpEnrol(User user, String code) {
        if (!user.totpSecret) {
            throw new IllegalStateException('start enrolment first')
        }
        if (!totpService.verifyCode(user.totpSecret, code)) {
            throw new IllegalArgumentException('invalid authenticator code')
        }
        List<String> codes = totpService.generateRecoveryCodes()
        user.totpEnabled = true
        user.totpRecoveryCodes = JsonOutput.toJson(codes)
        user.save(failOnError: true, flush: true)
        [ok: true, recoveryCodes: codes]
    }

    boolean verifyTotpOrRecovery(User user, String code) {
        if (!user?.totpEnabled || !user.totpSecret) {
            return true
        }
        if (totpService.verifyCode(user.totpSecret, code)) {
            return true
        }
        return consumeRecoveryCode(user, code)
    }

    private boolean consumeRecoveryCode(User user, String code) {
        if (!user.totpRecoveryCodes || !code) return false
        String normalized = code.trim()
        List list
        try {
            list = new JsonSlurper().parseText(user.totpRecoveryCodes) as List
        } catch (ignored) {
            return false
        }
        int idx = list.findIndexOf { String c -> c == normalized }
        if (idx < 0) return false
        list.remove(idx)
        user.totpRecoveryCodes = JsonOutput.toJson(list)
        user.save(failOnError: true, flush: true)
        true
    }

    void clearTotp(User user) {
        user.totpSecret = null
        user.totpEnabled = false
        user.totpRecoveryCodes = null
        user.save(failOnError: true, flush: true)
    }

    User createOrUpdateUser(String username, String password, List<String> authorities, String displayName = null) {
        String u = username?.trim()?.toLowerCase()
        if (!u || !password) {
            throw new IllegalArgumentException('username and password required')
        }
        PasswordPolicy.requireValid(password)
        User user = User.findByUsername(u)
        if (!user) {
            // Share default tenant key with admin (small-team alpha)
            user = new User(username: u, password: password, enabled: true, npub: 'user:admin')
        } else {
            user.password = password
        }
        if (!user.npub) {
            user.npub = 'user:admin'
        }
        user.displayName = displayName ?: user.displayName
        user.springSecurityService = springSecurityService
        user.save(failOnError: true, flush: true)
        authorities?.each { String auth ->
            Role role = ensureRole(auth)
            if (!UserRole.findByUserAndRole(user, role)) {
                UserRole.create(user, role, true)
            }
        }
        // PDR-026 — new users get membership on all companies (D5)
        try {
            companyContextService?.ensureBootstrapped()
            companyContextService?.companiesFor(user)?.each { le ->
                boolean isDef = le?.code == steakos.configuration.LegalEntity.CODE_PRIMARY
                companyContextService.ensureMembership(user, le, isDef)
            }
        } catch (ignored) {
        }
        user
    }

    List<Map> listUsers() {
        User.list(sort: 'username').collect { userToMap(it as User) }
    }

    Map userToMap(User u) {
        if (!u) return null
        [
                id            : u.id,
                username      : u.username,
                displayName   : u.displayName,
                enabled       : u.enabled,
                totpEnabled   : u.totpEnabled,
                hasNpub       : u.npub != null && !u.npub.isEmpty() && !u.npub.startsWith('user:'),
                authorities   : (u.authorities*.authority as List),
                roleProfileId : u.roleProfileId
        ]
    }

    void setUserRoles(User user, List<String> authorities) {
        UserRole.removeAll(user)
        authorities.each { String a ->
            UserRole.create(user, ensureRole(a), true)
        }
    }

    void setEnabled(User user, boolean enabled) {
        user.enabled = enabled
        user.save(failOnError: true, flush: true)
    }

    /** Persist per-user permission override JSON inside a GORM transaction. */
    User setPermissionOverride(User user, String permissionOverrideJson) {
        if (!user) throw new IllegalArgumentException('user required')
        user.permissionOverrideJson = permissionOverrideJson
        user.save(failOnError: true, flush: true)
        user
    }

    /**
     * Permanently delete every login with {@code enabled=false}.
     * Clears user_role, company memberships, and agent API tokens first.
     * @param skipUserId optional current principal — never delete yourself even if disabled
     */
    Map deleteDisabledUsers(Long skipUserId = null) {
        List<User> victims = User.findAllByEnabled(false, [sort: 'username']) as List<User>
        List<Map> deleted = []
        List<String> skipped = []
        for (User u : victims) {
            if (skipUserId != null && u.id == skipUserId) {
                skipped << (u.username ?: String.valueOf(u.id))
                continue
            }
            String name = u.username
            Long id = u.id
            UserRole.removeAll(u)
            UserCompanyMembership.where { user == u }.deleteAll()
            AgentApiToken.where { user == u }.deleteAll()
            u.delete(flush: true)
            deleted << [id: id, username: name]
        }
        [
                ok      : true,
                count   : deleted.size(),
                deleted : deleted,
                skipped : skipped
        ]
    }

    /** Change password (encodes via User.beforeUpdate). Enforces {@link PasswordPolicy}. */
    User changePassword(User user, String rawPassword) {
        if (!user) {
            throw new IllegalArgumentException('user required')
        }
        PasswordPolicy.requireValid(rawPassword)
        user.password = rawPassword
        user.springSecurityService = springSecurityService
        user.save(failOnError: true, flush: true)
        user
    }
}
