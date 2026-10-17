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

import spock.lang.Specification
import spock.lang.Unroll
import steakos.configuration.TemplateTiers

class StakeholderMetalPolicySpec extends Specification {

    @Unroll
    def "allows(#tier) -> #allowed"() {
        expect:
        StakeholderMetalPolicy.allows(tier) == allowed
        StakeholderMetalPolicy.refuses(tier) == !allowed

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

    def "denial code and message name StakeholderBasics"() {
        expect:
        StakeholderMetalPolicy.denialCode() == 'STAKEHOLDER_REQUIRES_SILVER'
        StakeholderMetalPolicy.denialMessage(TemplateTiers.BRONZE).contains('Silver')
        StakeholderMetalPolicy.denialMessage(TemplateTiers.BRONZE).contains('BRONZE')
    }

    def "OPEN_FOR_METAL_SELECTION remains Bronze-only (PR-17 gate)"() {
        expect:
        TemplateTiers.OPEN_FOR_METAL_SELECTION == [TemplateTiers.BRONZE]
        !TemplateTiers.isOpenForMetalSelection(TemplateTiers.SILVER)
    }

    def "StakeholderGateException carries code and tier"() {
        when:
        def ex = new StakeholderGateException(TemplateTiers.BRONZE)

        then:
        ex.code == 'STAKEHOLDER_REQUIRES_SILVER'
        ex.tier == TemplateTiers.BRONZE
        ex.message.contains('Silver')
    }
}
