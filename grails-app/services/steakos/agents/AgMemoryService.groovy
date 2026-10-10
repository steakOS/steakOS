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

/** Append-only Memory (AR4 A). */
@Slf4j
class AgMemoryService {

    AgAgentService agAgentService

    @Transactional
    Memory record(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        String kind = ((fields.memoryKind ?: 'FEEDBACK') as String).toUpperCase()
        if (!(kind in ['FEEDBACK', 'EVAL', 'LESSON', 'MARKETPLACE_REF'])) {
            throw new IllegalArgumentException("invalid memoryKind: ${kind}")
        }
        Long agentId = longOrNull(fields.agentId)
        if (agentId) agAgentService.load(agentId, npub)

        Memory m = new Memory(
                npubOwner    : npub,
                name         : name,
                description  : fields.description as String,
                agentId      : agentId,
                memoryKind   : kind,
                payloadJson  : fields.payloadJson as String,
                score        : fields.score != null && fields.score != '' ? fields.score as BigDecimal : null,
                sourceRef    : fields.sourceRef as String,
                memoryStatus : 'ACTIVE',
                status       : 'ACTIVE'
        )
        m.save(failOnError: true, flush: true)
        return m
    }

    @Transactional
    Memory archive(Long id, String npubOwner) {
        Memory m = load(id, npubOwner)
        m.memoryStatus = 'ARCHIVED'
        m.status = 'ARCHIVED'
        m.save(failOnError: true, flush: true)
        return m
    }

    @Transactional(readOnly = true)
    Memory get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Memory> list(String npubOwner, String memoryKind = null, boolean includeArchived = false) {
        listPage(npubOwner, memoryKind, includeArchived, 500, 0).items as List<Memory>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String memoryKind = null, boolean includeArchived = false, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        if (memoryKind && !includeArchived) {
            return [
                    items: Memory.findAllByNpubOwnerAndMemoryKindAndMemoryStatus(npub, memoryKind, 'ACTIVE', opts),
                    total: Memory.countByNpubOwnerAndMemoryKindAndMemoryStatus(npub, memoryKind, 'ACTIVE') as int
            ]
        }
        if (memoryKind) {
            return [
                    items: Memory.findAllByNpubOwnerAndMemoryKind(npub, memoryKind, opts),
                    total: Memory.countByNpubOwnerAndMemoryKind(npub, memoryKind) as int
            ]
        }
        if (!includeArchived) {
            return [
                    items: Memory.findAllByNpubOwnerAndMemoryStatus(npub, 'ACTIVE', opts),
                    total: Memory.countByNpubOwnerAndMemoryStatus(npub, 'ACTIVE') as int
            ]
        }
        return [
                items: Memory.findAllByNpubOwner(npub, opts),
                total: Memory.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    List<Memory> listByAgent(String npubOwner, Long agentId) {
        String npub = requireNpub(npubOwner)
        agAgentService.load(agentId, npub)
        Memory.findAllByNpubOwnerAndAgentId(npub, agentId, [sort: 'id', order: 'desc', max: 300])
                .findAll { it.memoryStatus != 'ARCHIVED' }
    }

    Map toMap(Memory m) {
        if (!m) return null
        [
                id          : m.id,
                name        : m.name,
                description : m.description,
                agentId     : m.agentId,
                memoryKind  : m.memoryKind,
                payloadJson : m.payloadJson,
                score       : m.score,
                sourceRef   : m.sourceRef,
                memoryStatus: m.memoryStatus,
                npubOwner   : m.npubOwner
        ]
    }

    private Memory load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Memory m = Memory.get(id)
        if (!m || m.npubOwner != npub) throw new IllegalArgumentException("memory not found: ${id}")
        m
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
