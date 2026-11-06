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

package steakos.grrc

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.helper.AssignableSupport

/**
 * GRRCBasics compliance policies light CRUD (G7). Company-scoped via npubOwner.
 */
@Slf4j
class GrrcComplianceService {

    @Transactional
    ComplianceItem create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        ComplianceItem c = new ComplianceItem(
                npubOwner     : npub,
                name          : name,
                description   : fields.description as String,
                itemKind      : (fields.itemKind ?: 'POLICY') as String,
                status        : (fields.status ?: 'DRAFT') as String,
                ownerName     : fields.ownerName as String,
                effectiveFrom : parseDate(fields.effectiveFrom),
                *: AssignableSupport.initialPair(fields)
        )
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    ComplianceItem update(Long id, String npubOwner, Map fields) {
        ComplianceItem c = load(id, npubOwner)
        if (fields.name) c.name = fields.name as String
        if (fields.containsKey('description')) c.description = fields.description as String
        if (fields.itemKind) c.itemKind = fields.itemKind as String
        if (fields.status) c.status = fields.status as String
        if (fields.containsKey('ownerName')) c.ownerName = fields.ownerName as String
        if (fields.containsKey('effectiveFrom')) c.effectiveFrom = parseDate(fields.effectiveFrom)
        AssignableSupport.apply(c, fields)
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    void delete(Long id, String npubOwner) {
        ComplianceItem c = load(id, npubOwner)
        c.delete(flush: true)
    }

    @Transactional(readOnly = true)
    ComplianceItem get(Long id, String npubOwner) { load(id, npubOwner) }

    static final List COMPLIANCE_SORT_FIELDS = [
            'id', 'name', 'status', 'itemKind', 'ownerName', 'effectiveFrom',
            'assigneeHumanNpub', 'assigneeAgentId'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String status = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', COMPLIANCE_SORT_FIELDS)
        String st = status?.trim() ? status.trim().toUpperCase() : null
        List items = ComplianceItem.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (st) eq('status', st)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = ComplianceItem.createCriteria().count {
            eq('npubOwner', npub)
            if (st) eq('status', st)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    Map toMap(ComplianceItem c) {
        if (!c) return null
        [
                id           : c.id,
                name         : c.name,
                description  : c.description,
                itemKind     : c.itemKind,
                status       : c.status,
                ownerName    : c.ownerName,
                effectiveFrom: c.effectiveFrom?.time,
                npubOwner    : c.npubOwner,
                *: AssignableSupport.toMapFragment(c)
        ]
    }

    private ComplianceItem load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        ComplianceItem c = ComplianceItem.get(id)
        if (!c || c.npubOwner != npub) throw new IllegalArgumentException("compliance item not found: ${id}")
        c
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    private static Date parseDate(Object raw) {
        if (raw == null || raw.toString().trim() == '') return null
        if (raw instanceof Date) return (Date) raw
        if (raw instanceof Number) return new Date(((Number) raw).longValue())
        String s = raw.toString().trim()
        // epoch millis
        if (s ==~ /^\d{10,13}$/) {
            long v = Long.parseLong(s)
            if (s.length() <= 10) v *= 1000L
            return new Date(v)
        }
        // ISO-8601 date or datetime (yyyy-MM-dd…)
        try {
            if (s.length() >= 10) {
                return Date.parse('yyyy-MM-dd', s.substring(0, 10))
            }
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException("invalid effectiveFrom: ${raw}")
    }
}
