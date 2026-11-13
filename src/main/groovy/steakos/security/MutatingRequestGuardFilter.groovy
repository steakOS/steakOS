// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import groovy.transform.CompileStatic
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

import java.net.URI

/**
 * Rejects cross-site mutations. Cookie-authenticated /api calls must send X-Steakos-Request: 1.
 * Bearer calls and payment webhooks are exempt. A browser form cannot set either of those.
 */
@CompileStatic
class MutatingRequestGuardFilter extends OncePerRequestFilter {

    static final String REQUEST_HEADER = 'X-Steakos-Request'

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = (request.method ?: 'GET').toUpperCase()
        if (method == 'GET' || method == 'HEAD' || method == 'OPTIONS') {
            filterChain.doFilter(request, response)
            return
        }

        String path = stripContext(request)
        if (path.contains('/webhook/')) {
            filterChain.doFilter(request, response)
            return
        }

        String authorization = request.getHeader('Authorization') ?: ''
        if (authorization.startsWith('Bearer ')) {
            filterChain.doFilter(request, response)
            return
        }

        String fetchSite = request.getHeader('Sec-Fetch-Site') ?: ''
        if (fetchSite.equalsIgnoreCase('cross-site')) {
            reject(response)
            return
        }

        String origin = request.getHeader('Origin')
        if (origin && !originAllowed(request, origin)) {
            reject(response)
            return
        }

        if (path.startsWith('/api/')) {
            String mark = request.getHeader(REQUEST_HEADER) ?: ''
            if (mark != '1') {
                reject(response)
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    private static String stripContext(HttpServletRequest request) {
        String path = request.requestURI ?: ''
        String ctx = request.contextPath ?: ''
        if (ctx && path.startsWith(ctx)) {
            return path.substring(ctx.length())
        }
        return path
    }

    private static boolean originAllowed(HttpServletRequest request, String origin) {
        try {
            String host = new URI(origin).host?.toLowerCase()
            if (!host) {
                return false
            }
            String server = request.serverName?.toLowerCase()
            if (server && host == server) {
                return true
            }
            if (host == 'localhost' || host == '127.0.0.1' || host == '::1') {
                return true
            }
            String frontend = System.getenv('STEAKOS_FRONTEND_URL') ?: ''
            if (frontend) {
                String frontHost = new URI(frontend).host?.toLowerCase()
                if (frontHost && frontHost == host) {
                    return true
                }
            }
            return false
        } catch (Exception ignored) {
            return false
        }
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.status = 403
        response.contentType = 'application/json;charset=UTF-8'
        response.writer.write('{"error":"Forbidden","code":"CROSS_SITE_REJECTED"}')
    }
}
