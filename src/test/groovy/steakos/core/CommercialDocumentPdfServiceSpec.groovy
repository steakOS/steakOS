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

import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.configuration.LocaleService
import steakos.configuration.PartnerLocaleService
import steakos.sales.Customer
import steakos.sales.CustomerQuote
import steakos.sales.QuoteLine
import steakos.sales.SalesCustomerService
import steakos.sales.SalesOrder
import steakos.sales.OrderLine
import steakos.sales.SalesOrderService
import steakos.sales.SalesQuoteService

/**
 * A8 / OI-BR05 D9 — golden multi-line quote PDF via services; totals vs source map.
 * OI-I18N02 — DE pack labels when partner communicationLocale is de_DE.
 */
class CommercialDocumentPdfServiceSpec extends Specification implements DataTest {

    SalesCustomerService customerService
    SalesQuoteService quoteService
    CommercialDocumentPdfService pdfService
    PartnerLocaleService partnerLocaleService

    Class[] getDomainClassesToMock() {
        [Customer, CustomerQuote, QuoteLine, SalesOrder, OrderLine] as Class[]
    }

    def setup() {
        partnerLocaleService = new PartnerLocaleService(localeService: new LocaleService())
        customerService = new SalesCustomerService(partnerLocaleService: partnerLocaleService)
        quoteService = new SalesQuoteService(
                salesCustomerService: customerService,
                salesOrderService: new SalesOrderService(salesCustomerService: customerService),
                partnerLocaleService: partnerLocaleService
        )
        pdfService = new CommercialDocumentPdfService(
                salesQuoteService: quoteService,
                partnerLocaleService: partnerLocaleService,
                tradeDocumentCompanyService: Stub(TradeDocumentCompanyService) {
                    loadSeller(_) >> [
                            name         : 'Bronowski Properties GmbH',
                            legalForm    : 'GMBH',
                            taxId        : 'DE123456789',
                            countryCode  : 'DE',
                            currencyCode : 'EUR',
                            addressLines : ['Musterstraße 1', '10115 Berlin'],
                            bankLines    : ['IBAN DE89 3704 0044 0532 0130 00']
                    ]
                }
        )
    }

    def "golden multi-line quote PDF reflects lines and totals vs API map"() {
        given:
        Customer c = customerService.createCustomer('npub-a8', [
                name       : 'Orange Steak House',
                contactName: 'Chef Ada',
                email      : 'ada@example.com'
        ])
        String markerA = 'A8-RIBEYE-GOLDEN'
        String markerB = 'A8-SIDES-GOLDEN'
        String markerC = 'A8-SERVICE-GOLDEN'
        CustomerQuote q = quoteService.createQuote('npub-a8', [
                customerId  : c.id,
                name        : 'Friday catering quote',
                currencyCode: 'EUR',
                lines       : [
                        [name: markerA, description: 'Dry-aged ribeye with pepper crust', quantity: 2, unitPriceEur: 42.50G],
                        [name: markerB, description: 'Seasonal sides platter for twelve', quantity: 3, unitPriceEur: 18.00G],
                        [name: markerC, description: 'On-site plating and service window', quantity: 1, unitPriceEur: 120.00G]
                ]
        ])
        Map api = quoteService.toMap(q, true)
        String expectedTotal = Iso6422TradeDocumentPdfBuilder.formatMoney(api.amountEur as BigDecimal)

        when:
        byte[] pdf = pdfService.renderQuotePdf(q.id, 'npub-a8')
        String meta = new String(pdf, 'ISO-8859-1')
        String text = extractPdfText(pdf)

        then:
        api.lines.size() == 3
        api.amountEur == 259.00G
        pdf != null
        pdf.length > 800
        meta.startsWith('%PDF-')
        meta.contains('MediaBox[0 0 595 842]') || meta.contains('/MediaBox[0 0 595')
        text.contains(markerA)
        text.contains(markerB)
        text.contains(markerC)
        text.contains(expectedTotal)
    }

    def "DE pack labels when partner communicationLocale is de_DE"() {
        given:
        Customer c = customerService.createCustomer('npub-i18n', [
                name                : 'Kunde DE',
                communicationLocale : 'de_DE',
                currencyCode        : 'EUR'
        ])
        CustomerQuote q = quoteService.createQuote('npub-i18n', [
                customerId  : c.id,
                name        : 'Angebotstest',
                currencyCode: 'EUR',
                lines       : [[name: 'Pos-A', quantity: 1, unitPriceEur: 10.00G]]
        ])

        when:
        byte[] pdf = pdfService.renderQuotePdf(q.id, 'npub-i18n')
        String text = extractPdfText(pdf)

        then:
        text.contains('ANGEBOT')
        text.contains('Warenbeschreibung') || text.contains('Empfänger')
    }

    private static String extractPdfText(byte[] pdf) {
        PdfReader reader = new PdfReader(pdf)
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader)
            StringBuilder sb = new StringBuilder()
            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                sb.append(extractor.getTextFromPage(p)).append('\n')
            }
            return sb.toString()
        } finally {
            reader.close()
        }
    }
}
