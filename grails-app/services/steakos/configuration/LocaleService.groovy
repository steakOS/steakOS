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

import grails.gorm.transactions.Transactional
import steakos.helper.SupportedLocaleCodes

/**
 * Locale catalog + user preference (i18n proof slice EN/DE).
 */
@Transactional
class LocaleService {

    static final String PREF_KEY = 'ui.locale'
    static final String DEFAULT_LOCALE = 'en_US'

    List<Map> listLocales() {
        SupportedLocaleCodes.ALL.collect { Map row ->
            [
                    code        : row.code,
                    name        : row.name,
                    languageCode: row.languageCode,
                    countryCode : row.countryCode,
                    currencyCode: row.currencyCode,
            ]
        }
    }

    String normalize(String code) {
        if (!code?.trim()) return null
        String c = code.trim().replace('-', '_')
        // en -> en_US, de -> de_DE
        if (c == 'en') return 'en_US'
        if (c == 'de') return 'de_DE'
        if (SupportedLocaleCodes.isValid(c)) return c
        return null
    }

    String languageOf(String localeCode) {
        String n = normalize(localeCode) ?: DEFAULT_LOCALE
        Map row = SupportedLocaleCodes.ALL.find { it.code == n }
        (row?.languageCode ?: 'en') as String
    }

    String matchBrowser(String acceptLanguage) {
        if (!acceptLanguage) return DEFAULT_LOCALE
        String primary = acceptLanguage.split(',')[0]?.trim()?.toLowerCase()
        if (!primary) return DEFAULT_LOCALE
        String norm = primary.replace('-', '_')
        if (SupportedLocaleCodes.isValid(norm)) return norm
        String lang = norm.split('_')[0]
        Map hit = SupportedLocaleCodes.ALL.find { it.languageCode == lang }
        hit?.code ?: DEFAULT_LOCALE
    }

    String getUserLocale(String username) {
        if (!username) return null
        UserPreference p = UserPreference.findByUsernameAndPrefKey(username, PREF_KEY)
        normalize(p?.prefValue)
    }

    UserPreference setUserLocale(String username, String localeCode) {
        String n = normalize(localeCode)
        if (!n) throw new IllegalArgumentException("Invalid locale: ${localeCode}")
        UserPreference p = UserPreference.findByUsernameAndPrefKey(username, PREF_KEY)
        if (!p) {
            p = new UserPreference(username: username, prefKey: PREF_KEY, category: 'ui')
        }
        p.prefValue = n
        p.category = 'ui'
        p.lastUpdatedBy = username
        p.save(failOnError: true)
        p
    }

    Map resolveForUser(String username, String acceptLanguage, String clientHint) {
        String fromUser = getUserLocale(username)
        String fromClient = normalize(clientHint)
        String fromBrowser = matchBrowser(acceptLanguage)
        String code = fromUser ?: fromClient ?: fromBrowser ?: DEFAULT_LOCALE
        Map row = SupportedLocaleCodes.ALL.find { it.code == code } ?: SupportedLocaleCodes.ALL.find { it.code == DEFAULT_LOCALE }
        [
                locale      : code,
                languageCode: row?.languageCode ?: 'en',
                countryCode : row?.countryCode,
                currencyCode: row?.currencyCode,
                name        : row?.name,
                source      : fromUser ? 'user' : (fromClient ? 'client' : (fromBrowser ? 'browser' : 'default'))
        ]
    }
}
