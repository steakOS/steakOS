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
import steakos.finance.FinanceRevenueService
import steakos.finance.Invoice

/**
 * Warranty claims (APQC 6.3.2). AS2 warranty required; AS3 B Finance credit; AS7 dual amounts.
 * Never posts JournalLine (FS6).
 */
@Slf4j
class AsClaimsService {

    AsWarrantyService asWarrantyService
    AsInquiryService asInquiryService
    FinanceRevenueService financeRevenueService

    @Transactional
    Claims receiveClaim(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Long warrantyId = longOrNull(fields.warrantyId)
        if (!warrantyId) throw new IllegalArgumentException('warrantyId is required (AS2)')
        Warranty w = asWarrantyService.get(warrantyId, npub)

        Inquiry inq = null
        if (fields.inquiryId) {
            inq = asInquiryService.get(fields.inquiryId as Long, npub)
        }

        BigDecimal amount = toBd(fields.claimAmountEur) ?: toBd(fields.amountEur) ?: BigDecimal.ZERO
        String number = fields.claimNumber?.toString()?.trim() ?: nextNumber(npub)
        String name = fields.name?.toString()?.trim() ?: "Claim ${number}"

        Claims c = new Claims(
                npubOwner              : npub,
                name                   : name,
                description            : fields.description as String,
                claimNumber            : number,
                warranty               : w,
                inquiry                : inq,
                claimAmountEur         : amount,
                claimAmountSats        : longOrNull(fields.claimAmountSats) ?: longOrNull(fields.amountSats),
                currencyCode           : (fields.currencyCode ?: 'EUR') as String,
                investigationNotes     : fields.investigationNotes as String,
                needsFieldVisit        : fields.needsFieldVisit as Boolean ?: Boolean.FALSE,
                receivedAt             : new Date(),
                claimStatus            : 'RECEIVED',
                status                 : 'RECEIVED'
        )
        c.save(failOnError: true, flush: true)
        log.info('Received claim {} for warranty {}', number, w.warrantyNumber)
        return c
    }

    @Transactional
    Claims validate(Long id, String npubOwner) {
        Claims c = load(id, npubOwner)
        if (!(c.claimStatus in ['RECEIVED', 'VALIDATED'])) {
            throw new IllegalStateException("cannot validate from ${c.claimStatus}")
        }
        if (!c.warranty) {
            throw new IllegalStateException('claim has no warranty (AS2)')
        }
        if (!asWarrantyService.isInForce(c.warranty)) {
            throw new IllegalStateException(
                    "warranty ${c.warranty.warrantyNumber} is not in force (status=${c.warranty.coverStatus})")
        }
        c.claimStatus = 'VALIDATED'
        c.status = 'VALIDATED'
        c.validatedAt = new Date()
        c.save(failOnError: true, flush: true)
        return c
    }

    /**
     * AS3 B: approve + optional Finance customer credit (no JE from AfterSales).
     */
    @Transactional
    Map approve(Long id, String npubOwner, Map fields = [:]) {
        Claims c = load(id, npubOwner)
        if (c.claimStatus == 'APPROVED') {
            return [claim: toMap(c), alreadyApproved: true, financeCreditInvoiceId: c.financeCreditInvoiceId]
        }
        if (c.claimStatus == 'REJECTED' || c.claimStatus == 'CLOSED') {
            throw new IllegalStateException("cannot approve ${c.claimStatus}")
        }
        if (c.claimStatus == 'RECEIVED') {
            // allow approve after implicit validate if cover in force
            if (!asWarrantyService.isInForce(c.warranty)) {
                throw new IllegalStateException('warranty not in force; validate/reject first')
            }
        } else if (c.claimStatus != 'VALIDATED') {
            throw new IllegalStateException("cannot approve from ${c.claimStatus}")
        }

        c.claimStatus = 'APPROVED'
        c.status = 'APPROVED'
        c.approvedAt = new Date()
        if (fields.resolution) c.resolution = fields.resolution as String
        if (fields.investigationNotes) c.investigationNotes = fields.investigationNotes as String

        Long creditId = null
        Map finance = null
        boolean handOff = fields.containsKey('createFinanceCredit')
                ? fields.createFinanceCredit as Boolean
                : true
        if (handOff && c.claimAmountEur != null && c.claimAmountEur > BigDecimal.ZERO && financeRevenueService) {
            try {
                Invoice credit = financeRevenueService.createCustomerCredit(npubOwner, [
                        amountEur    : c.claimAmountEur,
                        amountSats   : c.claimAmountSats,
                        customerId   : c.warranty?.customerId,
                        customerName : c.warranty?.customerName,
                        currencyCode : c.currencyCode,
                        referenceText: "AFTERSALES_CLAIM:${c.claimNumber}",
                        name         : "Credit ${c.claimNumber}",
                        description  : "Customer credit for warranty claim ${c.claimNumber}",
                        sourceType   : 'AFTERSALES_CLAIM',
                        sourceId     : c.id
                ])
                creditId = credit?.id
                c.financeCreditInvoiceId = creditId
                finance = financeRevenueService.invoiceToMap(credit)
                log.info('Claim {} approved → Finance credit invoice {}', c.claimNumber, creditId)
            } catch (Exception e) {
                log.warn('Finance credit hand-off failed for claim {}: {} — claim still APPROVED', c.claimNumber, e.message)
                finance = [error: e.message, stub: true]
            }
        } else {
            log.info('Claim {} approved (event-only / no credit amount)', c.claimNumber)
        }

        c.save(failOnError: true, flush: true)
        return [
                claim                 : toMap(c),
                alreadyApproved       : false,
                financeCreditInvoiceId: creditId,
                finance               : finance,
                event                 : 'aftersales.claim.approved'
        ]
    }

