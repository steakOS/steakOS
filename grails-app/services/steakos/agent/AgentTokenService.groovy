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

package steakos.agent

import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityService
import steakos.Role
import steakos.User
import steakos.UserRole
import steakos.security.RoleLevel

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat

/**
 * Mint / verify / revoke opaque bearer tokens for Hermes agents (ADR-037 P0).
 */
@Transactional
class AgentTokenService {

    SpringSecurityService springSecurityService

    private static final SecureRandom RANDOM = new SecureRandom()
    private static final HexFormat HEX = HexFormat.of()

    /**
     * Ensure ROLE_AI exists and create (or return) an agent User.
     * Password is random unusable for form login; auth is via bearer token.
     */
    User ensureAgentUser(String username, String linkedTenantKey = null) {
        String uname = normalizeAgentUsername(username)
        String tenant = (linkedTenantKey ?: resolveDefaultTenantKey(null))?.trim() ?: 'user:admin'
        User existing = User.findByUsername(uname)
        if (existing) {
            // Keep agent User.npub = linked tenant so domain APIs see human company data (ADR-037 D2)
            if (tenant && existing.npub != tenant) {
                existing.npub = tenant
                existing.save(failOnError: true)
            }
            return existing
        }

        Role aiRole = ensureRole(RoleLevel.AI.authority)
        Role userRole = ensureRole('ROLE_USER')

        String randomPw = randomSecret(24)
        User agent = new User(
                username: uname,
                password: randomPw,
                enabled: true,
                npub: tenant
        )
        agent.springSecurityService = springSecurityService
        agent.save(failOnError: true)
        UserRole.create(agent, aiRole, true)
        UserRole.create(agent, userRole, true)
        agent
    }

    /**
     * Mint a new opaque token. Returns map with plaintext {@code token} once.
     */
    Map mint(String username, String label, String hostLabel, String linkedTenantKey, String actor) {
        String tenant = (linkedTenantKey ?: resolveDefaultTenantKey(actor))?.trim()
        if (!tenant) {
            tenant = 'user:admin'
        }
        User agent = ensureAgentUser(username, tenant)

        String secret = 'sos_' + randomSecret(32)
        String hash = sha256Hex(secret)
        String prefix = secret.length() >= 12 ? secret.substring(0, 12) : secret

        AgentApiToken row = new AgentApiToken(
                tokenPrefix     : prefix,
                tokenHash       : hash,
                label           : (label ?: "${unameLabel(agent)} token").take(255),
                user            : agent,
                linkedTenantKey : tenant,
                hostLabel       : hostLabel?.trim() ?: null,
                revoked         : Boolean.FALSE,
                dateCreated     : new Date(),
                createdBy       : actor ?: 'admin'
        )
        row.save(failOnError: true)

        [
                id              : row.id,
                token           : secret,
                tokenPrefix     : prefix,
                label           : row.label,
                username        : agent.username,
                linkedTenantKey : row.linkedTenantKey,
                hostLabel       : row.hostLabel,
                dateCreated     : row.dateCreated?.time,
                warning         : 'Store this token now — it will not be shown again.'
        ]
    }

    List<Map> listTokens(boolean includeRevoked = false) {
        def c = AgentApiToken.createCriteria()
        List rows = c.list {
            if (!includeRevoked) {
                eq('revoked', Boolean.FALSE)
            }
            order('dateCreated', 'desc')
            maxResults(100)
        } as List
        rows.collect { AgentApiToken t -> toPublicMap(t) }
    }

    Map revoke(Long id, String actor) {
        AgentApiToken t = AgentApiToken.get(id)
        if (!t) {
            throw new IllegalArgumentException("Token not found: ${id}")
        }
        t.revoked = Boolean.TRUE
        t.save(failOnError: true)
        toPublicMap(t) + [revokedBy: actor]
    }

    /**
     * Verify bearer secret; returns agent User or null.
     */
    User authenticate(String rawToken) {
        if (!rawToken?.trim()) {
            return null
        }
        String hash = sha256Hex(rawToken.trim())
        AgentApiToken row = AgentApiToken.findByTokenHashAndRevoked(hash, Boolean.FALSE)
        if (!row) {
            return null
        }
        User u = row.user
        if (!u?.enabled || u.accountLocked || u.accountExpired) {
            return null
        }
        row.lastUsed = new Date()
        row.save(failOnError: false)
        u
    }

    /** Tenant key bound to a valid token (for future request attributes). */
    String linkedTenantForToken(String rawToken) {
        if (!rawToken?.trim()) return null
        AgentApiToken row = AgentApiToken.findByTokenHashAndRevoked(sha256Hex(rawToken.trim()), Boolean.FALSE)
        row?.linkedTenantKey
    }

    private static String normalizeAgentUsername(String username) {
        String u = username?.trim()
        if (!u) {
            throw new IllegalArgumentException('username required')
        }
        // Allow HiveA:bookkeeper, agent:navigator, etc.
        if (u.contains(' ') || u.length() > 128) {
            throw new IllegalArgumentException('invalid agent username')
        }
        u
    }

    private static String unameLabel(User u) {
        u?.username ?: 'agent'
    }

    private static String resolveDefaultTenantKey(String actor) {
        if (actor) {
            User human = User.findByUsername(actor)
            if (human?.npub) return human.npub
            if (human) return "user:${human.username}"
        }
        User admin = User.findByUsername('admin')
        if (admin?.npub) return admin.npub
        return 'user:admin'
    }

    private Role ensureRole(String authority) {
        Role r = Role.findByAuthority(authority)
        if (!r) {
            r = new Role(authority: authority).save(failOnError: true)
        }
        r
    }

    private static String randomSecret(int bytes) {
        byte[] buf = new byte[bytes]
        RANDOM.nextBytes(buf)
        HEX.formatHex(buf)
    }

    private static String sha256Hex(String s) {
        MessageDigest md = MessageDigest.getInstance('SHA-256')
        HEX.formatHex(md.digest(s.getBytes('UTF-8')))
    }

    private static Map toPublicMap(AgentApiToken t) {
        [
                id              : t.id,
                tokenPrefix     : t.tokenPrefix,
                label           : t.label,
                username        : t.user?.username,
                linkedTenantKey : t.linkedTenantKey,
                hostLabel       : t.hostLabel,
                revoked         : t.revoked,
                dateCreated     : t.dateCreated?.time,
                lastUsed        : t.lastUsed?.time,
                createdBy       : t.createdBy
        ]
    }
}
