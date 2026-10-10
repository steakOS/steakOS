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

class SupportedLocaleSpec extends Specification {

    def "underscore code converts to BCP-47 and Java Locale"() {
        given:
        def loc = new SupportedLocale(code: 'de_CH', languageCode: 'de', countryCode: 'CH', currencyCode: 'CHF')

        expect:
        loc.toLanguageTag() == 'de-CH'
        loc.toJavaLocale().language == 'de'
        loc.toJavaLocale().country == 'CH'
    }

    def "catalog lists five official codes"() {
        expect:
        SupportedLocaleCodes.ALL.size() == 5
        SupportedLocaleCodes.isValid('en_SG')
        !SupportedLocaleCodes.isValid('en-SG')
        !SupportedLocaleCodes.isValid('fr_FR')
    }
}
