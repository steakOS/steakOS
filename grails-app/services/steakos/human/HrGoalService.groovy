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
class HrGoalService {

    HrFileService hrFileService

    @Transactional
    Goal create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        HRFile file = null
        if (fields.hrFileId) {
            file = hrFileService.get(fields.hrFileId as Long, npub)
        }
        Goal g = new Goal(
                npubOwner : npub,
                name      : name,
                description: fields.description as String,
                hrFile    : file,
                dueDate   : parseDate(fields.dueDate),
                progress  : fields.progress != null && fields.progress != '' ? fields.progress as Integer : 0,
                goalStatus: 'OPEN',
                status    : 'OPEN'
        )
        g.save(failOnError: true, flush: true)
        return g
    }

    @Transactional
    Goal updateProgress(Long id, String npubOwner, Integer progress) {
        Goal g = load(id, npubOwner)
        if (g.goalStatus == 'CANCELLED') throw new IllegalStateException('cancelled goal')
        int p = progress != null ? progress : g.progress
        if (p < 0) p = 0
        if (p > 100) p = 100
        g.progress = p
        if (p >= 100) {
            g.goalStatus = 'DONE'
            g.status = 'DONE'
        }
        g.save(failOnError: true, flush: true)
        return g
    }

    @Transactional
    Goal complete(Long id, String npubOwner) {
        updateProgress(id, npubOwner, 100)
    }

    @Transactional
    Goal cancel(Long id, String npubOwner) {
        Goal g = load(id, npubOwner)
        g.goalStatus = 'CANCELLED'
        g.status = 'CANCELLED'
        g.save(failOnError: true, flush: true)
        return g
    }

    @Transactional(readOnly = true)
    List<Goal> list(String npubOwner) {
        listPage(npubOwner, 300, 0).items as List<Goal>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    static final List GOAL_SORT_FIELDS = [
            'id', 'name', 'status', 'code', 'dateCreated', 'lastUpdated',
            'goalStatus', 'progress', 'dueDate'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, int max = 25, int offset = 0, Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', GOAL_SORT_FIELDS)
        List items = Goal.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Goal.createCriteria().count {
            eq('npubOwner', npub)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    Goal get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(Goal g) {
        if (!g) return null
        [
                id        : g.id,
                name      : g.name,
                description: g.description,
                hrFileId  : g.hrFile?.id,
                hrFileName: g.hrFile?.name,
                dueDate   : g.dueDate,
                progress  : g.progress,
                goalStatus: g.goalStatus,
                npubOwner : g.npubOwner
        ]
    }

    private Goal load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Goal g = Goal.get(id)
        if (!g || g.npubOwner != npub) throw new IllegalArgumentException("goal not found: ${id}")
        g
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
