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

import steakos.configuration.TemplateTiers

/**
 * StakeholderBasics metal gate (G7 / OI-SI03). Pure tier rules — does not open metal selection.
 *
 * <ul>
 *   <li>SILVER / GOLD — StakeholderBasics allowed (fixed pack)</li>
 *   <li>IRON — eval dump may exercise Stakeholder APIs</li>
 *   <li>BRONZE / COPPER — refused</li>
 * </ul>
 */
final class StakeholderMetalPolicy {

    private StakeholderMetalPolicy() {}

    /** Tiers that may use StakeholderBasics surfaces. */
    static final List<String> ALLOWED_TIERS = [
            TemplateTiers.IRON,
            TemplateTiers.SILVER,
            TemplateTiers.GOLD
    ].asImmutable()

    static boolean allows(String tier) {
        String t = TemplateTiers.normalize(tier)
        ALLOWED_TIERS.contains(t)
    }

    /** True when StakeholderBasics must be hidden/refused (Bronze, Copper, unknown→Bronze). */
    static boolean refuses(String tier) {
        !allows(tier)
    }

    static String denialCode() {
        'STAKEHOLDER_REQUIRES_SILVER'
    }

    static String denialMessage(String tier) {
        String t = TemplateTiers.normalize(tier)
        "StakeholderBasics requires Silver or higher (tenant tier=${t})"
    }
}
