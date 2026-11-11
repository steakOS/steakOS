// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors

package steakos

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * The one-line install runs only the API jar. The configuration wizard is the
 * React app in the package {@code web/} folder. This filter serves that app for
 * {@code /setup} (and the other SPA routes) and serves Vite files under
 * {@code web/assets} when they exist. Grails asset-pipeline files are left alone.
 *
 * Requires {@code -Dsteakos.home=<prefix>} from bin/steakos.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class SpaWebFilter implements Filter {

    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        HttpServletRequest req = (HttpServletRequest) request
        HttpServletResponse res = (HttpServletResponse) response
        String uri = req.requestURI ?: '/'
        if (uri.contains('..')) {
            chain.doFilter(request, response)
            return
        }
        File web = SpaLocations.webRoot()
        if (web == null) {
            chain.doFilter(request, response)
            return
        }
        if (uri.startsWith('/assets/') || isRootStatic(uri)) {
            File file = new File(web, uri.startsWith('/') ? uri.substring(1) : uri)
            if (file.isFile()) {
                writeFile(res, file)
                return
            }
        }
        if (isSpaRoute(uri)) {
            File index = new File(web, 'index.html')
            if (index.isFile()) {
                writeFile(res, index)
                return
            }
        }
        chain.doFilter(request, response)
    }

    private static boolean isSpaRoute(String uri) {
        return uri == '/setup' || uri.startsWith('/setup/')
                || uri == '/dashboard' || uri.startsWith('/dashboard/')
                || uri == '/settings' || uri.startsWith('/settings/')
                || uri == '/help' || uri.startsWith('/help/')
                || uri == '/company' || uri == '/about' || uri == '/work'
                || uri == '/totp' || uri.startsWith('/totp/')
                || uri.startsWith('/sales') || uri.startsWith('/finance')
                || uri.startsWith('/system') || uri.startsWith('/domains')
                || uri.startsWith('/scm') || uri.startsWith('/agents')
                || uri.startsWith('/capability') || uri.startsWith('/analytics')
                || uri.startsWith('/hr') || uri.startsWith('/delivery')
                || uri.startsWith('/aftersales') || uri.startsWith('/digital')
                || uri.startsWith('/strategy') || uri.startsWith('/development')
                || uri.startsWith('/grrc') || uri.startsWith('/stakeholder')
                || uri == '/users' || uri == '/roles' || uri == '/security'
                || uri == '/integrations' || uri == '/company'
    }

    private static boolean isRootStatic(String uri) {
        return uri == '/favicon.ico' || uri.startsWith('/favicon')
                || uri.startsWith('/brand-') || uri.startsWith('/logo')
                || uri.startsWith('/icon-') || uri == '/icons.svg'
                || uri == '/apple-touch-icon.png'
    }

    private static void writeFile(HttpServletResponse res, File file) {
        String name = file.name.toLowerCase()
        String type = 'application/octet-stream'
        if (name.endsWith('.html')) type = 'text/html;charset=UTF-8'
        else if (name.endsWith('.js')) type = 'text/javascript;charset=UTF-8'
        else if (name.endsWith('.css')) type = 'text/css;charset=UTF-8'
        else if (name.endsWith('.json')) type = 'application/json;charset=UTF-8'
        else if (name.endsWith('.svg')) type = 'image/svg+xml'
        else if (name.endsWith('.png')) type = 'image/png'
        else if (name.endsWith('.ico')) type = 'image/x-icon'
        else if (name.endsWith('.jpg') || name.endsWith('.jpeg')) type = 'image/jpeg'
        res.contentType = type
        res.setHeader('Cache-Control', name.endsWith('.html') ? 'no-cache' : 'public, max-age=3600')
        file.withInputStream { input ->
            res.outputStream << input.bytes
        }
        res.outputStream.flush()
    }
}
