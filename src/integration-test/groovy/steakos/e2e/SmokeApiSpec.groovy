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
 * Smoke pack (concept §6): auth, version, stats, setup, catalogue CRUD, sales list.
 */
class SmokeApiSpec extends E2eBaseSpec {

    def setup() {
        initE2eClient()
        seed.truncateBusinessData()
        loginAsAdmin()
    }

    def "GET /api/version returns product stamp"() {
        when:
        Map res = http.get('/api/version')

        then:
        assertJsonOk(res)
        res.body.softwareVersion
        res.body.V
        res.body.fullStamp
    }

    def "GET /api/version/stats returns vitals"() {
        when:
        Map res = http.get('/api/version/stats')

        then:
        assertJsonOk(res)
        res.body.vitals
        (res.body.vitals.domains as Number) > 0
        (res.body.vitals.dbTables as Number) >= 0
    }

    def "GET /api/setup/status returns setup payload"() {
        when:
        Map res = http.get('/api/setup/status')

        then:
        assertJsonOk(res)
        res.body.containsKey('setupComplete') || res.body.containsKey('templateTier') || res.body.containsKey('complete')
    }

    def "catalogue list + create persists row"() {
        when:
        Map list0 = http.get('/api/catalogue/items')
        then:
        assertJsonOk(list0)
        list0.body.items instanceof List

        when:
        String code = "E2E-${System.currentTimeMillis()}"
        Map created = http.postJson('/api/catalogue/items', [
                name      : 'E2E Widget',
                code      : code,
                nature    : 'GOODS',
                canBeSold : true,
                canBeBought: true,
                listPrice : 9.99
        ])

        then:
        assertJsonOk(created, 201)
        created.body.id
        created.body.name == 'E2E Widget'
        created.body.code == code

        and: 'selective DB side-effect (D7)'
        seed.rowExists('catalogue_item', 'code = ?', [code])

        when:
        Map show = http.get("/api/catalogue/items/${created.body.id}")

        then:
        assertJsonOk(show)
        show.body.id == created.body.id
    }

    def "GET /api/sales/customers returns items list"() {
        when:
        Map res = http.get('/api/sales/customers')

        then:
        assertJsonOk(res)
        res.body.items instanceof List
    }
}
