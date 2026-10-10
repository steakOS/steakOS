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

package steakos.agents

import grails.testing.gorm.DataTest
import spock.lang.Specification

/**
 * Agentic Resources kernel (AR0–AR8).
 */
class AgServicesSpec extends Specification implements DataTest {

    AgAgentService agentService
    AgHiveService hiveService
    AgApprovalService approvalService
    AgMemoryService memoryService
    AgLinkService linkService

    Class[] getDomainClassesToMock() {
        [Agent, Hive, Approval, Memory, AgentHumanLink] as Class[]
    }

    def setup() {
        agentService = new AgAgentService()
        hiveService = new AgHiveService(agAgentService: agentService)
        approvalService = new AgApprovalService(agAgentService: agentService)
        memoryService = new AgMemoryService(agAgentService: agentService)
        linkService = new AgLinkService(agAgentService: agentService)
    }

    def "seed core agents is idempotent"() {
        when:
        def first = agentService.seedCoreAgents('npub-a')
        def second = agentService.seedCoreAgents('npub-a')

        then:
        first.size() == 5
        second.size() == 5
        agentService.list('npub-a').size() == 5
        agentService.getByKey('npub-a', 'bitcoin-nostr').runtimeKind == 'HERMES'
        agentService.getByKey('npub-a', 'optimization-suggester').agentStatus == 'ACTIVE'
    }

    def "multi-runtime create and persona fields"() {
        when:
        def a = agentService.create('npub-b', [
                name: 'Custom bot', agentKey: 'custom-1', runtimeKind: 'CUSTOM',
                endpointRef: 'https://example.local/agent',
                taskContext: 'Do X', soul: 'Be Y'
        ])

        then:
        a.runtimeKind == 'CUSTOM'
        a.taskContext == 'Do X'
        a.soul == 'Be Y'
    }

    def "hive master required to activate and assign members"() {
        given:
        def master = agentService.create('npub-h', [name: 'Master', agentKey: 'master-1', agentStatus: 'ACTIVE'])
        def worker = agentService.create('npub-h', [name: 'Worker', agentKey: 'worker-1', agentStatus: 'ACTIVE'])

        when:
        def hive = hiveService.create('npub-h', [name: 'Finance Hive', apqcAreaCode: '9.0', hiveMasterAgentId: master.id])
        hiveService.assignAgent(hive.id, 'npub-h', worker.id)
        def active = hiveService.activate(hive.id, 'npub-h')

        then:
        active.hiveStatus == 'ACTIVE'
        active.hiveMasterAgentId == master.id
        hiveService.listMembers('npub-h', hive.id).size() == 2
        agentService.get(master.id, 'npub-h').hiveId == hive.id
    }

    def "cannot activate hive without master"() {
        when:
        def hive = hiveService.create('npub-x', [name: 'Empty'])
        hiveService.activate(hive.id, 'npub-x')

        then:
        thrown(IllegalStateException)
    }

    def "approval submit approve reject flow"() {
        given:
        def agent = agentService.create('npub-p', [name: 'Suggester', agentKey: 'sug-1'])

        when:
        Approval pend = approvalService.submit('npub-p', [
                name: 'Change process step', agentId: agent.id, riskLevel: 'MED',
                proposalJson: '{"op":"addStep"}', targetFamily: 'capability'
        ])
        Approval ok = approvalService.approve(pend.id, 'npub-p', [decidedByNpub: 'npub-p'])

        then:
        ok.decision == 'APPROVED'
        ok.decidedAt
        approvalService.listOpen('npub-p').isEmpty()
    }

    def "memory append and archive"() {
        given:
        def agent = agentService.create('npub-m', [name: 'Learner', agentKey: 'learn-1'])

        when:
        def m = memoryService.record('npub-m', [
                name: 'User liked summary', agentId: agent.id, memoryKind: 'FEEDBACK', score: 0.9
        ])
        memoryService.archive(m.id, 'npub-m')

        then:
        memoryService.list('npub-m').isEmpty()
        memoryService.list('npub-m', null, true).size() == 1
    }

    def "human link owner"() {
        given:
        def agent = agentService.create('npub-l', [name: 'Buddy', agentKey: 'buddy-1'])

        when:
        def link = linkService.link('npub-l', [agentId: agent.id, linkKind: 'OWNER', humanNpub: 'npub-l'])

        then:
        link.active
        linkService.listByAgent('npub-l', agent.id).size() == 1
    }
}
