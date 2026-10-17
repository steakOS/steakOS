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

package steakos.stakeholder

import grails.converters.JSON
import groovy.util.logging.Slf4j
import steakos.configuration.TemplateTiers

/**
 * Refuses StakeholderBasics APIs for Bronze/Copper tenants (G7).
 * Covers dedicated /api/stakeholder/* paths.
 */
@Slf4j
class StakeholderGateInterceptor {

    StakeholderGateService stakeholderGateService

    StakeholderGateInterceptor() {
        match(uri: '/api/stakeholder/**')
        match(uri: '/api/stakeholder')
    }

    boolean before() {
        String tier = TemplateTiers.DEFAULT_OPERATOR_TIER
        try {
            tier = stakeholderGateService?.resolveTier(session) ?: tier
        } catch (Exception e) {
            log.debug("stakeholder gate tier resolve failed: ${e.message}")
        }
        if (StakeholderMetalPolicy.allows(tier)) {
            return true
        }
        response.status = 403
        response.contentType = 'application/json;charset=UTF-8'
        Map body = stakeholderGateService?.denialBody(tier) ?: [
                error: StakeholderMetalPolicy.denialMessage(tier),
                code : StakeholderMetalPolicy.denialCode(),
                tier : TemplateTiers.normalize(tier)
        ]
        render body as JSON
        return false
    }
}
