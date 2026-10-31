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

package steakos.agents

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/** Hive teams + Hive Master (AR2). */
@Slf4j
class AgHiveService {

    AgAgentService agAgentService

    @Transactional
    Hive create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Long masterId = longOrNull(fields.hiveMasterAgentId)
        if (masterId) agAgentService.load(masterId, npub)

        Hive h = new Hive(
                npubOwner         : npub,
                name              : name,
                description       : fields.description as String,
                apqcAreaCode      : fields.apqcAreaCode as String,
                hiveStatus        : (fields.hiveStatus ?: 'DRAFT') as String,
                hiveMasterAgentId : masterId,
                maxAgents         : fields.maxAgents != null ? fields.maxAgents as Integer : null,
                status            : (fields.hiveStatus ?: 'DRAFT') as String
        )
        h.save(failOnError: true, flush: true)
        if (masterId) {
            Agent master = agAgentService.load(masterId, npub)
            master.hiveId = h.id
            master.save(failOnError: true, flush: true)
        }
        return h
    }

    @Transactional
    Hive update(Long id, String npubOwner, Map fields) {
        Hive h = load(id, npubOwner)
        if (fields.name) h.name = fields.name as String
        if (fields.containsKey('description')) h.description = fields.description as String
        if (fields.containsKey('apqcAreaCode')) h.apqcAreaCode = fields.apqcAreaCode as String
        if (fields.containsKey('maxAgents')) {
            h.maxAgents = fields.maxAgents != null && fields.maxAgents != '' ? fields.maxAgents as Integer : null
        }
        h.save(failOnError: true, flush: true)
        return h
    }

    @Transactional
    Hive setHiveMaster(Long id, String npubOwner, Long agentId) {
        Hive h = load(id, npubOwner)
        Agent master = agAgentService.load(agentId, npubOwner)
        h.hiveMasterAgentId = agentId
        master.hiveId = h.id
        master.save(failOnError: true, flush: true)
        h.save(failOnError: true, flush: true)
        return h
    }

    @Transactional
    Agent assignAgent(Long hiveId, String npubOwner, Long agentId) {
        Hive h = load(hiveId, npubOwner)
        Agent a = agAgentService.load(agentId, npubOwner)
        if (h.maxAgents) {
            int count = Agent.countByNpubOwnerAndHiveId(requireNpub(npubOwner), hiveId)
            if (a.hiveId != hiveId && count >= h.maxAgents) {
                throw new IllegalStateException("hive full (max ${h.maxAgents})")
            }
        }
        a.hiveId = hiveId
        a.save(failOnError: true, flush: true)
        return a
    }

    @Transactional
    Hive activate(Long id, String npubOwner) {
        Hive h = load(id, npubOwner)
        if (!h.hiveMasterAgentId) {
            throw new IllegalStateException('Hive Master is required to activate')
        }
        agAgentService.load(h.hiveMasterAgentId, npubOwner)
        h.hiveStatus = 'ACTIVE'
        h.status = 'ACTIVE'
        h.save(failOnError: true, flush: true)
        return h
    }

    @Transactional
    Hive close(Long id, String npubOwner) {
        Hive h = load(id, npubOwner)
        h.hiveStatus = 'CLOSED'
        h.status = 'CLOSED'
        h.save(failOnError: true, flush: true)
        return h
    }

    @Transactional(readOnly = true)
    Hive get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Hive> list(String npubOwner, String hiveStatus = null) {
        listPage(npubOwner, hiveStatus, 500, 0).items as List<Hive>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    static final List HIVE_SORT_FIELDS = [
            'id', 'name', 'status', 'code',
            'hiveStatus', 'apqcAreaCode', 'maxAgents'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String hiveStatus = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', HIVE_SORT_FIELDS)
        List items = Hive.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (hiveStatus) eq('hiveStatus', hiveStatus)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Hive.createCriteria().count {
            eq('npubOwner', npub)
            if (hiveStatus) eq('hiveStatus', hiveStatus)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<Agent> listMembers(String npubOwner, Long hiveId) {
        agAgentService.listByHive(npubOwner, hiveId)
    }

    Map toMap(Hive h) {
        if (!h) return null
        [
                id               : h.id,
                name             : h.name,
                description      : h.description,
                apqcAreaCode     : h.apqcAreaCode,
                hiveStatus       : h.hiveStatus,
                hiveMasterAgentId: h.hiveMasterAgentId,
                maxAgents        : h.maxAgents,
                npubOwner        : h.npubOwner
        ]
    }

    private Hive load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Hive h = Hive.get(id)
        if (!h || h.npubOwner != npub) throw new IllegalArgumentException("hive not found: ${id}")
        h
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
