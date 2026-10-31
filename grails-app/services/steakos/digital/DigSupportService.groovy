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

/**
 * Light internal ITSM (DIG0/DIG3). Customer cases stay AfterSales.
 */
@Slf4j
class DigSupportService {

    @Transactional
    ItTicket openTicket(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        String number = fields.ticketNumber?.toString()?.trim() ?: nextNumber(npub)
        ItTicket t = new ItTicket(
                npubOwner    : npub,
                name         : name,
                description  : fields.description as String,
                ticketNumber : number,
                ticketKind   : (fields.ticketKind ?: 'INCIDENT') as String,
                priority     : (fields.priority ?: 'NORMAL') as String,
                ticketStatus : 'OPEN',
                configItemId : longOrNull(fields.configItemId),
                solutionId   : longOrNull(fields.solutionId),
                systemId     : longOrNull(fields.systemId),
                openedAt     : new Date(),
                assignedTo   : fields.assignedTo as String,
                status       : 'OPEN'
        )
        t.save(failOnError: true, flush: true)
        return t
    }

    @Transactional
    ItTicket startWork(Long id, String npubOwner) {
        ItTicket t = load(id, npubOwner)
        if (!(t.ticketStatus in ['OPEN', 'IN_PROGRESS'])) {
            throw new IllegalStateException("cannot start from ${t.ticketStatus}")
        }
        t.ticketStatus = 'IN_PROGRESS'
        t.status = 'IN_PROGRESS'
        t.save(failOnError: true, flush: true)
        return t
    }

    @Transactional
    ItTicket resolve(Long id, String npubOwner, Map fields = [:]) {
        ItTicket t = load(id, npubOwner)
        if (t.ticketStatus in ['CLOSED', 'CANCELLED']) {
            throw new IllegalStateException("cannot resolve ${t.ticketStatus}")
        }
        t.ticketStatus = 'RESOLVED'
        t.status = 'RESOLVED'
        t.resolvedAt = new Date()
        if (fields.resolution) {
            t.description = t.description ?
                    "${t.description}\n\n[Resolution] ${fields.resolution}" :
                    "[Resolution] ${fields.resolution}"
        }
        t.save(failOnError: true, flush: true)
        return t
    }

    @Transactional
    ItTicket close(Long id, String npubOwner) {
        ItTicket t = load(id, npubOwner)
        if (t.ticketStatus == 'CANCELLED') throw new IllegalStateException('cannot close cancelled')
        t.ticketStatus = 'CLOSED'
        t.status = 'CLOSED'
        t.closedAt = new Date()
        if (!t.resolvedAt) t.resolvedAt = t.closedAt
        t.save(failOnError: true, flush: true)
        return t
    }

    @Transactional
    ItTicket cancel(Long id, String npubOwner) {
        ItTicket t = load(id, npubOwner)
        if (t.ticketStatus == 'CLOSED') throw new IllegalStateException('cannot cancel closed')
        t.ticketStatus = 'CANCELLED'
        t.status = 'CANCELLED'
        t.closedAt = new Date()
        t.save(failOnError: true, flush: true)
        return t
    }

    @Transactional(readOnly = true)
    List<ItTicket> list(String npubOwner, String ticketStatus = null) {
        listPage(npubOwner, ticketStatus, 300, 0).items as List<ItTicket>
    }

    static final List TICKET_SORT_FIELDS = [
            'id', 'name', 'status', 'code', 'dateCreated', 'lastUpdated',
            'ticketStatus', 'ticketNumber', 'ticketKind', 'priority', 'openedAt'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String ticketStatus = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', TICKET_SORT_FIELDS)
        List items = ItTicket.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (ticketStatus) eq('ticketStatus', ticketStatus)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = ItTicket.createCriteria().count {
            eq('npubOwner', npub)
            if (ticketStatus) eq('ticketStatus', ticketStatus)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<ItTicket> listOpen(String npubOwner) {
        String npub = requireNpub(npubOwner)
        ItTicket.findAllByNpubOwnerAndTicketStatusInList(
                npub, ['OPEN', 'IN_PROGRESS'], [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    ItTicket get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(ItTicket t) {
        if (!t) return null
        [
                id          : t.id,
                name        : t.name,
                description : t.description,
                ticketNumber: t.ticketNumber,
                ticketKind  : t.ticketKind,
                priority    : t.priority,
                ticketStatus: t.ticketStatus,
                configItemId: t.configItemId,
                solutionId  : t.solutionId,
                systemId    : t.systemId,
                openedAt    : t.openedAt,
                resolvedAt  : t.resolvedAt,
                closedAt    : t.closedAt,
                assignedTo  : t.assignedTo,
                npubOwner   : t.npubOwner
        ]
    }

    private ItTicket load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        ItTicket t = ItTicket.get(id)
        if (!t || t.npubOwner != npub) throw new IllegalArgumentException("ticket not found: ${id}")
        t
    }

    private String nextNumber(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String p = "IT-${period}-"
        int seq = ItTicket.countByNpubOwner(npub) + 1
        String candidate = "${p}${String.format('%04d', seq)}"
        while (ItTicket.findByNpubOwnerAndTicketNumber(npub, candidate)) {
            seq++
            candidate = "${p}${String.format('%04d', seq)}"
        }
        return candidate
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
