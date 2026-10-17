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
import steakos.FieldEncryptionService
import steakos.configuration.CompanyConfigService

/**
 * Email MessagingAdapter — SMTP when enabled+configured; no-op when disabled.
 * Inject {@link RecordingEmailTransport} in unit tests; production uses {@link SmtpEmailTransport}.
 */
@Slf4j
class EmailSmtpSender implements MessagingAdapter {

    CompanyConfigService companyConfigService
    FieldEncryptionService fieldEncryptionService

    /** Override for tests / stub mode. Null → {@link SmtpEmailTransport}. */
    EmailTransport emailTransport

    @Override
    String getChannel() { MessagingKeys.CHANNEL_EMAIL }

    @Override
    boolean isEnabled(String npubOwner) {
        companyConfigService.getBoolean(requireNpub(npubOwner), MessagingKeys.EMAIL_ENABLED, false)
    }

    @Override
    boolean isConfigured(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String host = companyConfigService.getString(npub, MessagingKeys.EMAIL_SMTP_HOST, null)?.trim()
        String from = companyConfigService.getString(npub, MessagingKeys.EMAIL_FROM, null)?.trim()
        return !!(host && from)
    }

    @Override
    MessagingSendResult send(String npubOwner, String templateId, Map payload) {
        String npub = requireNpub(npubOwner)
        if (!isEnabled(npub)) {
            return MessagingSendResult.skipped(channel, 'disabled')
        }
        if (!isConfigured(npub)) {
            return MessagingSendResult.skipped(channel, 'not_configured')
        }
        String to = payload?.to?.toString()?.trim()
        if (!to) {
            return MessagingSendResult.failed(channel, 'missing_to')
        }
        String subject = (payload?.subject ?: templateId ?: 'steakOS notification').toString()
        String text = (payload?.text ?: payload?.body ?: '').toString()
        if (!text && payload) {
            text = payload.findAll { k, v -> !(k in ['to', 'subject', 'body', 'text', 'chatId']) }
                    .collect { k, v -> "${k}: ${v}" }
                    .join('\n')
        }

        Map msg = [
                host    : companyConfigService.getString(npub, MessagingKeys.EMAIL_SMTP_HOST, null),
                port    : companyConfigService.getString(npub, MessagingKeys.EMAIL_SMTP_PORT, MessagingKeys.DEFAULT_SMTP_PORT) ?: MessagingKeys.DEFAULT_SMTP_PORT,
                username: companyConfigService.getString(npub, MessagingKeys.EMAIL_USERNAME, null),
                password: decryptSecret(npub, MessagingKeys.EMAIL_PASSWORD),
                from    : companyConfigService.getString(npub, MessagingKeys.EMAIL_FROM, null),
                to      : to,
                subject : subject,
                text    : text,
                startTls: true
        ]
        try {
            String mid = resolveTransport().deliver(msg)
            return MessagingSendResult.sent(channel, mid, [templateId: templateId, to: to])
        } catch (Exception e) {
            log.warn("email send failed npub=${npub} template=${templateId}: ${e.message}")
            return MessagingSendResult.failed(channel, e.message ?: 'send_failed', [templateId: templateId])
        }
    }

    @Override
    Map health(String npubOwner) {
        String npub = requireNpub(npubOwner)
        boolean enabled = isEnabled(npub)
        boolean configured = isConfigured(npub)
        [
                channel   : channel,
                enabled   : enabled,
                configured: configured,
                ready     : enabled && configured,
                host      : companyConfigService.getString(npub, MessagingKeys.EMAIL_SMTP_HOST, null),
                from      : companyConfigService.getString(npub, MessagingKeys.EMAIL_FROM, null),
                transport : (emailTransport ?: new SmtpEmailTransport()).getClass().simpleName
        ]
    }

    private EmailTransport resolveTransport() {
        emailTransport ?: new SmtpEmailTransport()
    }

    private String decryptSecret(String npub, String key) {
        String raw = companyConfigService.getString(npub, key, null)
        if (!raw) return null
        if (!fieldEncryptionService) return raw
        try {
            return fieldEncryptionService.decrypt(raw)
        } catch (Exception e) {
            // Allow plaintext in early/dev configs
            log.debug("email secret decrypt failed; using raw: ${e.message}")
            return raw
        }
    }

    private static String requireNpub(String npubOwner) {
        String npub = npubOwner?.toString()?.trim()
        if (!npub) throw new IllegalArgumentException('npubOwner is required')
        return npub
    }
}
