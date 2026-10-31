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
class DigChangeService {

    @Transactional
    ITChange create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        ITChange c = new ITChange(
                npubOwner         : npub,
                name              : name,
                description       : fields.description as String,
                changeKind        : (fields.changeKind ?: 'CHANGE') as String,
                riskLevel         : (fields.riskLevel ?: 'LOW') as String,
                changeWindowStart : parseDate(fields.changeWindowStart),
                changeWindowEnd   : parseDate(fields.changeWindowEnd),
                changeStatus      : (fields.changeStatus ?: 'PLANNED') as String,
                solutionId        : longOrNull(fields.solutionId),
                systemId          : longOrNull(fields.systemId),
                configItemId      : longOrNull(fields.configItemId),
                cabNote           : fields.cabNote as String,
                status            : (fields.changeStatus ?: 'PLANNED') as String
        )
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    ITChange schedule(Long id, String npubOwner, Map fields = [:]) {
        ITChange c = load(id, npubOwner)
        if (c.changeStatus in ['DONE', 'CANCELLED']) {
            throw new IllegalStateException("cannot schedule ${c.changeStatus}")
        }
        c.changeStatus = 'PLANNED'
        c.status = 'PLANNED'
        if (fields.changeWindowStart) c.changeWindowStart = parseDate(fields.changeWindowStart)
        if (fields.changeWindowEnd) c.changeWindowEnd = parseDate(fields.changeWindowEnd)
        if (fields.cabNote) c.cabNote = fields.cabNote as String
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    ITChange start(Long id, String npubOwner) {
        ITChange c = load(id, npubOwner)
        if (!(c.changeStatus in ['PLANNED', 'IN_PROGRESS'])) {
            throw new IllegalStateException("cannot start from ${c.changeStatus}")
        }
        c.changeStatus = 'IN_PROGRESS'
        c.status = 'IN_PROGRESS'
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    ITChange complete(Long id, String npubOwner) {
        ITChange c = load(id, npubOwner)
        if (c.changeStatus == 'CANCELLED') throw new IllegalStateException('cannot complete cancelled')
        c.changeStatus = 'DONE'
        c.status = 'DONE'
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    ITChange cancel(Long id, String npubOwner) {
        ITChange c = load(id, npubOwner)
        if (c.changeStatus == 'DONE') throw new IllegalStateException('cannot cancel done')
        c.changeStatus = 'CANCELLED'
        c.status = 'CANCELLED'
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional(readOnly = true)
    List<ITChange> list(String npubOwner, String changeStatus = null) {
        listPage(npubOwner, changeStatus, 300, 0).items as List<ITChange>
    }

    static final List CHANGE_SORT_FIELDS = [
            'id', 'name', 'status', 'code', 'dateCreated', 'lastUpdated',
            'changeStatus', 'changeKind', 'riskLevel'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String changeStatus = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', CHANGE_SORT_FIELDS)
        List items = ITChange.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (changeStatus) eq('changeStatus', changeStatus)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = ITChange.createCriteria().count {
            eq('npubOwner', npub)
            if (changeStatus) eq('changeStatus', changeStatus)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<ITChange> listOpen(String npubOwner) {
        String npub = requireNpub(npubOwner)
        ITChange.findAllByNpubOwnerAndChangeStatusInList(
                npub, ['PLANNED', 'IN_PROGRESS'], [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    ITChange get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(ITChange c) {
        if (!c) return null
        [
                id               : c.id,
                name             : c.name,
                description      : c.description,
                changeKind       : c.changeKind,
                riskLevel        : c.riskLevel,
                changeWindowStart: c.changeWindowStart,
                changeWindowEnd  : c.changeWindowEnd,
                changeStatus     : c.changeStatus,
                solutionId       : c.solutionId,
                systemId         : c.systemId,
                configItemId     : c.configItemId,
                cabNote          : c.cabNote,
                npubOwner        : c.npubOwner
        ]
    }

    private ITChange load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        ITChange c = ITChange.get(id)
        if (!c || c.npubOwner != npub) throw new IllegalArgumentException("it change not found: ${id}")
        c
    }

    private static Long longOrNull(Object v) {
        if (v == null || v == '') return null
        return v as Long
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
