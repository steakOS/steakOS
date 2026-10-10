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

package steakos.payments

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.configuration.CompanyConfigService
import steakos.configuration.LegalEntity
import steakos.security.TotpService

/**
 * OI-BTC01 — Bitcoin integration level (DEFERRED | SINGLE_ADDRESS | BTCPAY).
 * Separate from primary value rail (PDR-023).
 */
@Slf4j
@Transactional
class BtcIntegrationLevelService {

    static final String CAT = 'payments'

    static final String LEVEL_DEFERRED = 'DEFERRED'
    static final String LEVEL_SINGLE_ADDRESS = 'SINGLE_ADDRESS'
    static final String LEVEL_BTCPAY = 'BTCPAY'

    static final List LEVELS = [LEVEL_DEFERRED, LEVEL_SINGLE_ADDRESS, LEVEL_BTCPAY].asImmutable()

    static final String KEY_LEVEL = 'payments.btcIntegrationLevel'
    static final String KEY_SET_AT = 'payments.btcIntegrationLevelSetAt'
    static final String KEY_SET_BY = 'payments.btcIntegrationLevelSetBy'
    static final String KEY_ADDRESS = 'payments.btcReceiveAddress'
    static final String KEY_ADDRESS_LABEL = 'payments.btcReceiveAddressLabel'
    static final String KEY_TRAINING = 'payments.btcTrainingNudge'
    static final String KEY_CEO_ACK_AT = 'payments.btcTrainingAckAt'
    static final String KEY_CEO_ACK_BY = 'payments.btcTrainingAckBy'

    static final String NUDGE_BASIC = 'BASIC'
    static final String NUDGE_ADVANCED = 'ADVANCED'
    static final String NUDGE_NONE = 'NONE'

    static final String ERR_ADMIN_REQUIRED = 'BTC_LEVEL_ADMIN_REQUIRED'
    static final String ERR_CEO_ACK_REQUIRED = 'BTC_LEVEL_CEO_ACK_REQUIRED'
    static final String ERR_DOWNGRADE_CONFIRM = 'BTC_LEVEL_DOWNGRADE_CONFIRM_REQUIRED'
    static final String ERR_ADDRESS_REQUIRED = 'BTC_RECEIVE_ADDRESS_REQUIRED'
    static final String ERR_ADDRESS_INVALID = 'BTC_RECEIVE_ADDRESS_INVALID'
    static final String ERR_DEMO_LIVE = 'DEMO_BTC_LEVEL_LIVE_BLOCKED'
    static final String ERR_UNKNOWN_LEVEL = 'BTC_LEVEL_UNKNOWN'

    CompanyConfigService companyConfigService
    PaymentSettingsService paymentSettingsService
    TotpService totpService

    /**
     * If level unset: BTCPAY when live BTCPay ready, else DEFERRED.
     * Idempotent. Returns resolved level code.
     */
    String ensureMigrated(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String existing = companyConfigService.getString(npub, KEY_LEVEL, null)
        if (existing && existing.trim()) {
            return normalizeLevel(existing)
        }
        boolean liveReady = false
        try {
            liveReady = paymentSettingsService?.isBtcpayLiveReady(npub) == true
        } catch (Exception e) {
            log.debug('migrate live-ready check failed: {}', e.message)
        }
        String level = liveReady ? LEVEL_BTCPAY : LEVEL_DEFERRED
        writeLevel(npub, level, 'SYSTEM', trainingNudgeFor(level))
        if (level == LEVEL_BTCPAY) {
            // Keep existing btcpay.* ; nothing else required
        }
        level
    }

    /** Migrate all LegalEntity tenants that lack a level key. */
    int migrateAllCompanies() {
        int n = 0
        LegalEntity.list()?.each { LegalEntity le ->
            if (!le?.npubOwner) return
            String existing = companyConfigService.getString(le.npubOwner, KEY_LEVEL, null)
            if (!existing?.trim()) {
                ensureMigrated(le.npubOwner)
                n++
            }
        }
        n
    }

    String currentLevel(String npubOwner) {
        ensureMigrated(npubOwner)
    }

