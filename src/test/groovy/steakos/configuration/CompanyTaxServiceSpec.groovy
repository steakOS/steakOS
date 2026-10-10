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

package steakos.configuration

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.finance.TaxCatalogCodes
import steakos.finance.TaxCode
import steakos.sales.Customer
import steakos.sales.CustomerQuote
import steakos.sales.OrderLine
import steakos.sales.QuoteLine
import steakos.sales.SalesCustomerService
import steakos.sales.SalesOrder
import steakos.sales.SalesOrderService
import steakos.sales.SalesQuoteService

/**
 * OI-F23 P1 — DE 19% line calc + reverse-charge wording.
 */
class CompanyTaxServiceSpec extends Specification implements DataTest {

    CompanyTaxService taxService
    CompanyConfigService configService
    SalesQuoteService quoteService

    Class[] getDomainClassesToMock() {
        [TaxCode, CompanyTaxCode, CompanySetting, LegalEntity,
         Customer, CustomerQuote, QuoteLine, SalesOrder, OrderLine] as Class[]
    }

    def setup() {
        configService = Stub(CompanyConfigService) {
            getString(_, _, _) >> { String npub, String key, String defVal ->
                stored.get("${npub}|${key}".toString()) ?: defVal
            }
            put(_, _, _, _, _) >> { String npub, String key, String value, String cat, String dt ->
                stored.put("${npub}|${key}".toString(), value)
                Stub(CompanySetting)
            }
        }
        taxService = new CompanyTaxService(companyConfigService: configService)
        seedCatalog()
        SalesCustomerService customers = new SalesCustomerService()
        quoteService = new SalesQuoteService(
                salesCustomerService: customers,
                salesOrderService: new SalesOrderService(salesCustomerService: customers),
                companyTaxService: taxService
        )
    }

    private final Map<String, String> stored = [:]

    private void seedCatalog() {
        TaxCatalogCodes.ALL.each { Map row ->
            if (!TaxCode.findByCode(row.code as String)) {
                new TaxCode(
                        code           : row.code as String,
                        jurisdiction   : row.jurisdiction as String,
                        name           : row.name as String,
                        description    : row.description as String,
                        ratePercent    : row.ratePercent as BigDecimal,
                        taxKind        : row.taxKind as String,
                        reverseCharge  : row.reverseCharge as Boolean ?: Boolean.FALSE,
                        intraCommunity : row.intraCommunity as Boolean ?: Boolean.FALSE,
                        enabled        : true
                ).save(failOnError: true)
            }
        }
    }

    def "computeTaxFromNet DE 19% half-up"() {
        expect:
        CompanyTaxService.computeTaxFromNet(100.00G, 19.00G) == 19.00G
        CompanyTaxService.computeTaxFromNet(10.00G, 19.00G) == 1.90G
        CompanyTaxService.computeTaxFromNet(1.00G, 19.00G) == 0.19G
        CompanyTaxService.computeTaxFromNet(100.00G, 0.00G) == 0.00G
    }

    def "regimeForCountry maps DE AT US"() {
        expect:
        CompanyTaxService.regimeForCountry('DE') == CompanyTaxService.REGIME_DE_VAT
        CompanyTaxService.regimeForCountry('AT') == CompanyTaxService.REGIME_AT_VAT
        CompanyTaxService.regimeForCountry('US') == CompanyTaxService.REGIME_US_SALES
        CompanyTaxService.regimeForCountry('CH') == CompanyTaxService.REGIME_NONE
        CompanyTaxService.suppressesOutputVat(CompanyTaxService.REGIME_DE_SMALL)
        CompanyTaxService.suppressesOutputVat(CompanyTaxService.REGIME_NONE)
        !CompanyTaxService.suppressesOutputVat(CompanyTaxService.REGIME_DE_VAT)
    }

    def "DE 19% line tax on quote nets to gross"() {
        given:
        taxService.applyRegimeSettings('npub-de', CompanyTaxService.REGIME_DE_VAT)
        Customer c = new SalesCustomerService().createCustomer('npub-de', [name: 'DE Buyer'])

        when:
        CustomerQuote q = quoteService.createQuote('npub-de', [
                customerId  : c.id,
                name        : 'VAT quote',
                currencyCode: 'EUR',
                lines       : [
                        [name: 'Consulting', quantity: 1, unitPriceEur: 100.00G, taxCode: 'DE_USt_STD']
                ]
        ])
        Map m = quoteService.toMap(q, true)

        then:
        m.lines[0].taxCode == 'DE_USt_STD'
        m.lines[0].taxRatePercent == 19.00G
        m.lines[0].taxAmountEur == 19.00G
        m.lines[0].lineTotalEur == 100.00G
        m.lines[0].grossAmountEur == 119.00G
        m.netAmountEur == 100.00G
        m.taxAmountEur == 19.00G
        m.amountEur == 119.00G
    }

    def "reverse charge zero output tax and PDF wording flag"() {
        given:
        taxService.applyRegimeSettings('npub-rc', CompanyTaxService.REGIME_DE_VAT)
        Customer c = new SalesCustomerService().createCustomer('npub-rc', [name: 'EU B2B'])

        when:
        CustomerQuote q = quoteService.createQuote('npub-rc', [
                customerId: c.id,
                lines     : [
                        [name: 'Cross-border service', quantity: 1, unitPriceEur: 500.00G, taxCode: 'DE_RC']
                ]
        ])
        Map m = quoteService.toMap(q, true)
        List phrases = CompanyTaxService.legalPhrasesForLines(m.lines as List)

        then:
        m.lines[0].taxCode == 'DE_RC'
        m.lines[0].taxAmountEur == 0.00G
        m.lines[0].reverseCharge == true
        m.taxAmountEur == 0.00G
        m.amountEur == 500.00G
        phrases.any { it.toString().toLowerCase().contains('reverse charge') }
        phrases.any { it.toString().contains('Steuerschuldnerschaft') }
        (m.taxNotes as List).any { it.toString().toLowerCase().contains('reverse charge') }
    }

    def "Kleinunternehmer / NONE suppresses VAT on lines"() {
        given:
        taxService.applyRegimeSettings('npub-ku', CompanyTaxService.REGIME_DE_SMALL)
        Customer c = new SalesCustomerService().createCustomer('npub-ku', [name: 'Small'])

        when:
        CustomerQuote q = quoteService.createQuote('npub-ku', [
                customerId: c.id,
                lines     : [[name: 'Work', quantity: 1, unitPriceEur: 100.00G, taxCode: 'DE_USt_STD']]
        ])
        Map m = quoteService.toMap(q, true)

        then:
        m.lines[0].taxCode == null
        m.lines[0].taxAmountEur == 0G || m.lines[0].taxAmountEur == 0.00G
        m.amountEur == 100.00G
    }

    def "ensureInUse and listInUse for DE pack"() {
        when:
        taxService.applyRegimeSettings('npub-pack', CompanyTaxService.REGIME_DE_VAT)
        List<Map> list = taxService.listInUse('npub-pack')

        then:
        list*.code.contains('DE_USt_STD')
        list*.code.contains('DE_RC')
        taxService.defaultTaxCode('npub-pack') == 'DE_USt_STD'
    }
}
