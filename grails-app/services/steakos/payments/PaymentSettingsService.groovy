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
import org.springframework.core.env.Environment
import steakos.FieldEncryptionService
import steakos.configuration.CompanyConfigService
import steakos.configuration.CompanyContextService
import steakos.configuration.LegalEntity

/**
 * Per-tenant payment rail settings (OI-F27 / Phase 1). Secrets encrypted at rest.
 * A2/D3: DEMO companies always resolve to MOCK (Reset-safe training).
 * OI-BTC01: live BTCPay only when integration level is BTCPAY.
 */
@Slf4j
@Transactional
class PaymentSettingsService {

    static final String CAT = 'payments'
    static final String KEY_PROVIDER = 'payments.provider'
    static final String KEY_LIVE = 'payments.liveEnabled'
    static final String KEY_BASE_URL = 'btcpay.baseUrl'
    static final String KEY_STORE_ID = 'btcpay.storeId'
    static final String KEY_API_KEY = 'btcpay.apiKey'
    static final String KEY_WEBHOOK_SECRET = 'btcpay.webhookSecret'
    static final String KEY_DEFAULT_CCY = 'btcpay.defaultCurrency'

    static final String PROVIDER_MOCK = 'MOCK'
    static final String PROVIDER_BTCPAY = 'BTCPAY'
    static final String ERR_DEMO_MOCK_ONLY = 'DEMO_PAYMENTS_MOCK_ONLY'
    static final String ERR_LEVEL_NOT_BTCPAY = 'BTC_LEVEL_NOT_BTCPAY'

    CompanyConfigService companyConfigService
    FieldEncryptionService fieldEncryptionService
    Environment environment
    BtcIntegrationLevelService btcIntegrationLevelService

    /**
     * Public settings map — never includes raw secrets.
     */
    Map publicSettings(String npubOwner) {
        String npub = requireNpub(npubOwner)
        boolean demoForced = isDemoCompany(npub)
        String provider = demoForced ? PROVIDER_MOCK : companyConfigService.getString(npub, KEY_PROVIDER, PROVIDER_MOCK)
        boolean live = demoForced ? false : companyConfigService.getBoolean(npub, KEY_LIVE, false)
        Map cfg = decryptedBtcpayConfig(npub)
        boolean ready = isBtcpayLiveReady(npub)
        Map level = [:]
        try {
            level = btcIntegrationLevelService?.publicLevel(npub) ?: [:]
        } catch (Exception e) {
            log.debug('btc level read failed: {}', e.message)
        }
        String note
        if (demoForced) {
            note = 'Demo company: mock settle only (A2/D3) — live BTCPay is for LIVE companies'
        } else if (level.level == BtcIntegrationLevelService.LEVEL_DEFERRED) {
            note = 'Bitcoin receive deferred (Level 1) — mock settle only; upgrade in Settings or wizard'
        } else if (level.level == BtcIntegrationLevelService.LEVEL_SINGLE_ADDRESS) {
            note = 'Level 2 single-address receive — manual mark paid; upgrade to BTCPay for Lightning'
        } else if (ready) {
            note = 'BTCPay Greenfield live (Phase 1 receive)'
        } else if (provider == PROVIDER_BTCPAY && live) {
            note = 'BTCPay selected but incomplete config (baseUrl, storeId, apiKey required)'
        } else {
            note = 'Mock provider is default for Iron / demo (OI-A08)'
        }
        [
                npubOwner       : npub,
                provider        : provider,
                liveEnabled     : live,
                demoForcedMock  : demoForced,
                btcpay          : [
                        baseUrl          : cfg.baseUrl,
                        storeId          : cfg.storeId,
                        apiKeyConfigured : cfg.apiKey as boolean,
                        webhookConfigured: cfg.webhookSecret as boolean,
                        defaultCurrency  : cfg.defaultCurrency ?: 'EUR'
                ],
                liveReady       : ready,
                activeProvider  : resolvedProviderCode(npub),
                btcIntegration  : level,
                note            : note
        ]
    }

