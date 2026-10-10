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
 * Platform / runtime systems (APQC 8.2, DIG1).
 * Domain class is {@link System} — always fully qualify to avoid java.lang.System clash.
 */
@Slf4j
class DigSystemService {

    @Transactional
    steakos.digital.System create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        steakos.digital.System s = new steakos.digital.System(
                npubOwner       : npub,
                name            : name,
                description     : fields.description as String,
                systemKey       : fields.systemKey as String,
                environment     : (fields.environment ?: 'PROD') as String,
                architectureNote: fields.architectureNote as String,
                active          : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE,
                status          : 'ACTIVE'
        )
        s.save(failOnError: true, flush: true)
        return s
    }

    @Transactional
    steakos.digital.System update(Long id, String npubOwner, Map fields) {
        steakos.digital.System s = load(id, npubOwner)
        if (fields.name) s.name = fields.name as String
        if (fields.containsKey('description')) s.description = fields.description as String
        if (fields.containsKey('systemKey')) s.systemKey = fields.systemKey as String
        if (fields.environment) s.environment = fields.environment as String
        if (fields.containsKey('architectureNote')) s.architectureNote = fields.architectureNote as String
        if (fields.containsKey('active')) s.active = fields.active as Boolean
        s.save(failOnError: true, flush: true)
        return s
    }

    @Transactional(readOnly = true)
    steakos.digital.System get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<steakos.digital.System> list(String npubOwner, boolean activeOnly = false) {
        listPage(npubOwner, activeOnly, 500, 0).items as List<steakos.digital.System>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, boolean activeOnly = false, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'name', order: 'asc', max: max, offset: offset]
        if (activeOnly) {
            return [
                    items: steakos.digital.System.findAllByNpubOwnerAndActive(npub, Boolean.TRUE, opts),
                    total: steakos.digital.System.countByNpubOwnerAndActive(npub, Boolean.TRUE) as int
            ]
        }
        return [
                items: steakos.digital.System.findAllByNpubOwner(npub, opts),
                total: steakos.digital.System.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    steakos.digital.System getByKey(String npubOwner, String systemKey) {
        String npub = requireNpub(npubOwner)
        if (!systemKey?.trim()) return null
        steakos.digital.System.findByNpubOwnerAndSystemKey(npub, systemKey.trim())
    }

    Map toMap(steakos.digital.System s) {
        if (!s) return null
        [
                id              : s.id,
                name            : s.name,
                description     : s.description,
                systemKey       : s.systemKey,
                environment     : s.environment,
                architectureNote: s.architectureNote,
                active          : s.active,
                npubOwner       : s.npubOwner
        ]
    }

    private steakos.digital.System load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        steakos.digital.System s = steakos.digital.System.get(id)
        if (!s || s.npubOwner != npub) throw new IllegalArgumentException("system not found: ${id}")
        s
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
