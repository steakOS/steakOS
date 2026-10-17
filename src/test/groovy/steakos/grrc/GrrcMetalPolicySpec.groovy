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

import spock.lang.Specification
import spock.lang.Unroll
import steakos.configuration.TemplateTiers

class GrrcMetalPolicySpec extends Specification {

    @Unroll
    def "allowsGrrc(#tier) -> #allowed"() {
        expect:
        GrrcMetalPolicy.allowsGrrc(tier) == allowed
        GrrcMetalPolicy.refusesGrrc(tier) == !allowed

        where:
        tier                         | allowed
        TemplateTiers.IRON           | true
        TemplateTiers.SILVER         | true
        TemplateTiers.GOLD           | true
        'silver'                     | true
        TemplateTiers.BRONZE         | false
        TemplateTiers.COPPER         | false
        null                         | false
        ''                           | false
        'nope'                       | false
    }

    def "OPEN_FOR_METAL_SELECTION remains Bronze-only (PR-17 gate)"() {
        expect:
        TemplateTiers.OPEN_FOR_METAL_SELECTION == [TemplateTiers.BRONZE]
        !TemplateTiers.isOpenForMetalSelection(TemplateTiers.SILVER)
        TemplateTiers.SILVER_FAMILIES.contains('grrc')
        !TemplateTiers.BRONZE_FAMILIES.contains('grrc')
    }

    def "denial code and message"() {
        expect:
        GrrcMetalPolicy.denialCode() == 'GRRC_REQUIRES_SILVER'
        GrrcMetalPolicy.denialMessage(TemplateTiers.BRONZE).contains('BRONZE')
        GrrcMetalPolicy.denialMessage(TemplateTiers.BRONZE).contains('Silver')
    }
}
