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
import steakos.delivery.*

/**
 * Delivery API (D-S1–D-S3): services, resources, service orders.
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class DeliveryApiController {

    DelServiceService delServiceService
    DelResourceService delResourceService
    DelOrderService delOrderService
    steakos.core.CommercialDocumentPdfService commercialDocumentPdfService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    // ----- Catalogue -----
    def serviceList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            boolean activeOnly = params.activeOnly != 'false'
            Map result = delServiceService.listServicesPage(npub, activeOnly, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { delServiceService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc']
            )
        }
    }

    def serviceCreate() {
        wrapBody { String npub, Map body ->
            def s = delServiceService.createService(npub, body)
            [status: 201, body: delServiceService.toMap(s)]
        }
    }

    def serviceShow() {
        wrap { String npub ->
            delServiceService.toMap(delServiceService.getService(params.long('id'), npub))
        }
    }

    def serviceUpdate() {
        wrapBody { String npub, Map body ->
            delServiceService.toMap(delServiceService.updateService(params.long('id'), npub, body))
        }
    }

    // ----- Resources -----
    def resourceList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            boolean activeOnly = params.activeOnly != 'false'
            // assignees=true|1 → sync from ACTIVE HR employment and return HUMAN resources only
            boolean assigneesOnly = params.assignees in ['true', '1', 'yes'] || params.fromHr in ['true', '1', 'yes']
            Map listOpts = [sort: params.sort, order: params.order, q: params.q]
            Map result = delResourceService.listResourcesPage(
                    npub, activeOnly, page.max as int, page.offset as int,
                    params.q as String, assigneesOnly, listOpts)
            ListPaging.envelope(
                    (result.items as List).collect { delResourceService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: result.sort ?: 'name', order: result.order ?: 'asc',
                     q: params.q, assignees: assigneesOnly]
            )
        }
    }

    def resourceCreate() {
        wrapBody { String npub, Map body ->
            def r = delResourceService.createResource(npub, body)
            [status: 201, body: delResourceService.toMap(r)]
        }
    }

    def resourceShow() {
        wrap { String npub ->
            delResourceService.toMap(delResourceService.getResource(params.long('id'), npub))
        }
    }

    def resourceUpdate() {
        wrapBody { String npub, Map body ->
            delResourceService.toMap(delResourceService.updateResource(params.long('id'), npub, body))
        }
    }

    // ----- Orders -----
    def orderList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map listOpts = [sort: params.sort, order: params.order, q: params.q]
            Map result = delOrderService.listOrdersPage(
                    npub, params.status as String, page.max as int, page.offset as int, listOpts)
            ListPaging.envelope(
                    (result.items as List).collect { delOrderService.toMap(it, false) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: result.sort ?: 'id', order: result.order ?: 'desc', q: params.q]
            )
        }
    }

    /** Dashboard / open queue — unpaged (capped by service). */
    def orderOpen() {
        wrap { String npub ->
            [npubOwner: npub, items: delOrderService.listOpen(npub).collect { delOrderService.toMap(it, false) }]
        }
    }

    def orderCreate() {
        wrapBody { String npub, Map body ->
            def o = delOrderService.createOrder(npub, body)
            [status: 201, body: delOrderService.toMap(o, true)]
        }
    }

    def orderFromSales() {
        wrapBody { String npub, Map body ->
            Long soId = body.salesOrderId != null ? body.salesOrderId as Long : params.long('salesOrderId')
            def list = delOrderService.createFromSalesOrder(soId, npub)
            [items: list.collect { delOrderService.toMap(it, true) }]
        }
    }

    def orderShow() {
        wrap { String npub ->
            delOrderService.toMap(delOrderService.getOrder(params.long('id'), npub), true)
        }
    }

    def orderLines() {
        wrapBody { String npub, Map body ->
            List lines = (body.lines instanceof List) ? body.lines : []
            delOrderService.toMap(delOrderService.setLines(params.long('id'), npub, lines), true)
        }
    }

    def orderAssign() {
        wrapBody { String npub, Map body ->
            Long rid = body.resourceId != null && body.resourceId != '' ? body.resourceId as Long : null
            delOrderService.toMap(delOrderService.assignResource(params.long('id'), npub, rid), true)
        }
    }

    def orderSchedule() {
        wrapBody { String npub, Map body ->
            delOrderService.toMap(delOrderService.schedule(params.long('id'), npub, body), true)
        }
    }

    def orderStart() {
        wrapBody { String npub, Map body ->
            delOrderService.toMap(delOrderService.start(params.long('id'), npub), true)
        }
    }

    def orderComplete() {
        wrapBody { String npub, Map body ->
            delOrderService.complete(params.long('id'), npub)
        }
    }

    def orderCancel() {
        wrapBody { String npub, Map body ->
            delOrderService.toMap(delOrderService.cancel(params.long('id'), npub), true)
        }
    }

    /** GET /api/delivery/orders/$id/pdf — ISO 6422-1 delivery / service delivery note */
    def orderPdf() {
        try {
            String npub = resolveNpub(params)
            Long id = params.long('id')
            byte[] pdf = commercialDocumentPdfService.renderDeliveryOrderPdf(id, npub)
            response.contentType = 'application/pdf'
            response.setHeader('Content-Disposition', "inline; filename=\"delivery-${id}.pdf\"")
            response.setContentLength(pdf.length)
            response.outputStream << pdf
            response.outputStream.flush()
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('Delivery PDF error', e)
            render(status: 500, text: ([code: 'DELIVERY_PDF_ERROR', error: e.message] as JSON))
        }
    }

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('Delivery API error', e)
            render(status: 500, text: ([code: 'DELIVERY_ERROR', error: e.message] as JSON))
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
            log.error('Delivery API error', e)
            render(status: 500, text: ([code: 'DELIVERY_ERROR', error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
