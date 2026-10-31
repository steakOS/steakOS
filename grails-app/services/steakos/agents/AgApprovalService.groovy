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

/** HITL Approval with folded proposal (AR3 A). */
@Slf4j
class AgApprovalService {

    AgAgentService agAgentService

    @Transactional
    Approval submit(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Long agentId = longOrNull(fields.agentId)
        if (agentId) agAgentService.load(agentId, npub)
        String risk = ((fields.riskLevel ?: 'LOW') as String).toUpperCase()
        if (!(risk in ['LOW', 'MED', 'HIGH'])) throw new IllegalArgumentException("invalid riskLevel: ${risk}")

        Approval a = new Approval(
                npubOwner   : npub,
                name        : name,
                description : fields.description as String,
                agentId     : agentId,
                proposalRef : fields.proposalRef as String,
                proposalJson: fields.proposalJson as String,
                targetFamily: fields.targetFamily as String,
                decision    : 'PENDING',
                riskLevel   : risk,
                status      : 'PENDING'
        )
        a.save(failOnError: true, flush: true)
        return a
    }

    @Transactional
    Approval approve(Long id, String npubOwner, Map fields = [:]) {
        decide(id, npubOwner, 'APPROVED', fields)
    }

    @Transactional
    Approval reject(Long id, String npubOwner, Map fields = [:]) {
        decide(id, npubOwner, 'REJECTED', fields)
    }

    @Transactional
    Approval cancel(Long id, String npubOwner) {
        Approval a = load(id, npubOwner)
        if (a.decision != 'PENDING') throw new IllegalStateException("cannot cancel ${a.decision}")
        a.decision = 'CANCELLED'
        a.status = 'CANCELLED'
        a.decidedAt = new Date()
        a.save(failOnError: true, flush: true)
        return a
    }

    @Transactional(readOnly = true)
    Approval get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Approval> list(String npubOwner, String decision = null) {
        listPage(npubOwner, decision, 300, 0).items as List<Approval>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    static final List APPROVAL_SORT_FIELDS = [
            'id', 'name', 'status', 'code',
            'decision', 'riskLevel', 'targetFamily', 'decidedAt'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String decision = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', APPROVAL_SORT_FIELDS)
        List items = Approval.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (decision) eq('decision', decision)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Approval.createCriteria().count {
            eq('npubOwner', npub)
            if (decision) eq('decision', decision)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<Approval> listOpen(String npubOwner) {
        list(npubOwner, 'PENDING')
    }

    Map toMap(Approval a) {
        if (!a) return null
        [
                id           : a.id,
                name         : a.name,
                description  : a.description,
                agentId      : a.agentId,
                proposalRef  : a.proposalRef,
                proposalJson : a.proposalJson,
                targetFamily : a.targetFamily,
                decision     : a.decision,
                decidedAt    : a.decidedAt,
                decidedByNpub: a.decidedByNpub,
                riskLevel    : a.riskLevel,
                nostrEventId : a.nostrEventId,
                npubOwner    : a.npubOwner
        ]
    }

    private Approval decide(Long id, String npubOwner, String decision, Map fields) {
        Approval a = load(id, npubOwner)
        if (a.decision != 'PENDING') throw new IllegalStateException("already decided: ${a.decision}")
        a.decision = decision
        a.status = decision
        a.decidedAt = new Date()
        a.decidedByNpub = (fields.decidedByNpub ?: npubOwner) as String
        if (fields.nostrEventId) a.nostrEventId = fields.nostrEventId as String
        a.save(failOnError: true, flush: true)
        return a
    }

    private Approval load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Approval a = Approval.get(id)
        if (!a || a.npubOwner != npub) throw new IllegalArgumentException("approval not found: ${id}")
        a
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
