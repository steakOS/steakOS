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
import groovy.transform.CompileStatic

import java.math.RoundingMode

/**
 * Tenant money preferences (PDR-023).
 * Primary value rail = GL functional currency (T1).
 */
@CompileStatic
@Transactional(readOnly = true)
class TenantMoneyService {

    CompanyConfigService companyConfigService

    static final List<String> RAILS = ['EUR', 'USD', 'BTC'].asImmutable()
    static final List<String> BTC_UNITS = ['SATS', 'BTC'].asImmutable()
    static final List<String> FIATS = ['EUR', 'USD'].asImmutable()
    static final BigDecimal SATS_PER_BTC = new BigDecimal('100000000')

    /**
     * Normalize wizard/API draft into canonical money prefs.
     * @return map with primaryValueRail, functionalCurrency, bitcoinDisplayUnit, displayFiat, dualSats
     */
    static Map normalizeDraft(Map d) {
        Map src = d ?: [:]
        String rail = (src.primaryValueRail as String)?.trim()?.toUpperCase()
        if (!rail || !(rail in RAILS)) {
            // Legacy: functionalCurrency only
            String fc = (src.functionalCurrency as String)?.trim()?.toUpperCase()
            if (fc == 'USD') rail = 'USD'
            else if (fc == 'BTC' || fc == 'XBT' || fc == 'SATS') rail = 'BTC'
            else rail = 'EUR'
        }
        String functional = rail // T1: rail == books
        String btcUnit = (src.bitcoinDisplayUnit as String)?.trim()?.toUpperCase()
        if (btcUnit == 'SAT') btcUnit = 'SATS'
        if (!(btcUnit in BTC_UNITS)) btcUnit = 'SATS'
        String displayFiat = (src.displayFiat as String)?.trim()?.toUpperCase()
        if (!(displayFiat in FIATS)) displayFiat = 'EUR'
        // PDR-023: conversion is hint-only — do not persist dual amounts on docs
        Boolean dualSats = Boolean.FALSE
        [
                primaryValueRail   : rail,
                functionalCurrency : functional,
                bitcoinDisplayUnit : rail == 'BTC' ? btcUnit : null,
                displayFiat        : rail == 'BTC' ? displayFiat : null,
                dualSats           : dualSats
        ]
    }

    /** Default primary rail from locale string (D13): en-US / en_US → USD, else EUR. */
    static String defaultRailForLocale(String locale) {
        String loc = (locale ?: '').trim().toLowerCase().replace('_', '-')
        if (loc == 'en-us' || loc.startsWith('en-us')) {
            return 'USD'
        }
        return 'EUR'
    }

    Map prefsFor(String npubOwner) {
        String npub = npubOwner?.trim()
        if (!npub) {
            return normalizeDraft([:])
        }
        String rail = companyConfigService.getString(npub, 'finance.primaryValueRail', null)
        String fc = companyConfigService.getString(npub, 'finance.functionalCurrency', 'EUR')
        String unit = companyConfigService.getString(npub, 'finance.bitcoinDisplayUnit', 'SATS')
        String fiat = companyConfigService.getString(npub, 'finance.displayFiat', 'EUR')
        normalizeDraft([
                primaryValueRail   : rail ?: fc,
                functionalCurrency : fc,
                bitcoinDisplayUnit : unit,
                displayFiat        : fiat
        ])
    }

    String functionalCurrency(String npubOwner) {
        (prefsFor(npubOwner).functionalCurrency as String) ?: 'EUR'
    }

    String primaryValueRail(String npubOwner) {
        (prefsFor(npubOwner).primaryValueRail as String) ?: 'EUR'
    }

    /**
     * Resolve commercial-document primary amount (PDR-023 D14).
     * Stores functional major units in amountEur column; never requires opposite rail.
     */
    Map resolveDocumentPrimaryAmount(String npubOwner, Map fields) {
        resolveDocumentPrimaryAmountFromPrefs(prefsFor(npubOwner), fields)
    }

    /**
     * Pure helper for tests / callers with prefs already loaded.
     * Accepts fields.amount / fields.amountEur (major), or for BTC+SATS unit:
     * fields.amountSats or fields.amount as integer sats.
     */
    static Map resolveDocumentPrimaryAmountFromPrefs(Map prefsIn, Map fields) {
        Map prefs = prefsIn ?: [:]
        String rail = (prefs.primaryValueRail as String) ?: 'EUR'
        String unit = (prefs.bitcoinDisplayUnit as String) ?: 'SATS'
        Map f = fields ?: [:]

        BigDecimal amount = toBd(f.amount != null ? f.amount : f.amountEur)
        Long satsIn = null
        if (f.amountSats != null && f.amountSats != '') {
            satsIn = f.amountSats as Long
        }

        if (rail == 'BTC' && unit == 'SATS') {
            if (satsIn == null && f.amount != null && f.amountEur == null) {
                try {
                    satsIn = new BigDecimal(f.amount.toString()).longValue()
                } catch (ignored) {
                    satsIn = null
                }
            }
            if (satsIn != null) {
                if (satsIn <= 0L) {
                    throw new IllegalArgumentException('amount must be > 0')
                }
                amount = new BigDecimal(satsIn).divide(SATS_PER_BTC, 8, RoundingMode.HALF_UP)
            }
        }

        if (amount == null || amount <= BigDecimal.ZERO) {
            throw new IllegalArgumentException('amount must be > 0')
        }
        String currency = rail == 'BTC' ? 'BTC' : rail
        [
                amountFunctional : amount,
                currencyCode     : currency,
                amountSatsInput  : satsIn
        ]
    }

    private static BigDecimal toBd(Object o) {
        if (o == null || o == '') return null
        if (o instanceof BigDecimal) return (BigDecimal) o
        try {
            return new BigDecimal(o.toString().trim())
        } catch (ignored) {
            return null
        }
    }
}
