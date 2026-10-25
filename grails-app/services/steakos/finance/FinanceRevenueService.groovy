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

/**
 * Revenue / invoicing / AR (APQC 9.2, F-S2).
 * Cash-oriented recognition (FS8): primary ledger post when payment settles via {@link FinanceLedgerService}.
 * Commercial docs store primary rail amount only (PDR-023 D14).
 * BTC rails: sats filled at payment-time FX when missing (PDR-023 T4).
 */
@Slf4j
class FinanceRevenueService {

    static final List DEFAULT_RAILS = ['SEPA', 'BTC_ONCHAIN', 'BTC_LIGHTNING'].asImmutable()

    /** Settlement rails including Copper CU-F5 wallet-address path */
    static final List SETTLE_RAILS = (DEFAULT_RAILS + ['BTC_WALLET_ADDRESS', 'OTHER']).asImmutable()

    FinanceLedgerService financeLedgerService
    FinanceTreasuryService financeTreasuryService
    PostingEngineService postingEngineService
    TenantMoneyService tenantMoneyService
    BtcFxService btcFxService
    steakos.payments.BtcIntegrationLevelService btcIntegrationLevelService
    steakos.nostr.NostrPublishService nostrPublishService
    steakos.configuration.PartnerLocaleService partnerLocaleService

    private static final Set BTC_RAILS = ['BTC_ONCHAIN', 'BTC_LIGHTNING', 'BTC_WALLET_ADDRESS'] as Set
    private static final List REGISTERABLE_RAILS =
            (DEFAULT_RAILS + ['BTC_WALLET_ADDRESS']).asImmutable()

    // -------------------------------------------------------------------------
    // Create / issue
    // -------------------------------------------------------------------------

    @Transactional
    Invoice createInvoice(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        financeLedgerService.ensureChartSeeded(npub)

        Map primary = resolvePrimary(npub, fields)
        BigDecimal amountEur = primary.amountFunctional as BigDecimal
        String currencyCode = primary.currencyCode as String

        String number = fields.invoiceNumber?.toString()?.trim()
        if (!number) {
            number = nextInvoiceNumber(npub)
        } else if (Invoice.findByNpubOwnerAndInvoiceNumber(npub, number)) {
            throw new IllegalArgumentException("invoice number already exists: ${number}")
        }

        String name = fields.name?.toString()?.trim() ?: "Invoice ${number}"
        Date issueDate = parseDate(fields.issueDate) ?: new Date()
        Date dueDate = parseDate(fields.dueDate)

        Invoice inv = new Invoice(
                npubOwner      : npub,
                name           : name,
                description    : fields.description as String,
                invoiceNumber  : number,
                customerId     : fields.customerId != null && fields.customerId != '' ? fields.customerId as Long : null,
                customerName   : fields.customerName as String,
                customerContact: fields.customerContact as String,
                issueDate      : issueDate,
                dueDate        : dueDate,
                amountEur      : amountEur,
                netAmountEur   : toBd(fields.netAmountEur),
                taxAmountEur   : toBd(fields.taxAmountEur),
                amountSats     : null,
                exchangeRate   : null,
                currencyCode   : currencyCode,
                invoiceStatus  : 'DRAFT',
                status         : 'DRAFT',
                recurring      : fields.recurring as Boolean ?: Boolean.FALSE,
                referenceText  : fields.referenceText as String
        )
        inv.save(failOnError: true, flush: true)
        log.info('Created invoice {} for {}', number, npub)
        return inv
    }

    private Map resolvePrimary(String npub, Map fields) {
        if (tenantMoneyService != null) {
            return tenantMoneyService.resolveDocumentPrimaryAmount(npub, fields)
        }
        BigDecimal amountEur = toBd(fields.amount != null ? fields.amount : fields.amountEur)
        if (amountEur == null || amountEur <= BigDecimal.ZERO) {
            throw new IllegalArgumentException('amount must be > 0')
        }
        [amountFunctional: amountEur, currencyCode: (fields.currencyCode ?: 'EUR') as String]
    }

