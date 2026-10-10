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

class MockBitcoinPaymentProviderSpec extends Specification {

    MockBitcoinPaymentProvider mock

    def setup() {
        mock = new MockBitcoinPaymentProvider()
    }

    def "createInbound returns LN and on-chain mock methods"() {
        when:
        InboundInvoice inv = mock.createInbound(new InboundCreate(
                npubOwner: 'npub-p',
                steakosInvoiceId: 42L,
                amountEur: 25G
        ))

        then:
        inv.externalId.startsWith('mock-inv-')
        inv.status == 'NEW'
        inv.amountSats > 0
        inv.payMethods.size() == 2
        inv.payMethods*.rail.containsAll(['BTC_LIGHTNING', 'BTC_ONCHAIN'])
        mock.listPayMethods(inv.externalId).size() == 2
    }

    def "markSettled and fetchInbound report SETTLED"() {
        given:
        InboundInvoice inv = mock.createInbound(new InboundCreate(
                steakosInvoiceId: 1L, amountEur: 10G, npubOwner: 'n'
        ))

        when:
        mock.markSettled(inv.externalId)
        InboundStatus st = mock.fetchInbound(inv.externalId)

        then:
        st.status == 'SETTLED'
        st.paidEur == 10G
    }

    def "parseWebhook after create settles and carries steakosInvoiceId"() {
        given:
        InboundInvoice inv = mock.createInbound(new InboundCreate(
                steakosInvoiceId: 7L, amountEur: 5G, npubOwner: 'n'
        ))
        String json = """{"externalId":"${inv.externalId}","eventId":"evt-1","rail":"BTC_LIGHTNING"}"""

        when:
        WebhookEvent ev = mock.parseWebhook(json.bytes)

        then:
        ev.eventId == 'evt-1'
        ev.steakosInvoiceId == 7L
        ev.externalInvoiceId == inv.externalId
        ev.paidEur == 5G
        mock.fetchInbound(inv.externalId).status == 'SETTLED'
    }

    def "createOutbound settles immediately with proof"() {
        when:
        OutboundPayment op = mock.createOutbound(new OutboundCreate(
                npubOwner: 'n',
                purpose: 'VENDOR',
                destination: 'lnbc_mock_vendor',
                amountEur: 3G,
                amountSats: 300_000L
        ))

        then:
        op.status == 'SETTLED'
        op.proof
        mock.fetchOutbound(op.externalId).status == 'SETTLED'
    }

    def "createInbound requires steakosInvoiceId"() {
        when:
        mock.createInbound(new InboundCreate(amountEur: 1G))

        then:
        thrown(PaymentProviderException)
    }
}
