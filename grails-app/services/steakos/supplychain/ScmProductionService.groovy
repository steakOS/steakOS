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

package steakos.supplychain

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.catalogue.CatalogueItem
import steakos.catalogue.CatalogueService
import steakos.production.Lot
import steakos.production.ProductionOrder
import steakos.sales.OrderLine
import steakos.sales.SalesOrder

/**
 * Production orders (SC). Catalogue GOODS or SERVICE (effort); stock only for GOODS.
 */
@Slf4j
class ScmProductionService {

    CatalogueService catalogueService
    ScmInventoryService scmInventoryService
    ScmQualityService scmQualityService

    @Transactional
    ProductionOrder createProductionOrder(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Object cid = CatalogueService.resolveId(fields)
        if (!cid) throw new IllegalArgumentException('catalogueItemId (or productId) is required')
        // GOODS or SERVICE allowed
        CatalogueItem item = catalogueService.requireItem(npub, cid, null)
        BigDecimal qty = toBd(fields.quantity) ?: BigDecimal.ONE
        if (qty <= BigDecimal.ZERO) throw new IllegalArgumentException('quantity must be > 0')
        String number = fields.orderNumber?.toString()?.trim() ?: nextOrderNumber(npub)
        ProductionOrder po = new ProductionOrder(
                npubOwner          : npub,
                name               : (fields.name ?: "Prod ${number}") as String,
                description        : fields.description as String,
                orderNumber        : number,
                catalogueItem      : item,
                quantity           : qty,
                unit               : (fields.unit ?: item.unit ?: 'EA') as String,
                scheduledStart     : parseDate(fields.scheduledStart),
                scheduledEnd       : parseDate(fields.scheduledEnd),
                prodStatus         : 'DRAFT',
                status             : 'DRAFT',
                sourceSalesOrderId : fields.sourceSalesOrderId != null && fields.sourceSalesOrderId != '' ? fields.sourceSalesOrderId as Long : null
        )
        po.save(failOnError: true, flush: true)
        return po
    }

