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

package steakos.service

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/**
 * Customer service cases (APQC 6.2.2–6.2.3, AS1 kind, AS0 selected L4 due dates).
 */
@Slf4j
class AsInquiryService {

    AsServiceLevelService asServiceLevelService

    @Transactional
    Inquiry openInquiry(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim() ?: fields.subject?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        String kind = (fields.kind ?: 'REQUEST').toString().toUpperCase()
        if (!(kind in ['REQUEST', 'PROBLEM', 'COMPLAINT', 'OTHER'])) {
            throw new IllegalArgumentException("kind must be REQUEST|PROBLEM|COMPLAINT|OTHER")
        }

        ServiceLevel sl = null
        if (fields.serviceLevelId) {
            sl = asServiceLevelService.get(fields.serviceLevelId as Long, npub)
        }

        Date opened = parseDate(fields.openedAt) ?: new Date()
        Date responseDue = parseDate(fields.responseDueAt)
        Date resolveDue = parseDate(fields.resolveDueAt)
        if (!responseDue && sl?.responseHours) {
            responseDue = addHours(opened, sl.responseHours)
        }
        if (!resolveDue && sl?.resolveHours) {
            resolveDue = addHours(opened, sl.resolveHours)
        }

        String number = fields.inquiryNumber?.toString()?.trim() ?: nextNumber(npub, 'INQ')
        Inquiry inq = new Inquiry(
                npubOwner     : npub,
                name          : name,
                description   : fields.description as String,
                inquiryNumber : number,
                customerId    : longOrNull(fields.customerId),
                customerName  : fields.customerName as String,
                channel       : (fields.channel ?: 'EMAIL') as String,
                kind          : kind,
                priority      : (fields.priority ?: 'NORMAL') as String,
                serviceLevel  : sl,
                salesOrderId  : longOrNull(fields.salesOrderId),
                serviceOrderId: longOrNull(fields.serviceOrderId),
                openedAt      : opened,
                responseDueAt : responseDue,
                resolveDueAt  : resolveDue,
                assignedTo    : fields.assignedTo as String,
                caseStatus    : 'OPEN',
                status        : 'OPEN'
        )
        inq.save(failOnError: true, flush: true)
        log.info('Opened inquiry {} kind={}', number, kind)
        return inq
    }

    @Transactional
    Inquiry update(Long id, String npubOwner, Map fields) {
        Inquiry inq = load(id, npubOwner)
        if (inq.caseStatus in ['CLOSED', 'CANCELLED']) {
            throw new IllegalStateException("cannot update ${inq.caseStatus} inquiry")
        }
        if (fields.name) inq.name = fields.name as String
        if (fields.containsKey('description')) inq.description = fields.description as String
        if (fields.containsKey('customerName')) inq.customerName = fields.customerName as String
        if (fields.containsKey('customerId')) inq.customerId = longOrNull(fields.customerId)
        if (fields.channel) inq.channel = fields.channel as String
        if (fields.kind) inq.kind = fields.kind.toString().toUpperCase()
        if (fields.priority) inq.priority = fields.priority as String
        if (fields.containsKey('assignedTo')) inq.assignedTo = fields.assignedTo as String
        if (fields.containsKey('responseDueAt')) inq.responseDueAt = parseDate(fields.responseDueAt)
        if (fields.containsKey('resolveDueAt')) inq.resolveDueAt = parseDate(fields.resolveDueAt)
        if (fields.serviceLevelId) {
            inq.serviceLevel = asServiceLevelService.get(fields.serviceLevelId as Long, npubOwner)
        }
        if (fields.containsKey('salesOrderId')) inq.salesOrderId = longOrNull(fields.salesOrderId)
        inq.save(failOnError: true, flush: true)
        return inq
    }

    @Transactional
    Inquiry startWork(Long id, String npubOwner) {
        Inquiry inq = load(id, npubOwner)
        if (!(inq.caseStatus in ['OPEN', 'IN_PROGRESS'])) {
            throw new IllegalStateException("cannot start work from ${inq.caseStatus}")
        }
        inq.caseStatus = 'IN_PROGRESS'
        inq.status = 'IN_PROGRESS'
        inq.save(failOnError: true, flush: true)
        return inq
    }

    @Transactional
    Inquiry resolve(Long id, String npubOwner, Map fields = [:]) {
        Inquiry inq = load(id, npubOwner)
        if (inq.caseStatus in ['CLOSED', 'CANCELLED']) {
            throw new IllegalStateException("cannot resolve ${inq.caseStatus}")
        }
        inq.caseStatus = 'RESOLVED'
        inq.status = 'RESOLVED'
        inq.resolvedAt = new Date()
        if (fields.resolution) {
            String note = fields.resolution as String
            inq.description = inq.description ? "${inq.description}\n\n[Resolution] ${note}" : "[Resolution] ${note}"
        }
        inq.save(failOnError: true, flush: true)
        return inq
    }

