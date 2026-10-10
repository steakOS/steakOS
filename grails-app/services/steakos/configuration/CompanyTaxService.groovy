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
import java.math.RoundingMode
import steakos.finance.TaxCatalogCodes
import steakos.finance.TaxCode

/**
 * Company tax regime + in-use tax codes + line net→tax calc (OI-F23 D2–D9, D14).
 */
@Slf4j
class CompanyTaxService {

    CompanyConfigService companyConfigService

    static final String KEY_REGIME = 'finance.taxRegime'
    static final String KEY_DEFAULT_CODE = 'finance.default_tax_code'
    static final String KEY_DEFAULT_RATE = 'finance.default_tax_rate'

    static final String REGIME_DE_VAT = 'DE_VAT'
    static final String REGIME_AT_VAT = 'AT_VAT'
    static final String REGIME_US_SALES = 'US_SALES'
    static final String REGIME_NONE = 'NONE'
    static final String REGIME_DE_SMALL = 'DE_SMALL_BUSINESS'

    static final List<String> REGIMES = [
            REGIME_DE_VAT, REGIME_AT_VAT, REGIME_US_SALES, REGIME_NONE, REGIME_DE_SMALL
    ].asImmutable()

    static final String ERR_UNKNOWN = 'TAX_CODE_UNKNOWN'
    static final String ERR_DISABLED = 'TAX_CODE_DISABLED'
    static final String ERR_NOT_IN_USE = 'TAX_CODE_NOT_IN_USE'
    static final String ERR_REQUIRED = 'TAX_CODE_REQUIRED'

    /** Map LegalEntity / wizard country → default regime (D3). */
    static String regimeForCountry(String countryCode) {
        String cc = countryCode?.trim()?.toUpperCase()
        if (cc == 'DE' || cc == 'DEU') return REGIME_DE_VAT
        if (cc == 'AT' || cc == 'AUT') return REGIME_AT_VAT
        if (cc == 'US' || cc == 'USA') return REGIME_US_SALES
        return REGIME_NONE
    }

    static String defaultCodeForRegime(String regime) {
        switch (normalizeRegime(regime)) {
            case REGIME_DE_VAT: return 'DE_USt_STD'
            case REGIME_AT_VAT: return 'AT_USt_STD'
            case REGIME_US_SALES: return 'US_ST_STD'
            default: return null
        }
    }

    static boolean suppressesOutputVat(String regime) {
        String r = normalizeRegime(regime)
        r == REGIME_NONE || r == REGIME_DE_SMALL
    }

    static boolean requiresLineTax(String regime) {
        !suppressesOutputVat(regime)
    }

    @Transactional(readOnly = true)
    String taxRegime(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String stored = companyConfigService?.getString(npub, KEY_REGIME, null)
        if (stored) return normalizeRegime(stored)
        LegalEntity le = LegalEntity.findByNpubOwner(npub)
        regimeForCountry(le?.countryCode)
    }

    @Transactional(readOnly = true)
    String defaultTaxCode(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String stored = companyConfigService?.getString(npub, KEY_DEFAULT_CODE, null)
        if (stored?.trim()) return stored.trim()
        defaultCodeForRegime(taxRegime(npub))
    }

    @Transactional
    void applyRegimeSettings(String npubOwner, String regimeIn, String defaultCodeIn = null) {
        String npub = requireNpub(npubOwner)
        String regime = normalizeRegime(regimeIn) ?: REGIME_NONE
        if (!(regime in REGIMES)) {
            throw new IllegalArgumentException("Unknown tax regime: ${regimeIn}")
        }
        companyConfigService?.put(npub, KEY_REGIME, regime, 'finance', 'string')
        String defCode = defaultCodeIn?.trim() ?: defaultCodeForRegime(regime)
        if (defCode) {
            companyConfigService?.put(npub, KEY_DEFAULT_CODE, defCode, 'finance', 'string')
            TaxCode tc = TaxCode.findByCode(defCode)
            if (tc?.ratePercent != null) {
                BigDecimal frac = tc.ratePercent.divide(new BigDecimal('100'), 6, RoundingMode.HALF_UP)
                companyConfigService?.put(npub, KEY_DEFAULT_RATE, frac.toPlainString(), 'finance', 'number')
            }
        }
        ensureCompanyDefaults(npub)
    }