    /**
     * DRAFT → ISSUED. Optionally creates default three payment rails (ADR-009).
     */
    @Transactional
    Invoice issueInvoice(Long invoiceId, String npubOwner, Map opts = [:]) {
        Invoice inv = loadInvoice(invoiceId, npubOwner)
        if (inv.invoiceStatus == 'VOID') {
            throw new IllegalStateException('cannot issue a void invoice')
        }
        if (inv.invoiceStatus in ['PAID', 'PART_PAID']) {
            return inv
        }
        if (inv.invoiceStatus != 'DRAFT' && inv.invoiceStatus != 'ISSUED') {
            throw new IllegalStateException("cannot issue from status ${inv.invoiceStatus}")
        }

        inv.invoiceStatus = 'ISSUED'
        inv.status = 'ISSUED'
        if (!inv.issueDate) {
            inv.issueDate = new Date()
        }
        partnerLocaleService?.snapshotInvoice(inv)
        inv.save(failOnError: true, flush: true)

        boolean addRails = opts.containsKey('defaultRails') ? opts.defaultRails as Boolean : true
        if (addRails) {
            issueRailsForLevel(inv.npubOwner).each { Map railSpec ->
                String rail = railSpec.rail as String
                if (!PaymentOption.findByInvoiceAndRail(inv, rail)) {
                    registerPaymentOption(inv.id, inv.npubOwner, railSpec)
                }
            }
        }
        return Invoice.get(inv.id)
    }

    /** OI-BTC01: payment options advertised on issue depend on Bitcoin integration level. */
    private List<Map> issueRailsForLevel(String npub) {
        String level = null
        Map pub = [:]
        try {
            level = btcIntegrationLevelService?.currentLevel(npub)
            pub = btcIntegrationLevelService?.publicLevel(npub) ?: [:]
        } catch (ignored) {
        }
        if (level == steakos.payments.BtcIntegrationLevelService.LEVEL_DEFERRED) {
            return [[rail: 'SEPA', estimatedSettle: '1-3 days',
                     name: 'SEPA (Bitcoin receive deferred — Level 1)']]
        }
        if (level == steakos.payments.BtcIntegrationLevelService.LEVEL_SINGLE_ADDRESS) {
            String addr = pub.receiveAddress as String
            List<Map> opts = [[rail: 'SEPA', estimatedSettle: '1-3 days']]
            if (addr) {
                opts << [
                        rail            : 'BTC_WALLET_ADDRESS',
                        paymentUri      : "bitcoin:${addr}",
                        qrPayload       : addr,
                        estimatedSettle : 'manual mark paid',
                        name            : 'BTC single address (Level 2 — occasional)'
                ]
            }
            return opts
        }
        // Level 3 / unset → classic three rails (Lightning honesty only at Level 3)
        return DEFAULT_RAILS.collect { String rail -> [rail: rail] }
    }

    @Transactional
    PaymentOption registerPaymentOption(Long invoiceId, String npubOwner, Map fields) {
        Invoice inv = loadInvoice(invoiceId, npubOwner)
        String rail = fields.rail?.toString()?.trim()
        if (!rail) {
            throw new IllegalArgumentException('rail is required')
        }
        if (!(rail in REGISTERABLE_RAILS)) {
            throw new IllegalArgumentException("rail must be one of ${REGISTERABLE_RAILS}")
        }

        PaymentOption existing = PaymentOption.findByInvoiceAndRail(inv, rail)
        if (existing) {
            existing.paymentUri = fields.paymentUri as String ?: existing.paymentUri
            existing.qrPayload = fields.qrPayload as String ?: existing.qrPayload
            existing.estimatedSettle = fields.estimatedSettle as String ?: existing.estimatedSettle
            existing.externalInvoiceId = fields.externalInvoiceId as String ?: existing.externalInvoiceId
            existing.active = fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE
            existing.save(failOnError: true, flush: true)
            return existing
        }

        String est = fields.estimatedSettle as String
        if (!est) {
            est = rail == 'SEPA' ? '1-3 days' : (rail == 'BTC_ONCHAIN' ? '~1 hour' : 'instant')
        }

        PaymentOption opt = new PaymentOption(
                npubOwner        : inv.npubOwner,
                invoice          : inv,
                name             : fields.name?.toString() ?: "${rail} option",
                description      : fields.description as String,
                rail             : rail,
                paymentUri       : fields.paymentUri as String,
                qrPayload        : fields.qrPayload as String,
                estimatedSettle  : est,
                externalInvoiceId: fields.externalInvoiceId as String,
                active           : Boolean.TRUE
        )
        opt.save(failOnError: true, flush: true)
        return opt
    }

