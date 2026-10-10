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
import steakos.agent.AgentCapabilityService
import steakos.agent.AgentGatewayService
import steakos.agents.AgApprovalService

/**
 * ADR-037 P1–P3 — Hermes agent gateway: me, L0 summary, capabilities, L1 proposals.
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class AgentGatewayApiController {

    static allowedMethods = [
            me           : 'GET',
            l0Summary    : 'GET',
            capabilities : 'GET',
            proposalList : 'GET',
            proposalShow : 'GET',
            proposalCreate: 'POST'
    ]

    AgentGatewayService agentGatewayService
    AgentCapabilityService agentCapabilityService
    AgApprovalService agApprovalService
    SpringSecurityService springSecurityService

    /** GET /api/agent/me */
    def me() {
        render(agentGatewayService.me(currentUsername(), linkedTenant()) as JSON)
    }

    /** GET /api/agent/l0/summary */
    def l0Summary() {
        render(agentGatewayService.l0Summary(linkedTenant()) as JSON)
    }

    /** GET /api/agent/capabilities — P2 catalog */
    def capabilities() {
        render([
                version     : '1',
                lanes       : AgentGatewayService.DEFAULT_LANES,
                capabilities: agentCapabilityService.catalog()
        ] as JSON)
    }

    /** GET /api/agent/proposals?decision=PENDING */
    def proposalList() {
        Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
        Map result = agentGatewayService.listProposals(
                linkedTenant(), params.decision as String, page.max as int, page.offset as int)
        render(ListPaging.envelope(
                (result.items as List).collect { agApprovalService.toMap(it) },
                result.total as int,
                page.max as int,
                page.offset as int,
                [npubOwner: linkedTenant(), decision: params.decision]
        ) as JSON)
    }

    /** GET /api/agent/proposals/$id */
    def proposalShow() {
        try {
            Long id = params.long('id')
            render(agApprovalService.toMap(agentGatewayService.getProposal(id, linkedTenant())) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 404, text: ([error: e.message, code: 'NOT_FOUND'] as JSON))
        }
    }

    /**
     * POST /api/agent/proposals — L1 propose (creates HITL Approval PENDING).
     * body: { name, description?, riskLevel?, targetFamily?, proposalJson?, action?, payload? }
     */
    def proposalCreate() {
        if (isAdminOnlyHumanWithoutPropose()) {
            // humans may also propose; agents always may
        }
        try {
            Map body = (request.JSON as Map) ?: [:]
            def a = agentGatewayService.propose(linkedTenant(), body, currentUsername())
            render(status: 201, text: (agApprovalService.toMap(a) as JSON))
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message, code: 'PROPOSAL_FAILED'] as JSON))
        }
    }

    private String linkedTenant() {
        // Prefer request attribute set by AgentBearerTokenFilter
        try {
            String attr = request.getAttribute('steakos.linkedTenantKey') as String
            if (attr?.trim()) return attr.trim()
        } catch (ignored) {
        }
        User u = springSecurityService?.currentUser as User
        if (u?.npub?.trim()) return u.npub.trim()
        if (u?.username) return "user:${u.username}"
        return 'user:admin'
    }

    private String currentUsername() {
        try {
            String attr = request.getAttribute('steakos.agentUsername') as String
            if (attr?.trim()) return attr.trim()
        } catch (ignored) {
        }
        User u = springSecurityService?.currentUser as User
        u?.username ?: springSecurityService?.principal?.username ?: 'unknown'
    }

    private boolean isAdminOnlyHumanWithoutPropose() {
        false
    }
}
