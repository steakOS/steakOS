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

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

/**
 * BTC spot FX for UI hints + payment-time sats estimates (PDR-023).
 * Cascade: CoinGecko → Kraken → Coinbase. Cache TTL ~10s. Rate = quote per 1 BTC.
 */
@Slf4j
@CompileStatic
class BtcFxService {

    static final long CACHE_TTL_MS = 10_000L
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(3)
    static final BigDecimal SATS_PER_BTC = new BigDecimal('100000000')

    /** Optional test hook: (url) -> body string */
    Closure<String> httpGet

    private final ConcurrentHashMap<String, CachedRate> cache = new ConcurrentHashMap<>()
    private final ConcurrentHashMap<String, CachedRate> lastGood = new ConcurrentHashMap<>()
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build()
    private final JsonSlurper slurper = new JsonSlurper()

    /** Convert fiat major units → sats using current (or stale) rate for quote. */
    Long fiatToSats(BigDecimal fiatAmount, String quote) {
        if (fiatAmount == null || fiatAmount <= BigDecimal.ZERO) {
            return null
        }
        Map fx = rate(quote)
        BigDecimal r = fx.rate as BigDecimal
        if (r == null || r <= BigDecimal.ZERO) {
            return null
        }
        return fiatAmount.divide(r, 12, RoundingMode.HALF_UP)
                .multiply(SATS_PER_BTC)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue()
    }

    /** BTC major → sats. */
    static Long btcMajorToSats(BigDecimal btc) {
        if (btc == null || btc <= BigDecimal.ZERO) {
            return null
        }
        return btc.multiply(SATS_PER_BTC).setScale(0, RoundingMode.HALF_UP).longValue()
    }

    /**
     * @param quote EUR or USD
     * @return map rate, quote, asOf (epoch ms), source, stale, ageSeconds
     */
    Map rate(String quote) {
        String q = (quote ?: 'EUR').trim().toUpperCase()
        if (!(q in ['EUR', 'USD'])) {
            throw new IllegalArgumentException('quote must be EUR or USD')
        }
        String key = "BTC/${q}"
        long now = System.currentTimeMillis()
        CachedRate hit = cache.get(key)
        if (hit != null && (now - hit.atMs) < CACHE_TTL_MS) {
            return hit.toMap(now, false)
        }

        List<Map> attempts = [
                [name: 'COINGECKO', call: { fetchCoinGecko(q) }],
                [name: 'KRAKEN', call: { fetchKraken(q) }],
                [name: 'COINBASE', call: { fetchCoinbase(q) }]
        ] as List<Map>

        Exception lastErr = null
        for (Map a : attempts) {
            try {
                BigDecimal rate = (a.call as Closure).call() as BigDecimal
                if (rate == null || rate <= BigDecimal.ZERO) {
                    throw new IllegalStateException("${a.name} returned empty rate")
                }
                CachedRate fresh = new CachedRate(rate, q, a.name as String, now)
                cache.put(key, fresh)
                lastGood.put(key, fresh)
                return fresh.toMap(now, false)
            } catch (Exception e) {
                lastErr = e
                log.warn('BTC FX {} failed for {}: {}', a.name, q, e.message)
            }
        }

        CachedRate stale = lastGood.get(key)
        if (stale != null) {
            log.warn('BTC FX all sources failed for {}; returning stale from {}', q, stale.source)
            return stale.toMap(now, true)
        }
        throw new IllegalStateException(
                "BTC FX unavailable for ${q}" + (lastErr ? ": ${lastErr.message}" : ''))
    }

    private BigDecimal fetchCoinGecko(String quote) {
        String vs = quote.toLowerCase()
        String body = get("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=${vs}")
        Map root = slurper.parseText(body) as Map
        Map btc = root.bitcoin as Map
        if (!btc?.containsKey(vs)) {
            throw new IllegalStateException('CoinGecko missing bitcoin.' + vs)
        }
        return toBd(btc[vs])
    }

    private BigDecimal fetchKraken(String quote) {
        String pair = quote == 'USD' ? 'XXBTZUSD' : 'XXBTZEUR'
        String body = get("https://api.kraken.com/0/public/Ticker?pair=${pair}")
        Map root = slurper.parseText(body) as Map
        List errors = root.error as List
        if (errors) {
            throw new IllegalStateException('Kraken error: ' + errors)
        }
        Map result = root.result as Map
        Map ticker = result?.values()?.iterator()?.next() as Map
        List c = ticker?.c as List // last trade closed [price, lot]
        if (!c || !c[0]) {
            throw new IllegalStateException('Kraken missing close price')
        }
        return toBd(c[0])
    }

    private BigDecimal fetchCoinbase(String quote) {
        String body = get("https://api.coinbase.com/v2/prices/BTC-${quote}/spot")
        Map root = slurper.parseText(body) as Map
        Map data = root.data as Map
        if (!data?.amount) {
            throw new IllegalStateException('Coinbase missing amount')
        }
        return toBd(data.amount)
    }

    private String get(String url) {
        if (httpGet != null) {
            return httpGet.call(url) as String
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header('Accept', 'application/json')
                .header('User-Agent', 'steakOS-BtcFx/0.1')
                .GET()
                .build()
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("HTTP ${res.statusCode()} for ${url}")
        }
        return res.body()
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return null
        if (o instanceof BigDecimal) return (BigDecimal) o
        return new BigDecimal(o.toString())
    }

    @CompileStatic
    static class CachedRate {
        final BigDecimal rate
        final String quote
        final String source
        final long atMs

        CachedRate(BigDecimal rate, String quote, String source, long atMs) {
            this.rate = rate
            this.quote = quote
            this.source = source
            this.atMs = atMs
        }

        Map toMap(long nowMs, boolean stale) {
            [
                    rate       : rate,
                    quote      : quote,
                    asOf       : atMs,
                    source     : source,
                    stale      : stale,
                    ageSeconds : Math.max(0L, (nowMs - atMs) / 1000L) as long
            ]
        }
    }
}
