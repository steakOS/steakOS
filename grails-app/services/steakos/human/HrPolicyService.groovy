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

package steakos.human

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

@Slf4j
class HrPolicyService {

    @Transactional
    Policy create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Policy p = new Policy(
                npubOwner    : npub,
                name         : name,
                description  : fields.description as String,
                code         : fields.code as String,
                policyKind   : (fields.policyKind ?: 'OTHER') as String,
                effectiveFrom: parseDate(fields.effectiveFrom) ?: new Date(),
                active       : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE,
                status       : 'ACTIVE'
        )
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional
    Policy update(Long id, String npubOwner, Map fields) {
        Policy p = load(id, npubOwner)
        if (fields.name) p.name = fields.name as String
        if (fields.containsKey('description')) p.description = fields.description as String
        if (fields.containsKey('code')) p.code = fields.code as String
        if (fields.policyKind) p.policyKind = fields.policyKind as String
        if (fields.containsKey('active')) p.active = fields.active as Boolean
        if (fields.containsKey('effectiveFrom')) p.effectiveFrom = parseDate(fields.effectiveFrom)
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional(readOnly = true)
    List<Policy> list(String npubOwner, boolean activeOnly = true) {
        listPage(npubOwner, activeOnly, 200, 0).items as List<Policy>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    static final List POLICY_SORT_FIELDS = [
            'id', 'name', 'status', 'code', 'dateCreated', 'lastUpdated',
            'policyKind', 'active', 'effectiveFrom'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, boolean activeOnly = true, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'name', 'asc', POLICY_SORT_FIELDS)
        List items = Policy.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Policy.createCriteria().count {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    Policy get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(Policy p) {
        if (!p) return null
        [
                id           : p.id,
                name         : p.name,
                description  : p.description,
                code         : p.code,
                policyKind   : p.policyKind,
                effectiveFrom: p.effectiveFrom,
                active       : p.active,
                npubOwner    : p.npubOwner
        ]
    }

    private Policy load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Policy p = Policy.get(id)
        if (!p || p.npubOwner != npub) throw new IllegalArgumentException("policy not found: ${id}")
        p
    }

    private static Date parseDate(Object raw) {
        if (raw == null || raw == '') return null
        if (raw instanceof Date) return raw
        String s = raw.toString().trim()
        if (s.length() >= 10) {
            try { return Date.parse('yyyy-MM-dd', s.substring(0, 10)) } catch (ignored) {}
        }
        null
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
