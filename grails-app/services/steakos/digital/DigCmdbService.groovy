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
 * CMDB-lite (DIG0). Manual CIs only.
 */
@Slf4j
class DigCmdbService {

    DigSystemService digSystemService
    DigSolutionService digSolutionService

    @Transactional
    ConfigItem createCi(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Long systemId = longOrNull(fields.systemId)
        Long solutionId = longOrNull(fields.solutionId)
        if (systemId) digSystemService.get(systemId, npub)
        if (solutionId) digSolutionService.get(solutionId, npub)

        ConfigItem ci = new ConfigItem(
                npubOwner  : npub,
                name       : name,
                description: fields.description as String,
                ciKind     : (fields.ciKind ?: 'OTHER') as String,
                hostname   : fields.hostname as String,
                ipAddress  : fields.ipAddress as String,
                ciStatus   : (fields.ciStatus ?: 'ACTIVE') as String,
                systemId   : systemId,
                solutionId : solutionId,
                status     : (fields.ciStatus ?: 'ACTIVE') as String
        )
        ci.save(failOnError: true, flush: true)
        return ci
    }

    @Transactional
    ConfigItem update(Long id, String npubOwner, Map fields) {
        ConfigItem ci = load(id, npubOwner)
        if (fields.name) ci.name = fields.name as String
        if (fields.containsKey('description')) ci.description = fields.description as String
        if (fields.ciKind) ci.ciKind = fields.ciKind as String
        if (fields.containsKey('hostname')) ci.hostname = fields.hostname as String
        if (fields.containsKey('ipAddress')) ci.ipAddress = fields.ipAddress as String
        if (fields.ciStatus) {
            ci.ciStatus = fields.ciStatus as String
            ci.status = ci.ciStatus
        }
        if (fields.containsKey('systemId')) {
            Long sid = longOrNull(fields.systemId)
            if (sid) digSystemService.get(sid, npubOwner)
            ci.systemId = sid
        }
        if (fields.containsKey('solutionId')) {
            Long soid = longOrNull(fields.solutionId)
            if (soid) digSolutionService.get(soid, npubOwner)
            ci.solutionId = soid
        }
        ci.save(failOnError: true, flush: true)
        return ci
    }

    @Transactional(readOnly = true)
    ConfigItem get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<ConfigItem> list(String npubOwner, Long systemId = null) {
        listPage(npubOwner, systemId, 500, 0).items as List<ConfigItem>
    }

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, Long systemId = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'name', order: 'asc', max: max, offset: offset]
        if (systemId) {
            return [
                    items: ConfigItem.findAllByNpubOwnerAndSystemId(npub, systemId, opts),
                    total: ConfigItem.countByNpubOwnerAndSystemId(npub, systemId) as int
            ]
        }
        return [
                items: ConfigItem.findAllByNpubOwner(npub, opts),
                total: ConfigItem.countByNpubOwner(npub) as int
        ]
    }

    Map toMap(ConfigItem ci) {
        if (!ci) return null
        [
                id        : ci.id,
                name      : ci.name,
                description: ci.description,
                ciKind    : ci.ciKind,
                hostname  : ci.hostname,
                ipAddress : ci.ipAddress,
                ciStatus  : ci.ciStatus,
                systemId  : ci.systemId,
                solutionId: ci.solutionId,
                npubOwner : ci.npubOwner
        ]
    }

    private ConfigItem load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        ConfigItem ci = ConfigItem.get(id)
        if (!ci || ci.npubOwner != npub) throw new IllegalArgumentException("config item not found: ${id}")
        ci
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
