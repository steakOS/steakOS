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

package steakos.payments

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.finance.FinanceRevenueService
import steakos.finance.Invoice
import steakos.finance.PaymentOption

/**
 * Single writer for rail-driven settlement (OI-F28).
 * Inbound → verify → re-fetch (BTCPay) → FinanceRevenueService.recordSettlement.
 */
@Slf4j
class PaymentSettlementService {

    FinanceRevenueService financeRevenueService
    PaymentProviderService paymentProviderService

    /**
     * Handle webhook body (JSON map). Prefer {@code rawBody} for HMAC when available.
     */
    @Transactional
    Map handleInboundWebhook(String npubOwner, Map body, Map headers = [:], byte[] rawBody = null) {
        String npub = requireNpub(npubOwner)
        BitcoinPaymentProvider provider = paymentProviderService.providerFor(npub)
        if (provider instanceof steakos.payments.BtcpayPaymentProvider && !provider.webhookSecret?.trim()) {
            throw new PaymentProviderException('webhook secret is not configured', 'WEBHOOK_SECRET', 503)
        }
        byte[] raw = rawBody
        if (raw == null || raw.length == 0) {
            raw = body ? groovy.json.JsonOutput.toJson(body).getBytes('UTF-8') : new byte[0]
        }
        if (!provider.verifyWebhook(raw, headers ?: [:])) {
            throw new PaymentProviderException('webhook verification failed', 'WEBHOOK_VERIFY', 401)
        }

        WebhookEvent event = provider.parseWebhook(raw)
        applyBodyOverlays(event, body)

        // Mock: parseWebhook may already mark settled; externalId path
        if (provider.code == MockBitcoinPaymentProvider.CODE) {
            return settleMockOrLegacy(npub, provider, event, body)
        }

        // BTCPay: require external invoice id + authoritative re-fetch
        if (!event.externalInvoiceId && body?.invoiceId && !body.invoiceId.toString().isLong()) {
            event.externalInvoiceId = body.invoiceId.toString()
        }
        if (!event.externalInvoiceId) {
            throw new IllegalArgumentException('BTCPay webhook missing invoiceId')
        }

        InboundStatus st = provider.fetchInbound(event.externalInvoiceId)
        event.rawStatus = st.status
        event.paidEur = st.paidEur ?: event.paidEur
        event.paidSats = st.paidSats ?: event.paidSats

        // metadata from re-fetch
        if (!event.steakosInvoiceId && provider instanceof BtcpayPaymentProvider) {
            try {
                Map inv = provider.client.getInvoice(event.externalInvoiceId)
                event.steakosInvoiceId = extractSteakosId(inv)
            } catch (Exception e) {
                log.debug('metadata extract failed: {}', e.message)
            }
        }
        if (!event.steakosInvoiceId) {
            event.steakosInvoiceId = findInvoiceIdByExternal(npub, event.externalInvoiceId)
        }

        if (st.status != 'SETTLED') {
            return [
                    accepted  : true,
                    settled   : false,
                    provider  : provider.code,
                    status    : st.status,
                    externalId: event.externalInvoiceId,
                    invoiceId : event.steakosInvoiceId,
                    note      : 'Invoice not SETTLED — no JE posted'
            ]
        }

        return settleInboundEvent(npub, event)
    }

    private Map settleMockOrLegacy(String npub, BitcoinPaymentProvider provider, WebhookEvent event, Map body) {
        if (event.externalInvoiceId) {
            try {
                InboundStatus st = provider.fetchInbound(event.externalInvoiceId)
                if (st.status == 'SETTLED') {
                    event.rawStatus = 'SETTLED'
                    event.paidEur = st.paidEur ?: event.paidEur
                    event.paidSats = st.paidSats ?: event.paidSats
                }
            } catch (ignored) {}
        }
        if (!event.steakosInvoiceId) {
            event.steakosInvoiceId = findInvoiceIdByExternal(npub, event.externalInvoiceId)
        }
        if (!event.steakosInvoiceId) {
            throw new IllegalArgumentException('steakosInvoiceId (or invoiceId) required for settlement')
        }
        return settleInboundEvent(npub, event)
    }

    private static void applyBodyOverlays(WebhookEvent event, Map body) {
        if (!body) return
        if (!event.steakosInvoiceId && body.invoiceId != null && body.invoiceId.toString().isLong()) {
            event.steakosInvoiceId = body.invoiceId as Long
        }
        if (!event.steakosInvoiceId && body.steakosInvoiceId != null) {
            event.steakosInvoiceId = body.steakosInvoiceId as Long
        }
        if (!event.eventId && body.eventId) event.eventId = body.eventId.toString()
        if (!event.externalInvoiceId && body.externalId) event.externalInvoiceId = body.externalId.toString()
        if (!event.rail && body.rail) event.rail = body.rail.toString()
        if (event.paidEur == null && body.amountEur != null) event.paidEur = body.amountEur as BigDecimal
        if (event.paidSats == null && body.amountSats != null) event.paidSats = body.amountSats as Long
    }

    private static Long extractSteakosId(Map inv) {
        if (!(inv?.metadata instanceof Map)) return null
        Map md = inv.metadata as Map
        def sid = md.steakosInvoiceId ?: md.orderId
        if (sid == null) return null
        String s = sid.toString()
        if (s.isLong()) return s as Long
        if (s.startsWith('steakos-')) {
            String n = s.substring('steakos-'.length())
            if (n.isLong()) return n as Long
        }
        return null
    }

