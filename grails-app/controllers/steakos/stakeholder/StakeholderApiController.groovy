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

package steakos.stakeholder

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import groovy.util.logging.Slf4j
import steakos.api.ListPaging
import steakos.configuration.TenantNpubService

/**
 * StakeholderBasics API — /api/stakeholder/* (Silver G7 / WP6).
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class StakeholderApiController {

    static allowedMethods = [
            partyList  : 'GET',
            partyCreate: 'POST',
            partyShow  : 'GET',
            partyUpdate: ['PUT', 'POST'],
            partyDelete: ['DELETE', 'POST'],
            commsList  : 'GET',
            commsCreate: 'POST',
            commsShow  : 'GET',
            commsUpdate: ['PUT', 'POST'],
            commsDelete: ['DELETE', 'POST']
    ]

    StakeholderBasicsService stakeholderBasicsService
    TenantNpubService tenantNpubService

    // ----- Parties -----

    def partyList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = stakeholderBasicsService.listPartiesPage(
                    npub, params.status as String, params.partyKind as String,
                    page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { stakeholderBasicsService.toPartyMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc',
                     status: params.status, partyKind: params.partyKind]
            )
        }
    }

    def partyCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: stakeholderBasicsService.toPartyMap(
                    stakeholderBasicsService.createParty(npub, body))]
        }
    }

    def partyShow() {
        wrap { String npub ->
            stakeholderBasicsService.toPartyMap(
                    stakeholderBasicsService.getParty(params.long('id'), npub))
        }
    }

    def partyUpdate() {
        wrapBody { String npub, Map body ->
            stakeholderBasicsService.toPartyMap(
                    stakeholderBasicsService.updateParty(params.long('id'), npub, body))
        }
    }

    def partyDelete() {
        wrap { String npub ->
            stakeholderBasicsService.deleteParty(params.long('id'), npub)
            [ok: true, id: params.long('id')]
        }
    }

    // ----- Comms -----

    def commsList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Long partyId = params.long('partyId') ?: params.long('stakeholderPartyId')
            Map result = stakeholderBasicsService.listCommsPage(
                    npub, partyId, params.status as String,
                    page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { stakeholderBasicsService.toCommsMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc',
                     partyId: partyId, status: params.status]
            )
        }
    }

    def commsCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: stakeholderBasicsService.toCommsMap(
                    stakeholderBasicsService.createComms(npub, body))]
        }
    }

    def commsShow() {
        wrap { String npub ->
            stakeholderBasicsService.toCommsMap(
                    stakeholderBasicsService.getComms(params.long('id'), npub))
        }
    }

    def commsUpdate() {
        wrapBody { String npub, Map body ->
            stakeholderBasicsService.toCommsMap(
                    stakeholderBasicsService.updateComms(params.long('id'), npub, body))
        }
    }

    def commsDelete() {
        wrap { String npub ->
            stakeholderBasicsService.deleteComms(params.long('id'), npub)
            [ok: true, id: params.long('id')]
        }
    }

    // ----- wrap -----

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('Stakeholder API error', e)
            render(status: 500, text: ([code: 'STAKEHOLDER_ERROR', error: e.message] as JSON))
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
            log.error('Stakeholder API error', e)
            render(status: 500, text: ([code: 'STAKEHOLDER_ERROR', error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
