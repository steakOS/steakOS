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

package steakos.core

import grails.testing.gorm.DataTest
import spock.lang.Specification

/**
 * OI-WK01 Task unification — defaults, assignees, Eisenhower fields.
 */
class TaskSpec extends Specification implements DataTest {

    Class[] getDomainClassesToMock() {
        [Task] as Class[]
    }

    def "defaults urgency=2, importance=2, status=OPEN on validate"() {
        when:
        def t = new Task(npubOwner: 'npub1test', name: 'Ship release')

        then:
        t.validate()
        t.urgency == 2
        t.importance == 2
        t.status == 'OPEN'
    }

    def "accepts human and agent assignees together"() {
        when:
        def t = new Task(
                npubOwner: 'npub1test',
                name: 'Review HITL',
                assigneeHumanNpub: 'user:alice',
                assigneeAgentId: 42L,
                urgency: 3,
                importance: 1,
                status: 'IN_PROGRESS'
        )

        then:
        t.validate()
        t.assigneeHumanNpub == 'user:alice'
        t.assigneeAgentId == 42L
        t.urgency == 3
        t.importance == 1
    }

    def "rejects urgency outside 1..3"() {
        when:
        def t = new Task(npubOwner: 'npub1test', name: 'Bad', urgency: 4, importance: 2)

        then:
        !t.validate()
        t.errors['urgency']
    }

    def "optional schedule dates are allowed"() {
        given:
        def start = Date.parse('yyyy-MM-dd', '2026-07-28')
        def due = Date.parse('yyyy-MM-dd', '2026-08-01')

        when:
        def t = new Task(
                npubOwner: 'npub1test',
                name: 'Timed',
                startAt: start,
                deadlineAt: due
        )

        then:
        t.validate()
        t.startAt == start
        t.deadlineAt == due
    }

    def "Activity class is no longer on the classpath as a domain"() {
        expect:
        // Unification: Activity removed; only Task remains for work items
        !this.class.classLoader.loadClass('steakos.core.Task').simpleName.isEmpty()
        try {
            this.class.classLoader.loadClass('steakos.core.Activity')
            assert false: 'Activity should not resolve'
        } catch (ClassNotFoundException ignored) {
            true
        }
    }
}
