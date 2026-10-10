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

import spock.lang.Specification

class TenantMoneyServiceSpec extends Specification {

    def "normalizeDraft maps EUR rail"() {
        when:
        def m = TenantMoneyService.normalizeDraft([primaryValueRail: 'EUR'])

        then:
        m.primaryValueRail == 'EUR'
        m.functionalCurrency == 'EUR'
        m.bitcoinDisplayUnit == null
        m.displayFiat == null
        m.dualSats == false
    }

    def "normalizeDraft BTC requires unit and display fiat defaults"() {
        when:
        def m = TenantMoneyService.normalizeDraft([primaryValueRail: 'btc', bitcoinDisplayUnit: 'sats'])

        then:
        m.primaryValueRail == 'BTC'
        m.functionalCurrency == 'BTC'
        m.bitcoinDisplayUnit == 'SATS'
        m.displayFiat == 'EUR'
    }

    def "legacy functionalCurrency USD becomes rail"() {
        when:
        def m = TenantMoneyService.normalizeDraft([functionalCurrency: 'USD'])

        then:
        m.primaryValueRail == 'USD'
        m.functionalCurrency == 'USD'
    }

    def "defaultRailForLocale US"() {
        expect:
        TenantMoneyService.defaultRailForLocale('en-US') == 'USD'
        TenantMoneyService.defaultRailForLocale('en_US') == 'USD'
        TenantMoneyService.defaultRailForLocale('de-DE') == 'EUR'
        TenantMoneyService.defaultRailForLocale(null) == 'EUR'
    }

    def "resolveDocumentPrimaryAmount converts sats when BTC+SATS"() {
        when:
        def m = TenantMoneyService.resolveDocumentPrimaryAmountFromPrefs(
                [primaryValueRail: 'BTC', bitcoinDisplayUnit: 'SATS'],
                [amount: '100000000']
        )

        then:
        m.currencyCode == 'BTC'
        m.amountFunctional == 1.0G
    }
}
