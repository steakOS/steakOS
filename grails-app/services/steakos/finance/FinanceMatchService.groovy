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
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames
import steakos.procurement.PurchaseOrder
import steakos.procurement.PurchaseOrderLine
import steakos.procurement.Receipt
import steakos.procurement.ReceiptLine

/**
 * FinanceAdvanced light three-way match (OI-F21 / PR-5b).
 * Matches PurchaseOrder ↔ Receipt ↔ Payable (supplier invoice) at header/amount level.
 *
 * Honest gaps (domain thin):
 * <ul>
 *   <li>No separate SupplierInvoice — Payable is the AP/supplier-invoice concept.</li>
 *   <li>Payable has no lines → no line-level price×qty match; amountOk is header variance.</li>
 *   <li>Does not inject ScmPurchaseService; reads procurement GORM rows in-monorepo.</li>
 *   <li>scm.receipt.posted is registered but not yet published from receiveGoods.</li>
 *   <li>Confirm does not hard-block AP pay; publishes finance.match.completed for consumers.</li>
 *   <li>One receipt per match row; multi-receipt allocation is out of this light slice.</li>
 *   <li>No Gold multi-entity.</li>
 * </ul>
 */
@Slf4j
class FinanceMatchService {

    static final List MATCH_STATUSES = ['PROPOSED', 'CONFIRMED', 'REJECTED', 'CANCELLED'].asImmutable()
    static final List CANDIDATE_PO_STATUSES = ['CONFIRMED', 'PART_RECEIVED', 'RECEIVED'].asImmutable()
    /** Absolute amount variance ≤ this → amountOk (EUR functional). */
    static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal('0.01')

    DomainEventBusService domainEventBusService

    // -------------------------------------------------------------------------
    // Candidates / list / show
    // -------------------------------------------------------------------------

    /**
     * POs that look matchable: confirmed/received path, linked payable, ≥1 receipt,
     * and no CONFIRMED match yet for that PO+payable.
     */
    @Transactional(readOnly = true)
    Map listCandidates(String npubOwner) {
        String npub = requireNpub(npubOwner)
        List<PurchaseOrder> pos = PurchaseOrder.findAllByNpubOwnerAndPoStatusInList(
                npub, CANDIDATE_PO_STATUSES, [sort: 'id', order: 'asc'])
        List<Map> items = []
        pos.each { PurchaseOrder po ->
            if (!po.financePayableId) {
                return
            }
            Payable pay = Payable.get(po.financePayableId)
            if (!pay || pay.npubOwner != npub || pay.payableStatus == 'VOID') {
                return
            }
            if (hasConfirmedMatch(npub, po.id, pay.id)) {
                return
            }
            List<Receipt> receipts = Receipt.findAllByNpubOwnerAndPurchaseOrder(npub, po, [sort: 'id'])
            if (!receipts) {
                return
            }
            items << [
                    purchaseOrderId  : po.id,
                    poNumber         : po.poNumber,
                    poStatus         : po.poStatus,
                    vendorName       : po.vendorName,
                    amountPoEur      : po.amountEur,
                    payableId        : pay.id,
                    payableStatus    : pay.payableStatus,
                    amountPayableEur : pay.amountEur,
                    varianceEur      : variance(pay.amountEur, po.amountEur),
                    receipts         : receipts.collect { Receipt r ->
                        [
                                receiptId     : r.id,
                                receiptNumber : r.receiptNumber,
                                receivedDate  : r.receivedDate,
                                qtyOk         : evaluateQty(po, r)
                        ]
                    },
                    gaps             : gapsNote()
            ]
        }
        [
                npubOwner: npub,
                total    : items.size(),
                items    : items,
                gaps     : gapsNote()
        ]
    }

    @Transactional(readOnly = true)
    Map listMatches(String npubOwner, String matchStatus = null) {
        String npub = requireNpub(npubOwner)
        List<ThreeWayMatch> rows
        String st = matchStatus?.trim()?.toUpperCase()
        if (st) {
            if (!(st in MATCH_STATUSES)) {
                throw new IllegalArgumentException("matchStatus must be one of ${MATCH_STATUSES}")
            }
            rows = ThreeWayMatch.findAllByNpubOwnerAndMatchStatus(npub, st, [sort: 'proposedAt', order: 'desc'])
        } else {
            rows = ThreeWayMatch.findAllByNpubOwner(npub, [sort: 'proposedAt', order: 'desc'])
        }
        [
                npubOwner: npub,
                total    : rows.size(),
                items    : rows.collect { toMap(it) },
                gaps     : gapsNote()
        ]
    }

