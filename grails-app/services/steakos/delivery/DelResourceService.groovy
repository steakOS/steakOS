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

package steakos.delivery

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.human.HRFile
import steakos.human.HrFileService

/**
 * Delivery resources (APQC 5.2). Human assignees sync from active HR employment files.
 */
@Slf4j
class DelResourceService {

    HrFileService hrFileService

    @Transactional
    Resource createResource(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Resource r = new Resource(
                npubOwner     : npub,
                name          : name,
                description   : fields.description as String,
                resourceKind  : (fields.resourceKind ?: 'HUMAN') as String,
                capacityHours : toBd(fields.capacityHours),
                agentRefId    : fields.agentRefId != null && fields.agentRefId != '' ? fields.agentRefId as Long : null,
                hrFileId      : fields.hrFileId != null && fields.hrFileId != '' ? fields.hrFileId as Long : null,
                personRef     : fields.personRef as String,
                email         : fields.email as String,
                active        : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE,
                status        : (fields.status ?: 'ACTIVE') as String
        )
        r.save(failOnError: true, flush: true)
        return r
    }

    @Transactional
    Resource updateResource(Long id, String npubOwner, Map fields) {
        Resource r = load(id, npubOwner)
        if (fields.name) r.name = fields.name as String
        if (fields.containsKey('description')) r.description = fields.description as String
        if (fields.resourceKind) r.resourceKind = fields.resourceKind as String
        if (fields.containsKey('capacityHours')) r.capacityHours = toBd(fields.capacityHours)
        if (fields.containsKey('agentRefId')) {
            r.agentRefId = fields.agentRefId != null && fields.agentRefId != '' ? fields.agentRefId as Long : null
        }
        if (fields.containsKey('hrFileId')) {
            r.hrFileId = fields.hrFileId != null && fields.hrFileId != '' ? fields.hrFileId as Long : null
        }
        if (fields.containsKey('personRef')) r.personRef = fields.personRef as String
        if (fields.containsKey('email')) r.email = fields.email as String
        if (fields.containsKey('active')) r.active = fields.active as Boolean
        r.save(failOnError: true, flush: true)
        return r
    }

    @Transactional(readOnly = true)
    Resource getResource(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Resource> listResources(String npubOwner, boolean activeOnly = true) {
        listResourcesPage(npubOwner, activeOnly, 200, 0).items as List<Resource>
    }

    /**
     * Paged resources. Optional free-text {@code q} on name/email/personRef.
     * When {@code assigneesOnly}, sync from active HR files and return HUMAN rows linked to employment.
     */
    @Transactional
    Map listResourcesPage(String npubOwner, boolean activeOnly = true, int max = 25, int offset = 0,
                          String q = null, boolean assigneesOnly = false) {
        String npub = requireNpub(npubOwner)
        if (assigneesOnly) {
            syncHumanAssigneesFromHr(npub)
        }
        String term = q?.trim() ? "%${q.trim()}%" : null
        List items = Resource.createCriteria().list(max: max, offset: offset, sort: 'name', order: 'asc') {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            if (assigneesOnly) {
                eq('resourceKind', 'HUMAN')
                isNotNull('hrFileId')
            }
            if (term) {
                or {
                    ilike('name', term)
                    ilike('email', term)
                    ilike('personRef', term)
                }
            }
        } as List
        Number total = Resource.createCriteria().count {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            if (assigneesOnly) {
                eq('resourceKind', 'HUMAN')
                isNotNull('hrFileId')
            }
            if (term) {
                or {
                    ilike('name', term)
                    ilike('email', term)
                    ilike('personRef', term)
                }
            }
        } as Number
        [items: items, total: total?.intValue() ?: 0]
    }

    /**
     * Ensure one active HUMAN Resource per ACTIVE HRFile; deactivate HR-linked resources
     * whose employment is no longer ACTIVE.
     */
    @Transactional
    int syncHumanAssigneesFromHr(String npubOwner) {
        String npub = requireNpub(npubOwner)
        if (hrFileService == null) {
            log.warn('HrFileService not wired — cannot sync delivery assignees from HR')
            return 0
        }
        List<HRFile> active = hrFileService.listActive(npub) as List<HRFile>
        Set<Long> activeIds = active.collect { it.id } as Set<Long>

        Resource.findAllByNpubOwnerAndHrFileIdIsNotNull(npub).each { Resource r ->
            if (!(r.hrFileId in activeIds)) {
                if (r.active != Boolean.FALSE) {
                    r.active = Boolean.FALSE
                    r.status = 'INACTIVE'
                    r.save(failOnError: true, flush: false)
                }
            }
        }

        int upserted = 0
        active.each { HRFile f ->
            Resource r = Resource.findByNpubOwnerAndHrFileId(npub, f.id)
            if (!r) {
                r = new Resource(
                        npubOwner   : npub,
                        resourceKind: 'HUMAN',
                        hrFileId    : f.id,
                        status      : 'ACTIVE',
                        active      : Boolean.TRUE
                )
            }
            String display = f.name?.toString()?.trim()
            if (!display) display = f.username ?: "Employee ${f.id}"
            r.name = display
            r.email = f.email
            r.personRef = f.personRef ?: f.username
            r.resourceKind = 'HUMAN'
            r.hrFileId = f.id
            r.active = Boolean.TRUE
            r.status = 'ACTIVE'
            r.save(failOnError: true, flush: false)
            upserted++
        }
        Resource.withSession { it.flush() }
        log.debug('Synced {} HR human assignees for {}', upserted, npub)
        return upserted
    }

    Map toMap(Resource r) {
        if (!r) return null
        [
                id           : r.id,
                name         : r.name,
                description  : r.description,
                resourceKind : r.resourceKind,
                capacityHours: r.capacityHours,
                agentRefId   : r.agentRefId,
                hrFileId     : r.hrFileId,
                personRef    : r.personRef,
                email        : r.email,
                active       : r.active,
                status       : r.status,
                npubOwner    : r.npubOwner
        ]
    }

    private Resource load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Resource r = Resource.get(id)
        if (!r || r.npubOwner != npub) throw new IllegalArgumentException("resource not found: ${id}")
        r
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') return null
        if (v instanceof BigDecimal) return v
        new BigDecimal(v.toString())
    }
}
