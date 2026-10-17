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

package steakos.grrc

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import groovy.util.logging.Slf4j
import steakos.api.ListPaging
import steakos.configuration.TenantNpubService

/**
 * GRRCBasics API (G7): /api/grrc/*
 * Risk + compliance registers; gated SILVER+ / Iron eval (Bronze refused).
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class GrrcApiController {

    GrrcRiskService grrcRiskService
    GrrcComplianceService grrcComplianceService
    GrrcGateService grrcGateService
    TenantNpubService tenantNpubService

    // ----- Risks -----
    def riskList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = grrcRiskService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { grrcRiskService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def riskCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: grrcRiskService.toMap(grrcRiskService.create(npub, body))]
        }
    }

    def riskShow() {
        wrap { String npub -> grrcRiskService.toMap(grrcRiskService.get(params.long('id'), npub)) }
    }

    def riskUpdate() {
        wrapBody { String npub, Map body ->
            grrcRiskService.toMap(grrcRiskService.update(params.long('id'), npub, body))
        }
    }

    def riskDelete() {
        wrap { String npub ->
            grrcRiskService.delete(params.long('id'), npub)
            [ok: true, id: params.long('id')]
        }
    }

    def riskClose() {
        wrapBody { String npub, Map body ->
            grrcRiskService.toMap(grrcRiskService.close(params.long('id'), npub, body))
        }
    }

    // ----- Compliance -----
    def complianceList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = grrcComplianceService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { grrcComplianceService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def complianceCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: grrcComplianceService.toMap(grrcComplianceService.create(npub, body))]
        }
    }

    def complianceShow() {
        wrap { String npub -> grrcComplianceService.toMap(grrcComplianceService.get(params.long('id'), npub)) }
    }

    def complianceUpdate() {
        wrapBody { String npub, Map body ->
            grrcComplianceService.toMap(grrcComplianceService.update(params.long('id'), npub, body))
        }
    }

    def complianceDelete() {
        wrap { String npub ->
            grrcComplianceService.delete(params.long('id'), npub)
            [ok: true, id: params.long('id')]
        }
    }

    private void wrap(Closure c) {
        try {
            String npub = resolveNpub(params)
            grrcGateService.assertGrrcAllowed(npub)
            render(c.call(npub) as JSON)
        } catch (GrrcGateException e) {
            response.status = 403
            render(grrcGateService.denialBody(e.tier) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('GRRC API error', e)
            render(status: 500, text: ([code: 'GRRC_ERROR', error: e.message] as JSON))
        }
    }

    private void wrapBody(Closure c) {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            grrcGateService.assertGrrcAllowed(npub)
            def result = c.call(npub, body)
            if (result instanceof Map && result.status && result.body != null) {
                render(status: result.status as int, text: (result.body as JSON))
            } else {
                render(result as JSON)
            }
        } catch (GrrcGateException e) {
            response.status = 403
            render(grrcGateService.denialBody(e.tier) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('GRRC API error', e)
            render(status: 500, text: ([code: 'GRRC_ERROR', error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
