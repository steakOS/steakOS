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

package steakos.api

import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import steakos.User
import steakos.agent.AgentTokenService

/**
 * ADR-037 P0 — mint / list / revoke agent opaque bearer tokens.
 * Human admin session only (not agent self-service for mint).
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class AgentTokenApiController {

    static allowedMethods = [
            list   : 'GET',
            mint   : 'POST',
            revoke : 'POST',
            ensure : 'POST'
    ]

    AgentTokenService agentTokenService
    SpringSecurityService springSecurityService

    /** GET /api/agent/tokens */
    def list() {
        if (!requireHumanAdmin()) {
            return
        }
        boolean all = params.boolean('includeRevoked')
        render([tokens: agentTokenService.listTokens(all)] as JSON)
    }

    /**
     * POST /api/agent/tokens
     * body: { username, label?, hostLabel?, linkedTenantKey? }
     */
    def mint() {
        if (!requireHumanAdmin()) {
            return
        }
        try {
            Map body = (request.JSON as Map) ?: [:]
            String username = body.username as String
            if (!username?.trim()) {
                render(status: 400, text: ([error: 'username required', code: 'AGENT_USERNAME_REQUIRED'] as JSON))
                return
            }
            Map result = agentTokenService.mint(
                    username,
                    body.label as String,
                    body.hostLabel as String,
                    body.linkedTenantKey as String,
                    currentUsername()
            )
            render([ok: true, result: result] as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'AGENT_TOKEN_MINT_FAILED'] as JSON))
        } catch (Exception e) {
            render(status: 500, text: ([error: e.message, code: 'AGENT_TOKEN_MINT_FAILED'] as JSON))
        }
    }

    /** POST /api/agent/tokens/$id/revoke */
    def revoke() {
        if (!requireHumanAdmin()) {
            return
        }
        try {
            Long id = params.long('id')
            if (!id) {
                render(status: 400, text: ([error: 'id required'] as JSON))
                return
            }
            Map result = agentTokenService.revoke(id, currentUsername())
            render([ok: true, result: result] as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 404, text: ([error: e.message, code: 'AGENT_TOKEN_NOT_FOUND'] as JSON))
        }
    }

    /**
     * POST /api/agent/users
     * body: { username, linkedTenantKey? } — ensure agent User exists (no token).
     */
    def ensure() {
        if (!requireHumanAdmin()) {
            return
        }
        try {
            Map body = (request.JSON as Map) ?: [:]
            String username = body.username as String
            if (!username?.trim()) {
                render(status: 400, text: ([error: 'username required'] as JSON))
                return
            }
            User u = agentTokenService.ensureAgentUser(username, body.linkedTenantKey as String)
            render([ok: true, username: u.username, id: u.id] as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    private boolean requireHumanAdmin() {
        // Prefer ROLE_ADMIN; allow any authenticated human for Iron solo (admin only in practice)
        def auth = springSecurityService?.authentication
        if (!auth?.authenticated) {
            render(status: 401, text: ([error: 'Unauthorized', code: 'UNAUTHORIZED'] as JSON))
            return false
        }
        // Block agent token self-mint: agent users have ROLE_AI — still allow if also ADMIN
        Collection authorities = auth.authorities*.authority
        boolean isAdmin = authorities?.contains('ROLE_ADMIN') || authorities?.contains('ROLE_C')
        if (!isAdmin) {
            // Solo SBM: admin is the only human with ROLE_ADMIN; agents have ROLE_AI+USER
            boolean isAgentOnly = authorities?.contains('ROLE_AI') && !authorities?.contains('ROLE_ADMIN')
            if (isAgentOnly) {
                render(status: 403, text: ([error: 'Agents cannot mint tokens', code: 'AGENT_TOKEN_FORBIDDEN'] as JSON))
                return false
            }
        }
        true
    }

    private String currentUsername() {
        User u = springSecurityService?.currentUser as User
        u?.username ?: springSecurityService?.principal?.username ?: 'admin'
    }
}
