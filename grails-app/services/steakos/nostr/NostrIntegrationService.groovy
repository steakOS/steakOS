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

package steakos.nostr

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.FieldEncryptionService
import steakos.User
import steakos.configuration.CompanyConfigService
import steakos.configuration.LegalEntity

/**
 * OI-NOSTR01 — Nostr integration level + relay settings (D9, D14, D15).
 * Levels: DEFERRED (0) | LOGIN_AUDIT (1) | LOGIN_AUDIT_NOTIFY (2).
 */
@Slf4j
@Transactional
class NostrIntegrationService {

    static final String CAT = 'nostr'

    static final String LEVEL_DEFERRED = 'DEFERRED'
    static final String LEVEL_LOGIN_AUDIT = 'LOGIN_AUDIT'
    static final String LEVEL_LOGIN_AUDIT_NOTIFY = 'LOGIN_AUDIT_NOTIFY'

    static final List LEVELS = [LEVEL_DEFERRED, LEVEL_LOGIN_AUDIT, LEVEL_LOGIN_AUDIT_NOTIFY].asImmutable()

    static final String KEY_LEVEL = 'nostr.integrationLevel'
    static final String KEY_SET_AT = 'nostr.integrationLevelSetAt'
    static final String KEY_SET_BY = 'nostr.integrationLevelSetBy'
    static final String KEY_RELAY_URL = 'nostr.relayUrl'
    static final String KEY_OPERATOR_NPUB = 'nostr.operatorNpub'
    static final String KEY_BOT_NSEC = 'nostr.companyBotNsec'
    static final String KEY_BOT_NPUB = 'nostr.companyBotNpub'
    static final String KEY_NUDGE = 'nostr.enableNudge'

    static final String DEFAULT_RELAY_URL = 'ws://127.0.0.1:7777'

    static final String ERR_ADMIN_REQUIRED = 'NOSTR_LEVEL_ADMIN_REQUIRED'
    static final String ERR_UNKNOWN_LEVEL = 'NOSTR_LEVEL_UNKNOWN'
    static final String ERR_DEMO_PUBLISH = 'DEMO_NOSTR_PUBLISH_BLOCKED'
    static final String ERR_NPUB_INVALID = 'NOSTR_NPUB_INVALID'
    static final String ERR_NSEC_REJECTED = 'NOSTR_NSEC_IN_FORM_REJECTED'

    CompanyConfigService companyConfigService
    FieldEncryptionService fieldEncryptionService

    String ensureMigrated(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String existing = companyConfigService.getString(npub, KEY_LEVEL, null)
        if (existing?.trim()) {
            return normalizeLevel(existing)
        }
        writeLevel(npub, LEVEL_DEFERRED, 'SYSTEM')
        String relay = companyConfigService.getString(npub, KEY_RELAY_URL, null)
        if (!relay?.trim()) {
            companyConfigService.put(npub, KEY_RELAY_URL, DEFAULT_RELAY_URL, CAT, 'string')
        }
        LEVEL_DEFERRED
    }

    String currentLevel(String npubOwner) {
        ensureMigrated(npubOwner)
    }

    Map publicSettings(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String level = ensureMigrated(npub)
        boolean demo = isDemo(npub)
        String relay = companyConfigService.getString(npub, KEY_RELAY_URL, DEFAULT_RELAY_URL) ?: DEFAULT_RELAY_URL
        String opNpub = companyConfigService.getString(npub, KEY_OPERATOR_NPUB, null)
        String botNpub = companyConfigService.getString(npub, KEY_BOT_NPUB, null)
        boolean botConfigured = companyConfigService.getString(npub, KEY_BOT_NSEC, null) as boolean
        [
                level              : level,
                uiLevel            : uiLevelNumber(level),
                uiLabel            : uiLabel(level),
                setAt              : companyConfigService.getString(npub, KEY_SET_AT, null),
                setBy              : companyConfigService.getString(npub, KEY_SET_BY, null),
                relayUrl           : relay,
                operatorNpub       : opNpub,
                operatorNpubMasked : maskNpub(opNpub),
                companyBotNpub     : botNpub,
                companyBotNpubMasked: maskNpub(botNpub),
                companyBotConfigured: botConfigured,
                demoNoPublicPublish: demo,
                publishAllowed     : allowsPublish(npub),
                notifyAllowed      : allowsNotify(npub),
                enableNudge        : level == LEVEL_DEFERRED,
                publicNostrDefault : false
        ]
    }

