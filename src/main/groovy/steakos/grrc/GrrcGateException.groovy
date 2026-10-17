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
 * Thrown when GRRC APIs/routes are hit on Bronze/Copper (G7).
 */
class GrrcGateException extends RuntimeException {

    final String tier
    final String code

    GrrcGateException(String tier) {
        super(GrrcMetalPolicy.denialMessage(tier))
        this.tier = TemplateTiers.normalize(tier)
        this.code = GrrcMetalPolicy.denialCode()
    }
}
