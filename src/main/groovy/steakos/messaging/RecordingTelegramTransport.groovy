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
 * In-memory Telegram stub — records sendMessage calls without network I/O.
 */
class RecordingTelegramTransport implements TelegramTransport {

    final List<Map> sent = Collections.synchronizedList([])

    @Override
    Map sendMessage(String apiBase, String botToken, String chatId, String text) {
        Map rec = [
                apiBase : apiBase,
                chatId  : chatId,
                text    : text,
                // token redacted
                hasToken: !!botToken
        ]
        sent << rec
        long mid = sent.size()
        return [ok: true, result: [message_id: mid]]
    }

    void clear() {
        sent.clear()
    }
}
