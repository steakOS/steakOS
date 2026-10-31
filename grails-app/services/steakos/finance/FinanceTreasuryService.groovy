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

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.configuration.TenantMoneyService

/**
 * Treasury positions (APQC 9.7 subset, F-S3). Cash/BTC/LN only — no hedge desk.
 * Dual treasury under one COA (PDR-023 T2–T3): fiat bank + BTC/LN wallets.
 */
@Slf4j
class FinanceTreasuryService {

    static final Map KIND_TO_COA = [
            FIAT_BANK  : '1000',
            BTC_WALLET : '1010',
            LN_WALLET  : '1020',
            FIAT_BRIDGE: '1000',
            OTHER      : '1000',
    ].asImmutable()

    static final Map RAIL_TO_KIND = [
            SEPA               : 'FIAT_BANK',
            BTC_ONCHAIN        : 'BTC_WALLET',
            BTC_LIGHTNING      : 'LN_WALLET',
            BTC_WALLET_ADDRESS : 'BTC_WALLET',
            OTHER              : 'OTHER',
    ].asImmutable()

    FinanceLedgerService financeLedgerService
    TenantMoneyService tenantMoneyService

    @Transactional
    List<Treasury> ensureDefaultPositions(String npubOwner) {
        String npub = requireNpub(npubOwner)
        financeLedgerService.ensureChartSeeded(npub)
        String fiatCur = fiatCurrencyFor(npub)
        [
                [kind: 'FIAT_BANK', name: 'Fiat bank / SEPA', currency: fiatCur],
                [kind: 'BTC_WALLET', name: 'BTC on-chain wallet', currency: 'BTC'],
                [kind: 'LN_WALLET', name: 'Lightning wallet', currency: 'BTC'],
        ].each { Map d ->
            Treasury existing = Treasury.findByNpubOwnerAndAccountKind(npub, d.kind as String)
            if (!existing) {
                registerPosition(npub, [
                        accountKind : d.kind,
                        name        : d.name,
                        currencyCode: d.currency
                ])
            } else if (d.kind in ['BTC_WALLET', 'LN_WALLET'] && existing.currencyCode == 'EUR') {
                // Migrate legacy seed that stamped EUR on crypto wallets
                existing.currencyCode = 'BTC'
                existing.save(failOnError: true, flush: true)
            }
        }
        return listPositions(npub)
    }

    @Transactional
    Treasury registerPosition(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        financeLedgerService.ensureChartSeeded(npub)
        String kind = (fields.accountKind ?: 'FIAT_BANK').toString()
        if (!(kind in KIND_TO_COA.keySet())) {
            throw new IllegalArgumentException("unknown accountKind: ${kind}")
        }
        Account coa = financeLedgerService.findAccount(npub, KIND_TO_COA[kind] as String)
        String defaultCur = kind in ['BTC_WALLET', 'LN_WALLET'] ? 'BTC' : fiatCurrencyFor(npub)
        Treasury t = new Treasury(
                npubOwner      : npub,
                name           : (fields.name ?: kind) as String,
                description    : fields.description as String,
                accountKind    : kind,
                currencyCode   : (fields.currencyCode ?: defaultCur) as String,
                balanceEur     : toBd(fields.balanceEur) ?: BigDecimal.ZERO,
                balanceSats    : fields.balanceSats != null && fields.balanceSats != '' ? fields.balanceSats as Long : 0L,
                ledgerAccountId: coa?.id,
                externalRef    : fields.externalRef as String,
                active         : Boolean.TRUE
        )
        t.save(failOnError: true, flush: true)
        return t
    }

    /** Fiat side of dual treasury (EUR or USD); BTC books still get a fiat bank for SEPA. */
    private String fiatCurrencyFor(String npub) {
        try {
            if (tenantMoneyService != null) {
                Map prefs = tenantMoneyService.prefsFor(npub)
                String rail = prefs.primaryValueRail as String
                if (rail == 'BTC') {
                    return (prefs.displayFiat as String) ?: 'EUR'
                }
                if (rail in ['EUR', 'USD']) {
                    return rail
                }
            }
        } catch (ignored) {
        }
        return 'EUR'
    }

    @Transactional(readOnly = true)
    List<Treasury> listPositions(String npubOwner) {
        listPositionsPage(npubOwner, 200, 0).items as List<Treasury>
    }

    static final List TREASURY_SORT_FIELDS = [
            'id', 'name', 'accountKind', 'balanceEur', 'balanceSats',
            'currencyCode', 'lastReconciledAt', 'dateCreated', 'lastUpdated'
    ].asImmutable()

