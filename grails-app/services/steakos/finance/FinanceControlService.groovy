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

package steakos.finance

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/**
 * Soft period controls (APQC 9.8 subset, F-S6). Delegates close to ledger.
 */
@Slf4j
class FinanceControlService {

    FinanceLedgerService financeLedgerService

    @Transactional(readOnly = true)
    void assertPeriodOpen(String npubOwner, String period) {
        if (financeLedgerService.isPeriodClosed(npubOwner, period)) {
            throw new IllegalStateException("period ${period} is closed")
        }
    }

    @Transactional
    Map lockPeriod(String npubOwner, String period) {
        financeLedgerService.closePeriod(npubOwner, period)
    }

    @Transactional(readOnly = true)
    Map periodStatus(String npubOwner, String period) {
        [
                period : period,
                closed : financeLedgerService.isPeriodClosed(npubOwner, period),
                npubOwner: npubOwner
        ]
    }

    /**
     * Lightweight audit trail: recent posted journal headers (legacy cap).
     */
    @Transactional(readOnly = true)
    List listAuditTrail(String npubOwner, int max = 50) {
        listAuditTrailPage(npubOwner, max, 0).items as List
    }

    static final List AUDIT_SORT_FIELDS = [
            'id', 'name', 'period', 'sourceType', 'sourceId', 'eventId',
            'postedAt', 'entryDate', 'status', 'dateCreated', 'lastUpdated'
    ].asImmutable()

    /** Paged audit trail of posted journal headers (LIST-PAGINATION). */
    @Transactional(readOnly = true)
    Map listAuditTrailPage(String npubOwner, int max = 25, int offset = 0, Map listOpts = [:]) {
        String npub = npubOwner?.trim()
        if (!npub) throw new IllegalArgumentException('npubOwner is required')
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', AUDIT_SORT_FIELDS)
        List entries = JournalEntry.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            eq('posted', Boolean.TRUE)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        List items = entries.collect { JournalEntry e ->
            [
                    id        : e.id,
                    name      : e.name,
                    period    : e.period,
                    sourceType: e.sourceType,
                    sourceId  : e.sourceId,
                    eventId   : e.eventId,
                    postedAt  : e.postedAt,
                    status    : e.status
            ]
        }
        Number total = JournalEntry.createCriteria().count {
            eq('npubOwner', npub)
            eq('posted', Boolean.TRUE)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }
}
