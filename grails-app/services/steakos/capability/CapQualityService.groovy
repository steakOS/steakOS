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

/** Enterprise quality light (APQC 13.3). */
@Slf4j
class CapQualityService {

    @Transactional
    Quality createQuality(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Quality q = new Quality(
                npubOwner    : npub,
                name         : name,
                description  : fields.description as String,
                qualityCode  : fields.qualityCode as String,
                qualityStatus: (fields.qualityStatus ?: 'ACTIVE') as String,
                status       : (fields.qualityStatus ?: 'ACTIVE') as String
        )
        q.save(failOnError: true, flush: true)
        return q
    }

    @Transactional
    QualityItem addItem(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Long qualityId = longOrNull(fields.qualityId)
        if (!qualityId) throw new IllegalArgumentException('qualityId is required')
        loadQuality(qualityId, npub)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        String kind = (fields.itemKind ?: 'REQ') as String
        if (!(kind in ['REQ', 'NCR'])) throw new IllegalArgumentException("invalid itemKind: ${kind}")
        QualityItem item = new QualityItem(
                npubOwner : npub,
                name      : name,
                description: fields.description as String,
                qualityId : qualityId,
                itemKind  : kind,
                itemStatus: (fields.itemStatus ?: 'OPEN') as String,
                status    : (fields.itemStatus ?: 'OPEN') as String
        )
        item.save(failOnError: true, flush: true)
        return item
    }

    @Transactional
    QualityItem closeItem(Long id, String npubOwner) {
        QualityItem item = loadItem(id, npubOwner)
        item.itemStatus = 'CLOSED'
        item.status = 'CLOSED'
        item.save(failOnError: true, flush: true)
        return item
    }

    @Transactional
    Quality archive(Long id, String npubOwner) {
        Quality q = loadQuality(id, npubOwner)
        q.qualityStatus = 'ARCHIVED'
        q.status = 'ARCHIVED'
        q.save(failOnError: true, flush: true)
        return q
    }

    @Transactional(readOnly = true)
    Quality get(Long id, String npubOwner) { loadQuality(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Quality> list(String npubOwner, String qualityStatus = null) {
        listPage(npubOwner, qualityStatus, 500, 0).items as List<Quality>
    }

    static final List QUALITY_SORT_FIELDS = [
            'id', 'name', 'qualityCode', 'qualityStatus', 'status'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String qualityStatus = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', QUALITY_SORT_FIELDS)
        List items = Quality.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (qualityStatus) eq('qualityStatus', qualityStatus)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Quality.createCriteria().count {
            eq('npubOwner', npub)
            if (qualityStatus) eq('qualityStatus', qualityStatus)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<QualityItem> listItems(String npubOwner, Long qualityId = null) {
        String npub = requireNpub(npubOwner)
        if (qualityId) {
            loadQuality(qualityId, npub)
            return QualityItem.findAllByNpubOwnerAndQualityId(npub, qualityId, [sort: 'id', order: 'desc'])
        }
        return QualityItem.findAllByNpubOwner(npub, [sort: 'id', order: 'desc', max: 500])
    }

    Map toQualityMap(Quality q) {
        if (!q) return null
        [
                id           : q.id,
                name         : q.name,
                description  : q.description,
                qualityCode  : q.qualityCode,
                qualityStatus: q.qualityStatus,
                npubOwner    : q.npubOwner
        ]
    }

    Map toItemMap(QualityItem i) {
        if (!i) return null
        [
                id        : i.id,
                name      : i.name,
                description: i.description,
                qualityId : i.qualityId,
                itemKind  : i.itemKind,
                itemStatus: i.itemStatus,
                npubOwner : i.npubOwner
        ]
    }

    private Quality loadQuality(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Quality q = Quality.get(id)
        if (!q || q.npubOwner != npub) throw new IllegalArgumentException("quality not found: ${id}")
        q
    }

    private QualityItem loadItem(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        QualityItem i = QualityItem.get(id)
        if (!i || i.npubOwner != npub) throw new IllegalArgumentException("quality item not found: ${id}")
        i
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
