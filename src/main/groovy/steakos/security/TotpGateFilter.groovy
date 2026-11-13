// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import steakos.User
import steakos.configuration.CompanyConfigService

/**
 * After password login, block API/app until TOTP verified when user.totpEnabled.
 * Session key {@link #SESSION_TOTP_OK}.
 * Admin bypass: CompanySetting SYSTEM / security.adminTotpBypass (testing only).
 */
@Slf4j
@CompileStatic
class TotpGateFilter extends OncePerRequestFilter {

    static final String SESSION_TOTP_OK = 'STEAKOS_TOTP_OK'
    static final String SESSION_TOTP_AT = 'STEAKOS_TOTP_AT'
    static final String SESSION_AUTH_VIA_NOSTR = 'STEAKOS_AUTH_NOSTR'

    AuthAccountService authAccountService
    CompanyConfigService companyConfigService

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.requestURI ?: ''
        if (isExempt(path)) {
            filterChain.doFilter(request, response)
            return
        }
        Authentication auth = SecurityContextHolder.context?.authentication
        if (auth == null || !auth.authenticated || auth.principal == null || auth.principal == 'anonymousUser') {
            filterChain.doFilter(request, response)
            return
        }
        HttpSession session = request.getSession(false)
        if (session?.getAttribute(SESSION_TOTP_OK) == Boolean.TRUE) {
            filterChain.doFilter(request, response)
            return
        }
        String username = auth.name
        User user = authAccountService?.findByUsername(username)
        if (user == null || !user.totpEnabled) {
            // no TOTP required
            session = request.getSession(true)
            session.setAttribute(SESSION_TOTP_OK, Boolean.TRUE)
            filterChain.doFilter(request, response)
            return
        }
        if (adminTotpBypass(auth)) {
            session = request.getSession(true)
            session.setAttribute(SESSION_TOTP_OK, Boolean.TRUE)
            filterChain.doFilter(request, response)
            return
        }
        // TOTP required
        if (path.startsWith('/api/')) {
            response.status = 403
            response.contentType = 'application/json'
            response.writer.write('{"error":"Authenticator code required","code":"TOTP_REQUIRED"}')
            return
        }
        // HTML / SPA: send to totp step
        String ctx = request.contextPath ?: ''
        response.sendRedirect(ctx + '/totp')
    }

    private boolean adminTotpBypass(Authentication auth) {
        try {
            if (ProductionSecrets.productionFlag()) {
                return false
            }
            if (companyConfigService == null || !companyConfigService.isAdminTotpBypassEnabled()) {
                return false
            }
            Collection<? extends GrantedAuthority> authorities = auth?.authorities
            return authorities?.any { it?.authority == 'ROLE_ADMIN' }
        } catch (Exception e) {
            log.debug('adminTotpBypass check failed: {}', e.message)
            return false
        }
    }

    private static boolean isExempt(String path) {
        path.startsWith('/login') ||
                path.startsWith('/logout') ||
                path.startsWith('/assets') ||
                path.startsWith('/.well-known') ||
                path.startsWith('/tour') ||
                path == '/totp' || path.startsWith('/totp/') ||
                // Config wizard must finish after Admin/CEO enrol TOTP mid-session
                path.startsWith('/setup') ||
                path.startsWith('/api/setup') ||
                // Shell must learn role / totpRequired even before TOTP OK
                path == '/api/me' || path.startsWith('/api/me/') ||
                path.startsWith('/api/auth/totp') ||
                path.startsWith('/api/auth/nostr') ||
                path.startsWith('/api/auth/users') ||
                path.startsWith('/api/locales') ||
                path.startsWith('/api/version') ||
                path == '/api/health' || path.startsWith('/api/health/') ||
                path == '/api/ops/metrics' ||
                path.startsWith('/api/ops/dependencies') ||

                path.endsWith('.js') || path.endsWith('.css') || path.endsWith('.map') ||
                path.endsWith('.ico') || path.endsWith('.png') || path.endsWith('.svg') ||
                path.endsWith('.jpg') || path.endsWith('.jpeg') || path.endsWith('.webp') ||
                path.endsWith('.woff') || path.endsWith('.woff2')
    }
}
