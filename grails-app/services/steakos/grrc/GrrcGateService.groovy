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

package steakos.grrc

import grails.gorm.transactions.Transactional
import jakarta.servlet.http.HttpSession
import steakos.configuration.CompanyConfigService
import steakos.configuration.TemplateTiers
import steakos.configuration.TenantNpubService

/**
 * Resolves tenant metal and enforces GRRC-off-Bronze (G7).
 * Controllers / interceptor call {@link #assertGrrcAllowed}.
 */
@Transactional(readOnly = true)
class GrrcGateService {

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

    boolean allowsGrrcFor(String npubOwner) {
        GrrcMetalPolicy.allowsGrrc(resolveTier(npubOwner))
    }

    boolean allowsGrrcFor(HttpSession session, Object source = null) {
        GrrcMetalPolicy.allowsGrrc(resolveTier(session, source))
    }

    /**
     * @throws GrrcGateException when Bronze/Copper (or unknown→Bronze)
     */
    void assertGrrcAllowed(String npubOwner) {
        String tier = resolveTier(npubOwner)
        if (GrrcMetalPolicy.refusesGrrc(tier)) {
            throw new GrrcGateException(tier)
        }
    }

    void assertGrrcAllowed(HttpSession session, Object source = null) {
        String npub = tenantNpubService?.resolve(session, source)
        assertGrrcAllowed(npub)
    }

    /** JSON body for 403 responses. */
    Map denialBody(String tier) {
        [
                error: GrrcMetalPolicy.denialMessage(tier),
                code : GrrcMetalPolicy.denialCode(),
                tier : TemplateTiers.normalize(tier)
        ]
    }
}
