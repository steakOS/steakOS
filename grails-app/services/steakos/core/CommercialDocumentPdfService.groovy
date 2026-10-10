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

package steakos.core

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.configuration.PartnerLocaleService
import steakos.delivery.DelOrderService
import steakos.delivery.ServiceOrder
import steakos.procurement.PurchaseOrder
import steakos.procurement.Vendor
import steakos.production.ProductionOrder
import steakos.sales.Customer
import steakos.sales.CustomerQuote
import steakos.sales.SalesOrder
import steakos.sales.SalesOrderService
import steakos.sales.SalesQuoteService
import steakos.service.AsRmaService
import steakos.service.Rma
import steakos.supplychain.ScmProductionService
import steakos.supplychain.ScmPurchaseService

/**
 * ISO 6422-1:2010 A4 trade PDFs for commercial / logistics documents.
 * Invoice uses {@link steakos.finance.InvoicePdfService} with the same builder.
 * Labels follow partner {@code communicationLocale} / document snapshot (OI-I18N02).
 */
@Slf4j
class CommercialDocumentPdfService {

    SalesQuoteService salesQuoteService
    SalesOrderService salesOrderService
    ScmPurchaseService scmPurchaseService
    ScmProductionService scmProductionService
    DelOrderService delOrderService
    AsRmaService asRmaService
    TradeDocumentCompanyService tradeDocumentCompanyService
    PartnerLocaleService partnerLocaleService

    @Transactional(readOnly = true)
    byte[] renderQuotePdf(Long quoteId, String npubOwner) {
        CustomerQuote q = salesQuoteService.getQuote(quoteId, npubOwner)
        Map data = salesQuoteService.toMap(q, true)
        Map seller = tradeDocumentCompanyService.loadSeller(npubOwner)
        PdfMessageBundle msg = bundleFor(partnerLocaleService?.resolveForDocument(q))
        Map partner = partnerFromCustomer(q.customer, data.customerName as String, msg)

        List lines = normalizeLines(data.lines as List)
        String cur = (data.currencyCode ?: seller.currencyCode ?: 'EUR') as String

        return Iso6422TradeDocumentPdfBuilder.build([
                documentType    : msg.get('pdf.doc.QUOTE'),
                documentNumber  : data.quoteNumber ?: q.quoteNumber ?: q.id?.toString(),
                status          : data.quoteStatus ?: q.quoteStatus,
                currencyCode    : cur,
                issueDate       : q.issueDate,
                validUntil      : q.validUntil,
                seller          : seller,
                partnerLabel    : msg.get('pdf.partner.customer'),
                partnerName     : partner.name,
                partnerLines    : partner.lines,
                referenceLines  : [
                        q.name ? msg.format('pdf.subject', q.name) : null,
                        data.resultingOrderId ? msg.format('pdf.ref.resultingOrder', data.resultingOrderId) : null
                ].findAll { it },
                termsLines      : [
                        msg.format('pdf.quote.validUntil', Iso6422TradeDocumentPdfBuilder.formatDate(q.validUntil)),
                        msg.get('pdf.quote.terms')
                ],
                lines           : lines,
                totals          : taxAwareTotals(data, q.amountEur, cur, msg),
                notesLines      : withTaxAndCurrencyNotes(q.description ? [q.description as String] : [], data, msg),
                freeDisposalLines: [],
                totalAmount     : data.amountEur ?: q.amountEur,
                labels          : msg.structuralLabels(cur)
        ])
    }