    // -------------------------------------------------------------------------
    // Settlement (FS8 cash-oriented → ledger)
    // -------------------------------------------------------------------------

    /**
     * Record a settled payment and post Dr wallet / Cr Revenue via {@link PostingEngineService}
     * ({@code invoice.settle} rules). Idempotent when {@code eventId} is set.
     */
    @Transactional
    Map recordSettlement(Long invoiceId, String npubOwner, Map fields) {
        Invoice inv = loadInvoice(invoiceId, npubOwner)
        if (inv.invoiceStatus == 'VOID') {
            throw new IllegalStateException('cannot settle a void invoice')
        }
        if (inv.invoiceStatus == 'DRAFT') {
            throw new IllegalStateException('issue the invoice before recording payment')
        }

        String eventId = fields.eventId?.toString()?.trim()
        if (eventId) {
            Payment existing = Payment.findByNpubOwnerAndEventId(inv.npubOwner, eventId)
            if (existing) {
                log.debug('Idempotent settlement eventId={}', eventId)
                return [
                        payment: paymentToMap(existing),
                        invoice: invoiceToMap(Invoice.get(inv.id), true),
                        journalEntryId: existing.journalEntryId,
                        idempotent: true
                ]
            }
        }

        String rail = (fields.rail ?: 'SEPA').toString().trim()
        if (!(rail in SETTLE_RAILS)) {
            throw new IllegalArgumentException("unknown rail: ${rail} (allowed: ${SETTLE_RAILS})")
        }

        // Copper CU-F5: operator records settlement against a BTC wallet address (no live BTCPay required)
        String walletAddress = (fields.walletAddress ?: fields.btcAddress ?: fields.paymentUri)?.toString()?.trim()
        if (rail == 'BTC_WALLET_ADDRESS') {
            if (!walletAddress) {
                throw new IllegalArgumentException('walletAddress is required for BTC_WALLET_ADDRESS settle')
            }
            if (walletAddress.length() < 14 || walletAddress.length() > 128) {
                throw new IllegalArgumentException('walletAddress length looks invalid')
            }
        }

        // Functional settlement amount = invoice primary (PDR-023); fields may override for partial pay
        BigDecimal amountEur = toBd(fields.amount != null ? fields.amount : fields.amountEur)
        if (amountEur == null) {
            amountEur = inv.amountEur
        }
        if (amountEur == null || amountEur <= BigDecimal.ZERO) {
            throw new IllegalArgumentException('settlement amount must be > 0')
        }

        Long amountSats = fields.amountSats != null && fields.amountSats != '' ?
                fields.amountSats as Long : null
        // T4: payment-time sats for BTC rails — never use live UI hint cache semantics beyond FX fetch
        if (amountSats == null && rail in BTC_RAILS) {
            amountSats = estimateSatsAtPayment(inv.npubOwner, amountEur, inv.currencyCode)
        }

        financeLedgerService.ensureChartSeeded(inv.npubOwner)
        if (financeTreasuryService != null) {
            try {
                financeTreasuryService.ensureDefaultPositions(inv.npubOwner)
            } catch (Exception e) {
                log.debug('ensureDefaultPositions: {}', e.message)
            }
        }

        if (!postingEngineService) {
            throw new PostingRulesException(PostingEngineService.CODE_MISSING,
                    'PostingEngineService is required for settlement')
        }

        String jeEventId = eventId ?: "pay-${inv.id}-${System.currentTimeMillis()}"
        String settleMemo = rail == 'BTC_WALLET_ADDRESS'
                ? "BTC addr ${walletAddress.take(12)}…"
                : rail

        // Cash recognition via company posting rules (invoice.settle)
        JournalEntry je = postingEngineService.postEvent(
                inv.npubOwner,
                PostingEngineService.EVENT_INVOICE_SETTLE,
                ['payment.functional': amountEur],
                [
                        name       : "Payment ${inv.invoiceNumber} ${rail}",
                        description: "Settlement invoice ${inv.invoiceNumber}" +
                                (walletAddress ? " wallet=${walletAddress}" : ''),
                        entryDate  : parseDate(fields.receivedAt) ?: new Date(),
                        period     : fields.period as String,
                        sourceType : 'PAYMENT',
                        sourceId   : inv.id.toString(),
                        eventId    : jeEventId,
                        memo       : fields.memo as String ?: "Invoice ${inv.invoiceNumber}",
                        rail       : rail,
                        amountSats : amountSats,
                        lineMemos  : [settleMemo, "Revenue ${inv.invoiceNumber}"],
                ]
        )

        Payment payment = new Payment(
                npubOwner     : inv.npubOwner,
                invoice       : inv,
                name          : fields.name?.toString() ?: "Pay ${inv.invoiceNumber}",
                description   : fields.description as String ?:
                        (walletAddress ? "BTC wallet ${walletAddress}" : null),
                rail          : rail,
                amountEur     : amountEur,
                amountSats    : amountSats,
                receivedAt    : parseDate(fields.receivedAt) ?: new Date(),
                paymentStatus : 'SETTLED',
                status        : 'SETTLED',
                externalTxId  : (fields.externalTxId as String) ?: walletAddress,
                eventId       : eventId ?: jeEventId,
                journalEntryId: je.id
        )
        payment.save(failOnError: true, flush: true)

        // F-S3: mirror inbound cash to treasury position
        Treasury treasuryPos = null
        if (financeTreasuryService != null) {
            try {
                treasuryPos = financeTreasuryService.recordInbound(inv.npubOwner, [
                        rail      : rail,
                        amountEur : amountEur,
                        amountSats: amountSats
                ])
            } catch (Exception e) {
                log.warn('Treasury inbound failed (non-fatal): {}', e.message)
            }
        }

        refreshInvoicePaymentStatus(inv)
        inv = Invoice.get(inv.id)

        // OI-NOSTR01 D12: dual-write selected audit (invoice settle) when Level ≥ 1
        Map nostrPub = null
        try {
            if (nostrPublishService != null) {
                nostrPub = nostrPublishService.publishAuditEvent(inv.npubOwner,
                        "invoice.settle ${inv.invoiceNumber} rail=${rail} amount=${amountEur}",
                        [event: 'invoice.settle', invoiceId: inv.id, rail: rail, paymentId: payment.id])
            }
        } catch (Exception e) {
            log.debug('Nostr audit dual-write skipped: {}', e.message)
        }

        log.info('Settled payment id={} invoice={} je={}', payment.id, inv.invoiceNumber, je.id)
        return [
                payment       : paymentToMap(payment),
                invoice       : invoiceToMap(inv, true),
                journalEntryId: je.id,
                journalEntry  : financeLedgerService.entryToMap(je),
                treasury      : treasuryPos && financeTreasuryService ?
                        financeTreasuryService.toMap(treasuryPos) : null,
                nostrPublish  : nostrPub,
                idempotent    : false
        ]
    }

