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

package steakos.human

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.User

/**
 * Non-employee party access without full HRFile (HR1).
 */
@Slf4j
class HrPartyService {

    HrUserSupportService hrUserSupportService

    @Transactional
    PartyAccess provision(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        String kind = (fields.partyKind ?: 'OTHER').toString().toUpperCase()
        if (!(kind in ['BUSINESS_PARTNER', 'EXTERNAL_WORKER', 'STUDENT', 'TEMPORARY', 'OTHER'])) {
            throw new IllegalArgumentException("invalid partyKind: ${kind}")
        }

        // New users deactivated by default (HR1)
        Map userResult = hrUserSupportService.resolveOrCreateUser(fields + [name: name, enabled: false])
        User u = userResult.user as User

        PartyAccess pa = new PartyAccess(
                npubOwner : npub,
                name      : name,
                description: fields.description as String,
                userId    : u.id,
                username  : u.username,
                partyKind : kind,
                active    : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE,
                notes     : fields.notes as String,
                status    : 'ACTIVE'
        )
        pa.save(failOnError: true, flush: true)
        log.info('PartyAccess {} kind={} user={}', pa.id, kind, u.username)
        return pa
    }

    @Transactional(readOnly = true)
    List<PartyAccess> list(String npubOwner, String partyKind = null) {
        listPage(npubOwner, partyKind, 300, 0).items as List<PartyAccess>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String partyKind = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        if (partyKind) {
            return [
                    items: PartyAccess.findAllByNpubOwnerAndPartyKind(npub, partyKind, opts),
                    total: PartyAccess.countByNpubOwnerAndPartyKind(npub, partyKind) as int
            ]
        }
        return [
                items: PartyAccess.findAllByNpubOwner(npub, opts),
                total: PartyAccess.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    PartyAccess get(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        PartyAccess p = PartyAccess.get(id)
        if (!p || p.npubOwner != npub) throw new IllegalArgumentException("party access not found: ${id}")
        p
    }

    Map toMap(PartyAccess p) {
        if (!p) return null
        User u = p.userId ? User.get(p.userId) : null
        [
                id         : p.id,
                name       : p.name,
                description: p.description,
                userId     : p.userId,
                username   : p.username,
                userEnabled: u?.enabled,
                partyKind  : p.partyKind,
                active     : p.active,
                notes      : p.notes,
                npubOwner  : p.npubOwner
        ]
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
