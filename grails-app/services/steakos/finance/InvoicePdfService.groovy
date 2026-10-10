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

package steakos.finance

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.configuration.PartnerLocaleService
import steakos.core.Iso6422TradeDocumentPdfBuilder
import steakos.core.PdfMessageBundle
import steakos.core.TradeDocumentCompanyService
import steakos.sales.Customer
import steakos.sales.OrderLine
import steakos.sales.SalesOrder

/**
 * Copper CU-F7: ISO 6422-1:2010 A4 invoice PDF.
 * Line items: from linked sales order when present; otherwise a single goods line from the invoice amount.
 * Labels follow partner communication locale / document snapshot (OI-I18N02).
 */
@Slf4j
class InvoicePdfService {

    FinanceRevenueService financeRevenueService
    TradeDocumentCompanyService tradeDocumentCompanyService
    PartnerLocaleService partnerLocaleService

    @Transactional(readOnly = true)
    byte[] renderInvoicePdf(Long invoiceId, String npubOwner) {
        Invoice inv = financeRevenueService.getInvoice(invoiceId, npubOwner)
        Map data = financeRevenueService.invoiceToMap(inv, true)
        Map seller = tradeDocumentCompanyService.loadSeller(npubOwner)
        PdfMessageBundle msg = bundleFor(partnerLocaleService?.resolveForDocument(inv))

        Customer customer = inv.customerId ? Customer.get(inv.customerId as Long) : null
        List partnerLines = []
        if (customer) {
            if (customer.contactName) partnerLines << "${msg.get('pdf.contact')}: ${customer.contactName}"
            if (customer.email) partnerLines << customer.email
            if (customer.phone) partnerLines << "${msg.get('pdf.tel')}: ${customer.phone}"
            if (customer.taxId) partnerLines << "${msg.get('pdf.taxId')}: ${customer.taxId}"
        } else if (data.customerContact) {
            partnerLines << data.customerContact
        }

        List lines = invoiceLines(inv, data, msg)
        String cur = (data.currencyCode ?: inv.currencyCode ?: seller.currencyCode ?: 'EUR') as String

        List rails = (data.paymentOptions as List)?.collect { Map o ->
            o.rail as String
        }?.findAll { it }?.unique()
        if (!rails) {
            rails = ['SEPA', 'BTC_ONCHAIN', 'BTC_LIGHTNING']
        }

        List free = [
                msg.format('pdf.inv.paymentRails', rails.join(' · '))
        ]
        Map btc = data.btcIntegration instanceof Map ? data.btcIntegration as Map : null
        if (btc?.level == 'SINGLE_ADDRESS' && btc.receiveAddress) {
            free << msg.format('pdf.inv.btcReceive', btc.receiveAddress)
            free << msg.get('pdf.inv.btcManual')
            if (btc.receiveAddressLabel) {
                free << msg.format('pdf.inv.btcLabel', btc.receiveAddressLabel)
            }
        } else if (btc?.level == 'DEFERRED') {
            free << msg.get('pdf.inv.btcDeferred')
        }
        if (inv.amountSats != null) {
            free << msg.format('pdf.inv.satsHint', inv.amountSats)
        }

        return Iso6422TradeDocumentPdfBuilder.build([
                documentType     : msg.get('pdf.doc.INVOICE'),
                documentNumber   : data.invoiceNumber ?: inv.invoiceNumber ?: inv.id?.toString(),
                status           : data.invoiceStatus ?: inv.invoiceStatus,
                currencyCode     : cur,
                issueDate        : inv.issueDate,
                dueDate          : inv.dueDate,
                seller           : seller,
                partnerLabel     : msg.get('pdf.partner.billTo'),
                partnerName      : data.customerName ?: inv.customerName ?: customer?.name ?: '—',
                partnerLines     : partnerLines,
                referenceLines   : [
                        inv.name ? msg.format('pdf.subject', inv.name) : null,
                        inv.referenceText ? msg.format('pdf.ref.reference', inv.referenceText) : null,
                        inv.recurring ? msg.get('pdf.ref.recurring') : null
                ].findAll { it },
                termsLines       : [
                        msg.format('pdf.inv.dueBy', Iso6422TradeDocumentPdfBuilder.formatDate(inv.dueDate)),
                        msg.get('pdf.inv.quoteNumber')
                ],
                lines            : lines,
                totals           : invoiceTaxTotals(inv, data, cur, lines, msg),
                notesLines       : invoiceNotes(inv, lines, msg),
                freeDisposalLines: free,
                totalAmount      : inv.amountEur,
                labels           : msg.structuralLabels(cur)
        ])
    }

