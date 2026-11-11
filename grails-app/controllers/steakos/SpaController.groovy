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
        response.contentType = 'text/html;charset=UTF-8'
        response.setHeader('Cache-Control', 'no-cache')
        response.outputStream << index.bytes
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
}
