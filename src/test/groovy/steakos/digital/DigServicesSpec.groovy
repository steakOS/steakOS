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

package steakos.digital

import grails.testing.gorm.DataTest
import spock.lang.Specification

/**
 * Digital kernel: system, solution, cmdb-lite, light ITSM, change.
 */
class DigServicesSpec extends Specification implements DataTest {

    DigSystemService systemService
    DigDataService dataService
    DigSolutionService solutionService
    DigChangeService changeService
    DigCmdbService cmdbService
    DigSupportService supportService

    Class[] getDomainClassesToMock() {
        // Fully qualify System — avoids java.lang.System
        [steakos.digital.System, Data, Solution, ITChange, ConfigItem, ItTicket] as Class[]
    }

    def setup() {
        systemService = new DigSystemService()
        dataService = new DigDataService()
        solutionService = new DigSolutionService(digSystemService: systemService)
        changeService = new DigChangeService()
        cmdbService = new DigCmdbService(
                digSystemService: systemService,
                digSolutionService: solutionService
        )
        supportService = new DigSupportService()
    }

    def "system and solution with optional system link"() {
        when:
        def sys = systemService.create('npub-d', [name: 'steakOS', systemKey: 'steakos', environment: 'PROD'])
        def sol = solutionService.create('npub-d', [
                name: 'BTCPay', integrationType: 'BTCPAY', systemId: sys.id
        ])
        solutionService.activate(sol.id, 'npub-d')

        then:
        sys.systemKey == 'steakos'
        sol.systemId == sys.id
        solutionService.get(sol.id, 'npub-d').lifecycleStatus == 'ACTIVE'
    }

    def "cmdb ci and internal ticket lifecycle"() {
        given:
        def sys = systemService.create('npub-c', [name: 'App host'])
        def ci = cmdbService.createCi('npub-c', [name: 'web-1', ciKind: 'HOST', systemId: sys.id])

        when:
        ItTicket t = supportService.openTicket('npub-c', [
                name: 'Disk full', ticketKind: 'INCIDENT', configItemId: ci.id
        ])
        supportService.startWork(t.id, 'npub-c')
        supportService.resolve(t.id, 'npub-c')
        ItTicket closed = supportService.close(t.id, 'npub-c')

        then:
        ci.systemId == sys.id
        closed.ticketStatus == 'CLOSED'
        closed.ticketNumber
    }

    def "CHANGE ticket kind for finance policy"() {
        when:
        ItTicket t = supportService.openTicket('npub-pol', [
                name: '[Finance policy] Posting rules save (CASH)',
                ticketKind: 'CHANGE',
                description: 'PDR-027 D6 audit',
        ])

        then:
        t.ticketKind == 'CHANGE'
        t.ticketStatus == 'OPEN'
        t.ticketNumber
    }

    def "change kind and complete"() {
        when:
        ITChange c = changeService.create('npub-x', [
                name: 'Nightly backup', changeKind: 'BACKUP', riskLevel: 'LOW'
        ])
        changeService.start(c.id, 'npub-x')
        ITChange done = changeService.complete(c.id, 'npub-x')

        then:
        done.changeKind == 'BACKUP'
        done.changeStatus == 'DONE'
    }

    def "data classification"() {
        when:
        Data d = dataService.create('npub-y', [
                name: 'Customer PII', dataClass: 'CONFIDENTIAL', retentionDays: 730
        ])

        then:
        d.dataClass == 'CONFIDENTIAL'
        d.retentionDays == 730
    }
}
