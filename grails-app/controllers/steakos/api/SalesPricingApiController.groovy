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
import steakos.sales.SalesPriceListService

/**
 * SalesAdvanced price lists API — /api/sales/pricelists (OI-S04 / PR-4a).
 * Gated SILVER+ via AdvancedRouteCatalog + {@link RequiresAdvanced}.
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
@RequiresAdvanced(family = 'sales')
class SalesPricingApiController {

    static allowedMethods = [
            list   : 'GET',
            create : 'POST',
            show   : 'GET',
            update : 'PUT',
            delete : 'DELETE',
            lines  : 'PUT',
            resolve: 'GET'
    ]

    SalesPriceListService salesPriceListService
    TenantNpubService tenantNpubService

    def list() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            boolean activeOnly = params.active == null || params.boolean('active')
            Long customerId = params.customerId ? params.long('customerId') : null
            Map result = salesPriceListService.listPriceListsPage(
                    npub,
                    activeOnly,
                    page.max as int,
                    page.offset as int,
                    params.q as String,
                    params.listKind as String,
                    customerId
            )
            ListPaging.envelope(
                    (result.items as List).collect { salesPriceListService.toMap(it, false) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc', q: params.q, listKind: params.listKind]
            )
        }
    }

    def create() {
        wrapBody { String npub, Map body ->
            def pl = salesPriceListService.createPriceList(npub, body)
            [status: 201, body: salesPriceListService.toMap(pl, true)]
        }
    }

    def show() {
        wrap { String npub ->
            salesPriceListService.toMap(salesPriceListService.getPriceList(params.long('id'), npub), true)
        }
    }

    def update() {
        wrapBody { String npub, Map body ->
            salesPriceListService.toMap(
                    salesPriceListService.updatePriceList(params.long('id'), npub, body), true)
        }
    }

    def delete() {
        wrap { String npub ->
            boolean hard = params.boolean('hard')
            if (hard) {
                salesPriceListService.deletePriceList(params.long('id'), npub)
                [ok: true, deleted: true]
            } else {
                salesPriceListService.toMap(salesPriceListService.deactivate(params.long('id'), npub), false)
            }
        }
    }

    def lines() {
        wrapBody { String npub, Map body ->
            List lineRows = (body.lines instanceof List) ? body.lines as List : []
            salesPriceListService.toMap(
                    salesPriceListService.setLines(params.long('id'), npub, lineRows), true)
        }
    }

    /** GET /api/sales/pricelists/resolve?catalogueItemId=&customerId=&quantity= */
    def resolve() {
        wrap { String npub ->
            Long catalogueItemId = params.long('catalogueItemId')
            Long customerId = params.customerId ? params.long('customerId') : null
            BigDecimal qty = params.quantity != null
                    ? new BigDecimal(params.quantity.toString())
                    : BigDecimal.ONE
            salesPriceListService.resolvePrice(npub, catalogueItemId, customerId, qty)
        }
    }

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            log.error('SalesPricing API error', e)
            render(status: 500, text: ([error: e.message] as JSON))
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
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            log.error('SalesPricing API error', e)
            render(status: 500, text: ([error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
