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

import grails.gorm.transactions.Transactional
import jakarta.servlet.http.HttpSession
import steakos.configuration.CompanyConfigService
import steakos.configuration.TemplateTiers
import steakos.configuration.TenantNpubService

/**
 * Resolves tenant metal and enforces Stakeholder-off-Bronze (G7).
 * Controllers / interceptor call {@link #assertStakeholderAllowed}.
 */
@Transactional(readOnly = true)
class StakeholderGateService {

    CompanyConfigService companyConfigService
    TenantNpubService tenantNpubService

    String resolveTier(String npubOwner) {
        String npub = npubOwner?.trim()
        if (!npub) {
            return TemplateTiers.DEFAULT_OPERATOR_TIER
        }
        String raw = companyConfigService?.getString(npub, 'template.tier', TemplateTiers.DEFAULT_OPERATOR_TIER)
        TemplateTiers.normalize(raw)
    }

    String resolveTier(HttpSession session, Object source = null) {
        String npub = tenantNpubService?.resolve(session, source)
        resolveTier(npub)
    }

    boolean allowsStakeholderFor(String npubOwner) {
        StakeholderMetalPolicy.allows(resolveTier(npubOwner))
    }

    boolean allowsStakeholderFor(HttpSession session, Object source = null) {
        StakeholderMetalPolicy.allows(resolveTier(session, source))
    }

    /**
     * @throws StakeholderGateException when Bronze/Copper (or unknown→Bronze)
     */
    void assertStakeholderAllowed(String npubOwner) {
        String tier = resolveTier(npubOwner)
        if (StakeholderMetalPolicy.refuses(tier)) {
            throw new StakeholderGateException(tier)
        }
    }

    void assertStakeholderAllowed(HttpSession session, Object source = null) {
        String npub = tenantNpubService?.resolve(session, source)
        assertStakeholderAllowed(npub)
    }

    /** JSON body for 403 responses. */
    Map denialBody(String tier) {
        [
                error: StakeholderMetalPolicy.denialMessage(tier),
                code : StakeholderMetalPolicy.denialCode(),
                tier : TemplateTiers.normalize(tier)
        ]
    }
}
