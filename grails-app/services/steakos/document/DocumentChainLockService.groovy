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

package steakos.document

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.finance.Invoice
import steakos.finance.Payable
import steakos.finance.Payment
import steakos.finance.PayrollItem
import steakos.finance.ThreeWayMatch
import steakos.procurement.PurchaseOrder
import steakos.procurement.Receipt
import steakos.sales.CustomerQuote
import steakos.sales.SalesOrder
import steakos.service.Claims

/**
 * ADR-049 / DOCUMENT-CHAIN-LOCK-ROLLBACK-CONCEPT — shared chain lock.
 * Call {@link #assertUnlocked} before material edits; skip on compensating void/reverse/unmatch paths.
 */
@Slf4j
class DocumentChainLockService {

    static final Set<String> ISSUED_PLUS = ['ISSUED', 'PART_PAID', 'PAID'] as Set
    static final Set<String> PAYABLE_LIVE = ['OPEN', 'PART_PAID', 'PAID', 'DRAFT'] as Set

    /** Domain simple names (and aliases) that participate in chain lock. */
    static final Set<String> REGISTERED = [
            'CustomerQuote', 'SalesOrder', 'Invoice', 'Payment',
            'PurchaseOrder', 'Receipt', 'Payable',
            'PayrollItem', 'Claims', 'ThreeWayMatch'
    ] as Set

    boolean isRegistered(Class domainClass) {
        domainClass && REGISTERED.contains(domainClass.simpleName)
    }

    boolean isRegisteredName(String resource) {
        resource && REGISTERED.contains(resource)
    }

    /**
     * @return list of lock reason maps (empty = unlocked)
     */
    @Transactional(readOnly = true)
    List<Map> lockReasons(String resource, Long id, String npubOwner) {
        if (!resource || id == null) {
            return []
        }
        String npub = npubOwner?.trim()
        if (!npub) {
            return []
        }
        switch (resource) {
            case 'CustomerQuote':
                return reasonsForQuote(id, npub)
            case 'SalesOrder':
                return reasonsForOrder(id, npub)
            case 'Invoice':
                return reasonsForInvoice(id, npub)
            case 'Payment':
                return reasonsForPayment(id, npub)
            case 'PurchaseOrder':
                return reasonsForPo(id, npub)
            case 'Receipt':
                return reasonsForReceipt(id, npub)
            case 'Payable':
                return reasonsForPayable(id, npub)
            case 'PayrollItem':
                return reasonsForPayrollItem(id, npub)
            case 'Claims':
                return reasonsForClaim(id, npub)
            default:
                return []
        }
    }

    @Transactional(readOnly = true)
    void assertUnlocked(String resource, Long id, String npubOwner) {
        List<Map> reasons = lockReasons(resource, id, npubOwner)
        if (reasons) {
            String hint = reasons.collect { it.unlockHint ?: it.message }.join(' · ')
            throw new DocumentChainLockedException(
                    "Locked by downstream document(s): ${hint}",
                    reasons
            )
        }
    }

    @Transactional(readOnly = true)
    void assertDomainUnlocked(Class domainClass, Long id, String npubOwner) {
        if (!isRegistered(domainClass) || id == null) {
            return
        }
        assertUnlocked(domainClass.simpleName, id, npubOwner)
    }

    // -------------------------------------------------------------------------
    // Sales → AR
    // -------------------------------------------------------------------------

    private List<Map> reasonsForQuote(Long id, String npub) {
        List<Map> out = []
        CustomerQuote q = CustomerQuote.get(id)
        if (!q || q.npubOwner != npub) {
            return out
        }
        List<SalesOrder> orders = []
        if (q.resultingOrderId) {
            SalesOrder o = SalesOrder.get(q.resultingOrderId)
            if (o && o.npubOwner == npub) {
                orders << o
            }
        }
        SalesOrder.findAllByNpubOwnerAndSourceQuote(npub, q)?.each { SalesOrder o ->
            if (!orders.any { it.id == o.id }) {
                orders << o
            }
        }
        orders.each { SalesOrder o ->
            if (o.orderStatus in ['CONFIRMED', 'CLOSED'] || o.financeInvoiceId) {
                out << reason('SalesOrder', o.id, o.orderStatus,
                        "Sales order ${o.orderNumber ?: o.id} is ${o.orderStatus}",
                        'Cancel the order (only if no live issued invoice) or keep quote as accepted')
            }
            out.addAll(invoiceReasonsForOrder(o, npub))
        }
        out
    }