    /**
     * Admin sets level / relay / optional public operator npub paste (never nsec — D8).
     */
    Map updateSettings(String npubOwner, Map body, String actorUsername, boolean actorIsAdmin) {
        String npub = requireNpub(npubOwner)
        if (!actorIsAdmin) {
            throw new IllegalArgumentException("${ERR_ADMIN_REQUIRED}: only Admin may change Nostr integration settings")
        }
        rejectNsecInBody(body)

        if (body.containsKey('relayUrl') || body.containsKey('nostrRelayUrl')) {
            String url = (body.relayUrl ?: body.nostrRelayUrl)?.toString()?.trim()
            if (url) {
                validateRelayUrl(url)
                companyConfigService.put(npub, KEY_RELAY_URL, url, CAT, 'string')
            }
        }

        if (body.containsKey('operatorNpub') || body.containsKey('publicNpub')) {
            String pasted = (body.operatorNpub ?: body.publicNpub)?.toString()?.trim()
            if (pasted) {
                validatePublicNpub(pasted)
                companyConfigService.put(npub, KEY_OPERATOR_NPUB, pasted, CAT, 'string')
            } else if (pasted == '') {
                companyConfigService.put(npub, KEY_OPERATOR_NPUB, '', CAT, 'string')
            }
        }

        // Sealed company bot nsec — API only, never wizard forms (D8 / D11 mix). Empty clears.
        if (body.containsKey('companyBotNsec')) {
            String raw = body.companyBotNsec?.toString()?.trim()
            if (!raw) {
                companyConfigService.put(npub, KEY_BOT_NSEC, '', CAT, 'string')
            } else {
                if (!(raw.toLowerCase().startsWith('nsec1') || raw ==~ /^[0-9a-fA-F]{64}$/)) {
                    throw new IllegalArgumentException("${ERR_NSEC_REJECTED}: companyBotNsec must be nsec1… or 64-hex (Admin API only)")
                }
                String sealed = fieldEncryptionService ? fieldEncryptionService.encrypt(raw) : raw
                companyConfigService.put(npub, KEY_BOT_NSEC, sealed, CAT, 'string')
            }
        }
        if (body.containsKey('companyBotNpub')) {
            String bp = body.companyBotNpub?.toString()?.trim()
            if (bp) {
                validatePublicNpub(bp)
                companyConfigService.put(npub, KEY_BOT_NPUB, bp, CAT, 'string')
            }
        }

        if (body.containsKey('level') || body.containsKey('nostrIntegrationLevel') || body.containsKey('integrationLevel')) {
            String target = normalizeLevel(body.level ?: body.nostrIntegrationLevel ?: body.integrationLevel)
            if (!target) {
                throw new IllegalArgumentException("${ERR_UNKNOWN_LEVEL}: level must be one of ${LEVELS}")
            }
            // DEMO may store Level 1/2 config but publish path stays mock-safe (D4)
            writeLevel(npub, target, actorUsername ?: 'admin')
        }

        publicSettings(npub)
    }

    /**
     * Wizard apply — Admin-only step content already gated in SPA (D10).
     * Seeds level + optional operator npub + relay URL. Never accepts nsec (D8).
     */
    Map applyFromWizard(String npubOwner, Map draft, String actorUsername) {
        String npub = requireNpub(npubOwner)
        rejectNsecInBody(draft)

        String raw = (draft.nostrIntegrationLevel ?: draft.integrationLevel)?.toString()
        String level = raw ? normalizeLevel(raw) : LEVEL_DEFERRED
        if (!level) level = LEVEL_DEFERRED

        String relay = (draft.nostrRelayUrl ?: draft.relayUrl)?.toString()?.trim()
        if (relay) {
            validateRelayUrl(relay)
            companyConfigService.put(npub, KEY_RELAY_URL, relay, CAT, 'string')
        } else if (!companyConfigService.getString(npub, KEY_RELAY_URL, null)?.trim()) {
            companyConfigService.put(npub, KEY_RELAY_URL, DEFAULT_RELAY_URL, CAT, 'string')
        }

        String pasted = (draft.nostrOperatorNpub ?: draft.operatorNpub ?: draft.publicNpub)?.toString()?.trim()
        if (pasted) {
            validatePublicNpub(pasted)
            companyConfigService.put(npub, KEY_OPERATOR_NPUB, pasted, CAT, 'string')
        }

        writeLevel(npub, level, actorUsername ?: 'admin')
        publicSettings(npub)
    }

    /** TCP / HTTP upgrade probe stub — honest about reachability. */
    Map probeRelay(String npubOwner) {
        String npub = requireNpub(npubOwner)
        ensureMigrated(npub)
        String url = companyConfigService.getString(npub, KEY_RELAY_URL, DEFAULT_RELAY_URL) ?: DEFAULT_RELAY_URL
        Map out = [
                relayUrl : url,
                ok       : false,
                reachable: false,
                detail   : null,
                probedAt : isoNow()
        ]
        try {
            URI uri = URI.create(url.replaceFirst(/^ws:/, 'http:').replaceFirst(/^wss:/, 'https:'))
            String host = uri.host ?: '127.0.0.1'
            int port = uri.port > 0 ? uri.port : (url.startsWith('wss') ? 443 : 7777)
            Socket s = new Socket()
            try {
                s.connect(new InetSocketAddress(host, port), 2000)
                out.reachable = true
                out.ok = true
                out.detail = "TCP connect ${host}:${port} ok (WebSocket AUTH/EVENT not verified in probe stub)"
            } finally {
                try { s.close() } catch (ignored) {}
            }
        } catch (Exception e) {
            out.detail = "unreachable: ${e.message}"
            log.info('Nostr relay probe failed for {}: {}', url, e.message)
        }
        out
    }

