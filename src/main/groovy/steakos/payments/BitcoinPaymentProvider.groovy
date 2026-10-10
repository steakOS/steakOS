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
 * Pluggable Bitcoin / Lightning payment rail (ADR-009, OI-F27).
 * Implementations: Mock (Phase 0), BTCPay Greenfield (Phase 1), later NWC/LND.
 */
interface BitcoinPaymentProvider {

    /** MOCK | BTCPAY | … */
    String getCode()

    InboundInvoice createInbound(InboundCreate cmd)

    InboundStatus fetchInbound(String externalId)

    List<PayMethod> listPayMethods(String externalId)

    OutboundPayment createOutbound(OutboundCreate cmd)

    OutboundStatus fetchOutbound(String externalId)

    /**
     * @return true if signature / authenticity checks pass (Mock always true for test payloads)
     */
    boolean verifyWebhook(byte[] rawBody, Map headers)

    WebhookEvent parseWebhook(byte[] rawBody)
}
