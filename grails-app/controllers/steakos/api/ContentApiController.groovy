// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package steakos.api

import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import groovy.util.logging.Slf4j
import steakos.User
import steakos.generic.*

/**
 * Object Companion API (CT-S1): /api/content/*
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class ContentApiController {

    ContentCompanionService contentCompanionService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    def typeList() {
        wrap { String npub ->
            [items: contentCompanionService.listTypes()]
        }
    }

    def profileShow() {
        wrap { String npub ->
            contentCompanionService.profile(hostPackage(), hostResource())
        }
    }

    def rail() {
        wrap { String npub ->
            contentCompanionService.rail(
                    npub,
                    hostPackage(),
                    hostResource(),
                    hostId(),
                    params.channel as String
            )
        }
    }

    /**
     * Batch content flags for desk list icons.
     * GET /api/content/summary?package=sales&resource=CustomerQuote&ids=1,2,3
     * (query keys package/resource are fine; path vars use hostPackage/hostResource)
     */
    def summary() {
        wrap { String npub ->
            String idsRaw = params.ids?.toString() ?: ''
            List idList = idsRaw ? idsRaw.split(',').toList() : []
            String pkg = (params.hostPackage ?: params.package) as String
            String res = (params.hostResource ?: params.resource) as String
            [
                    hostPackage : pkg,
                    hostResource: res,
                    items       : contentCompanionService.summarize(npub, pkg, res, idList)
            ]
        }
    }

    def addLink() {
        wrapBody { String npub, Map body ->
            [status: 201, body: contentCompanionService.toMap(
                    contentCompanionService.addLink(npub, hostPackage(), hostResource(), hostId(), body))]
        }
    }

    def addBody() {
        wrapBody { String npub, Map body ->
            [status: 201, body: contentCompanionService.toMap(
                    contentCompanionService.addBody(npub, hostPackage(), hostResource(), hostId(), body))]
        }
    }

    def addFile() {
        try {
            String npub = resolveNpub(params)
            def f = request.getFile('file')
            if (!f || f.empty) throw new IllegalArgumentException('file is required')
            ObjectContent c = contentCompanionService.addFile(
                    npub,
                    hostPackage(),
                    hostResource(),
                    hostId(),
                    f.bytes,
                    f.originalFilename,
                    f.contentType
            )
            renderJson(201, contentCompanionService.toMap(c))
        } catch (IllegalArgumentException | IllegalStateException e) {
            renderJson(400, [code: 'BAD_REQUEST', error: e.message])
        } catch (org.springframework.web.multipart.MultipartException e) {
            log.warn('Content multipart rejected: {}', e.message)
            renderJson(400, [code: 'UPLOAD_TOO_LARGE', error: e.message ?: 'Upload rejected (file too large or multipart error)'])
        } catch (Exception e) {
            log.error('Content file upload error', e)
            renderJson(500, [code: 'CONTENT_ERROR', error: e.message ?: e.class.simpleName])
        }
    }

    private void renderJson(int status, Object body) {
        render(status: status, contentType: 'application/json', text: (body as JSON).toString())
    }

    def contentUpdate() {
        wrapBody { String npub, Map body ->
            contentCompanionService.toMap(contentCompanionService.update(params.long('id'), npub, body))
        }
    }

    def contentPin() {
        wrapBody { String npub, Map body ->
            boolean pinned = body.containsKey('pinned') ? body.pinned as Boolean : true
            contentCompanionService.toMap(contentCompanionService.pin(params.long('id'), npub, pinned))
        }
    }

    def contentDelete() {
        wrap { String npub ->
            contentCompanionService.delete(params.long('id'), npub)
            [ok: true]
        }
    }

    def contentDownload() {
        try {
            String npub = resolveNpub(params)
            ObjectContent c = contentCompanionService.get(params.long('id'), npub)
            byte[] data = contentCompanionService.download(c.id, npub)
            String name = c.fileName ?: 'download'
            response.setContentType(c.mimeType ?: 'application/octet-stream')
            response.setHeader('Content-Disposition', "attachment; filename=\"${name}\"")
            response.outputStream << data
            response.outputStream.flush()
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('Content download error', e)
            render(status: 500, text: ([code: 'CONTENT_ERROR', error: e.message] as JSON))
        }
    }

    private String hostPackage() {
        (params.hostPackage ?: params.package)?.toString()?.trim()
    }

    private String hostResource() {
        (params.hostResource ?: params.resource)?.toString()?.trim()
    }

    private Long hostId() {
        if (params.hostId != null && params.hostId != '') return params.long('hostId')
        return params.long('id')
    }

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('Content API error', e)
            render(status: 500, text: ([code: 'CONTENT_ERROR', error: e.message] as JSON))
        }
    }

    private void wrapBody(Closure c) {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            def result = c.call(npub, body)
            if (result instanceof Map && result.status && result.body != null) {
                render(status: result.status as int, text: (result.body as JSON))
            } else {
                render(result as JSON)
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('Content API error', e)
            render(status: 500, text: ([code: 'CONTENT_ERROR', error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
