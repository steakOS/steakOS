// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import groovy.transform.CompileStatic
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Enforces the safe headers. Content-Security-Policy is report-only so the
 * current pages keep rendering while violations are visible.
 */
@CompileStatic
class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob:; connect-src 'self'; frame-ancestors 'none'; " +
            "base-uri 'self'; form-action 'self'; object-src 'none'"

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader('Content-Security-Policy-Report-Only', CSP)
        response.setHeader('X-Content-Type-Options', 'nosniff')
        response.setHeader('X-Frame-Options', 'DENY')
        response.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin')
        response.setHeader('Permissions-Policy', 'geolocation=(), microphone=(), camera=()')
        response.setHeader('Cross-Origin-Opener-Policy', 'same-origin')
        if (request.isSecure()) {
            response.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains')
        }
        String path = request.requestURI ?: ''
        if (path.startsWith('/api/')) {
            response.setHeader('Cache-Control', 'no-store')
        }
        filterChain.doFilter(request, response)
    }
}
