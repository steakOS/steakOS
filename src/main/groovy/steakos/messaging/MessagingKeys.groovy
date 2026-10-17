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

/**
 * CompanySetting keys for outbound messaging (ADR-022 / PR-3 / PR-8).
 * Flag defaults are false — adapters no-op until opt-in + credentials.
 */
final class MessagingKeys {

    private MessagingKeys() {}

    static final String CAT = 'messaging'

    static final String EMAIL_ENABLED = 'messaging.emailEnabled'
    static final String TELEGRAM_ENABLED = 'messaging.telegramEnabled'

    static final String EMAIL_SMTP_HOST = 'messaging.email.smtpHost'
    static final String EMAIL_SMTP_PORT = 'messaging.email.smtpPort'
    static final String EMAIL_USERNAME = 'messaging.email.username'
    static final String EMAIL_PASSWORD = 'messaging.email.password'
    static final String EMAIL_FROM = 'messaging.email.fromAddress'

    static final String TELEGRAM_BOT_TOKEN = 'messaging.telegram.botToken'
    static final String TELEGRAM_CHAT_ID = 'messaging.telegram.chatId'
    static final String TELEGRAM_API_BASE = 'messaging.telegram.apiBase'

    static final String DEFAULT_TELEGRAM_API_BASE = 'https://api.telegram.org'
    static final String DEFAULT_SMTP_PORT = '587'

    static final String CHANNEL_EMAIL = 'email'
    static final String CHANNEL_TELEGRAM = 'telegram'
}
