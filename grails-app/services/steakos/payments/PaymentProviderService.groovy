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

import groovy.util.logging.Slf4j
import jakarta.annotation.PostConstruct

/**
 * Resolves {@link BitcoinPaymentProvider} per tenant (OI-F27 Phase 0 + Phase 1 BTCPay).
 */
@Slf4j
class PaymentProviderService {

    PaymentSettingsService paymentSettingsService

    /** Shared mock instance (in-memory state useful for tests / demo). */
    final MockBitcoinPaymentProvider mockProvider = new MockBitcoinPaymentProvider()

    @PostConstruct
    void init() {
        log.info('PaymentProviderService ready (MOCK + BTCPay Greenfield Phase 1)')
    }

    BitcoinPaymentProvider providerFor(String npubOwner) {
        String code = paymentSettingsService.resolvedProviderCode(npubOwner)
        if (code == PaymentSettingsService.PROVIDER_BTCPAY || code == BtcpayPaymentProvider.CODE) {
            return buildBtcpayProvider(npubOwner)
        }
        return mockProvider
    }

    BtcpayPaymentProvider buildBtcpayProvider(String npubOwner) {
        Map cfg = paymentSettingsService.decryptedBtcpayConfig(npubOwner)
        String base = BtcpayUrlValidator.validateAndNormalize(
                cfg.baseUrl as String,
                paymentSettingsService.internalBtcpayHosts()
        )
        BtcpayGreenfieldClient client = new BtcpayGreenfieldClient(
                base,
                cfg.storeId as String,
                cfg.apiKey as String,
                paymentSettingsService.httpTimeoutMs()
        )
        new BtcpayPaymentProvider(client, cfg.webhookSecret as String, base)
    }

    MockBitcoinPaymentProvider getMock() {
        mockProvider
    }
}
