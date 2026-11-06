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
import steakos.helper.AssignableSupport

/**
 * Warranty cover registration (APQC 6.3.1).
 */
@Slf4j
class AsWarrantyService {

    @Transactional
    Warranty register(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) {
            name = fields.productName ? "Warranty ${fields.productName}" : 'Warranty'
        }
        String number = fields.warrantyNumber?.toString()?.trim() ?: nextNumber(npub)
        Date from = parseDate(fields.validFrom) ?: new Date()
        Date to = parseDate(fields.validTo)
        String cover = (fields.coverStatus ?: fields.status ?: 'ACTIVE') as String
        if (!(cover in ['DRAFT', 'ACTIVE', 'EXPIRED', 'VOID'])) {
            cover = 'ACTIVE'
        }

        Warranty w = new Warranty(
                npubOwner      : npub,
                name           : name,
                description    : fields.description as String,
                warrantyNumber : number,
                customerId     : longOrNull(fields.customerId),
                customerName   : fields.customerName as String,
                catalogueItemId: longOrNull(fields.catalogueItemId ?: fields.productId),
                productName    : fields.productName as String,
                salesOrderId   : longOrNull(fields.salesOrderId),
                validFrom      : from,
                validTo        : to,
                coverStatus    : cover,
                status         : cover,
                *: AssignableSupport.initialPair(fields)
        )
        w.save(failOnError: true, flush: true)
        log.info('Registered warranty {}', number)
        return w
    }

    @Transactional
    Warranty update(Long id, String npubOwner, Map fields) {
        Warranty w = load(id, npubOwner)
        if (w.coverStatus == 'VOID') {
            throw new IllegalStateException('cannot update void warranty')
        }
        if (fields.name) w.name = fields.name as String
        if (fields.containsKey('description')) w.description = fields.description as String
        if (fields.containsKey('customerName')) w.customerName = fields.customerName as String
        if (fields.containsKey('customerId')) w.customerId = longOrNull(fields.customerId)
        if (fields.containsKey('catalogueItemId') || fields.containsKey('productId')) {
            w.catalogueItemId = longOrNull(fields.catalogueItemId ?: fields.productId)
        }
        if (fields.containsKey('productName')) w.productName = fields.productName as String
        if (fields.containsKey('salesOrderId')) w.salesOrderId = longOrNull(fields.salesOrderId)
        if (fields.containsKey('validFrom')) w.validFrom = parseDate(fields.validFrom)
        if (fields.containsKey('validTo')) w.validTo = parseDate(fields.validTo)
        AssignableSupport.apply(w, fields)
        w.save(failOnError: true, flush: true)
        return w
    }

    @Transactional
    Warranty activate(Long id, String npubOwner) {
        Warranty w = load(id, npubOwner)
        if (w.coverStatus == 'VOID') throw new IllegalStateException('cannot activate void warranty')
        w.coverStatus = 'ACTIVE'
        w.status = 'ACTIVE'
        if (!w.validFrom) w.validFrom = new Date()
        w.save(failOnError: true, flush: true)
        return w
    }

    @Transactional
    Warranty voidCover(Long id, String npubOwner) {
        Warranty w = load(id, npubOwner)
        w.coverStatus = 'VOID'
        w.status = 'VOID'
        w.save(failOnError: true, flush: true)
        return w
    }

    @Transactional(readOnly = true)
    List<Warranty> list(String npubOwner, String coverStatus = null) {
        listPage(npubOwner, coverStatus, 200, 0).items as List<Warranty>
    }

    static final List WARRANTY_SORT_FIELDS = [
            'id', 'name', 'warrantyNumber', 'coverStatus', 'status',
            'customerName', 'productName', 'dateCreated', 'lastUpdated',
            'assigneeHumanNpub', 'assigneeAgentId'
    ].asImmutable()

    /**
     * Paged warranties with optional coverStatus filter. Returns [items, total, sort, order].
     */
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String coverStatus = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', WARRANTY_SORT_FIELDS)
        List items = Warranty.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (coverStatus) eq('coverStatus', coverStatus)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Warranty.createCriteria().count {
            eq('npubOwner', npub)
            if (coverStatus) eq('coverStatus', coverStatus)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<Warranty> listActiveForCustomer(String npubOwner, Long customerId) {
        String npub = requireNpub(npubOwner)
        List<Warranty> all = Warranty.findAllByNpubOwnerAndCoverStatus(npub, 'ACTIVE', [sort: 'id', order: 'desc'])
        if (customerId != null) {
            all = all.findAll { it.customerId == customerId }
        }
        return all
    }

    @Transactional(readOnly = true)
    Warranty get(Long id, String npubOwner) { load(id, npubOwner) }

    /**
     * AS2: cover must be ACTIVE and within validity for new claims.
     */
    boolean isInForce(Warranty w, Date asOf = new Date()) {
        if (!w || w.coverStatus != 'ACTIVE') return false
        if (w.validFrom && w.validFrom.after(asOf)) return false
        if (w.validTo && w.validTo.before(asOf)) return false
        return true
    }

    Map toMap(Warranty w) {
        if (!w) return null
        [
                id            : w.id,
                name          : w.name,
                description   : w.description,
                warrantyNumber: w.warrantyNumber,
                customerId    : w.customerId,
                customerName  : w.customerName,
                productId       : w.catalogueItemId,
                catalogueItemId : w.catalogueItemId,
                productName     : w.productName,
                salesOrderId  : w.salesOrderId,
                validFrom     : w.validFrom,
                validTo       : w.validTo,
                coverStatus   : w.coverStatus,
                inForce       : isInForce(w),
                npubOwner     : w.npubOwner,
                *: AssignableSupport.toMapFragment(w)
        ]
    }

    private Warranty load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Warranty w = Warranty.get(id)
        if (!w || w.npubOwner != npub) throw new IllegalArgumentException("warranty not found: ${id}")
        w
    }

    private String nextNumber(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String p = "WRN-${period}-"
        int seq = Warranty.countByNpubOwner(npub) + 1
        String candidate = "${p}${String.format('%04d', seq)}"
        while (Warranty.findByNpubOwnerAndWarrantyNumber(npub, candidate)) {
            seq++
            candidate = "${p}${String.format('%04d', seq)}"
        }
        return candidate
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
