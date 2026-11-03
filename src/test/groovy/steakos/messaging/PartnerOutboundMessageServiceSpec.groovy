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

package steakos.messaging

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.configuration.LocaleService
import steakos.configuration.PartnerLocaleService
import steakos.events.DomainEventNames
import steakos.sales.Customer

/**
 * OI-I18N02 PR4a / PL-TR3 — partner locale selection for outbound email compose.
 */
class PartnerOutboundMessageServiceSpec extends Specification implements DataTest {

    PartnerOutboundMessageService service

    Class[] getDomainClassesToMock() {
        [Customer] as Class[]
    }

    def setup() {
        service = new PartnerOutboundMessageService(
                partnerLocaleService: new PartnerLocaleService(localeService: new LocaleService())
        )
    }

    def "no partner id returns empty compose"() {
        expect:
        service.composeForEvent(DomainEventNames.FINANCE_INVOICE_OVERDUE, [invoiceNumber: 'X']) == [:]
    }

    def "DE customer yields German overdue subject and partner email as to"() {
        given:
        Customer c = new Customer(
                npubOwner: 'npub-msg',
                name: 'Partner GmbH',
                email: 'billing@partner.de',
                communicationLocale: 'de_DE',
                currencyCode: 'EUR'
        ).save(failOnError: true, flush: true)

        when:
        Map out = service.composeForEvent(DomainEventNames.FINANCE_INVOICE_OVERDUE, [
                customerId    : c.id,
                invoiceNumber : 'INV-DE-1',
                amountOpenEur : 120.5G,
                currencyCode  : 'EUR',
                dueDate       : Date.parse('yyyy-MM-dd', '2026-01-15'),
                customerName  : c.name
        ])

        then:
        out.locale == 'de_DE'
        out.to == 'billing@partner.de'
        out.email == 'billing@partner.de'
        out.subject == 'Rechnung INV-DE-1 ist überfällig'
        out.text.contains('Offener Betrag')
        out.text.contains('120.5 EUR')
        out.text.contains('Partner GmbH')
    }

    def "EN fallback when communicationLocale blank"() {
        given:
        Customer c = new Customer(
                npubOwner: 'npub-msg',
                name: 'Acme',
                email: 'ar@acme.example',
                communicationLocale: null,
                currencyCode: 'EUR'
        ).save(failOnError: true, flush: true)

        when:
        Map out = service.composeForEvent(DomainEventNames.FINANCE_INVOICE_OVERDUE, [
                customerId   : c.id,
                invoiceNumber: 'INV-EN-2',
                amountOpenEur: 10G
        ])

        then:
        out.locale == 'en_US'
        out.subject == 'Invoice INV-EN-2 is overdue'
        out.to == 'ar@acme.example'
    }

    def "explicit subject and to are not overwritten"() {
        given:
        Customer c = new Customer(
                npubOwner: 'npub-msg',
                name: 'DE Co',
                email: 'de@example.com',
                communicationLocale: 'de_DE',
                currencyCode: 'EUR'
        ).save(failOnError: true, flush: true)

        when:
        Map out = service.composeForEvent(DomainEventNames.FINANCE_INVOICE_OVERDUE, [
                customerId: c.id,
                subject   : 'Custom subject',
                text      : 'Custom body',
                to        : 'ops@tenant.example'
        ])

        then:
        out.to == 'ops@tenant.example'
        !out.containsKey('subject')
        !out.containsKey('text')
        out.locale == 'de_DE'
    }

    def "generic event uses event template keys"() {
        given:
        Customer c = new Customer(
                npubOwner: 'npub-msg',
                name: 'DE Co',
                email: 'de@example.com',
                communicationLocale: 'de_DE',
                currencyCode: 'EUR'
        ).save(failOnError: true, flush: true)

        when:
        Map out = service.composeForEvent(DomainEventNames.FINANCE_INVOICE_ISSUED, [
                customerId: c.id,
                invoiceId : 99
        ])

        then:
        out.subject == 'steakOS: finance.invoice.issued'
        out.text.contains('Benachrichtigung')
        out.text.contains('invoiceId=99')
    }
}
