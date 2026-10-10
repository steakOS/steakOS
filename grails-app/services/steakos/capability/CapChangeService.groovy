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

package steakos.capability

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/**
 * Organizational change (APQC 13.4). Not Digital ITChange (CAP3 A).
 */
@Slf4j
class CapChangeService {

    @Transactional
    Change createChange(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Change c = new Change(
                npubOwner   : npub,
                name        : name,
                description : fields.description as String,
                changeStatus: (fields.changeStatus ?: 'PLANNED') as String,
                plannedStart: parseDate(fields.plannedStart),
                plannedEnd  : parseDate(fields.plannedEnd),
                impactNote  : fields.impactNote as String,
                status      : (fields.changeStatus ?: 'PLANNED') as String
        )
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    ChangeItem addItem(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Long changeId = longOrNull(fields.changeId)
        if (!changeId) throw new IllegalArgumentException('changeId is required')
        loadChange(changeId, npub)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Integer itemNo = fields.itemNo != null ? fields.itemNo as Integer : nextItemNo(npub, changeId)
        ChangeItem item = new ChangeItem(
                npubOwner : npub,
                name      : name,
                description: fields.description as String,
                changeId  : changeId,
                itemNo    : itemNo,
                itemStatus: (fields.itemStatus ?: 'OPEN') as String,
                status    : (fields.itemStatus ?: 'OPEN') as String
        )
        item.save(failOnError: true, flush: true)
        return item
    }

    @Transactional
    Change plan(Long id, String npubOwner, Map fields = [:]) {
        Change c = loadChange(id, npubOwner)
        if (c.changeStatus in ['DONE', 'CANCELLED']) {
            throw new IllegalStateException("cannot re-plan ${c.changeStatus}")
        }
        c.changeStatus = 'PLANNED'
        c.status = 'PLANNED'
        if (fields.plannedStart) c.plannedStart = parseDate(fields.plannedStart)
        if (fields.plannedEnd) c.plannedEnd = parseDate(fields.plannedEnd)
        if (fields.containsKey('impactNote')) c.impactNote = fields.impactNote as String
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    Change implement(Long id, String npubOwner) {
        Change c = loadChange(id, npubOwner)
        if (!(c.changeStatus in ['PLANNED', 'IN_PROGRESS'])) {
            throw new IllegalStateException("cannot implement from ${c.changeStatus}")
        }
        c.changeStatus = 'IN_PROGRESS'
        c.status = 'IN_PROGRESS'
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    Change close(Long id, String npubOwner) {
        Change c = loadChange(id, npubOwner)
        if (c.changeStatus == 'CANCELLED') throw new IllegalStateException('cannot close cancelled')
        c.changeStatus = 'DONE'
        c.status = 'DONE'
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    Change cancel(Long id, String npubOwner) {
        Change c = loadChange(id, npubOwner)
        if (c.changeStatus == 'DONE') throw new IllegalStateException('cannot cancel done')
        c.changeStatus = 'CANCELLED'
        c.status = 'CANCELLED'
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional(readOnly = true)
    Change get(Long id, String npubOwner) { loadChange(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Change> list(String npubOwner, String changeStatus = null) {
        listPage(npubOwner, changeStatus, 300, 0).items as List<Change>
    }

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String changeStatus = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        if (changeStatus) {
            return [
                    items: Change.findAllByNpubOwnerAndChangeStatus(npub, changeStatus, opts),
                    total: Change.countByNpubOwnerAndChangeStatus(npub, changeStatus) as int
            ]
        }
        return [
                items: Change.findAllByNpubOwner(npub, opts),
                total: Change.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    List<Change> listOpen(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Change.findAllByNpubOwnerAndChangeStatusInList(
                npub, ['PLANNED', 'IN_PROGRESS'], [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    List<ChangeItem> listItems(String npubOwner, Long changeId) {
        String npub = requireNpub(npubOwner)
        loadChange(changeId, npub)
        ChangeItem.findAllByNpubOwnerAndChangeId(npub, changeId, [sort: 'itemNo', order: 'asc'])
    }

    Map toChangeMap(Change c) {
        if (!c) return null
        [
                id          : c.id,
                name        : c.name,
                description : c.description,
                changeStatus: c.changeStatus,
                plannedStart: c.plannedStart,
                plannedEnd  : c.plannedEnd,
                impactNote  : c.impactNote,
                npubOwner   : c.npubOwner
        ]
    }

    Map toItemMap(ChangeItem i) {
        if (!i) return null
        [
                id        : i.id,
                name      : i.name,
                description: i.description,
                changeId  : i.changeId,
                itemNo    : i.itemNo,
                itemStatus: i.itemStatus,
                npubOwner : i.npubOwner
        ]
    }

    private Change loadChange(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Change c = Change.get(id)
        if (!c || c.npubOwner != npub) throw new IllegalArgumentException("change not found: ${id}")
        c
    }

    private Integer nextItemNo(String npub, Long changeId) {
        List<ChangeItem> items = ChangeItem.findAllByNpubOwnerAndChangeId(npub, changeId, [sort: 'itemNo', order: 'desc', max: 1])
        if (!items) return 1
        (items[0].itemNo ?: 0) + 1
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