    private List<Map> reasonsForOrder(Long id, String npub) {
        SalesOrder o = SalesOrder.get(id)
        if (!o || o.npubOwner != npub) {
            return []
        }
        invoiceReasonsForOrder(o, npub)
    }

    private List<Map> invoiceReasonsForOrder(SalesOrder o, String npub) {
        List<Map> out = []
        if (!o?.financeInvoiceId) {
            return out
        }
        Invoice inv = Invoice.get(o.financeInvoiceId)
        if (!inv || inv.npubOwner != npub || inv.invoiceStatus == 'VOID') {
            return out
        }
        if (inv.invoiceStatus in ISSUED_PLUS) {
            out << reason('Invoice', inv.id, inv.invoiceStatus,
                    "Invoice ${inv.invoiceNumber ?: inv.id} is ${inv.invoiceStatus}",
                    'Void the invoice (reverse settle first if paid)')
        }
        List<Payment> settled = Payment.findAllByInvoiceAndPaymentStatus(inv, 'SETTLED')
        settled.each { Payment p ->
            out << reason('Payment', p.id, p.paymentStatus,
                    "Payment ${p.id} is SETTLED",
                    'Void invoice to reverse settle, then void')
        }
        out
    }

    private List<Map> reasonsForInvoice(Long id, String npub) {
        Invoice inv = Invoice.get(id)
        if (!inv || inv.npubOwner != npub || inv.invoiceStatus == 'VOID') {
            return []
        }
        List<Map> out = []
        if (inv.invoiceStatus in ['PART_PAID', 'PAID'] ||
                Payment.countByInvoiceAndPaymentStatus(inv, 'SETTLED') > 0) {
            out << reason('Payment', inv.id, inv.invoiceStatus,
                    "Invoice ${inv.invoiceNumber ?: inv.id} has settled payment(s)",
                    'Void invoice (auto-reverses settle JEs when period open) or use customer credit')
        }
        // Confirmed three-way N/A for AR invoices
        out
    }

    private List<Map> reasonsForPayment(Long id, String npub) {
        Payment p = Payment.get(id)
        if (!p || p.npubOwner != npub) {
            return []
        }
        if (p.paymentStatus == 'SETTLED' && p.journalEntryId) {
            return [reason('JournalEntry', p.journalEntryId, 'POSTED',
                    "Payment ${p.id} is SETTLED with journal ${p.journalEntryId}",
                    'Void the related invoice to reverse, or reverse the journal in an open period')]
        }
        []
    }

    // -------------------------------------------------------------------------
    // Procurement → AP + stock
    // -------------------------------------------------------------------------

    private List<Map> reasonsForPo(Long id, String npub) {
        PurchaseOrder po = PurchaseOrder.get(id)
        if (!po || po.npubOwner != npub) {
            return []
        }
        List<Map> out = []
        if (po.financePayableId) {
            Payable pay = Payable.get(po.financePayableId)
            if (pay && pay.npubOwner == npub && pay.payableStatus != 'VOID') {
                out << reason('Payable', pay.id, pay.payableStatus,
                        "Payable ${pay.id} is ${pay.payableStatus}",
                        pay.payableStatus == 'PAID'
                                ? 'Reverse vendor pay journal then void payable'
                                : 'Void the payable in Finance')
            }
        }
        List<Receipt> receipts = Receipt.findAllByNpubOwnerAndPurchaseOrder(npub, po)
        receipts.findAll { it.receiptStatus == 'POSTED' }.each { Receipt r ->
            out << reason('Receipt', r.id, r.receiptStatus,
                    "Goods receipt ${r.receiptNumber ?: r.id} is POSTED",
                    'Reverse stock / un-receive before changing PO quantities or cancelling')
        }
        out.addAll(confirmedMatchReasons(npub, po.id, null, po.financePayableId))
        out
    }