    /** Seed regime + in-use pack from LegalEntity country (wizard / backfill). */
    @Transactional
    void ensureCompanyDefaults(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String regime = taxRegime(npub)
        if (!companyConfigService?.getString(npub, KEY_REGIME, null)) {
            companyConfigService?.put(npub, KEY_REGIME, regime, 'finance', 'string')
        }
        if (suppressesOutputVat(regime)) return

        String jur = jurisdictionForRegime(regime)
        List<Map> pack = jur ? TaxCatalogCodes.forJurisdiction(jur) : []
        pack.each { Map row ->
            try {
                ensureInUse(npub, row.code as String)
            } catch (IllegalArgumentException e) {
                log.warn('ensureCompanyDefaults({} {}): {}', npub, row.code, e.message)
            }
        }
        String defaultCode = defaultTaxCode(npub)
        if (defaultCode && !companyConfigService?.getString(npub, KEY_DEFAULT_CODE, null)) {
            companyConfigService?.put(npub, KEY_DEFAULT_CODE, defaultCode, 'finance', 'string')
        }
    }

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

    @Transactional
    CompanyTaxCode ensureInUse(String npubOwner, String taxCode) {
        String npub = requireNpub(npubOwner)
        String code = normalizeCode(taxCode)
        if (!code) throw new IllegalArgumentException('tax code is required')
        TaxCode cat = TaxCode.findByCode(code)
        if (!cat) {
            throw new IllegalArgumentException("${ERR_UNKNOWN}: tax code ${code} is not in the system catalog")
        }
        if (cat.enabled == Boolean.FALSE) {
            throw new IllegalArgumentException("${ERR_DISABLED}: tax code ${code} is disabled system-wide")
        }
        CompanyTaxCode row = CompanyTaxCode.findByNpubOwnerAndTaxCode(npub, code)
        if (!row) {
            row = new CompanyTaxCode(npubOwner: npub, taxCode: code, inUse: Boolean.TRUE)
        } else if (row.inUse != Boolean.TRUE) {
            row.inUse = Boolean.TRUE
        }
        row.save(failOnError: true)
        row
    }

    @Transactional(readOnly = true)
    List<Map> listInUse(String npubOwner) {
        String npub = requireNpub(npubOwner)
        CompanyTaxCode.findAllByNpubOwnerAndInUse(npub, Boolean.TRUE, [sort: 'taxCode']).collect { CompanyTaxCode cc ->
            TaxCode cat = TaxCode.findByCode(cc.taxCode)
            cat && cat.enabled != Boolean.FALSE ? toCatalogMap(cat) : null
        }.findAll { it }
    }

    @Transactional(readOnly = true)
    List<Map> listSystemEnabled(String jurisdiction = null) {
        if (jurisdiction?.trim()) {
            return TaxCode.findAllByEnabledAndJurisdiction(Boolean.TRUE, jurisdiction.trim().toUpperCase(), [sort: 'code'])
                    .collect { toCatalogMap(it) }
        }
        TaxCode.findAllByEnabled(Boolean.TRUE, [sort: 'code']).collect { toCatalogMap(it) }
    }

