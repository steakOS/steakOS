// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors

package steakos

import grails.plugin.springsecurity.annotation.Secured

/**
 * Serves frontend/public/tour (copied to classpath:static/tour) from the API jar.
 * /tour and /tour/ open index.html. Grails' catch-all mapping would 404 these.
 */
@Secured(['permitAll'])
class TourStaticController {

    def serve() {
        String uri = (request.forwardURI ?: request.requestURI ?: '/tour') as String
        String rel = uri.replaceFirst('^.*?/tour/?', '')
        if (!rel || rel.endsWith('/')) {
            rel = "${rel}index.html"
        }
        rel = rel.replace('\\', '/')
        if (rel.contains('..') || rel.startsWith('/')) {
            render status: 404
            return
        }
        def resource = grailsApplication.mainContext.getResource("classpath:static/tour/${rel}")
        if (resource == null || !resource.exists()) {
            render status: 404
            return
        }
        String lower = rel.toLowerCase()
        String type = 'application/octet-stream'
        if (lower.endsWith('.html')) type = 'text/html;charset=UTF-8'
        else if (lower.endsWith('.json')) type = 'application/json;charset=UTF-8'
        else if (lower.endsWith('.css')) type = 'text/css;charset=UTF-8'
        else if (lower.endsWith('.js')) type = 'text/javascript;charset=UTF-8'
        else if (lower.endsWith('.svg')) type = 'image/svg+xml'
        else if (lower.endsWith('.png')) type = 'image/png'
        else if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) type = 'image/jpeg'
        response.contentType = type
        response.setHeader('Cache-Control', 'no-cache')
        resource.inputStream.withCloseable { input ->
            response.outputStream << input.bytes
        }
        response.outputStream.flush()
    }
}
