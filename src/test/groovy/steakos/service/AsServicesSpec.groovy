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

package steakos.service

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.catalogue.CatalogueItem
import steakos.catalogue.CatalogueService
import steakos.finance.Invoice
import steakos.finance.FinanceRevenueService
import steakos.supplychain.StockMove
import steakos.supplychain.ScmInventoryService

/**
 * AfterSales kernel: service level, inquiry, warranty, claims, RMA.
 */
class AsServicesSpec extends Specification implements DataTest {

    AsServiceLevelService serviceLevelService
    AsInquiryService inquiryService
    AsWarrantyService warrantyService
    AsClaimsService claimsService
    AsRmaService rmaService
    ScmInventoryService inventoryService
    CatalogueService catalogueService

    Class[] getDomainClassesToMock() {
        [
                ServiceLevel, Inquiry, Warranty, Claims, Rma,
                StockMove, Invoice, CatalogueItem
        ] as Class[]
    }

    def setup() {
        serviceLevelService = new AsServiceLevelService()
        inquiryService = new AsInquiryService(asServiceLevelService: serviceLevelService)
        warrantyService = new AsWarrantyService()
        catalogueService = new CatalogueService()
        inventoryService = new ScmInventoryService(catalogueService: catalogueService)
        rmaService = new AsRmaService(
                asInquiryService: inquiryService,
                scmInventoryService: inventoryService
        )
        claimsService = new AsClaimsService(
                asWarrantyService: warrantyService,
                asInquiryService: inquiryService,
                financeRevenueService: null // unit: no finance bean
        )
    }

    def "service level and inquiry lifecycle with kind"() {
        when:
        ServiceLevel sl = serviceLevelService.create('npub-as', [
                name: 'Gold', responseHours: 4, resolveHours: 24, channel: 'EMAIL'
        ])
        Inquiry inq = inquiryService.openInquiry('npub-as', [
                name: 'Broken unit', kind: 'COMPLAINT', serviceLevelId: sl.id, customerName: 'Acme'
        ])

        then:
        inq.kind == 'COMPLAINT'
        inq.caseStatus == 'OPEN'
        inq.responseDueAt != null

        when:
        inquiryService.startWork(inq.id, 'npub-as')
        inquiryService.resolve(inq.id, 'npub-as', [resolution: 'Replaced'])
        Inquiry closed = inquiryService.close(inq.id, 'npub-as')

        then:
        closed.caseStatus == 'CLOSED'
    }

    def "claim requires warranty; approve without finance bean still works"() {
        given:
        Warranty w = warrantyService.register('npub-cl', [
                name: 'Unit cover', customerName: 'Bob', coverStatus: 'ACTIVE'
        ])

        when:
        claimsService.receiveClaim('npub-cl', [claimAmountEur: 50G])

        then:
        thrown(IllegalArgumentException)

        when:
        Claims c = claimsService.receiveClaim('npub-cl', [
                warrantyId: w.id, claimAmountEur: 50G, claimAmountSats: 1000L
        ])
        claimsService.validate(c.id, 'npub-cl')
        Map result = claimsService.approve(c.id, 'npub-cl')

        then:
        result.claim.claimStatus == 'APPROVED'
        result.claim.claimAmountSats == 1000L
        // no finance service → no credit id
        result.financeCreditInvoiceId == null
    }

    def "rma confirm posts stock receipt when productId set"() {
        given:
        CatalogueItem item = catalogueService.create('npub-r', [
                name       : 'Returned widget',
                nature     : CatalogueItem.NATURE_GOODS,
                canBeSold  : true,
                canBeBought: true,
                unit       : 'EA',
                currency   : 'EUR',
                listPrice  : 10G
        ])

        when:
        Rma r = rmaService.openRma('npub-r', [
                productId: item.id, quantity: 2G, customerName: 'C', reason: 'defective'
        ])
        Map conf = rmaService.confirm(r.id, 'npub-r')

        then:
        conf.rma.rmaStatus == 'CONFIRMED'
        conf.stockMoveId != null
        inventoryService.onHand('npub-r', item.id) == 2G
    }
}
