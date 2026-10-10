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
import steakos.digital.*

/**
 * Digital Technology API (DIG-S0–DIG-S2): /api/digital/*
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class DigitalApiController {

    DigSystemService digSystemService
    DigDataService digDataService
    DigSolutionService digSolutionService
    DigChangeService digChangeService
    DigCmdbService digCmdbService
    DigSupportService digSupportService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    // ----- Systems -----
    def systemList() {
        wrap { String npub ->
            boolean activeOnly = params.activeOnly == 'true'
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = digSystemService.listPage(npub, activeOnly, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { digSystemService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc']
            )
        }
    }

    def systemCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: digSystemService.toMap(digSystemService.create(npub, body))]
        }
    }

    def systemShow() {
        wrap { String npub -> digSystemService.toMap(digSystemService.get(params.long('id'), npub)) }
    }

    def systemUpdate() {
        wrapBody { String npub, Map body ->
            digSystemService.toMap(digSystemService.update(params.long('id'), npub, body))
        }
    }

    // ----- Data -----
    def dataList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = digDataService.listPage(npub, params.dataClass as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { digDataService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc']
            )
        }
    }

    def dataCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: digDataService.toMap(digDataService.create(npub, body))]
        }
    }

    def dataShow() {
        wrap { String npub -> digDataService.toMap(digDataService.get(params.long('id'), npub)) }
    }

    // ----- Solutions -----
    def solutionList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = digSolutionService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { digSolutionService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def solutionCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: digSolutionService.toMap(digSolutionService.create(npub, body))]
        }
    }

    def solutionShow() {
        wrap { String npub -> digSolutionService.toMap(digSolutionService.get(params.long('id'), npub)) }
    }

    def solutionActivate() {
        wrapBody { String npub, Map body ->
            digSolutionService.toMap(digSolutionService.activate(params.long('id'), npub))
        }
    }

    def solutionRetire() {
        wrapBody { String npub, Map body ->
            digSolutionService.toMap(digSolutionService.retire(params.long('id'), npub))
        }
    }

    // ----- Changes -----
    def changeList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = digChangeService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { digChangeService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def changeOpen() {
        wrap { String npub ->
            [npubOwner: npub, items: digChangeService.listOpen(npub).collect { digChangeService.toMap(it) }]
        }
    }

    def changeCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: digChangeService.toMap(digChangeService.create(npub, body))]
        }
    }

    def changeStart() {
        wrapBody { String npub, Map body ->
            digChangeService.toMap(digChangeService.start(params.long('id'), npub))
        }
    }

    def changeComplete() {
        wrapBody { String npub, Map body ->
            digChangeService.toMap(digChangeService.complete(params.long('id'), npub))
        }
    }

    def changeCancel() {
        wrapBody { String npub, Map body ->
            digChangeService.toMap(digChangeService.cancel(params.long('id'), npub))
        }
    }

    // ----- CMDB -----
    def ciList() {
        wrap { String npub ->
            Long sid = params.systemId ? params.long('systemId') : null
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = digCmdbService.listPage(npub, sid, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { digCmdbService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc']
            )
        }
    }

    def ciCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: digCmdbService.toMap(digCmdbService.createCi(npub, body))]
        }
    }

    def ciShow() {
        wrap { String npub -> digCmdbService.toMap(digCmdbService.get(params.long('id'), npub)) }
    }

    // ----- Support -----
    def ticketList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = digSupportService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { digSupportService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def ticketOpen() {
        wrap { String npub ->
            [npubOwner: npub, items: digSupportService.listOpen(npub).collect { digSupportService.toMap(it) }]
        }
    }

    def ticketCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: digSupportService.toMap(digSupportService.openTicket(npub, body))]
        }
    }

    def ticketStart() {
        wrapBody { String npub, Map body ->
            digSupportService.toMap(digSupportService.startWork(params.long('id'), npub))
        }
    }

    def ticketResolve() {
        wrapBody { String npub, Map body ->
            digSupportService.toMap(digSupportService.resolve(params.long('id'), npub, body))
        }
    }

    def ticketClose() {
        wrapBody { String npub, Map body ->
            digSupportService.toMap(digSupportService.close(params.long('id'), npub))
        }
    }

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('Digital API error', e)
            render(status: 500, text: ([code: 'DIGITAL_ERROR', error: e.message] as JSON))
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
            log.error('Digital API error', e)
            render(status: 500, text: ([code: 'DIGITAL_ERROR', error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
