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

package steakos.sales

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.catalogue.CatalogueService
import steakos.finance.FinanceRevenueService
import steakos.finance.Invoice

/**
 * Sales order capture (S-S3). SS3: confirm → Finance DRAFT invoice. Never posts journals.
 */
@Slf4j
class SalesOrderService {

    SalesCustomerService salesCustomerService
    FinanceRevenueService financeRevenueService
    CatalogueService catalogueService
    steakos.configuration.CompanyCurrencyService companyCurrencyService
    steakos.configuration.CompanyTaxService companyTaxService
    steakos.configuration.PartnerLocaleService partnerLocaleService

    @Transactional
    SalesOrder createOrder(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Customer customer = resolveCustomer(npub, fields)
        String number = fields.orderNumber?.toString()?.trim() ?: nextOrderNumber(npub)
        String ccy = (fields.currencyCode ?: customer?.currencyCode ?: 'EUR') as String
        companyCurrencyService?.validateWritableCode(npub, ccy)

        SalesOrder order = new SalesOrder(
                npubOwner    : npub,
                name         : (fields.name ?: "Order ${number}") as String,
                description  : fields.description as String,
                orderNumber  : number,
                customer     : customer,
                customerName : customer?.name ?: fields.customerName as String,
                orderDate    : parseDate(fields.orderDate) ?: new Date(),
                currencyCode : ccy,
                orderStatus  : 'DRAFT',
                status       : 'DRAFT',
                amountEur    : BigDecimal.ZERO
        )
        order.save(failOnError: true, flush: true)

        List lineMaps = (fields.lines instanceof List) ? (List) fields.lines : []
        if (lineMaps) {
            replaceLines(order, lineMaps)
        }
        recomputeTotals(order)
        order.save(failOnError: true, flush: true)
        return order
    }

    /**
     * SS2: create DRAFT order from accepted quote (lines copied).
     */
    @Transactional
    SalesOrder createFromQuote(Long quoteId, String npubOwner) {
        String npub = requireNpub(npubOwner)
        CustomerQuote q = CustomerQuote.get(quoteId)
        if (!q || q.npubOwner != npub) {
            throw new IllegalArgumentException("quote not found: ${quoteId}")
        }
        List<QuoteLine> qLines = QuoteLine.findAllByQuote(q, [sort: 'lineNo'])
        List lineMaps = qLines.collect { QuoteLine ql ->
            [
                    name         : ql.name,
                    description  : ql.description,
                    quantity     : ql.quantity,
                    unitPriceEur : ql.unitPriceEur,
                    unitPriceSats: ql.unitPriceSats,
                    productRefId    : ql.productRefId,
                    productRefType  : ql.productRefType,
                    catalogueItemId : ql.catalogueItemId,
                    taxCode         : ql.taxCode
            ]
        }
        SalesOrder order = createOrder(npub, [
                name        : "Order from ${q.quoteNumber}",
                customerId  : q.customer?.id,
                customerName: q.customerName,
                currencyCode: q.currencyCode,
                lines       : lineMaps
        ])
        order.sourceQuote = q
        order.save(failOnError: true, flush: true)
        return order
    }

    @Transactional
    SalesOrder setLines(Long orderId, String npubOwner, List lineMaps) {
        SalesOrder order = load(orderId, npubOwner)
        assertDraft(order)
        replaceLines(order, lineMaps ?: [])
        recomputeTotals(order)
        order.save(failOnError: true, flush: true)
        return order
    }

    @Transactional
    Map confirmOrder(Long orderId, String npubOwner) {
        SalesOrder order = load(orderId, npubOwner)
        if (order.orderStatus == 'CONFIRMED' || order.orderStatus == 'CLOSED') {
            return [
                    order           : toMap(order, true),
                    financeInvoiceId: order.financeInvoiceId,
                    alreadyConfirmed: true
            ]
        }
        if (order.orderStatus == 'CANCELLED') {
            throw new IllegalStateException('cannot confirm a cancelled order')
        }
        if ((order.amountEur ?: BigDecimal.ZERO) <= BigDecimal.ZERO) {
            throw new IllegalStateException('order total must be > 0')
        }

        order.orderStatus = 'CONFIRMED'
        order.status = 'CONFIRMED'
        order.confirmedAt = new Date()
        partnerLocaleService?.snapshotSalesOrder(order)
        order.save(failOnError: true, flush: true)

        // SS3: Finance DRAFT invoice
        Invoice inv = null
        if (financeRevenueService != null) {
            inv = financeRevenueService.createInvoice(order.npubOwner, [
                    customerId     : order.customer?.id,
                    customerName   : order.customerName,
                    customerContact: order.customer?.email,
                    amountEur      : order.amountEur,
                    netAmountEur   : order.netAmountEur,
                    taxAmountEur   : order.taxAmountEur,
                    amountSats     : order.amountSats,
                    currencyCode   : order.currencyCode,
                    referenceText  : "Sales order ${order.orderNumber}",
                    name           : "Invoice for ${order.orderNumber}",
                    issue          : false
            ])
            order.financeInvoiceId = inv.id
            order.save(failOnError: true, flush: true)
        }

        log.info('Confirmed order {} → invoice {}', order.orderNumber, inv?.id)
        return [
                order          : toMap(order, true),
                financeInvoiceId: order.financeInvoiceId,
                alreadyConfirmed: false
        ]
    }