    /**
     * Update settings. apiKey / webhookSecret only written when non-blank (write-only).
     * DEMO tenants cannot enable BTCPAY or liveEnabled.
     */
    Map updateSettings(String npubOwner, Map body) {
        String npub = requireNpub(npubOwner)
        if (isDemoCompany(npub)) {
            boolean wantsBtcpay = body.containsKey('provider') &&
                    body.provider?.toString()?.toUpperCase() == PROVIDER_BTCPAY
            boolean wantsLive = body.containsKey('liveEnabled') &&
                    (body.liveEnabled == true || body.liveEnabled?.toString()?.equalsIgnoreCase('true'))
            if (wantsBtcpay || wantsLive) {
                throw new IllegalArgumentException(
                        "${ERR_DEMO_MOCK_ONLY}: Demo companies use mock settle only — switch to your LIVE company for BTCPay")
            }
            // Ignore provider/live writes; still allow clearing unrelated fields safely as MOCK
            body = new LinkedHashMap(body)
            body.remove('provider')
            body.remove('liveEnabled')
        }
        // OI-BTC01: live BTCPay path only at Level 3
        boolean wantsBtcpayLive = false
        if (body.containsKey('provider') && body.provider?.toString()?.toUpperCase() == PROVIDER_BTCPAY) {
            wantsBtcpayLive = true
        }
        if (body.containsKey('liveEnabled') &&
                (body.liveEnabled == true || body.liveEnabled?.toString()?.equalsIgnoreCase('true'))) {
            wantsBtcpayLive = true
        }
        if (wantsBtcpayLive && !isDemoCompany(npub)) {
            String level = null
            try {
                level = btcIntegrationLevelService?.currentLevel(npub)
            } catch (ignored) {
            }
            if (level && level != BtcIntegrationLevelService.LEVEL_BTCPAY) {
                throw new IllegalArgumentException(
                        "${ERR_LEVEL_NOT_BTCPAY}: set Bitcoin integration level to BTCPay (Level 3) before enabling live BTCPay")
            }
        }
        if (body.containsKey('provider')) {
            String p = body.provider?.toString()?.toUpperCase()
            if (!(p in [PROVIDER_MOCK, PROVIDER_BTCPAY])) {
                throw new IllegalArgumentException("provider must be MOCK or BTCPAY")
            }
            companyConfigService.put(npub, KEY_PROVIDER, p, CAT, 'string')
        }
        if (body.containsKey('liveEnabled')) {
            boolean live = body.liveEnabled == true || body.liveEnabled?.toString()?.equalsIgnoreCase('true')
            companyConfigService.put(npub, KEY_LIVE, live ? 'true' : 'false', CAT, 'boolean')
        }
        Map btcpay = body.btcpay instanceof Map ? body.btcpay as Map : body
        if (btcpay.baseUrl != null) {
            String url = btcpay.baseUrl?.toString()?.trim() ?: ''
            if (url) {
                url = BtcpayUrlValidator.validateAndNormalize(url, allowPrivateUrls())
            }
            companyConfigService.put(npub, KEY_BASE_URL, url, CAT, 'string')
        }
        if (btcpay.storeId != null) {
            companyConfigService.put(npub, KEY_STORE_ID, btcpay.storeId?.toString()?.trim() ?: '', CAT, 'string')
        }
        if (btcpay.defaultCurrency != null) {
            companyConfigService.put(npub, KEY_DEFAULT_CCY, (btcpay.defaultCurrency ?: 'EUR').toString(), CAT, 'string')
        }
        if (btcpay.apiKey?.toString()?.trim()) {
            String enc = fieldEncryptionService.encrypt(btcpay.apiKey.toString().trim())
            companyConfigService.put(npub, KEY_API_KEY, enc, CAT, 'string')
        }
        if (btcpay.webhookSecret?.toString()?.trim()) {
            String enc = fieldEncryptionService.encrypt(btcpay.webhookSecret.toString().trim())
            companyConfigService.put(npub, KEY_WEBHOOK_SECRET, enc, CAT, 'string')
        }
        publicSettings(npub)
    }

    /**
     * Provider used for rail calls: BTCPAY only when Level 3 + fully configured + liveEnabled.
     * DEMO companies always MOCK (A2/D3). Level 1–2 force MOCK (OI-BTC01).
     */
    String resolvedProviderCode(String npubOwner) {
        String npub = requireNpub(npubOwner)
        if (isDemoCompany(npub)) {
            return PROVIDER_MOCK
        }
        if (!levelAllowsLiveBtcpay(npub)) {
            return PROVIDER_MOCK
        }
        String provider = companyConfigService.getString(npub, KEY_PROVIDER, PROVIDER_MOCK)?.toUpperCase()
        boolean live = companyConfigService.getBoolean(npub, KEY_LIVE, false)
        if (provider == PROVIDER_BTCPAY && live && isBtcpayLiveReady(npub)) {
            return PROVIDER_BTCPAY
        }
        return PROVIDER_MOCK
    }

