// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

class MutatingRequestGuardFilterSpec extends Specification {

    def "cross-site API post is rejected"() {
        given:
        def filter = new MutatingRequestGuardFilter()
        def request = new MockHttpServletRequest('POST', '/api/setup/accounts')
        request.serverName = 'localhost'
        request.addHeader('Origin', 'http://evil.test')
        request.addHeader('Sec-Fetch-Site', 'cross-site')
        request.addHeader(MutatingRequestGuardFilter.REQUEST_HEADER, '1')
        def response = new MockHttpServletResponse()
        def chain = Mock(FilterChain)

        when:
        filter.doFilter(request, response, chain)

        then:
        response.status == 403
        response.contentAsString.contains('CROSS_SITE_REJECTED')
        0 * chain.doFilter(_, _)
    }

    def "same-origin API post with the header continues"() {
        given:
        def filter = new MutatingRequestGuardFilter()
        def request = new MockHttpServletRequest('POST', '/api/setup/accounts')
        request.serverName = 'localhost'
        request.addHeader('Origin', 'http://localhost:9090')
        request.addHeader('Sec-Fetch-Site', 'same-origin')
        request.addHeader(MutatingRequestGuardFilter.REQUEST_HEADER, '1')
        def response = new MockHttpServletResponse()
        def chain = Mock(FilterChain)

        when:
        filter.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
    }

    def "API post without the header is rejected"() {
        given:
        def filter = new MutatingRequestGuardFilter()
        def request = new MockHttpServletRequest('POST', '/api/invoices')
        request.serverName = 'localhost'
        request.addHeader('Origin', 'http://localhost:9090')
        def response = new MockHttpServletResponse()
        def chain = Mock(FilterChain)

        when:
        filter.doFilter(request, response, chain)

        then:
        response.status == 403
        0 * chain.doFilter(_, _)
    }

    def "bearer calls skip the header"() {
        given:
        def filter = new MutatingRequestGuardFilter()
        def request = new MockHttpServletRequest('POST', '/api/agent/proposals')
        request.addHeader('Authorization', 'Bearer abc')
        def response = new MockHttpServletResponse()
        def chain = Mock(FilterChain)

        when:
        filter.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
    }
}
