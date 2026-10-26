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

import spock.lang.Specification
import spock.lang.Unroll

class AdvancedMetalPolicySpec extends Specification {

    @Unroll
    def "allowsAdvanced(#tier) -> #allowed"() {
        expect:
        AdvancedMetalPolicy.allowsAdvanced(tier) == allowed
        AdvancedMetalPolicy.refusesAdvanced(tier) == !allowed

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

    def "OPEN_FOR_METAL_SELECTION includes Silver after PR-17; Gold stays closed"() {
        expect:
        TemplateTiers.OPEN_FOR_METAL_SELECTION == [TemplateTiers.BRONZE, TemplateTiers.SILVER]
        TemplateTiers.isOpenForMetalSelection(TemplateTiers.SILVER)
        !TemplateTiers.isOpenForMetalSelection(TemplateTiers.GOLD)
    }

    @Unroll
    def "AdvancedRouteCatalog recognizes #path"() {
        expect:
        AdvancedRouteCatalog.isAdvancedPath(path) == hit

        where:
        path                              | hit
        '/api/sales/pricelists'           | true
        '/api/sales/pricelists/1'         | true
        '/api/sales/pricelists/resolve'   | true
        '/api/sales/crm'                  | true
        '/api/sales/crm/opportunities'    | true
        '/api/sales/crm/opportunities/1'  | true
        '/api/sales/opportunities'        | true
        '/sales/crm'                      | true
        '/sales/opportunities'            | true
        '/sales/opportunities/1'          | true
        '/api/finance/collections'        | true
        '/api/finance/match'              | true
        '/api/finance/match/candidates'   | true
        '/finance/match'                  | true
        '/api/sales/customers'            | false
        '/api/finance/invoices'           | false
        '/api/setup/status'               | false
        null                              | false
    }
}
