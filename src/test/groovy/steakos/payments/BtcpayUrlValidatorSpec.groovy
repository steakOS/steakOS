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

class BtcpayUrlValidatorSpec extends Specification {

    def setup() {
        BtcpayUrlValidator.addressResolver = { String host ->
            [InetAddress.getByAddress(host, [1, 2, 3, 4] as byte[])]
        }
    }

    def "normalizes https production URL"() {
        expect:
        BtcpayUrlValidator.validateAndNormalize('https://pay.example.com/btcpay/') == 'https://pay.example.com/btcpay'
    }

    def "blocks private hosts when not on the allowlist"() {
        when:
        BtcpayUrlValidator.validateAndNormalize('https://127.0.0.1:49392')

        then:
        def e = thrown(PaymentProviderException)
        e.code == 'SSRF'
    }

    def "allows a named internal host"() {
        expect:
        BtcpayUrlValidator.validateAndNormalize('http://btcpay.internal:23000', ['btcpay.internal']) ==
                'http://btcpay.internal:23000'
    }

    def "rejects a name that resolves to a private address"() {
        given:
        BtcpayUrlValidator.addressResolver = { String host ->
            [InetAddress.getByAddress(host, [10, 0, 0, 5] as byte[])]
        }

        when:
        BtcpayUrlValidator.validateAndNormalize('https://pay.internal')

        then:
        def e = thrown(PaymentProviderException)
        e.code == 'SSRF'
    }

    def "rejects non-http schemes"() {
        when:
        BtcpayUrlValidator.validateAndNormalize('ftp://pay.example.com')

        then:
        thrown(PaymentProviderException)
    }
}
