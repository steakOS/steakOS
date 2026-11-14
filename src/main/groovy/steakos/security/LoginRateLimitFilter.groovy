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
 * Counts failed password posts to /login/authenticate.
 * The 6th attempt in the window is rejected before the password is checked.
 */
@CompileStatic
class LoginRateLimitFilter extends OncePerRequestFilter {

    AttemptLimiter attemptLimiter = new AttemptLimiter()

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.requestURI ?: ''
        String ctx = request.contextPath ?: ''
        if (ctx && path.startsWith(ctx)) path = path.substring(ctx.length())
        if (request.method != 'POST' || !(path == '/login/authenticate' || path.endsWith('/login/authenticate'))) {
            filterChain.doFilter(request, response)
            return
        }
        String ip = clientIp(request)
        String user = (request.getParameter('username') ?: '').trim().toLowerCase()
        String userKey = "login:user:${user}:${ip}"
        String ipKey = "login:ip:${ip}"
        if (user && !attemptLimiter.allow(userKey, AttemptLimiter.LOGIN_PER_USER_IP, AttemptLimiter.LOGIN_USER_WINDOW_MS)) {
            reject(response, attemptLimiter.retryAfterSeconds(userKey, AttemptLimiter.LOGIN_USER_WINDOW_MS))
            return
        }
        if (!attemptLimiter.allow(ipKey, AttemptLimiter.LOGIN_PER_IP, AttemptLimiter.LOGIN_IP_WINDOW_MS)) {
            reject(response, attemptLimiter.retryAfterSeconds(ipKey, AttemptLimiter.LOGIN_IP_WINDOW_MS))
            return
        }
        filterChain.doFilter(request, response)
        String location = response.getHeader('Location') ?: ''
        int status = response.status
        boolean failed = status == 401 || location.contains('login_error') || location.contains('/login/authfail')
        if (failed) {
            if (user) attemptLimiter.record(userKey, AttemptLimiter.LOGIN_USER_WINDOW_MS)
            attemptLimiter.record(ipKey, AttemptLimiter.LOGIN_IP_WINDOW_MS)
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String remote = request.remoteAddr ?: 'unknown'
        if (remote == '127.0.0.1' || remote == '::1' || remote == '0:0:0:0:0:0:0:1') {
            String forwarded = request.getHeader('X-Forwarded-For')
            if (forwarded) {
                return forwarded.split(',')[0].trim()
            }
        }
        return remote
    }

    private static void reject(HttpServletResponse response, long retryAfter) throws IOException {
        response.status = 429
        response.setHeader('Retry-After', String.valueOf(retryAfter))
        response.contentType = 'application/json;charset=UTF-8'
        response.writer.write('{"error":"Too many attempts","code":"RATE_LIMIT"}')
    }
}
