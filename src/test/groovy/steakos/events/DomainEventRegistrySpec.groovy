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

package steakos.events

import spock.lang.Specification
import spock.lang.Unroll

class DomainEventRegistrySpec extends Specification {

    def "catalog includes Silver contract names for Sales/Finance/messaging"() {
        expect:
        DomainEventRegistry.isRegistered(DomainEventNames.SALES_ORDER_CONFIRMED)
        DomainEventRegistry.isRegistered(DomainEventNames.SALES_OPPORTUNITY_WON)
        DomainEventRegistry.isRegistered(DomainEventNames.SALES_PRICELIST_UPDATED)
        DomainEventRegistry.isRegistered(DomainEventNames.FINANCE_MATCH_COMPLETED)
        DomainEventRegistry.isRegistered(DomainEventNames.MESSAGING_OUTBOUND_SENT)
        DomainEventRegistry.knownNames().size() >= 13
    }

    def "requireKnown rejects unknown names"() {
        when:
        DomainEventRegistry.requireKnown('not.a.real.event')

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    def "intended consumers for #name"() {
        expect:
        DomainEventRegistry.intendedConsumers(name).contains(consumer)

        where:
        name                                      | consumer
        DomainEventNames.SALES_ORDER_CONFIRMED    | 'finance'
        DomainEventNames.SALES_PRICELIST_UPDATED  | 'audit'
        DomainEventNames.FINANCE_INVOICE_OVERDUE  | 'messaging'
        DomainEventNames.SCM_RECEIPT_POSTED       | 'finance'
    }
}
