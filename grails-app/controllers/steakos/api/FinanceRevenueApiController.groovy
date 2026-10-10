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
import steakos.finance.FinanceRevenueService
import steakos.finance.Invoice
import steakos.finance.PaymentOption
import steakos.finance.PostingRulesException

/**
 * Revenue / invoice API (F-S2 / APQC 9.2). Settlements post via FinanceLedgerService (FS6/FS8).
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class FinanceRevenueApiController {

    static allowedMethods = [
            list          : 'GET',
            open          : 'GET',
            show          : 'GET',
            create        : 'POST',
            issue         : 'POST',
            addOption     : 'POST',
            settle        : 'POST',
            voidInvoice   : 'POST',
            pdf           : 'GET'
    ]

    FinanceRevenueService financeRevenueService
    steakos.finance.InvoicePdfService invoicePdfService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    /** GET /api/finance/revenue/invoices */
    def list() {
        try {
            String npub = resolveNpub(params)
            String status = params.status
            Map page = steakos.api.ListPaging.pageParams(params, steakos.api.ListPaging.DEFAULT_MAX, steakos.api.ListPaging.HARD_MAX)
            Map result = financeRevenueService.listInvoicesPage(npub, status, page.max as int, page.offset as int)
            render(steakos.api.ListPaging.envelope(
                    (result.items as List).collect { financeRevenueService.invoiceToMap(it, false) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            ) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** GET /api/finance/revenue/invoices/open */
    def open() {
        try {
            String npub = resolveNpub(params)
            List<Invoice> items = financeRevenueService.listOpenReceivables(npub)
            render([
                    npubOwner: npub,
                    items    : items.collect { financeRevenueService.invoiceToMap(it, false) }
            ] as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** GET /api/finance/revenue/invoices/$id */
    def show() {
        try {
            String npub = resolveNpub(params)
            Invoice inv = financeRevenueService.getInvoice(params.long('id'), npub)
            render(financeRevenueService.invoiceToMap(inv, true) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/finance/revenue/invoices */
    def create() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            Invoice inv = financeRevenueService.createInvoice(npub, body)
            boolean autoIssue = body.issue == true || body.issue == 'true'
            if (autoIssue) {
                inv = financeRevenueService.issueInvoice(inv.id, npub, body)
            }
            render(status: 201, text: (financeRevenueService.invoiceToMap(inv, true) as JSON))
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/finance/revenue/invoices/$id/issue */
    def issue() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            Invoice inv = financeRevenueService.issueInvoice(params.long('id'), npub, body)
            render(financeRevenueService.invoiceToMap(inv, true) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/finance/revenue/invoices/$id/options */
    def addOption() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            PaymentOption opt = financeRevenueService.registerPaymentOption(params.long('id'), npub, body)
            render(status: 201, text: (financeRevenueService.paymentOptionToMap(opt) as JSON))
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/finance/revenue/invoices/$id/settle */
    def settle() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            Map result = financeRevenueService.recordSettlement(params.long('id'), npub, body)
            render(result as JSON)
        } catch (PostingRulesException e) {
            render(status: 400, text: ([error: e.message, code: e.code] as JSON))
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/finance/revenue/invoices/$id/void */
    def voidInvoice() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            String reason = body.reason as String
            Invoice inv = financeRevenueService.voidInvoice(params.long('id'), npub, reason)
            render(financeRevenueService.invoiceToMap(inv, true) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** GET /api/finance/revenue/invoices/$id/pdf — Copper CU-F7 invoice PDF */
    def pdf() {
        try {
            String npub = resolveNpub(params)
            Invoice inv = financeRevenueService.getInvoice(params.long('id'), npub)
            byte[] pdf = invoicePdfService.renderInvoicePdf(inv.id, npub)
            String filename = "invoice-${inv.invoiceNumber ?: inv.id}.pdf"
            response.status = 200
            response.contentType = 'application/pdf'
            response.setHeader('Content-Disposition', "attachment; filename=\"${filename}\"")
            response.setContentLength(pdf.length)
            response.outputStream << pdf
            response.outputStream.flush()
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 500, text: ([error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
