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

package steakos.payments

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Thin first-party Greenfield HTTP client (Phase 1 / OI-F01).
 * No third-party BTCPay SDK — owned surface for Grails.
 */
@Slf4j
class BtcpayGreenfieldClient {

    final String baseUrl
    final String storeId
    final String apiKey
    final int timeoutMs

    private final HttpClient http
    private final JsonSlurper slurper = new JsonSlurper()

    BtcpayGreenfieldClient(String baseUrl, String storeId, String apiKey, int timeoutMs = 15000) {
        this.baseUrl = baseUrl?.replaceAll(/\/+$/, '')
        this.storeId = storeId
        this.apiKey = apiKey
        this.timeoutMs = timeoutMs
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs as long))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
    }

    Map getStore() {
        getJson("/api/v1/stores/${enc(storeId)}") as Map
    }

    Map createInvoice(Map body) {
        postJson("/api/v1/stores/${enc(storeId)}/invoices", body) as Map
    }

    Map getInvoice(String invoiceId) {
        getJson("/api/v1/stores/${enc(storeId)}/invoices/${enc(invoiceId)}") as Map
    }

    List getPaymentMethods(String invoiceId) {
        def res = getJson("/api/v1/stores/${enc(storeId)}/invoices/${enc(invoiceId)}/payment-methods")
        if (res instanceof List) return res as List
        if (res instanceof Map && res.paymentMethods instanceof List) return res.paymentMethods as List
        return []
    }

    private Object getJson(String path) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(timeoutMs as long))
                .header('Authorization', "token ${apiKey}")
                .header('Accept', 'application/json')
                .GET()
                .build()
        return exchange(req)
    }

    private Object postJson(String path, Map body) {
        String json = JsonOutput.toJson(body ?: [:])
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(timeoutMs as long))
                .header('Authorization', "token ${apiKey}")
                .header('Content-Type', 'application/json')
                .header('Accept', 'application/json')
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build()
        return exchange(req)
    }

    private Object exchange(HttpRequest req) {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            int code = resp.statusCode()
            String body = resp.body() ?: ''
            if (code >= 200 && code < 300) {
                if (!body.trim()) return [:]
                return slurper.parseText(body)
            }
            String msg = "BTCPay HTTP ${code}"
            try {
                def err = slurper.parseText(body)
                if (err instanceof Map && err.message) msg = "${msg}: ${err.message}"
                else if (body) msg = "${msg}: ${body.take(200)}"
            } catch (ignored) {
                if (body) msg = "${msg}: ${body.take(200)}"
            }
            throw new PaymentProviderException(msg, 'BTCPAY_HTTP', code)
        } catch (PaymentProviderException e) {
            throw e
        } catch (Exception e) {
            throw new PaymentProviderException("BTCPay request failed: ${e.message}", e, 'BTCPAY_IO')
        }
    }

    private static String enc(String s) {
        URLEncoder.encode(s ?: '', 'UTF-8').replace('+', '%20')
    }
}
