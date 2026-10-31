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

package steakos.digital

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

@Slf4j
class DigSolutionService {

    DigSystemService digSystemService

    @Transactional
    Solution create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Long systemId = longOrNull(fields.systemId)
        if (systemId) digSystemService.get(systemId, npub)

        Solution s = new Solution(
                npubOwner       : npub,
                name            : name,
                description     : fields.description as String,
                integrationType : (fields.integrationType ?: 'OTHER') as String,
                lifecycleStatus : (fields.lifecycleStatus ?: 'DRAFT') as String,
                endpointRef     : fields.endpointRef as String,
                versionLabel    : fields.versionLabel as String,
                systemId        : systemId,
                status          : (fields.lifecycleStatus ?: 'DRAFT') as String
        )
        s.save(failOnError: true, flush: true)
        return s
    }

    @Transactional
    Solution update(Long id, String npubOwner, Map fields) {
        Solution s = load(id, npubOwner)
        if (fields.name) s.name = fields.name as String
        if (fields.containsKey('description')) s.description = fields.description as String
        if (fields.integrationType) s.integrationType = fields.integrationType as String
        if (fields.containsKey('endpointRef')) s.endpointRef = fields.endpointRef as String
        if (fields.containsKey('versionLabel')) s.versionLabel = fields.versionLabel as String
        if (fields.containsKey('systemId')) {
            Long sid = longOrNull(fields.systemId)
            if (sid) digSystemService.get(sid, npubOwner)
            s.systemId = sid
        }
        s.save(failOnError: true, flush: true)
        return s
    }

    @Transactional
    Solution setLifecycle(Long id, String npubOwner, String lifecycleStatus) {
        Solution s = load(id, npubOwner)
        String st = lifecycleStatus?.toUpperCase()
        if (!(st in ['DRAFT', 'ACTIVE', 'RETIRED', 'CANCELLED'])) {
            throw new IllegalArgumentException("invalid lifecycle: ${lifecycleStatus}")
        }
        s.lifecycleStatus = st
        s.status = st
        s.save(failOnError: true, flush: true)
        return s
    }

    @Transactional
    Solution activate(Long id, String npubOwner) { setLifecycle(id, npubOwner, 'ACTIVE') }

    @Transactional
    Solution retire(Long id, String npubOwner) { setLifecycle(id, npubOwner, 'RETIRED') }

    @Transactional(readOnly = true)
    Solution get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Solution> list(String npubOwner, String lifecycleStatus = null) {
        listPage(npubOwner, lifecycleStatus, 500, 0).items as List<Solution>
    }

    static final List SOLUTION_SORT_FIELDS = [
            'id', 'name', 'status', 'code', 'dateCreated', 'lastUpdated',
            'lifecycleStatus', 'integrationType', 'versionLabel'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String lifecycleStatus = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', SOLUTION_SORT_FIELDS)
        List items = Solution.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (lifecycleStatus) eq('lifecycleStatus', lifecycleStatus)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Solution.createCriteria().count {
            eq('npubOwner', npub)
            if (lifecycleStatus) eq('lifecycleStatus', lifecycleStatus)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    Map toMap(Solution s) {
        if (!s) return null
        [
                id              : s.id,
                name            : s.name,
                description     : s.description,
                integrationType : s.integrationType,
                lifecycleStatus : s.lifecycleStatus,
                endpointRef     : s.endpointRef,
                versionLabel    : s.versionLabel,
                systemId        : s.systemId,
                npubOwner       : s.npubOwner
        ]
    }

    private Solution load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Solution s = Solution.get(id)
        if (!s || s.npubOwner != npub) throw new IllegalArgumentException("solution not found: ${id}")
        s
    }

    private static Long longOrNull(Object v) {
        if (v == null || v == '') return null
        return v as Long
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
