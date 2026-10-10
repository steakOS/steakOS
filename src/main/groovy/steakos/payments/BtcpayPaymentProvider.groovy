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

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

/**
 * BTCPay Server Greenfield implementation of {@link BitcoinPaymentProvider} (Phase 1).
 */
@Slf4j
class BtcpayPaymentProvider implements BitcoinPaymentProvider {

    static final String CODE = 'BTCPAY'

    final BtcpayGreenfieldClient client
    final String webhookSecret
    final String baseUrl

    BtcpayPaymentProvider(BtcpayGreenfieldClient client, String webhookSecret = null, String baseUrl = null) {
        this.client = client
        this.webhookSecret = webhookSecret
        this.baseUrl = baseUrl ?: client?.baseUrl
    }

    @Override
    String getCode() { CODE }

    @Override
    InboundInvoice createInbound(InboundCreate cmd) {
        if (!cmd?.steakosInvoiceId) {
            throw new PaymentProviderException('steakosInvoiceId required', 'VALIDATION')
        }
        Map metadata = (cmd.metadata ?: [:]) + [
                orderId          : "steakos-${cmd.steakosInvoiceId}",
                steakosInvoiceId : cmd.steakosInvoiceId.toString(),
                npubOwner        : cmd.npubOwner
        ]
        Map body = [
                amount  : cmd.amountEur != null ? cmd.amountEur.toPlainString() : null,
                currency: cmd.currency ?: 'EUR',
                metadata: metadata
        ]
        if (cmd.description) {
            body.checkout = [description: cmd.description]
        }
        if (cmd.redirectUrl) {
            body.checkout = (body.checkout ?: [:]) + [redirectURL: cmd.redirectUrl]
        }
        // Remove null amount if sats-only later; Greenfield wants amount for fiat
        if (body.amount == null) {
            throw new PaymentProviderException('amountEur required for BTCPay invoice', 'VALIDATION')
        }

        Map created = client.createInvoice(body)
        String id = created.id?.toString()
        if (!id) {
            throw new PaymentProviderException('BTCPay create invoice returned no id', 'BTCPAY_BAD_RESPONSE')
        }

        List methods = []
        try {
            methods = client.getPaymentMethods(id)
        } catch (Exception e) {
            log.warn('payment-methods fetch failed for {}: {}', id, e.message)
        }

        String checkout = created.checkoutLink?.toString()
        if (!checkout && baseUrl) {
            checkout = "${baseUrl}/i/${id}"
        }

        InboundInvoice inv = mapInvoice(created, methods, checkout)
        inv.metadata = metadata
        return inv
    }

    @Override
    InboundStatus fetchInbound(String externalId) {
        Map inv = client.getInvoice(externalId)
        String status = mapBtcpayStatus(inv.status?.toString())
        BigDecimal paidEur = null
        Long paidSats = null
        if (status == 'SETTLED') {
            paidEur = toBd(inv.amount)
            // additionalData may hold sats; best-effort
            if (inv.additionalData instanceof Map && inv.additionalData.satsPaid != null) {
                paidSats = inv.additionalData.satsPaid as Long
            }
        }
        return new InboundStatus(
                externalId: externalId,
                status    : status,
                paidEur   : paidEur,
                paidSats  : paidSats,
                rawStatus : inv.status?.toString(),
                extra     : [checkoutLink: inv.checkoutLink, storeId: inv.storeId]
        )
    }

    @Override
    List<PayMethod> listPayMethods(String externalId) {
        List raw = client.getPaymentMethods(externalId)
        return mapPayMethods(raw, null)
    }

    @Override
    OutboundPayment createOutbound(OutboundCreate cmd) {
        // Phase 2 — Greenfield payouts / pull payments
        throw new PaymentProviderException(
                'BTCPay outbound (payouts) not implemented yet — Phase 2',
                'NOT_IMPLEMENTED',
                501
        )
    }

    @Override
    OutboundStatus fetchOutbound(String externalId) {
        throw new PaymentProviderException(
                'BTCPay outbound fetch not implemented yet — Phase 2',
                'NOT_IMPLEMENTED',
                501
        )
    }

    /**
     * BTCPay-SIG: sha256=&lt;hmac hex of raw body with webhook secret&gt;
     * If no secret configured, returns true and relies on re-fetch (dev only).
     */
    @Override
    boolean verifyWebhook(byte[] rawBody, Map headers) {
        if (!webhookSecret?.trim()) {
            log.warn('BTCPay webhook secret not configured — skipping HMAC (re-fetch still required)')
            return true
        }
        String sig = headerIgnoreCase(headers, 'BTCPAY-SIG') ?: headerIgnoreCase(headers, 'btcpay-sig')
        if (!sig?.trim()) {
            log.warn('Missing BTCPAY-SIG header')
            return false
        }
        String expected = 'sha256=' + hmacSha256Hex(webhookSecret, rawBody ?: new byte[0])
        return constantTimeEquals(expected, sig.trim())
    }

