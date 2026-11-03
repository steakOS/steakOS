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
import steakos.configuration.DemoSeedService
import steakos.configuration.OrangeSteakHouseSeedService
import steakos.configuration.HeardCookArchitectsSeedService
import steakos.configuration.CompanyContextService
import steakos.configuration.SetupService

/**
 * Config wizard API (ADR-026 / OI-W01–W02).
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class SetupApiController {

    static allowedMethods = [
            status       : 'GET',
            draftGet     : 'GET',
            draftPut     : 'PUT',
            apply        : 'POST',
            complete     : 'POST',
            reset        : 'POST',
            check        : 'GET',
            profile      : 'GET',
            demoSeed     : 'POST',
            demoReset    : 'POST',
            heardCookSeed: 'POST',
            heardCookReset: 'POST',
            templateTier : 'POST',
            packOptions  : 'POST',
            privacyHrAck : 'POST'
    ]

    SetupService setupService
    DemoSeedService demoSeedService
    OrangeSteakHouseSeedService orangeSteakHouseSeedService
    HeardCookArchitectsSeedService heardCookArchitectsSeedService
    CompanyContextService companyContextService
    SpringSecurityService springSecurityService

    /** GET /api/setup/status */
    def status() {
        render(setupService.statusMap(resolveNpub(null)) as JSON)
    }

    /** GET /api/setup/draft */
    def draftGet() {
        render(setupService.getDraft(resolveNpub(null)) as JSON)
    }

    /** PUT /api/setup/draft */
    def draftPut() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            Integer step = body.step != null ? body.step as Integer : null
            Map draft = (body.draft as Map) ?: body
            draft.remove('step')
            setupService.saveDraft(resolveNpub(body), step, draft)
            render(setupService.getDraft(resolveNpub(body)) as JSON)
        } catch (IllegalStateException e) {
            render(status: 409, text: ([error: e.message, code: 'SETUP_COMPLETE'] as JSON))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/setup/apply */
    def apply() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            Map draft = (body.draft as Map) ?: body
            Map result = setupService.apply(resolveNpub(body), draft, currentUsername())
            render([ok: true, result: result, status: setupService.statusMap(resolveNpub(body))] as JSON)
        } catch (IllegalStateException e) {
            String code = e.message?.contains('complete') ? 'SETUP_COMPLETE' : 'SETUP_ERROR'
            render(status: code == 'SETUP_COMPLETE' ? 409 : 400, text: ([error: e.message, code: code] as JSON))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 500, text: ([error: e.message, code: 'SETUP_APPLY_FAILED'] as JSON))
        }
    }

    /** POST /api/setup/complete */
    def complete() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            setupService.complete(npub, currentUsername())
            Map demo = null
            boolean wantDemo = body.seedDemo == null || body.seedDemo == true || body.seedDemo == 'true'
            Map heard = null
            if (wantDemo) {
                try {
                    companyContextService?.ensureBootstrapped()
                    User.findAllByEnabled(true).each { User u ->
                        companyContextService?.companiesFor(u)?.each { le ->
                            boolean isDef = le?.code == steakos.configuration.LegalEntity.CODE_PRIMARY
                            companyContextService.ensureMembership(u, le, isDef)
                        }
                    }
                    demo = orangeSteakHouseSeedService?.ensureAndSeed()
                    heard = heardCookArchitectsSeedService?.ensureAndSeed()
                } catch (Exception e) {
                    demo = [error: e.message]
                }
            }
            // Completing setup adopts the configured (LIVE primary) company for this user.
            try {
                User u = springSecurityService.currentUser as User
                steakos.configuration.LegalEntity primary =
                        steakos.configuration.LegalEntity.findByCode(steakos.configuration.LegalEntity.CODE_PRIMARY)
                if (u && primary) {
                    companyContextService?.adoptCompany(u, session, primary)
                }
            } catch (ignored) {
            }
            render([ok: true, status: setupService.statusMap(npub), demoSeed: demo, heardCookSeed: heard] as JSON)
        } catch (IllegalStateException e) {
            render(status: 400, text: ([error: e.message, code: 'SETUP_INCOMPLETE'] as JSON))
        }
    }

    /** POST /api/setup/demo-seed — Orange Steak House pack (PDR-026); falls back to Iron seed if needed */
    def demoSeed() {
        try {
            if (orangeSteakHouseSeedService != null) {
                companyContextService?.ensureBootstrapped()
                render(orangeSteakHouseSeedService.ensureAndSeed() as JSON)
                return
            }
            Map body = (request.JSON as Map) ?: [:]
            render(demoSeedService.seedDemo(resolveNpub(body)) as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/setup/demo-reset — Admin wipe+reseed Orange Steak House only */
    @Secured(['ROLE_ADMIN'])
    def demoReset() {
        if (isAgentPrincipal()) {
            render(status: 403, text: ([error: 'Agents cannot reset demo', code: 'AGENT_DEMO_RESET_DENIED'] as JSON))
            return
        }
        try {
            Map result = orangeSteakHouseSeedService.resetDemo()
            render([ok: true] + result as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message, code: 'DEMO_RESET_FAILED'] as JSON))
        }
    }

    /** POST /api/setup/heard-cook-seed — ensure Heard & Cook Architects pack */
    def heardCookSeed() {
        try {
            companyContextService?.ensureBootstrapped()
            render(heardCookArchitectsSeedService.ensureAndSeed() as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/setup/heard-cook-reset — Admin wipe+reseed Heard & Cook only */
    @Secured(['ROLE_ADMIN'])
    def heardCookReset() {
        if (isAgentPrincipal()) {
            render(status: 403, text: ([error: 'Agents cannot reset demo', code: 'AGENT_DEMO_RESET_DENIED'] as JSON))
            return
        }
        try {
            Map result = heardCookArchitectsSeedService.resetDemo()
            render([ok: true] + result as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message, code: 'HEARD_COOK_RESET_FAILED'] as JSON))
        }
    }

    /** POST /api/setup/reset — admin full reset (ADR-037: denied for agents) */
    def reset() {
        if (isAgentPrincipal()) {
            render(status: 403, text: ([error: 'Agents cannot reset setup', code: 'AGENT_SETUP_RESET_DENIED'] as JSON))
            return
        }
        try {
            Map body = (request.JSON as Map) ?: [:]
            boolean wipeAll = body.wipeAll == true || body.wipeAll == 'true' ||
                    body.wipeMode == 'all'
            boolean wipe = wipeAll || body.wipeSeed == true || body.wipeSeed == 'true' ||
                    body.wipeMode == 'seed'
            Map result = setupService.reset(resolveNpub(body), wipe, wipeAll, currentUsername())
            Map status = setupService.statusMap(resolveNpub(body))
            render([
                    ok      : true,
                    wiped   : wipe,
                    wipeAll : wipeAll,
                    backup  : result?.backup,
                    wipe    : result?.wipe ? [
                            truncatedCount: (result.wipe.truncated as List)?.size() ?: 0,
                            kept          : result.wipe.kept,
                            skippedCount  : (result.wipe.skipped as List)?.size() ?: 0,
                    ] : null,
                    status  : status,
            ] as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message, code: 'SETUP_RESET_FAILED'] as JSON))
        }
    }

    /**
     * POST /api/setup/template-tier — switch metal pack + families without wizard reset.
     * Body: { "templateTier": "BRONZE" } optional enabledFamilies, pluginDepth, messaging.
     * Only metals open for metal selection are accepted (Silver stays locked until DoD).
     */
    def templateTier() {
        if (isAgentPrincipal()) {
            render(status: 403, text: ([error: 'Agents cannot change template tier', code: 'AGENT_TEMPLATE_DENIED'] as JSON))
            return
        }
        try {
            Map body = (request.JSON as Map) ?: [:]
            String tier = (body.templateTier ?: body.tier) as String
            if (!tier?.trim()) {
                render(status: 400, text: ([error: 'templateTier required'] as JSON))
                return
            }
            List override = body.enabledFamilies instanceof List ? (body.enabledFamilies as List) : null
            Map options = [:]
            if (body.pluginDepth instanceof Map) options.pluginDepth = body.pluginDepth
            if (body.messaging instanceof Map) options.messaging = body.messaging
            Map status = setupService.setTemplateTier(resolveNpub(body), tier, override, options ?: null)
            render([ok: true, status: status] as JSON)
        } catch (IllegalArgumentException e) {
            String msg = e.message ?: ''
            String code = msg.contains('cannot enable families') ? 'COPPER_FAMILY_FORBIDDEN'
                    : msg.contains('privacy') || msg.contains('acknowledgement') ? 'HR_PRIVACY_ACK_REQUIRED'
                    : 'METAL_NOT_OPEN_FOR_SELECTION'
            render(status: 400, text: ([error: e.message, code: code] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /**
     * POST /api/setup/pack-options — messaging toggles (+ optional pluginDepth) for current tier.
     * Body: { "messaging": { "emailEnabled": false, "telegramEnabled": false }, "pluginDepth"?: {...} }
     * Does not change template tier; Silver+ depth remains Advanced (pack-mandatory).
     */
    def packOptions() {
        if (isAgentPrincipal()) {
            render(status: 403, text: ([error: 'Agents cannot change pack options', code: 'AGENT_PACK_DENIED'] as JSON))
            return
        }
        try {
            Map body = (request.JSON as Map) ?: [:]
            Map options = [:]
            if (body.messaging instanceof Map) {
                options.messaging = body.messaging
            } else if (body.containsKey('emailEnabled') || body.containsKey('telegramEnabled')) {
                options.messaging = [
                        emailEnabled   : body.emailEnabled,
                        telegramEnabled: body.telegramEnabled
                ]
            }
            if (body.pluginDepth instanceof Map) options.pluginDepth = body.pluginDepth
            if (!options) {
                render(status: 400, text: ([error: 'messaging or pluginDepth required'] as JSON))
                return
            }
            Map status = setupService.setPackOptions(resolveNpub(body), options)
            render([ok: true, status: status] as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/setup/privacy/hr-ack — Bronze BR-R6 privacy acknowledgement for HR */
    def privacyHrAck() {
        if (isAgentPrincipal()) {
            render(status: 403, text: ([error: 'Agents cannot acknowledge privacy', code: 'AGENT_PRIVACY_DENIED'] as JSON))
            return
        }
        try {
            Map body = (request.JSON as Map) ?: [:]
            Map status = setupService.acknowledgeHrPrivacy(resolveNpub(body), currentUsername())
            render([ok: true, status: status] as JSON)
        } catch (Exception e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    private boolean isAgentPrincipal() {
        def auth = springSecurityService?.authentication
        if (!auth?.authenticated) return false
        Collection authorities = auth.authorities*.authority
        boolean hasAi = authorities?.contains('ROLE_AI')
        boolean hasAdmin = authorities?.contains('ROLE_ADMIN') || authorities?.contains('ROLE_C')
        return hasAi && !hasAdmin
    }

    /** GET /api/setup/check */
    def check() {
        render(setupService.consistencyReport(resolveNpub(null)) as JSON)
    }

    /** GET /api/setup/profile — Company profile (read-only tenant config) */
    def profile() {
        render(setupService.profile(resolveNpub(null), currentUsername()) as JSON)
    }

    private String resolveNpub(Map body) {
        if (body?.npubOwner) return body.npubOwner as String
        User u = springSecurityService?.currentUser as User
        if (u?.npub) return u.npub
        return 'user:admin'
    }

    private String currentUsername() {
        User u = springSecurityService?.currentUser as User
        u?.username ?: 'admin'
    }
}