    @Transactional(readOnly = true)
    byte[] renderSalesOrderPdf(Long orderId, String npubOwner) {
        SalesOrder o = salesOrderService.getOrder(orderId, npubOwner)
        Map data = salesOrderService.toMap(o, true)
        Map seller = tradeDocumentCompanyService.loadSeller(npubOwner)
        PdfMessageBundle msg = bundleFor(partnerLocaleService?.resolveForDocument(o))
        Map partner = partnerFromCustomer(o.customer, data.customerName as String, msg)

        List lines = normalizeLines(data.lines as List)
        String cur = (data.currencyCode ?: seller.currencyCode ?: 'EUR') as String

        return Iso6422TradeDocumentPdfBuilder.build([
                documentType    : msg.get('pdf.doc.SALES_ORDER'),
                documentNumber  : data.orderNumber ?: o.orderNumber ?: o.id?.toString(),
                status          : data.orderStatus ?: o.orderStatus,
                currencyCode    : cur,
                issueDate       : o.orderDate ?: o.dateCreated,
                orderDate       : o.orderDate ?: o.dateCreated,
                seller          : seller,
                partnerLabel    : msg.get('pdf.partner.customer'),
                partnerName     : partner.name,
                partnerLines    : partner.lines,
                referenceLines  : [
                        o.name ? msg.format('pdf.subject', o.name) : null,
                        data.sourceQuoteId ? msg.format('pdf.ref.sourceQuote', data.sourceQuoteId) : null,
                        data.financeInvoiceId ? msg.format('pdf.ref.financeInvoice', data.financeInvoiceId) : null
                ].findAll { it },
                termsLines      : [
                        msg.get('pdf.so.terms'),
                        o.confirmedAt
                                ? msg.format('pdf.so.confirmed', Iso6422TradeDocumentPdfBuilder.formatDate(o.confirmedAt))
                                : msg.get('pdf.so.confirmation')
                ],
                lines           : lines,
                totals          : taxAwareTotals(data, o.amountEur, cur, msg),
                notesLines      : withTaxAndCurrencyNotes(o.description ? [o.description as String] : [], data, msg),
                freeDisposalLines: [],
                totalAmount     : data.amountEur ?: o.amountEur,
                labels          : msg.structuralLabels(cur)
        ])
    }

    @Transactional(readOnly = true)
    byte[] renderPurchaseOrderPdf(Long poId, String npubOwner) {
        PurchaseOrder po = scmPurchaseService.getPurchaseOrder(poId, npubOwner)
        Map data = scmPurchaseService.toMap(po, true)
        Map seller = tradeDocumentCompanyService.loadSeller(npubOwner)
        PdfMessageBundle msg = bundleFor(partnerLocaleService?.resolveForDocument(po))
        // On a PO the "consignor" zone is still our company (buyer); partner is the vendor.
        Map partner = partnerFromVendor(po.vendor, (data.vendorName ?: data.vendor?.name) as String, msg)

        List lines = normalizeLines(data.lines as List)
        String cur = (data.currencyCode ?: seller.currencyCode ?: 'EUR') as String

        return Iso6422TradeDocumentPdfBuilder.build([
                documentType    : msg.get('pdf.doc.PURCHASE_ORDER'),
                documentNumber  : data.poNumber ?: po.poNumber ?: po.id?.toString(),
                status          : data.poStatus ?: po.poStatus,
                currencyCode    : cur,
                issueDate       : po.orderDate ?: po.dateCreated,
                orderDate       : po.orderDate ?: po.dateCreated,
                seller          : seller,
                partnerLabel    : msg.get('pdf.partner.vendor'),
                partnerName     : partner.name,
                partnerLines    : partner.lines,
                referenceLines  : [
                        po.name ? msg.format('pdf.subject', po.name) : null,
                        data.financePayableId ? msg.format('pdf.ref.financePayable', data.financePayableId) : null
                ].findAll { it },
                termsLines      : [
                        msg.get('pdf.po.terms'),
                        po.confirmedAt
                                ? msg.format('pdf.po.vendorConfirmed', Iso6422TradeDocumentPdfBuilder.formatDate(po.confirmedAt))
                                : msg.get('pdf.po.awaiting')
                ],
                lines           : lines,
                totals          : [
                        [msg.get('pdf.total.subtotal'), Iso6422TradeDocumentPdfBuilder.formatMoney(data.amountEur ?: po.amountEur)],
                        [msg.format('pdf.total.total', cur), Iso6422TradeDocumentPdfBuilder.formatMoney(data.amountEur ?: po.amountEur)]
                ],
                notesLines      : withCurrencyFootnotes(po.description ? [po.description as String] : [], data.warnings as List, msg),
                freeDisposalLines: [],
                totalAmount     : data.amountEur ?: po.amountEur,
                labels          : msg.structuralLabels(cur)
        ])
    }

