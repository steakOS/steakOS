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
 * Time & attendance (APQC 7.7.6). HR4: hrFileId required.
 */
@Slf4j
class HrTimeService {

    HrFileService hrFileService

    @Transactional
    TimeTrack createEntry(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Long hrFileId = longOrNull(fields.hrFileId)
        if (!hrFileId) throw new IllegalArgumentException('hrFileId is required (HR4)')
        HRFile file = hrFileService.get(hrFileId, npub)

        BigDecimal hours = toBd(fields.hours)
        Date started = parseDateTime(fields.startedAt)
        Date ended = parseDateTime(fields.endedAt)
        if (hours == null && started && ended) {
            hours = BigDecimal.valueOf((ended.time - started.time) / 3600000d).setScale(2, java.math.RoundingMode.HALF_UP)
        }
        if (hours == null) hours = BigDecimal.ZERO
        if (hours < BigDecimal.ZERO) throw new IllegalArgumentException('hours must be >= 0')

        TimeTrack t = new TimeTrack(
                npubOwner : npub,
                name      : (fields.name ?: "${file.name} time") as String,
                description: fields.description as String,
                hrFile    : file,
                startedAt : started,
                endedAt   : ended,
                hours     : hours,
                timeKind  : (fields.timeKind ?: 'WORK') as String,
                timeStatus: (fields.timeStatus ?: 'POSTED') as String,
                status    : (fields.timeStatus ?: 'POSTED') as String
        )
        t.save(failOnError: true, flush: true)
        return t
    }

    @Transactional(readOnly = true)
    List<TimeTrack> list(String npubOwner, Long hrFileId = null) {
        listPage(npubOwner, hrFileId, 300, 0).items as List<TimeTrack>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). */
    static final List TIME_SORT_FIELDS = [
            'id', 'name', 'status', 'code', 'dateCreated', 'lastUpdated',
            'timeKind', 'timeStatus', 'hours', 'startedAt', 'endedAt'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, Long hrFileId = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', TIME_SORT_FIELDS)
        HRFile file = hrFileId ? hrFileService.get(hrFileId, npub) : null
        List items = TimeTrack.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (file) eq('hrFile', file)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = TimeTrack.createCriteria().count {
            eq('npubOwner', npub)
            if (file) eq('hrFile', file)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    TimeTrack get(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        TimeTrack t = TimeTrack.get(id)
        if (!t || t.npubOwner != npub) throw new IllegalArgumentException("time track not found: ${id}")
        t
    }

    Map toMap(TimeTrack t) {
        if (!t) return null
        [
                id        : t.id,
                name      : t.name,
                description: t.description,
                hrFileId  : t.hrFile?.id,
                hrFileName: t.hrFile?.name,
                startedAt : t.startedAt,
                endedAt   : t.endedAt,
                hours     : t.hours,
                timeKind  : t.timeKind,
                timeStatus: t.timeStatus,
                npubOwner : t.npubOwner
        ]
    }

    private static Long longOrNull(Object v) {
        if (v == null || v == '') return null
        return v as Long
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') return null
        if (v instanceof BigDecimal) return v
        new BigDecimal(v.toString())
    }

    private static Date parseDateTime(Object raw) {
        if (raw == null || raw == '') return null
        if (raw instanceof Date) return raw
        String s = raw.toString().trim()
        if (s.length() >= 19) {
            try { return Date.parse("yyyy-MM-dd'T'HH:mm:ss", s.substring(0, 19)) } catch (ignored) {}
        }
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
