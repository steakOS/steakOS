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

/**
 * Advanced-off-Bronze policy (OI-SI05). Pure tier rules — does not open metal selection.
 *
 * <ul>
 *   <li>SILVER / GOLD — Advanced allowed (fixed pack)</li>
 *   <li>IRON — eval dump may see built Advanced desks</li>
 *   <li>BRONZE — Advanced refused</li>
 *   <li>COPPER — Advanced refused (honesty: smaller than Bronze)</li>
 * </ul>
 *
 * Does not mutate {@link TemplateTiers#OPEN_FOR_METAL_SELECTION}.
 */
final class AdvancedMetalPolicy {

    private AdvancedMetalPolicy() {}

    /** Tiers that may use SalesAdvanced / FinanceAdvanced surfaces. */
    static final List<String> ADVANCED_ALLOWED_TIERS = [
            TemplateTiers.IRON,
            TemplateTiers.SILVER,
            TemplateTiers.GOLD
    ].asImmutable()

    static boolean allowsAdvanced(String tier) {
        String t = TemplateTiers.normalize(tier)
        ADVANCED_ALLOWED_TIERS.contains(t)
    }

    /** True when Advanced must be hidden/refused (Bronze, Copper, unknown→Bronze). */
    static boolean refusesAdvanced(String tier) {
        !allowsAdvanced(tier)
    }

    static String denialCode() {
        'ADVANCED_REQUIRES_SILVER'
    }

    static String denialMessage(String tier) {
        String t = TemplateTiers.normalize(tier)
        "Advanced features require Silver or higher (tenant tier=${t})"
    }
}
