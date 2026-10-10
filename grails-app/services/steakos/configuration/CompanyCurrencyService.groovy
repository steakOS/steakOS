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
import groovy.util.logging.Slf4j
import steakos.helper.Currencies

/**
 * Company currency in-use + company working fiat (OI-CUR01 D1–D2, D7–D8, D11).
 */
@Slf4j
class CompanyCurrencyService {

    TenantMoneyService tenantMoneyService

    static final String ERR_UNKNOWN = 'CURRENCY_UNKNOWN'
    static final String ERR_DISABLED = 'CURRENCY_DISABLED'
    static final String ERR_NOT_IN_USE = 'CURRENCY_NOT_IN_USE'

    /**
     * Company currency for chip / soft checks:
     * functional fiat when rail EUR/USD; displayFiat when rail BTC.
     * Falls back to LegalEntity.functionalCurrency when money prefs were never applied.
     */
    @Transactional(readOnly = true)
    String companyCurrencyCode(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Map prefs = (tenantMoneyService != null ? tenantMoneyService.prefsFor(npub) : null) ?: [:]
        String rail = (prefs.primaryValueRail as String) ?: 'EUR'
        if (rail == 'BTC') {
            return ((prefs.displayFiat as String) ?: 'EUR').toUpperCase()
        }
        if (rail == 'USD' || rail == 'EUR') {
            // Prefer explicit LE fiat when company config still defaults to EUR
            String fromLe = legalEntityFiat(npub)
            if (fromLe && rail == 'EUR' && fromLe != 'EUR' && !hasExplicitRailConfig(npub)) {
                return fromLe
            }
            return rail
        }
        String fc = (prefs.functionalCurrency as String)?.toUpperCase()
        if (fc && fc != 'BTC') return fc
        return legalEntityFiat(npub) ?: 'EUR'
    }

    private static String legalEntityFiat(String npub) {
        LegalEntity le = LegalEntity.findByNpubOwner(npub)
        String fc = le?.functionalCurrency?.trim()?.toUpperCase()
        if (!fc || fc == 'BTC' || fc == 'XBT' || fc == 'SATS') return null
        fc
    }

    private boolean hasExplicitRailConfig(String npub) {
        if (tenantMoneyService?.companyConfigService == null) return false
        try {
            String rail = tenantMoneyService.companyConfigService.getString(npub, 'finance.primaryValueRail', null)
            return rail != null && !rail.trim().isEmpty()
        } catch (ignored) {
            return false
        }
    }

    @Transactional
    CompanyCurrency ensureInUse(String npubOwner, String currencyCode) {
        String npub = requireNpub(npubOwner)
        String code = normalizeCode(currencyCode)
        if (!code) {
            throw new IllegalArgumentException('currency code is required')
        }
        Currencies cat = Currencies.findByCode(code)
        if (!cat) {
            throw new IllegalArgumentException("${ERR_UNKNOWN}: currency ${code} is not in the system catalog")
        }
        if (cat.enabled == Boolean.FALSE) {
            throw new IllegalArgumentException("${ERR_DISABLED}: currency ${code} is disabled system-wide")
        }
        CompanyCurrency row = CompanyCurrency.findByNpubOwnerAndCurrencyCode(npub, code)
        if (!row) {
            row = new CompanyCurrency(npubOwner: npub, currencyCode: code, inUse: Boolean.TRUE)
        } else if (row.inUse != Boolean.TRUE) {
            row.inUse = Boolean.TRUE
        }
        row.save(failOnError: true)
        row
    }

    @Transactional(readOnly = true)
    List<Map> listInUse(String npubOwner) {
        String npub = requireNpub(npubOwner)
        List<CompanyCurrency> rows = CompanyCurrency.findAllByNpubOwnerAndInUse(npub, Boolean.TRUE, [sort: 'currencyCode'])
        List<Map> out = []
        rows.each { CompanyCurrency cc ->
            Currencies cat = Currencies.findByCode(cc.currencyCode)
            if (cat && cat.enabled != Boolean.FALSE) {
                out << toCatalogMap(cat)
            }
        }
        out
    }

    @Transactional(readOnly = true)
    List<Map> listSystemEnabled() {
        Currencies.findAllByEnabled(Boolean.TRUE, [sort: 'code']).collect { Currencies c -> toCatalogMap(c) }
    }

    /**
     * Admin: set exact in-use set. Codes not listed are marked inUse=false (rows kept).
     * Always keeps company currency in-use.
     */
    @Transactional
    List<Map> setInUse(String npubOwner, Collection codesIn) {
        String npub = requireNpub(npubOwner)
        Set<String> wanted = new LinkedHashSet<>()
        (codesIn ?: []).each { Object raw ->
            String c = normalizeCode(raw?.toString())
            if (c) wanted << c
        }
        String company = companyCurrencyCode(npub)
        if (company) wanted << company

        wanted.each { String code ->
            Currencies cat = Currencies.findByCode(code)
            if (!cat) {
                throw new IllegalArgumentException("${ERR_UNKNOWN}: currency ${code} is not in the system catalog")
            }
            if (cat.enabled == Boolean.FALSE) {
                throw new IllegalArgumentException("${ERR_DISABLED}: currency ${code} is disabled system-wide")
            }
            CompanyCurrency row = CompanyCurrency.findByNpubOwnerAndCurrencyCode(npub, code)
            if (!row) {
                row = new CompanyCurrency(npubOwner: npub, currencyCode: code, inUse: Boolean.TRUE)
            } else {
                row.inUse = Boolean.TRUE
            }
            row.save(failOnError: true)
        }

        CompanyCurrency.findAllByNpubOwner(npub).each { CompanyCurrency row ->
            if (!(row.currencyCode in wanted) && row.inUse) {
                row.inUse = Boolean.FALSE
                row.save(failOnError: true, flush: true)
            }
        }
        listInUse(npub)
    }

