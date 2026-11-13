// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import spock.lang.Specification

class SecretsStartupGuardSpec extends Specification {

    def "production rejects missing and known-weak secrets"() {
        expect:
        ProductionSecrets.problems(null, null, null, null).size() == 4
        ProductionSecrets.problems('steakos-dev-only-change-me!!', 'a1b2c3d4e5f60718', 'steakos',
                'dev-remember-me-key-not-for-production-use!!')
        !ProductionSecrets.problems(good, goodSalt, good, good).isEmpty() == false
    }

    def "messages do not echo the secret"() {
        when:
        String text = ProductionSecrets.problems('steakos-dev-only-change-me!!', 'aa', 'steakos', 'short').join(' ')

        then:
        !text.contains('steakos-dev-only')
        !text.contains('a1b2c3d4')
        text.contains('STEAKOS_FIELD_ENCRYPTION_PASSWORD')
        text.contains('STEAKOS_DB_PASSWORD')
        text.contains('STEAKOS_REMEMBER_ME_KEY')
    }

    def "a long unique salt and secrets pass"() {
        expect:
        ProductionSecrets.problems(good, goodSalt, good, good).isEmpty()
    }

    private static String good = '0123456789abcdef0123456789abcdef'
    private static String goodSalt = '0123456789abcdef0123456789abcdef'
}
