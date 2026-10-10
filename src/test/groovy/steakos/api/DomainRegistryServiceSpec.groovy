// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
package steakos.api

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.helper.Currencies
import steakos.sales.Customer

/**
 * B4 — Domain CRUD tenant scoping via active company npubOwner.
 */
class DomainRegistryServiceSpec extends Specification implements DataTest {

    DomainRegistryService service

    Class[] getDomainClassesToMock() {
        [Customer, Currencies] as Class[]
    }

    def setup() {
        service = new DomainRegistryService()
    }

    def "isTenantScoped true for NpubOwned, false for shared Currencies"() {
        expect:
        service.isTenantScoped(Customer)
        !service.isTenantScoped(Currencies)
    }

    def "listPage filters tenant rows by active npubOwner"() {
        given:
        service.create(Customer, [name: 'Mine', customerKind: 'ORGANIZATION'], 'company:a')
        service.create(Customer, [name: 'Other', customerKind: 'ORGANIZATION'], 'company:b')

        when:
        Map result = service.listPage(Customer, 25, 0, [npubOwner: 'company:a'])

        then:
        result.total == 1
        (result.items as List)*.name == ['Mine']
    }

    def "get returns null for cross-tenant id (no leak)"() {
        given:
        Customer other = new Customer(name: 'Secret', npubOwner: 'company:other',
                customerKind: 'ORGANIZATION', currencyCode: 'EUR').save(failOnError: true)

        expect:
        service.get(Customer, other.id, 'company:mine') == null
        service.get(Customer, other.id, 'company:other')?.name == 'Secret'
    }

    def "create forces active npub and rejects foreign npubOwner in body"() {
        when:
        Customer ok = service.create(Customer, [name: 'Forced', customerKind: 'ORGANIZATION'], 'company:live') as Customer

        then:
        ok.npubOwner == 'company:live'

        when:
        service.create(Customer, [name: 'Bad', npubOwner: 'company:other', customerKind: 'ORGANIZATION'], 'company:live')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('npubOwner must match active company')
    }

    def "update returns null for cross-tenant row"() {
        given:
        Customer other = new Customer(name: 'X', npubOwner: 'company:other',
                customerKind: 'ORGANIZATION', currencyCode: 'EUR').save(failOnError: true, flush: true)

        when:
        def row = service.update(Customer, other.id, [name: 'Hacked'], 'company:mine')

        then:
        row == null
        Customer.get(other.id).name == 'X'
    }

    def "delete returns false for cross-tenant row"() {
        given:
        Customer other = new Customer(name: 'Keep', npubOwner: 'company:other',
                customerKind: 'ORGANIZATION', currencyCode: 'EUR').save(failOnError: true, flush: true)

        expect:
        !service.delete(Customer, other.id, 'company:mine')
        Customer.get(other.id) != null
    }

    def "shared Currencies list is not filtered by npubOwner"() {
        given:
        service.create(Currencies, [code: 'EUR', name: 'Euro', description: 'Euro', decimals: 2, enabled: true])
        service.create(Currencies, [code: 'USD', name: 'US Dollar', description: 'USD', decimals: 2, enabled: true])

        when:
        Map result = service.listPage(Currencies, 25, 0, [npubOwner: 'company:a'])

        then:
        result.total == 2
        (result.items as List)*.code as Set == ['EUR', 'USD'] as Set
    }
}
