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
import steakos.catalogue.CatalogueItem
import steakos.catalogue.CatalogueService
import steakos.configuration.SetupService

@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class CatalogueApiController {

    static allowedMethods = [
            list  : 'GET',
            show  : 'GET',
            create: 'POST',
            update: 'PUT'
    ]

    CatalogueService catalogueService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    /** GET /api/catalogue/items?q=&for=sale|purchase&nature=GOODS|SERVICE&max=&offset= */
    def list() {
        try {
            String npub = resolveNpub()
            // Masters allow larger pages than desk docs (HARD_MAX 500 for pickers/lists)
            Map page = steakos.api.ListPaging.pageParams(params, 25, 500)
            Map result = catalogueService.searchPage(
                    npub,
                    params.q as String,
                    params.for as String ?: params.channel as String,
                    params.nature as String,
                    params.boolean('activeOnly', true),
                    page.max as int,
                    page.offset as int
            )
            render(steakos.api.ListPaging.envelope(
                    (result.items as List).collect { catalogueService.toMap(it as CatalogueItem) },
                    result.total as int,
                    result.max as int,
                    result.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc']
            ) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** GET /api/catalogue/items/$id */
    def show() {
        try {
            CatalogueItem ci = catalogueService.get(params.long('id'), resolveNpub())
            render(catalogueService.toMap(ci) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 404, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/catalogue/items */
    def create() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            CatalogueItem ci = catalogueService.create(resolveNpub(body), body)
            render(status: 201, text: (catalogueService.toMap(ci) as JSON))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** PUT /api/catalogue/items/$id */
    def update() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            CatalogueItem ci = catalogueService.update(params.long('id'), resolveNpub(body), body)
            render(catalogueService.toMap(ci) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
