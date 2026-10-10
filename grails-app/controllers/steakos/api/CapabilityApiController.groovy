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
import steakos.capability.*

/**
 * Capability API (CAP-S0–CAP-S3): /api/capability/*
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class CapabilityApiController {

    CapProcessService capProcessService
    CapPortfolioService capPortfolioService
    CapProjectService capProjectService
    CapQualityService capQualityService
    CapChangeService capChangeService
    CapKnowledgeService capKnowledgeService
    CapStandardService capStandardService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    // ----- Processes -----
    def processList() {
        wrap { String npub ->
            String kind = params.kind ?: params.processKind
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = capProcessService.listProcessesPage(
                    npub, params.status as String, kind as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { capProcessService.toProcessMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def processCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: capProcessService.toProcessMap(capProcessService.createProcess(npub, body))]
        }
    }

    def processShow() {
        wrap { String npub ->
            def p = capProcessService.getProcess(params.long('id'), npub)
            Map m = capProcessService.toProcessMap(p)
            m.mermaid = capProcessService.toMermaid(npub, p.id)
            m.steps = capProcessService.listSteps(npub, p.id).collect { capProcessService.toStepMap(it) }
            m
        }
    }

    def processByCode() {
        wrap { String npub ->
            String code = params.code ?: params.processCode
            def p = capProcessService.findByCode(npub, code)
            if (!p) {
                throw new IllegalArgumentException("process not found: ${code}")
            }
            Map m = capProcessService.toProcessMap(p)
            m.mermaid = capProcessService.toMermaid(npub, p.id)
            m.steps = capProcessService.listSteps(npub, p.id).collect { capProcessService.toStepMap(it) }
            m
        }
    }

    def processUpdate() {
        wrapBody { String npub, Map body ->
            capProcessService.toProcessMap(capProcessService.updateProcess(params.long('id'), npub, body))
        }
    }

    def processActivate() {
        wrapBody { String npub, Map body ->
            capProcessService.toProcessMap(capProcessService.activate(params.long('id'), npub))
        }
    }

    def processRetire() {
        wrapBody { String npub, Map body ->
            capProcessService.toProcessMap(capProcessService.retire(params.long('id'), npub))
        }
    }

    def processSteps() {
        wrap { String npub ->
            [npubOwner: npub, items: capProcessService.listSteps(npub, params.long('id')).collect { capProcessService.toStepMap(it) }]
        }
    }

    def processStepCreate() {
        wrapBody { String npub, Map body ->
            body.processId = params.long('id')
            [status: 201, body: capProcessService.toStepMap(capProcessService.addStep(npub, body))]
        }
    }

    def processMermaid() {
        wrap { String npub ->
            [
                    processId: params.long('id'),
                    mermaid  : capProcessService.toMermaid(npub, params.long('id'))
            ]
        }
    }

    /** POST — reseed SYSTEM catalogue (D4 admin). */
    def processReseed() {
        wrapBody { String npub, Map body ->
            capProcessService.seedSystemProcesses(npub)
        }
    }

    def procedureList() {
        wrap { String npub ->
            Long pid = params.processId ? params.long('processId') : null
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = capProcessService.listProceduresPage(npub, pid, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { capProcessService.toProcedureMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def procedureCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: capProcessService.toProcedureMap(capProcessService.createProcedure(npub, body))]
        }
    }

    // ----- Portfolios -----
    def portfolioList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = capPortfolioService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { capPortfolioService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def portfolioCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: capPortfolioService.toMap(capPortfolioService.create(npub, body))]
        }
    }

    def portfolioShow() {
        wrap { String npub -> capPortfolioService.toMap(capPortfolioService.get(params.long('id'), npub)) }
    }

    def portfolioClose() {
        wrapBody { String npub, Map body ->
            capPortfolioService.toMap(capPortfolioService.close(params.long('id'), npub))
        }
    }

    // ----- Programs -----
    def programList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = capProjectService.listProgramsPage(npub, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { capProjectService.toProgramMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def programCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: capProjectService.toProgramMap(capProjectService.createProgram(npub, body))]
        }
    }

    // ----- Projects -----
    def projectList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = capProjectService.listProjectsPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { capProjectService.toProjectMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def projectOpen() {
        wrap { String npub ->
            [npubOwner: npub, items: capProjectService.listOpen(npub).collect { capProjectService.toProjectMap(it) }]
        }
    }

    def projectCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: capProjectService.toProjectMap(capProjectService.createProject(npub, body))]
        }
    }

    def projectShow() {
        wrap { String npub -> capProjectService.toProjectMap(capProjectService.getProject(params.long('id'), npub)) }
    }

    def projectStart() {
        wrapBody { String npub, Map body ->
            capProjectService.toProjectMap(capProjectService.start(params.long('id'), npub))
        }
    }

    def projectComplete() {
        wrapBody { String npub, Map body ->
            capProjectService.toProjectMap(capProjectService.complete(params.long('id'), npub))
        }
    }

    def projectCancel() {
        wrapBody { String npub, Map body ->
            capProjectService.toProjectMap(capProjectService.cancel(params.long('id'), npub))
        }
    }

    def projectItems() {
        wrap { String npub ->
            [npubOwner: npub, items: capProjectService.listItems(npub, params.long('id')).collect { capProjectService.toItemMap(it) }]
        }
    }

    def projectItemCreate() {
        wrapBody { String npub, Map body ->
            body.projectId = params.long('id')
            [status: 201, body: capProjectService.toItemMap(capProjectService.addItem(npub, body))]
        }
    }

    // ----- Quality -----
    def qualityList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = capQualityService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { capQualityService.toQualityMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def qualityCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: capQualityService.toQualityMap(capQualityService.createQuality(npub, body))]
        }
    }

    def qualityShow() {
        wrap { String npub -> capQualityService.toQualityMap(capQualityService.get(params.long('id'), npub)) }
    }

    def qualityArchive() {
        wrapBody { String npub, Map body ->
            capQualityService.toQualityMap(capQualityService.archive(params.long('id'), npub))
        }
    }

    def qualityItems() {
        wrap { String npub ->
            Long qid = params.id ? params.long('id') : null
            [npubOwner: npub, items: capQualityService.listItems(npub, qid).collect { capQualityService.toItemMap(it) }]
        }
    }

    def qualityItemCreate() {
        wrapBody { String npub, Map body ->
            body.qualityId = params.long('id')
            [status: 201, body: capQualityService.toItemMap(capQualityService.addItem(npub, body))]
        }
    }

    def qualityItemClose() {
        wrapBody { String npub, Map body ->
            capQualityService.toItemMap(capQualityService.closeItem(params.long('id'), npub))
        }
    }

    // ----- Org Changes -----
    def changeList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = capChangeService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { capChangeService.toChangeMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def changeOpen() {
        wrap { String npub ->
            [npubOwner: npub, items: capChangeService.listOpen(npub).collect { capChangeService.toChangeMap(it) }]
        }
    }

    def changeCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: capChangeService.toChangeMap(capChangeService.createChange(npub, body))]
        }
    }

    def changeShow() {
        wrap { String npub -> capChangeService.toChangeMap(capChangeService.get(params.long('id'), npub)) }
    }

    def changeImplement() {
        wrapBody { String npub, Map body ->
            capChangeService.toChangeMap(capChangeService.implement(params.long('id'), npub))
        }
    }

    def changeClose() {
        wrapBody { String npub, Map body ->
            capChangeService.toChangeMap(capChangeService.close(params.long('id'), npub))
        }
    }

    def changeCancel() {
        wrapBody { String npub, Map body ->
            capChangeService.toChangeMap(capChangeService.cancel(params.long('id'), npub))
        }
    }

    def changeItems() {
        wrap { String npub ->
            [npubOwner: npub, items: capChangeService.listItems(npub, params.long('id')).collect { capChangeService.toItemMap(it) }]
        }
    }

    def changeItemCreate() {
        wrapBody { String npub, Map body ->
            body.changeId = params.long('id')
            [status: 201, body: capChangeService.toItemMap(capChangeService.addItem(npub, body))]
        }
    }

    // ----- Knowledge -----
    def knowledgeList() {
        wrap { String npub ->
            boolean activeOnly = params.activeOnly == 'true'
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = capKnowledgeService.listPage(npub, activeOnly, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { capKnowledgeService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc']
            )
        }
    }

    def knowledgeCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: capKnowledgeService.toMap(capKnowledgeService.create(npub, body))]
        }
    }

    def knowledgeShow() {
        wrap { String npub -> capKnowledgeService.toMap(capKnowledgeService.get(params.long('id'), npub)) }
    }

    def knowledgeChildren() {
        wrap { String npub ->
            [npubOwner: npub, items: capKnowledgeService.listChildren(npub, params.long('id')).collect { capKnowledgeService.toMap(it) }]
        }
    }

    // ----- Standards -----
    def standardList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = capStandardService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { capStandardService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def standardCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: capStandardService.toMap(capStandardService.create(npub, body))]
        }
    }

    def standardShow() {
        wrap { String npub -> capStandardService.toMap(capStandardService.get(params.long('id'), npub)) }
    }

    def standardUpdate() {
        wrapBody { String npub, Map body ->
            capStandardService.toMap(capStandardService.update(params.long('id'), npub, body))
        }
    }

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('Capability API error', e)
            render(status: 500, text: ([code: 'CAPABILITY_ERROR', error: e.message] as JSON))
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
            log.error('Capability API error', e)
            render(status: 500, text: ([code: 'CAPABILITY_ERROR', error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
