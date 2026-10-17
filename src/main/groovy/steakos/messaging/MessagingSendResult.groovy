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
 * Outcome of one outbound adapter attempt.
 */
class MessagingSendResult {

    static final String STATUS_SENT = 'sent'
    static final String STATUS_SKIPPED = 'skipped'
    static final String STATUS_FAILED = 'failed'

    String channel
    String status
    String reason
    String messageId
    Map detail = [:]

    boolean isSent() { status == STATUS_SENT }
    boolean isSkipped() { status == STATUS_SKIPPED }
    boolean isFailed() { status == STATUS_FAILED }

    Map toMap() {
        [
                channel  : channel,
                status   : status,
                reason   : reason,
                messageId: messageId,
                detail   : detail ?: [:]
        ]
    }

    static MessagingSendResult sent(String channel, String messageId = null, Map detail = [:]) {
        new MessagingSendResult(channel: channel, status: STATUS_SENT, messageId: messageId, detail: detail ?: [:])
    }

    static MessagingSendResult skipped(String channel, String reason, Map detail = [:]) {
        new MessagingSendResult(channel: channel, status: STATUS_SKIPPED, reason: reason, detail: detail ?: [:])
    }

    static MessagingSendResult failed(String channel, String reason, Map detail = [:]) {
        new MessagingSendResult(channel: channel, status: STATUS_FAILED, reason: reason, detail: detail ?: [:])
    }
}
