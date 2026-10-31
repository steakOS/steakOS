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

/**
 * Candidate pipeline + hire (APQC 7.2, HR2).
 */
@Slf4j
class HrRecruitService {

    HrFileService hrFileService

    @Transactional
    Candidate createCandidate(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Candidate c = new Candidate(
                npubOwner : npub,
                name      : name,
                description: fields.description as String,
                email     : fields.email as String,
                source    : fields.source as String,
                roleLabel : fields.roleLabel as String,
                stage     : (fields.stage ?: 'APPLIED') as String,
                appliedAt : new Date(),
                status    : (fields.stage ?: 'APPLIED') as String
        )
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    Candidate advanceStage(Long id, String npubOwner, String stage) {
        Candidate c = load(id, npubOwner)
        if (c.stage in ['HIRED', 'REJECTED', 'WITHDRAWN']) {
            throw new IllegalStateException("cannot advance from ${c.stage}")
        }
        String s = stage?.toUpperCase()
        if (!(s in ['APPLIED', 'SCREEN', 'OFFER', 'HIRED', 'REJECTED', 'WITHDRAWN'])) {
            throw new IllegalArgumentException("invalid stage: ${stage}")
        }
        if (s == 'HIRED') {
            return hire(id, npubOwner, [:])
        }
        c.stage = s
        c.status = s
        c.save(failOnError: true, flush: true)
        return c
    }

    /**
     * HR2: always create/link HRFile + User (new User deactivated).
     */
    @Transactional
    Map hire(Long id, String npubOwner, Map fields = [:]) {
        Candidate c = load(id, npubOwner)
        if (c.stage == 'HIRED' && c.hrFileId) {
            return [candidate: toMap(c), hrFile: hrFileService.toMap(hrFileService.get(c.hrFileId, npubOwner)), alreadyHired: true]
        }
        if (c.stage in ['REJECTED', 'WITHDRAWN']) {
            throw new IllegalStateException("cannot hire ${c.stage}")
        }

        HRFile file
        if (fields.hrFileId) {
            file = hrFileService.get(fields.hrFileId as Long, npubOwner)
        } else {
            file = hrFileService.create(npubOwner, [
                    name          : c.name,
                    email         : c.email ?: fields.email,
                    username      : fields.username,
                    userId        : fields.userId,
                    employmentKind: fields.employmentKind ?: 'EMPLOYEE',
                    hireDate      : fields.hireDate ?: new Date(),
                    description   : "Hired from candidate ${c.id}"
            ])
        }

        c.stage = 'HIRED'
        c.status = 'HIRED'
        c.hrFileId = file.id
        c.hiredAt = new Date()
        c.save(failOnError: true, flush: true)
        log.info('Hired candidate {} → HRFile {}', c.id, file.id)
        return [candidate: toMap(c), hrFile: hrFileService.toMap(file), alreadyHired: false]
    }

    @Transactional
    Candidate reject(Long id, String npubOwner) {
        Candidate c = load(id, npubOwner)
        if (c.stage == 'HIRED') throw new IllegalStateException('cannot reject hired candidate')
        c.stage = 'REJECTED'
        c.status = 'REJECTED'
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional(readOnly = true)
    List<Candidate> list(String npubOwner, String stage = null) {
        listPage(npubOwner, stage, 300, 0).items as List<Candidate>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    static final List CANDIDATE_SORT_FIELDS = [
            'id', 'name', 'status', 'code', 'dateCreated', 'lastUpdated',
            'stage', 'roleLabel', 'email', 'appliedAt'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String stage = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', CANDIDATE_SORT_FIELDS)
        List items = Candidate.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (stage) eq('stage', stage)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Candidate.createCriteria().count {
            eq('npubOwner', npub)
            if (stage) eq('stage', stage)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    Candidate get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(Candidate c) {
        if (!c) return null
        [
                id         : c.id,
                name       : c.name,
                description: c.description,
                email      : c.email,
                source     : c.source,
                roleLabel  : c.roleLabel,
                stage      : c.stage,
                hrFileId   : c.hrFileId,
                appliedAt  : c.appliedAt,
                hiredAt    : c.hiredAt,
                npubOwner  : c.npubOwner
        ]
    }

    private Candidate load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Candidate c = Candidate.get(id)
        if (!c || c.npubOwner != npub) throw new IllegalArgumentException("candidate not found: ${id}")
        c
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
