// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.api

import spock.lang.Specification

class ListPagingSpec extends Specification {

    def "requireSortParams defaults when blank"() {
        when:
        Map so = ListPaging.requireSortParams([:], 'id', 'desc', ['id', 'name'])

        then:
        so.sort == 'id'
        so.order == 'desc'
    }

    def "requireSortParams accepts allowlisted field"() {
        when:
        Map so = ListPaging.requireSortParams([sort: 'name', order: 'asc'], 'id', 'desc', ['id', 'name'])

        then:
        so.sort == 'name'
        so.order == 'asc'
    }

    def "requireSortParams rejects unknown sort (D1)"() {
        when:
        ListPaging.requireSortParams([sort: 'nope'], 'id', 'desc', ['id', 'name'])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('Unknown sort field')
    }

    def "likeContains strips LIKE wildcards"() {
        expect:
        ListPaging.likeContains('a%b_c') == '%abc%'
        ListPaging.likeContains('  ') == null
        ListPaging.likeContains(null) == null
    }
}
