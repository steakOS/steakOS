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

package steakos.e2e

import steakos.e2e.support.E2eBaseSpec

/**
 * D3 + D10: human session mints agent token; bearer used for L0 reads.
 */
class AgentBearerSmokeSpec extends E2eBaseSpec {

    def setup() {
        initE2eClient()
        seed.truncateBusinessData()
        loginAsAdmin()
    }

    def "mint agent token then GET version with Bearer"() {
        when: 'ensure agent user + mint (admin session)'
        Map ensure = http.postJson('/api/agent/users', [
                username        : 'e2e.agent.bookkeeper',
                linkedTenantKey : 'user:admin'
        ])
        // ensure may be 200 or 201
        assert ensure.status in [200, 201]: "ensure user failed ${ensure.status}: ${ensure.bodyText?.take(300)}"

        Map mint = http.postJson('/api/agent/tokens', [
                username        : 'e2e.agent.bookkeeper',
                label           : 'e2e-smoke',
                hostLabel       : 'integration-test',
                linkedTenantKey : 'user:admin'
        ])

        then:
        // Controller returns 200 { ok, result: { token, ... } }
        assertJsonOk(mint, 200)
        def tokenPayload = mint.body.result ?: mint.body
        tokenPayload.token
        tokenPayload.token.toString().startsWith('sos_')

        when: 'drop session; use bearer only'
        String token = tokenPayload.token as String
        http.clearAuth()
        http.bearerToken = token
        Map ver = http.get('/api/version')

        then:
        assertJsonOk(ver)
        ver.body.softwareVersion
        ver.body.V

        when:
        Map stats = http.get('/api/version/stats')

        then:
        assertJsonOk(stats)
        stats.body.vitals

        when:
        Map cat = http.get('/api/catalogue/items')

        then:
        assertJsonOk(cat)
        cat.body.items instanceof List
    }
}