    /**
     * Void invoice. Reverses settled payment journal entries when present.
     */
    @Transactional
    Invoice voidInvoice(Long invoiceId, String npubOwner, String reason = null) {
        Invoice inv = loadInvoice(invoiceId, npubOwner)
        if (inv.invoiceStatus == 'VOID') {
            return inv
        }

        List<Payment> settled = Payment.findAllByInvoiceAndPaymentStatus(inv, 'SETTLED')
        settled.each { Payment p ->
            if (p.journalEntryId) {
                try {
                    financeLedgerService.reverseEntry(
                            p.journalEntryId,
                            inv.npubOwner,
                            reason ?: "Void invoice ${inv.invoiceNumber}"
                    )
                } catch (IllegalStateException | IllegalArgumentException e) {
                    log.warn('Could not reverse JE {} for payment {}: {}', p.journalEntryId, p.id, e.message)
                }
            }
            p.paymentStatus = 'REFUNDED'
            p.status = 'REFUNDED'
            p.save(failOnError: true)
        }

        inv.invoiceStatus = 'VOID'
        inv.status = 'VOID'
        inv.settledAt = null
        inv.save(failOnError: true, flush: true)
        return inv
    }

    // -------------------------------------------------------------------------
    // Customer credit (AfterSales AS3 B hand-off — document only, no JE here)
    // -------------------------------------------------------------------------

