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

/**
 * Agent registry & lifecycle (APQC 14.2 / 14.5 / 14.6). AR1 C multi-runtime; AR5 D core seed.
 */
@Slf4j
class AgAgentService {

    private static final List<Map> CORE_AGENTS = [
            [agentKey: 'apqc-navigator', name: 'APQC Navigator',
             taskContext: 'Guide users through APQC processes and suggest next logical steps.',
             soul: 'Patient navigator. Clear, process-oriented, never bypasses human judgment on high-value steps.'],
            [agentKey: 'compliance-audit', name: 'Compliance & Audit',
             taskContext: 'Real-time rule checking and automatic Nostr-ready audit signals.',
             soul: 'Rigorous and calm. Prefers evidence over opinion; flags risk without alarmism.'],
            [agentKey: 'reporting-insight', name: 'Reporting & Insight',
             taskContext: 'Summaries, anomalies, human-reviewable reports across family data.',
             soul: 'Insightful analyst. Short headlines, then detail; always cites sources when possible.'],
            [agentKey: 'bitcoin-nostr', name: 'Bitcoin/Nostr Integration',
             taskContext: 'Payments, Nostr events, identity refs, and audit trail coordination.',
             soul: 'Sovereign-stack native. Speaks sats and npubs fluently; never custody secrets.'],
            [agentKey: 'optimization-suggester', name: 'Optimization Suggester',
             taskContext: 'Analyze workflows and propose improvements. All high-value proposals require Approval.',
             soul: 'Constructive improver. Template-first suggestions; never silent auto-apply of risky changes.']
    ]

    @Transactional
    Agent create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        String key = (fields.agentKey ?: slugKey(name))?.toString()?.trim()
        if (!key) throw new IllegalArgumentException('agentKey is required')
        if (getByKey(npub, key)) throw new IllegalArgumentException("agentKey already exists: ${key}")
        String runtime = ((fields.runtimeKind ?: 'HERMES') as String).toUpperCase()
        if (!(runtime in ['HERMES', 'CUSTOM', 'EXTERNAL'])) {
            throw new IllegalArgumentException("invalid runtimeKind: ${runtime}")
        }
        Long hiveId = longOrNull(fields.hiveId)
        if (hiveId) loadHive(hiveId, npub)

