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

/** Human–agent links (not HR). */
@Slf4j
class AgLinkService {

    AgAgentService agAgentService

    @Transactional
    AgentHumanLink link(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Long agentId = longOrNull(fields.agentId)
        if (!agentId) throw new IllegalArgumentException('agentId is required')
        agAgentService.load(agentId, npub)
        String kind = ((fields.linkKind ?: 'OWNER') as String).toUpperCase()
        if (!(kind in ['OWNER', 'OPERATOR', 'REVIEWER'])) {
            throw new IllegalArgumentException("invalid linkKind: ${kind}")
        }
        String name = fields.name?.toString()?.trim() ?: "${kind} link"
        String humanNpub = (fields.humanNpub ?: npub) as String

        AgentHumanLink l = new AgentHumanLink(
                npubOwner : npub,
                name      : name,
                description: fields.description as String,
                agentId   : agentId,
                userId    : longOrNull(fields.userId),
                humanNpub : humanNpub,
                linkKind  : kind,
                active    : Boolean.TRUE,
                status    : 'ACTIVE'
        )
        l.save(failOnError: true, flush: true)
        return l
    }

    @Transactional
    AgentHumanLink unlink(Long id, String npubOwner) {
        AgentHumanLink l = load(id, npubOwner)
        l.active = Boolean.FALSE
        l.status = 'INACTIVE'
        l.save(failOnError: true, flush: true)
        return l
    }

    @Transactional(readOnly = true)
    List<AgentHumanLink> listByAgent(String npubOwner, Long agentId) {
        String npub = requireNpub(npubOwner)
        agAgentService.load(agentId, npub)
        AgentHumanLink.findAllByNpubOwnerAndAgentId(npub, agentId, [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    List<AgentHumanLink> listByHuman(String npubOwner, String humanNpub) {
        String npub = requireNpub(npubOwner)
        String hn = humanNpub?.trim() ?: npub
        AgentHumanLink.findAllByNpubOwnerAndHumanNpub(npub, hn, [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    List<AgentHumanLink> list(String npubOwner) {
        listPage(npubOwner, 500, 0).items as List<AgentHumanLink>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). Optional for links. */
    static final List LINK_SORT_FIELDS = [
            'id', 'name', 'status', 'code',
            'linkKind', 'active', 'humanNpub'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, int max = 25, int offset = 0, Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', LINK_SORT_FIELDS)
        List items = AgentHumanLink.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = AgentHumanLink.createCriteria().count {
            eq('npubOwner', npub)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    Map toMap(AgentHumanLink l) {
        if (!l) return null
        [
                id        : l.id,
                name      : l.name,
                description: l.description,
                agentId   : l.agentId,
                userId    : l.userId,
                humanNpub : l.humanNpub,
                linkKind  : l.linkKind,
                active    : l.active,
                npubOwner : l.npubOwner
        ]
    }

    private AgentHumanLink load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        AgentHumanLink l = AgentHumanLink.get(id)
        if (!l || l.npubOwner != npub) throw new IllegalArgumentException("link not found: ${id}")
        l
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
