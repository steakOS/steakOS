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

import spock.lang.Specification

class BtcFxServiceSpec extends Specification {

    def "cascade uses second source when first fails"() {
        given:
        def svc = new BtcFxService()
        int calls = 0
        svc.httpGet = { String url ->
            calls++
            if (url.contains('coingecko')) {
                throw new RuntimeException('down')
            }
            if (url.contains('kraken')) {
                return '{"error":[],"result":{"XXBTZEUR":{"c":["95000.5","1"]}}}'
            }
            throw new RuntimeException('should not reach coinbase')
        }

        when:
        def m = svc.rate('EUR')

        then:
        m.rate == 95000.5G
        m.source == 'KRAKEN'
        m.stale == false
        m.quote == 'EUR'
        calls >= 2
    }

    def "cache returns within TTL without second upstream"() {
        given:
        def svc = new BtcFxService()
        int calls = 0
        svc.httpGet = { String url ->
            calls++
            return '{"bitcoin":{"eur":100000}}'
        }

        when:
        def a = svc.rate('EUR')
        def b = svc.rate('EUR')

        then:
        a.rate == 100000G
        b.rate == 100000G
        a.source == 'COINGECKO'
        calls == 1
    }

    def "stale last-good when all fail after success"() {
        given:
        def svc = new BtcFxService()
        int calls = 0
        svc.httpGet = { String url ->
            calls++
            if (calls == 1) {
                return '{"bitcoin":{"usd":80000}}'
            }
            throw new RuntimeException('all down')
        }

        when:
        def ok = svc.rate('USD')
        // expire cache
        svc.cache.clear()
        def stale = svc.rate('USD')

        then:
        ok.stale == false
        ok.rate == 80000G
        stale.stale == true
        stale.rate == 80000G
        stale.source == 'COINGECKO'
    }

    def "rejects bad quote"() {
        when:
        new BtcFxService().rate('GBP')

        then:
        thrown(IllegalArgumentException)
    }

    def "fiatToSats uses rate"() {
        given:
        def svc = new BtcFxService()
        svc.httpGet = { String url -> '{"bitcoin":{"eur":50000}}' }

        expect:
        svc.fiatToSats(100.0G, 'EUR') == 200_000L
        BtcFxService.btcMajorToSats(0.5G) == 50_000_000L
    }
}
