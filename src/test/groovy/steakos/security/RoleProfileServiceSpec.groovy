// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import spock.lang.Specification

class RoleProfileServiceSpec extends Specification {

    def "defaults include seven presets"() {
        when:
        List ids = RoleProfileService.defaults()*.profileId

        then:
        ids == ['admin', 'ceo', 'office', 'ops', 'finance', 'user', 'read_only']
    }

    def "admin matrix allows all desks and system"() {
        when:
        Map m = RoleProfileService.defaults().find { it.profileId == 'admin' }.matrix as Map

        then:
        m.system.users == true
        m.system.setup == true
        m.system.configuration == true
        m.desks.sales.see == true
        m.desks.finance.create == true
    }

    def "office cannot settle or manage users"() {
        when:
        Map m = RoleProfileService.defaults().find { it.profileId == 'office' }.matrix as Map

        then:
        m.system.users == false
        m.system.settle == false
        m.desks.sales.see == true
        !m.desks.finance // not granted — effective resolve treats missing as deny
    }

    def "ceo spring roles include CEO and ADMIN for Secured compat"() {
        when:
        List roles = RoleProfileService.defaults().find { it.profileId == 'ceo' }.springRoles as List

        then:
        roles.contains('ROLE_CEO')
        roles.contains('ROLE_ADMIN')
        roles.contains('ROLE_USER')
    }

    def "ceo defaults deny setup and catalogs but allow configuration, users and settle"() {
        when:
        Map m = RoleProfileService.defaults().find { it.profileId == 'ceo' }.matrix as Map

        then:
        m.system.setup == false
        m.system.configuration == true
        m.system.catalogs == false
        m.system.users == true
        m.system.settle == true
        m.desks.strategy.see == true
        m.desks.human.see == true
    }

    def "read_only has no write verbs in seed"() {
        when:
        Map m = RoleProfileService.defaults().find { it.profileId == 'read_only' }.matrix as Map

        then:
        m.desks.sales.create == false
        m.desks.sales.change == false
        m.desks.sales.see == true
    }
}