        Agent a = new Agent(
                npubOwner   : npub,
                name        : name,
                description : fields.description as String,
                agentKey    : key,
                runtimeKind : runtime,
                endpointRef : fields.endpointRef as String,
                toolsJson   : fields.toolsJson as String,
                agentStatus : (fields.agentStatus ?: 'DRAFT') as String,
                versionLabel: fields.versionLabel as String,
                agentNpub   : fields.agentNpub as String,
                hiveId      : hiveId,
                taskContext : fields.taskContext as String,
                soul        : fields.soul as String,
                status      : (fields.agentStatus ?: 'DRAFT') as String
        )
        a.save(failOnError: true, flush: true)
        return a
    }

    @Transactional
    Agent update(Long id, String npubOwner, Map fields) {
        Agent a = load(id, npubOwner)
        if (fields.name) a.name = fields.name as String
        if (fields.containsKey('description')) a.description = fields.description as String
        if (fields.containsKey('endpointRef')) a.endpointRef = fields.endpointRef as String
        if (fields.containsKey('toolsJson')) a.toolsJson = fields.toolsJson as String
        if (fields.containsKey('versionLabel')) a.versionLabel = fields.versionLabel as String
        if (fields.containsKey('agentNpub')) a.agentNpub = fields.agentNpub as String
        if (fields.containsKey('taskContext')) a.taskContext = fields.taskContext as String
        if (fields.containsKey('soul')) a.soul = fields.soul as String
        if (fields.runtimeKind) {
            String runtime = (fields.runtimeKind as String).toUpperCase()
            if (!(runtime in ['HERMES', 'CUSTOM', 'EXTERNAL'])) {
                throw new IllegalArgumentException("invalid runtimeKind: ${runtime}")
            }
            a.runtimeKind = runtime
        }
        if (fields.containsKey('hiveId')) {
            Long hiveId = longOrNull(fields.hiveId)
            if (hiveId) loadHive(hiveId, npubOwner)
            a.hiveId = hiveId
        }
        a.save(failOnError: true, flush: true)
        return a
    }

    @Transactional
    Agent activate(Long id, String npubOwner) { setStatus(id, npubOwner, 'ACTIVE') }

    @Transactional
    Agent pause(Long id, String npubOwner) { setStatus(id, npubOwner, 'PAUSED') }

    @Transactional
    Agent retire(Long id, String npubOwner) { setStatus(id, npubOwner, 'RETIRED') }

    /**
     * Idempotent seed of ADR-027 core agents (AR5 D).
     */
    @Transactional
    List<Agent> seedCoreAgents(String npubOwner) {
        String npub = requireNpub(npubOwner)
        List<Agent> result = []
        CORE_AGENTS.each { Map defn ->
            Agent existing = getByKey(npub, defn.agentKey as String)
            if (existing) {
                result << existing
            } else {
                result << create(npub, [
                        name       : defn.name,
                        agentKey   : defn.agentKey,
                        runtimeKind: 'HERMES',
                        agentStatus: 'ACTIVE',
                        taskContext: defn.taskContext,
                        soul       : defn.soul,
                        versionLabel: '1.0'
                ])
            }
        }
        return result
    }

    @Transactional(readOnly = true)
    Agent get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    Agent getByKey(String npubOwner, String agentKey) {
        String npub = requireNpub(npubOwner)
        if (!agentKey?.trim()) return null
        Agent.findByNpubOwnerAndAgentKey(npub, agentKey.trim())
    }

    @Transactional(readOnly = true)
    List<Agent> list(String npubOwner, String agentStatus = null) {
        listPage(npubOwner, agentStatus, 500, 0).items as List<Agent>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String agentStatus = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'name', order: 'asc', max: max, offset: offset]
        if (agentStatus) {
            return [
                    items: Agent.findAllByNpubOwnerAndAgentStatus(npub, agentStatus, opts),
                    total: Agent.countByNpubOwnerAndAgentStatus(npub, agentStatus) as int
            ]
        }
        return [
                items: Agent.findAllByNpubOwner(npub, opts),
                total: Agent.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    List<Agent> listByHive(String npubOwner, Long hiveId) {
        String npub = requireNpub(npubOwner)
        loadHive(hiveId, npub)
        Agent.findAllByNpubOwnerAndHiveId(npub, hiveId, [sort: 'name', order: 'asc'])
    }

    Map toMap(Agent a) {
        if (!a) return null
        [
                id          : a.id,
                name        : a.name,
                description : a.description,
                agentKey    : a.agentKey,
                runtimeKind : a.runtimeKind,
                endpointRef : a.endpointRef,
                toolsJson   : a.toolsJson,
                agentStatus : a.agentStatus,
                versionLabel: a.versionLabel,
                agentNpub   : a.agentNpub,
                hiveId      : a.hiveId,
                taskContext : a.taskContext,
                soul        : a.soul,
                npubOwner   : a.npubOwner
        ]
    }

    private Agent setStatus(Long id, String npubOwner, String st) {
        Agent a = load(id, npubOwner)
        if (st == 'ACTIVE' && a.agentStatus == 'RETIRED') {
            throw new IllegalStateException('cannot activate retired agent')
        }
        a.agentStatus = st
        a.status = st
        a.save(failOnError: true, flush: true)
        return a
    }

    Agent load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Agent a = Agent.get(id)
        if (!a || a.npubOwner != npub) throw new IllegalArgumentException("agent not found: ${id}")
        a
    }

    private Hive loadHive(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Hive h = Hive.get(id)
        if (!h || h.npubOwner != npub) throw new IllegalArgumentException("hive not found: ${id}")
        h
    }

    private static String slugKey(String name) {
        name.toLowerCase().replaceAll(/[^a-z0-9]+/, '-').replaceAll(/(^-|-$)/, '').take(64)
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
