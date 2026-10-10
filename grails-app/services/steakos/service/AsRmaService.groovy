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
import steakos.supplychain.ScmInventoryService
import steakos.supplychain.StockMove

/**
 * Thin RMA / return case (APQC 6.2.4, AS4 B). Confirm → SCM stock RECEIPT; no inventory ledger here.
 */
@Slf4j
class AsRmaService {

    AsInquiryService asInquiryService
    ScmInventoryService scmInventoryService

    @Transactional
    Rma openRma(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) {
            name = fields.productName ? "RMA ${fields.productName}" : 'Return'
        }
        String number = fields.rmaNumber?.toString()?.trim() ?: nextNumber(npub)
        Inquiry inq = null
        if (fields.inquiryId) {
            inq = asInquiryService.get(fields.inquiryId as Long, npub)
        }
        BigDecimal qty = toBd(fields.quantity) ?: BigDecimal.ONE
        if (qty <= BigDecimal.ZERO) throw new IllegalArgumentException('quantity must be > 0')

        Rma r = new Rma(
                npubOwner         : npub,
                name              : name,
                description       : fields.description as String,
                rmaNumber         : number,
                customerId        : longOrNull(fields.customerId),
                customerName      : fields.customerName as String,
                salesOrderId      : longOrNull(fields.salesOrderId),
                inquiry           : inq,
                catalogueItemId   : longOrNull(fields.catalogueItemId ?: fields.productId),
                productName       : fields.productName as String,
                quantity          : qty,
                reason            : fields.reason as String,
                expectedRefundEur : toBd(fields.expectedRefundEur),
                expectedRefundSats: longOrNull(fields.expectedRefundSats),
                currencyCode      : (fields.currencyCode ?: 'EUR') as String,
                requestedAt       : new Date(),
                rmaStatus         : 'REQUESTED',
                status            : 'REQUESTED'
        )
        r.save(failOnError: true, flush: true)
        log.info('Opened RMA {}', number)
        return r
    }

    /**
     * Confirm return: if productId set, post SCM StockMove RECEIPT (+qty). AfterSales never owns stock.
     */
    @Transactional
    Map confirm(Long id, String npubOwner, Map fields = [:]) {
        Rma r = load(id, npubOwner)
        if (r.rmaStatus == 'CONFIRMED' || r.rmaStatus == 'CLOSED') {
            return [rma: toMap(r), alreadyConfirmed: true, stockMoveId: r.stockMoveId]
        }
        if (r.rmaStatus == 'CANCELLED') {
            throw new IllegalStateException('cannot confirm cancelled RMA')
        }

        Long catId = fields.catalogueItemId != null && fields.catalogueItemId != '' ?
                fields.catalogueItemId as Long :
                (fields.productId != null && fields.productId != '' ? fields.productId as Long : r.catalogueItemId)
        BigDecimal qty = toBd(fields.quantity) ?: r.quantity ?: BigDecimal.ONE
        Long moveId = null
        Map stock = null

        if (catId && scmInventoryService) {
            StockMove m = scmInventoryService.postMove(npubOwner, [
                    catalogueItemId: catId,
                    qtyDelta       : qty,
                    reason         : 'RECEIPT',
                    name           : "RMA ${r.rmaNumber}",
                    description    : r.reason ?: "Return ${r.rmaNumber}",
                    sourceType     : 'AFTERSALES_RMA',
                    sourceId       : r.id
            ])
            moveId = m.id
            r.stockMoveId = moveId
            r.catalogueItemId = catId
            stock = scmInventoryService.toMap(m)
            log.info('RMA {} confirmed → StockMove {} +{}', r.rmaNumber, moveId, qty)
        } else {
            log.info('RMA {} confirmed without stock (no catalogueItemId)', r.rmaNumber)
        }

        r.rmaStatus = 'CONFIRMED'
        r.status = 'CONFIRMED'
        r.confirmedAt = new Date()
        r.save(failOnError: true, flush: true)

        return [
                rma             : toMap(r),
                alreadyConfirmed: false,
                stockMoveId     : moveId,
                stockMove       : stock,
                event           : 'aftersales.rma.confirmed'
        ]
    }

    @Transactional
    Rma close(Long id, String npubOwner) {
        Rma r = load(id, npubOwner)
        if (r.rmaStatus == 'CANCELLED') {
            throw new IllegalStateException('cannot close cancelled RMA')
        }
        r.rmaStatus = 'CLOSED'
        r.status = 'CLOSED'
        r.closedAt = new Date()
        r.save(failOnError: true, flush: true)
        return r
    }

    @Transactional
    Rma cancel(Long id, String npubOwner) {
        Rma r = load(id, npubOwner)
        if (r.rmaStatus == 'CONFIRMED' || r.rmaStatus == 'CLOSED') {
            throw new IllegalStateException("cannot cancel ${r.rmaStatus} RMA (stock may already be posted)")
        }
        r.rmaStatus = 'CANCELLED'
        r.status = 'CANCELLED'
        r.closedAt = new Date()
        r.save(failOnError: true, flush: true)
        return r
    }

    @Transactional(readOnly = true)
    List<Rma> list(String npubOwner, String status = null) {
        listPage(npubOwner, status, 200, 0).items as List<Rma>
    }

    /**
     * Paged RMAs with optional rmaStatus filter. Returns [items, total].
     */
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String status = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        if (status) {
            return [
                    items: Rma.findAllByNpubOwnerAndRmaStatus(npub, status, opts),
                    total: Rma.countByNpubOwnerAndRmaStatus(npub, status) as int
            ]
        }
        return [
                items: Rma.findAllByNpubOwner(npub, opts),
                total: Rma.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    List<Rma> listOpen(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Rma.findAllByNpubOwnerAndRmaStatusInList(
                npub, ['REQUESTED', 'CONFIRMED'], [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    Rma get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(Rma r) {
        if (!r) return null
        [
                id                : r.id,
                name              : r.name,
                description       : r.description,
                rmaNumber         : r.rmaNumber,
                customerId        : r.customerId,
                customerName      : r.customerName,
                salesOrderId      : r.salesOrderId,
                inquiryId         : r.inquiry?.id,
                productId         : r.catalogueItemId,
                catalogueItemId   : r.catalogueItemId,
                productName       : r.productName,
                quantity          : r.quantity,
                reason            : r.reason,
                expectedRefundEur : r.expectedRefundEur,
                expectedRefundSats: r.expectedRefundSats,
                currencyCode      : r.currencyCode,
                requestedAt       : r.requestedAt,
                confirmedAt       : r.confirmedAt,
                closedAt          : r.closedAt,
                stockMoveId       : r.stockMoveId,
                rmaStatus         : r.rmaStatus,
                npubOwner         : r.npubOwner
        ]
    }

    private Rma load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Rma r = Rma.get(id)
        if (!r || r.npubOwner != npub) throw new IllegalArgumentException("rma not found: ${id}")
        r
    }

    private String nextNumber(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String p = "RMA-${period}-"
        int seq = Rma.countByNpubOwner(npub) + 1
        String candidate = "${p}${String.format('%04d', seq)}"
        while (Rma.findByNpubOwnerAndRmaNumber(npub, candidate)) {
            seq++
            candidate = "${p}${String.format('%04d', seq)}"
        }
        return candidate
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

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