    @Transactional(readOnly = true)
    Map show(Long matchId, String npubOwner) {
        toMap(load(matchId, npubOwner))
    }

    // -------------------------------------------------------------------------
    // Propose / confirm / reject
    // -------------------------------------------------------------------------

    /**
     * Propose a match. Body: purchaseOrderId (required), receiptId?, payableId?.
     * Defaults payable from PO.financePayableId; receipt if exactly one exists for the PO.
     */
    @Transactional
    ThreeWayMatch propose(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        if (fields?.purchaseOrderId == null || fields.purchaseOrderId == '') {
            throw new IllegalArgumentException('purchaseOrderId is required')
        }
        Long poId = fields.purchaseOrderId as Long
        PurchaseOrder po = PurchaseOrder.get(poId)
        if (!po || po.npubOwner != npub) {
            throw new IllegalArgumentException("purchaseOrder not found: ${poId}")
        }
        if (!(po.poStatus in CANDIDATE_PO_STATUSES) && po.poStatus != 'SENT') {
            throw new IllegalStateException("PO status ${po.poStatus} is not matchable")
        }

        Long payableId = fields.payableId != null && fields.payableId != '' ?
                fields.payableId as Long : po.financePayableId
        if (!payableId) {
            throw new IllegalArgumentException('payableId required (PO has no financePayableId)')
        }
        Payable pay = Payable.get(payableId)
        if (!pay || pay.npubOwner != npub) {
            throw new IllegalArgumentException("payable not found: ${payableId}")
        }
        if (pay.payableStatus == 'VOID') {
            throw new IllegalStateException('cannot match VOID payable')
        }

        Long receiptId = fields.receiptId != null && fields.receiptId != '' ?
                fields.receiptId as Long : null
        Receipt receipt
        if (receiptId) {
            receipt = Receipt.get(receiptId)
            if (!receipt || receipt.npubOwner != npub) {
                throw new IllegalArgumentException("receipt not found: ${receiptId}")
            }
            if (receipt.purchaseOrder?.id != po.id) {
                throw new IllegalArgumentException('receipt does not belong to purchaseOrder')
            }
        } else {
            List<Receipt> receipts = Receipt.findAllByNpubOwnerAndPurchaseOrder(npub, po, [sort: 'id'])
            if (!receipts) {
                throw new IllegalStateException('no goods receipt for PO — receive before match')
            }
            if (receipts.size() > 1) {
                throw new IllegalArgumentException('multiple receipts — pass receiptId')
            }
            receipt = receipts[0]
        }

        if (hasConfirmedMatch(npub, po.id, pay.id)) {
            throw new IllegalStateException('CONFIRMED match already exists for this PO and payable')
        }
        ThreeWayMatch openDup = ThreeWayMatch.findByNpubOwnerAndPurchaseOrderIdAndReceiptIdAndPayableIdAndMatchStatus(
                npub, po.id, receipt.id, pay.id, 'PROPOSED')
        if (openDup) {
            return openDup
        }

        BigDecimal poAmt = po.amountEur ?: BigDecimal.ZERO
        BigDecimal payAmt = pay.amountEur ?: BigDecimal.ZERO
        BigDecimal var = variance(payAmt, poAmt)
        boolean amtOk = amountWithinTolerance(var)
        boolean qOk = evaluateQty(po, receipt)

        ThreeWayMatch m = new ThreeWayMatch(
                npubOwner        : npub,
                name             : "Match ${po.poNumber ?: po.id} / AP-${pay.id}",
                description      : fields.description?.toString() ?: fields.note?.toString(),
                purchaseOrderId  : po.id,
                receiptId        : receipt.id,
                payableId        : pay.id,
                matchStatus      : 'PROPOSED',
                status           : 'PROPOSED',
                amountPoEur      : poAmt,
                amountPayableEur : payAmt,
                varianceEur      : var,
                amountOk         : amtOk,
                qtyOk            : qOk,
                proposedAt       : new Date()
        )
        m.save(failOnError: true, flush: true)
        log.info('three-way match proposed id={} po={} receipt={} payable={} amountOk={} qtyOk={}',
                m.id, po.id, receipt.id, pay.id, amtOk, qOk)
        return m
    }

