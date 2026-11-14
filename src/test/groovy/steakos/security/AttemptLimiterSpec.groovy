// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import spock.lang.Specification

class AttemptLimiterSpec extends Specification {

    def "the attempt that reaches the limit is rejected"() {
        given:
        def limiter = new AttemptLimiter()
        long t = 1_000_000L

        expect:
        (1..5).every { limiter.allow('login:user:ada:1.1.1.1', 5, 60_000L, t) }
        (1..5).each { limiter.record('login:user:ada:1.1.1.1', 60_000L, t) }
        !limiter.allow('login:user:ada:1.1.1.1', 5, 60_000L, t + 1_000L)
    }

    def "the window opens again after it expires"() {
        given:
        def limiter = new AttemptLimiter()
        long t = 1_000_000L
        5.times { limiter.record('totp:ada', 5 * 60_000L, t) }

        expect:
        limiter.allow('totp:ada', 5, 5 * 60_000L, t + (5 * 60_000L) + 1)
    }
}