    @Transactional
    Claims reject(Long id, String npubOwner, Map fields = [:]) {
        Claims c = load(id, npubOwner)
        if (c.claimStatus in ['APPROVED', 'CLOSED']) {
            throw new IllegalStateException("cannot reject ${c.claimStatus}")
        }
        c.claimStatus = 'REJECTED'
        c.status = 'REJECTED'
        c.closedAt = new Date()
        if (fields.resolution) c.resolution = fields.resolution as String
        c.save(failOnError: true, flush: true)
        log.info('Claim {} rejected', c.claimNumber)
        return c
    }

    @Transactional
    Claims close(Long id, String npubOwner) {
        Claims c = load(id, npubOwner)
        if (!(c.claimStatus in ['APPROVED', 'REJECTED', 'CLOSED'])) {
            throw new IllegalStateException("close only after approve/reject (status=${c.claimStatus})")
        }
        c.claimStatus = 'CLOSED'
        c.status = 'CLOSED'
        c.closedAt = new Date()
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    Claims updateNotes(Long id, String npubOwner, Map fields) {
        Claims c = load(id, npubOwner)
        if (c.claimStatus == 'CLOSED') {
            throw new IllegalStateException('cannot update closed claim')
        }
        if (fields.containsKey('investigationNotes')) {
            c.investigationNotes = fields.investigationNotes as String
        }
        if (fields.containsKey('needsFieldVisit')) {
            c.needsFieldVisit = fields.needsFieldVisit as Boolean
        }
        if (fields.containsKey('description')) c.description = fields.description as String
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional(readOnly = true)
    List<Claims> list(String npubOwner, String status = null) {
        listPage(npubOwner, status, 200, 0).items as List<Claims>
    }

    /**
     * Paged claims with optional claimStatus filter. Returns [items, total].
     */
    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String status = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        if (status) {
            return [
                    items: Claims.findAllByNpubOwnerAndClaimStatus(npub, status, opts),
                    total: Claims.countByNpubOwnerAndClaimStatus(npub, status) as int
            ]
        }
        return [
                items: Claims.findAllByNpubOwner(npub, opts),
                total: Claims.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    List<Claims> listOpen(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Claims.findAllByNpubOwnerAndClaimStatusInList(
                npub, ['RECEIVED', 'VALIDATED', 'APPROVED'], [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    Claims get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(Claims c) {
        if (!c) return null
        [
                id                    : c.id,
                name                  : c.name,
                description           : c.description,
                claimNumber           : c.claimNumber,
                warrantyId            : c.warranty?.id,
                warrantyNumber        : c.warranty?.warrantyNumber,
                inquiryId             : c.inquiry?.id,
                claimAmountEur        : c.claimAmountEur,
                claimAmountSats       : c.claimAmountSats,
                currencyCode          : c.currencyCode,
                investigationNotes    : c.investigationNotes,
                needsFieldVisit       : c.needsFieldVisit,
                receivedAt            : c.receivedAt,
                validatedAt           : c.validatedAt,
                approvedAt            : c.approvedAt,
                closedAt              : c.closedAt,
                resolution            : c.resolution,
                financeCreditInvoiceId: c.financeCreditInvoiceId,
                claimStatus           : c.claimStatus,
                npubOwner             : c.npubOwner
        ]
    }

    private Claims load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Claims c = Claims.get(id)
        if (!c || c.npubOwner != npub) throw new IllegalArgumentException("claim not found: ${id}")
        c
    }

    private String nextNumber(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String p = "CLM-${period}-"
        int seq = Claims.countByNpubOwner(npub) + 1
        String candidate = "${p}${String.format('%04d', seq)}"
        while (Claims.findByNpubOwnerAndClaimNumber(npub, candidate)) {
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
