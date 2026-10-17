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

import steakos.configuration.TemplateTiers

/**
 * GRRCBasics metal gate (G7 / OI-SI03). Pure tier rules — does not open metal selection.
 *
 * <ul>
 *   <li>SILVER / GOLD — GRRC Basics allowed (fixed pack)</li>
 *   <li>IRON — eval dump may see built GRRC APIs</li>
 *   <li>BRONZE — GRRC refused</li>
 *   <li>COPPER — GRRC refused</li>
 * </ul>
 *
 * GRRC is Basics (not Advanced); tier allowlist matches Silver+ / Iron eval.
 * Does not mutate {@link TemplateTiers#OPEN_FOR_METAL_SELECTION}.
 */
final class GrrcMetalPolicy {

    private GrrcMetalPolicy() {}

    /** Tiers that may use GRRCBasics surfaces. */
    static final List<String> GRRC_ALLOWED_TIERS = [
            TemplateTiers.IRON,
            TemplateTiers.SILVER,
            TemplateTiers.GOLD
    ].asImmutable()

    static boolean allowsGrrc(String tier) {
        String t = TemplateTiers.normalize(tier)
        GRRC_ALLOWED_TIERS.contains(t)
    }

    /** True when GRRC must be hidden/refused (Bronze, Copper, unknown→Bronze). */
    static boolean refusesGrrc(String tier) {
        !allowsGrrc(tier)
    }

    static String denialCode() {
        'GRRC_REQUIRES_SILVER'
    }

    static String denialMessage(String tier) {
        String t = TemplateTiers.normalize(tier)
        "GRRC Basics require Silver or higher (tenant tier=${t})"
    }
}
