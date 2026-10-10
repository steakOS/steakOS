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
import steakos.User
import steakos.configuration.ImportService
import steakos.configuration.SetupService

@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class ImportApiController {

    static allowedMethods = [
            packs  : 'GET',
            list   : 'GET',
            upload : 'POST',
            preview: 'POST',
            commit : 'POST',
            show   : 'GET'
    ]

    ImportService importService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    def packs() {
        render([packs: importService.packs()] as JSON)
    }

    def list() {
        render([items: importService.listJobs(npub())] as JSON)
    }

    def show(Long id) {
        try {
            def job = steakos.configuration.ImportJob.get(id)
            if (!job || job.npubOwner != npub()) {
                render(status: 404, text: ([error: 'not found'] as JSON))
                return
            }
            render(importService.toMap(job) as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST multipart or JSON { packType, fileName, csvText } */
    def upload() {
        try {
            String packType = params.packType ?: request.JSON?.packType
            String fileName = params.fileName ?: request.JSON?.fileName
            String raw = request.JSON?.csvText as String
            if (!raw && params.csvText) raw = params.csvText as String
            if (!raw && request.getFile('file')) {
                raw = new String(request.getFile('file').bytes, 'UTF-8')
                fileName = fileName ?: request.getFile('file').originalFilename
            }
            def job = importService.createJob(npub(), packType as String, fileName as String, raw)
            def preview = importService.preview(job.id, npub())
            render([ok: true, job: preview] as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /**
     * Re-run dry-run analysis after the UI mapping step.
     * body.mapping = steakOS-field → CSV-header;
     * body.defaultNature = optional GOODS|SERVICE|product|service for all rows.
     */
    def preview(Long id) {
        try {
            Map body = (request.JSON as Map) ?: [:]
            render(importService.preview(id, npub(), body) as JSON)
        } catch (Exception e) {
            log.error('Import preview failed', e)
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    def commit(Long id) {
        try {
            Map body = (request.JSON as Map) ?: [:]
            Map opts = [
                    confirmed    : body.confirmed == true || body.confirmed == 'true' || params.boolean('confirmed'),
                    mapping      : body.mapping as Map,
                    defaultNature: body.defaultNature,
            ]
            render([ok: true, job: importService.commit(id, npub(), opts)] as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message, code: 'IMPORT_NOT_CONFIRMED'] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    private String npub() {
        tenantNpubService.resolve(session, null)
    }
}
