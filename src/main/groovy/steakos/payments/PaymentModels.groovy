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

/**
 * DTOs for {@link BitcoinPaymentProvider} (Phase 0+).
 * Maps kept simple for Groovy/Grails JSON and tests.
 */

class InboundCreate {
    String npubOwner
    Long steakosInvoiceId
    BigDecimal amountEur
    Long amountSats
    String currency = 'EUR'
    Integer expirySeconds
    Map metadata = [:]
    String redirectUrl
    String description
}

class PayMethod {
    String rail          // BTC_LIGHTNING | BTC_ONCHAIN | …
    String uri           // bolt11 / bip21
    String checkoutUrl
    String label
    Date expiresAt
    Map extra = [:]
}

class InboundInvoice {
    String externalId
    String status        // NEW | PROCESSING | SETTLED | EXPIRED | INVALID
    BigDecimal amountEur
    Long amountSats
    String currency
    String checkoutUrl
    List<PayMethod> payMethods = []
    Map metadata = [:]
    Date createdAt
    Date expiresAt
}

class InboundStatus {
    String externalId
    String status
    BigDecimal paidEur
    Long paidSats
    String rawStatus
    Map extra = [:]
}

class OutboundCreate {
    String npubOwner
    String purpose       // VENDOR | PAYROLL | OTHER
    String sourceDocType
    Long sourceDocId
    BigDecimal amountEur
    Long amountSats
    String destination   // bolt11, address, or pull descriptor
    String memo
    Map metadata = [:]
}

class OutboundPayment {
    String externalId
    String status        // PENDING | SETTLED | FAILED | CANCELLED
    BigDecimal amountEur
    Long amountSats
    String destination
    String proof         // preimage / txid
    Map extra = [:]
}

class OutboundStatus {
    String externalId
    String status
    String proof
    Map extra = [:]
}

class WebhookEvent {
    String eventId
    String type
    String externalInvoiceId
    Long steakosInvoiceId
    String rail
    BigDecimal paidEur
    Long paidSats
    String rawStatus
    Date receivedAt
    Map raw = [:]
}
