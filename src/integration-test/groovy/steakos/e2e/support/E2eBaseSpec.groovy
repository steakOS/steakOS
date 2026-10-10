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

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import spock.lang.Specification
import steakos.Application

import javax.sql.DataSource

/**
 * Shared base for HTTP E2E: boots full app (D1), random/local port, session login (D3).
 */
@Integration(applicationClass = Application)
abstract class E2eBaseSpec extends Specification {

    @LocalServerPort
    Integer serverPort

    @Autowired
    DataSource dataSource

    E2eHttpClient http
    E2eSeedSupport seed

    String getBaseUrl() {
        int port = serverPort ?: 0
        if (port <= 0) {
            // Grails sometimes exposes serverPort as property without annotation
            try {
                def p = this.hasProperty('serverPort') ? this.serverPort : null
                if (p) port = p as int
            } catch (ignored) { }
        }
        if (port <= 0) {
            port = (System.getProperty('local.server.port') ?: '0') as int
        }
        if (port <= 0) {
            throw new IllegalStateException('serverPort not injected — is @Integration booting the web environment?')
        }
        "http://localhost:${port}"
    }

    /** Call from subclass setup() — do not name this setup() (Spock forbids super.setup). */
    protected void initE2eClient() {
        http = new E2eHttpClient(baseUrl)
        seed = new E2eSeedSupport(dataSource)
    }

    protected void loginAsAdmin() {
        http.loginSession('admin', 'admin')
    }

    protected void assertJsonOk(Map res, int expectedStatus = 200) {
        assert res.status == expectedStatus: "HTTP ${res.status}: ${res.bodyText?.take(400)}"
        assert res.body != null: "expected JSON body, got: ${res.bodyText?.take(200)}"
    }
}
