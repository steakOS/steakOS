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
import steakos.service.*

/**
 * AfterSales API (AS-S0–AS-S3): service levels, inquiries, warranties, claims, RMA.
 * Path: /api/aftersales/* (AS6). Services: As* in steakos.service (AS8).
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class AfterSalesApiController {

    AsServiceLevelService asServiceLevelService
    AsInquiryService asInquiryService
    AsWarrantyService asWarrantyService
    AsClaimsService asClaimsService
    AsRmaService asRmaService
    steakos.core.CommercialDocumentPdfService commercialDocumentPdfService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    // ----- Service levels -----
    def serviceLevelList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            boolean activeOnly = params.activeOnly != 'false'
            Map result = asServiceLevelService.listPage(npub, activeOnly, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { asServiceLevelService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc']
            )
        }
    }

    def serviceLevelCreate() {
        wrapBody { String npub, Map body ->
            def s = asServiceLevelService.create(npub, body)
            [status: 201, body: asServiceLevelService.toMap(s)]
        }
    }

    def serviceLevelShow() {
        wrap { String npub ->
            asServiceLevelService.toMap(asServiceLevelService.get(params.long('id'), npub))
        }
    }

    def serviceLevelUpdate() {
        wrapBody { String npub, Map body ->
            asServiceLevelService.toMap(asServiceLevelService.update(params.long('id'), npub, body))
        }
    }

    // ----- Inquiries -----
    def inquiryList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = asInquiryService.listPage(
                    npub, params.status as String, params.kind as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { asInquiryService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    /** Dashboard open queue — unpaged (capped by service). */
    def inquiryOpen() {
        wrap { String npub ->
            [npubOwner: npub, items: asInquiryService.listOpen(npub).collect { asInquiryService.toMap(it) }]
        }
    }

    def inquiryCreate() {
        wrapBody { String npub, Map body ->
            def i = asInquiryService.openInquiry(npub, body)
            [status: 201, body: asInquiryService.toMap(i)]
        }
    }

    def inquiryShow() {
        wrap { String npub ->
            asInquiryService.toMap(asInquiryService.get(params.long('id'), npub))
        }
    }

    def inquiryUpdate() {
        wrapBody { String npub, Map body ->
            asInquiryService.toMap(asInquiryService.update(params.long('id'), npub, body))
        }
    }

    def inquiryStart() {
        wrapBody { String npub, Map body ->
            asInquiryService.toMap(asInquiryService.startWork(params.long('id'), npub))
        }
    }

    def inquiryResolve() {
        wrapBody { String npub, Map body ->
            asInquiryService.toMap(asInquiryService.resolve(params.long('id'), npub, body))
        }
    }

    def inquiryClose() {
        wrapBody { String npub, Map body ->
            asInquiryService.toMap(asInquiryService.close(params.long('id'), npub))
        }
    }

    def inquiryCancel() {
        wrapBody { String npub, Map body ->
            asInquiryService.toMap(asInquiryService.cancel(params.long('id'), npub))
        }
    }

    // ----- Warranties -----
    def warrantyList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            String cover = (params.status ?: params.coverStatus) as String
            Map result = asWarrantyService.listPage(npub, cover, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { asWarrantyService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def warrantyCreate() {
        wrapBody { String npub, Map body ->
            def w = asWarrantyService.register(npub, body)
            [status: 201, body: asWarrantyService.toMap(w)]
        }
    }

    def warrantyShow() {
        wrap { String npub ->
            asWarrantyService.toMap(asWarrantyService.get(params.long('id'), npub))
        }
    }

    def warrantyUpdate() {
        wrapBody { String npub, Map body ->
            asWarrantyService.toMap(asWarrantyService.update(params.long('id'), npub, body))
        }
    }

    def warrantyActivate() {
        wrapBody { String npub, Map body ->
            asWarrantyService.toMap(asWarrantyService.activate(params.long('id'), npub))
        }
    }

    def warrantyVoid() {
        wrapBody { String npub, Map body ->
            asWarrantyService.toMap(asWarrantyService.voidCover(params.long('id'), npub))
        }
    }

    // ----- Claims -----
    def claimList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = asClaimsService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { asClaimsService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    /** Dashboard open queue — unpaged (capped by service). */
    def claimOpen() {
        wrap { String npub ->
            [npubOwner: npub, items: asClaimsService.listOpen(npub).collect { asClaimsService.toMap(it) }]
        }
    }

    def claimCreate() {
        wrapBody { String npub, Map body ->
            def c = asClaimsService.receiveClaim(npub, body)
            [status: 201, body: asClaimsService.toMap(c)]
        }
    }

    def claimShow() {
        wrap { String npub ->
            asClaimsService.toMap(asClaimsService.get(params.long('id'), npub))
        }
    }

    def claimValidate() {
        wrapBody { String npub, Map body ->
            asClaimsService.toMap(asClaimsService.validate(params.long('id'), npub))
        }
    }

    def claimApprove() {
        wrapBody { String npub, Map body ->
            asClaimsService.approve(params.long('id'), npub, body)
        }
    }

    def claimReject() {
        wrapBody { String npub, Map body ->
            asClaimsService.toMap(asClaimsService.reject(params.long('id'), npub, body))
        }
    }

    def claimClose() {
        wrapBody { String npub, Map body ->
            asClaimsService.toMap(asClaimsService.close(params.long('id'), npub))
        }
    }

    def claimNotes() {
        wrapBody { String npub, Map body ->
            asClaimsService.toMap(asClaimsService.updateNotes(params.long('id'), npub, body))
        }
    }

    // ----- RMA -----
    def rmaList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = asRmaService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { asRmaService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    /** Dashboard open queue — unpaged (capped by service). */
    def rmaOpenList() {
        wrap { String npub ->
            [npubOwner: npub, items: asRmaService.listOpen(npub).collect { asRmaService.toMap(it) }]
        }
    }

    def rmaCreate() {
        wrapBody { String npub, Map body ->
            def r = asRmaService.openRma(npub, body)
            [status: 201, body: asRmaService.toMap(r)]
        }
    }

    def rmaShow() {
        wrap { String npub ->
            asRmaService.toMap(asRmaService.get(params.long('id'), npub))
        }
    }

    def rmaConfirm() {
        wrapBody { String npub, Map body ->
            asRmaService.confirm(params.long('id'), npub, body)
        }
    }

    def rmaClose() {
        wrapBody { String npub, Map body ->
            asRmaService.toMap(asRmaService.close(params.long('id'), npub))
        }
    }

    def rmaCancel() {
        wrapBody { String npub, Map body ->
            asRmaService.toMap(asRmaService.cancel(params.long('id'), npub))
        }
    }

    /** GET /api/aftersales/rmas/$id/pdf — ISO 6422-1 return / RMA */
    def rmaPdf() {
        try {
            String npub = resolveNpub(params)
            Long id = params.long('id')
            byte[] pdf = commercialDocumentPdfService.renderRmaPdf(id, npub)
            response.contentType = 'application/pdf'
            response.setHeader('Content-Disposition', "inline; filename=\"rma-${id}.pdf\"")
            response.setContentLength(pdf.length)
            response.outputStream << pdf
            response.outputStream.flush()
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('RMA PDF error', e)
            render(status: 500, text: ([code: 'RMA_PDF_ERROR', error: e.message] as JSON))
        }
    }

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('AfterSales API error', e)
            render(status: 500, text: ([code: 'AFTERSALES_ERROR', error: e.message] as JSON))
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
            log.error('AfterSales API error', e)
            render(status: 500, text: ([code: 'AFTERSALES_ERROR', error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
