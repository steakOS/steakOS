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
 * Outbound channel adapter port (ADR-022).
 * Implementations no-op when the tenant flag is off or credentials are missing.
 */
interface MessagingAdapter {

    /** Channel id: {@link MessagingKeys#CHANNEL_EMAIL} or {@link MessagingKeys#CHANNEL_TELEGRAM}. */
    String getChannel()

    /** Tenant opt-in flag (default false). */
    boolean isEnabled(String npubOwner)

    /** Secrets / host config present enough to attempt a send. */
    boolean isConfigured(String npubOwner)

    /**
     * Send a notification.
     * @param npubOwner tenant
     * @param templateId logical template / event name (e.g. finance.invoice.overdue)
     * @param payload channel-agnostic fields: to, subject, text, chatId, …
     */
    MessagingSendResult send(String npubOwner, String templateId, Map payload)

    /** Lightweight health for Integrations UI / probes. */
    Map health(String npubOwner)
}
