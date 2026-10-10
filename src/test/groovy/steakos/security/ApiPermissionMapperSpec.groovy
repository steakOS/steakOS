// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import spock.lang.Specification

class ApiPermissionMapperSpec extends Specification {

    def "GET is always allow"() {
        expect:
        ApiPermissionMapper.requireFor('GET', '/api/sales/customers').kind == 'allow'
    }

    def "sales POST maps to desk create"() {
        when:
        Map r = ApiPermissionMapper.requireFor('POST', '/api/sales/quotes')

        then:
        r.kind == 'desk'
        r.family == 'sales'
        r.verb == 'create'
    }

    def "domains package maps to family"() {
        when:
        Map r = ApiPermissionMapper.requireFor('PUT', '/api/domains/finance/Account/1')

        then:
        r.kind == 'desk'
        r.family == 'finance'
        r.verb == 'change'
    }

    def "settle requires settle system key"() {
        when:
        Map r = ApiPermissionMapper.requireFor('POST', '/api/finance/revenue/invoices/9/settle')

        then:
        r.kind == 'system'
        r.systemKey == 'settle'
    }

    def "users API requires users system"() {
        when:
        Map r = ApiPermissionMapper.requireFor('POST', '/api/auth/users')

        then:
        r.kind == 'system'
        r.systemKey == 'users'
    }

    def "totp self enrolment allowed"() {
        expect:
        ApiPermissionMapper.requireFor('POST', '/api/auth/totp/begin').kind == 'allow'
        ApiPermissionMapper.requireFor('POST', '/api/auth/totp/verify').kind == 'allow'
    }

    def "import requires import system"() {
        when:
        Map r = ApiPermissionMapper.requireFor('POST', '/api/import/jobs')

        then:
        r.kind == 'system'
        r.systemKey == 'import'
    }

    def "scm catalogue under scm family"() {
        when:
        Map r = ApiPermissionMapper.requireFor('POST', '/api/catalogue/items')

        then:
        r.family == 'scm'
        r.verb == 'create'
    }
}
