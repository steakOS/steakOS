// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import spock.lang.Specification
import steakos.User

class SelfAssignedTaskDeleteSpec extends Specification {

    def "parses Task id from DELETE path"() {
        expect:
        SelfAssignedTaskDelete.taskIdFromDeletePath('DELETE', '/api/domains/core/Task/42', '') == 42L
        SelfAssignedTaskDelete.taskIdFromDeletePath('DELETE', '/api/domains/core/Task/42/', '') == 42L
        SelfAssignedTaskDelete.taskIdFromDeletePath('DELETE', '/steakos/api/domains/core/Task/7', '/steakos') == 7L
    }

    def "ignores non-delete and non-task paths"() {
        expect:
        SelfAssignedTaskDelete.taskIdFromDeletePath('GET', '/api/domains/core/Task/42', '') == null
        SelfAssignedTaskDelete.taskIdFromDeletePath('DELETE', '/api/domains/core/Task', '') == null
        SelfAssignedTaskDelete.taskIdFromDeletePath('DELETE', '/api/domains/sales/Customer/1', '') == null
        SelfAssignedTaskDelete.taskIdFromDeletePath('PUT', '/api/domains/core/Task/42', '') == null
    }

    def "assignee matches user:username convention"() {
        given:
        User u = new User(username: 'admin')

        expect:
        SelfAssignedTaskDelete.assigneeMatchesUser('user:admin', u)
        !SelfAssignedTaskDelete.assigneeMatchesUser('user:other', u)
        !SelfAssignedTaskDelete.assigneeMatchesUser('', u)
        !SelfAssignedTaskDelete.assigneeMatchesUser('user:admin', null)
    }

    def "assignee matches raw npub when set"() {
        given:
        User u = new User(username: 'alice', npub: 'npub1alice')

        expect:
        SelfAssignedTaskDelete.assigneeMatchesUser('npub1alice', u)
        !SelfAssignedTaskDelete.assigneeMatchesUser('npub1bob', u)
    }
}
