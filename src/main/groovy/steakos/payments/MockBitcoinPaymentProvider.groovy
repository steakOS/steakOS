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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-process rail for Iron demo / CI. No network. Deterministic external ids.
 */
@Slf4j
class MockBitcoinPaymentProvider implements BitcoinPaymentProvider {

    static final String CODE = 'MOCK'

    private final AtomicLong seq = new AtomicLong(1000)
    private final Map<String, InboundInvoice> inbounds = new ConcurrentHashMap<>()
    private final Map<String, OutboundPayment> outbounds = new ConcurrentHashMap<>()

    @Override
    String getCode() { CODE }

    @Override
    InboundInvoice createInbound(InboundCreate cmd) {
        if (!cmd?.steakosInvoiceId) {
            throw new PaymentProviderException('steakosInvoiceId required', 'VALIDATION')
        }
        String id = "mock-inv-${seq.incrementAndGet()}"
        Date now = new Date()
        Date exp = new Date(now.time + (cmd.expirySeconds ?: 3600) * 1000L)
        Long sats = cmd.amountSats
        if (sats == null && cmd.amountEur != null) {
            // Demo rate ~100k sats / EUR (not a real oracle)
            sats = (cmd.amountEur * 100_000G).longValue()
        }
        InboundInvoice inv = new InboundInvoice(
                externalId  : id,
                status      : 'NEW',
                amountEur   : cmd.amountEur,
                amountSats  : sats ?: 0L,
                currency    : cmd.currency ?: 'EUR',
                checkoutUrl : "mock://checkout/${id}",
                createdAt   : now,
                expiresAt   : exp,
                metadata    : (cmd.metadata ?: [:]) + [steakosInvoiceId: cmd.steakosInvoiceId, npubOwner: cmd.npubOwner],
                payMethods  : [
                        new PayMethod(
                                rail       : 'BTC_LIGHTNING',
                                uri        : "lnbc_mock_${id}",
                                checkoutUrl: "mock://checkout/${id}",
                                label      : 'Lightning (mock)',
                                expiresAt  : exp
                        ),
                        new PayMethod(
                                rail       : 'BTC_ONCHAIN',
                                uri        : "bitcoin:bc1qmock${id}?amount=${cmd.amountEur ?: 0}",
                                checkoutUrl: "mock://checkout/${id}",
                                label      : 'On-chain (mock)',
                                expiresAt  : exp
                        )
                ]
        )
        inbounds[id] = inv
        log.debug('Mock inbound created {} for invoice {}', id, cmd.steakosInvoiceId)
        return inv
    }

    @Override
    InboundStatus fetchInbound(String externalId) {
        InboundInvoice inv = inbounds[externalId]
        if (!inv) {
            throw new PaymentProviderException("unknown inbound: ${externalId}", 'NOT_FOUND', 404)
        }
        boolean settled = inv.status == 'SETTLED'
        return new InboundStatus(
                externalId: inv.externalId,
                status    : inv.status,
                paidEur   : settled ? inv.amountEur : null,
                paidSats  : settled ? inv.amountSats : null,
                rawStatus : inv.status
        )
    }

    @Override
    List<PayMethod> listPayMethods(String externalId) {
        InboundInvoice inv = inbounds[externalId]
        if (!inv) {
            throw new PaymentProviderException("unknown inbound: ${externalId}", 'NOT_FOUND', 404)
        }
        return inv.payMethods ?: []
    }

    /**
     * Test helper: mark mock invoice settled (simulates customer pay).
     */
    InboundInvoice markSettled(String externalId) {
        InboundInvoice inv = inbounds[externalId]
        if (!inv) {
            throw new PaymentProviderException("unknown inbound: ${externalId}", 'NOT_FOUND', 404)
        }
        inv.status = 'SETTLED'
        inbounds[externalId] = inv
        return inv
    }

    @Override
    OutboundPayment createOutbound(OutboundCreate cmd) {
        if (!cmd?.destination?.trim()) {
            throw new PaymentProviderException('destination required', 'VALIDATION')
        }
        String id = "mock-out-${seq.incrementAndGet()}"
        OutboundPayment op = new OutboundPayment(
                externalId : id,
                status     : 'SETTLED',
                amountEur  : cmd.amountEur,
                amountSats : cmd.amountSats,
                destination: cmd.destination,
                proof      : "mock-preimage-${id}",
                extra      : [purpose: cmd.purpose, sourceDocType: cmd.sourceDocType, sourceDocId: cmd.sourceDocId]
        )
        outbounds[id] = op
        return op
    }

    @Override
    OutboundStatus fetchOutbound(String externalId) {
        OutboundPayment op = outbounds[externalId]
        if (!op) {
            throw new PaymentProviderException("unknown outbound: ${externalId}", 'NOT_FOUND', 404)
        }
        return new OutboundStatus(externalId: op.externalId, status: op.status, proof: op.proof)
    }

    @Override
    boolean verifyWebhook(byte[] rawBody, Map headers) {
        true
    }

    @Override
    WebhookEvent parseWebhook(byte[] rawBody) {
        Map body = rawBody ? (new JsonSlurper().parseText(new String(rawBody, 'UTF-8')) as Map) : [:]
        String externalId = (body.externalId ?: body.invoiceId)?.toString()
        // Support steakOS-style stub body: invoiceId = steakOS Long, externalId = mock id
        Long steakosId = null
        if (body.steakosInvoiceId != null) {
            steakosId = body.steakosInvoiceId as Long
        } else if (body.invoiceId != null && body.invoiceId.toString().isLong()) {
            // legacy stub used steakOS invoice id as invoiceId
            steakosId = body.invoiceId as Long
        }
        if (externalId && inbounds.containsKey(externalId)) {
            markSettled(externalId)
            InboundInvoice inv = inbounds[externalId]
            if (steakosId == null && inv.metadata?.steakosInvoiceId != null) {
                steakosId = inv.metadata.steakosInvoiceId as Long
            }
            return new WebhookEvent(
                    eventId           : (body.eventId ?: "mock-evt-${externalId}-${System.currentTimeMillis()}").toString(),
                    type              : (body.type ?: 'InvoiceSettled').toString(),
                    externalInvoiceId : externalId,
                    steakosInvoiceId  : steakosId,
                    rail              : (body.rail ?: 'BTC_LIGHTNING').toString(),
                    paidEur           : inv.amountEur,
                    paidSats          : inv.amountSats,
                    rawStatus         : 'SETTLED',
                    receivedAt        : new Date(),
                    raw               : body
            )
        }
        // Legacy path: settle by steakOS invoice id only (no prior createInbound)
        return new WebhookEvent(
                eventId           : (body.eventId ?: body.id ?: "mock-evt-${System.currentTimeMillis()}").toString(),
                type              : (body.type ?: 'InvoiceSettled').toString(),
                externalInvoiceId : externalId,
                steakosInvoiceId  : steakosId,
                rail              : (body.rail ?: 'BTC_LIGHTNING').toString(),
                paidEur           : body.amountEur != null ? body.amountEur as BigDecimal : null,
                paidSats          : body.amountSats != null ? body.amountSats as Long : null,
                rawStatus         : (body.status ?: 'SETTLED').toString(),
                receivedAt        : new Date(),
                raw               : body
        )
    }

    /** Clear in-memory state (tests). */
    void reset() {
        inbounds.clear()
        outbounds.clear()
    }

    static String toJson(Object o) {
        JsonOutput.toJson(o)
    }
}
