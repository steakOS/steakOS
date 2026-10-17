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

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.configuration.CompanyConfigService
import steakos.events.DomainEvent
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames

/**
 * StakeholderBasics thin kernel: company-scoped party/comms CRUD + metal gate.
 */
class StakeholderBasicsServiceSpec extends Specification implements DataTest {

    StakeholderBasicsService service
    DomainEventBusService bus

    Class[] getDomainClassesToMock() {
        [StakeholderParty, CommsLog] as Class[]
    }

    def setup() {
        bus = new DomainEventBusService()
        service = new StakeholderBasicsService(domainEventBusService: bus)
    }

    def "party create list update and tenant isolation"() {
        when:
        StakeholderParty a = service.createParty('npub-a', [
                name: 'City Council', partyKind: 'REGULATOR', ethicsNote: 'Public hearing'
        ])
        service.createParty('npub-b', [name: 'Other tenant party'])

        then:
        a.status == 'ACTIVE'
        a.partyKind == 'REGULATOR'
        service.listPartiesPage('npub-a').total == 1
        service.listPartiesPage('npub-b').total == 1

        when:
        StakeholderParty updated = service.updateParty(a.id, 'npub-a', [
                status: 'WATCH', contactEmail: 'pr@ex.com'
        ])

        then:
        updated.status == 'WATCH'
        updated.contactEmail == 'pr@ex.com'
        updated.ethicsNote == 'Public hearing'

        when:
        service.getParty(a.id, 'npub-b')

        then:
        thrown(IllegalArgumentException)
    }

    def "comms log requires party and publishes event when LOGGED"() {
        given:
        DomainEvent seen = null
        bus.subscribe(DomainEventNames.STAKEHOLDER_COMMUNICATION_LOGGED) { DomainEvent e -> seen = e }
        StakeholderParty p = service.createParty('npub-ev', [name: 'Partner X', partyKind: 'PARTNER'])

        when:
        CommsLog c = service.createComms('npub-ev', [
                stakeholderPartyId: p.id,
                subject           : 'Q3 briefing',
                channel           : 'MEETING',
                direction         : 'OUT',
                summary           : 'Discussed roadmap'
        ])

        then:
        c.id
        c.stakeholderPartyId == p.id
        c.status == 'LOGGED'
        seen != null
        seen.name == DomainEventNames.STAKEHOLDER_COMMUNICATION_LOGGED
        seen.npubOwner == 'npub-ev'
        seen.payload.commsLogId == c.id
        seen.payload.stakeholderPartyId == p.id

        when:
        Map page = service.listCommsPage('npub-ev', p.id)

        then:
        page.total == 1
        service.toCommsMap(page.items[0]).subject == 'Q3 briefing'
    }

    def "cannot delete party with linked comms; DRAFT skips event"() {
        given:
        DomainEvent seen = null
        bus.subscribe(DomainEventNames.STAKEHOLDER_COMMUNICATION_LOGGED) { DomainEvent e -> seen = e }
        StakeholderParty p = service.createParty('npub-d', [name: 'NGO Y', partyKind: 'NGO'])

        when:
        service.createComms('npub-d', [
                stakeholderPartyId: p.id, subject: 'Intro', status: 'DRAFT'
        ])

        then:
        seen == null

        when:
        service.deleteParty(p.id, 'npub-d')

        then:
        thrown(IllegalArgumentException)
        StakeholderParty.get(p.id) != null
    }

    def "name and subject required"() {
        when:
        service.createParty('npub-x', [partyKind: 'OTHER'])

        then:
        thrown(IllegalArgumentException)

        when:
        StakeholderParty p = service.createParty('npub-x', [name: 'Ok'])
        service.createComms('npub-x', [stakeholderPartyId: p.id])

        then:
        thrown(IllegalArgumentException)
    }

    def "gate service refuses Bronze and allows Silver / Iron"() {
        given:
        def config = Stub(CompanyConfigService) {
            getString('npub-bronze', 'template.tier', _) >> 'BRONZE'
            getString('npub-silver', 'template.tier', _) >> 'SILVER'
            getString('npub-iron', 'template.tier', _) >> 'IRON'
        }
        StakeholderGateService gate = new StakeholderGateService(companyConfigService: config)

        expect:
        !gate.allowsStakeholderFor('npub-bronze')
        gate.allowsStakeholderFor('npub-silver')
        gate.allowsStakeholderFor('npub-iron')

        when:
        gate.assertStakeholderAllowed('npub-bronze')

        then:
        StakeholderGateException ex = thrown()
        ex.code == 'STAKEHOLDER_REQUIRES_SILVER'
        ex.tier == 'BRONZE'
    }
}
