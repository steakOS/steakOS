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

import spock.lang.Specification

class BtcpayPaymentProviderSpec extends Specification {

    def "mapBtcpayStatus normalizes settled variants"() {
        expect:
        BtcpayPaymentProvider.mapBtcpayStatus('Settled') == 'SETTLED'
        BtcpayPaymentProvider.mapBtcpayStatus('New') == 'NEW'
        BtcpayPaymentProvider.mapBtcpayStatus('Expired') == 'EXPIRED'
        BtcpayPaymentProvider.mapBtcpayStatus('Processing') == 'PROCESSING'
    }

    def "mapRail detects lightning and on-chain"() {
        expect:
        BtcpayPaymentProvider.mapRail('BTC-LightningNetwork') == 'BTC_LIGHTNING'
        BtcpayPaymentProvider.mapRail('BTC-CHAIN') == 'BTC_ONCHAIN'
        BtcpayPaymentProvider.mapRail('BTC') == 'BTC_ONCHAIN'
    }

    def "mapPayMethods keeps one per rail"() {
        given:
        List raw = [
                [paymentMethodId: 'BTC-LightningNetwork', destination: 'lnbc1', paymentLink: 'https://x/i/1'],
                [paymentMethodId: 'BTC-LightningNetwork', destination: 'lnbc2', paymentLink: 'https://x/i/1'],
                [paymentMethodId: 'BTC-CHAIN', destination: 'bitcoin:bc1q', paymentLink: 'https://x/i/1'],
        ]

        when:
        List<PayMethod> methods = BtcpayPaymentProvider.mapPayMethods(raw, 'https://x/i/1')

        then:
        methods.size() == 2
        methods*.rail.containsAll(['BTC_LIGHTNING', 'BTC_ONCHAIN'])
    }

    def "HMAC verify accepts matching BTCPAY-SIG"() {
        given:
        String secret = 'whsec-test'
        byte[] body = '{"type":"InvoiceSettled","invoiceId":"abc"}'.bytes
        String sig = 'sha256=' + BtcpayPaymentProvider.hmacSha256Hex(secret, body)
        BtcpayGreenfieldClient client = Stub(BtcpayGreenfieldClient)
        BtcpayPaymentProvider provider = new BtcpayPaymentProvider(client, secret, 'https://btcpay.example')

        expect:
        provider.verifyWebhook(body, ['BTCPAY-SIG': sig])
        !provider.verifyWebhook(body, ['BTCPAY-SIG': 'sha256=deadbeef'])
    }

    def "parseWebhook extracts invoiceId and deliveryId"() {
        given:
        BtcpayPaymentProvider provider = new BtcpayPaymentProvider(Stub(BtcpayGreenfieldClient), null, null)
        byte[] body = '''{"type":"InvoiceSettled","invoiceId":"inv-99","deliveryId":"del-1","storeId":"s1"}'''.bytes

        when:
        WebhookEvent ev = provider.parseWebhook(body)

        then:
        ev.externalInvoiceId == 'inv-99'
        ev.eventId == 'del-1'
        ev.type == 'InvoiceSettled'
    }

    def "createInbound maps client response"() {
        given:
        BtcpayGreenfieldClient client = Stub(BtcpayGreenfieldClient) {
            createInvoice(_) >> [
                    id          : 'ext-1',
                    status      : 'New',
                    amount      : '42.5',
                    currency    : 'EUR',
                    checkoutLink: 'https://btcpay.example/i/ext-1',
                    metadata    : [steakosInvoiceId: '9']
            ]
            getPaymentMethods('ext-1') >> [
                    [paymentMethodId: 'BTC-LightningNetwork', destination: 'lnbc_x', paymentLink: 'https://btcpay.example/i/ext-1']
            ]
        }
        BtcpayPaymentProvider provider = new BtcpayPaymentProvider(client, null, 'https://btcpay.example')

        when:
        InboundInvoice inv = provider.createInbound(new InboundCreate(
                steakosInvoiceId: 9L,
                amountEur: 42.5G,
                npubOwner: 'npub-x'
        ))

        then:
        inv.externalId == 'ext-1'
        inv.status == 'NEW'
        inv.checkoutUrl.contains('ext-1')
        inv.payMethods.any { it.rail == 'BTC_LIGHTNING' && it.uri == 'lnbc_x' }
    }

    def "fetchInbound SETTLED maps amount"() {
        given:
        BtcpayGreenfieldClient client = Stub(BtcpayGreenfieldClient) {
            getInvoice('ext-2') >> [id: 'ext-2', status: 'Settled', amount: '10']
        }
        BtcpayPaymentProvider provider = new BtcpayPaymentProvider(client, null, null)

        when:
        InboundStatus st = provider.fetchInbound('ext-2')

        then:
        st.status == 'SETTLED'
        st.paidEur == 10G
    }
}
