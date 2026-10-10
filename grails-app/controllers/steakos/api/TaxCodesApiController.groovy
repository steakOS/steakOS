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
import steakos.configuration.CompanyTaxService
import steakos.configuration.TenantNpubService

/**
 * System tax-code catalog + company in-use (OI-F23 P1).
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class TaxCodesApiController {

    static allowedMethods = [index: 'GET', inUse: 'GET', setInUse: 'PUT']

    CompanyTaxService companyTaxService
    TenantNpubService tenantNpubService
    SpringSecurityService springSecurityService

    /** GET /api/tax-codes — system enabled catalog (?jurisdiction=DE) */
    def index() {
        try {
            String jur = params.jurisdiction as String
            render([items: companyTaxService.listSystemEnabled(jur)] as JSON)
        } catch (Exception e) {
            render(status: 500, text: ([error: e.message, code: 'TAX_CODES_LIST_FAILED'] as JSON))
        }
    }

    /** GET /api/tax-codes/in-use — current company dropdown set + regime */
    def inUse() {
        try {
            String npub = resolveNpub()
            render([
                    items          : companyTaxService.listInUse(npub),
                    taxRegime      : companyTaxService.taxRegime(npub),
                    defaultTaxCode : companyTaxService.defaultTaxCode(npub)
            ] as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message, code: 'TAX_CODES_IN_USE_FAILED'] as JSON))
        }
    }

    /** PUT /api/tax-codes/in-use — Admin; body { codes: [...], taxRegime?, defaultTaxCode? } */
    @Secured(['ROLE_ADMIN'])
    def setInUse() {
        try {
            String npub = resolveNpub()
            Map body = (request.JSON as Map) ?: [:]
            if (body.taxRegime || body.defaultTaxCode) {
                String regime = (body.taxRegime as String) ?: companyTaxService.taxRegime(npub)
                companyTaxService.applyRegimeSettings(npub, regime, body.defaultTaxCode as String)
            }
            Collection codes = body.codes instanceof Collection ? (Collection) body.codes : null
            List<Map> items = codes != null
                    ? companyTaxService.setInUse(npub, codes)
                    : companyTaxService.listInUse(npub)
            render([
                    ok             : true,
                    items          : items,
                    taxRegime      : companyTaxService.taxRegime(npub),
                    defaultTaxCode : companyTaxService.defaultTaxCode(npub)
            ] as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 422, text: ([error: e.message, code: 'TAX_CODES_IN_USE_INVALID'] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message, code: 'TAX_CODES_IN_USE_SAVE_FAILED'] as JSON))
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
