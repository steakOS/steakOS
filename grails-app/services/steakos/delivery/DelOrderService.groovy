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

package steakos.delivery

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.catalogue.CatalogueItem
import steakos.sales.Customer
import steakos.sales.OrderLine
import steakos.sales.SalesOrder

/**
 * Service fulfillment (APQC 5.4). D1 lines; D2 from-sales service lines only; D3 no Finance invoice; D4 single resource.
 */
@Slf4j
class DelOrderService {

    DelServiceService delServiceService
    DelResourceService delResourceService
    steakos.catalogue.CatalogueService catalogueService

    static final Set SERVICE_LINE_TYPES = [
            'SERVICE', 'DELIVERY', 'INSTALL', 'INSTALLATION',
            'TRANSPORT', 'TRANSPORTATION', 'LABOR', 'CONSULTING'
    ].asImmutable()

    @Transactional
    ServiceOrder createOrder(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        CatalogueItem svcItem = resolveCatalogueService(npub, fields)
        Resource res = resolveResource(npub, fields)
        String number = fields.orderNumber?.toString()?.trim() ?: nextOrderNumber(npub)

        Long custId = fields.customerId != null && fields.customerId != '' ? fields.customerId as Long : null
        String custName = fields.customerName as String
        if (custId != null && !custName?.trim()) {
            Customer c = Customer.get(custId)
            if (c && c.npubOwner == npub) {
                custName = c.name
            }
        }

        ServiceOrder order = new ServiceOrder(
                npubOwner         : npub,
                name              : (fields.name ?: "SO ${number}") as String,
                description       : fields.description as String,
                orderNumber       : number,
                catalogueItemId   : svcItem?.id,
                customerId        : custId,
                customerName      : custName,
                sourceSalesOrderId: fields.sourceSalesOrderId != null && fields.sourceSalesOrderId != '' ? fields.sourceSalesOrderId as Long : null,
                resource          : res,
                scheduledAt       : parseDate(fields.scheduledAt),
                currencyCode      : (fields.currencyCode ?: 'EUR') as String,
                fulfillStatus     : 'DRAFT',
                status            : 'DRAFT',
                amountEur         : BigDecimal.ZERO
        )
        order.save(failOnError: true, flush: true)

        List lineMaps = (fields.lines instanceof List) ? (List) fields.lines : []
        if (lineMaps) {
            replaceLines(order, lineMaps)
        } else if (svcItem && (fields.amountEur != null || svcItem.listPrice)) {
            BigDecimal unit = toBd(fields.amountEur) ?: svcItem.listPrice ?: BigDecimal.ZERO
            BigDecimal qty = toBd(fields.quantity) ?: BigDecimal.ONE
            replaceLines(order, [[
                    name            : svcItem.name,
                    quantity        : qty,
                    unitPriceEur    : unit,
                    catalogueItemId : svcItem.id,
                    productRefType  : 'SERVICE'
            ]])
        }
        recomputeTotals(order)
        order.save(failOnError: true, flush: true)
        return order
    }

    /**
     * D2: user-triggered — only sales lines that look like services (not physical products).
     */
    @Transactional
    List<ServiceOrder> createFromSalesOrder(Long salesOrderId, String npubOwner) {
        String npub = requireNpub(npubOwner)
        SalesOrder so = SalesOrder.get(salesOrderId)
        if (!so || so.npubOwner != npub) {
            throw new IllegalArgumentException("sales order not found: ${salesOrderId}")
        }
        List<OrderLine> lines = OrderLine.findAllBySalesOrder(so, [sort: 'lineNo'])
        List serviceLines = lines.findAll { OrderLine ol -> isServiceLine(ol) }
        if (!serviceLines) {
            throw new IllegalStateException(
                    'no service lines on sales order (mark productRefType as SERVICE/INSTALL/TRANSPORT or use isService product)')
        }
        List lineMaps = serviceLines.collect { OrderLine ol ->
            [
                    name          : ol.name,
                    description   : ol.description,
                    quantity      : ol.quantity,
                    unitPriceEur  : ol.unitPriceEur,
                    unitPriceSats : ol.unitPriceSats,
                    productRefId  : ol.productRefId,
                    productRefType: ol.productRefType ?: 'SERVICE',
                    serviceRefId  : ol.productRefType?.equalsIgnoreCase('SERVICE') ? ol.productRefId : null
            ]
        }
        ServiceOrder order = createOrder(npub, [
                name              : "Delivery from ${so.orderNumber}",
                customerId        : so.customer?.id,
                customerName      : so.customerName,
                currencyCode      : so.currencyCode,
                sourceSalesOrderId: so.id,
                lines             : lineMaps
        ])
        return [order]
    }