    /**
     * Delivery / service delivery note (ServiceOrder).
     * Title adapts: SERVICE DELIVERY NOTE when lines look service-heavy, else DELIVERY NOTE.
     */
    @Transactional(readOnly = true)
    byte[] renderDeliveryOrderPdf(Long orderId, String npubOwner) {
        ServiceOrder o = delOrderService.getOrder(orderId, npubOwner)
        Map data = delOrderService.toMap(o, true)
        Map seller = tradeDocumentCompanyService.loadSeller(npubOwner)
        Customer customer = o.customerId ? Customer.get(o.customerId as Long) : null
        PdfMessageBundle msg = bundleFor(partnerLocaleService?.resolveCustomer(customer))
        Map partner = partnerFromCustomer(customer, data.customerName as String, msg)

        List lines = normalizeLines(data.lines as List)
        if (!lines) {
            // Header-only service order: synthesise one line from catalogue/service name
            lines = [[
                    lineNo    : 1,
                    name      : data.serviceName ?: o.name ?: msg.get('pdf.line.delivery'),
                    description: o.description,
                    quantity  : BigDecimal.ONE,
                    unitPrice : o.amountEur,
                    lineTotal : o.amountEur
            ]]
        }
        boolean serviceHeavy = lines.every { Map lm ->
            String n = ((lm.name ?: '') as String).toLowerCase()
            n.contains('service') || lm.serviceRefId != null
        } || (data.serviceId || data.catalogueItemId)
        String docType = serviceHeavy ? msg.get('pdf.doc.SERVICE_DELIVERY_NOTE') : msg.get('pdf.doc.DELIVERY_NOTE')
        String cur = (data.currencyCode ?: seller.currencyCode ?: 'EUR') as String

        return Iso6422TradeDocumentPdfBuilder.build([
                documentType     : docType,
                documentNumber   : data.orderNumber ?: o.orderNumber ?: o.id?.toString(),
                status           : data.fulfillStatus ?: o.fulfillStatus,
                currencyCode     : cur,
                issueDate        : o.scheduledAt ?: o.dateCreated,
                orderDate        : o.startedAt ?: o.scheduledAt,
                seller           : seller,
                partnerLabel     : msg.get('pdf.partner.customer'),
                partnerName      : partner.name,
                partnerLines     : partner.lines,
                referenceLines   : [
                        o.name ? msg.format('pdf.subject', o.name) : null,
                        data.sourceSalesOrderId ? msg.format('pdf.ref.sourceSalesOrder', data.sourceSalesOrderId) : null,
                        data.resourceName ? msg.format('pdf.ref.resource', data.resourceName) : null,
                        o.completedAt ? msg.format('pdf.ref.completed', Iso6422TradeDocumentPdfBuilder.formatDate(o.completedAt)) : null
                ].findAll { it },
                termsLines       : [
                        msg.get('pdf.del.terms'),
                        o.resource?.name
                                ? msg.format('pdf.del.assigned', o.resource.name)
                                : msg.get('pdf.del.schedule')
                ],
                lines            : lines,
                totals           : [
                        [msg.get('pdf.total.subtotal'), Iso6422TradeDocumentPdfBuilder.formatMoney(data.amountEur ?: o.amountEur)],
                        [msg.format('pdf.total.total', cur), Iso6422TradeDocumentPdfBuilder.formatMoney(data.amountEur ?: o.amountEur)]
                ],
                notesLines       : o.description ? [o.description as String] : [],
                freeDisposalLines: [],
                totalAmount      : data.amountEur ?: o.amountEur,
                labels           : msg.structuralLabels(cur)
        ])
    }

    /** Returns / RMA — ISO 6422-1 layout (no separate RMA layout key). */
    @Transactional(readOnly = true)
    byte[] renderRmaPdf(Long rmaId, String npubOwner) {
        Rma r = asRmaService.get(rmaId, npubOwner)
        Map data = asRmaService.toMap(r)
        Map seller = tradeDocumentCompanyService.loadSeller(npubOwner)
        Customer customer = r.customerId ? Customer.get(r.customerId as Long) : null
        PdfMessageBundle msg = bundleFor(partnerLocaleService?.resolveCustomer(customer))
        Map partner = partnerFromCustomer(customer, data.customerName as String, msg)
        String cur = (data.currencyCode ?: seller.currencyCode ?: 'EUR') as String

        List lines = [[
                lineNo      : 1,
                name        : data.productName ?: r.productName ?: r.name ?: msg.get('pdf.line.returned'),
                description : [r.reason, r.description].findAll { it }.join('\n') ?: null,
                quantity    : r.quantity ?: BigDecimal.ONE,
                unitPrice   : null,
                lineTotal   : r.expectedRefundEur
        ]]

        return Iso6422TradeDocumentPdfBuilder.build([
                documentType     : msg.get('pdf.doc.RETURN_RMA'),
                documentNumber   : data.rmaNumber ?: r.rmaNumber ?: r.id?.toString(),
                status           : data.rmaStatus ?: r.rmaStatus,
                currencyCode     : cur,
                issueDate        : r.requestedAt ?: r.dateCreated,
                orderDate        : r.confirmedAt,
                seller           : seller,
                partnerLabel     : msg.get('pdf.partner.returnFrom'),
                partnerName      : partner.name,
                partnerLines     : partner.lines,
                referenceLines   : [
                        r.salesOrderId ? msg.format('pdf.ref.salesOrder', r.salesOrderId) : null,
                        data.inquiryId ? msg.format('pdf.ref.inquiry', data.inquiryId) : null,
                        r.stockMoveId ? msg.format('pdf.ref.stockMove', r.stockMoveId) : null,
                        r.closedAt ? msg.format('pdf.ref.closed', Iso6422TradeDocumentPdfBuilder.formatDate(r.closedAt)) : null
                ].findAll { it },
                termsLines       : [
                        msg.get('pdf.rma.terms'),
                        r.reason ? msg.format('pdf.rma.reason', r.reason) : msg.get('pdf.rma.reasonDefault')
                ],
                lines            : lines,
                totals           : [
                        [msg.get('pdf.total.expectedRefund'), Iso6422TradeDocumentPdfBuilder.formatMoney(r.expectedRefundEur)],
                        [msg.format('pdf.total.amountCur', cur), Iso6422TradeDocumentPdfBuilder.formatMoney(r.expectedRefundEur)]
                ],
                notesLines       : r.description ? [r.description as String] : [],
                freeDisposalLines: [],
                totalAmount      : r.expectedRefundEur,
                labels           : msg.structuralLabels(cur)
        ])
    }

