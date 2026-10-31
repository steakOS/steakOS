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

/** Knowledge areas thin (APQC 13.5). */
@Slf4j
class CapKnowledgeService {

    @Transactional
    KnowledgeArea create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Long parentId = longOrNull(fields.parentId)
        if (parentId) load(parentId, npub)
        KnowledgeArea ka = new KnowledgeArea(
                npubOwner : npub,
                name      : name,
                description: fields.description as String,
                parentId  : parentId,
                areaCode  : fields.areaCode as String,
                areaStatus: (fields.areaStatus ?: 'ACTIVE') as String,
                status    : (fields.areaStatus ?: 'ACTIVE') as String
        )
        ka.save(failOnError: true, flush: true)
        return ka
    }

    @Transactional
    KnowledgeArea update(Long id, String npubOwner, Map fields) {
        KnowledgeArea ka = load(id, npubOwner)
        if (fields.name) ka.name = fields.name as String
        if (fields.containsKey('description')) ka.description = fields.description as String
        if (fields.containsKey('areaCode')) ka.areaCode = fields.areaCode as String
        if (fields.containsKey('parentId')) {
            Long pid = longOrNull(fields.parentId)
            if (pid) {
                if (pid == id) throw new IllegalArgumentException('parentId cannot be self')
                load(pid, npubOwner)
            }
            ka.parentId = pid
        }
        if (fields.areaStatus) {
            String st = fields.areaStatus as String
            if (!(st in ['ACTIVE', 'INACTIVE'])) throw new IllegalArgumentException("invalid areaStatus: ${st}")
            ka.areaStatus = st
            ka.status = st
        }
        ka.save(failOnError: true, flush: true)
        return ka
    }

    @Transactional(readOnly = true)
    KnowledgeArea get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<KnowledgeArea> list(String npubOwner, boolean activeOnly = false) {
        listPage(npubOwner, activeOnly, 500, 0).items as List<KnowledgeArea>
    }

    static final List KNOWLEDGE_SORT_FIELDS = [
            'id', 'name', 'areaCode', 'areaStatus', 'status'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, boolean activeOnly = false, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'name', 'asc', KNOWLEDGE_SORT_FIELDS)
        List items = KnowledgeArea.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (activeOnly) eq('areaStatus', 'ACTIVE')
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = KnowledgeArea.createCriteria().count {
            eq('npubOwner', npub)
            if (activeOnly) eq('areaStatus', 'ACTIVE')
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<KnowledgeArea> listChildren(String npubOwner, Long parentId) {
        String npub = requireNpub(npubOwner)
        if (parentId) load(parentId, npub)
        KnowledgeArea.findAllByNpubOwnerAndParentId(npub, parentId, [sort: 'name', order: 'asc'])
    }

    Map toMap(KnowledgeArea ka) {
        if (!ka) return null
        [
                id        : ka.id,
                name      : ka.name,
                description: ka.description,
                parentId  : ka.parentId,
                areaCode  : ka.areaCode,
                areaStatus: ka.areaStatus,
                npubOwner : ka.npubOwner
        ]
    }

    private KnowledgeArea load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        KnowledgeArea ka = KnowledgeArea.get(id)
        if (!ka || ka.npubOwner != npub) throw new IllegalArgumentException("knowledge area not found: ${id}")
        ka
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
