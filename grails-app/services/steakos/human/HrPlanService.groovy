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

@Slf4j
class HrPlanService {

    @Transactional
    HeadcountPlan create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        HeadcountPlan p = new HeadcountPlan(
                npubOwner    : npub,
                name         : name,
                description  : fields.description as String,
                periodStart  : parseDate(fields.periodStart),
                periodEnd    : parseDate(fields.periodEnd),
                plannedCount : fields.plannedCount != null && fields.plannedCount != '' ? fields.plannedCount as Integer : 0,
                roleLabel    : fields.roleLabel as String,
                planStatus   : (fields.planStatus ?: 'DRAFT') as String,
                status       : (fields.planStatus ?: 'DRAFT') as String
        )
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional
    HeadcountPlan update(Long id, String npubOwner, Map fields) {
        HeadcountPlan p = load(id, npubOwner)
        if (fields.name) p.name = fields.name as String
        if (fields.containsKey('description')) p.description = fields.description as String
        if (fields.containsKey('plannedCount')) p.plannedCount = fields.plannedCount as Integer
        if (fields.containsKey('roleLabel')) p.roleLabel = fields.roleLabel as String
        if (fields.planStatus) {
            p.planStatus = fields.planStatus as String
            p.status = p.planStatus
        }
        if (fields.containsKey('periodStart')) p.periodStart = parseDate(fields.periodStart)
        if (fields.containsKey('periodEnd')) p.periodEnd = parseDate(fields.periodEnd)
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional(readOnly = true)
    List<HeadcountPlan> list(String npubOwner) {
        listPage(npubOwner, 200, 0).items as List<HeadcountPlan>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        return [
                items: HeadcountPlan.findAllByNpubOwner(npub, opts),
                total: HeadcountPlan.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    HeadcountPlan get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(HeadcountPlan p) {
        if (!p) return null
        [
                id          : p.id,
                name        : p.name,
                description : p.description,
                periodStart : p.periodStart,
                periodEnd   : p.periodEnd,
                plannedCount: p.plannedCount,
                roleLabel   : p.roleLabel,
                planStatus  : p.planStatus,
                npubOwner   : p.npubOwner
        ]
    }

    private HeadcountPlan load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        HeadcountPlan p = HeadcountPlan.get(id)
        if (!p || p.npubOwner != npub) throw new IllegalArgumentException("headcount plan not found: ${id}")
        p
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

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
