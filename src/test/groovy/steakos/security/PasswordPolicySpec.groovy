// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import spock.lang.Specification

class PasswordPolicySpec extends Specification {

    def "dev default meets policy"() {
        expect:
        PasswordPolicy.isValid(PasswordPolicy.DEV_DEFAULT_PASSWORD)
        PasswordPolicy.DEV_DEFAULT_PASSWORD == 'Schn1tz3l3ck3$'
    }

    def "rejects weak passwords"() {
        expect:
        PasswordPolicy.validate(raw) != null

        where:
        raw << [
                null,
                '',
                'short',
                'alllowercase1!',
                'ALLUPPERCASE1!',
                'NoDigitsHere!!',
                'NoSpecialChar1',
                'AlmostThere1', // 12 chars but no special
        ]
    }

    def "accepts strong passwords"() {
        expect:
        PasswordPolicy.isValid(raw)

        where:
        raw << [
                'Schn1tz3l3ck3$',
                'Abcdefghijk1!',
                'Good-Passw0rd',
        ]
    }
}