    @Transactional
    ThreeWayMatch confirm(String npubOwner, Long matchId, Map fields = [:]) {
        String npub = requireNpub(npubOwner)
        ThreeWayMatch m = load(matchId, npub)
        if (m.matchStatus == 'CONFIRMED') {
            return m
        }
        if (m.matchStatus != 'PROPOSED') {
            throw new IllegalStateException("cannot confirm match in status ${m.matchStatus}")
        }
        if (hasConfirmedMatch(npub, m.purchaseOrderId, m.payableId, m.id)) {
            throw new IllegalStateException('another CONFIRMED match exists for this PO and payable')
        }

        // Re-evaluate flags at confirm time
        PurchaseOrder po = PurchaseOrder.get(m.purchaseOrderId)
        Receipt receipt = Receipt.get(m.receiptId)
        Payable pay = Payable.get(m.payableId)
        if (!po || !receipt || !pay) {
            throw new IllegalStateException('linked PO/receipt/payable missing')
        }
        m.amountPoEur = po.amountEur ?: BigDecimal.ZERO
        m.amountPayableEur = pay.amountEur ?: BigDecimal.ZERO
        m.varianceEur = variance(m.amountPayableEur, m.amountPoEur)
        m.amountOk = amountWithinTolerance(m.varianceEur)
        m.qtyOk = evaluateQty(po, receipt)

        boolean force = fields?.force == true || fields?.force?.toString()?.equalsIgnoreCase('true')
        if ((!m.amountOk || !m.qtyOk) && !force) {
            throw new IllegalStateException(
                    "match not clean (amountOk=${m.amountOk}, qtyOk=${m.qtyOk}); pass force=true to override")
        }

        m.matchStatus = 'CONFIRMED'
        m.status = 'CONFIRMED'
        m.confirmedAt = new Date()
        m.confirmedByNpub = fields?.confirmedByNpub?.toString()
        if (fields?.description) {
            m.description = fields.description.toString()
        }
        m.save(failOnError: true, flush: true)
        log.info('three-way match confirmed id={}', m.id)

        if (domainEventBusService != null) {
            try {
                domainEventBusService.publish(
                        DomainEventNames.FINANCE_MATCH_COMPLETED,
                        'finance.match',
                        npub,
                        [
                                matchId         : m.id,
                                purchaseOrderId : m.purchaseOrderId,
                                receiptId       : m.receiptId,
                                payableId       : m.payableId,
                                amountOk        : m.amountOk,
                                qtyOk           : m.qtyOk,
                                varianceEur     : m.varianceEur,
                                forced          : force
                        ]
                )
            } catch (Exception e) {
                log.warn('finance.match.completed publish failed: {}', e.message)
            }
        }
        return m
    }

    @Transactional
    ThreeWayMatch reject(String npubOwner, Long matchId, Map fields = [:]) {
        ThreeWayMatch m = load(matchId, npubOwner)
        if (m.matchStatus == 'CONFIRMED') {
            throw new IllegalStateException('cannot reject CONFIRMED match')
        }
        if (m.matchStatus == 'REJECTED') {
            return m
        }
        m.matchStatus = 'REJECTED'
        m.status = 'REJECTED'
        if (fields?.description) {
            m.description = fields.description.toString()
        }
        m.save(failOnError: true, flush: true)
        return m
    }

    @Transactional
    ThreeWayMatch cancel(String npubOwner, Long matchId) {
        ThreeWayMatch m = load(matchId, npubOwner)
        if (m.matchStatus == 'CONFIRMED') {
            throw new IllegalStateException('cannot cancel CONFIRMED match — use unmatch()')
        }
        m.matchStatus = 'CANCELLED'
        m.status = 'CANCELLED'
        m.save(failOnError: true, flush: true)
        return m
    }

    /**
     * ADR-049 P4 — compensating rollback for a CONFIRMED three-way match.
     * Clears the lock on PO / receipt / payable without deleting history.
     */
    @Transactional
    ThreeWayMatch unmatch(String npubOwner, Long matchId, Map fields = [:]) {
        ThreeWayMatch m = load(matchId, npubOwner)
        if (m.matchStatus == 'CANCELLED') {
            return m
        }
        if (m.matchStatus != 'CONFIRMED') {
            throw new IllegalStateException("unmatch only for CONFIRMED matches (status=${m.matchStatus})")
        }
        m.matchStatus = 'CANCELLED'
        m.status = 'CANCELLED'
        if (fields?.description) {
            m.description = ((m.description ? m.description + '\n' : '') +
                    "Unmatched: ${fields.description}").toString()
        } else if (!m.description) {
            m.description = 'Unmatched (ADR-049 compensating rollback)'
        }
        m.save(failOnError: true, flush: true)
        log.info('three-way match unmatched id={}', m.id)
        return m
    }

