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
import steakos.core.PdfMessageBundle
import steakos.sales.Customer
import steakos.sales.CustomerQuote

/**
 * OI-I18N02 — resolve fallback, DE pack, snapshot freeze.
 */
class PartnerLocaleServiceSpec extends Specification implements DataTest {

    PartnerLocaleService service

    Class[] getDomainClassesToMock() {
        [Customer, CustomerQuote] as Class[]
    }

    def setup() {
        service = new PartnerLocaleService(localeService: new LocaleService())
    }

    def "resolve falls back to en_US when partner locale blank"() {
        expect:
        service.resolveCustomer(null) == PartnerLocaleService.FALLBACK
        service.resolveCustomer(new Customer(name: 'X', communicationLocale: null)) == 'en_US'
        service.resolveCustomer(new Customer(name: 'Y', communicationLocale: '')) == 'en_US'
        service.resolve(null) == 'en_US'
        service.resolve('') == 'en_US'
    }

    def "resolve uses partner de_DE"() {
        expect:
        service.resolveCustomer(new Customer(name: 'DE', communicationLocale: 'de_DE')) == 'de_DE'
        service.packLanguage('de_DE') == 'de'
        service.packLanguage('de_CH') == 'de'
        service.packLanguage('en_SG') == 'en'
    }

    def "DE PDF pack used when partner de_DE"() {
        when:
        PdfMessageBundle de = PdfMessageBundle.forLocale('de_DE')
        PdfMessageBundle en = PdfMessageBundle.forLocale('en_US')

        then:
        de.get('pdf.doc.QUOTE') == 'ANGEBOT'
        de.get('pdf.total.net') == 'Netto'
        en.get('pdf.doc.QUOTE') == 'QUOTE'
        en.get('pdf.total.net') == 'Net'
    }

    def "documentLocale snapshot freezes language after issue"() {
        given:
        Customer c = new Customer(
                npubOwner: 'npub-i18n',
                name: 'Partner GmbH',
                communicationLocale: 'de_DE',
                currencyCode: 'EUR'
        ).save(failOnError: true, flush: true)
        CustomerQuote q = new CustomerQuote(
                npubOwner: 'npub-i18n',
                name: 'Q1',
                quoteNumber: 'Q-I18N-1',
                customer: c,
                customerName: c.name,
                currencyCode: 'EUR',
                quoteStatus: 'DRAFT',
                amountEur: 10.0G
        ).save(failOnError: true, flush: true)

        expect:
        service.resolveForDocument(q) == 'de_DE'

        when:
        service.snapshotQuote(q)
        q.save(failOnError: true, flush: true)
        c.communicationLocale = 'en_US'
        c.save(failOnError: true, flush: true)

        then:
        q.documentLocale == 'de_DE'
        service.resolveForDocument(q) == 'de_DE'
    }
}