    @Transactional
    Inquiry close(Long id, String npubOwner) {
        Inquiry inq = load(id, npubOwner)
        if (inq.caseStatus == 'CANCELLED') {
            throw new IllegalStateException('cannot close cancelled inquiry')
        }
        if (inq.caseStatus != 'RESOLVED' && inq.caseStatus != 'CLOSED') {
            // allow close from OPEN/IN_PROGRESS for SBM simplicity
            if (!(inq.caseStatus in ['OPEN', 'IN_PROGRESS', 'RESOLVED'])) {
                throw new IllegalStateException("cannot close from ${inq.caseStatus}")
            }
        }
        inq.caseStatus = 'CLOSED'
        inq.status = 'CLOSED'
        inq.closedAt = new Date()
        if (!inq.resolvedAt) inq.resolvedAt = inq.closedAt
        inq.save(failOnError: true, flush: true)
        return inq
    }

    @Transactional
    Inquiry cancel(Long id, String npubOwner) {
        Inquiry inq = load(id, npubOwner)
        if (inq.caseStatus == 'CLOSED') {
            throw new IllegalStateException('cannot cancel closed inquiry')
        }
        inq.caseStatus = 'CANCELLED'
        inq.status = 'CANCELLED'
        inq.closedAt = new Date()
        inq.save(failOnError: true, flush: true)
        return inq
    }

    @Transactional(readOnly = true)
    List<Inquiry> list(String npubOwner, String status = null, String kind = null) {
        listPage(npubOwner, status, kind, 200, 0).items as List<Inquiry>
    }

    static final List INQUIRY_SORT_FIELDS = [
            'id', 'name', 'inquiryNumber', 'caseStatus', 'status', 'kind',
            'customerName', 'priority', 'dateCreated', 'lastUpdated'
    ].asImmutable()

    /**
     * Paged inquiries with optional caseStatus / kind filters. Returns [items, total, sort, order].
     */
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String status = null, String kind = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', INQUIRY_SORT_FIELDS)
        int limit = Math.max(1, max)
        int off = Math.max(0, offset)
        List items = Inquiry.createCriteria().list(max: limit, offset: off) {
            eq('npubOwner', npub)
            if (status) eq('caseStatus', status)
            if (kind) eq('kind', kind)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Inquiry.createCriteria().count {
            eq('npubOwner', npub)
            if (status) eq('caseStatus', status)
            if (kind) eq('kind', kind)
        }
        [items: items, total: total as int, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<Inquiry> listOpen(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Inquiry.findAllByNpubOwnerAndCaseStatusInList(
                npub, ['OPEN', 'IN_PROGRESS'], [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    Inquiry get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(Inquiry inq) {
        if (!inq) return null
        boolean overdue = false
        Date now = new Date()
        if (inq.caseStatus in ['OPEN', 'IN_PROGRESS']) {
            if (inq.resolveDueAt && inq.resolveDueAt.before(now)) overdue = true
            else if (inq.responseDueAt && inq.responseDueAt.before(now) && inq.caseStatus == 'OPEN') overdue = true
        }
        [
                id            : inq.id,
                name          : inq.name,
                description   : inq.description,
                inquiryNumber : inq.inquiryNumber,
                customerId    : inq.customerId,
                customerName  : inq.customerName,
                channel       : inq.channel,
                kind          : inq.kind,
                priority      : inq.priority,
                serviceLevelId: inq.serviceLevel?.id,
                salesOrderId  : inq.salesOrderId,
                serviceOrderId: inq.serviceOrderId,
                openedAt      : inq.openedAt,
                responseDueAt : inq.responseDueAt,
                resolveDueAt  : inq.resolveDueAt,
                resolvedAt    : inq.resolvedAt,
                closedAt      : inq.closedAt,
                assignedTo    : inq.assignedTo,
                caseStatus    : inq.caseStatus,
                overdue       : overdue,
                npubOwner     : inq.npubOwner
        ]
    }

    private Inquiry load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Inquiry inq = Inquiry.get(id)
        if (!inq || inq.npubOwner != npub) throw new IllegalArgumentException("inquiry not found: ${id}")
        inq
    }

    private String nextNumber(String npub, String prefix) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String p = "${prefix}-${period}-"
        int seq = Inquiry.countByNpubOwner(npub) + 1
        String candidate = "${p}${String.format('%04d', seq)}"
        while (Inquiry.findByNpubOwnerAndInquiryNumber(npub, candidate)) {
            seq++
            candidate = "${p}${String.format('%04d', seq)}"
        }
        return candidate
    }

    private static Date addHours(Date base, Integer hours) {
        if (!base || hours == null) return null
        Calendar c = Calendar.getInstance()
        c.setTime(base)
        c.add(Calendar.HOUR_OF_DAY, hours)
        return c.time
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
        if (raw == null || raw == '') return null
        if (raw instanceof Date) return raw
        String s = raw.toString().trim()
        if (s.length() >= 10) {
            try { return Date.parse('yyyy-MM-dd', s.substring(0, 10)) } catch (ignored) {}
        }
        null
    }
}
