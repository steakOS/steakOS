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

package steakos.sales

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.events.DomainEvent
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames

/**
 * SalesAdvanced price lists (PR-4a / OI-S04).
 */
class SalesPriceListServiceSpec extends Specification implements DataTest {

    SalesPriceListService service
    SalesCustomerService customerService
    DomainEventBusService bus
    List<DomainEvent> published

    Class[] getDomainClassesToMock() {
        [Customer, PriceList, PriceListLine] as Class[]
    }

    def setup() {
        customerService = new SalesCustomerService()
        bus = new DomainEventBusService()
        published = []
        bus.subscribe(DomainEventNames.SALES_PRICELIST_UPDATED) { DomainEvent e ->
            published << e
        }
        service = new SalesPriceListService(
                salesCustomerService: customerService,
                domainEventBusService: bus
        )
    }

    def cleanup() {
        bus.clearSubscribers()
    }

    def "create STANDARD price list with lines"() {
        when:
        PriceList pl = service.createPriceList('npub-pl', [
                name    : 'Retail 2026',
                code    : 'RETAIL-26',
                listKind: 'STANDARD',
                lines   : [
                        [name: 'Widget A', catalogueItemId: 101L, unitPrice: 25G, minQuantity: 1],
                        [name: 'Widget A bulk', catalogueItemId: 101L, unitPrice: 20G, minQuantity: 10,
                         discountPercent: 5G]
                ]
        ])

        then:
        pl.id
        pl.code == 'RETAIL-26'
        pl.active
        PriceListLine.countByPriceList(pl) == 2
        published.size() == 1
        published[0].payload.action == 'created'
        published[0].payload.priceListId == pl.id
    }

    def "CUSTOMER list requires customerId"() {
        when:
        service.createPriceList('npub-pl', [name: 'VIP', listKind: 'CUSTOMER'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('customerId')
    }

    def "CUSTOMER list binds to existing customer"() {
        given:
        Customer c = customerService.createCustomer('npub-pl', [name: 'VIP Co'])

        when:
        PriceList pl = service.createPriceList('npub-pl', [
                name      : 'VIP list',
                listKind  : 'CUSTOMER',
                customerId: c.id,
                lines     : [[name: 'Special', catalogueItemId: 7L, unitPrice: 9.5G]]
        ])

        then:
        pl.customerId == c.id
        pl.listKind == PriceList.KIND_CUSTOMER
    }

    def "duplicate code rejected per tenant"() {
        given:
        service.createPriceList('npub-pl', [name: 'A', code: 'SAME'])

        when:
        service.createPriceList('npub-pl', [name: 'B', code: 'SAME'])

        then:
        thrown(IllegalArgumentException)
    }

    def "setLines replaces previous lines"() {
        given:
        PriceList pl = service.createPriceList('npub-pl', [
                name : 'Swap',
                lines: [[name: 'Old', unitPrice: 1G]]
        ])

        when:
        service.setLines(pl.id, 'npub-pl', [
                [name: 'New1', unitPrice: 2G],
                [name: 'New2', unitPrice: 3G, catalogueItemId: 55L]
        ])

        then:
        PriceListLine.countByPriceList(pl) == 2
        service.toMap(pl, true).lines*.name == ['New1', 'New2']
        published*.payload.action.contains('lines')
    }

    def "resolvePrice prefers customer list then volume tier"() {
        given:
        Customer c = customerService.createCustomer('npub-r', [name: 'Buyer'])
        service.createPriceList('npub-r', [
                name : 'Std',
                code : 'STD',
                lines: [
                        [name: 'Item', catalogueItemId: 42L, unitPrice: 100G, minQuantity: 1],
                        [name: 'Item10', catalogueItemId: 42L, unitPrice: 90G, minQuantity: 10]
                ]
        ])
        service.createPriceList('npub-r', [
                name      : 'Cust',
                listKind  : 'CUSTOMER',
                customerId: c.id,
                defaultDiscountPercent: 10G,
                lines     : [[name: 'Item', catalogueItemId: 42L, unitPrice: 80G, minQuantity: 1]]
        ])

        when:
        Map std = service.resolvePrice('npub-r', 42L, null, 10G)
        Map cust = service.resolvePrice('npub-r', 42L, c.id, 1G)
        Map miss = service.resolvePrice('npub-r', 999L, null, 1G)

        then:
        std.found
        std.unitPrice == 90G
        std.netUnitPrice == 90G
        cust.found
        cust.unitPrice == 80G
        cust.netUnitPrice == 72G  // 10% default discount
        cust.priceListName == 'Cust'
        !miss.found
    }

    def "deactivate soft-disables list"() {
        given:
        PriceList pl = service.createPriceList('npub-pl', [name: 'Temp', code: 'TMP'])

        when:
        service.deactivate(pl.id, 'npub-pl')
        Map page = service.listPriceListsPage('npub-pl', true, 25, 0)

        then:
        !PriceList.get(pl.id).active
        page.total == 0
        published*.payload.action.contains('deactivated')
    }

    def "npub scope isolation"() {
        given:
        PriceList pl = service.createPriceList('npub-a', [name: 'A only'])

        when:
        service.getPriceList(pl.id, 'npub-b')

        then:
        thrown(IllegalArgumentException)
    }

    def "Advanced route catalog covers pricelists API"() {
        expect:
        steakos.configuration.AdvancedRouteCatalog.isAdvancedPath('/api/sales/pricelists')
        steakos.configuration.AdvancedRouteCatalog.isAdvancedPath('/api/sales/pricelists/resolve')
        steakos.configuration.AdvancedRouteCatalog.isAdvancedPath('/api/sales/pricelists/12/lines')
    }
}
