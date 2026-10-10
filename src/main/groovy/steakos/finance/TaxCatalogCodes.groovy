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

package steakos.finance

/**
 * System tax-code seed packs (OI-F23 D1, D14).
 * Rates are starting points — operator owns correctness (not legal advice).
 */
class TaxCatalogCodes {

    static final String KIND_VAT = 'VAT'
    static final String KIND_SALES_TAX = 'SALES_TAX'
    static final String KIND_REVERSE_CHARGE = 'REVERSE_CHARGE'
    static final String KIND_ZERO = 'ZERO'
    static final String KIND_EXEMPT = 'EXEMPT'

    /**
     * Rows: code, jurisdiction, name, description, ratePercent, taxKind, reverseCharge, intraCommunity
     */
    static final List<Map> ALL = [
            // --- Germany ---
            [code: 'DE_USt_STD', jurisdiction: 'DE', name: 'DE VAT standard 19%',
             description: 'Germany Umsatzsteuer standard rate (operator-owned).',
             ratePercent: 19.00G, taxKind: KIND_VAT, reverseCharge: false, intraCommunity: false],
            [code: 'DE_USt_RED', jurisdiction: 'DE', name: 'DE VAT reduced 7%',
             description: 'Germany reduced rate (eligibility operator-chosen).',
             ratePercent: 7.00G, taxKind: KIND_VAT, reverseCharge: false, intraCommunity: false],
            [code: 'DE_USt_0', jurisdiction: 'DE', name: 'DE VAT zero / exempt',
             description: 'Explicit zero-rated or exempt DE line (still shown).',
             ratePercent: 0.00G, taxKind: KIND_EXEMPT, reverseCharge: false, intraCommunity: false],
            [code: 'DE_RC', jurisdiction: 'DE', name: 'DE reverse charge',
             description: 'B2B reverse charge — net invoice; buyer accounts for VAT.',
             ratePercent: 0.00G, taxKind: KIND_REVERSE_CHARGE, reverseCharge: true, intraCommunity: false],
            [code: 'DE_IG', jurisdiction: 'DE', name: 'DE intra-community supply',
             description: 'Intra-community B2B supply (0% + IG flag).',
             ratePercent: 0.00G, taxKind: KIND_ZERO, reverseCharge: false, intraCommunity: true],
            [code: 'DE_EXPORT', jurisdiction: 'DE', name: 'DE export outside EU',
             description: 'Export outside EU (documentary 0%).',
             ratePercent: 0.00G, taxKind: KIND_ZERO, reverseCharge: false, intraCommunity: false],

            // --- Austria ---
            [code: 'AT_USt_STD', jurisdiction: 'AT', name: 'AT VAT standard 20%',
             description: 'Austria Umsatzsteuer standard rate (operator-owned).',
             ratePercent: 20.00G, taxKind: KIND_VAT, reverseCharge: false, intraCommunity: false],
            [code: 'AT_USt_RED', jurisdiction: 'AT', name: 'AT VAT reduced 10%',
             description: 'Austria reduced rate 10%.',
             ratePercent: 10.00G, taxKind: KIND_VAT, reverseCharge: false, intraCommunity: false],
            [code: 'AT_USt_RED2', jurisdiction: 'AT', name: 'AT VAT intermediate 13%',
             description: 'Austria intermediate reduced rate 13%.',
             ratePercent: 13.00G, taxKind: KIND_VAT, reverseCharge: false, intraCommunity: false],
            [code: 'AT_USt_0', jurisdiction: 'AT', name: 'AT VAT zero / exempt',
             description: 'Explicit zero-rated or exempt AT line.',
             ratePercent: 0.00G, taxKind: KIND_EXEMPT, reverseCharge: false, intraCommunity: false],
            [code: 'AT_RC', jurisdiction: 'AT', name: 'AT reverse charge',
             description: 'B2B reverse charge — net invoice; buyer accounts for VAT.',
             ratePercent: 0.00G, taxKind: KIND_REVERSE_CHARGE, reverseCharge: true, intraCommunity: false],
            [code: 'AT_IG', jurisdiction: 'AT', name: 'AT intra-community supply',
             description: 'Intra-community B2B supply (0% + IG flag).',
             ratePercent: 0.00G, taxKind: KIND_ZERO, reverseCharge: false, intraCommunity: true],
            [code: 'AT_EXPORT', jurisdiction: 'AT', name: 'AT export outside EU',
             description: 'Export outside EU (documentary 0%).',
             ratePercent: 0.00G, taxKind: KIND_ZERO, reverseCharge: false, intraCommunity: false],

            // --- United States stubs (P2 depth) ---
            [code: 'US_EXEMPT', jurisdiction: 'US', name: 'US exempt sale',
             description: 'Exempt sale (resale / nontaxable) — stub.',
             ratePercent: 0.00G, taxKind: KIND_EXEMPT, reverseCharge: false, intraCommunity: false],
            [code: 'US_NONE', jurisdiction: 'US', name: 'US nexus not configured',
             description: 'No US nexus / out of scope — stub (0% with operator warning).',
             ratePercent: 0.00G, taxKind: KIND_ZERO, reverseCharge: false, intraCommunity: false],
            [code: 'US_ST_STD', jurisdiction: 'US', name: 'US state sales tax (manual)',
             description: 'Manual blended/state rate template — set rate in Admin (P2).',
             ratePercent: 0.00G, taxKind: KIND_SALES_TAX, reverseCharge: false, intraCommunity: false],
    ].asImmutable()

    static boolean isValid(String code) {
        code != null && ALL.any { (it.code as String) == code }
    }

    static Map rowFor(String code) {
        String c = code?.trim()
        if (!c) return null
        ALL.find { (it.code as String).equalsIgnoreCase(c) }
    }

    static List<Map> forJurisdiction(String jurisdiction) {
        String j = jurisdiction?.trim()?.toUpperCase()
        if (!j) return []
        ALL.findAll { (it.jurisdiction as String) == j }
    }
}
