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
import groovy.util.logging.Slf4j
import steakos.User
import steakos.api.ListPaging
import steakos.agents.*

/**
 * Agentic Resources API (AR-S0–AR-S3): /api/agents/*
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class AgentsApiController {

    AgAgentService agAgentService
    AgHiveService agHiveService
    AgApprovalService agApprovalService
    AgMemoryService agMemoryService
    AgLinkService agLinkService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    // ----- Agents -----
    def agentList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = agAgentService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { agAgentService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc']
            )
        }
    }

    def agentCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: agAgentService.toMap(agAgentService.create(npub, body))]
        }
    }

    def agentShow() {
        wrap { String npub -> agAgentService.toMap(agAgentService.get(params.long('id'), npub)) }
    }

    def agentUpdate() {
        wrapBody { String npub, Map body ->
            agAgentService.toMap(agAgentService.update(params.long('id'), npub, body))
        }
    }

    def agentActivate() {
        wrapBody { String npub, Map body ->
            agAgentService.toMap(agAgentService.activate(params.long('id'), npub))
        }
    }

    def agentPause() {
        wrapBody { String npub, Map body ->
            agAgentService.toMap(agAgentService.pause(params.long('id'), npub))
        }
    }

    def agentRetire() {
        wrapBody { String npub, Map body ->
            agAgentService.toMap(agAgentService.retire(params.long('id'), npub))
        }
    }

    def agentSeedCore() {
        wrapBody { String npub, Map body ->
            [items: agAgentService.seedCoreAgents(npub).collect { agAgentService.toMap(it) }]
        }
    }

    // ----- Hives -----
    def hiveList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = agHiveService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { agHiveService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def hiveCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: agHiveService.toMap(agHiveService.create(npub, body))]
        }
    }

    def hiveShow() {
        wrap { String npub -> agHiveService.toMap(agHiveService.get(params.long('id'), npub)) }
    }

    def hiveSetMaster() {
        wrapBody { String npub, Map body ->
            Long agentId = body.agentId as Long
            if (!agentId) throw new IllegalArgumentException('agentId is required')
            agHiveService.toMap(agHiveService.setHiveMaster(params.long('id'), npub, agentId))
        }
    }

    def hiveAssign() {
        wrapBody { String npub, Map body ->
            Long agentId = body.agentId as Long
            if (!agentId) throw new IllegalArgumentException('agentId is required')
            agAgentService.toMap(agHiveService.assignAgent(params.long('id'), npub, agentId))
        }
    }

    def hiveMembers() {
        wrap { String npub ->
            [npubOwner: npub, items: agHiveService.listMembers(npub, params.long('id')).collect { agAgentService.toMap(it) }]
        }
    }

    def hiveActivate() {
        wrapBody { String npub, Map body ->
            agHiveService.toMap(agHiveService.activate(params.long('id'), npub))
        }
    }

    def hiveClose() {
        wrapBody { String npub, Map body ->
            agHiveService.toMap(agHiveService.close(params.long('id'), npub))
        }
    }

    // ----- HITL Approvals -----
    def approvalList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = agApprovalService.listPage(npub, params.decision as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { agApprovalService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def approvalOpen() {
        wrap { String npub ->
            [npubOwner: npub, items: agApprovalService.listOpen(npub).collect { agApprovalService.toMap(it) }]
        }
    }

    def approvalCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: agApprovalService.toMap(agApprovalService.submit(npub, body))]
        }
    }

    def approvalShow() {
        wrap { String npub -> agApprovalService.toMap(agApprovalService.get(params.long('id'), npub)) }
    }

    def approvalApprove() {
        wrapBody { String npub, Map body ->
            agApprovalService.toMap(agApprovalService.approve(params.long('id'), npub, body))
        }
    }

    def approvalReject() {
        wrapBody { String npub, Map body ->
            agApprovalService.toMap(agApprovalService.reject(params.long('id'), npub, body))
        }
    }

    def approvalCancel() {
        wrapBody { String npub, Map body ->
            agApprovalService.toMap(agApprovalService.cancel(params.long('id'), npub))
        }
    }

    // ----- Memory -----
    def memoryList() {
        wrap { String npub ->
            boolean archived = params.includeArchived == 'true'
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = agMemoryService.listPage(npub, params.kind as String, archived, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { agMemoryService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def memoryCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: agMemoryService.toMap(agMemoryService.record(npub, body))]
        }
    }

    def memoryShow() {
        wrap { String npub -> agMemoryService.toMap(agMemoryService.get(params.long('id'), npub)) }
    }

    def memoryArchive() {
        wrapBody { String npub, Map body ->
            agMemoryService.toMap(agMemoryService.archive(params.long('id'), npub))
        }
    }

    def memoryByAgent() {
        wrap { String npub ->
            [npubOwner: npub, items: agMemoryService.listByAgent(npub, params.long('id')).collect { agMemoryService.toMap(it) }]
        }
    }

    // ----- Links -----
    def linkList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = agLinkService.listPage(npub, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { agLinkService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def linkCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: agLinkService.toMap(agLinkService.link(npub, body))]
        }
    }

    def linkUnlink() {
        wrapBody { String npub, Map body ->
            agLinkService.toMap(agLinkService.unlink(params.long('id'), npub))
        }
    }

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('Agents API error', e)
            render(status: 500, text: ([code: 'AGENTS_ERROR', error: e.message] as JSON))
        }
    }

    private void wrapBody(Closure c) {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            def result = c.call(npub, body)
            if (result instanceof Map && result.status && result.body != null) {
                render(status: result.status as int, text: (result.body as JSON))
            } else {
                render(result as JSON)
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('Agents API error', e)
            render(status: 500, text: ([code: 'AGENTS_ERROR', error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
