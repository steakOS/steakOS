// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors

package steakos

import grails.plugin.springsecurity.annotation.Secured

/**
 * Serves the packaged React app (prefix/web) on the API port.
 * /setup is the configuration wizard. Without this mapping Grails treats
 * "setup" as a missing controller and returns 404.
 */
@Secured(['permitAll'])
class SpaController {

    def page() {
        File root = SpaLocations.webRoot()
        File index = root ? new File(root, 'index.html') : null
        if (index == null || !index.isFile()) {
            render status: 404, text: "Setup wizard is not in this install (looked for ${root ?: 'web'}/index.html)."
            return
        }
        // The shell points at /assets/*.js. Grails' asset pipeline owns that
        // path and returns 404 for Vite files, which leaves a blank white page.
        // Serve the same files from /ui-static/ instead. Imports inside the
        // bundles are relative, so they follow this prefix.
        String html = index.getText('UTF-8')
        html = html.replace('="/assets/', '="/ui-static/assets/')
        html = html.replace('="/favicon', '="/ui-static/favicon')
        html = html.replace('="/apple-touch-icon.png"', '="/ui-static/apple-touch-icon.png"')
        render(text: html, contentType: 'text/html', encoding: 'UTF-8')
    }

    /** Files from the packaged web/ directory, under /ui-static/. */
    def file() {
        String uri = (request.forwardURI ?: request.requestURI ?: '') as String
        String rel = uri.replaceFirst('^.*?/ui-static/?', '')
        if (!rel || rel.contains('..') || rel.startsWith('/')) {
            render status: 404
            return
        }
        File root = SpaLocations.webRoot()
        File file = root ? new File(root, rel) : null
        if (file == null || !file.isFile()) {
            render status: 404, text: "Missing wizard file ${rel}"
            return
        }
        response.contentType = contentTypeFor(file.name)
        response.setHeader('Cache-Control', file.name.endsWith('.html') ? 'no-cache' : 'public, max-age=3600')
        response.outputStream << file.bytes
        response.outputStream.flush()
    }

    /** Vite files shipped in web/assets. Grails asset-pipeline files are not here. */
    def asset() {
        String uri = (request.forwardURI ?: request.requestURI ?: '') as String
        String rel = uri.replaceFirst('^.*?/assets/?', '')
        if (!rel || rel.contains('..')) {
            render status: 404
            return
        }
        File root = SpaLocations.webRoot()
        File file = root ? new File(new File(root, 'assets'), rel) : null
        if (file == null || !file.isFile()) {
            render status: 404
            return
        }
        String name = file.name.toLowerCase()
        String type = 'application/octet-stream'
        if (name.endsWith('.js')) type = 'text/javascript;charset=UTF-8'
        else if (name.endsWith('.css')) type = 'text/css;charset=UTF-8'
        else if (name.endsWith('.json')) type = 'application/json;charset=UTF-8'
        else if (name.endsWith('.svg')) type = 'image/svg+xml'
        else if (name.endsWith('.png')) type = 'image/png'
        else if (name.endsWith('.map')) type = 'application/json'
        response.contentType = type
        response.outputStream << file.bytes
        response.outputStream.flush()
    }

    private static String contentTypeFor(String filename) {
        String name = filename.toLowerCase()
        if (name.endsWith('.html')) return 'text/html;charset=UTF-8'
        if (name.endsWith('.js')) return 'text/javascript;charset=UTF-8'
        if (name.endsWith('.css')) return 'text/css;charset=UTF-8'
        if (name.endsWith('.json')) return 'application/json;charset=UTF-8'
        if (name.endsWith('.svg')) return 'image/svg+xml'
        if (name.endsWith('.png')) return 'image/png'
        if (name.endsWith('.ico')) return 'image/x-icon'
        if (name.endsWith('.jpg') || name.endsWith('.jpeg')) return 'image/jpeg'
        return 'application/octet-stream'
    }
}
