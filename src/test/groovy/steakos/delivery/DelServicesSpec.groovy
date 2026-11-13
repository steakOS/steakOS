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

package steakos.delivery

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.Role
import steakos.User
import steakos.UserRole
import steakos.catalogue.CatalogueItem
import steakos.catalogue.CatalogueService
import steakos.human.HRFile
import steakos.human.HrFileService
import steakos.human.HrUserSupportService
import steakos.sales.Customer
import steakos.sales.OrderLine
import steakos.sales.SalesOrder

/**
 * Delivery kernel: catalogue SERVICE + resource + order lines; from-sales filters service lines.
 */
class DelServicesSpec extends Specification implements DataTest {

    DelServiceService serviceService
    DelResourceService resourceService
    DelOrderService orderService
    CatalogueService catalogueService
    HrFileService hrFileService

    Class[] getDomainClassesToMock() {
        [
                CatalogueItem, Resource, ServiceOrder, ServiceOrderLine,
                Customer, SalesOrder, OrderLine,
                HRFile, User, UserRole, Role
        ] as Class[]
    }

    def setup() {
        catalogueService = new CatalogueService()
        serviceService = new DelServiceService(catalogueService: catalogueService)
        hrFileService = new HrFileService(hrUserSupportService: new HrUserSupportService(springSecurityService: null))
        resourceService = new DelResourceService(hrFileService: hrFileService)
        orderService = new DelOrderService(
                delServiceService: serviceService,
                delResourceService: resourceService,
                catalogueService: catalogueService
        )
    }

    def "create catalogue service and resource"() {
        when:
        CatalogueItem s = serviceService.createService('npub-d', [
                name: 'Installation', unit: 'HOUR', listPriceEur: 120G
        ])
        Resource r = resourceService.createResource('npub-d', [
                name: 'Founder', resourceKind: 'HUMAN'
        ])

        then:
        s.id
        s.listPrice == 120G
        s.nature == CatalogueItem.NATURE_SERVICE
        r.resourceKind == 'HUMAN'
    }

    def "assignee list syncs active HR employees only"() {
        given:
        HRFile active = hrFileService.create('npub-asg', [name: 'Active Emp', username: 'active.emp', email: 'a@ex.com'])
        HRFile exited = hrFileService.create('npub-asg', [name: 'Exited Emp', username: 'exited.emp'])
        hrFileService.separate(exited.id, 'npub-asg', [:])
        resourceService.createResource('npub-asg', [name: 'Orphan tech', resourceKind: 'HUMAN'])
        resourceService.createResource('npub-asg', [name: 'Bot', resourceKind: 'AGENT'])

        when:
        Map page = resourceService.listResourcesPage('npub-asg', true, 50, 0, null, true)

        then:
        page.total == 1
        page.items[0].hrFileId == active.id
        page.items[0].name == 'Active Emp'
        page.items[0].email == 'a@ex.com'
        !page.items.any { it.name == 'Orphan tech' || it.name == 'Bot' || it.hrFileId == exited.id }
    }

    def "order lines recompute header; complete without finance"() {
        given:
        CatalogueItem s = serviceService.createService('npub-o', [name: 'Install', listPriceEur: 50G])
        Resource r = resourceService.createResource('npub-o', [name: 'Tech'])

        when:
        ServiceOrder o = orderService.createOrder('npub-o', [
                serviceId   : s.id,
                resourceId  : r.id,
                customerName: 'Acme',
                lines       : [
                        [name: 'Install day', quantity: 2G, unitPriceEur: 50G, unitPriceSats: 1000L,
                         catalogueItemId: s.id]
                ]
        ])

        then:
        o.fulfillStatus == 'DRAFT'
        o.amountEur == 100G
        o.amountSats == 2000L
        o.resource?.id == r.id

        when:
        orderService.schedule(o.id, 'npub-o')
        orderService.start(o.id, 'npub-o')
        Map done = orderService.complete(o.id, 'npub-o')

        then:
        done.alreadyCompleted == false
        done.order.fulfillStatus == 'COMPLETED'
        // D3: no finance side-effect in map
        !done.financeInvoiceId
    }

    def "createFromSalesOrder takes service lines only"() {
        given:
        Customer c = new Customer(npubOwner: 'npub-m', name: 'Buyer').save(failOnError: true, flush: true)
        CatalogueItem goods = catalogueService.create('npub-m', [
                name: 'Turbine', code: 'T1', nature: CatalogueItem.NATURE_GOODS
        ])
        CatalogueItem svc = catalogueService.create('npub-m', [
                name: 'Install svc', code: 'I1', nature: CatalogueItem.NATURE_SERVICE, listPrice: 100G
        ])
        SalesOrder so = new SalesOrder(
                npubOwner: 'npub-m', name: 'Mixed SO', orderNumber: 'SO-MIX-1',
                customer: c, customerName: 'Buyer', orderStatus: 'CONFIRMED', amountEur: 0G
        ).save(failOnError: true, flush: true)
        new OrderLine(
                npubOwner: 'npub-m', salesOrder: so, lineNo: 1, name: 'Turbine unit',
                quantity: 1G, unitPriceEur: 1000G, lineTotalEur: 1000G,
                productRefId: goods.id, productRefType: 'PRODUCT', catalogueItemId: goods.id
        ).save(failOnError: true, flush: true)
        new OrderLine(
                npubOwner: 'npub-m', salesOrder: so, lineNo: 2, name: 'Installation',
                quantity: 4G, unitPriceEur: 100G, lineTotalEur: 400G,
                productRefId: svc.id, productRefType: 'SERVICE', catalogueItemId: svc.id
        ).save(failOnError: true, flush: true)

        when:
        List<ServiceOrder> created = orderService.createFromSalesOrder(so.id, 'npub-m')

        then:
        created.size() == 1
        created[0].sourceSalesOrderId == so.id
        created[0].amountEur == 400G
        ServiceOrderLine.countByServiceOrder(created[0]) == 1
        ServiceOrderLine.findByServiceOrder(created[0]).name == 'Installation'
    }
}