    private List<Map> reasonsForReceipt(Long id, String npub) {
        Receipt r = Receipt.get(id)
        if (!r || r.npubOwner != npub) {
            return []
        }
        List<Map> out = []
        if (r.receiptStatus == 'POSTED') {
            Long poId = r.purchaseOrder?.id
            out.addAll(confirmedMatchReasons(npub, poId, r.id, null))
            // Posted receipt material edits locked unless no confirmed match —
            // still lock header edits while POSTED (compensating un-receive is separate).
            if (!out) {
                out << reason('Receipt', r.id, 'POSTED',
                        "Goods receipt ${r.receiptNumber ?: r.id} is POSTED",
                        'Use stock reverse / un-receive before editing receipt lines')
            }
        }
        out
    }

    private List<Map> reasonsForPayable(Long id, String npub) {
        Payable p = Payable.get(id)
        if (!p || p.npubOwner != npub || p.payableStatus == 'VOID') {
            return []
        }
        List<Map> out = []
        if (p.payableStatus == 'PAID' && p.journalEntryId) {
            out << reason('JournalEntry', p.journalEntryId, 'POSTED',
                    "Payable ${p.id} is PAID with journal ${p.journalEntryId}",
                    'Reverse the pay journal in an open period, then void payable')
        }
        PurchaseOrder po = PurchaseOrder.findByNpubOwnerAndFinancePayableId(npub, p.id)
        if (po) {
            out.addAll(confirmedMatchReasons(npub, po.id, null, p.id))
        }
        out
    }

    private List<Map> confirmedMatchReasons(String npub, Long poId, Long receiptId, Long payableId) {
        List<Map> out = []
        List<ThreeWayMatch> matches = ThreeWayMatch.findAllByNpubOwnerAndMatchStatus(npub, 'CONFIRMED')
        matches.each { ThreeWayMatch m ->
            boolean hit = (poId && m.purchaseOrderId == poId) ||
                    (receiptId && m.receiptId == receiptId) ||
                    (payableId && m.payableId == payableId)
            if (hit) {
                out << reason('ThreeWayMatch', m.id, 'CONFIRMED',
                        "Three-way match ${m.id} is CONFIRMED",
                        'Unmatch (cancel confirmed match) before changing PO/receipt/payable')
            }
        }
        out
    }

    // -------------------------------------------------------------------------
    // Payroll
    // -------------------------------------------------------------------------

    private List<Map> reasonsForPayrollItem(Long id, String npub) {
        PayrollItem item = PayrollItem.get(id)
        if (!item || item.npubOwner != npub) {
            return []
        }
        if (item.payrollStatus in ['POSTED', 'PAID']) {
            return [reason('PayrollItem', item.id, item.payrollStatus,
                    "Payroll line ${item.id} is ${item.payrollStatus}",
                    item.payrollStatus == 'PAID'
                            ? 'Reverse payroll pay JE, then reverse recognize, then edit'
                            : 'Reverse payroll recognize JE in an open period, then edit')]
        }
        []
    }

    // -------------------------------------------------------------------------
    // AfterSales
    // -------------------------------------------------------------------------

    private List<Map> reasonsForClaim(Long id, String npub) {
        Claims c = Claims.get(id)
        if (!c || c.npubOwner != npub) {
            return []
        }
        List<Map> out = []
        if (c.financeCreditInvoiceId) {
            Invoice inv = Invoice.get(c.financeCreditInvoiceId)
            if (inv && inv.npubOwner == npub && inv.invoiceStatus != 'VOID') {
                if (inv.invoiceStatus in ISSUED_PLUS || inv.invoiceStatus == 'DRAFT') {
                    // DRAFT credit still locks claim amounts once approved+linked (obligation intent)
                    out << reason('Invoice', inv.id, inv.invoiceStatus,
                            "Finance credit invoice ${inv.invoiceNumber ?: inv.id} is ${inv.invoiceStatus}",
                            'Void the credit invoice before changing claim amounts')
                }
            }
        }
        out
    }

    private static Map reason(String type, Long id, String status, String message, String unlockHint) {
        [
                downstreamType: type,
                downstreamId  : id,
                status        : status,
                message       : message,
                unlockHint    : unlockHint
        ]
    }
}