    /** Production / manufacturing order — ISO 6422-1 layout (no separate MO layout key). */
    @Transactional(readOnly = true)
    byte[] renderProductionOrderPdf(Long orderId, String npubOwner) {
        ProductionOrder po = scmProductionService.getOrder(orderId, npubOwner)
        Map data = scmProductionService.toMap(po)
        Map seller = tradeDocumentCompanyService.loadSeller(npubOwner)
        // Internal doc — company default / en_US (no partner)
        PdfMessageBundle msg = bundleFor(PartnerLocaleService.FALLBACK)
        String cur = (seller.currencyCode ?: 'EUR') as String

        List lines = [[
                lineNo      : 1,
                name        : data.productName ?: po.catalogueItem?.name ?: po.name ?: msg.get('pdf.line.manufactured'),
                description : po.description,
                quantity    : po.quantity ?: BigDecimal.ONE,
                unit        : po.unit ?: data.unit,
                unitPrice   : null,
                lineTotal   : null
        ]]

        return Iso6422TradeDocumentPdfBuilder.build([
                documentType     : msg.get('pdf.doc.PRODUCTION_ORDER'),
                documentNumber   : data.orderNumber ?: po.orderNumber ?: po.id?.toString(),
                status           : data.prodStatus ?: po.prodStatus,
                currencyCode     : cur,
                issueDate        : po.scheduledStart ?: po.dateCreated,
                orderDate        : po.scheduledStart,
                dueDate          : po.scheduledEnd,
                seller           : seller,
                partnerLabel     : msg.get('pdf.partner.internal'),
                partnerName      : seller.name ?: 'Production',
                partnerLines     : [
                        data.nature ? msg.format('pdf.ref.nature', data.nature) : null,
                        po.resultingLotId ? msg.format('pdf.ref.lot', po.resultingLotId) : null
                ].findAll { it },
                referenceLines   : [
                        po.name ? msg.format('pdf.subject', po.name) : null,
                        data.sourceSalesOrderId ? msg.format('pdf.ref.sourceSalesOrder', data.sourceSalesOrderId) : null,
                        po.completedAt ? msg.format('pdf.ref.completed', Iso6422TradeDocumentPdfBuilder.formatDate(po.completedAt)) : null
                ].findAll { it },
                termsLines       : [
                        msg.get('pdf.mo.terms'),
                        po.scheduledStart || po.scheduledEnd
                                ? msg.format('pdf.mo.schedule',
                                Iso6422TradeDocumentPdfBuilder.formatDate(po.scheduledStart),
                                Iso6422TradeDocumentPdfBuilder.formatDate(po.scheduledEnd))
                                : msg.get('pdf.mo.scheduleDefault')
                ],
                lines            : lines,
                totals           : [
                        [msg.get('pdf.total.quantity'), Iso6422TradeDocumentPdfBuilder.formatQty(po.quantity)],
                        [msg.get('pdf.total.unit'), (po.unit ?: data.unit ?: 'ea') as String]
                ],
                notesLines       : po.description ? [po.description as String] : [],
                freeDisposalLines: [],
                totalAmount      : null,
                labels           : msg.structuralLabels(cur)
        ])
    }

    private PdfMessageBundle bundleFor(String localeCode) {
        String lang = partnerLocaleService?.packLanguage(localeCode) ?: 'en'
        PdfMessageBundle.forLanguage(lang)
    }

