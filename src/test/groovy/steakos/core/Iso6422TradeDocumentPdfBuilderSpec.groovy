// SPDX-License-Identifier: Apache-2.0
package steakos.core

import spock.lang.Specification

/**
 * Unit tests for ISO 6422-1 A4 trade PDF builder (no Spring context).
 */
class Iso6422TradeDocumentPdfBuilderSpec extends Specification {

    def "builds valid PDF with multiple wrapping line items on A4"() {
        given:
        Map seller = [
                name         : 'Bronowski Properties GmbH',
                legalForm    : 'GMBH',
                taxId        : 'DE123456789',
                countryCode  : 'DE',
                currencyCode : 'EUR',
                addressLines : ['Musterstraße 1', '10115 Berlin', 'Germany'],
                bankLines    : ['Business · IBAN DE89 3704 0044 0532 0130 00 · BIC COBADEFFXXX']
        ]
        List lines = (1..12).collect { int i ->
            [
                    lineNo    : i,
                    name      : "Catalogue item ${i} — grilled steak with seasonal sides and a long description that must wrap across lines",
                    description: "Detail notes for line ${i}: preparation, allergens, and delivery window.",
                    quantity  : new BigDecimal("${i}.5"),
                    unitPrice : new BigDecimal('12.34'),
                    lineTotal : new BigDecimal("${i}.5").multiply(new BigDecimal('12.34'))
            ]
        }
        BigDecimal total = lines.inject(BigDecimal.ZERO) { BigDecimal a, Map l -> a.add(l.lineTotal as BigDecimal) }

        when:
        byte[] pdf = Iso6422TradeDocumentPdfBuilder.build([
                documentType     : 'QUOTE',
                documentNumber   : 'Q-2026-0042',
                status           : 'SENT',
                currencyCode     : 'EUR',
                issueDate        : new Date(),
                validUntil       : new Date() + 14,
                seller           : seller,
                partnerLabel     : 'Consignee / customer',
                partnerName      : 'Orange Steak House',
                partnerLines     : ['Contact: Chef Ada', 'ada@example.com'],
                referenceLines   : ['Subject: Friday catering'],
                termsLines       : ['Offer valid 14 days', 'Payment: net 14'],
                lines            : lines,
                totals           : [
                        ['Subtotal', Iso6422TradeDocumentPdfBuilder.formatMoney(total)],
                        ['Total (EUR)', Iso6422TradeDocumentPdfBuilder.formatMoney(total)]
                ],
                notesLines       : ['Thank you.'],
                freeDisposalLines: ['Payment rails: SEPA · BTC'],
                totalAmount      : total
        ])

        then:
        pdf != null
        pdf.length > 500
        new String(pdf, 0, Math.min(8, pdf.length), 'ISO-8859-1').startsWith('%PDF-')
        def hay = new String(pdf, 'ISO-8859-1')
        // A4 MediaBox (ISO 216) — OpenPDF uses 595×842 pt
        hay.contains('MediaBox[0 0 595 842]') || hay.contains('/MediaBox[0 0 595')
        // Multi-line fixture spills to a second page (header rows repeat)
        hay.contains('/Count 2') || hay.contains('/Count 3')
        hay.contains('OpenPDF')
    }

    def "formatMoney and formatQty are stable"() {
        expect:
        Iso6422TradeDocumentPdfBuilder.formatMoney(new BigDecimal('1234.5')) == '1,234.50'
        Iso6422TradeDocumentPdfBuilder.formatQty(new BigDecimal('3.00')) == '3'
        Iso6422TradeDocumentPdfBuilder.formatQty(new BigDecimal('3.50')) == '3.5'
    }

    def "empty lines still produce a PDF"() {
        when:
        byte[] pdf = Iso6422TradeDocumentPdfBuilder.build([
                documentType   : 'INVOICE',
                documentNumber : 'INV-1',
                seller         : [name: 'Acme', addressLines: []],
                partnerName    : 'Buyer',
                partnerLines   : [],
                lines          : [],
                totals         : [['Total', '0.00']],
                currencyCode   : 'EUR'
        ])

        then:
        pdf.length > 200
        new String(pdf, 0, 5, 'ISO-8859-1') == '%PDF-'
    }
}
