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
import steakos.nostr.NostrIntegrationService
import steakos.nostr.NostrPublishService
import steakos.nostr.NostrRelayOpsService

/**
 * OI-NOSTR01 — Nostr integration settings, probe, publish stub (D15 Integrations hub API).
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class NostrApiController {

    NostrIntegrationService nostrIntegrationService
    NostrPublishService nostrPublishService
    NostrRelayOpsService nostrRelayOpsService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    static allowedMethods = [
            settingsGet : 'GET',
            settingsPut : 'PUT',
            probe       : 'POST',
            publishStub : 'POST',
            lastIntent  : 'GET',
            identityHint: 'GET',
            relayStatus : 'GET',
            relayEnsure : 'POST'
    ]

    def settingsGet() {
        wrap { String npub ->
            nostrIntegrationService.publicSettings(npub)
        }
    }

    def settingsPut() {
        wrapBody { String npub, Map body ->
            User u = springSecurityService.currentUser as User
            Collection auths = springSecurityService.authentication?.authorities*.authority ?: []
            boolean isAdmin = auths.contains('ROLE_ADMIN')
            nostrIntegrationService.updateSettings(npub, body, u?.username, isAdmin)
        }
    }

    def probe() {
        wrap { String npub ->
            nostrIntegrationService.probeRelay(npub)
        }
    }

    /** Local relay binary / reachability (wizard + Integrations). */
    def relayStatus() {
        wrap { String npub ->
            nostrRelayOpsService.status(npub)
        }
    }

    /**
     * Ensure project-local nostr-rs-relay is installed and listening (D2 native).
     * Admin-oriented; used by setup wizard when Level ≥ 1.
     */
    def relayEnsure() {
        wrap { String npub ->
            Collection auths = springSecurityService.authentication?.authorities*.authority ?: []
            if (!(auths.contains('ROLE_ADMIN') || auths.contains('ROLE_CEO'))) {
                throw new IllegalStateException('Admin or CEO required to install/start the local Nostr relay')
            }
            nostrRelayOpsService.ensureInstalledAndStarted(npub)
        }
    }

    /** Manual audit publish / stub for Integrations hub testing. */
    def publishStub() {
        wrapBody { String npub, Map body ->
            String summary = (body.summary ?: body.message ?: 'manual audit stub')?.toString()
            nostrPublishService.publishAuditEvent(npub, summary, body.meta instanceof Map ? body.meta as Map : [:])
        }
    }

    def lastIntent() {
        wrap { String npub ->
            nostrPublishService.lastIntent(npub)
        }
    }

    /** Wizard / hub identity detection hints (D7) — never returns nsec. */
    def identityHint() {
        wrap { String npub ->
            User u = springSecurityService.currentUser as User
            String linked = NostrIntegrationService.linkedNpubForUser(u)
            Map settings = nostrIntegrationService.publicSettings(npub)
            [
                    linkedNpub       : linked,
                    linkedNpubMasked : NostrIntegrationService.maskNpub(linked),
                    operatorNpub     : settings.operatorNpub,
                    extensionHint    : 'Detect window.nostr in the browser (NIP-07); never paste nsec',
                    relayUrl         : settings.relayUrl,
                    level            : settings.level,
                    uiLevel          : settings.uiLevel
            ]
        }
    }

    private void wrap(Closure c) {
        try {
            String npub = resolveNpub(params)
            def result = c.call(npub)
            render(result as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    private void wrapBody(Closure c) {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            def result = c.call(npub, body)
            render(result as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