    /**
     * Create a DRAFT customer-credit invoice representing a refund obligation
     * (e.g. approved warranty claim). Does <strong>not</strong> post journals;
     * settlement/refund rails remain separate Finance steps.
     */
    @Transactional
    Invoice createCustomerCredit(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        financeLedgerService.ensureChartSeeded(npub)

        BigDecimal amountEur = toBd(fields.amountEur)
        if (amountEur == null || amountEur <= BigDecimal.ZERO) {
            throw new IllegalArgumentException('amountEur must be > 0 for customer credit')
        }

        String number = fields.invoiceNumber?.toString()?.trim()
        if (!number) {
            String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
            int seq = Invoice.countByNpubOwner(npub) + 1
            number = "CR-${period}-${String.format('%04d', seq)}"
            while (Invoice.findByNpubOwnerAndInvoiceNumber(npub, number)) {
                seq++
                number = "CR-${period}-${String.format('%04d', seq)}"
            }
        } else if (Invoice.findByNpubOwnerAndInvoiceNumber(npub, number)) {
            throw new IllegalArgumentException("invoice number already exists: ${number}")
        }

        String name = fields.name?.toString()?.trim() ?: "Customer credit ${number}"
        String ref = fields.referenceText?.toString()
        if (!ref && fields.sourceType) {
            ref = "${fields.sourceType}:${fields.sourceId ?: ''}"
        }

        Invoice inv = new Invoice(
                npubOwner      : npub,
                name           : name,
                description    : (fields.description ?: 'Customer credit / refund obligation') as String,
                invoiceNumber  : number,
                customerId     : fields.customerId != null && fields.customerId != '' ? fields.customerId as Long : null,
                customerName   : fields.customerName as String,
                issueDate      : parseDate(fields.issueDate) ?: new Date(),
                amountEur      : amountEur,
                amountSats     : fields.amountSats != null && fields.amountSats != '' ? fields.amountSats as Long : null,
                currencyCode   : (fields.currencyCode ?: 'EUR') as String,
                invoiceStatus  : 'DRAFT',
                status         : 'DRAFT',
                recurring      : Boolean.FALSE,
                referenceText  : ref,
                type           : 'CREDIT'
        )
        inv.save(failOnError: true, flush: true)
        log.info('Created customer credit {} for {}', number, npub)
        return inv
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<Invoice> listInvoices(String npubOwner, String statusFilter = null) {
        listInvoicesPage(npubOwner, statusFilter, 200, 0).items as List<Invoice>
    }

    static final List INVOICE_SORT_FIELDS = [
            'id', 'name', 'code', 'status', 'invoiceStatus', 'invoiceNumber',
            'amountEur', 'customerName', 'dateCreated', 'lastUpdated'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listInvoicesPage(String npubOwner, String statusFilter = null, int max = 25, int offset = 0,
                         Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', INVOICE_SORT_FIELDS)
        String term = steakos.api.ListPaging.likeContains(listOpts?.q as String)
        List items = Invoice.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (statusFilter) eq('invoiceStatus', statusFilter)
            if (term) {
                or {
                    ilike('name', term)
                    ilike('invoiceNumber', term)
                    ilike('customerName', term)
                    ilike('description', term)
                    ilike('referenceText', term)
                }
            }
            order(so.sort as String, so.order as String)
            if (so.sort != 'id') order('id', 'asc')
        } as List
        Number total = Invoice.createCriteria().count {
            eq('npubOwner', npub)
            if (statusFilter) eq('invoiceStatus', statusFilter)
            if (term) {
                or {
                    ilike('name', term)
                    ilike('invoiceNumber', term)
                    ilike('customerName', term)
                    ilike('description', term)
                    ilike('referenceText', term)
                }
            }
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    /** Open = ISSUED or PART_PAID */
    @Transactional(readOnly = true)
    List<Invoice> listOpenReceivables(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Invoice.findAllByNpubOwnerAndInvoiceStatusInList(
                npub, ['ISSUED', 'PART_PAID'], [sort: 'id', order: 'desc'])
    }

    @Transactional(readOnly = true)
    Invoice getInvoice(Long id, String npubOwner) {
        loadInvoice(id, npubOwner)
    }

    // -------------------------------------------------------------------------
    // Maps
    // -------------------------------------------------------------------------

    Map invoiceToMap(Invoice inv, boolean includeChildren = false) {
        if (!inv) {
            return null
        }
        Map m = [
                id             : inv.id,
                name           : inv.name,
                description    : inv.description,
                invoiceNumber  : inv.invoiceNumber,
                customerId     : inv.customerId,
                customerName   : inv.customerName,
                customerContact: inv.customerContact,
                issueDate      : inv.issueDate,
                dueDate        : inv.dueDate,
                amountEur      : inv.amountEur,
                netAmountEur   : inv.netAmountEur,
                taxAmountEur   : inv.taxAmountEur,
                amountSats     : inv.amountSats,
                exchangeRate   : inv.exchangeRate,
                currencyCode   : inv.currencyCode,
                documentLocale : inv.documentLocale,
                invoiceStatus  : inv.invoiceStatus,
                status         : inv.status,
                recurring      : inv.recurring,
                settledAt      : inv.settledAt,
                referenceText  : inv.referenceText,
                npubOwner      : inv.npubOwner
        ]
        if (includeChildren) {
            m.paymentOptions = PaymentOption.findAllByInvoice(inv).collect { paymentOptionToMap(it) }
            m.payments = Payment.findAllByInvoice(inv, [sort: 'id', order: 'desc']).collect { paymentToMap(it) }
            m.paidEur = sumSettled(inv)
            try {
                Map lvl = btcIntegrationLevelService?.publicLevel(inv.npubOwner)
                if (lvl) {
                    m.btcIntegration = [
                            level                 : lvl.level,
                            uiLevel               : lvl.uiLevel,
                            uiLabel               : lvl.uiLabel,
                            receiveAddress        : lvl.receiveAddress,
                            receiveAddressMasked  : lvl.receiveAddressMasked,
                            receiveAddressLabel   : lvl.receiveAddressLabel,
                            receiveAddressQrDataUrl: lvl.receiveAddressQrDataUrl,
                            level2Warning         : lvl.level2Warning
                    ]
                }
            } catch (ignored) {
            }
        }
        return m
    }

    Map paymentToMap(Payment p) {
        if (!p) {
            return null
        }
        [
                id            : p.id,
                name          : p.name,
                invoiceId     : p.invoice?.id,
                rail          : p.rail,
                amountEur     : p.amountEur,
                amountSats    : p.amountSats,
                receivedAt    : p.receivedAt,
                paymentStatus : p.paymentStatus,
                externalTxId  : p.externalTxId,
                eventId       : p.eventId,
                journalEntryId: p.journalEntryId,
                npubOwner     : p.npubOwner
        ]
    }

    Map paymentOptionToMap(PaymentOption o) {
        if (!o) {
            return null
        }
        [
                id               : o.id,
                name             : o.name,
                invoiceId        : o.invoice?.id,
                rail             : o.rail,
                paymentUri       : o.paymentUri,
                qrPayload        : o.qrPayload,
                estimatedSettle  : o.estimatedSettle,
                externalInvoiceId: o.externalInvoiceId,
                active           : o.active
        ]
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    /**
     * Estimate sats at payment time from functional amount + live FX (T4).
     * Returns null if FX unavailable (settlement still posts functional legs).
     */
    private Long estimateSatsAtPayment(String npub, BigDecimal functionalAmount, String docCurrency) {
        if (functionalAmount == null || functionalAmount <= BigDecimal.ZERO) {
            return null
        }
        String cur = (docCurrency ?: '').trim().toUpperCase()
        if (!cur && financeLedgerService != null) {
            cur = financeLedgerService.functionalCurrencyFor(npub)
        }
        if (!cur) {
            cur = 'EUR'
        }
        if (cur == 'BTC' || cur == 'XBT') {
            return BtcFxService.btcMajorToSats(functionalAmount)
        }
        if (!(cur in ['EUR', 'USD'])) {
            cur = 'EUR'
        }
        if (btcFxService == null) {
            return null
        }
        try {
            return btcFxService.fiatToSats(functionalAmount, cur)
        } catch (Exception e) {
            log.warn('payment-time FX sats estimate failed: {}', e.message)
            return null
        }
    }

    private void refreshInvoicePaymentStatus(Invoice inv) {
        BigDecimal paid = sumSettled(inv)
        BigDecimal total = inv.amountEur ?: BigDecimal.ZERO
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            if (inv.invoiceStatus != 'VOID' && inv.invoiceStatus != 'DRAFT') {
                inv.invoiceStatus = 'ISSUED'
                inv.status = 'ISSUED'
                inv.settledAt = null
            }
        } else if (paid.compareTo(total) >= 0) {
            inv.invoiceStatus = 'PAID'
            inv.status = 'PAID'
            inv.settledAt = new Date()
        } else {
            inv.invoiceStatus = 'PART_PAID'
            inv.status = 'PART_PAID'
            inv.settledAt = null
        }
        inv.save(failOnError: true, flush: true)
    }

    private BigDecimal sumSettled(Invoice inv) {
        List<Payment> settled = Payment.findAllByInvoiceAndPaymentStatus(inv, 'SETTLED')
        settled.inject(BigDecimal.ZERO) { BigDecimal acc, Payment p ->
            acc.add(p.amountEur ?: BigDecimal.ZERO)
        } as BigDecimal
    }

    private Invoice loadInvoice(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Invoice inv = Invoice.get(id)
        if (!inv || inv.npubOwner != npub) {
            throw new IllegalArgumentException("invoice not found: ${id}")
        }
        return inv
    }

    private String nextInvoiceNumber(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String prefix = "INV-${period}-"
        int seq = Invoice.countByNpubOwner(npub) + 1
        String candidate = "${prefix}${String.format('%04d', seq)}"
        while (Invoice.findByNpubOwnerAndInvoiceNumber(npub, candidate)) {
            seq++
            candidate = "${prefix}${String.format('%04d', seq)}"
        }
        return candidate
    }

    private static String requireNpub(String npubOwner) {
        String n = npubOwner?.trim()
        if (!n) {
            throw new IllegalArgumentException('npubOwner is required')
        }
        return n
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') {
            return null
        }
        if (v instanceof BigDecimal) {
            return v
        }
        return new BigDecimal(v.toString())
    }

    private static Date parseDate(Object raw) {
        if (raw == null || raw == '') {
            return null
        }
        if (raw instanceof Date) {
            return raw
        }
        String s = raw.toString().trim()
        if (s.length() >= 10 && s.charAt(4) == '-' as char) {
            try {
                return Date.parse('yyyy-MM-dd', s.substring(0, 10))
            } catch (ignored) {
            }
        }
        return null
    }
}