    Map publicLevel(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String level = ensureMigrated(npub)
        boolean demo = isDemo(npub)
        String addr = companyConfigService.getString(npub, KEY_ADDRESS, null)
        String label = companyConfigService.getString(npub, KEY_ADDRESS_LABEL, null)
        String nudge = companyConfigService.getString(npub, KEY_TRAINING, null) ?: trainingNudgeFor(level)
        Map out = [
                level            : level,
                uiLevel          : uiLevelNumber(level),
                uiLabel          : uiLabel(level),
                setAt            : companyConfigService.getString(npub, KEY_SET_AT, null),
                setBy            : companyConfigService.getString(npub, KEY_SET_BY, null),
                trainingNudge    : nudge,
                trainingAckAt    : companyConfigService.getString(npub, KEY_CEO_ACK_AT, null),
                trainingAckBy    : companyConfigService.getString(npub, KEY_CEO_ACK_BY, null),
                demoForcedMock   : demo,
                receiveAddress   : null,
                receiveAddressMasked: null,
                receiveAddressLabel : label,
                receiveAddressQrDataUrl: null,
                level2Warning    : level == LEVEL_SINGLE_ADDRESS,
                allowsLiveBtcpay : level == LEVEL_BTCPAY && !demo,
                allowsSingleAddressReceive: level == LEVEL_SINGLE_ADDRESS && !demo
        ]
        if (level == LEVEL_SINGLE_ADDRESS && addr) {
            out.receiveAddress = addr
            out.receiveAddressMasked = maskAddress(addr)
            try {
                if (totpService != null) {
                    String uri = addr.startsWith('bitcoin:') ? addr : "bitcoin:${addr}"
                    out.receiveAddressQrDataUrl = totpService.qrDataUrl(uri, 180)
                }
            } catch (Exception e) {
                log.debug('QR for receive address failed: {}', e.message)
            }
        }
        out
    }

    /**
     * Admin sets level. Upgrade to SINGLE_ADDRESS/BTCPAY requires ceoTrainingAck.
     * Downgrade from BTCPAY requires confirmDowngrade and disables liveEnabled.
     */
    Map setLevel(String npubOwner, Map body, String actorUsername,
                 boolean actorIsAdmin, boolean actorIsCeo) {
        String npub = requireNpub(npubOwner)
        if (!actorIsAdmin) {
            throw new IllegalArgumentException("${ERR_ADMIN_REQUIRED}: only Admin may change Bitcoin integration level")
        }
        String target = normalizeLevel(body.level ?: body.btcIntegrationLevel)
        if (!target) {
            throw new IllegalArgumentException("${ERR_UNKNOWN_LEVEL}: level must be one of ${LEVELS}")
        }
        if (isDemo(npub) && target != LEVEL_DEFERRED) {
            throw new IllegalArgumentException(
                    "${ERR_DEMO_LIVE}: Demo companies stay on Level 1 (deferred / mock) — use your LIVE company")
        }

        String current = ensureMigrated(npub)
        boolean upgrade = rank(target) > rank(current)
        boolean downgradeFrom3 = current == LEVEL_BTCPAY && target != LEVEL_BTCPAY

        if (upgrade && target in [LEVEL_SINGLE_ADDRESS, LEVEL_BTCPAY]) {
            boolean ack = body.ceoTrainingAck == true ||
                    body.ceoTrainingAck?.toString()?.equalsIgnoreCase('true')
            if (!ack) {
                throw new IllegalArgumentException(
                        "${ERR_CEO_ACK_REQUIRED}: CEO must acknowledge training before upgrade to Level ${uiLevelNumber(target)}")
            }
            String ackBy = (body.ceoTrainingAckBy ?: actorUsername ?: 'admin').toString()
            companyConfigService.put(npub, KEY_CEO_ACK_AT, isoNow(), CAT, 'string')
            companyConfigService.put(npub, KEY_CEO_ACK_BY, ackBy, CAT, 'string')
        }

        if (downgradeFrom3) {
            boolean confirm = body.confirmDowngrade == true ||
                    body.confirmDowngrade?.toString()?.equalsIgnoreCase('true')
            if (!confirm) {
                throw new IllegalArgumentException(
                        "${ERR_DOWNGRADE_CONFIRM}: confirmDowngrade=true required to leave Level 3 (disables live BTCPay)")
            }
            // D6: disable live path
            companyConfigService.put(npub, PaymentSettingsService.KEY_LIVE, 'false', CAT, 'boolean')
            companyConfigService.put(npub, PaymentSettingsService.KEY_PROVIDER, PaymentSettingsService.PROVIDER_MOCK, CAT, 'string')
        }

        if (target == LEVEL_SINGLE_ADDRESS) {
            String addr = (body.btcReceiveAddress ?: body.receiveAddress)?.toString()?.trim()
            if (!addr) {
                // Keep existing if already set
                addr = companyConfigService.getString(npub, KEY_ADDRESS, null)
            }
            if (!addr) {
                throw new IllegalArgumentException("${ERR_ADDRESS_REQUIRED}: Level 2 requires a receive address")
            }
            validateReceiveAddress(addr)
            companyConfigService.put(npub, KEY_ADDRESS, addr, CAT, 'string')
            if (body.btcReceiveAddressLabel != null || body.receiveAddressLabel != null) {
                String lbl = (body.btcReceiveAddressLabel ?: body.receiveAddressLabel)?.toString()?.trim() ?: ''
                companyConfigService.put(npub, KEY_ADDRESS_LABEL, lbl, CAT, 'string')
            }
        }

        writeLevel(npub, target, actorUsername ?: 'admin', trainingNudgeFor(target))

        if (target == LEVEL_BTCPAY) {
            Map btcpay = body.btcpay instanceof Map ? body.btcpay as Map : null
            if (btcpay != null && paymentSettingsService != null) {
                Map upd = [provider: PaymentSettingsService.PROVIDER_BTCPAY]
                upd.btcpay = btcpay
                if (body.liveEnabled != null) {
                    upd.liveEnabled = body.liveEnabled
                }
                paymentSettingsService.updateSettings(npub, upd)
            }
        }

        if (target == LEVEL_DEFERRED) {
            companyConfigService.put(npub, PaymentSettingsService.KEY_LIVE, 'false', CAT, 'boolean')
            companyConfigService.put(npub, PaymentSettingsService.KEY_PROVIDER, PaymentSettingsService.PROVIDER_MOCK, CAT, 'string')
        }

        publicLevel(npub)
    }

