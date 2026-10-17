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

package steakos.sales

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.events.DomainEvent
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames

/**
 * SalesAdvanced light CRM (PR-4b / OI-S08).
 */
class SalesCrmServiceSpec extends Specification implements DataTest {

    SalesCrmService service
    SalesCustomerService customerService
    SalesQuoteService quoteService
    SalesOrderService orderService
    DomainEventBusService bus
    List<DomainEvent> published

    Class[] getDomainClassesToMock() {
        [Customer, CustomerQuote, QuoteLine, SalesOrder, OrderLine, Opportunity, CrmActivity] as Class[]
    }

    def setup() {
        customerService = new SalesCustomerService()
        orderService = new SalesOrderService(salesCustomerService: customerService)
        quoteService = new SalesQuoteService(
                salesCustomerService: customerService,
                salesOrderService: orderService
        )
        bus = new DomainEventBusService()
        published = []
        bus.subscribe(DomainEventNames.SALES_OPPORTUNITY_WON) { DomainEvent e ->
            published << e
        }
        service = new SalesCrmService(
                salesCustomerService: customerService,
                salesQuoteService: quoteService,
                domainEventBusService: bus
        )
    }

    def cleanup() {
        bus.clearSubscribers()
    }

    def "create opportunity without customer"() {
        when:
        Opportunity opp = service.createOpportunity('npub-crm', [
                name             : 'New kitchen fit-out',
                stage            : 'LEAD',
                expectedAmountEur: 12000G,
                contactName      : 'Alex',
                source           : 'WEB'
        ])

        then:
        opp.id
        opp.stage == Opportunity.STAGE_LEAD
        opp.customerId == null
        opp.contactName == 'Alex'
        opp.active
        opp.expectedAmountEur == 12000G
    }

    def "create opportunity linked to customer"() {
        given:
        Customer c = customerService.createCustomer('npub-crm', [name: 'Buyer Co'])

        when:
        Opportunity opp = service.createOpportunity('npub-crm', [
                name      : 'Upsell',
                customerId: c.id,
                stage     : 'QUALIFIED'
        ])

        then:
        opp.customerId == c.id
        opp.stage == Opportunity.STAGE_QUALIFIED
    }

    def "invalid stage rejected"() {
        when:
        service.createOpportunity('npub-crm', [name: 'X', stage: 'NOPE'])

        then:
        thrown(IllegalArgumentException)
    }

    def "update stage and soft deactivate"() {
        given:
        Opportunity opp = service.createOpportunity('npub-crm', [name: 'Pipe'])

        when:
        service.updateOpportunity(opp.id, 'npub-crm', [stage: 'PROPOSAL', ownerName: 'Sam'])
        service.deactivate(opp.id, 'npub-crm')
        Map page = service.listOpportunitiesPage('npub-crm', true, 25, 0)

        then:
        Opportunity.get(opp.id).stage == Opportunity.STAGE_PROPOSAL
        Opportunity.get(opp.id).ownerName == 'Sam'
        !Opportunity.get(opp.id).active
        page.total == 0
    }

    def "mark lost"() {
        given:
        Opportunity opp = service.createOpportunity('npub-crm', [name: 'Gone'])

        when:
        service.markLost(opp.id, 'npub-crm', [description: 'budget cut'])

        then:
        Opportunity.get(opp.id).stage == Opportunity.STAGE_LOST
        Opportunity.get(opp.id).description.contains('budget cut')
    }

    def "activity CRUD on opportunity"() {
        given:
        Opportunity opp = service.createOpportunity('npub-crm', [name: 'Call me'])

        when:
        CrmActivity act = service.createActivity(opp.id, 'npub-crm', [
                kind       : 'CALL',
                channel    : 'PHONE',
                description: 'Intro call'
        ])

        then:
        act.id
        act.kind == 'CALL'
        act.channel == 'PHONE'

        when:
        service.updateActivity(act.id, 'npub-crm', [kind: 'MEETING', name: 'Site visit'])
        List acts = service.listActivities(opp.id, 'npub-crm')

        then:
        CrmActivity.get(act.id).kind == 'MEETING'
        CrmActivity.get(act.id).name == 'Site visit'
        acts.size() == 1
        service.toMap(opp, true).activityCount == 1

        when:
        service.deleteActivity(act.id, 'npub-crm')

        then:
        CrmActivity.countByOpportunity(opp) == 0
    }