    @Transactional
    ServiceOrder setLines(Long orderId, String npubOwner, List lineMaps) {
        ServiceOrder order = load(orderId, npubOwner)
        assertEditable(order)
        replaceLines(order, lineMaps ?: [])
        recomputeTotals(order)
        order.save(failOnError: true, flush: true)
        return order
    }

    @Transactional
    ServiceOrder assignResource(Long orderId, String npubOwner, Long resourceId) {
        ServiceOrder order = load(orderId, npubOwner)
        if (order.fulfillStatus == 'COMPLETED' || order.fulfillStatus == 'CANCELLED') {
            throw new IllegalStateException("cannot assign resource when ${order.fulfillStatus}")
        }
        if (resourceId == null) {
            order.resource = null
        } else {
            order.resource = delResourceService.getResource(resourceId, npubOwner)
        }
        order.save(failOnError: true, flush: true)
        return order
    }

    @Transactional
    ServiceOrder schedule(Long orderId, String npubOwner, Map fields = [:]) {
        ServiceOrder order = load(orderId, npubOwner)
        if (!(order.fulfillStatus in ['DRAFT', 'SCHEDULED'])) {
            throw new IllegalStateException("cannot schedule from ${order.fulfillStatus}")
        }
        order.fulfillStatus = 'SCHEDULED'
        order.status = 'SCHEDULED'
        if (fields.scheduledAt) order.scheduledAt = parseDate(fields.scheduledAt) ?: new Date()
        else if (!order.scheduledAt) order.scheduledAt = new Date()
        if (fields.resourceId) {
            order.resource = delResourceService.getResource(fields.resourceId as Long, npubOwner)
        }
        order.save(failOnError: true, flush: true)
        return order
    }

    @Transactional
    ServiceOrder start(Long orderId, String npubOwner) {
        ServiceOrder order = load(orderId, npubOwner)
        if (!(order.fulfillStatus in ['DRAFT', 'SCHEDULED', 'IN_PROGRESS'])) {
            throw new IllegalStateException("cannot start from ${order.fulfillStatus}")
        }
        order.fulfillStatus = 'IN_PROGRESS'
        order.status = 'IN_PROGRESS'
        if (!order.startedAt) order.startedAt = new Date()
        order.save(failOnError: true, flush: true)
        return order
    }

    /**
     * D3: complete only — emit log; no Finance invoice.
     */
    @Transactional
    Map complete(Long orderId, String npubOwner) {
        ServiceOrder order = load(orderId, npubOwner)
        if (order.fulfillStatus == 'COMPLETED') {
            return [order: toMap(order, true), alreadyCompleted: true]
        }
        if (order.fulfillStatus == 'CANCELLED') {
            throw new IllegalStateException('cannot complete a cancelled order')
        }
        order.fulfillStatus = 'COMPLETED'
        order.status = 'COMPLETED'
        order.completedAt = new Date()
        order.save(failOnError: true, flush: true)
        log.info('Completed service order {} (no Finance invoice — D3)', order.orderNumber)
        return [order: toMap(order, true), alreadyCompleted: false]
    }

    @Transactional
    ServiceOrder cancel(Long orderId, String npubOwner) {
        ServiceOrder order = load(orderId, npubOwner)
        if (order.fulfillStatus == 'COMPLETED') {
            throw new IllegalStateException('cannot cancel completed order')
        }
        order.fulfillStatus = 'CANCELLED'
        order.status = 'CANCELLED'
        order.save(failOnError: true, flush: true)
        return order
    }

