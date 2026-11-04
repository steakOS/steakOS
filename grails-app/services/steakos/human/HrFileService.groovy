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
import steakos.UserRole
import steakos.security.RoleLevel

/**
 * Employment file master (APQC 7.7.3 / 7.6). HR1: User required; names encrypted via domain converter.
 * Agent principals (ROLE_AI / Hive*:agentKey) live only in the Agents desk — never as HR employment.
 */
@Slf4j
class HrFileService {

    HrUserSupportService hrUserSupportService

    @Transactional
    HRFile create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')

        Map userResult = hrUserSupportService.resolveOrCreateUser(fields + [name: name, enabled: false])
        User u = userResult.user as User
        rejectAgentPrincipal(u)

        if (HRFile.findByNpubOwnerAndUserId(npub, u.id)) {
            throw new IllegalStateException("HRFile already exists for user ${u.username}")
        }

        HRFile f = new HRFile(
                npubOwner        : npub,
                name             : name,
                description      : fields.description as String,
                userId           : u.id,
                username         : u.username,
                employmentKind   : (fields.employmentKind ?: 'EMPLOYEE') as String,
                employmentStatus : (fields.employmentStatus ?: 'ACTIVE') as String,
                hireDate         : parseDate(fields.hireDate) ?: new Date(),
                email            : fields.email as String,
                personRef        : fields.personRef as String,
                status           : (fields.employmentStatus ?: 'ACTIVE') as String
        )
        f.save(failOnError: true, flush: true)
        log.info('HRFile {} for user {} (userCreated={})', f.id, u.username, userResult.created)
        return f
    }

    @Transactional
    HRFile update(Long id, String npubOwner, Map fields) {
        HRFile f = load(id, npubOwner)
        if (fields.name) f.name = fields.name as String
        if (fields.containsKey('description')) f.description = fields.description as String
        if (fields.employmentKind) f.employmentKind = fields.employmentKind as String
        if (fields.containsKey('email')) f.email = fields.email as String
        if (fields.containsKey('personRef')) f.personRef = fields.personRef as String
        if (fields.containsKey('hireDate')) f.hireDate = parseDate(fields.hireDate)
        f.save(failOnError: true, flush: true)
        return f
    }

    @Transactional
    HRFile separate(Long id, String npubOwner, Map fields = [:]) {
        HRFile f = load(id, npubOwner)
        f.employmentStatus = 'EXITED'
        f.status = 'EXITED'
        f.exitDate = parseDate(fields.exitDate) ?: new Date()
        f.save(failOnError: true, flush: true)
        log.info('HRFile {} EXITED', f.id)
        return f
    }

    @Transactional(readOnly = true)
    List<HRFile> list(String npubOwner, String employmentStatus = null) {
        listPage(npubOwner, employmentStatus, 500, 0).items as List<HRFile>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    static final List FILE_SORT_FIELDS = [
            'id', 'name', 'status', 'code', 'dateCreated', 'lastUpdated',
            'employmentStatus', 'employmentKind', 'username'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String employmentStatus = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', FILE_SORT_FIELDS)
        List items = HRFile.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (employmentStatus) eq('employmentStatus', employmentStatus)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = HRFile.createCriteria().count {
            eq('npubOwner', npub)
            if (employmentStatus) eq('employmentStatus', employmentStatus)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<HRFile> listActive(String npubOwner) {
        list(npubOwner, 'ACTIVE')
    }

    @Transactional(readOnly = true)
    HRFile get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(HRFile f) {
        if (!f) return null
        User u = f.userId ? User.get(f.userId) : null
        [
                id              : f.id,
                name            : f.name,
                description     : f.description,
                userId          : f.userId,
                username        : f.username,
                userEnabled     : u?.enabled,
                employmentKind  : f.employmentKind,
                employmentStatus: f.employmentStatus,
                hireDate        : f.hireDate,
                exitDate        : f.exitDate,
                email           : f.email,
                personRef       : f.personRef,
                npubOwner       : f.npubOwner
        ]
    }

    private HRFile load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        HRFile f = HRFile.get(id)
        if (!f || f.npubOwner != npub) throw new IllegalArgumentException("hr file not found: ${id}")
        f
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    /**
     * Agents (Hermes token principals) are not employees — keep them out of HR / assignees.
     */
    private static void rejectAgentPrincipal(User u) {
        if (!u) return
        String uname = u.username?.toString() ?: ''
        boolean aiRole = UserRole.findAllByUser(u).any { UserRole ur ->
            ur.role?.authority == RoleLevel.AI.authority
        }
        // HiveA:bookkeeper-style agent usernames
        boolean agentUsername = uname.contains(':')
        if (aiRole || agentUsername) {
            throw new IllegalArgumentException(
                    "Agents are not HR employees (user=${uname}). Manage them under Agents / Hives.")
        }
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
}