    /**
     * D8: new writes must be system-enabled and company in-use.
     * @throws IllegalArgumentException with clear message
     */
    @Transactional(readOnly = true)
    void validateWritableCode(String npubOwner, String currencyCode) {
        String npub = requireNpub(npubOwner)
        String code = normalizeCode(currencyCode)
        if (!code) {
            throw new IllegalArgumentException("${ERR_UNKNOWN}: currency code is required")
        }
        Currencies cat = Currencies.findByCode(code)
        if (!cat) {
            throw new IllegalArgumentException(
                    "${ERR_UNKNOWN}: currency ${code} is not in the system catalog — enable it in Currencies first")
        }
        if (cat.enabled == Boolean.FALSE) {
            throw new IllegalArgumentException(
                    "${ERR_DISABLED}: currency ${code} is disabled system-wide")
        }
        CompanyCurrency row = CompanyCurrency.findByNpubOwnerAndCurrencyCode(npub, code)
        if (!row || row.inUse != Boolean.TRUE) {
            throw new IllegalArgumentException(
                    "${ERR_NOT_IN_USE}: currency ${code} is not in use for this company — Admin can enable it under Settings → Currencies we use")
        }
    }

    /** Soft-only mismatch warnings (OI-CUR01 D14–D15). Never blocks save. */
    @Transactional(readOnly = true)
    List<Map> collectDocumentWarnings(String npubOwner, String documentCurrency,
                                      String partnerPreferred, String partnerLabel,
                                      List lineItems) {
        List<Map> warnings = []
        String npub = npubOwner?.trim()
        if (!npub) return warnings
        String doc = normalizeCode(documentCurrency) ?: 'EUR'
        String company = companyCurrencyCode(npub)
        if (company && doc != company) {
            warnings << [
                    code   : 'CURRENCY_DOC_NE_COMPANY',
                    message: "Document currency differs from company currency (top bar): document ${doc}, company ${company}."
            ]
        }
        String partner = normalizeCode(partnerPreferred)
        if (partner && partner != doc) {
            String who = (partnerLabel ?: 'Partner').trim()
            warnings << [
                    code   : 'CURRENCY_DOC_NE_PARTNER',
                    message: "${who} preferred currency is ${partner}; document is ${doc}."
            ]
        }
        Set<String> seenItems = new LinkedHashSet<>()
        (lineItems ?: []).each { Object raw ->
            Map line = raw instanceof Map ? (Map) raw : [:]
            String itemCur = normalizeCode((line.currency ?: line.itemCurrency ?: line.currencyCode) as String)
            if (!itemCur || itemCur == doc) return
            String itemName = (line.name ?: line.itemName ?: line.code ?: 'item') as String
            String key = "${itemName}|${itemCur}"
            if (seenItems.contains(key)) return
            seenItems << key
            warnings << [
                    code   : 'CURRENCY_LINE_NE_DOC',
                    message: "List price for ${itemName} is in ${itemCur}, not document ${doc}. Amount may be wrong."
            ]
        }
        warnings
    }

    /** Ensure functional (+ displayFiat if distinct) in-use for one tenant (D7). */
    @Transactional
    void ensureCompanyDefaults(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Set<String> codes = new LinkedHashSet<>()
        String company = companyCurrencyCode(npub)
        if (company) codes << company
        String leFiat = legalEntityFiat(npub)
        if (leFiat) codes << leFiat
        if (tenantMoneyService != null) {
            Map prefs = tenantMoneyService.prefsFor(npub) ?: [:]
            String rail = prefs.primaryValueRail as String
            if (rail == 'BTC') {
                String fiat = prefs.displayFiat as String
                if (fiat) codes << fiat.toUpperCase()
            } else {
                String fc = prefs.functionalCurrency as String
                if (fc && fc != 'BTC') codes << fc.toUpperCase()
            }
        }
        codes.each { String code ->
            try {
                ensureInUse(npub, code)
            } catch (IllegalArgumentException e) {
                log.warn('ensureCompanyDefaults({} {}): {}', npub, code, e.message)
            }
        }
    }

    /** Backfill all LegalEntity tenants. */
    @Transactional
    int backfillAllCompanies() {
        int n = 0
        LegalEntity.list().each { LegalEntity le ->
            if (le?.npubOwner) {
                ensureCompanyDefaults(le.npubOwner)
                n++
            }
        }
        n
    }

    static Map toCatalogMap(Currencies c) {
        [
                code       : c.code,
                name       : c.name,
                description: c.description,
                decimals   : c.decimals,
                enabled    : c.enabled != Boolean.FALSE,
                label      : "${c.code} — ${c.name}"
        ]
    }

    static String normalizeCode(String raw) {
        String c = raw?.trim()?.toUpperCase()
        if (!c) return null
        // Reject non-ISO rails masquerading as catalog codes
        if (c in ['BTC', 'XBT', 'SATS', 'SAT']) return null
        c.length() > 3 ? c.substring(0, 3) : c
    }

    private static String requireNpub(String npubOwner) {
        String npub = npubOwner?.trim()
        if (!npub) throw new IllegalArgumentException('npubOwner is required')
        npub
    }
}