    @Transactional
    Map settleInboundEvent(String npubOwner, WebhookEvent event) {
        String npub = requireNpub(npubOwner)
        if (!event?.steakosInvoiceId) {
            throw new IllegalArgumentException('steakosInvoiceId (or invoiceId) required for settlement')
        }
        String eventId = event.eventId?.trim() ?: "rail-${event.externalInvoiceId ?: event.steakosInvoiceId}-SETTLED"
        Map result = financeRevenueService.recordSettlement(event.steakosInvoiceId, npub, [
                rail        : event.rail ?: 'BTC_LIGHTNING',
                amountEur   : event.paidEur,
                amountSats  : event.paidSats,
                eventId     : eventId,
                externalTxId: event.externalInvoiceId ?: eventId
        ])
        result.provider = paymentProviderService.providerFor(npub).code
        result.webhookEventId = eventId
        result.settled = true
        result
    }

    /**
     * Create inbound rail object for an issued invoice; updates PaymentOption URIs/external ids.
     */
    @Transactional
    Map createInboundForInvoice(String npubOwner, Long invoiceId, Map fields = [:]) {
        String npub = requireNpub(npubOwner)
        Invoice inv = Invoice.findByIdAndNpubOwner(invoiceId, npub)
        if (!inv) {
            throw new IllegalArgumentException("invoice not found: ${invoiceId}")
        }
        if (inv.invoiceStatus == 'DRAFT') {
            throw new IllegalStateException('issue the invoice before creating payment rails')
        }
        if (inv.invoiceStatus == 'VOID') {
            throw new IllegalStateException('cannot create rails for void invoice')
        }

        BitcoinPaymentProvider provider = paymentProviderService.providerFor(npub)
        BigDecimal amountEur = fields.amountEur != null ? fields.amountEur as BigDecimal : inv.amountEur
        InboundCreate cmd = new InboundCreate(
                npubOwner        : npub,
                steakosInvoiceId : invoiceId,
                amountEur        : amountEur,
                amountSats       : fields.amountSats != null ? fields.amountSats as Long : inv.amountSats,
                currency         : (fields.currency ?: inv.currencyCode ?: 'EUR') as String,
                description      : (fields.description ?: inv.invoiceNumber ?: inv.name) as String,
                metadata         : (fields.metadata as Map) ?: [:]
        )
        InboundInvoice rail = provider.createInbound(cmd)

        (rail.payMethods ?: []).each { PayMethod m ->
            if (!(m.rail in ['BTC_LIGHTNING', 'BTC_ONCHAIN'])) return
            financeRevenueService.registerPaymentOption(invoiceId, npub, [
                    rail             : m.rail,
                    paymentUri       : m.uri,
                    qrPayload        : m.uri,
                    externalInvoiceId: rail.externalId,
                    estimatedSettle  : m.rail == 'BTC_LIGHTNING' ? 'instant' : '~1 hour',
                    name             : m.label ?: "${m.rail} via ${provider.code}"
            ])
        }
        if (!PaymentOption.findByInvoiceAndRail(inv, 'SEPA')) {
            financeRevenueService.registerPaymentOption(invoiceId, npub, [rail: 'SEPA'])
        }

        [
                provider   : provider.code,
                externalId : rail.externalId,
                status     : rail.status,
                checkoutUrl: rail.checkoutUrl,
                amountEur  : rail.amountEur,
                amountSats : rail.amountSats,
                payMethods : rail.payMethods.collect { PayMethod m ->
                    [
                            rail       : m.rail,
                            uri        : m.uri,
                            checkoutUrl: m.checkoutUrl,
                            label      : m.label,
                            expiresAt  : m.expiresAt
                    ]
                },
                invoiceId  : invoiceId
        ]
    }

    @Transactional
    Map reconcileExternal(String npubOwner, String externalId, Long steakosInvoiceId = null) {
        String npub = requireNpub(npubOwner)
        BitcoinPaymentProvider provider = paymentProviderService.providerFor(npub)
        InboundStatus st = provider.fetchInbound(externalId)
        Long invId = steakosInvoiceId ?: findInvoiceIdByExternal(npub, externalId)
        if (!invId) {
            return [ok: false, settled: false, status: st.status, externalId: externalId, error: 'no steakOS invoice linked']
        }
        if (st.status != 'SETTLED') {
            return [ok: true, settled: false, status: st.status, externalId: externalId, invoiceId: invId]
        }
        WebhookEvent event = new WebhookEvent(
                eventId           : "reconcile-${externalId}-SETTLED",
                type              : 'ReconcileSettled',
                externalInvoiceId : externalId,
                steakosInvoiceId  : invId,
                rail              : 'BTC_LIGHTNING',
                paidEur           : st.paidEur,
                paidSats          : st.paidSats,
                rawStatus         : 'SETTLED',
                receivedAt        : new Date()
        )
        settleInboundEvent(npub, event)
    }

    private Long findInvoiceIdByExternal(String npub, String externalId) {
        if (!externalId) return null
        PaymentOption opt = PaymentOption.findByNpubOwnerAndExternalInvoiceId(npub, externalId)
        if (!opt) return null
        return opt.invoice?.id
    }

    private static String requireNpub(String npubOwner) {
        if (!npubOwner?.trim()) throw new IllegalArgumentException('npubOwner is required')
        npubOwner.trim()
    }
}
