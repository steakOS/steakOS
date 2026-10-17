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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Telegram Bot API sendMessage via java.net.http.
 */
@Slf4j
class HttpTelegramTransport implements TelegramTransport {

    int timeoutMs = 15000

    private final HttpClient http
    private final JsonSlurper slurper = new JsonSlurper()

    HttpTelegramTransport() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs as long))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
    }

    @Override
    Map sendMessage(String apiBase, String botToken, String chatId, String text) {
        String base = (apiBase ?: MessagingKeys.DEFAULT_TELEGRAM_API_BASE).replaceAll(/\/+$/, '')
        String url = "${base}/bot${botToken}/sendMessage"
        String json = JsonOutput.toJson([chat_id: chatId, text: text])
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs as long))
                .header('Content-Type', 'application/json')
                .header('Accept', 'application/json')
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build()
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        int code = resp.statusCode()
        Object body
        try {
            body = slurper.parseText(resp.body() ?: '{}')
        } catch (Exception e) {
            throw new IOException("Telegram non-JSON response HTTP ${code}: ${resp.body()}")
        }
        if (code < 200 || code >= 300) {
            throw new IOException("Telegram HTTP ${code}: ${body}")
        }
        if (!(body instanceof Map) || !body.ok) {
            throw new IOException("Telegram API error: ${body}")
        }
        return body as Map
    }
}
