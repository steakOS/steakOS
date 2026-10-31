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
import steakos.configuration.TenantMoneyService
import steakos.procurement.PurchaseOrder

/**
 * Simple AP (APQC 9.6 subset, F-S4).
 */
@Slf4j
class FinancePayableService {

    FinanceLedgerService financeLedgerService
    FinanceTreasuryService financeTreasuryService
    PostingEngineService postingEngineService
    TenantMoneyService tenantMoneyService

    @Transactional
    Payable createPayable(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        financeLedgerService.ensureChartSeeded(npub)
        Map primary = tenantMoneyService != null
                ? tenantMoneyService.resolveDocumentPrimaryAmount(npub, fields)
                : [amountFunctional: toBd(fields.amountEur), currencyCode: (fields.currencyCode ?: 'EUR')]
        BigDecimal amt = primary.amountFunctional as BigDecimal
        if (amt == null || amt <= BigDecimal.ZERO) {
            throw new IllegalArgumentException('amount must be > 0')
        }
        Payable p = new Payable(
                npubOwner     : npub,
                name          : (fields.name ?: fields.vendorName ?: 'Payable') as String,
                description   : fields.description as String,
                vendorId      : fields.vendorId != null && fields.vendorId != '' ? fields.vendorId as Long : null,
                vendorName    : fields.vendorName as String,
                amountEur     : amt,
                amountSats    : null,
                currencyCode  : (primary.currencyCode ?: 'EUR') as String,
                issueDate     : parseDate(fields.issueDate) ?: new Date(),
                dueDate       : parseDate(fields.dueDate),
                payableStatus : 'OPEN',
                status        : 'OPEN'
        )
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional
    Payable approvePayable(Long id, String npubOwner) {
        Payable p = load(id, npubOwner)
        if (p.payableStatus == 'VOID') throw new IllegalStateException('void')
        if (p.payableStatus == 'DRAFT') {
            p.payableStatus = 'OPEN'
            p.status = 'OPEN'
            p.save(failOnError: true, flush: true)
        }
        return p
    }

    /**
     * Pay vendor via {@link PostingEngineService} ({@code payable.pay} rules);
     * SBM default = Dr Expense 5000 / Cr Cash 1000. Debits treasury fiat.
     */
    @Transactional
    Map recordPayment(Long id, String npubOwner, Map fields = [:]) {
        Payable p = load(id, npubOwner)
        if (p.payableStatus == 'PAID') {
            return [payable: toMap(p), idempotent: true]
        }
        if (p.payableStatus == 'VOID') {
            throw new IllegalStateException('cannot pay void payable')
        }
        financeLedgerService.ensureChartSeeded(p.npubOwner)
        if (!postingEngineService) {
            throw new PostingRulesException(PostingEngineService.CODE_MISSING,
                    'PostingEngineService is required for payable pay')
        }
        String eventId = fields.eventId?.toString()?.trim() ?: "ap-pay-${p.id}"
        String rail = (fields.rail ?: 'SEPA').toString().trim()

        JournalEntry je = postingEngineService.postEvent(
                p.npubOwner,
                PostingEngineService.EVENT_PAYABLE_PAY,
                ['payable.functional': p.amountEur],
                [
                        name      : "AP pay ${p.name}",
                        sourceType: 'PAYABLE',
                        sourceId  : p.id.toString(),
                        eventId   : eventId,
                        rail      : rail,
                        amountSats: p.amountSats,
                ]
        )

        financeTreasuryService.recordOutbound(p.npubOwner, [
                accountKind: 'FIAT_BANK',
                amountEur  : p.amountEur,
                amountSats : p.amountSats
        ])

        p.payableStatus = 'PAID'
        p.status = 'PAID'
        p.paidAt = new Date()
        p.journalEntryId = je.id
        p.save(failOnError: true, flush: true)
        return [payable: toMap(p), journalEntryId: je.id, idempotent: false]
    }

    @Transactional
    Payable voidPayable(Long id, String npubOwner) {
        Payable p = load(id, npubOwner)
        if (p.payableStatus == 'PAID' && p.journalEntryId) {
            financeLedgerService.reverseEntry(p.journalEntryId, p.npubOwner, 'Void payable')
            financeTreasuryService.recordInbound(p.npubOwner, [
                    accountKind: 'FIAT_BANK',
                    amountEur  : p.amountEur,
                    amountSats : p.amountSats
            ])
        }
        p.payableStatus = 'VOID'
        p.status = 'VOID'
        p.save(failOnError: true, flush: true)

        // ADR-049 L10 — keep PO CONFIRMED; clear payable link after void
        PurchaseOrder linked = PurchaseOrder.findByNpubOwnerAndFinancePayableId(p.npubOwner, p.id)
        if (linked) {
            linked.financePayableId = null
            linked.save(failOnError: true, flush: true)
            log.info('Cleared financePayableId on PO {} after voiding payable {}', linked.poNumber, p.id)
        }

        return p
    }

    @Transactional(readOnly = true)
    List<Payable> listPayables(String npubOwner, String status = null) {
        listPayablesPage(npubOwner, status, 200, 0).items as List<Payable>
    }

    static final List PAYABLE_SORT_FIELDS = [
            'id', 'name', 'vendorName', 'amountEur', 'payableStatus',
            'issueDate', 'dueDate', 'paidAt', 'dateCreated', 'lastUpdated'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPayablesPage(String npubOwner, String status = null, int max = 25, int offset = 0,
                         Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', PAYABLE_SORT_FIELDS)
        List items = Payable.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (status) eq('payableStatus', status)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Payable.createCriteria().count {
            eq('npubOwner', npub)
            if (status) eq('payableStatus', status)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    List<Payable> listOpen(String npubOwner) {
        listPayables(npubOwner, 'OPEN')
    }

    Map toMap(Payable p) {
        if (!p) return null
        [
                id            : p.id,
                name          : p.name,
                vendorName    : p.vendorName,
                amountEur     : p.amountEur,
                amountSats    : p.amountSats,
                currencyCode  : p.currencyCode,
                issueDate     : p.issueDate,
                dueDate       : p.dueDate,
                paidAt        : p.paidAt,
                payableStatus : p.payableStatus,
                journalEntryId: p.journalEntryId,
                npubOwner     : p.npubOwner
        ]
    }

    private Payable load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Payable p = Payable.get(id)
        if (!p || p.npubOwner != npub) throw new IllegalArgumentException("payable not found: ${id}")
        p
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') return null
        if (v instanceof BigDecimal) return v
        new BigDecimal(v.toString())
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
