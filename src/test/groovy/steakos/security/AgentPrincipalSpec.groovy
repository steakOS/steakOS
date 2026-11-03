// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

class AgentPrincipalSpec extends Specification {

    def "ROLE_AI without admin is agent-only"() {
        given:
        def auth = new UsernamePasswordAuthenticationToken(
                'HiveA:bookkeeper', 'n/a',
                [new SimpleGrantedAuthority('ROLE_AI'), new SimpleGrantedAuthority('ROLE_USER')])

        expect:
        AgentPrincipal.isAgentOnly(auth)
    }

    def "ROLE_AI plus ROLE_ADMIN is not agent-only"() {
        given:
        def auth = new UsernamePasswordAuthenticationToken(
                'admin', 'n/a',
                [new SimpleGrantedAuthority('ROLE_AI'), new SimpleGrantedAuthority('ROLE_ADMIN')])

        expect:
        !AgentPrincipal.isAgentOnly(auth)
    }

    def "human roles are not agent-only"() {
        given:
        def auth = new UsernamePasswordAuthenticationToken(
                'operator', 'n/a',
                [new SimpleGrantedAuthority('ROLE_USER'), new SimpleGrantedAuthority('ROLE_M')])

        expect:
        !AgentPrincipal.isAgentOnly(auth)
    }

    def "unauthenticated is not agent-only"() {
        expect:
        !AgentPrincipal.isAgentOnly(null)
    }
}