    /** Paged treasury positions (LIST-PAGINATION). */
    @Transactional(readOnly = true)
    Map listPositionsPage(String npubOwner, int max = 25, int offset = 0, Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'accountKind', 'asc', TREASURY_SORT_FIELDS)
        List items = Treasury.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            eq('active', Boolean.TRUE)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Treasury.createCriteria().count {
            eq('npubOwner', npub)
            eq('active', Boolean.TRUE)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    /**
     * Credit a position (inbound payment). Used by revenue settlement and BTCPay stub.
     */
    @Transactional
    Treasury recordInbound(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String kind = fields.accountKind?.toString()
        if (!kind && fields.rail) {
            kind = RAIL_TO_KIND[fields.rail.toString()] ?: 'FIAT_BANK'
        }
        kind = kind ?: 'FIAT_BANK'
        Treasury pos = Treasury.findByNpubOwnerAndAccountKind(npub, kind)
        if (!pos) {
            ensureDefaultPositions(npub)
            pos = Treasury.findByNpubOwnerAndAccountKind(npub, kind)
        }
        if (!pos) {
            throw new IllegalStateException("no treasury position for kind ${kind}")
        }
        BigDecimal amt = toBd(fields.amountEur) ?: BigDecimal.ZERO
        Long sats = fields.amountSats != null && fields.amountSats != '' ? fields.amountSats as Long : 0L
        pos.balanceEur = (pos.balanceEur ?: BigDecimal.ZERO).add(amt)
        pos.balanceSats = (pos.balanceSats ?: 0L) + (sats ?: 0L)
        pos.save(failOnError: true, flush: true)
        log.info('Treasury inbound {} {} EUR on {} for {}', kind, amt, pos.id, npub)
        return pos
    }

    /**
     * Debit a position (outbound / payable payment).
     */
    @Transactional
    Treasury recordOutbound(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String kind = (fields.accountKind ?: 'FIAT_BANK').toString()
        Treasury pos = Treasury.findByNpubOwnerAndAccountKind(npub, kind)
        if (!pos) {
            ensureDefaultPositions(npub)
            pos = Treasury.findByNpubOwnerAndAccountKind(npub, kind)
        }
        if (!pos) {
            throw new IllegalStateException("no treasury position for kind ${kind}")
        }
        BigDecimal amt = toBd(fields.amountEur) ?: BigDecimal.ZERO
        Long sats = fields.amountSats != null && fields.amountSats != '' ? fields.amountSats as Long : 0L
        pos.balanceEur = (pos.balanceEur ?: BigDecimal.ZERO).subtract(amt)
        pos.balanceSats = (pos.balanceSats ?: 0L) - (sats ?: 0L)
        pos.save(failOnError: true, flush: true)
        return pos
    }

    @Transactional
    Treasury reconcile(Long id, String npubOwner, Map fields) {
        Treasury pos = load(id, npubOwner)
        if (fields.containsKey('balanceEur')) {
            pos.balanceEur = toBd(fields.balanceEur) ?: BigDecimal.ZERO
        }
        if (fields.containsKey('balanceSats')) {
            pos.balanceSats = fields.balanceSats as Long
        }
        pos.lastReconciledAt = new Date()
        pos.externalRef = fields.externalRef as String ?: pos.externalRef
        pos.save(failOnError: true, flush: true)
        return pos
    }

    /**
     * BTCPay-style webhook stub: settle invoice by external event (no real BTCPay call).
     */
    @Transactional
    Map handlePaymentWebhook(Map payload) {
        String npub = requireNpub(payload.npubOwner as String ?: 'SYSTEM_DEV')
        Long invoiceId = payload.invoiceId as Long
        if (!invoiceId) {
            throw new IllegalArgumentException('invoiceId required')
        }
        // Revenue service injected optionally via Spring
        return [
                accepted : true,
                stub     : true,
                npubOwner: npub,
                invoiceId: invoiceId,
                rail     : payload.rail ?: 'BTC_LIGHTNING',
                note     : 'Use FinanceRevenueService.recordSettlement from controller for real post'
        ]
    }

    Map toMap(Treasury t) {
        if (!t) return null
        [
                id              : t.id,
                name            : t.name,
                description     : t.description,
                accountKind     : t.accountKind,
                currencyCode    : t.currencyCode,
                balanceEur      : t.balanceEur,
                balanceSats     : t.balanceSats,
                ledgerAccountId : t.ledgerAccountId,
                coaCode         : KIND_TO_COA[t.accountKind],
                externalRef     : t.externalRef,
                active          : t.active,
                lastReconciledAt: t.lastReconciledAt,
                npubOwner       : t.npubOwner
        ]
    }

    private Treasury load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Treasury t = Treasury.get(id)
        if (!t || t.npubOwner != npub) {
            throw new IllegalArgumentException("treasury position not found: ${id}")
        }
        return t
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') return null
        if (v instanceof BigDecimal) return v
        new BigDecimal(v.toString())
    }
}
