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
import steakos.configuration.CompanyContextService
import steakos.configuration.LocaleService
import steakos.configuration.CompanyConfigService
import steakos.configuration.DevelopmentModeService
import steakos.configuration.LegalEntity
import steakos.configuration.TemplateTiers
import steakos.security.RoleProfileService

@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class MeApiController {

    static allowedMethods = [index: 'GET', preferences: 'GET', updatePreferences: 'PUT', selectCompany: 'POST']

    LocaleService localeService
    SpringSecurityService springSecurityService
    RoleProfileService roleProfileService
    CompanyConfigService companyConfigService
    CompanyContextService companyContextService
    DevelopmentModeService developmentModeService

    def index() {
        User u = springSecurityService.currentUser as User
        String accept = request.getHeader('Accept-Language')
        String hint = params.locale ?: request.getHeader('X-SteakOS-Locale')
        Map resolved = localeService.resolveForUser(u?.username, accept, hint as String)
        Collection authorities = springSecurityService.authentication?.authorities*.authority ?: []
        boolean readOnly = authorities?.contains('ROLE_READ_ONLY')
        boolean isAdmin = authorities?.contains('ROLE_ADMIN')
        boolean isCeo = authorities?.contains('ROLE_CEO')
        boolean totpEnabled = !!u?.totpEnabled
        boolean totpVerified = session.getAttribute(steakos.security.TotpGateFilter.SESSION_TOTP_OK) == Boolean.TRUE
        boolean adminTotpBypass = false
        try {
            if (!steakos.security.ProductionSecrets.productionFlag()) {
                adminTotpBypass = isAdmin && !!companyConfigService?.isAdminTotpBypassEnabled()
                if (adminTotpBypass && !totpVerified) {
                    session.setAttribute(steakos.security.TotpGateFilter.SESSION_TOTP_OK, Boolean.TRUE)
                    totpVerified = true
                }
            }
        } catch (ignored) {
        }

        List enabledFamilies = null
        String templateTier = TemplateTiers.DEFAULT_OPERATOR_TIER
        try {
            String npub = u?.npub?.trim()
            if (npub) {
                templateTier = companyConfigService?.getString(npub, 'template.tier', TemplateTiers.DEFAULT_OPERATOR_TIER) ?: TemplateTiers.DEFAULT_OPERATOR_TIER
                enabledFamilies = companyConfigService?.enabledFamilies(npub) as List
            }
        } catch (ignored) {
        }

        Map permissions = null
        // After login land on dashboard for Admin and CEO; desk users → My work
        String defaultHome = (isCeo || isAdmin) ? '/dashboard' : '/work'
        String roleProfileId = u?.roleProfileId
        try {
            if (roleProfileService != null && u != null) {
                permissions = roleProfileService.effectivePermissions(u, enabledFamilies, templateTier)
                // Do not let a stale RoleProfile.defaultHome (/settings) override Admin/CEO dashboard.
                if (!(isCeo || isAdmin)) {
                    defaultHome = (permissions.defaultHome as String) ?: defaultHome
                } else {
                    defaultHome = '/dashboard'
                }
                roleProfileId = (permissions.roleProfileId as String) ?: roleProfileId
                // Nav pack for Admin/CEO = metal default (navFamilies); others keep template list
                if (permissions?.navFamilies instanceof List && (permissions.navFamilies as List)) {
                    enabledFamilies = permissions.navFamilies as List
                }
            }
        } catch (ignored) {
        }

        Map companyCtx = [:]
        try {
            if (companyContextService != null && u != null) {
                companyCtx = companyContextService.contextPayload(u, session)
            }
        } catch (ignored) {
        }

        boolean developmentMode = false
        try {
            developmentMode = !!developmentModeService?.isEnabled()
        } catch (ignored) {
        }

        render([
                username      : u?.username,
                displayName   : u?.displayName,
                locale        : resolved,
                authorities   : authorities as List,
                readOnly      : readOnly,
                isAdmin       : isAdmin,
                isCeo         : isCeo,
                totpEnabled   : totpEnabled,
                totpVerified  : totpVerified || !totpEnabled || adminTotpBypass,
                totpRequired  : totpEnabled && !totpVerified && !adminTotpBypass,
                adminTotpBypass: adminTotpBypass,
                hasNpub       : u?.npub && !u.npub.startsWith('user:'),
                defaultHome   : defaultHome,
                roleProfileId : roleProfileId,
                permissions   : permissions,
                templateTier  : templateTier,
                enabledFamilies: enabledFamilies,
                companies       : companyCtx.companies ?: [],
                activeCompanyId : companyCtx.activeCompanyId,
                activeCompany   : companyCtx.activeCompany,
                needCompanyPick : !!companyCtx.needCompanyPick,
                companyCurrency : (companyCtx.activeCompany as Map)?.companyCurrency,
                developmentMode : developmentMode,
                coderAgentKey   : DevelopmentModeService.CODER_AGENT_KEY
        ] as JSON)
    }

    /** Set active company for this session and persist per-user preference. */
    def selectCompany() {
        try {
            User u = springSecurityService.currentUser as User
            Map body = (request.JSON as Map) ?: [:]
            Long companyId = body.companyId != null ? (body.companyId as Number).longValue() : null
            if (companyId == null && params.companyId) {
                companyId = params.long('companyId')
            }
            LegalEntity le = companyContextService.selectCompany(u, session, companyId)
            render([
                    ok            : true,
                    activeCompanyId: le.id,
                    activeCompany : companyContextService.companyMap(le),
                    needCompanyPick: false
            ] as JSON)
        } catch (SecurityException e) {
            render(status: 403, text: ([error: e.message, code: 'COMPANY_FORBIDDEN'] as JSON))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'COMPANY_INVALID'] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message, code: 'COMPANY_SELECT_FAILED'] as JSON))
        }
    }

    def preferences() {
        User u = springSecurityService.currentUser as User
        String loc = localeService.getUserLocale(u?.username)
        Long companyId = null
        try {
            companyId = companyContextService?.preferredCompanyId(u)
            if (companyId == null && u != null) {
                companyId = companyContextService?.resolveActiveCompanyId(u, session)
            }
        } catch (ignored) {
        }
        render([
                'ui.locale'           : loc,
                locale                : loc,
                'ui.activeCompanyId'  : companyId,
                activeCompanyId       : companyId
        ] as JSON)
    }

    def updatePreferences() {
        try {
            User u = springSecurityService.currentUser as User
            Map body = (request.JSON as Map) ?: [:]
            String code = (body['ui.locale'] ?: body.locale) as String
            Long companyId = null
            if (body['ui.activeCompanyId'] != null || body.activeCompanyId != null || body.companyId != null) {
                def raw = body['ui.activeCompanyId'] ?: body.activeCompanyId ?: body.companyId
                companyId = raw instanceof Number ? (raw as Number).longValue() : raw?.toString()?.toLong()
            }
            if (!code && companyId == null) {
                render(status: 400, text: ([error: 'ui.locale or ui.activeCompanyId required', code: 'INVALID_PREFS'] as JSON))
                return
            }
            Map out = [ok: true]
            if (code) {
                localeService.setUserLocale(u.username, code)
                String accept = request.getHeader('Accept-Language')
                out.locale = localeService.resolveForUser(u.username, accept, code)
            }
            if (companyId != null) {
                LegalEntity le = companyContextService.selectCompany(u, session, companyId)
                out.activeCompanyId = le.id
                out.activeCompany = companyContextService.companyMap(le)
                out['ui.activeCompanyId'] = le.id
            }
            render(out as JSON)
        } catch (SecurityException e) {
            render(status: 403, text: ([error: e.message, code: 'COMPANY_FORBIDDEN'] as JSON))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'INVALID_PREFS'] as JSON))
        }
    }
}
