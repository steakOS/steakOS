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

class DomainEventBusServiceSpec extends Specification {

    DomainEventBusService bus

    def setup() {
        bus = new DomainEventBusService()
    }

    def cleanup() {
        bus.clearSubscribers()
    }

    def "publish fans out to subscribers"() {
        given:
        List seen = []
        bus.subscribe(DomainEventNames.SALES_ORDER_CONFIRMED) { DomainEvent e ->
            seen << e.payload.orderId
        }

        when:
        DomainEvent published = bus.publish(
                DomainEventNames.SALES_ORDER_CONFIRMED, 'sales', 'company:demo', [orderId: 42])

        then:
        published.eventId
        seen == [42]
        bus.subscriberCount(DomainEventNames.SALES_ORDER_CONFIRMED) == 1
    }

    def "unsubscribe stops delivery"() {
        given:
        int count = 0
        Closure off = bus.subscribe(DomainEventNames.MESSAGING_OUTBOUND_SENT) { DomainEvent e ->
            count++
        }

        when:
        bus.publish(DomainEventNames.MESSAGING_OUTBOUND_SENT, 'messaging', null, [channel: 'email'])
        off.call()
        bus.publish(DomainEventNames.MESSAGING_OUTBOUND_SENT, 'messaging', null, [channel: 'email'])

        then:
        count == 1
    }

    def "handler exceptions do not block other subscribers"() {
        given:
        List ok = []
        bus.subscribe(DomainEventNames.FINANCE_INVOICE_SETTLED) { DomainEvent e ->
            throw new RuntimeException('boom')
        }
        bus.subscribe(DomainEventNames.FINANCE_INVOICE_SETTLED) { DomainEvent e ->
            ok << e.name
        }

        when:
        bus.publish(DomainEventNames.FINANCE_INVOICE_SETTLED, 'finance', 'n1', [:])

        then:
        ok == [DomainEventNames.FINANCE_INVOICE_SETTLED]
    }

    def "publish rejects unknown event names"() {
        when:
        bus.publish('unknown.event', 'test')

        then:
        thrown(IllegalArgumentException)
    }
}
