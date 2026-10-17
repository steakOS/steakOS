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

package steakos

import grails.converters.JSON
import groovy.util.logging.Slf4j
import steakos.configuration.AdvancedGateService
import steakos.configuration.AdvancedMetalPolicy
import steakos.configuration.AdvancedRouteCatalog
import steakos.configuration.RequiresAdvanced
import steakos.configuration.TemplateTiers

/**
 * Refuses Advanced routes/APIs for Bronze/Copper tenants (OI-SI05).
 * Matches known Advanced path prefixes and controllers/actions annotated {@link RequiresAdvanced}.
 */
@Slf4j
class AdvancedGateInterceptor {

    AdvancedGateService advancedGateService

    AdvancedGateInterceptor() {
        matchAll()
    }

    boolean before() {
        boolean gated = AdvancedRouteCatalog.isAdvancedPath(request?.requestURI) || hasRequiresAdvanced()
        if (!gated) {
            return true
        }
        String tier = TemplateTiers.DEFAULT_OPERATOR_TIER
        try {
            tier = advancedGateService?.resolveTier(session) ?: tier
        } catch (Exception e) {
            log.debug("advanced gate tier resolve failed: ${e.message}")
        }
        if (AdvancedMetalPolicy.allowsAdvanced(tier)) {
            return true
        }
        response.status = 403
        response.contentType = 'application/json;charset=UTF-8'
        Map body = advancedGateService?.denialBody(tier) ?: [
                error: AdvancedMetalPolicy.denialMessage(tier),
                code : AdvancedMetalPolicy.denialCode(),
                tier : TemplateTiers.normalize(tier)
        ]
        render body as JSON
        return false
    }

    private boolean hasRequiresAdvanced() {
        try {
            def ctrl = controller
            if (ctrl != null) {
                Class<?> cls = ctrl.getClass()
                if (cls.isAnnotationPresent(RequiresAdvanced)) {
                    return true
                }
                String action = actionName
                if (action) {
                    def methods = cls.methods.findAll { it.name == action }
                    if (methods.any { it.isAnnotationPresent(RequiresAdvanced) }) {
                        return true
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false
    }
}