    // -------------------------------------------------------------------------
    // Maps / helpers
    // -------------------------------------------------------------------------

    Map toMap(ThreeWayMatch m) {
        if (!m) {
            return null
        }
        [
                id               : m.id,
                name             : m.name,
                description      : m.description,
                purchaseOrderId  : m.purchaseOrderId,
                receiptId        : m.receiptId,
                payableId        : m.payableId,
                matchStatus      : m.matchStatus,
                amountPoEur      : m.amountPoEur,
                amountPayableEur : m.amountPayableEur,
                varianceEur      : m.varianceEur,
                amountOk         : m.amountOk,
                qtyOk            : m.qtyOk,
                proposedAt       : m.proposedAt,
                confirmedAt      : m.confirmedAt,
                confirmedByNpub  : m.confirmedByNpub,
                npubOwner        : m.npubOwner,
                dateCreated      : m.dateCreated,
                gaps             : gapsNote()
        ]
    }

    static List<String> gapsNote() {
        [
                'Payable is the supplier-invoice stand-in (no separate SupplierInvoice domain)',
                'Header/amount match only — Payable has no lines',
                'Confirm does not block AP pay; finance.match.completed is advisory',
                'scm.receipt.posted not yet emitted from receiveGoods',
                'One receipt per match; multi-receipt allocation deferred'
        ].asImmutable()
    }

    static BigDecimal variance(BigDecimal payableAmt, BigDecimal poAmt) {
        (payableAmt ?: BigDecimal.ZERO).subtract(poAmt ?: BigDecimal.ZERO)
    }

    static boolean amountWithinTolerance(BigDecimal varianceEur) {
        if (varianceEur == null) {
            return false
        }
        varianceEur.abs().compareTo(AMOUNT_TOLERANCE) <= 0
    }

    /**
     * Qty OK when every PO line linked on the receipt has received qty ≥ ordered,
     * or (light) when receipt has lines and total received &gt; 0 with no over-receive on linked lines.
     * Unlinked receipt lines count toward "something received".
     */
    boolean evaluateQty(PurchaseOrder po, Receipt receipt) {
        if (!po || !receipt) {
            return false
        }
        List<ReceiptLine> rLines = ReceiptLine.findAllByReceipt(receipt, [sort: 'lineNo'])
        if (!rLines) {
            return false
        }
        BigDecimal totalRec = BigDecimal.ZERO
        boolean anyLinked = false
        boolean allLinkedCovered = true
        rLines.each { ReceiptLine rl ->
            BigDecimal q = rl.quantityReceived ?: BigDecimal.ZERO
            totalRec = totalRec.add(q)
            PurchaseOrderLine pol = rl.purchaseOrderLine
            if (pol) {
                anyLinked = true
                BigDecimal ordered = pol.quantity ?: BigDecimal.ZERO
                BigDecimal receivedOnPo = pol.quantityReceived ?: BigDecimal.ZERO
                // Prefer cumulative PO received; fall back to this receipt line qty
                BigDecimal effective = receivedOnPo > BigDecimal.ZERO ? receivedOnPo : q
                if (effective.compareTo(ordered) < 0) {
                    allLinkedCovered = false
                }
            }
        }
        if (totalRec.compareTo(BigDecimal.ZERO) <= 0) {
            return false
        }
        if (anyLinked) {
            return allLinkedCovered
        }
        // No line links — treat positive receipt as soft OK for light match
        return true
    }

    private boolean hasConfirmedMatch(String npub, Long poId, Long payableId, Long excludeId = null) {
        List<ThreeWayMatch> confirmed = ThreeWayMatch.findAllByNpubOwnerAndPurchaseOrderIdAndPayableIdAndMatchStatus(
                npub, poId, payableId, 'CONFIRMED')
        if (excludeId != null) {
            confirmed = confirmed.findAll { it.id != excludeId }
        }
        return !confirmed.isEmpty()
    }

    private ThreeWayMatch load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        ThreeWayMatch m = ThreeWayMatch.get(id)
        if (!m || m.npubOwner != npub) {
            throw new IllegalArgumentException("match not found: ${id}")
        }
        return m
    }

    private static String requireNpub(String npubOwner) {
        String n = npubOwner?.trim()
        if (!n) {
            throw new IllegalArgumentException('npubOwner is required')
        }
        return n
    }
}
