// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification
import steakos.User

class MatrixAccessFilterSpec extends Specification {

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "a signed-in user the filter cannot resolve is rejected"() {
        given:
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                'admin', null, [new SimpleGrantedAuthority('ROLE_ADMIN')])
        def filter = new MatrixAccessFilter()
        filter.authAccountService = Stub(AuthAccountService) {
            findByUsername(_) >> null
        }
        def request = new MockHttpServletRequest('POST', '/api/setup/reset')
        def response = new MockHttpServletResponse()
        def chain = Mock(FilterChain)

        when:
        filter.doFilter(request, response, chain)

        then:
        response.status == 403
        response.contentAsString.contains('MATRIX_USER_UNKNOWN')
        0 * chain.doFilter(_, _)
    }

    def "a permission lookup failure does not let a mutation through"() {
        given:
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                'admin', null, [new SimpleGrantedAuthority('ROLE_ADMIN')])
        def filter = new MatrixAccessFilter()
        filter.authAccountService = Stub(AuthAccountService) {
            findByUsername(_) >> new User(username: 'admin', npub: 'npub1example')
        }
        filter.companyConfigService = Stub(steakos.configuration.CompanyConfigService) {
            enabledFamilies(_) >> ['sales']
            getString(_, _, _) >> null
        }
        filter.roleProfileService = Stub(RoleProfileService) {
            effectivePermissions(_, _, _) >> { throw new IllegalStateException('boom-secret') }
        }
        def request = new MockHttpServletRequest('POST', '/api/setup/reset')
        def response = new MockHttpServletResponse()
        def chain = Mock(FilterChain)

        when:
        filter.doFilter(request, response, chain)

        then:
        response.status == 503
        response.contentAsString.contains('AUTHZ_UNAVAILABLE')
        !response.contentAsString.contains('boom-secret')
        0 * chain.doFilter(_, _)
    }
}