    @Override
    WebhookEvent parseWebhook(byte[] rawBody) {
        Map body = [:]
        if (rawBody?.length) {
            body = new JsonSlurper().parseText(new String(rawBody, StandardCharsets.UTF_8)) as Map
        }
        // Greenfield webhook: type, invoiceId, storeId, deliveryId, …
        String invoiceId = (body.invoiceId ?: body.InvoiceId)?.toString()
        String type = (body.type ?: body.Type ?: 'InvoiceUpdated').toString()
        String eventId = (body.deliveryId ?: body.originalDeliveryId ?: body.eventId
                ?: "${type}-${invoiceId}-${body.timestamp ?: System.currentTimeMillis()}").toString()

        Long steakosId = null
        // metadata not always on webhook — settlement re-fetches invoice
        if (body.metadata instanceof Map && body.metadata.steakosInvoiceId) {
            steakosId = body.metadata.steakosInvoiceId.toString().isLong() ?
                    body.metadata.steakosInvoiceId as Long : null
        }

        return new WebhookEvent(
                eventId           : eventId,
                type              : type,
                externalInvoiceId : invoiceId,
                steakosInvoiceId  : steakosId,
                rail              : 'BTC_LIGHTNING',
                paidEur           : null,
                paidSats          : null,
                rawStatus         : type,
                receivedAt        : new Date(),
                raw               : body
        )
    }

    // ----- mapping helpers -----

    static InboundInvoice mapInvoice(Map created, List methods, String checkoutUrl) {
        String status = mapBtcpayStatus(created.status?.toString())
        new InboundInvoice(
                externalId : created.id?.toString(),
                status     : status,
                amountEur  : toBd(created.amount),
                amountSats : null,
                currency   : created.currency?.toString() ?: 'EUR',
                checkoutUrl: checkoutUrl ?: created.checkoutLink?.toString(),
                payMethods : mapPayMethods(methods, checkoutUrl ?: created.checkoutLink?.toString()),
                createdAt  : new Date(),
                expiresAt  : parseIso(created.expirationTime?.toString() ?: created.expiresAt?.toString()),
                metadata   : (created.metadata instanceof Map) ? created.metadata as Map : [:]
        )
    }

    static List<PayMethod> mapPayMethods(List raw, String checkoutUrl) {
        if (!raw) {
            return checkoutUrl ? [
                    new PayMethod(rail: 'BTC_LIGHTNING', checkoutUrl: checkoutUrl, label: 'Checkout', uri: checkoutUrl)
            ] : []
        }
        List<PayMethod> out = []
        raw.each { Object row ->
            if (!(row instanceof Map)) return
            Map m = row as Map
            String pmid = (m.paymentMethodId ?: m.paymentMethod ?: m.id ?: '').toString()
            String rail = mapRail(pmid)
            if (!rail) return
            String dest = (m.destination ?: m.paymentLink ?: m.invoiceBitcoinUrl ?: m.link)?.toString()
            String link = (m.paymentLink ?: checkoutUrl)?.toString()
            out << new PayMethod(
                    rail       : rail,
                    uri        : dest ?: link,
                    checkoutUrl: link ?: checkoutUrl,
                    label      : pmid,
                    expiresAt  : null,
                    extra      : [paymentMethodId: pmid]
            )
        }
        // Prefer one option per rail
        Map byRail = [:]
        out.each { PayMethod p -> if (!byRail[p.rail]) byRail[p.rail] = p }
        return byRail.values() as List
    }

    static String mapRail(String paymentMethodId) {
        if (!paymentMethodId) return null
        String p = paymentMethodId.toLowerCase()
        if (p.contains('lightning') || p.contains('ln') || p.contains('l-btc')) {
            return 'BTC_LIGHTNING'
        }
        if (p.contains('btc') || p.contains('onchain') || p.contains('chain') || p == 'bitcoin') {
            return 'BTC_ONCHAIN'
        }
        return null
    }

    static String mapBtcpayStatus(String s) {
        if (!s) return 'NEW'
        switch (s.toLowerCase()) {
            case 'new': return 'NEW'
            case 'processing':
            case 'paidpartial':
            case 'paidlate': return 'PROCESSING'
            case 'settled':
            case 'complete':
            case 'confirmed':
            case 'paid': return 'SETTLED'
            case 'expired': return 'EXPIRED'
            case 'invalid': return 'INVALID'
            default: return s.toUpperCase()
        }
    }

    static boolean isSettledType(String type) {
        if (!type) return false
        String t = type.toLowerCase()
        t.contains('settled') || t == 'invoicepaymentsettled' || t.contains('invoicesettled')
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') return null
        if (v instanceof BigDecimal) return v
        try {
            return new BigDecimal(v.toString())
        } catch (Exception ignored) {
            return null
        }
    }

    private static Date parseIso(String s) {
        if (!s) return null
        try {
            return Date.from(java.time.Instant.parse(s))
        } catch (Exception ignored) {
            return null
        }
    }

    private static String headerIgnoreCase(Map headers, String name) {
        if (!headers) return null
        def v = headers[name]
        if (v != null) return v.toString()
        def e = headers.find { k, val -> k?.toString()?.equalsIgnoreCase(name) }
        return e?.value?.toString()
    }

    static String hmacSha256Hex(String secret, byte[] body) {
        Mac mac = Mac.getInstance('HmacSHA256')
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), 'HmacSHA256'))
        byte[] raw = mac.doFinal(body ?: new byte[0])
        raw.collect { String.format('%02x', it) }.join('')
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false
        byte[] x = a.getBytes(StandardCharsets.UTF_8)
        byte[] y = b.getBytes(StandardCharsets.UTF_8)
        if (x.length != y.length) return false
        int r = 0
        for (int i = 0; i < x.length; i++) {
            r |= x[i] ^ y[i]
        }
        return r == 0
    }
}