    /**
     * SC6: demand pull from sales lines with catalogueItemId (GOODS only).
     */
    @Transactional
    List<ProductionOrder> createFromSalesOrder(Long salesOrderId, String npubOwner) {
        String npub = requireNpub(npubOwner)
        SalesOrder so = SalesOrder.get(salesOrderId)
        if (!so || so.npubOwner != npub) {
            throw new IllegalArgumentException("sales order not found: ${salesOrderId}")
        }
        List<OrderLine> lines = OrderLine.findAllBySalesOrder(so, [sort: 'lineNo'])
        List<ProductionOrder> created = []
        lines.each { OrderLine ol ->
            Long catId = ol.catalogueItemId
            if (!catId && ol.productRefId) {
                // legacy dual-read via resolve
                Map resolved = catalogueService.resolveForLine(npub, [
                        productRefId  : ol.productRefId,
                        productRefType: ol.productRefType,
                        catalogueItemId: ol.catalogueItemId
                ])
                catId = resolved.catalogueItemId as Long
            }
            if (!catId) return
            try {
                CatalogueItem ci = catalogueService.requireItem(npub, catId, null)
                if (ci.nature == CatalogueItem.NATURE_SERVICE) return
                ProductionOrder po = createProductionOrder(npub, [
                        catalogueItemId   : ci.id,
                        quantity          : ol.quantity,
                        name              : "From ${so.orderNumber} · ${ol.name}",
                        sourceSalesOrderId: so.id
                ])
                created << po
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (!created) {
            throw new IllegalStateException('no makeable GOODS lines on sales order (need catalogueItemId)')
        }
        return created
    }

    @Transactional
    ProductionOrder release(Long id, String npubOwner) {
        ProductionOrder po = load(id, npubOwner)
        if (po.prodStatus != 'DRAFT' && po.prodStatus != 'RELEASED') {
            throw new IllegalStateException("cannot release from ${po.prodStatus}")
        }
        po.prodStatus = 'RELEASED'
        po.status = 'RELEASED'
        po.save(failOnError: true, flush: true)
        return po
    }

    @Transactional
    ProductionOrder start(Long id, String npubOwner) {
        ProductionOrder po = load(id, npubOwner)
        if (!(po.prodStatus in ['RELEASED', 'DRAFT', 'IN_PROGRESS'])) {
            throw new IllegalStateException("cannot start from ${po.prodStatus}")
        }
        po.prodStatus = 'IN_PROGRESS'
        po.status = 'IN_PROGRESS'
        po.save(failOnError: true, flush: true)
        return po
    }

    /**
     * Complete GOODS → OPEN lot awaiting QA (stock posts only on Check PASS / RELEASED).
     * SERVICE completes with no lot or stock.
     */
    @Transactional
    Map complete(Long id, String npubOwner) {
        ProductionOrder po = load(id, npubOwner)
        if (po.prodStatus == 'COMPLETED') {
            Map lotMap = null
            if (po.resultingLotId && scmQualityService != null) {
                try {
                    lotMap = scmQualityService.lotToMap(scmQualityService.getLot(po.resultingLotId, npubOwner))
                } catch (ignored) {
                }
            }
            return [productionOrder: toMap(po), lot: lotMap, alreadyCompleted: true]
        }
        if (po.prodStatus == 'CANCELLED') {
            throw new IllegalStateException('cannot complete cancelled order')
        }
        po.prodStatus = 'COMPLETED'
        po.status = 'COMPLETED'
        po.completedAt = new Date()

        Lot lot = null
        CatalogueItem item = po.catalogueItem
        if (scmQualityService != null && item && item.nature == CatalogueItem.NATURE_GOODS) {
            lot = scmQualityService.createLot(po.npubOwner, [
                    catalogueItemId        : item.id,
                    quantity               : po.quantity,
                    name                   : "Lot from ${po.orderNumber}",
                    sourceProductionOrderId: po.id,
                    lotStatus              : 'OPEN'
            ])
            po.resultingLotId = lot.id
        }
        po.save(failOnError: true, flush: true)
        log.info('Completed production {} qty={} lot={} (awaiting QA for stock)',
                po.orderNumber, po.quantity, lot?.lotCode)
        return [
                productionOrder: toMap(po),
                lot            : lot && scmQualityService != null ? scmQualityService.lotToMap(lot) : null,
                alreadyCompleted: false,
                awaitingQa     : lot != null
        ]
    }

    @Transactional
    ProductionOrder cancel(Long id, String npubOwner) {
        ProductionOrder po = load(id, npubOwner)
        if (po.prodStatus == 'COMPLETED') {
            throw new IllegalStateException('cannot cancel completed order')
        }
        po.prodStatus = 'CANCELLED'
        po.status = 'CANCELLED'
        po.save(failOnError: true, flush: true)
        return po
    }

    @Transactional(readOnly = true)
    List<ProductionOrder> listOrders(String npubOwner, String status = null) {
        listOrdersPage(npubOwner, status, 200, 0).items as List<ProductionOrder>
    }

    static final List PROD_SORT_FIELDS = [
            'id', 'name', 'orderNumber', 'prodStatus', 'status', 'quantity',
            'scheduledStart', 'scheduledEnd', 'completedAt'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listOrdersPage(String npubOwner, String status = null, int max = 25, int offset = 0,
                       Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', PROD_SORT_FIELDS)
        List items = ProductionOrder.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (status) eq('prodStatus', status)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = ProductionOrder.createCriteria().count {
            eq('npubOwner', npub)
            if (status) eq('prodStatus', status)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    ProductionOrder getOrder(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(ProductionOrder po) {
        if (!po) return null
        [
                id                : po.id,
                name              : po.name,
                description       : po.description,
                orderNumber       : po.orderNumber,
                productId         : po.catalogueItem?.id,
                catalogueItemId   : po.catalogueItem?.id,
                productName       : po.catalogueItem?.name,
                nature            : po.catalogueItem?.nature,
                quantity          : po.quantity,
                unit              : po.unit,
                scheduledStart    : po.scheduledStart,
                scheduledEnd      : po.scheduledEnd,
                completedAt       : po.completedAt,
                prodStatus        : po.prodStatus,
                sourceSalesOrderId: po.sourceSalesOrderId,
                resultingLotId    : po.resultingLotId,
                npubOwner         : po.npubOwner
        ]
    }

    private ProductionOrder load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        ProductionOrder po = ProductionOrder.get(id)
        if (!po || po.npubOwner != npub) throw new IllegalArgumentException("production order not found: ${id}")
        po
    }

    private String nextOrderNumber(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String prefix = "MO-${period}-"
        int seq = ProductionOrder.countByNpubOwner(npub) + 1
        String candidate = "${prefix}${String.format('%04d', seq)}"
        while (ProductionOrder.findByNpubOwnerAndOrderNumber(npub, candidate)) {
            seq++
            candidate = "${prefix}${String.format('%04d', seq)}"
        }
        return candidate
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') return null
        if (v instanceof BigDecimal) return v
        new BigDecimal(v.toString())
    }

    private static Date parseDate(Object raw) {
        if (raw == null || raw == '') return null
        if (raw instanceof Date) return raw
        String s = raw.toString().trim()
        if (s.length() >= 10) {
            try { return Date.parse('yyyy-MM-dd', s.substring(0, 10)) } catch (ignored) {}
        }
        null
    }
}