    boolean isBtcpayLiveReady(String npubOwner) {
        String npub = requireNpub(npubOwner)
        if (isDemoCompany(npub)) {
            return false
        }
        if (!levelAllowsLiveBtcpay(npub)) {
            return false
        }
        boolean live = companyConfigService.getBoolean(npub, KEY_LIVE, false)
        String provider = companyConfigService.getString(npub, KEY_PROVIDER, PROVIDER_MOCK)?.toUpperCase()
        if (!live || provider != PROVIDER_BTCPAY) return false
        Map cfg = decryptedBtcpayConfig(npub)
        return cfg.baseUrl && cfg.storeId && cfg.apiKey
    }

    /** Level gate without re-entering migrate→liveReady recursion. */
    private boolean levelAllowsLiveBtcpay(String npub) {
        String stored = companyConfigService.getString(npub, BtcIntegrationLevelService.KEY_LEVEL, null)
        if (stored?.trim()) {
            return BtcIntegrationLevelService.normalizeLevel(stored) == BtcIntegrationLevelService.LEVEL_BTCPAY
        }
        // Unmigrated: treat configured live BTCPay as Level 3 for resolve (migrate fills key on read)
        return true
    }

    /** True when the tenant npub belongs to a DEMO LegalEntity (or known showcase npub). */
    boolean isDemoCompany(String npubOwner) {
        String npub = requireNpub(npubOwner)
        // Known showcase tenants — defend against stale kind=LIVE rows (OI-F01 / L5)
        if (npub == CompanyContextService.DEMO_NPUB || npub == CompanyContextService.HEARD_COOK_NPUB) {
            return true
        }
        LegalEntity le = LegalEntity.findByNpubOwner(npub)
        if (le == null) {
            return false
        }
        if (le.isDemo()) {
            return true
        }
        String code = le.code?.toString()
        return code == LegalEntity.CODE_ORANGE_STEAK || code == LegalEntity.CODE_HEARD_COOK
    }

    Map decryptedBtcpayConfig(String npubOwner) {
        String npub = requireNpub(npubOwner)
        [
                baseUrl        : companyConfigService.getString(npub, KEY_BASE_URL, null),
                storeId        : companyConfigService.getString(npub, KEY_STORE_ID, null),
                apiKey         : decryptOptional(companyConfigService.getString(npub, KEY_API_KEY, null)),
                webhookSecret  : decryptOptional(companyConfigService.getString(npub, KEY_WEBHOOK_SECRET, null)),
                defaultCurrency: companyConfigService.getString(npub, KEY_DEFAULT_CCY, 'EUR')
        ]
    }

    int httpTimeoutMs() {
        environment?.getProperty('steakos.payments.http-timeout-ms', Integer, 15000) ?: 15000
    }

    boolean allowPrivateUrls() {
        environment?.getProperty('steakos.payments.url-allow-private', Boolean, false) ?: false
    }

    /**
     * Probe BTCPay store endpoint with current secrets (does not enable live).
     */
    Map probeConnection(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Map cfg = decryptedBtcpayConfig(npub)
        if (!cfg.baseUrl || !cfg.storeId || !cfg.apiKey) {
            return [ok: false, error: 'baseUrl, storeId, and apiKey required']
        }
        try {
            String base = BtcpayUrlValidator.validateAndNormalize(cfg.baseUrl as String, allowPrivateUrls())
            BtcpayGreenfieldClient client = new BtcpayGreenfieldClient(
                    base, cfg.storeId as String, cfg.apiKey as String, httpTimeoutMs()
            )
            Map store = client.getStore()
            [
                    ok     : true,
                    storeId: store.id ?: cfg.storeId,
                    name   : store.name,
                    baseUrl: base
            ]
        } catch (PaymentProviderException e) {
            [ok: false, error: e.message, code: e.code, httpStatus: e.httpStatus]
        } catch (Exception e) {
            [ok: false, error: e.message]
        }
    }

    private String decryptOptional(String stored) {
        if (!stored?.trim()) return null
        try {
            return fieldEncryptionService.decrypt(stored)
        } catch (Exception e) {
            log.warn('Failed to decrypt payment setting (treating as plain for migration): {}', e.message)
            return stored
        }
    }

    private static String requireNpub(String npubOwner) {
        if (!npubOwner?.trim()) throw new IllegalArgumentException('npubOwner is required')
        npubOwner.trim()
    }
}