    def "convert to quote emits sales.opportunity.won"() {
        given:
        Customer c = customerService.createCustomer('npub-win', [name: 'Winner GmbH'])
        Opportunity opp = service.createOpportunity('npub-win', [
                name             : 'Big deal',
                customerId       : c.id,
                stage            : 'NEGOTIATION',
                expectedAmountEur: 5000G
        ])

        when:
        Map result = service.convertToQuote(opp.id, 'npub-win', [:])

        then:
        !result.alreadyConverted
        result.quote.id
        result.opportunity.stage == Opportunity.STAGE_WON
        Opportunity.get(opp.id).resultingQuoteId == result.quote.id
        published.size() == 1
        published[0].name == DomainEventNames.SALES_OPPORTUNITY_WON
        published[0].payload.opportunityId == opp.id
        published[0].payload.quoteId == result.quote.id
        published[0].payload.customerId == c.id
    }

    def "convert requires customerId"() {
        given:
        Opportunity opp = service.createOpportunity('npub-crm', [name: 'No customer'])

        when:
        service.convertToQuote(opp.id, 'npub-crm', [:])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('customerId')
    }

    def "convert is idempotent when already converted"() {
        given:
        Customer c = customerService.createCustomer('npub-id', [name: 'Idem'])
        Opportunity opp = service.createOpportunity('npub-id', [
                name: 'Once', customerId: c.id, expectedAmountEur: 100G
        ])
        Map first = service.convertToQuote(opp.id, 'npub-id', [:])

        when:
        Map second = service.convertToQuote(opp.id, 'npub-id', [:])

        then:
        first.alreadyConverted == false
        second.alreadyConverted == true
        second.quote.id == first.quote.id
        // only one won event from first convert (second hits already-WON path before emit)
        published.size() == 1
    }

    def "cannot mutate WON opportunity"() {
        given:
        Customer c = customerService.createCustomer('npub-w', [name: 'W'])
        Opportunity opp = service.createOpportunity('npub-w', [name: 'Done', customerId: c.id])
        service.convertToQuote(opp.id, 'npub-w', [:])

        when:
        service.updateOpportunity(opp.id, 'npub-w', [stage: 'LEAD'])

        then:
        thrown(IllegalStateException)
    }

    def "npub scope isolation"() {
        given:
        Opportunity opp = service.createOpportunity('npub-a', [name: 'A only'])

        when:
        service.getOpportunity(opp.id, 'npub-b')

        then:
        thrown(IllegalArgumentException)
    }

    def "list filters by stage and openOnly"() {
        given:
        service.createOpportunity('npub-f', [name: 'L1', stage: 'LEAD'])
        service.createOpportunity('npub-f', [name: 'Q1', stage: 'QUALIFIED'])
        Customer c = customerService.createCustomer('npub-f', [name: 'C'])
        Opportunity won = service.createOpportunity('npub-f', [name: 'W1', customerId: c.id])
        service.convertToQuote(won.id, 'npub-f', [amountEur: 10G])

        when:
        Map leads = service.listOpportunitiesPage('npub-f', true, 25, 0, null, 'LEAD', null, false)
        Map open = service.listOpportunitiesPage('npub-f', true, 25, 0, null, null, null, true)

        then:
        leads.total == 1
        leads.items[0].name == 'L1'
        open.total == 2
        open.items*.stage.every { it in Opportunity.OPEN_STAGES }
    }

    def "Advanced route catalog covers crm API"() {
        expect:
        steakos.configuration.AdvancedRouteCatalog.isAdvancedPath('/api/sales/crm')
        steakos.configuration.AdvancedRouteCatalog.isAdvancedPath('/api/sales/crm/opportunities')
        steakos.configuration.AdvancedRouteCatalog.isAdvancedPath('/api/sales/crm/opportunities/12/convert')
        steakos.configuration.AdvancedRouteCatalog.isAdvancedPath('/api/sales/crm/activities/3')
        steakos.configuration.AdvancedRouteCatalog.isAdvancedPath('/sales/crm')
    }
}
