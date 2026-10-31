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
import steakos.supplychain.*

/**
 * Supply chain commercial API (SC kernel): vendors, products, POs, production, quality, inventory.
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class ScmApiController {

    ScmVendorService scmVendorService
    ScmProductService scmProductService
    ScmPurchaseService scmPurchaseService
    ScmBomService scmBomService
    ScmProductionService scmProductionService
    ScmQualityService scmQualityService
    ScmInventoryService scmInventoryService
    steakos.core.CommercialDocumentPdfService commercialDocumentPdfService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    // ----- Vendors -----
    def vendorList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = scmVendorService.listVendorsPage(
                    npub, true, page.max as int, page.offset as int, params.q as String)
            ListPaging.envelope(
                    (result.items as List).collect { scmVendorService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc', q: params.q]
            )
        }
    }

    def vendorCreate() {
        wrapBody { String npub, Map body ->
            def v = scmVendorService.createVendor(npub, body)
            [status: 201, body: scmVendorService.toMap(v)]
        }
    }

    def vendorShow() {
        wrap { String npub ->
            scmVendorService.toMap(scmVendorService.getVendor(params.long('id'), npub))
        }
    }

    def vendorUpdate() {
        wrapBody { String npub, Map body ->
            scmVendorService.toMap(scmVendorService.updateVendor(params.long('id'), npub, body))
        }
    }

    // ----- Products -----
    def productList() {
        wrap { String npub ->
            [npubOwner: npub, items: scmProductService.listProducts(npub).collect { scmProductService.toMap(it) }]
        }
    }

    def productCreate() {
        wrapBody { String npub, Map body ->
            def p = scmProductService.createProduct(npub, body)
            [status: 201, body: scmProductService.toMap(p)]
        }
    }

    def productShow() {
        wrap { String npub ->
            scmProductService.toMap(scmProductService.getProduct(params.long('id'), npub))
        }
    }

    def productUpdate() {
        wrapBody { String npub, Map body ->
            scmProductService.toMap(scmProductService.updateProduct(params.long('id'), npub, body))
        }
    }

    // ----- Purchase orders -----
    def poList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map listOpts = [sort: params.sort, order: params.order, q: params.q]
            Map result = scmPurchaseService.listPurchaseOrdersPage(
                    npub, params.status as String, page.max as int, page.offset as int, listOpts)
            ListPaging.envelope(
                    (result.items as List).collect { scmPurchaseService.toMap(it, false) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: result.sort ?: 'id', order: result.order ?: 'desc', q: params.q]
            )
        }
    }

    def poCreate() {
        wrapBody { String npub, Map body ->
            def po = scmPurchaseService.createPurchaseOrder(npub, body)
            [status: 201, body: scmPurchaseService.toMap(po, true)]
        }
    }

    def poShow() {
        wrap { String npub ->
            scmPurchaseService.toMap(scmPurchaseService.getPurchaseOrder(params.long('id'), npub), true)
        }
    }

    /** GET /api/scm/purchase-orders/$id/pdf — Bronze BR-R11 */
    def poPdf() {
        try {
            String npub = resolveNpub(params)
            Long id = params.long('id')
            byte[] pdf = commercialDocumentPdfService.renderPurchaseOrderPdf(id, npub)
            response.contentType = 'application/pdf'
            response.setHeader('Content-Disposition', "inline; filename=\"purchase-order-${id}.pdf\"")
            response.setContentLength(pdf.length)
            response.outputStream << pdf
            response.outputStream.flush()
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 500, text: ([error: e.message] as JSON))
        }
    }

    /** GET /api/scm/production-orders/$id/pdf — ISO 6422-1 production order */
    def prodPdf() {
        try {
            String npub = resolveNpub(params)
            Long id = params.long('id')
            byte[] pdf = commercialDocumentPdfService.renderProductionOrderPdf(id, npub)
            response.contentType = 'application/pdf'
            response.setHeader('Content-Disposition', "inline; filename=\"production-order-${id}.pdf\"")
            response.setContentLength(pdf.length)
            response.outputStream << pdf
            response.outputStream.flush()
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 500, text: ([error: e.message] as JSON))
        }
    }

    def poLines() {
        wrapBody { String npub, Map body ->
            List lines = (body.lines instanceof List) ? body.lines : []
            scmPurchaseService.toMap(scmPurchaseService.setLines(params.long('id'), npub, lines), true)
        }
    }

    def poSend() {
        wrapBody { String npub, Map body ->
            scmPurchaseService.toMap(scmPurchaseService.sendPurchaseOrder(params.long('id'), npub), true)
        }
    }

    def poConfirm() {
        wrapBody { String npub, Map body ->
            scmPurchaseService.confirmPurchaseOrder(params.long('id'), npub)
        }
    }

    def poReceive() {
        wrapBody { String npub, Map body ->
            scmPurchaseService.receiveGoods(params.long('id'), npub, body)
        }
    }

    def poCancel() {
        wrapBody { String npub, Map body ->
            scmPurchaseService.toMap(scmPurchaseService.cancelPurchaseOrder(params.long('id'), npub), true)
        }
    }

    // ----- BOM -----
    def bomList() {
        wrap { String npub ->
            [npubOwner: npub, items: scmBomService.listBoms(npub).collect { scmBomService.toMap(it, false) }]
        }
    }

    def bomCreate() {
        wrapBody { String npub, Map body ->
            def b = scmBomService.createBom(npub, body)
            [status: 201, body: scmBomService.toMap(b, true)]
        }
    }

    def bomShow() {
        wrap { String npub ->
            scmBomService.toMap(scmBomService.getBom(params.long('id'), npub), true)
        }
    }

    // ----- Production -----
    def prodList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = scmProductionService.listOrdersPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { scmProductionService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def prodCreate() {
        wrapBody { String npub, Map body ->
            def o = scmProductionService.createProductionOrder(npub, body)
            [status: 201, body: scmProductionService.toMap(o)]
        }
    }

    def prodFromSales() {
        wrapBody { String npub, Map body ->
            Long soId = body.salesOrderId != null ? body.salesOrderId as Long : params.long('salesOrderId')
            def list = scmProductionService.createFromSalesOrder(soId, npub)
            [items: list.collect { scmProductionService.toMap(it) }]
        }
    }

    def prodShow() {
        wrap { String npub ->
            scmProductionService.toMap(scmProductionService.getOrder(params.long('id'), npub))
        }
    }

    def prodRelease() {
        wrapBody { String npub, Map body ->
            scmProductionService.toMap(scmProductionService.release(params.long('id'), npub))
        }
    }

    def prodStart() {
        wrapBody { String npub, Map body ->
            scmProductionService.toMap(scmProductionService.start(params.long('id'), npub))
        }
    }

    def prodComplete() {
        wrapBody { String npub, Map body ->
            scmProductionService.complete(params.long('id'), npub)
        }
    }

    def prodCancel() {
        wrapBody { String npub, Map body ->
            scmProductionService.toMap(scmProductionService.cancel(params.long('id'), npub))
        }
    }

    // ----- Quality -----
    def lotList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = scmQualityService.listLotsPage(npub, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { scmQualityService.lotToMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def lotCreate() {
        wrapBody { String npub, Map body ->
            def l = scmQualityService.createLot(npub, body)
            [status: 201, body: scmQualityService.lotToMap(l)]
        }
    }

    def lotShow() {
        wrap { String npub ->
            scmQualityService.lotToMap(scmQualityService.getLot(params.long('id'), npub))
        }
    }

    def lotCheck() {
        wrapBody { String npub, Map body ->
            body.lotId = body.lotId ?: params.long('id')
            scmQualityService.checkToMap(scmQualityService.recordCheck(npub, body))
        }
    }

    // ----- Inventory -----
    def inventoryOnHand() {
        wrap { String npub ->
            [npubOwner: npub, items: scmInventoryService.onHandSummary(npub)]
        }
    }

    def inventoryMoves() {
        wrap { String npub ->
            Long pid = params.productId ? params.long('productId') : null
            [npubOwner: npub, items: scmInventoryService.listMoves(npub, pid)]
        }
    }

    def inventoryAdjust() {
        wrapBody { String npub, Map body ->
            body.reason = body.reason ?: 'ADJUST'
            def m = scmInventoryService.postMove(npub, body)
            [status: 201, body: scmInventoryService.toMap(m)]
        }
    }

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (steakos.document.DocumentChainLockedException e) {
            renderChainLocked(e)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            log.error('SCM API error', e)
            String msg = rootMessage(e)
            render(status: 500, text: ([error: msg] as JSON))
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
        } catch (steakos.document.DocumentChainLockedException e) {
            renderChainLocked(e)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            log.error('SCM API error', e)
            String msg = rootMessage(e)
            render(status: 500, text: ([error: msg] as JSON))
        }
    }

    private void renderChainLocked(steakos.document.DocumentChainLockedException e) {
        Map payload = [
                code      : steakos.document.DocumentChainLockedException.CODE,
                error     : e.message,
                detail    : e.message,
                reasons   : e.reasons,
                unlockHint: e.reasons?.collect { it.unlockHint ?: it.message }?.join(' · ')
        ]
        render(status: 409, text: (payload as JSON))
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e
        while (t.cause != null && t.cause != t) {
            t = t.cause
        }
        return t.message ?: e.message ?: e.class.simpleName
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
