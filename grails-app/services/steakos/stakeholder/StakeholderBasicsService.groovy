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

package steakos.stakeholder

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames
import steakos.helper.AssignableSupport

/**
 * StakeholderBasics thin kernel (G7 / WP6): party register + communications log.
 * Metal gate is enforced by {@link StakeholderGateService} / interceptor (SILVER+ / IRON).
 */
@Slf4j
class StakeholderBasicsService {

    DomainEventBusService domainEventBusService

    // ----- Parties -----

    @Transactional
    StakeholderParty createParty(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        String kind = (fields.partyKind ?: 'OTHER').toString().trim().toUpperCase()
        if (!(kind in ['REGULATOR', 'COMMUNITY', 'MEDIA', 'PARTNER', 'NGO', 'INVESTOR', 'OTHER'])) {
            throw new IllegalArgumentException("invalid partyKind: ${kind}")
        }
        String st = (fields.status ?: 'ACTIVE').toString().trim().toUpperCase()
        if (!(st in ['ACTIVE', 'INACTIVE', 'WATCH'])) {
            throw new IllegalArgumentException("invalid status: ${st}")
        }
        StakeholderParty party = new StakeholderParty(
                npubOwner   : npub,
                name        : name,
                description : fields.description as String,
                partyKind   : kind,
                contactName : fields.contactName as String,
                contactEmail: fields.contactEmail as String,
                ethicsNote  : fields.ethicsNote as String,
                externalRef : fields.externalRef as String,
                status      : st,
                *: AssignableSupport.initialPair(fields)
        )
        party.markCreatedBy(npub)
        party.save(failOnError: true, flush: true)
        return party
    }

    @Transactional
    StakeholderParty updateParty(Long id, String npubOwner, Map fields) {
        StakeholderParty party = loadParty(id, npubOwner)
        if (fields.name != null) {
            String name = fields.name.toString().trim()
            if (!name) throw new IllegalArgumentException('name is required')
            party.name = name
        }
        if (fields.containsKey('description')) party.description = fields.description as String
        if (fields.partyKind) {
            String kind = fields.partyKind.toString().trim().toUpperCase()
            if (!(kind in ['REGULATOR', 'COMMUNITY', 'MEDIA', 'PARTNER', 'NGO', 'INVESTOR', 'OTHER'])) {
                throw new IllegalArgumentException("invalid partyKind: ${kind}")
            }
            party.partyKind = kind
        }
        if (fields.containsKey('contactName')) party.contactName = fields.contactName as String
        if (fields.containsKey('contactEmail')) party.contactEmail = fields.contactEmail as String
        if (fields.containsKey('ethicsNote')) party.ethicsNote = fields.ethicsNote as String
        if (fields.containsKey('externalRef')) party.externalRef = fields.externalRef as String
        if (fields.status) {
            String st = fields.status.toString().trim().toUpperCase()
            if (!(st in ['ACTIVE', 'INACTIVE', 'WATCH'])) {
                throw new IllegalArgumentException("invalid status: ${st}")
            }
            party.status = st
        }
        AssignableSupport.apply(party, fields)
        party.markUpdatedBy(requireNpub(npubOwner))
        party.save(failOnError: true, flush: true)
        return party
    }

    @Transactional(readOnly = true)
    StakeholderParty getParty(Long id, String npubOwner) {
        loadParty(id, npubOwner)
    }

