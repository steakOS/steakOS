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
 * Common ISO 4217 fiat seed for system {@link Currencies} catalog (OI-CUR01 D3–D4, D9).
 * No BTC / XBT / SATS — Bitcoin stays on the PDR-023 value rail only.
 */
class CurrencyCatalogCodes {

    /**
     * Rows: code, name, description, decimals
     */
    static final List<Map> ALL = [
            [code: 'EUR', name: 'Euro', description: 'Euro — European Union', decimals: 2],
            [code: 'USD', name: 'US Dollar', description: 'US Dollar — United States', decimals: 2],
            [code: 'CHF', name: 'Swiss Franc', description: 'Swiss Franc — Switzerland', decimals: 2],
            [code: 'GBP', name: 'Pound Sterling', description: 'Pound Sterling — United Kingdom', decimals: 2],
            [code: 'SGD', name: 'Singapore Dollar', description: 'Singapore Dollar — Singapore', decimals: 2],
            [code: 'JPY', name: 'Japanese Yen', description: 'Japanese Yen — Japan', decimals: 0],
            [code: 'CAD', name: 'Canadian Dollar', description: 'Canadian Dollar — Canada', decimals: 2],
            [code: 'AUD', name: 'Australian Dollar', description: 'Australian Dollar — Australia', decimals: 2],
            [code: 'NZD', name: 'New Zealand Dollar', description: 'New Zealand Dollar — New Zealand', decimals: 2],
            [code: 'SEK', name: 'Swedish Krona', description: 'Swedish Krona — Sweden', decimals: 2],
            [code: 'NOK', name: 'Norwegian Krone', description: 'Norwegian Krone — Norway', decimals: 2],
            [code: 'DKK', name: 'Danish Krone', description: 'Danish Krone — Denmark', decimals: 2],
            [code: 'PLN', name: 'Polish Zloty', description: 'Polish Zloty — Poland', decimals: 2],
            [code: 'CZK', name: 'Czech Koruna', description: 'Czech Koruna — Czechia', decimals: 2],
            [code: 'HUF', name: 'Hungarian Forint', description: 'Hungarian Forint — Hungary', decimals: 2],
            [code: 'RON', name: 'Romanian Leu', description: 'Romanian Leu — Romania', decimals: 2],
            [code: 'BGN', name: 'Bulgarian Lev', description: 'Bulgarian Lev — Bulgaria', decimals: 2],
            [code: 'TRY', name: 'Turkish Lira', description: 'Turkish Lira — Türkiye', decimals: 2],
            [code: 'INR', name: 'Indian Rupee', description: 'Indian Rupee — India', decimals: 2],
            [code: 'CNY', name: 'Chinese Yuan', description: 'Chinese Yuan — China', decimals: 2],
            [code: 'HKD', name: 'Hong Kong Dollar', description: 'Hong Kong Dollar — Hong Kong', decimals: 2],
            [code: 'KRW', name: 'South Korean Won', description: 'South Korean Won — South Korea', decimals: 0],
            [code: 'MXN', name: 'Mexican Peso', description: 'Mexican Peso — Mexico', decimals: 2],
            [code: 'BRL', name: 'Brazilian Real', description: 'Brazilian Real — Brazil', decimals: 2],
            [code: 'ZAR', name: 'South African Rand', description: 'South African Rand — South Africa', decimals: 2],
            [code: 'AED', name: 'UAE Dirham', description: 'UAE Dirham — United Arab Emirates', decimals: 2],
            [code: 'SAR', name: 'Saudi Riyal', description: 'Saudi Riyal — Saudi Arabia', decimals: 2],
            [code: 'ILS', name: 'Israeli New Shekel', description: 'Israeli New Shekel — Israel', decimals: 2],
            [code: 'THB', name: 'Thai Baht', description: 'Thai Baht — Thailand', decimals: 2],
            [code: 'MYR', name: 'Malaysian Ringgit', description: 'Malaysian Ringgit — Malaysia', decimals: 2],
            [code: 'IDR', name: 'Indonesian Rupiah', description: 'Indonesian Rupiah — Indonesia', decimals: 2],
            [code: 'PHP', name: 'Philippine Peso', description: 'Philippine Peso — Philippines', decimals: 2],
            [code: 'VND', name: 'Vietnamese Dong', description: 'Vietnamese Dong — Vietnam', decimals: 0],
    ].asImmutable()

    static boolean isValid(String code) {
        code != null && ALL.any { (it.code as String) == code }
    }

    static Map rowFor(String code) {
        String c = code?.trim()?.toUpperCase()
        ALL.find { (it.code as String) == c }
    }
}
