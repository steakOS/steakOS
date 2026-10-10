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
class DigDataService {

    @Transactional
    Data create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Data d = new Data(
                npubOwner    : npub,
                name         : name,
                description  : fields.description as String,
                retentionDays: fields.retentionDays != null && fields.retentionDays != '' ? fields.retentionDays as Integer : null,
                dataClass    : (fields.dataClass ?: 'INTERNAL') as String,
                ownerRef     : fields.ownerRef as String,
                dataStatus   : (fields.dataStatus ?: 'ACTIVE') as String,
                status       : (fields.dataStatus ?: 'ACTIVE') as String
        )
        d.save(failOnError: true, flush: true)
        return d
    }

    @Transactional
    Data update(Long id, String npubOwner, Map fields) {
        Data d = load(id, npubOwner)
        if (fields.name) d.name = fields.name as String
        if (fields.containsKey('description')) d.description = fields.description as String
        if (fields.containsKey('retentionDays')) {
            d.retentionDays = fields.retentionDays != null && fields.retentionDays != '' ? fields.retentionDays as Integer : null
        }
        if (fields.dataClass) d.dataClass = fields.dataClass as String
        if (fields.containsKey('ownerRef')) d.ownerRef = fields.ownerRef as String
        if (fields.dataStatus) {
            d.dataStatus = fields.dataStatus as String
            d.status = d.dataStatus
        }
        d.save(failOnError: true, flush: true)
        return d
    }

    @Transactional(readOnly = true)
    Data get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Data> list(String npubOwner, String dataClass = null) {
        listPage(npubOwner, dataClass, 500, 0).items as List<Data>
    }

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String dataClass = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'name', order: 'asc', max: max, offset: offset]
        if (dataClass) {
            return [
                    items: Data.findAllByNpubOwnerAndDataClass(npub, dataClass, opts),
                    total: Data.countByNpubOwnerAndDataClass(npub, dataClass) as int
            ]
        }
        return [
                items: Data.findAllByNpubOwner(npub, opts),
                total: Data.countByNpubOwner(npub) as int
        ]
    }

    Map toMap(Data d) {
        if (!d) return null
        [
                id           : d.id,
                name         : d.name,
                description  : d.description,
                retentionDays: d.retentionDays,
                dataClass    : d.dataClass,
                ownerRef     : d.ownerRef,
                dataStatus   : d.dataStatus,
                npubOwner    : d.npubOwner
        ]
    }

    private Data load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Data d = Data.get(id)
        if (!d || d.npubOwner != npub) throw new IllegalArgumentException("data record not found: ${id}")
        d
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
