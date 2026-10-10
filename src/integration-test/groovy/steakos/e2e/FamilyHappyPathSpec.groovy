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
 * D6: one happy-path write/read per major family desk API.
 */
class FamilyHappyPathSpec extends E2eBaseSpec {

    def setup() {
        initE2eClient()
        seed.truncateBusinessData()
        loginAsAdmin()
    }

    def "sales: create customer"() {
        when:
        String name = "E2E Customer ${System.currentTimeMillis()}"
        Map res = http.postJson('/api/sales/customers', [name: name, email: 'e2e@example.com'])

        then:
        assertJsonOk(res, 201)
        res.body.id
        res.body.name == name
        seed.rowExists('customer', 'name = ?', [name])
    }

    def "scm: create vendor"() {
        when:
        String name = "E2E Vendor ${System.currentTimeMillis()}"
        Map res = http.postJson('/api/scm/vendors', [name: name])

        then:
        assertJsonOk(res, 201)
        res.body.id
        seed.rowExists('vendor', 'name = ?', [name])
    }

    def "scm: create goods catalogue via products facade"() {
        when:
        String sku = "E2E-SKU-${System.currentTimeMillis()}"
        Map res = http.postJson('/api/scm/products', [name: 'E2E Product', sku: sku, unit: 'EA'])

        then:
        // facade returns 201 with catalogue-shaped body
        res.status in [200, 201]
        res.body?.id
        seed.rowExists('catalogue_item', 'code = ? OR name = ?', [sku, 'E2E Product']) ||
                seed.rowExists('catalogue_item', 'name = ?', ['E2E Product'])
    }

    def "delivery: create resource"() {
        when:
        String name = "E2E Resource ${System.currentTimeMillis()}"
        Map res = http.postJson('/api/delivery/resources', [
                name        : name,
                resourceKind: 'HUMAN'
        ])

        then:
        assertJsonOk(res, 201)
        res.body.id
        seed.rowExists('resource', 'name = ?', [name])
    }

    def "delivery: list service orders"() {
        when:
        Map res = http.get('/api/delivery/orders')

        then:
        assertJsonOk(res)
        res.body.items instanceof List || res.body instanceof List
    }

    def "aftersales: list inquiries"() {
        when:
        Map res = http.get('/api/aftersales/inquiries')

        then:
        assertJsonOk(res)
        res.body.items instanceof List
    }

    def "hr: list candidates"() {
        when:
        Map res = http.get('/api/hr/candidates')

        then:
        assertJsonOk(res)
        res.body.items instanceof List
    }

    def "digital: list systems or solutions"() {
        when:
        Map res = http.get('/api/digital/systems')
        if (res.status == 404) {
            res = http.get('/api/digital/solutions')
        }

        then:
        res.status == 200
        res.body != null
    }

    def "finance: list ledger accounts (may be empty)"() {
        when:
        Map res = http.get('/api/finance/ledger/accounts')

        then:
        assertJsonOk(res)
        // either items or accounts key
        res.body.items instanceof List || res.body.accounts instanceof List || res.body instanceof List || res.body instanceof Map
    }

    def "capability: list processes"() {
        when:
        Map res = http.get('/api/capability/processes')

        then:
        assertJsonOk(res)
        res.body.items instanceof List || res.body instanceof List
    }

    def "agents: list agents"() {
        when:
        Map res = http.get('/api/agents/agents')

        then:
        assertJsonOk(res)
        res.body.items instanceof List || res.body instanceof List
    }
}

