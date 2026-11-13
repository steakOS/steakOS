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

package steakos.e2e.support

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Minimal HTTP client for steakOS E2E (session cookie + optional Bearer).
 * Uses HttpURLConnection so we do not depend on RestAssured.
 */
class E2eHttpClient {

    final String baseUrl
    String sessionCookie
    String bearerToken

    private final JsonSlurper slurper = new JsonSlurper()

    E2eHttpClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith('/') ? baseUrl[0..-2] : baseUrl
    }

    /** Form login → store JSESSIONID (Spring Security). */
    void loginSession(String username = 'admin',
                      String password = steakos.security.PasswordPolicy.DEV_DEFAULT_PASSWORD) {
        // warm cookie jar
        request('GET', '/login/auth', null, false)
        Map auth = request('POST', '/login/authenticate', null, false, [
                'Content-Type': 'application/x-www-form-urlencoded'
        ], "username=${URLEncoder.encode(username, 'UTF-8')}&password=${URLEncoder.encode(password, 'UTF-8')}")
        // 302 is success for form login — but redirect back to /login/auth means failure
        String loc = headerFirst(auth.headers, 'Location')
        if (!(auth.status in [200, 302, 303])) {
            throw new IllegalStateException("login failed HTTP ${auth.status}: ${auth.bodyText?.take(200)}")
        }
        if (loc && loc.contains('/login/auth')) {
            throw new IllegalStateException(
                    "login rejected (redirect to login): status=${auth.status} location=${loc} cookie=${sessionCookie}")
        }
        if (!sessionCookie) {
            throw new IllegalStateException("login succeeded but no JSESSIONID cookie captured (status=${auth.status} loc=${loc})")
        }
    }

    private static String headerFirst(Map headers, String name) {
        if (!headers) return null
        def v = headers[name] ?: headers[name.toLowerCase()]
        if (v instanceof List) return v ? v[0]?.toString() : null
        return v?.toString()
    }

    void clearAuth() {
        sessionCookie = null
        bearerToken = null
    }

    Map get(String path) { request('GET', path, null) }

    Map postJson(String path, Object body) {
        request('POST', path, body, true, ['Content-Type': 'application/json'])
    }

    Map putJson(String path, Object body) {
        request('PUT', path, body, true, ['Content-Type': 'application/json'])
    }

    private Map request(String method, String path,
                        Object body = null,
                        boolean parseJson = true,
                        Map extraHeaders = [:],
                        String rawBody = null) {
        String url = path.startsWith('http') ? path : "${baseUrl}${path.startsWith('/') ? path : '/' + path}"
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection()
        conn.requestMethod = method
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.setRequestProperty('Accept', 'application/json, text/html, */*')
        if (!(method in ['GET', 'HEAD', 'OPTIONS'])) {
            conn.setRequestProperty('X-Steakos-Request', '1')
        }
        extraHeaders.each { k, v -> conn.setRequestProperty(k as String, v as String) }
        if (sessionCookie) {
            conn.setRequestProperty('Cookie', sessionCookie)
        }
        if (bearerToken) {
            conn.setRequestProperty('Authorization', "Bearer ${bearerToken}")
        }

        String payload = rawBody
        if (payload == null && body != null) {
            payload = body instanceof String ? body : JsonOutput.toJson(body)
        }
        if (payload != null && method in ['POST', 'PUT', 'PATCH']) {
            conn.doOutput = true
            conn.outputStream.withWriter('UTF-8') { it << payload }
        }

        int status = conn.responseCode
        captureCookies(conn)
        String text = ''
        try {
            InputStream stream = status >= 400 ? conn.errorStream : conn.inputStream
            if (stream) {
                text = stream.getText('UTF-8')
            }
        } catch (ignored) { }

        Object json = null
        String trimmed = text?.trim()
        if (parseJson && trimmed && (trimmed.startsWith('{') || trimmed.startsWith('['))) {
            try {
                json = slurper.parseText(text)
            } catch (ignored) { }
        }
        [
                status  : status,
                body    : json,
                bodyText: text,
                headers : conn.headerFields
        ]
    }

    private void captureCookies(HttpURLConnection conn) {
        List<String> cookies = conn.getHeaderFields()?.get('Set-Cookie')
        if (!cookies) {
            cookies = conn.getHeaderFields()?.get('set-cookie')
        }
        if (!cookies) return
        cookies.each { String c ->
            if (c?.toUpperCase()?.startsWith('JSESSIONID=')) {
                sessionCookie = c.split(';')[0]
            }
        }
    }
}