    /**
     * Wizard apply path — no Admin/CEO dance; operator is configuring first-time.
     * Seeds BTCPay settings when Level 3 fields present.
     */
    Map applyFromWizard(String npubOwner, Map draft, String actorUsername) {
        String npub = requireNpub(npubOwner)
        String raw = (draft.btcIntegrationLevel ?: draft.bitcoinIntegrationLevel)?.toString()
        String level = raw ? normalizeLevel(raw) : LEVEL_DEFERRED
        if (!level) level = LEVEL_DEFERRED

        if (isDemo(npub)) {
            level = LEVEL_DEFERRED
        }

        if (level == LEVEL_SINGLE_ADDRESS) {
            String addr = (draft.btcReceiveAddress ?: draft.receiveAddress)?.toString()?.trim()
            if (!addr) {
                throw new IllegalArgumentException("${ERR_ADDRESS_REQUIRED}: Level 2 requires a Bitcoin receive address")
            }
            validateReceiveAddress(addr)
            companyConfigService.put(npub, KEY_ADDRESS, addr, CAT, 'string')
            String lbl = (draft.btcReceiveAddressLabel ?: draft.receiveAddressLabel)?.toString()?.trim() ?: ''
            companyConfigService.put(npub, KEY_ADDRESS_LABEL, lbl, CAT, 'string')
            companyConfigService.put(npub, KEY_CEO_ACK_AT, isoNow(), CAT, 'string')
            companyConfigService.put(npub, KEY_CEO_ACK_BY, actorUsername ?: 'admin', CAT, 'string')
        }

        // Persist level first so PaymentSettingsService live gate sees Level 3
        writeLevel(npub, level, actorUsername ?: 'admin', trainingNudgeFor(level))

        if (level == LEVEL_BTCPAY && paymentSettingsService != null) {
            Map btcpay = [:]
            if (draft.btcpayBaseUrl || draft.btcpayUrl) {
                btcpay.baseUrl = (draft.btcpayBaseUrl ?: draft.btcpayUrl)?.toString()?.trim()
            }
            if (draft.btcpayStoreId) btcpay.storeId = draft.btcpayStoreId.toString().trim()
            if (draft.btcpayDefaultCurrency) btcpay.defaultCurrency = draft.btcpayDefaultCurrency.toString().trim()
            if (draft.btcpayApiKey) btcpay.apiKey = draft.btcpayApiKey.toString().trim()
            if (draft.btcpayWebhookSecret) btcpay.webhookSecret = draft.btcpayWebhookSecret.toString().trim()
            Map upd = [provider: PaymentSettingsService.PROVIDER_BTCPAY, btcpay: btcpay]
            // Wizard seeds config; liveEnabled stays off until probe in Finance → Payments
            if (draft.btcpayLiveEnabled == true || draft.btcpayLiveEnabled?.toString()?.equalsIgnoreCase('true')) {
                upd.liveEnabled = true
            }
            paymentSettingsService.updateSettings(npub, upd)
            companyConfigService.put(npub, KEY_CEO_ACK_AT, isoNow(), CAT, 'string')
            companyConfigService.put(npub, KEY_CEO_ACK_BY, actorUsername ?: 'admin', CAT, 'string')
        }

        if (level == LEVEL_DEFERRED && paymentSettingsService != null) {
            paymentSettingsService.updateSettings(npub, [
                    provider   : PaymentSettingsService.PROVIDER_MOCK,
                    liveEnabled: false
            ])
        }

        publicLevel(npub)
    }

