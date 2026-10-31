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

package steakos.service

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/**
 * Post-sale service levels (APQC 6.1).
 */
@Slf4j
class AsServiceLevelService {

    @Transactional
    ServiceLevel create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        ServiceLevel sl = new ServiceLevel(
                npubOwner     : npub,
                name          : name,
                description   : fields.description as String,
                code          : fields.code as String,
                responseHours : fields.responseHours != null && fields.responseHours != '' ? fields.responseHours as Integer : null,
                resolveHours  : fields.resolveHours != null && fields.resolveHours != '' ? fields.resolveHours as Integer : null,
                channel       : (fields.channel ?: 'ANY') as String,
                active        : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE,
                status        : (fields.status ?: 'ACTIVE') as String
        )
        sl.save(failOnError: true, flush: true)
        return sl
    }

    @Transactional
    ServiceLevel update(Long id, String npubOwner, Map fields) {
        ServiceLevel sl = load(id, npubOwner)
        if (fields.name) sl.name = fields.name as String
        if (fields.containsKey('description')) sl.description = fields.description as String
        if (fields.containsKey('code')) sl.code = fields.code as String
        if (fields.containsKey('responseHours')) {
            sl.responseHours = fields.responseHours != null && fields.responseHours != '' ? fields.responseHours as Integer : null
        }
        if (fields.containsKey('resolveHours')) {
            sl.resolveHours = fields.resolveHours != null && fields.resolveHours != '' ? fields.resolveHours as Integer : null
        }
        if (fields.channel) sl.channel = fields.channel as String
        if (fields.containsKey('active')) sl.active = fields.active as Boolean
        sl.save(failOnError: true, flush: true)
        return sl
    }

    @Transactional(readOnly = true)
    ServiceLevel get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<ServiceLevel> list(String npubOwner, boolean activeOnly = true) {
        listPage(npubOwner, activeOnly, 200, 0).items as List<ServiceLevel>
    }

    static final List SERVICE_LEVEL_SORT_FIELDS = [
            'id', 'name', 'code', 'status', 'channel', 'dateCreated', 'lastUpdated'
    ].asImmutable()

    /**
     * Paged service levels. Returns [items, total, sort, order].
     */
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, boolean activeOnly = true, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'name', 'asc', SERVICE_LEVEL_SORT_FIELDS)
        List items = ServiceLevel.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = ServiceLevel.createCriteria().count {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    Map toMap(ServiceLevel sl) {
        if (!sl) return null
        [
                id           : sl.id,
                name         : sl.name,
                description  : sl.description,
                code         : sl.code,
                responseHours: sl.responseHours,
                resolveHours : sl.resolveHours,
                channel      : sl.channel,
                active       : sl.active,
                status       : sl.status,
                npubOwner    : sl.npubOwner
        ]
    }

    private ServiceLevel load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        ServiceLevel sl = ServiceLevel.get(id)
        if (!sl || sl.npubOwner != npub) throw new IllegalArgumentException("service level not found: ${id}")
        sl
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
