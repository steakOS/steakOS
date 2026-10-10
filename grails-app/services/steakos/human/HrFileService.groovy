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
 * Employment file master (APQC 7.7.3 / 7.6). HR1: User required; names encrypted via domain converter.
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
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String employmentStatus = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        if (employmentStatus) {
            return [
                    items: HRFile.findAllByNpubOwnerAndEmploymentStatus(npub, employmentStatus, opts),
                    total: HRFile.countByNpubOwnerAndEmploymentStatus(npub, employmentStatus) as int
            ]
        }
        return [
                items: HRFile.findAllByNpubOwner(npub, opts),
                total: HRFile.countByNpubOwner(npub) as int
        ]
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
