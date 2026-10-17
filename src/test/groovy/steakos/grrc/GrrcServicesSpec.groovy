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

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.events.DomainEvent
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames

/**
 * GRRCBasics thin kernel: risk + compliance company-scoped CRUD.
 */
class GrrcServicesSpec extends Specification implements DataTest {

    GrrcRiskService riskService
    GrrcComplianceService complianceService
    DomainEventBusService bus

    Class[] getDomainClassesToMock() {
        [RiskItem, ComplianceItem] as Class[]
    }

    def setup() {
        bus = new DomainEventBusService()
        riskService = new GrrcRiskService(domainEventBusService: bus)
        complianceService = new GrrcComplianceService()
    }

    def "risk create list update close and tenant isolation"() {
        when:
        RiskItem a = riskService.create('npub-a', [
                name: 'Supplier delay', severity: 'HIGH', likelihood: 'MEDIUM'
        ])
        riskService.create('npub-b', [name: 'Other tenant risk', severity: 'LOW'])

        then:
        a.status == 'OPEN'
        a.severity == 'HIGH'
        riskService.listPage('npub-a').total == 1
        riskService.listPage('npub-b').total == 1

        when:
        RiskItem updated = riskService.update(a.id, 'npub-a', [
                status: 'MITIGATING', treatmentNote: 'Dual source'
        ])

        then:
        updated.status == 'MITIGATING'
        updated.treatmentNote == 'Dual source'

        when:
        RiskItem closed = riskService.close(a.id, 'npub-a', [treatmentNote: 'Resolved'])

        then:
        closed.status == 'CLOSED'
        closed.treatmentNote == 'Resolved'

        when:
        riskService.get(a.id, 'npub-b')

        then:
        thrown(IllegalArgumentException)
    }

    def "risk opened publishes domain event"() {
        given:
        DomainEvent seen = null
        bus.subscribe(DomainEventNames.GRRC_RISK_OPENED) { DomainEvent e -> seen = e }

        when:
        RiskItem r = riskService.create('npub-ev', [name: 'Fire risk', severity: 'CRITICAL'])

        then:
        seen != null
        seen.name == DomainEventNames.GRRC_RISK_OPENED
        seen.npubOwner == 'npub-ev'
        seen.payload.riskId == r.id
    }

    def "compliance create list update delete"() {
        when:
        ComplianceItem c = complianceService.create('npub-c', [
                name: 'Privacy policy', itemKind: 'POLICY', status: 'DRAFT',
                effectiveFrom: '2026-01-15'
        ])

        then:
        c.itemKind == 'POLICY'
        c.status == 'DRAFT'
        c.effectiveFrom != null
        complianceService.listPage('npub-c').total == 1

        when:
        ComplianceItem active = complianceService.update(c.id, 'npub-c', [status: 'ACTIVE'])
        Map m = complianceService.toMap(active)

        then:
        active.status == 'ACTIVE'
        m.name == 'Privacy policy'
        m.effectiveFrom != null

        when:
        complianceService.delete(c.id, 'npub-c')

        then:
        complianceService.listPage('npub-c').total == 0
    }

    def "name required on create"() {
        when:
        riskService.create('npub-x', [severity: 'LOW'])

        then:
        thrown(IllegalArgumentException)

        when:
        complianceService.create('npub-x', [:])

        then:
        thrown(IllegalArgumentException)
    }

    def "gate service refuses Bronze and allows Silver"() {
        given:
        def config = Stub(steakos.configuration.CompanyConfigService) {
            getString('npub-bronze', 'template.tier', _) >> 'BRONZE'
            getString('npub-silver', 'template.tier', _) >> 'SILVER'
            getString('npub-iron', 'template.tier', _) >> 'IRON'
        }
        GrrcGateService gate = new GrrcGateService(companyConfigService: config)

        expect:
        !gate.allowsGrrcFor('npub-bronze')
        gate.allowsGrrcFor('npub-silver')
        gate.allowsGrrcFor('npub-iron')

        when:
        gate.assertGrrcAllowed('npub-bronze')

        then:
        GrrcGateException ex = thrown()
        ex.code == 'GRRC_REQUIRES_SILVER'
        ex.tier == 'BRONZE'
    }
}
