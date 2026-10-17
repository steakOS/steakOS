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

package steakos.configuration

import grails.gorm.transactions.Transactional
import jakarta.servlet.http.HttpSession

/**
 * Resolves tenant metal and enforces Advanced-off-Bronze (OI-SI05).
 * Controllers / Wave 2 services call {@link #assertAdvancedAllowed} or {@link #allowsAdvancedFor}.
 */
@Transactional(readOnly = true)
class AdvancedGateService {

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

    boolean allowsAdvancedFor(String npubOwner) {
        AdvancedMetalPolicy.allowsAdvanced(resolveTier(npubOwner))
    }

    boolean allowsAdvancedFor(HttpSession session, Object source = null) {
        AdvancedMetalPolicy.allowsAdvanced(resolveTier(session, source))
    }

    /**
     * @throws AdvancedGateException when Bronze/Copper (or unknown→Bronze)
     */
    void assertAdvancedAllowed(String npubOwner) {
        String tier = resolveTier(npubOwner)
        if (AdvancedMetalPolicy.refusesAdvanced(tier)) {
            throw new AdvancedGateException(tier)
        }
    }

    void assertAdvancedAllowed(HttpSession session, Object source = null) {
        String npub = tenantNpubService?.resolve(session, source)
        assertAdvancedAllowed(npub)
    }

    /** JSON body for 403 responses. */
    Map denialBody(String tier) {
        [
                error  : AdvancedMetalPolicy.denialMessage(tier),
                code   : AdvancedMetalPolicy.denialCode(),
                tier   : TemplateTiers.normalize(tier)
        ]
    }
}
