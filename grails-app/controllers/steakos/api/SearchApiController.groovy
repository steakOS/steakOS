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
import steakos.configuration.CompanyConfigService
import steakos.configuration.TenantNpubService
import steakos.configuration.TemplateTiers
import steakos.search.SearchService
import steakos.security.RoleProfileService

/**
 * GET /api/search?q=&types=&maxPerType= — S0 top-bar fan-out (Task 7642).
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class SearchApiController {

    static allowedMethods = [index: 'GET']

    SearchService searchService
    TenantNpubService tenantNpubService
    SpringSecurityService springSecurityService
    RoleProfileService roleProfileService
    CompanyConfigService companyConfigService

    def index() {
        String q = (params.q ?: '').toString()
        Integer maxPerType = params.maxPerType != null ? params.int('maxPerType') : null
        List<String> types = null
        if (params.types) {
            types = params.types.toString().split(',').collect { it.trim() }.findAll { it }
        }
        String npub = tenantNpubService.resolve(session)
        User u = springSecurityService.currentUser as User
        Collection authorities = springSecurityService.authentication?.authorities*.authority ?: []
        boolean elevated = authorities?.contains('ROLE_ADMIN') || authorities?.contains('ROLE_CEO')

        List enabledFamilies = null
        String templateTier = TemplateTiers.DEFAULT_OPERATOR_TIER
        try {
            String cfgNpub = npub ?: u?.npub ?: 'user:admin'
            templateTier = companyConfigService?.getString(cfgNpub, 'template.tier', TemplateTiers.DEFAULT_OPERATOR_TIER)
                    ?: TemplateTiers.DEFAULT_OPERATOR_TIER
            enabledFamilies = companyConfigService?.enabledFamilies(cfgNpub) as List
            if (u && roleProfileService) {
                Map perms = roleProfileService.effectivePermissions(u, enabledFamilies, templateTier)
                if (perms?.navFamilies) {
                    enabledFamilies = perms.navFamilies as List
                }
            }
        } catch (ignored) {
        }

        Map result = searchService.search(
                q, types, maxPerType, npub, u, enabledFamilies, templateTier, elevated)
        render(result as JSON)
    }
}
