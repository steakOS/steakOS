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
import grails.plugin.springsecurity.annotation.Secured
import groovy.util.logging.Slf4j
import steakos.configuration.RequiresAdvanced
import steakos.configuration.TenantNpubService
import steakos.sales.CrmActivity
import steakos.sales.Opportunity
import steakos.sales.SalesCrmService

/**
 * SalesAdvanced light CRM API — /api/sales/crm (OI-S08 / PR-4b).
 * Gated SILVER+ via AdvancedRouteCatalog + {@link RequiresAdvanced}.
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
@RequiresAdvanced(family = 'sales')
class SalesCrmApiController {

    static allowedMethods = [
            list            : 'GET',
            create          : 'POST',
            show            : 'GET',
            update          : 'PUT',
            delete          : 'DELETE',
            markLost        : 'POST',
            convert         : 'POST',
            listActivities  : 'GET',
            createActivity  : 'POST',
            updateActivity  : 'PUT',
            deleteActivity  : 'DELETE'
    ]

    SalesCrmService salesCrmService
    TenantNpubService tenantNpubService

    /** GET /api/sales/crm/opportunities */
    def list() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            boolean activeOnly = params.active == null || params.boolean('active')
            boolean openOnly = params.boolean('open')
            Long customerId = params.customerId ? params.long('customerId') : null
            Map result = salesCrmService.listOpportunitiesPage(
                    npub,
                    activeOnly,
                    page.max as int,
                    page.offset as int,
                    params.q as String,
                    params.stage as String,
                    customerId,
                    openOnly
            )
            ListPaging.envelope(
                    (result.items as List).collect { salesCrmService.toMap(it as Opportunity, false) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc', q: params.q, stage: params.stage]
            )
        }
    }

    /** POST /api/sales/crm/opportunities */
    def create() {
        wrapBody { String npub, Map body ->
            Opportunity opp = salesCrmService.createOpportunity(npub, body)
            [status: 201, body: salesCrmService.toMap(opp, false)]
        }
    }

    /** GET /api/sales/crm/opportunities/$id */
    def show() {
        wrap { String npub ->
            salesCrmService.toMap(salesCrmService.getOpportunity(params.long('id'), npub), true)
        }
    }

    /** PUT /api/sales/crm/opportunities/$id */
    def update() {
        wrapBody { String npub, Map body ->
            salesCrmService.toMap(
                    salesCrmService.updateOpportunity(params.long('id'), npub, body), true)
        }
    }

    /** DELETE /api/sales/crm/opportunities/$id — soft deactivate unless hard=true */
    def delete() {
        wrap { String npub ->
            boolean hard = params.boolean('hard')
            if (hard) {
                salesCrmService.deleteOpportunity(params.long('id'), npub)
                [ok: true, deleted: true]
            } else {
                salesCrmService.toMap(salesCrmService.deactivate(params.long('id'), npub), false)
            }
        }
    }

    /** POST /api/sales/crm/opportunities/$id/lost */
    def markLost() {
        wrapBody { String npub, Map body ->
            salesCrmService.toMap(salesCrmService.markLost(params.long('id'), npub, body), true)
        }
    }

    /** POST /api/sales/crm/opportunities/$id/convert — → draft quote + sales.opportunity.won */
    def convert() {
        wrapBody { String npub, Map body ->
            Map result = salesCrmService.convertToQuote(params.long('id'), npub, body)
            [status: result.alreadyConverted ? 200 : 201, body: result]
        }
    }

    /** GET /api/sales/crm/opportunities/$id/activities */
    def listActivities() {
        wrap { String npub ->
            [
                    opportunityId: params.long('id'),
                    items        : salesCrmService.listActivities(params.long('id'), npub)
                            .collect { salesCrmService.activityToMap(it) }
            ]
        }
    }

    /** POST /api/sales/crm/opportunities/$id/activities */
    def createActivity() {
        wrapBody { String npub, Map body ->
            CrmActivity act = salesCrmService.createActivity(params.long('id'), npub, body)
            [status: 201, body: salesCrmService.activityToMap(act)]
        }
    }

    /** PUT /api/sales/crm/activities/$activityId */
    def updateActivity() {
        wrapBody { String npub, Map body ->
            salesCrmService.activityToMap(
                    salesCrmService.updateActivity(params.long('activityId'), npub, body))
        }
    }

    /** DELETE /api/sales/crm/activities/$activityId */
    def deleteActivity() {
        wrap { String npub ->
            salesCrmService.deleteActivity(params.long('activityId'), npub)
            [ok: true, deleted: true]
        }
    }

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message, code: 'CRM_BAD_REQUEST'] as JSON))
        } catch (Exception e) {
            log.error('SalesCrm API error', e)
            render(status: 500, text: ([error: e.message, code: 'CRM_ERROR'] as JSON))
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
            render(status: 400, text: ([error: e.message, code: 'CRM_BAD_REQUEST'] as JSON))
        } catch (Exception e) {
            log.error('SalesCrm API error', e)
            render(status: 500, text: ([error: e.message, code: 'CRM_ERROR'] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
