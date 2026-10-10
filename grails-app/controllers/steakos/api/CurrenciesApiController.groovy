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
import steakos.configuration.CompanyCurrencyService
import steakos.configuration.TenantNpubService

/**
 * System currency catalog + company in-use (OI-CUR01 PR2).
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class CurrenciesApiController {

    static allowedMethods = [index: 'GET', inUse: 'GET', setInUse: 'PUT']

    CompanyCurrencyService companyCurrencyService
    TenantNpubService tenantNpubService
    SpringSecurityService springSecurityService

    /** GET /api/currencies — system enabled catalog */
    def index() {
        try {
            render([items: companyCurrencyService.listSystemEnabled()] as JSON)
        } catch (Exception e) {
            render(status: 500, text: ([error: e.message, code: 'CURRENCIES_LIST_FAILED'] as JSON))
        }
    }

    /** GET /api/currencies/in-use — current company dropdown set */
    def inUse() {
        try {
            String npub = resolveNpub()
            render([
                    items           : companyCurrencyService.listInUse(npub),
                    companyCurrency : companyCurrencyService.companyCurrencyCode(npub)
            ] as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message, code: 'CURRENCIES_IN_USE_FAILED'] as JSON))
        }
    }

    /** PUT /api/currencies/in-use — Admin only; body { codes: ["EUR","USD"] } */
    @Secured(['ROLE_ADMIN'])
    def setInUse() {
        try {
            String npub = resolveNpub()
            Map body = (request.JSON as Map) ?: [:]
            Collection codes = body.codes instanceof Collection ? (Collection) body.codes : []
            List<Map> items = companyCurrencyService.setInUse(npub, codes)
            render([
                    ok              : true,
                    items           : items,
                    companyCurrency : companyCurrencyService.companyCurrencyCode(npub)
            ] as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 422, text: ([error: e.message, code: 'CURRENCIES_IN_USE_INVALID'] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message, code: 'CURRENCIES_IN_USE_SAVE_FAILED'] as JSON))
        }
    }

    private String resolveNpub() {
        if (tenantNpubService != null) {
            return tenantNpubService.resolve(session)
        }
        User u = springSecurityService.currentUser as User
        return u?.npub ?: 'user:admin'
    }
}
