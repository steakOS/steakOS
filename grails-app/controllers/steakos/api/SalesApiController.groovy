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
import steakos.api.ListPaging
import steakos.sales.*

/**
 * Sales commercial API (S-S1–S-S3): customers, quotes, orders.
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class SalesApiController {

    SalesCustomerService salesCustomerService
    SalesQuoteService salesQuoteService
    SalesOrderService salesOrderService
    steakos.core.CommercialDocumentPdfService commercialDocumentPdfService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    // ----- Customers -----
    def customerList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = salesCustomerService.listCustomersPage(
                    npub, true, page.max as int, page.offset as int, params.q as String)
            ListPaging.envelope(
                    (result.items as List).collect { salesCustomerService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc', q: params.q]
            )
        }
    }

    def customerCreate() {
        wrapBody { String npub, Map body ->
            def c = salesCustomerService.createCustomer(npub, body)
            [status: 201, body: salesCustomerService.toMap(c)]
        }
    }

    def customerShow() {
        wrap { String npub ->
            salesCustomerService.toMap(salesCustomerService.getCustomer(params.long('id'), npub))
        }
    }

    def customerUpdate() {
        wrapBody { String npub, Map body ->
            salesCustomerService.toMap(salesCustomerService.updateCustomer(params.long('id'), npub, body))
        }
    }

    // ----- Quotes -----
    def quoteList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = salesQuoteService.listQuotesPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { salesQuoteService.toMap(it, false) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def quoteCreate() {
        wrapBody { String npub, Map body ->
            def q = salesQuoteService.createQuote(npub, body)
            [status: 201, body: salesQuoteService.toMap(q, true)]
        }
    }

    def quoteShow() {
        wrap { String npub ->
            salesQuoteService.toMap(salesQuoteService.getQuote(params.long('id'), npub), true)
        }
    }

    def quoteLines() {
        wrapBody { String npub, Map body ->
            List lines = (body.lines instanceof List) ? body.lines : []
            salesQuoteService.toMap(salesQuoteService.setLines(params.long('id'), npub, lines), true)
        }
    }

    def quoteSend() {
        wrapBody { String npub, Map body ->
            salesQuoteService.toMap(salesQuoteService.sendQuote(params.long('id'), npub), true)
        }
    }

    def quoteAccept() {
        wrapBody { String npub, Map body ->
            salesQuoteService.acceptQuote(params.long('id'), npub)
        }
    }

    def quoteReject() {
        wrapBody { String npub, Map body ->
            salesQuoteService.toMap(salesQuoteService.rejectQuote(params.long('id'), npub), true)
        }
    }

    /** GET /api/sales/quotes/$id/pdf — Bronze BR-R11 */
    def quotePdf() {
        try {
            String npub = resolveNpub(params)
            Long id = params.long('id')
            byte[] pdf = commercialDocumentPdfService.renderQuotePdf(id, npub)
            response.contentType = 'application/pdf'
            response.setHeader('Content-Disposition', "inline; filename=\"quote-${id}.pdf\"")
            response.setContentLength(pdf.length)
            response.outputStream << pdf
            response.outputStream.flush()
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 500, text: ([error: e.message] as JSON))
        }
    }

    // ----- Orders -----
    def orderList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = salesOrderService.listOrdersPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { salesOrderService.toMap(it, false) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def orderOpen() {
        wrap { String npub ->
            [npubOwner: npub, items: salesOrderService.listOpenOrders(npub).collect { salesOrderService.toMap(it, false) }]
        }
    }

    def orderCreate() {
        wrapBody { String npub, Map body ->
            def o = salesOrderService.createOrder(npub, body)
            [status: 201, body: salesOrderService.toMap(o, true)]
        }
    }

    def orderShow() {
        wrap { String npub ->
            salesOrderService.toMap(salesOrderService.getOrder(params.long('id'), npub), true)
        }
    }

    def orderLines() {
        wrapBody { String npub, Map body ->
            List lines = (body.lines instanceof List) ? body.lines : []
            salesOrderService.toMap(salesOrderService.setLines(params.long('id'), npub, lines), true)
        }
    }

    def orderConfirm() {
        wrapBody { String npub, Map body ->
            salesOrderService.confirmOrder(params.long('id'), npub)
        }
    }

    def orderCancel() {
        wrapBody { String npub, Map body ->
            salesOrderService.toMap(salesOrderService.cancelOrder(params.long('id'), npub), true)
        }
    }

    /** GET /api/sales/orders/$id/pdf — Bronze BR-R11 */
    def orderPdf() {
        try {
            String npub = resolveNpub(params)
            Long id = params.long('id')
            byte[] pdf = commercialDocumentPdfService.renderSalesOrderPdf(id, npub)
            response.contentType = 'application/pdf'
            response.setHeader('Content-Disposition', "inline; filename=\"sales-order-${id}.pdf\"")
            response.setContentLength(pdf.length)
            response.outputStream << pdf
            response.outputStream.flush()
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 500, text: ([error: e.message] as JSON))
        }
    }

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
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
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
