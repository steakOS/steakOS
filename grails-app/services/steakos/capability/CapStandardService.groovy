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

/** Standards / KPIs thin (APQC 13.7). */
@Slf4j
class CapStandardService {

    @Transactional
    Standard create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Standard s = new Standard(
                npubOwner     : npub,
                name          : name,
                description   : fields.description as String,
                metricCode    : fields.metricCode as String,
                targetValue   : fields.targetValue as String,
                unit          : fields.unit as String,
                standardStatus: (fields.standardStatus ?: 'ACTIVE') as String,
                status        : (fields.standardStatus ?: 'ACTIVE') as String
        )
        s.save(failOnError: true, flush: true)
        return s
    }

    @Transactional
    Standard update(Long id, String npubOwner, Map fields) {
        Standard s = load(id, npubOwner)
        if (fields.name) s.name = fields.name as String
        if (fields.containsKey('description')) s.description = fields.description as String
        if (fields.containsKey('metricCode')) s.metricCode = fields.metricCode as String
        if (fields.containsKey('targetValue')) s.targetValue = fields.targetValue as String
        if (fields.containsKey('unit')) s.unit = fields.unit as String
        if (fields.standardStatus) {
            String st = fields.standardStatus as String
            if (!(st in ['ACTIVE', 'INACTIVE'])) throw new IllegalArgumentException("invalid standardStatus: ${st}")
            s.standardStatus = st
            s.status = st
        }
        s.save(failOnError: true, flush: true)
        return s
    }

    @Transactional(readOnly = true)
    Standard get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Standard> list(String npubOwner, String standardStatus = null) {
        listPage(npubOwner, standardStatus, 500, 0).items as List<Standard>
    }

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String standardStatus = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        if (standardStatus) {
            return [
                    items: Standard.findAllByNpubOwnerAndStandardStatus(npub, standardStatus, opts),
                    total: Standard.countByNpubOwnerAndStandardStatus(npub, standardStatus) as int
            ]
        }
        return [
                items: Standard.findAllByNpubOwner(npub, opts),
                total: Standard.countByNpubOwner(npub) as int
        ]
    }

    Map toMap(Standard s) {
        if (!s) return null
        [
                id            : s.id,
                name          : s.name,
                description   : s.description,
                metricCode    : s.metricCode,
                targetValue   : s.targetValue,
                unit          : s.unit,
                standardStatus: s.standardStatus,
                npubOwner     : s.npubOwner
        ]
    }

    private Standard load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Standard s = Standard.get(id)
        if (!s || s.npubOwner != npub) throw new IllegalArgumentException("standard not found: ${id}")
        s
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
