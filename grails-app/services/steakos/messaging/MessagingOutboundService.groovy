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

package steakos.messaging

import groovy.util.logging.Slf4j
import jakarta.annotation.PostConstruct
import steakos.FieldEncryptionService
import steakos.configuration.CompanyConfigService
import steakos.events.DomainEvent
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames

/**
 * Outbound messaging façade (ADR-022 / Silver PR-8).
 * Routes to email / Telegram adapters when tenant flags are on and configured;
 * no-ops when disabled (defaults false). Publishes {@code messaging.outbound.sent} on success.
 */
@Slf4j
class MessagingOutboundService {

    /** Events that may trigger outbound notifies (Si-M). */
    static final List<String> TRIGGER_EVENTS = [
            DomainEventNames.FINANCE_INVOICE_ISSUED,
            DomainEventNames.FINANCE_INVOICE_SETTLED,
            DomainEventNames.FINANCE_INVOICE_OVERDUE,
            DomainEventNames.STAKEHOLDER_COMMUNICATION_LOGGED,
            DomainEventNames.AGENT_PROPOSAL_SUBMITTED,
            DomainEventNames.AGENT_PROPOSAL_APPROVED
    ].asImmutable()

    CompanyConfigService companyConfigService
    FieldEncryptionService fieldEncryptionService
    DomainEventBusService domainEventBusService

    /** Injectable adapters (tests). Null → built from config services. */
    EmailSmtpSender emailSmtpSender
    TelegramBotSender telegramBotSender

    private final List<Closure> unsubscribers = []

    @PostConstruct
    void init() {
        ensureAdapters()
        subscribeTriggers()
    }

    void ensureAdapters() {
        if (!emailSmtpSender && companyConfigService) {
            emailSmtpSender = new EmailSmtpSender(
                    companyConfigService: companyConfigService,
                    fieldEncryptionService: fieldEncryptionService
            )
        }
        if (!telegramBotSender && companyConfigService) {
            telegramBotSender = new TelegramBotSender(
                    companyConfigService: companyConfigService,
                    fieldEncryptionService: fieldEncryptionService
            )
        }
    }

    private void subscribeTriggers() {
        if (!domainEventBusService) return
        TRIGGER_EVENTS.each { String name ->
            try {
                unsubscribers << domainEventBusService.subscribe(name) { DomainEvent e ->
                    onDomainEvent(e)
                }
            } catch (Exception e) {
                log.warn("messaging subscribe failed for ${name}: ${e.message}")
            }
        }
        log.info("messaging outbound subscribed to ${TRIGGER_EVENTS.size()} trigger events")
    }

    /** Explicit notify on one channel. */
    MessagingSendResult send(String npubOwner, String channel, String templateId, Map payload = [:]) {
        ensureAdapters()
        MessagingAdapter adapter = adapterFor(channel)
        if (!adapter) {
            return MessagingSendResult.failed(channel ?: 'unknown', 'unknown_channel')
        }
        MessagingSendResult result = adapter.send(npubOwner, templateId, payload ?: [:])
        publishAudit(npubOwner, result, templateId)
        return result
    }

    /**
     * Fan-out to every enabled+configured channel.
     * Returns one result per attempted channel (skipped channels omitted unless {@code includeSkipped}).
     */
    List<MessagingSendResult> notifyAll(String npubOwner, String templateId, Map payload = [:],
                                        boolean includeSkipped = false) {
        ensureAdapters()
        List<MessagingSendResult> out = []
        adapters().each { MessagingAdapter a ->
            MessagingSendResult r = a.send(npubOwner, templateId, payload ?: [:])
            if (includeSkipped || !r.isSkipped()) {
                out << r
            }
            publishAudit(npubOwner, r, templateId)
        }
        return out
    }

    Map health(String npubOwner) {
        ensureAdapters()
        [
                email   : emailSmtpSender?.health(npubOwner),
                telegram: telegramBotSender?.health(npubOwner)
        ]
    }

    /** Domain-event → outbound (Si-M). Skips when all channels disabled. */
    void onDomainEvent(DomainEvent event) {
        if (!event?.npubOwner) {
            log.debug("messaging skip event without npubOwner name=${event?.name}")
            return
        }
        ensureAdapters()
        Map payload = buildPayloadFromEvent(event)
        notifyAll(event.npubOwner, event.name, payload, false)
    }

    private Map buildPayloadFromEvent(DomainEvent event) {
        Map p = new LinkedHashMap(event.payload ?: [:])
        if (!p.subject) {
            p.subject = "steakOS: ${event.name}"
        }
        if (!p.text && !p.body) {
            p.text = "Event ${event.name}\n" + (event.payload ?: [:]).collect { k, v -> "${k}=${v}" }.join('\n')
        }
        // Prefer explicit recipient fields from publishers when present
        if (!p.to && p.email) p.to = p.email
        if (!p.chatId && p.telegramChatId) p.chatId = p.telegramChatId
        return p
    }

    private void publishAudit(String npubOwner, MessagingSendResult result, String templateId) {
        if (!result?.isSent() || !domainEventBusService) return
        try {
            domainEventBusService.publish(
                    DomainEventNames.MESSAGING_OUTBOUND_SENT,
                    'messaging',
                    npubOwner,
                    [
                            channel   : result.channel,
                            templateId: templateId,
                            messageId : result.messageId,
                            status    : result.status
                    ]
            )
        } catch (Exception e) {
            log.warn("messaging.outbound.sent publish failed: ${e.message}")
        }
    }

    private MessagingAdapter adapterFor(String channel) {
        switch (channel?.toLowerCase()) {
            case MessagingKeys.CHANNEL_EMAIL: return emailSmtpSender
            case MessagingKeys.CHANNEL_TELEGRAM: return telegramBotSender
            default: return null
        }
    }

    private List<MessagingAdapter> adapters() {
        [emailSmtpSender, telegramBotSender].findAll { it != null }
    }
}
