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
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import steakos.configuration.CompanyConfigService
import steakos.traits.NostrPublishable

/**
 * OI-NOSTR01 / PL-TR4 — outbound Nostr publish (D11 mix, D12 audit-first, D16 no publicNostr).
 *
 * <p>Transport: thin JDK WebSocket ({@link NostrRelayWsClient}) + {@code fr.acinq.secp256k1}
 * signing — <b>not</b> {@code xyz.tcheeric:nostr-java-client} (Spring clash). DEMO / Level 0
 * stay no-publish via {@link NostrIntegrationService#allowsPublish}. Residual: NIP-17 giftwrap
 * notify ({@link #publishNotifyStub}).</p>
 */
@Slf4j
@Transactional
class NostrPublishService {

    static final String CAT = 'nostr'
    static final String KEY_LAST_INTENT = 'nostr.lastPublishIntent'
    static final String KEY_PUBLISH_COUNT = 'nostr.publishIntentCount'

    /** Kind used for steakOS private audit notes (local relay allowlist). */
    static final int KIND_AUDIT_NOTE = 1

    NostrIntegrationService nostrIntegrationService
    CompanyConfigService companyConfigService

    /** Injectable for unit tests (mock Session / framing). */
    NostrRelayWsClient nostrRelayWsClient = new NostrRelayWsClient()

    /**
     * Dual-write selected audit events when Level ≥ 1 (LIVE only).
     * Returns map: published|skipped|stubbed|error + reason + optional nostrEventId.
     */
    Map publishAuditEvent(String npubOwner, String summary, Map meta = [:]) {
        String npub = requireNpub(npubOwner)
        Map base = [
                kind   : KIND_AUDIT_NOTE,
                summary: summary?.toString()?.take(500),
                meta   : meta ?: [:],
                at     : java.time.Instant.now().toString()
        ]

        if (!nostrIntegrationService.allowsPublish(npub)) {
            String reason
            if (nostrIntegrationService.publicSettings(npub).demoNoPublicPublish) {
                reason = 'DEMO mock-safe — no public/local publish (D4)'
            } else {
                reason = 'integration level 0 (DEFERRED) — publish skipped'
            }
            log.info('Nostr publish skipped npubOwner={}: {}', npub, reason)
            recordIntent(npub, base + [status: 'skipped', reason: reason])
            return [status: 'skipped', reason: reason, nostrEventId: null]
        }

        String relay = nostrIntegrationService.publicSettings(npub).relayUrl as String
        String botNsec = nostrIntegrationService.decryptedBotNsec(npub)
        if (!botNsec) {
            String reason = 'company bot nsec not configured — audit intent recorded; configure Admin API companyBotNsec for real EVENT'
            log.warn('Nostr publish stub npubOwner={}: {}', npub, reason)
            String stubId = stubEventId(npub, summary)
            recordIntent(npub, base + [status: 'stubbed', reason: reason, relayUrl: relay, stubId: stubId])
            return [
                    status       : 'stubbed',
                    reason       : reason,
                    relayUrl     : relay,
                    nostrEventId : stubId,
                    residual     : 'Set companyBotNsec (Admin API) to enable JDK WebSocket EVENT + NIP-42 AUTH'
            ]
        }

        try {
            String content = summary?.toString()?.take(500) ?: ''
            List tags = [
                    ['client', 'steakOS'],
                    ['steakos', 'audit']
            ]
            if (meta) {
                meta.each { k, v ->
                    if (k && v != null) {
                        tags << ['steakos_meta', k.toString().take(40), v.toString().take(200)]
                    }
                }
            }
            Map transport = nostrRelayWsClient.publishEvent(relay, botNsec, [
                    kind   : KIND_AUDIT_NOTE,
                    content: content,
                    tags   : tags
            ])
            boolean ok = transport.ok as boolean
            String eventId = transport.eventId as String
            if (ok) {
                log.info('Nostr EVENT published npubOwner={} id={} relay={}', npub, eventId, relay)
                recordIntent(npub, base + [
                        status         : 'published',
                        relayUrl       : relay,
                        eventId        : eventId,
                        authenticated  : transport.authenticated
                ])
                return [
                        status       : 'published',
                        reason       : 'EVENT accepted by relay',
                        relayUrl     : relay,
                        nostrEventId : eventId,
                        authenticated: transport.authenticated
                ]
            }
            String detail = (transport.detail ?: 'relay rejected EVENT') as String
            log.warn('Nostr EVENT rejected npubOwner={}: {}', npub, detail)
            recordIntent(npub, base + [status: 'error', reason: detail, relayUrl: relay, eventId: eventId])
            return [
                    status       : 'error',
                    reason       : detail,
                    relayUrl     : relay,
                    nostrEventId : eventId
            ]
        } catch (Exception e) {
            log.warn('Nostr publish failed npubOwner={}: {}', npub, e.message)
            String stubId = stubEventId(npub, summary)
            recordIntent(npub, base + [status: 'error', reason: e.message, relayUrl: relay, stubId: stubId])
            return [
                    status       : 'error',
                    reason       : e.message,
                    relayUrl     : relay,
                    nostrEventId : stubId
            ]
        }
    }

    /**
     * Level 2 in-app notify path — residual until giftwrap (NIP-17/44/59) lands.
     * Still dual-writes an audit-shaped EVENT when bot nsec + Level ≥ 1 allow publish.
     */
    Map publishNotifyStub(String npubOwner, String summary, Map meta = [:]) {
        String npub = requireNpub(npubOwner)
        if (!nostrIntegrationService.allowsNotify(npub)) {
            return [status: 'skipped', reason: 'notify requires Level 2 (LOGIN_AUDIT_NOTIFY) on LIVE']
        }
        Map r = publishAuditEvent(npub, "[notify] ${summary}", meta + [notify: true])
        r.notifyResidual = 'NIP-17 giftwrap send not implemented (D13 send 17 later)'
        r
    }

    /** Attach stub/real event id onto a NostrPublishable domain instance when present. */
    void recordOnDomain(Object domain, Map publishResult) {
        if (!(domain instanceof NostrPublishable)) return
        String id = publishResult?.nostrEventId as String
        if (!id) return
        ((NostrPublishable) domain).recordNostrEvent(id, publishResult.status as String)
        // Caller saves domain
    }

    Map lastIntent(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String raw = companyConfigService.getString(npub, KEY_LAST_INTENT, null)
        if (!raw) return [status: 'none']
        try {
            return new groovy.json.JsonSlurper().parseText(raw) as Map
        } catch (Exception e) {
            return [status: 'error', detail: e.message]
        }
    }

    private void recordIntent(String npub, Map intent) {
        try {
            companyConfigService.put(npub, KEY_LAST_INTENT, JsonOutput.toJson(intent), CAT, 'json')
            int n = 0
            try {
                n = Integer.parseInt(companyConfigService.getString(npub, KEY_PUBLISH_COUNT, '0') ?: '0')
            } catch (ignored) {
            }
            companyConfigService.put(npub, KEY_PUBLISH_COUNT, String.valueOf(n + 1), CAT, 'number')
        } catch (Exception e) {
            log.debug('record publish intent failed: {}', e.message)
        }
    }

    private static String stubEventId(String npub, String summary) {
        String material = "${npub}|${summary}|${System.currentTimeMillis()}"
        material.digest('SHA-256')
    }

    private static String requireNpub(String npubOwner) {
        if (!npubOwner?.trim()) throw new IllegalArgumentException('npubOwner is required')
        npubOwner.trim()
    }
}
