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

/**
 * Official supported UI locales (L4-A). Underscore codes (Grails/Java).
 * Single source for locale → country → currency alignment; Countries/Currencies seed from here.
 */
class SupportedLocaleCodes {

    /**
     * Rows: code, name, languageCode, countryCode, currencyCode
     */
    static final List<Map<String, String>> ALL = [
            [code: 'de_DE', name: 'German (Germany)', languageCode: 'de', countryCode: 'DE', currencyCode: 'EUR'],
            [code: 'de_CH', name: 'German (Switzerland)', languageCode: 'de', countryCode: 'CH', currencyCode: 'CHF'],
            [code: 'de_AT', name: 'German (Austria)', languageCode: 'de', countryCode: 'AT', currencyCode: 'EUR'],
            [code: 'en_US', name: 'English (United States)', languageCode: 'en', countryCode: 'US', currencyCode: 'USD'],
            [code: 'en_SG', name: 'English (Singapore)', languageCode: 'en', countryCode: 'SG', currencyCode: 'SGD'],
            [code: 'es_ES', name: 'Spanish (Spain)', languageCode: 'es', countryCode: 'ES', currencyCode: 'EUR'],
    ].asImmutable()

    /** ISO 3166-1 alpha-2 → English display name (seed / UI). */
    static final Map<String, String> COUNTRY_NAMES = [
            DE: 'Germany',
            CH: 'Switzerland',
            AT: 'Austria',
            US: 'United States',
            SG: 'Singapore',
            ES: 'Spain',
    ].asImmutable()

    /** ISO 4217 → English display name (seed / UI). */
    static final Map<String, String> CURRENCY_NAMES = [
            EUR: 'Euro',
            CHF: 'Swiss Franc',
            USD: 'US Dollar',
            SGD: 'Singapore Dollar',
    ].asImmutable()

    static boolean isValid(String code) {
        code != null && ALL.any { it.code == code }
    }

    /**
     * Unique countries implied by supported locales (order of first appearance).
     * Each map: code, name
     */
    static List<Map<String, String>> uniqueCountries() {
        List<Map<String, String>> out = []
        Set<String> seen = [] as Set
        ALL.each { Map<String, String> row ->
            String c = row.countryCode
            if (c && !seen.contains(c)) {
                seen << c
                out << [code: c, name: COUNTRY_NAMES[c] ?: c]
            }
        }
        return out.asImmutable()
    }

    /**
     * Unique currencies implied by supported locales (order of first appearance).
     * Each map: code, name
     */
    static List<Map<String, String>> uniqueCurrencies() {
        List<Map<String, String>> out = []
        Set<String> seen = [] as Set
        ALL.each { Map<String, String> row ->
            String c = row.currencyCode
            if (c && !seen.contains(c)) {
                seen << c
                out << [code: c, name: CURRENCY_NAMES[c] ?: c]
            }
        }
        return out.asImmutable()
    }
}