    private PdfMessageBundle bundleFor(String localeCode) {
        String lang = partnerLocaleService?.packLanguage(localeCode) ?: 'en'
        PdfMessageBundle.forLanguage(lang)
    }

    private static List invoiceTaxTotals(Invoice inv, Map data, String cur, List lines, PdfMessageBundle msg) {
        BigDecimal tax = inv.taxAmountEur != null ? inv.taxAmountEur : (data.taxAmountEur as BigDecimal)
        BigDecimal net = inv.netAmountEur != null ? inv.netAmountEur : (data.netAmountEur as BigDecimal)
        boolean hasTaxBreakdown = (tax != null && tax.compareTo(BigDecimal.ZERO) != 0) ||
                (net != null && inv.amountEur != null && net.compareTo(inv.amountEur) != 0)
        boolean hasRc = linesHaveReverseCharge(lines)
        if (hasTaxBreakdown || hasRc) {
            BigDecimal n = net != null ? net : (inv.amountEur ?: BigDecimal.ZERO).subtract(tax ?: BigDecimal.ZERO)
            BigDecimal t = tax ?: BigDecimal.ZERO
            BigDecimal g = inv.amountEur ?: n.add(t)
            return [
                    [msg.get('pdf.total.net'), Iso6422TradeDocumentPdfBuilder.formatMoney(n)],
                    [msg.get('pdf.total.vat'), Iso6422TradeDocumentPdfBuilder.formatMoney(t)],
                    [msg.format('pdf.total.grossDue', cur), Iso6422TradeDocumentPdfBuilder.formatMoney(g)]
            ]
        }
        [
                [msg.get('pdf.total.netTotal'), Iso6422TradeDocumentPdfBuilder.formatMoney(inv.amountEur)],
                [msg.format('pdf.total.amountDue', cur), Iso6422TradeDocumentPdfBuilder.formatMoney(inv.amountEur)]
        ]
    }

    private static List invoiceNotes(Invoice inv, List lines, PdfMessageBundle msg) {
        List notes = inv.description ? [inv.description as String] : [msg.get('pdf.inv.thanks')]
        steakos.configuration.CompanyTaxService.legalPhrasesForLines(lines).each { notes << it }
        notes
    }

    private static boolean linesHaveReverseCharge(List lines) {
        (lines ?: []).any { Object raw ->
            Map lm = raw instanceof Map ? (Map) raw : [:]
            lm.reverseCharge == true || lm.reverseCharge == Boolean.TRUE ||
                    ((lm.taxCode ?: '') as String).contains('_RC')
        }
    }

    /**
     * Prefer sales-order lines when this invoice was raised from an order; else one summary line.
     */
    private static List invoiceLines(Invoice inv, Map data, PdfMessageBundle msg) {
        SalesOrder order = SalesOrder.findByFinanceInvoiceId(inv.id)
        if (order) {
            List<OrderLine> ols = OrderLine.findAllBySalesOrder(order, [sort: 'lineNo'])
            if (ols) {
                return ols.collect { OrderLine l ->
                    [
                            lineNo         : l.lineNo,
                            name           : l.name ?: 'Line',
                            description    : l.description,
                            quantity       : l.quantity,
                            unitPrice      : l.unitPriceEur,
                            lineTotal      : l.lineTotalEur,
                            taxCode        : l.taxCode,
                            taxAmountEur   : l.taxAmountEur,
                            reverseCharge  : l.reverseCharge == Boolean.TRUE
                    ]
                }
            }
        }
        // Single commercial line from invoice header (Copper invoices are amount-centric today)
        String label = inv.name?.trim() ?: inv.referenceText?.trim() ?: msg.get('pdf.line.goods')
        return [[
                        lineNo    : 1,
                        name      : label,
                        description: inv.description,
                        quantity  : BigDecimal.ONE,
                        unitPrice : inv.amountEur,
                        lineTotal : inv.amountEur
                ]]
    }
}