    @Transactional
    List<Map> setInUse(String npubOwner, Collection codesIn) {
        String npub = requireNpub(npubOwner)
        Set<String> wanted = new LinkedHashSet<>()
        (codesIn ?: []).each { Object raw ->
            String c = normalizeCode(raw?.toString())
            if (c) wanted << c
        }
        String defaultCode = defaultTaxCode(npub)
        if (defaultCode && requiresLineTax(taxRegime(npub))) wanted << defaultCode

        wanted.each { String code ->
            TaxCode cat = TaxCode.findByCode(code)
            if (!cat) {
                throw new IllegalArgumentException("${ERR_UNKNOWN}: tax code ${code} is not in the system catalog")
            }
            if (cat.enabled == Boolean.FALSE) {
                throw new IllegalArgumentException("${ERR_DISABLED}: tax code ${code} is disabled system-wide")
            }
            CompanyTaxCode row = CompanyTaxCode.findByNpubOwnerAndTaxCode(npub, code)
            if (!row) {
                row = new CompanyTaxCode(npubOwner: npub, taxCode: code, inUse: Boolean.TRUE)
            } else {
                row.inUse = Boolean.TRUE
            }
            row.save(failOnError: true)
        }
        CompanyTaxCode.findAllByNpubOwner(npub).each { CompanyTaxCode row ->
            if (!(row.taxCode in wanted) && row.inUse) {
                row.inUse = Boolean.FALSE
                row.save(failOnError: true, flush: true)
            }
        }
        listInUse(npub)
    }

    @Transactional(readOnly = true)
    void validateWritableCode(String npubOwner, String taxCode) {
        String npub = requireNpub(npubOwner)
        String code = normalizeCode(taxCode)
        if (!code) {
            throw new IllegalArgumentException("${ERR_UNKNOWN}: tax code is required")
        }
        TaxCode cat = TaxCode.findByCode(code)
        if (!cat) {
            throw new IllegalArgumentException("${ERR_UNKNOWN}: tax code ${code} is not in the system catalog")
        }
        if (cat.enabled == Boolean.FALSE) {
            throw new IllegalArgumentException("${ERR_DISABLED}: tax code ${code} is disabled system-wide")
        }
        CompanyTaxCode row = CompanyTaxCode.findByNpubOwnerAndTaxCode(npub, code)
        if (!row || row.inUse != Boolean.TRUE) {
            throw new IllegalArgumentException(
                    "${ERR_NOT_IN_USE}: tax code ${code} is not in use for this company")
        }
    }

    /**
     * Resolve line tax from explicit code, catalogue override, or company default (D4, D9).
     * Returns map: taxCode, ratePercent, taxAmountEur, reverseCharge, intraCommunity, taxKind
     */
    @Transactional(readOnly = true)
    Map resolveLineTax(String npubOwner, BigDecimal netAmount, String taxCodeIn,
                       String catalogueDefaultCode = null) {
        String npub = requireNpub(npubOwner)
        String regime = taxRegime(npub)
        if (suppressesOutputVat(regime)) {
            return [
                    taxCode        : null,
                    ratePercent    : BigDecimal.ZERO,
                    taxAmountEur   : BigDecimal.ZERO,
                    reverseCharge  : false,
                    intraCommunity : false,
                    taxKind        : null
            ]
        }
        String code = normalizeCode(taxCodeIn) ?: normalizeCode(catalogueDefaultCode) ?: defaultTaxCode(npub)
        if (!code) {
            if (requiresLineTax(regime)) {
                throw new IllegalArgumentException("${ERR_REQUIRED}: tax code required when regime is ${regime}")
            }
            return [
                    taxCode        : null,
                    ratePercent    : BigDecimal.ZERO,
                    taxAmountEur   : BigDecimal.ZERO,
                    reverseCharge  : false,
                    intraCommunity : false,
                    taxKind        : null
            ]
        }
        // Calc uses system catalog (enabled). In-use gates dropdowns; soft-allow known codes on lines.
        TaxCode cat = TaxCode.findByCode(code)
        if (!cat) {
            throw new IllegalArgumentException("${ERR_UNKNOWN}: tax code ${code} is not in the system catalog")
        }
        if (cat.enabled == Boolean.FALSE) {
            throw new IllegalArgumentException("${ERR_DISABLED}: tax code ${code} is disabled system-wide")
        }
        BigDecimal net = netAmount ?: BigDecimal.ZERO
        BigDecimal tax = computeTaxFromNet(net, cat.ratePercent)
        [
                taxCode        : code,
                ratePercent    : cat.ratePercent,
                taxAmountEur   : tax,
                reverseCharge  : cat.reverseCharge == Boolean.TRUE,
                intraCommunity : cat.intraCommunity == Boolean.TRUE,
                taxKind        : cat.taxKind
        ]
    }

