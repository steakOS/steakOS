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
import steakos.helper.Currencies
import steakos.helper.CurrencyCatalogCodes

class CompanyCurrencyServiceSpec extends Specification implements DataTest {

    CompanyCurrencyService service

    Class[] getDomainClassesToMock() {
        [Currencies, CompanyCurrency, LegalEntity] as Class[]
    }

    def setup() {
        service = new CompanyCurrencyService()
        service.tenantMoneyService = Stub(TenantMoneyService) {
            prefsFor(_) >> [primaryValueRail: 'EUR', functionalCurrency: 'EUR', displayFiat: null]
        }
        CurrencyCatalogCodes.ALL.take(5).each { Map row ->
            new Currencies(
                    code: row.code as String,
                    name: row.name as String,
                    description: row.description as String,
                    decimals: (row.decimals as Integer) ?: 2,
                    enabled: true
            ).save(failOnError: true)
        }
        if (!Currencies.findByCode('USD')) {
            new Currencies(code: 'USD', name: 'US Dollar', description: 'US Dollar', decimals: 2, enabled: true)
                    .save(failOnError: true)
        }
    }

    def "ensureInUse creates row and listInUse returns catalog maps"() {
        when:
        service.ensureInUse('npub1a', 'EUR')
        service.ensureInUse('npub1a', 'USD')
        List<Map> list = service.listInUse('npub1a')

        then:
        list*.code as Set == ['EUR', 'USD'] as Set
        list.find { it.code == 'EUR' }.label == 'EUR — Euro'
    }

    def "validateWritableCode blocks unknown and not-in-use"() {
        given:
        service.ensureInUse('npub1b', 'EUR')

        when:
        service.validateWritableCode('npub1b', 'EUR')

        then:
        noExceptionThrown()

        when:
        service.validateWritableCode('npub1b', 'USD')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('CURRENCY_NOT_IN_USE')

        when:
        service.validateWritableCode('npub1b', 'ZZZ')

        then:
        def ex2 = thrown(IllegalArgumentException)
        ex2.message.contains('CURRENCY_UNKNOWN')
    }

    def "setInUse disables codes not in the new set but keeps company currency"() {
        given:
        service.ensureInUse('npub1c', 'EUR')
        service.ensureInUse('npub1c', 'USD')
        service.ensureInUse('npub1c', 'CHF')

        when:
        List<Map> list = service.setInUse('npub1c', ['USD'])

        then:
        // company currency EUR always kept
        list*.code as Set == ['EUR', 'USD'] as Set
        CompanyCurrency.findByNpubOwnerAndCurrencyCode('npub1c', 'CHF').inUse == false
    }

    def "companyCurrencyCode uses displayFiat when rail is BTC"() {
        given:
        service.tenantMoneyService = Stub(TenantMoneyService) {
            prefsFor('npub1btc') >> [primaryValueRail: 'BTC', functionalCurrency: 'BTC', displayFiat: 'USD']
        }

        expect:
        service.companyCurrencyCode('npub1btc') == 'USD'
        service.companyCurrencyCode('npub1a') == 'EUR'
    }

    def "collectDocumentWarnings is soft-only and covers company partner and line"() {
        given:
        service.ensureInUse('npub1w', 'EUR')
        service.ensureInUse('npub1w', 'USD')

        when:
        List<Map> w = service.collectDocumentWarnings(
                'npub1w', 'USD', 'EUR', 'Customer',
                [[name: 'Widget', currency: 'EUR'], [name: 'Widget', currency: 'EUR']])

        then:
        w*.code.contains('CURRENCY_DOC_NE_COMPANY')
        w*.code.contains('CURRENCY_DOC_NE_PARTNER')
        w*.code.contains('CURRENCY_LINE_NE_DOC')
        // duplicate line item collapsed
        w.count { it.code == 'CURRENCY_LINE_NE_DOC' } == 1
    }

    def "normalizeCode rejects bitcoin aliases"() {
        expect:
        CompanyCurrencyService.normalizeCode('btc') == null
        CompanyCurrencyService.normalizeCode('SATS') == null
        CompanyCurrencyService.normalizeCode('eur') == 'EUR'
    }
}