    boolean allowsPublish(String npubOwner) {
        String npub = requireNpub(npubOwner)
        if (isDemo(npub)) return false
        String level = ensureMigrated(npub)
        level in [LEVEL_LOGIN_AUDIT, LEVEL_LOGIN_AUDIT_NOTIFY]
    }

    boolean allowsNotify(String npubOwner) {
        String npub = requireNpub(npubOwner)
        if (isDemo(npub)) return false
        ensureMigrated(npub) == LEVEL_LOGIN_AUDIT_NOTIFY
    }

    /** Decrypted company bot nsec or null. */
    String decryptedBotNsec(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String sealed = companyConfigService.getString(npub, KEY_BOT_NSEC, null)
        if (!sealed?.trim()) return null
        try {
            return fieldEncryptionService ? fieldEncryptionService.decrypt(sealed) : sealed
        } catch (Exception e) {
            log.warn('company bot nsec decrypt failed: {}', e.message)
            return null
        }
    }

    /** Linked npub on current user if any (hex or npub). */
    static String linkedNpubForUser(User u) {
        if (!u?.npub) return null
        String p = u.npub.trim()
        if (!p || p.startsWith('user:') || p.startsWith('company:')) return null
        p
    }

    static int uiLevelNumber(String level) {
        switch (normalizeLevel(level)) {
            case LEVEL_LOGIN_AUDIT: return 1
            case LEVEL_LOGIN_AUDIT_NOTIFY: return 2
            default: return 0
        }
    }

    static String uiLabel(String level) {
        switch (normalizeLevel(level)) {
            case LEVEL_LOGIN_AUDIT: return '1 — Login + private audit'
            case LEVEL_LOGIN_AUDIT_NOTIFY: return '2 — Login + audit + notify'
            default: return '0 — Not now'
        }
    }

    static String normalizeLevel(Object raw) {
        if (raw == null) return null
        String s = raw.toString().trim().toUpperCase()
        if (!s) return null
        if (s in ['0', 'L0', 'LEVEL0', 'LEVEL_0', 'DEFER', 'NONE', 'SKIP', 'NOT_NOW']) return LEVEL_DEFERRED
        if (s in ['1', 'L1', 'LEVEL1', 'LEVEL_1', 'AUDIT', 'LOGIN_AUDIT']) return LEVEL_LOGIN_AUDIT
        if (s in ['2', 'L2', 'LEVEL2', 'LEVEL_2', 'NOTIFY', 'LOGIN_AUDIT_NOTIFY']) return LEVEL_LOGIN_AUDIT_NOTIFY
        if (s in LEVELS) return s
        null
    }

    static void validateRelayUrl(String url) {
        String u = url?.trim()
        if (!u || !(u.startsWith('ws://') || u.startsWith('wss://'))) {
            throw new IllegalArgumentException('relayUrl must start with ws:// or wss://')
        }
    }

    static void validatePublicNpub(String value) {
        String v = value?.trim()
        if (!v) return
        String lower = v.toLowerCase()
        if (lower.startsWith('nsec') || lower.contains('nsec1')) {
            throw new IllegalArgumentException("${ERR_NSEC_REJECTED}: never paste nsec — public npub / hex only (D8)")
        }
        boolean ok = lower.startsWith('npub1') || (v ==~ /^[0-9a-fA-F]{64}$/)
        if (!ok) {
            throw new IllegalArgumentException("${ERR_NPUB_INVALID}: expected npub1… or 64-char hex pubkey")
        }
    }

    static void rejectNsecInBody(Map body) {
        if (!body) return
        body.each { k, v ->
            String key = k?.toString()?.toLowerCase() ?: ''
            if (key.contains('nsec') && key != 'companybotnsec') {
                // companyBotNsec is Admin API only — still reject wizard-ish keys
                if (key in ['nsec', 'nostrnsec', 'privatensec', 'secretkey', 'privatekey']) {
                    throw new IllegalArgumentException("${ERR_NSEC_REJECTED}: never submit nsec in forms (D8)")
                }
            }
            if (v instanceof CharSequence) {
                String s = v.toString().trim().toLowerCase()
                if (s.startsWith('nsec1') && key != 'companybotnsec') {
                    throw new IllegalArgumentException("${ERR_NSEC_REJECTED}: never paste nsec (D8)")
                }
            }
        }
    }

    static String maskNpub(String npub) {
        if (!npub || npub.length() < 12) return npub
        return npub.take(8) + '…' + npub.substring(npub.length() - 4)
    }

    private void writeLevel(String npub, String level, String actor) {
        companyConfigService.put(npub, KEY_LEVEL, level, CAT, 'string')
        companyConfigService.put(npub, KEY_SET_AT, isoNow(), CAT, 'string')
        companyConfigService.put(npub, KEY_SET_BY, (actor ?: 'SYSTEM').toString(), CAT, 'string')
        companyConfigService.put(npub, KEY_NUDGE, level == LEVEL_DEFERRED ? 'true' : 'false', CAT, 'boolean')
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