    /** D5: tax = net × rate% / 100, HALF_UP scale 2. */
    static BigDecimal computeTaxFromNet(BigDecimal net, BigDecimal ratePercent) {
        if (net == null || ratePercent == null) return BigDecimal.ZERO.setScale(2)
        if (ratePercent.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2)
        }
        net.multiply(ratePercent)
                .divide(new BigDecimal('100'), 2, RoundingMode.HALF_UP)
    }

    /** PDF / notes: reverse-charge and IG legal phrases (D8). EN + DE. */
    static List<String> legalPhrasesForLines(List lineMaps) {
        boolean rc = false
        boolean ig = false
        (lineMaps ?: []).each { Object raw ->
            Map lm = raw instanceof Map ? (Map) raw : [:]
            if (lm.reverseCharge == true || lm.reverseCharge == Boolean.TRUE) rc = true
            if (lm.intraCommunity == true || lm.intraCommunity == Boolean.TRUE) ig = true
            String code = (lm.taxCode ?: '') as String
            if (code.endsWith('_RC') || code.contains('_RC')) rc = true
            if (code.endsWith('_IG') || code.contains('_IG')) ig = true
        }
        List<String> notes = []
        if (rc) {
            notes << 'Reverse charge — VAT to be accounted for by the recipient / Steuerschuldnerschaft des Leistungsempfängers.'
        }
        if (ig) {
            notes << 'Intra-community supply (0% VAT) / Innergemeinschaftliche Lieferung.'
        }
        notes
    }

    /** True when any invoice/doc tax amounts should drive period estimate (D10). */
    @Transactional(readOnly = true)
    boolean companyHasLineTax(String npubOwner) {
        String npub = requireNpub(npubOwner)
        if (suppressesOutputVat(taxRegime(npub))) return false
        CompanyTaxCode.countByNpubOwnerAndInUse(npub, Boolean.TRUE) > 0
    }

    static Map toCatalogMap(TaxCode c) {
        [
                code           : c.code,
                jurisdiction   : c.jurisdiction,
                name           : c.name,
                description    : c.description,
                ratePercent    : c.ratePercent,
                taxKind        : c.taxKind,
                reverseCharge  : c.reverseCharge == Boolean.TRUE,
                intraCommunity : c.intraCommunity == Boolean.TRUE,
                enabled        : c.enabled != Boolean.FALSE,
                label          : "${c.code} — ${c.name}"
        ]
    }

    static String normalizeCode(String raw) {
        String c = raw?.trim()
        if (!c) return null
        c
    }

    static String normalizeRegime(String raw) {
        String r = raw?.trim()?.toUpperCase()
        if (!r) return null
        if (r == 'DE_SMALL' || r == 'KLEINUNTERNEHMER') return REGIME_DE_SMALL
        r
    }

    static String jurisdictionForRegime(String regime) {
        switch (normalizeRegime(regime)) {
            case REGIME_DE_VAT:
            case REGIME_DE_SMALL: return 'DE'
            case REGIME_AT_VAT: return 'AT'
            case REGIME_US_SALES: return 'US'
            default: return null
        }
    }

    private static String requireNpub(String npubOwner) {
        String npub = npubOwner?.trim()
        if (!npub) throw new IllegalArgumentException('npubOwner is required')
        npub
    }
}
