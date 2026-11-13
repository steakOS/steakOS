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
 * Telegram MessagingAdapter — Bot API when enabled+configured; no-op when disabled.
 * Inject {@link RecordingTelegramTransport} in unit tests; production uses {@link HttpTelegramTransport}.
 */
@Slf4j
class TelegramBotSender implements MessagingAdapter {

    CompanyConfigService companyConfigService
    FieldEncryptionService fieldEncryptionService

    /** Override for tests / stub mode. Null → {@link HttpTelegramTransport}. */
    TelegramTransport telegramTransport

    @Override
    String getChannel() { MessagingKeys.CHANNEL_TELEGRAM }

    @Override
    boolean isEnabled(String npubOwner) {
        companyConfigService.getBoolean(requireNpub(npubOwner), MessagingKeys.TELEGRAM_ENABLED, false)
    }

    @Override
    boolean isConfigured(String npubOwner) {
        String npub = requireNpub(npubOwner)
        String token = companyConfigService.getString(npub, MessagingKeys.TELEGRAM_BOT_TOKEN, null)?.trim()
        String chatId = companyConfigService.getString(npub, MessagingKeys.TELEGRAM_CHAT_ID, null)?.trim()
        return !!(token && chatId)
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
        String chatId = (payload?.chatId ?: companyConfigService.getString(npub, MessagingKeys.TELEGRAM_CHAT_ID, null))?.toString()?.trim()
        if (!chatId) {
            return MessagingSendResult.failed(channel, 'missing_chatId')
        }
        String text = (payload?.text ?: payload?.body ?: '').toString()
        if (!text) {
            String subject = payload?.subject?.toString()
            text = subject ? "${subject}\n${payload?.to ?: ''}".trim() : (templateId ?: 'steakOS notification')
        }
        String token = decryptSecret(npub, MessagingKeys.TELEGRAM_BOT_TOKEN)
        String apiBase = companyConfigService.getString(npub, MessagingKeys.TELEGRAM_API_BASE, MessagingKeys.DEFAULT_TELEGRAM_API_BASE)
                ?: MessagingKeys.DEFAULT_TELEGRAM_API_BASE
        try {
            Map resp = resolveTransport().sendMessage(apiBase, token, chatId, text)
            String mid = resp?.result?.message_id?.toString()
            return MessagingSendResult.sent(channel, mid, [templateId: templateId, chatId: chatId])
        } catch (Exception e) {
            log.warn("telegram send failed npub=${npub} template=${templateId}: ${e.message}")
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
                apiBase   : companyConfigService.getString(npub, MessagingKeys.TELEGRAM_API_BASE, MessagingKeys.DEFAULT_TELEGRAM_API_BASE),
                chatId    : companyConfigService.getString(npub, MessagingKeys.TELEGRAM_CHAT_ID, null),
                transport : (telegramTransport ?: new HttpTelegramTransport()).getClass().simpleName
        ]
    }

    private TelegramTransport resolveTransport() {
        telegramTransport ?: new HttpTelegramTransport()
    }

    private String decryptSecret(String npub, String key) {
        String raw = companyConfigService.getString(npub, key, null)
        if (!raw) return null
        if (!fieldEncryptionService) {
            throw new IllegalStateException('Field encryption is not available')
        }
        try {
            return fieldEncryptionService.decrypt(raw)
        } catch (steakos.security.FieldDecryptionException e) {
            throw e
        } catch (Exception ignored) {
            if (System.getenv('STEAKOS_ENCRYPTION_ALLOW_LEGACY_PLAINTEXT') in ['true', '1'] &&
                    !steakos.security.CiphertextHints.looksLikeCiphertext(raw)) {
                return raw
            }
            log.warn('telegram secret could not be decrypted')
            throw new steakos.security.FieldDecryptionException('telegram secret could not be decrypted')
        }
    }

    private static String requireNpub(String npubOwner) {
        String npub = npubOwner?.toString()?.trim()
        if (!npub) throw new IllegalArgumentException('npubOwner is required')
        return npub
    }
}