    static final List PARTY_SORT_FIELDS = [
            'id', 'name', 'status', 'partyKind', 'contactName', 'contactEmail',
            'assigneeHumanNpub', 'assigneeAgentId'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPartiesPage(String npubOwner, String status = null, String partyKind = null,
                        int max = 25, int offset = 0, Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', PARTY_SORT_FIELDS)
        List items = StakeholderParty.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (status) eq('status', status)
            if (partyKind) eq('partyKind', partyKind)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = StakeholderParty.createCriteria().count {
            eq('npubOwner', npub)
            if (status) eq('status', status)
            if (partyKind) eq('partyKind', partyKind)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional
    void deleteParty(Long id, String npubOwner) {
        StakeholderParty party = loadParty(id, npubOwner)
        int linked = CommsLog.countByNpubOwnerAndStakeholderPartyId(requireNpub(npubOwner), id) as int
        if (linked > 0) {
            throw new IllegalArgumentException("cannot delete party with ${linked} communications log entries")
        }
        party.delete(flush: true)
    }

    Map toPartyMap(StakeholderParty p) {
        if (!p) return null
        [
                id          : p.id,
                name        : p.name,
                description : p.description,
                partyKind   : p.partyKind,
                contactName : p.contactName,
                contactEmail: p.contactEmail,
                ethicsNote  : p.ethicsNote,
                externalRef : p.externalRef,
                status      : p.status,
                npubOwner   : p.npubOwner,
                dateCreated : p.dateCreated,
                lastUpdated : p.lastUpdated,
                *: AssignableSupport.toMapFragment(p)
        ]
    }

    // ----- Comms log -----

    @Transactional
    CommsLog createComms(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Long partyId = longOrNull(fields.stakeholderPartyId ?: fields.partyId)
        if (!partyId) throw new IllegalArgumentException('stakeholderPartyId is required')
        loadParty(partyId, npub)
        String subject = fields.subject?.toString()?.trim()
        if (!subject) throw new IllegalArgumentException('subject is required')
        String channel = (fields.channel ?: 'OTHER').toString().trim().toUpperCase()
        if (!(channel in ['EMAIL', 'MEETING', 'CALL', 'PRESS', 'OTHER'])) {
            throw new IllegalArgumentException("invalid channel: ${channel}")
        }
        String direction = (fields.direction ?: 'OUT').toString().trim().toUpperCase()
        if (!(direction in ['IN', 'OUT', 'INTERNAL'])) {
            throw new IllegalArgumentException("invalid direction: ${direction}")
        }
        String st = (fields.status ?: 'LOGGED').toString().trim().toUpperCase()
        if (!(st in ['LOGGED', 'DRAFT'])) {
            throw new IllegalArgumentException("invalid status: ${st}")
        }
        Date occurred = parseDate(fields.occurredAt) ?: new Date()
        CommsLog entry = new CommsLog(
                npubOwner          : npub,
                stakeholderPartyId : partyId,
                subject            : subject,
                summary            : fields.summary as String,
                channel            : channel,
                direction          : direction,
                occurredAt         : occurred,
                status             : st,
                *: AssignableSupport.initialPair(fields)
        )
        entry.markCreatedBy(npub)
        entry.save(failOnError: true, flush: true)
        if (st == 'LOGGED' && domainEventBusService != null) {
            try {
                domainEventBusService.publish(
                        DomainEventNames.STAKEHOLDER_COMMUNICATION_LOGGED,
                        'steakos.stakeholder',
                        npub,
                        [commsLogId: entry.id, stakeholderPartyId: partyId, channel: channel]
                )
            } catch (Exception e) {
                log.warn("stakeholder.communication.logged publish failed: ${e.message}")
            }
        }
        return entry
    }

    @Transactional
    CommsLog updateComms(Long id, String npubOwner, Map fields) {
        CommsLog entry = loadComms(id, npubOwner)
        if (fields.containsKey('stakeholderPartyId') || fields.containsKey('partyId')) {
            Long partyId = longOrNull(fields.stakeholderPartyId ?: fields.partyId)
            if (!partyId) throw new IllegalArgumentException('stakeholderPartyId is required')
            loadParty(partyId, npubOwner)
            entry.stakeholderPartyId = partyId
        }
        if (fields.subject != null) {
            String subject = fields.subject.toString().trim()
            if (!subject) throw new IllegalArgumentException('subject is required')
            entry.subject = subject
        }
        if (fields.containsKey('summary')) entry.summary = fields.summary as String
        if (fields.channel) {
            String channel = fields.channel.toString().trim().toUpperCase()
            if (!(channel in ['EMAIL', 'MEETING', 'CALL', 'PRESS', 'OTHER'])) {
                throw new IllegalArgumentException("invalid channel: ${channel}")
            }
            entry.channel = channel
        }
        if (fields.direction) {
            String direction = fields.direction.toString().trim().toUpperCase()
            if (!(direction in ['IN', 'OUT', 'INTERNAL'])) {
                throw new IllegalArgumentException("invalid direction: ${direction}")
            }
            entry.direction = direction
        }
        if (fields.containsKey('occurredAt')) {
            entry.occurredAt = parseDate(fields.occurredAt) ?: entry.occurredAt
        }
        if (fields.status) {
            String st = fields.status.toString().trim().toUpperCase()
            if (!(st in ['LOGGED', 'DRAFT'])) {
                throw new IllegalArgumentException("invalid status: ${st}")
            }
            entry.status = st
        }
        AssignableSupport.apply(entry, fields)
        entry.markUpdatedBy(requireNpub(npubOwner))
        entry.save(failOnError: true, flush: true)
        return entry
    }

    @Transactional(readOnly = true)
    CommsLog getComms(Long id, String npubOwner) {
        loadComms(id, npubOwner)
    }

    @Transactional(readOnly = true)
    Map listCommsPage(String npubOwner, Long partyId = null, String status = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        List<CommsLog> items
        int total
        if (partyId && status) {
            items = CommsLog.findAllByNpubOwnerAndStakeholderPartyIdAndStatus(npub, partyId, status, opts)
            total = CommsLog.countByNpubOwnerAndStakeholderPartyIdAndStatus(npub, partyId, status) as int
        } else if (partyId) {
            items = CommsLog.findAllByNpubOwnerAndStakeholderPartyId(npub, partyId, opts)
            total = CommsLog.countByNpubOwnerAndStakeholderPartyId(npub, partyId) as int
        } else if (status) {
            items = CommsLog.findAllByNpubOwnerAndStatus(npub, status, opts)
            total = CommsLog.countByNpubOwnerAndStatus(npub, status) as int
        } else {
            items = CommsLog.findAllByNpubOwner(npub, opts)
            total = CommsLog.countByNpubOwner(npub) as int
        }
        [items: items, total: total]
    }

    @Transactional
    void deleteComms(Long id, String npubOwner) {
        CommsLog entry = loadComms(id, npubOwner)
        entry.delete(flush: true)
    }

    Map toCommsMap(CommsLog c) {
        if (!c) return null
        [
                id                 : c.id,
                stakeholderPartyId : c.stakeholderPartyId,
                subject            : c.subject,
                summary            : c.summary,
                channel            : c.channel,
                direction          : c.direction,
                occurredAt         : c.occurredAt,
                status             : c.status,
                npubOwner          : c.npubOwner,
                dateCreated        : c.dateCreated,
                lastUpdated        : c.lastUpdated,
                *: AssignableSupport.toMapFragment(c)
        ]
    }

    // ----- helpers -----

    private StakeholderParty loadParty(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        StakeholderParty party = StakeholderParty.get(id)
        if (!party || party.npubOwner != npub) {
            throw new IllegalArgumentException("stakeholder party not found: ${id}")
        }
        party
    }

    private CommsLog loadComms(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        CommsLog entry = CommsLog.get(id)
        if (!entry || entry.npubOwner != npub) {
            throw new IllegalArgumentException("comms log not found: ${id}")
        }
        entry
    }

    private static Long longOrNull(Object v) {
        if (v == null || v == '') return null
        return v as Long
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    private static Date parseDate(Object raw) {
        if (raw == null) return null
        if (raw instanceof Date) return (Date) raw
        String s = raw.toString().trim()
        if (!s) return null
        try {
            if (s.isLong()) {
                return new Date(s.toLong())
            }
            return Date.parse("yyyy-MM-dd'T'HH:mm:ss", s.take(19))
        } catch (Exception ignored) {
            try {
                return Date.parse('yyyy-MM-dd', s.take(10))
            } catch (Exception ignored2) {
                return null
            }
        }
    }
}
