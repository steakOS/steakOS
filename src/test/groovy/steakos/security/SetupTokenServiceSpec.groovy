// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import spock.lang.Specification

class SetupTokenServiceSpec extends Specification {

    def "missing token is rejected, the right token is accepted, and discard blocks a replay"() {
        given:
        File dir = File.createTempDir('steakos-setup-token')
        SetupTokenService svc = new SetupTokenService(directoryOverride: dir, forceEnforce: true)

        expect:
        !svc.allows('anything')

        when:
        svc.ensureMaterial()
        String token = svc.tokenFile().getText('UTF-8').trim()

        then:
        token.length() == 64
        !svc.allows('wrong-token')
        svc.allows(token)

        when:
        svc.discard()

        then:
        !svc.tokenFile().exists()
        !svc.allows(token)

        cleanup:
        dir.deleteDir()
    }

    def "compare does not treat a different length as equal"() {
        expect:
        !SetupTokenService.constantTimeEquals('abcd', 'abc')
        SetupTokenService.constantTimeEquals('abcd', 'abcd')
    }
}