    /** True when live BTCPay Greenfield path may run. */
    boolean allowsLiveBtcpay(String npubOwner) {
        String npub = requireNpub(npubOwner)
        if (isDemo(npub)) return false
        ensureMigrated(npub) == LEVEL_BTCPAY
    }

    boolean isSingleAddress(String npubOwner) {
        ensureMigrated(npubOwner) == LEVEL_SINGLE_ADDRESS && !isDemo(npubOwner)
    }

    static int uiLevelNumber(String level) {
        switch (normalizeLevel(level)) {
            case LEVEL_SINGLE_ADDRESS: return 2
            case LEVEL_BTCPAY: return 3
            default: return 1
        }
    }

    static String uiLabel(String level) {
        switch (normalizeLevel(level)) {
            case LEVEL_SINGLE_ADDRESS: return '2 — Single address'
            case LEVEL_BTCPAY: return '3 — BTCPay'
            default: return '1 — Not now'
        }
    }

    static String trainingNudgeFor(String level) {
        switch (normalizeLevel(level)) {
            case LEVEL_SINGLE_ADDRESS: return NUDGE_ADVANCED
            case LEVEL_BTCPAY: return NUDGE_NONE
            default: return NUDGE_BASIC
        }
    }

    static String normalizeLevel(Object raw) {
        if (raw == null) return null
        String s = raw.toString().trim().toUpperCase()
        if (!s) return null
        // Accept UI numbers / aliases
        if (s in ['1', 'L1', 'LEVEL1', 'LEVEL_1', 'DEFER', 'NONE', 'MOCK']) return LEVEL_DEFERRED
        if (s in ['2', 'L2', 'LEVEL2', 'LEVEL_2', 'ADDRESS', 'SINGLE', 'WALLET']) return LEVEL_SINGLE_ADDRESS
        if (s in ['3', 'L3', 'LEVEL3', 'LEVEL_3', 'BTCPAY_SERVER', 'GREENFIELD']) return LEVEL_BTCPAY
        if (s in LEVELS) return s
        null
    }

    static void validateReceiveAddress(String addr) {
        String a = addr?.trim()
        if (!a) {
            throw new IllegalArgumentException("${ERR_ADDRESS_INVALID}: empty address")
        }
        String lower = a.toLowerCase()
        if (lower.contains('xprv') || lower.contains('xpub') || lower.contains('mnemonic') ||
                a.split(/\s+/).length >= 12) {
            throw new IllegalArgumentException("${ERR_ADDRESS_INVALID}: do not enter seeds, xprv, or mnemonics")
        }
        // Legacy P2PKH/P2SH or bech32 (bc1…) / testnet tb1 — length band only
        boolean ok = (a ==~ /^(bc1|[13])[a-zA-HJ-NP-Z0-9]{14,90}$/) ||
                (a ==~ /^(tb1|bcrt1)[a-zA-HJ-NP-Z0-9]{14,90}$/)
        if (!ok) {
            throw new IllegalArgumentException(
                    "${ERR_ADDRESS_INVALID}: expected a Bitcoin address (bc1… / 1… / 3…), not a seed")
        }
    }

    static String maskAddress(String addr) {
        if (!addr || addr.length() < 12) return addr
        return addr.take(6) + '…' + addr.substring(addr.length() - 4)
    }

    private void writeLevel(String npub, String level, String actor, String nudge) {
        companyConfigService.put(npub, KEY_LEVEL, level, CAT, 'string')
        companyConfigService.put(npub, KEY_SET_AT, isoNow(), CAT, 'string')
        companyConfigService.put(npub, KEY_SET_BY, (actor ?: 'SYSTEM').toString(), CAT, 'string')
        companyConfigService.put(npub, KEY_TRAINING, nudge, CAT, 'string')
    }

    private static int rank(String level) {
        switch (normalizeLevel(level)) {
            case LEVEL_BTCPAY: return 3
            case LEVEL_SINGLE_ADDRESS: return 2
            default: return 1
        }
    }

    private boolean isDemo(String npub) {
        LegalEntity le = LegalEntity.findByNpubOwner(npub)
        return le != null && le.isDemo()
    }

    private static String isoNow() {
        java.time.Instant.now().toString()
    }

    private static String requireNpub(String npubOwner) {
        if (!npubOwner?.trim()) throw new IllegalArgumentException('npubOwner is required')
        npubOwner.trim()
    }
}