    @Transactional(readOnly = true)
    List<ServiceOrder> listOrders(String npubOwner, String status = null) {
        listOrdersPage(npubOwner, status, 200, 0).items as List<ServiceOrder>
    }

    static final List ORDER_SORT_FIELDS = [
            'id', 'name', 'orderNumber', 'fulfillStatus', 'status',
            'amountEur', 'customerName', 'dateCreated', 'lastUpdated'
    ].asImmutable()

    /**
     * Paged service orders with optional fulfillStatus filter. Returns [items, total, sort, order].
     */
    @Transactional(readOnly = true)
    Map listOrdersPage(String npubOwner, String status = null, int max = 25, int offset = 0,
                       Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', ORDER_SORT_FIELDS)
        String term = steakos.api.ListPaging.likeContains(listOpts?.q as String)
        List items = ServiceOrder.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (status) eq('fulfillStatus', status)
            if (term) {
                or {
                    ilike('name', term)
                    ilike('orderNumber', term)
                    ilike('customerName', term)
                    ilike('description', term)
                }
            }
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = ServiceOrder.createCriteria().count {
            eq('npubOwner', npub)
            if (status) eq('fulfillStatus', status)
            if (term) {
                or {
                    ilike('name', term)
                    ilike('orderNumber', term)
                    ilike('customerName', term)
                    ilike('description', term)
                }
            }
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<ServiceOrder> listOpen(String npubOwner) {
        String npub = requireNpub(npubOwner)
        ServiceOrder.findAllByNpubOwnerAndFulfillStatusInList(
                npub, ['DRAFT', 'SCHEDULED', 'IN_PROGRESS'], [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    ServiceOrder getOrder(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(ServiceOrder o, boolean includeLines = false) {
        if (!o) return null
        Map m = [
                id                : o.id,
                name              : o.name,
                description       : o.description,
                orderNumber       : o.orderNumber,
                serviceId         : o.catalogueItemId,
                catalogueItemId   : o.catalogueItemId,
                serviceName       : null,
                customerId        : o.customerId,
                customerName      : o.customerName,
                sourceSalesOrderId: o.sourceSalesOrderId,
                resourceId        : o.resource?.id,
                resourceName      : o.resource?.name,
                scheduledAt       : o.scheduledAt,
                startedAt         : o.startedAt,
                completedAt       : o.completedAt,
                amountEur         : o.amountEur,
                amountSats        : o.amountSats,
                currencyCode      : o.currencyCode,
                fulfillStatus     : o.fulfillStatus,
                npubOwner         : o.npubOwner
        ]
        if (includeLines) {
            m.lines = ServiceOrderLine.findAllByServiceOrder(o, [sort: 'lineNo']).collect { lineToMap(it) }
        }
        return m
    }

    Map lineToMap(ServiceOrderLine l) {
        [
                id            : l.id,
                lineNo        : l.lineNo,
                name          : l.name,
                description   : l.description,
                quantity      : l.quantity,
                unitPriceEur  : l.unitPriceEur,
                lineTotalEur  : l.lineTotalEur,
                unitPriceSats : l.unitPriceSats,
                lineTotalSats : l.lineTotalSats,
                serviceRefId    : l.serviceRefId,
                productRefId    : l.productRefId,
                productRefType  : l.productRefType,
                catalogueItemId : l.catalogueItemId
        ]
    }

    private boolean isServiceLine(OrderLine ol) {
        if (!ol) return false
        if (ol.catalogueItemId && catalogueService != null) {
            try {
                CatalogueItem ci = catalogueService.get(ol.catalogueItemId, ol.npubOwner)
                if (ci.nature == CatalogueItem.NATURE_SERVICE) return true
            } catch (ignored) {
            }
        }
        String t = ol.productRefType?.toString()?.trim()?.toUpperCase()
        if (t && SERVICE_LINE_TYPES.any { t == it || t.contains(it) }) {
            return true
        }
        return false
    }

    private CatalogueItem resolveCatalogueService(String npub, Map fields) {
        Object id = fields.catalogueItemId ?: fields.serviceId
        if (!id || id == '') return null
        try {
            return catalogueService.requireItem(npub, id, CatalogueItem.NATURE_SERVICE)
        } catch (Exception e) {
            log.warn("resolve catalogue service: ${e.message}")
            return null
        }
    }

    private void replaceLines(ServiceOrder order, List lineMaps) {
        ServiceOrderLine.findAllByServiceOrder(order).each { it.delete(flush: true) }
        int n = 1
        lineMaps.each { Object raw ->
            Map lm = raw as Map
            Map resolved = catalogueService != null ? catalogueService.resolveForLine(order.npubOwner, lm) : [:]
            BigDecimal qty = toBd(lm.quantity) ?: BigDecimal.ONE
            BigDecimal unit = toBd(lm.unitPriceEur) ?: (resolved.listPrice as BigDecimal) ?: BigDecimal.ZERO
            BigDecimal total = toBd(lm.lineTotalEur)
            if (total == null) total = qty.multiply(unit)
            Long unitSats = lm.unitPriceSats != null && lm.unitPriceSats != '' ? lm.unitPriceSats as Long : null
            Long totalSats = lm.lineTotalSats != null && lm.lineTotalSats != '' ? lm.lineTotalSats as Long : null
            if (totalSats == null && unitSats != null) {
                totalSats = (unitSats * qty.longValue()) as Long
            }
            // serviceRefId / productRefId store catalogue item ids (legacy masters removed)
            Long catId = resolved.catalogueItemId as Long
            Long serviceRef = lm.serviceRefId != null && lm.serviceRefId != '' ? lm.serviceRefId as Long : null
            if (!serviceRef && catId && resolved.nature == CatalogueItem.NATURE_SERVICE) {
                serviceRef = catId
            }
            Long productRef = lm.productRefId != null && lm.productRefId != '' ? lm.productRefId as Long : null
            if (!productRef && catId) productRef = catId
            String refType = lm.productRefType as String
            if (!refType && resolved.nature) {
                refType = resolved.nature == CatalogueItem.NATURE_SERVICE ? 'SERVICE' : 'PRODUCT'
            }
            new ServiceOrderLine(
                    npubOwner       : order.npubOwner,
                    serviceOrder    : order,
                    lineNo          : n++,
                    name            : (lm.name ?: resolved.name ?: lm.description ?: "Line ${n - 1}") as String,
                    description     : lm.description as String,
                    quantity        : qty,
                    unitPriceEur    : unit,
                    lineTotalEur    : total,
                    unitPriceSats   : unitSats,
                    lineTotalSats   : totalSats,
                    serviceRefId    : serviceRef,
                    productRefId    : productRef,
                    productRefType  : refType,
                    catalogueItemId : catId
            ).save(failOnError: true, flush: true)
        }
    }

    private void recomputeTotals(ServiceOrder order) {
        List<ServiceOrderLine> lines = ServiceOrderLine.findAllByServiceOrder(order)
        BigDecimal sum = BigDecimal.ZERO
        long sats = 0L
        lines.each { ServiceOrderLine l ->
            sum = sum.add(l.lineTotalEur ?: BigDecimal.ZERO)
            if (l.lineTotalSats) sats += l.lineTotalSats
        }
        order.amountEur = sum
        order.amountSats = sats > 0 ? sats : null
    }

    private void assertEditable(ServiceOrder o) {
        if (!(o.fulfillStatus in ['DRAFT', 'SCHEDULED'])) {
            throw new IllegalStateException("order is ${o.fulfillStatus}; only DRAFT/SCHEDULED can edit lines")
        }
    }

    private Resource resolveResource(String npub, Map fields) {
        if (fields.resourceId) {
            return delResourceService.getResource(fields.resourceId as Long, npub)
        }
        null
    }

    private ServiceOrder load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        ServiceOrder o = ServiceOrder.get(id)
        if (!o || o.npubOwner != npub) throw new IllegalArgumentException("service order not found: ${id}")
        o
    }

    private String nextOrderNumber(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String prefix = "SVO-${period}-"
        int seq = ServiceOrder.countByNpubOwner(npub) + 1
        String candidate = "${prefix}${String.format('%04d', seq)}"
        while (ServiceOrder.findByNpubOwnerAndOrderNumber(npub, candidate)) {
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