    @Transactional
    SalesOrder cancelOrder(Long orderId, String npubOwner) {
        SalesOrder order = load(orderId, npubOwner)
        if (order.orderStatus == 'CANCELLED') return order
        if (order.financeInvoiceId) {
            throw new IllegalStateException('cancel via Finance void when invoice exists (manual for now)')
        }
        order.orderStatus = 'CANCELLED'
        order.status = 'CANCELLED'
        order.save(failOnError: true, flush: true)
        return order
    }

    @Transactional(readOnly = true)
    List<SalesOrder> listOrders(String npubOwner, String status = null) {
        listOrdersPage(npubOwner, status, 200, 0).items as List<SalesOrder>
    }

    @Transactional(readOnly = true)
    Map listOrdersPage(String npubOwner, String status = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        if (status) {
            return [
                    items: SalesOrder.findAllByNpubOwnerAndOrderStatus(npub, status, opts),
                    total: SalesOrder.countByNpubOwnerAndOrderStatus(npub, status) as int
            ]
        }
        return [
                items: SalesOrder.findAllByNpubOwner(npub, opts),
                total: SalesOrder.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    List<SalesOrder> listOpenOrders(String npubOwner) {
        String npub = requireNpub(npubOwner)
        SalesOrder.findAllByNpubOwnerAndOrderStatusInList(npub, ['DRAFT', 'CONFIRMED'], [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    SalesOrder getOrder(Long id, String npubOwner) {
        load(id, npubOwner)
    }

    Map toMap(SalesOrder o, boolean includeLines = false) {
        if (!o) return null
        Map m = [
                id              : o.id,
                name            : o.name,
                description     : o.description,
                orderNumber     : o.orderNumber,
                customerId      : o.customer?.id,
                customerName    : o.customerName,
                sourceQuoteId   : o.sourceQuote?.id,
                orderDate       : o.orderDate,
                confirmedAt     : o.confirmedAt,
                amountEur       : o.amountEur,
                netAmountEur    : o.netAmountEur,
                taxAmountEur    : o.taxAmountEur,
                amountSats      : o.amountSats,
                currencyCode    : o.currencyCode,
                documentLocale  : o.documentLocale,
                orderStatus     : o.orderStatus,
                financeInvoiceId: o.financeInvoiceId,
                npubOwner       : o.npubOwner
        ]
        if (includeLines) {
            m.lines = OrderLine.findAllBySalesOrder(o, [sort: 'lineNo']).collect { lineToMap(it) }
        }
        m.warnings = buildWarnings(o, includeLines ? (m.lines as List) : null)
        List linesForTax = includeLines ? (m.lines as List) : OrderLine.findAllBySalesOrder(o, [sort: 'lineNo']).collect { lineToMap(it) }
        m.taxNotes = steakos.configuration.CompanyTaxService.legalPhrasesForLines(linesForTax)
        return m
    }

    List<Map> buildWarnings(SalesOrder o, List lines = null) {
        if (!o || companyCurrencyService == null) return []
        List lineItems = []
        List src = lines
        if (src == null) {
            src = OrderLine.findAllBySalesOrder(o, [sort: 'lineNo']).collect { lineToMap(it) }
        }
        src.each { Object raw ->
            Map lm = raw as Map
            Map resolved = catalogueService != null ? catalogueService.resolveForLine(o.npubOwner, lm) : [:]
            if (resolved.currency) {
                lineItems << [name: (lm.name ?: resolved.name), currency: resolved.currency]
            }
        }
        companyCurrencyService.collectDocumentWarnings(
                o.npubOwner, o.currencyCode, o.customer?.currencyCode, 'Customer', lineItems)
    }

    Map lineToMap(OrderLine l) {
        [
                id           : l.id,
                lineNo       : l.lineNo,
                name         : l.name,
                description  : l.description,
                quantity     : l.quantity,
                unitPriceEur : l.unitPriceEur,
                lineTotalEur : l.lineTotalEur,
                unitPriceSats   : l.unitPriceSats,
                lineTotalSats   : l.lineTotalSats,
                productRefId    : l.productRefId,
                productRefType  : l.productRefType,
                catalogueItemId : l.catalogueItemId,
                taxCode         : l.taxCode,
                taxRatePercent  : l.taxRatePercent,
                taxAmountEur    : l.taxAmountEur,
                reverseCharge   : l.reverseCharge == Boolean.TRUE,
                grossAmountEur  : (l.lineTotalEur ?: BigDecimal.ZERO).add(l.taxAmountEur ?: BigDecimal.ZERO)
        ]
    }

    private void replaceLines(SalesOrder order, List lineMaps) {
        OrderLine.findAllBySalesOrder(order).each { it.delete(flush: true) }
        int n = 1
        lineMaps.each { Object raw ->
            Map lm = raw as Map
            Map resolved = resolveLineCatalogue(order.npubOwner, lm)
            BigDecimal qty = toBd(lm.quantity) ?: BigDecimal.ONE
            BigDecimal unit = toBd(lm.unitPriceEur) ?: (resolved.listPrice as BigDecimal) ?: BigDecimal.ZERO
            BigDecimal total = toBd(lm.lineTotalEur)
            if (total == null) total = qty.multiply(unit)
            Map tax = resolveTax(order.npubOwner, total, lm.taxCode as String, resolved.defaultTaxCode as String)
            new OrderLine(
                    npubOwner       : order.npubOwner,
                    salesOrder      : order,
                    lineNo          : n++,
                    name            : (lm.name ?: resolved.name ?: lm.description ?: "Line ${n - 1}") as String,
                    description     : lm.description as String,
                    quantity        : qty,
                    unitPriceEur    : unit,
                    lineTotalEur    : total,
                    unitPriceSats   : null,
                    lineTotalSats   : null,
                    productRefId    : lm.productRefId != null && lm.productRefId != '' ? lm.productRefId as Long : null,
                    productRefType  : lm.productRefType as String,
                    catalogueItemId : resolved.catalogueItemId as Long,
                    taxCode         : tax.taxCode as String,
                    taxRatePercent  : tax.ratePercent as BigDecimal,
                    taxAmountEur    : tax.taxAmountEur as BigDecimal,
                    reverseCharge   : tax.reverseCharge as Boolean
            ).save(failOnError: true, flush: true)
        }
    }

    private Map resolveLineCatalogue(String npub, Map lm) {
        if (catalogueService != null) {
            return catalogueService.resolveForLine(npub, lm)
        }
        [
                catalogueItemId: lm.catalogueItemId != null && lm.catalogueItemId != '' ? lm.catalogueItemId as Long : null,
                name           : lm.name,
                listPrice      : null,
                defaultTaxCode : null
        ]
    }

    private Map resolveTax(String npub, BigDecimal net, String taxCode, String catalogueDefault) {
        if (companyTaxService == null) {
            return [taxCode: taxCode, ratePercent: null, taxAmountEur: BigDecimal.ZERO,
                    reverseCharge: false, intraCommunity: false]
        }
        companyTaxService.resolveLineTax(npub, net, taxCode, catalogueDefault)
    }

    private void recomputeTotals(SalesOrder order) {
        List<OrderLine> lines = OrderLine.findAllBySalesOrder(order)
        BigDecimal net = BigDecimal.ZERO
        BigDecimal tax = BigDecimal.ZERO
        lines.each { OrderLine l ->
            net = net.add(l.lineTotalEur ?: BigDecimal.ZERO)
            tax = tax.add(l.taxAmountEur ?: BigDecimal.ZERO)
        }
        order.netAmountEur = net
        order.taxAmountEur = tax
        order.amountEur = net.add(tax)
        order.amountSats = null
    }

    private void assertDraft(SalesOrder o) {
        if (o.orderStatus != 'DRAFT') {
            throw new IllegalStateException("order is ${o.orderStatus}; only DRAFT can be edited")
        }
    }

    private Customer resolveCustomer(String npub, Map fields) {
        if (fields.customerId) {
            return salesCustomerService.getCustomer(fields.customerId as Long, npub)
        }
        null
    }

    private SalesOrder load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        SalesOrder o = SalesOrder.get(id)
        if (!o || o.npubOwner != npub) throw new IllegalArgumentException("order not found: ${id}")
        o
    }

    private String nextOrderNumber(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String prefix = "SO-${period}-"
        int seq = SalesOrder.countByNpubOwner(npub) + 1
        String candidate = "${prefix}${String.format('%04d', seq)}"
        while (SalesOrder.findByNpubOwnerAndOrderNumber(npub, candidate)) {
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