    /** OI-CUR01 D14 — soft currency mismatch footnotes on PDF notes. */
    private static List withCurrencyFootnotes(List baseNotes, List warnings, PdfMessageBundle msg = null) {
        PdfMessageBundle m = msg ?: PdfMessageBundle.forLanguage('en')
        List notes = []
        (baseNotes ?: []).each { notes << it }
        (warnings ?: []).each { Object raw ->
            Map w = raw instanceof Map ? (Map) raw : [:]
            String text = w.message as String
            if (text) notes << m.format('pdf.currencyNote', text)
        }
        notes
    }

    /** OI-F23 — append reverse-charge / IG phrases + currency footnotes. */
    private static List withTaxAndCurrencyNotes(List baseNotes, Map data, PdfMessageBundle msg = null) {
        List notes = withCurrencyFootnotes(baseNotes, data?.warnings as List, msg)
        (data?.taxNotes as List)?.each { Object n ->
            if (n) notes << n.toString()
        }
        notes
    }

    /** OI-F23 D5/D8 — Net / Tax / Gross when tax present; else legacy Subtotal / Total. */
    private static List taxAwareTotals(Map data, BigDecimal fallbackAmount, String cur, PdfMessageBundle msg = null) {
        PdfMessageBundle m = msg ?: PdfMessageBundle.forLanguage('en')
        BigDecimal tax = toBd(data?.taxAmountEur)
        BigDecimal net = toBd(data?.netAmountEur)
        BigDecimal gross = toBd(data?.amountEur) ?: fallbackAmount
        boolean showTax = (tax != null && tax.compareTo(BigDecimal.ZERO) != 0) ||
                (net != null && gross != null && net.compareTo(gross) != 0) ||
                ((data?.taxNotes as List)?.size() > 0)
        if (showTax) {
            BigDecimal n = net != null ? net : (gross ?: BigDecimal.ZERO).subtract(tax ?: BigDecimal.ZERO)
            BigDecimal t = tax ?: BigDecimal.ZERO
            BigDecimal g = gross ?: n.add(t)
            return [
                    [m.get('pdf.total.net'), Iso6422TradeDocumentPdfBuilder.formatMoney(n)],
                    [m.get('pdf.total.vat'), Iso6422TradeDocumentPdfBuilder.formatMoney(t)],
                    [m.format('pdf.total.gross', cur), Iso6422TradeDocumentPdfBuilder.formatMoney(g)]
            ]
        }
        [
                [m.get('pdf.total.subtotal'), Iso6422TradeDocumentPdfBuilder.formatMoney(gross)],
                [m.format('pdf.total.total', cur), Iso6422TradeDocumentPdfBuilder.formatMoney(gross)]
        ]
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') return null
        if (v instanceof BigDecimal) return v
        new BigDecimal(v.toString())
    }

    private static List normalizeLines(List itemLines) {
        if (!itemLines) return []
        itemLines.collect { Object raw ->
            Map lm = raw as Map
            [
                    lineNo       : lm.lineNo,
                    name         : lm.name ?: lm.description ?: 'Line',
                    description  : lm.description,
                    quantity     : lm.quantity,
                    unit         : lm.unit,
                    unitPrice    : lm.unitPriceEur ?: lm.unitPrice,
                    lineTotal    : lm.lineTotalEur ?: lm.lineTotal,
                    serviceRefId : lm.serviceRefId
            ]
        }
    }

    private static Map partnerFromCustomer(Customer c, String fallbackName, PdfMessageBundle msg) {
        List lines = []
        if (c) {
            if (c.contactName) lines << "${msg.get('pdf.contact')}: ${c.contactName}"
            if (c.email) lines << c.email
            if (c.phone) lines << "${msg.get('pdf.tel')}: ${c.phone}"
            if (c.taxId) lines << "${msg.get('pdf.taxId')}: ${c.taxId}"
            if (c.description) lines << c.description
        }
        [name: (c?.name ?: fallbackName ?: '—'), lines: lines]
    }

    private static Map partnerFromVendor(Vendor v, String fallbackName, PdfMessageBundle msg) {
        List lines = []
        if (v) {
            if (v.contactName) lines << "${msg.get('pdf.contact')}: ${v.contactName}"
            if (v.email) lines << v.email
            if (v.phone) lines << "${msg.get('pdf.tel')}: ${v.phone}"
            if (v.taxId) lines << "${msg.get('pdf.taxId')}: ${v.taxId}"
            if (v.description) lines << v.description
        }
        [name: (v?.name ?: fallbackName ?: '—'), lines: lines]
    }
}
