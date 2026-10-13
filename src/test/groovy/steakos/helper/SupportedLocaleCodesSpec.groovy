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

package steakos.helper

import spock.lang.Specification

class SupportedLocaleCodesSpec extends Specification {

    def "unique countries derived from locales"() {
        when:
        def countries = SupportedLocaleCodes.uniqueCountries()

        then:
        countries*.code as Set == ['DE', 'CH', 'AT', 'US', 'SG', 'ES'] as Set
        countries.find { it.code == 'DE' }.name == 'Germany'
        countries.find { it.code == 'ES' }.name == 'Spain'
    }

    def "unique currencies collapse shared EUR"() {
        when:
        def currencies = SupportedLocaleCodes.uniqueCurrencies()

        then:
        currencies*.code as Set == ['EUR', 'CHF', 'USD', 'SGD'] as Set
        currencies.find { it.code == 'EUR' }.name == 'Euro'
        // DE + AT share EUR → only one entry
        currencies.count { it.code == 'EUR' } == 1
    }
}
