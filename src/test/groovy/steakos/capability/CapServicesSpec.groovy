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

package steakos.capability

import grails.testing.gorm.DataTest
import spock.lang.Specification

/**
 * Capability kernel (CAP0–CAP8): process, PPP, quality, org change, knowledge, standard.
 */
class CapServicesSpec extends Specification implements DataTest {

    CapProcessService processService
    CapPortfolioService portfolioService
    CapProjectService projectService
    CapQualityService qualityService
    CapChangeService changeService
    CapKnowledgeService knowledgeService
    CapStandardService standardService

    Class[] getDomainClassesToMock() {
        [
                steakos.capability.Process, ProcessStep, Procedure,
                Portfolio, Program, Project, ProjectItem,
                Quality, QualityItem, Change, ChangeItem,
                KnowledgeArea, Standard
        ] as Class[]
    }

    def setup() {
        processService = new CapProcessService()
        portfolioService = new CapPortfolioService()
        projectService = new CapProjectService(capPortfolioService: portfolioService)
        qualityService = new CapQualityService()
        changeService = new CapChangeService()
        knowledgeService = new CapKnowledgeService()
        standardService = new CapStandardService()
    }

    def "process activate with steps and predecessor"() {
        when:
        def p = processService.createProcess('npub-p', [name: 'Order to cash', processCode: 'O2C'])
        def s1 = processService.addStep('npub-p', [processId: p.id, name: 'Quote'])
        def s2 = processService.addStep('npub-p', [processId: p.id, name: 'Order', predecessorId: s1.id])
        processService.activate(p.id, 'npub-p')

        then:
        processService.getProcess(p.id, 'npub-p').processStatus == 'ACTIVE'
        s2.predecessorId == s1.id
        processService.listSteps('npub-p', p.id).size() == 2
    }

    def "flexible PPP project may stand alone and lifecycle"() {
        when:
        def proj = projectService.createProject('npub-j', [name: 'Solo launch'])
        projectService.start(proj.id, 'npub-j')
        projectService.addItem('npub-j', [projectId: proj.id, name: 'MVP', taskId: 42L])
        def done = projectService.complete(proj.id, 'npub-j')

        then:
        done.projectStatus == 'DONE'
        !done.programId
        projectService.listItems('npub-j', proj.id)[0].taskId == 42L
    }

    def "portfolio program project hierarchy optional"() {
        when:
        def port = portfolioService.create('npub-h', [name: '2026 Portfolio'])
        def prog = projectService.createProgram('npub-h', [name: 'Growth', portfolioId: port.id])
        def proj = projectService.createProject('npub-h', [
                name: 'CRM rollout', programId: prog.id, portfolioId: port.id
        ])

        then:
        proj.programId == prog.id
        proj.portfolioId == port.id
        portfolioService.get(port.id, 'npub-h').portfolioStatus == 'ACTIVE'
    }

    def "quality NCR open and close"() {
        when:
        def q = qualityService.createQuality('npub-q', [name: 'ISO register', qualityCode: 'Q-1'])
        def item = qualityService.addItem('npub-q', [qualityId: q.id, name: 'Scratch', itemKind: 'NCR'])
        def closed = qualityService.closeItem(item.id, 'npub-q')

        then:
        closed.itemKind == 'NCR'
        closed.itemStatus == 'CLOSED'
    }

    def "org change implement and close (not IT change)"() {
        when:
        Change c = changeService.createChange('npub-c', [name: 'Reorg sales', impactNote: 'Medium'])
        changeService.implement(c.id, 'npub-c')
        changeService.addItem('npub-c', [changeId: c.id, name: 'Comms plan'])
        Change done = changeService.close(c.id, 'npub-c')

        then:
        done.changeStatus == 'DONE'
        changeService.listItems('npub-c', c.id).size() == 1
    }

    def "knowledge tree and standard KPI"() {
        when:
        def root = knowledgeService.create('npub-k', [name: 'Ops', areaCode: 'OPS'])
        def child = knowledgeService.create('npub-k', [name: 'SCM', parentId: root.id, areaCode: 'OPS-SCM'])
        def std = standardService.create('npub-k', [
                name: 'OTIF', metricCode: 'OTIF', targetValue: '95', unit: '%'
        ])

        then:
        knowledgeService.listChildren('npub-k', root.id)*.id == [child.id]
        std.targetValue == '95'
        standardService.list('npub-k').size() == 1
    }
}
