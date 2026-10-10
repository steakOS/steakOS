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

package steakos.supplychain

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.catalogue.CatalogueItem
import steakos.catalogue.CatalogueService
import steakos.finance.Account
import steakos.finance.FinanceLedgerService
import steakos.finance.FinancePayableService
import steakos.finance.JournalEntry
import steakos.finance.JournalLine
import steakos.finance.Payable
import steakos.procurement.PurchaseOrder
import steakos.procurement.PurchaseOrderLine
import steakos.procurement.Receipt
import steakos.procurement.ReceiptLine
import steakos.procurement.Vendor
import steakos.production.BillOfMaterials
import steakos.production.BomItem
import steakos.production.Check
import steakos.production.Component
import steakos.production.Lot
import steakos.production.ProductionOrder
import steakos.sales.Customer
import steakos.sales.CustomerQuote
import steakos.sales.OrderLine
import steakos.sales.QuoteLine
import steakos.sales.SalesOrder

/**
 * SC kernel: vendor → PO confirm→payable → receive→stock; catalogue goods → production complete→stock.
 */
class ScmServicesSpec extends Specification implements DataTest {

    ScmVendorService vendorService
    ScmProductService productService
    ScmInventoryService inventoryService
    ScmPurchaseService purchaseService
    ScmBomService bomService
    ScmQualityService qualityService
    ScmProductionService productionService
    FinancePayableService payableService
    FinanceLedgerService ledgerService
    CatalogueService catalogueService

    Class[] getDomainClassesToMock() {
        [
                Vendor, PurchaseOrder, PurchaseOrderLine, Receipt, ReceiptLine,
                CatalogueItem, Component, BillOfMaterials, BomItem, ProductionOrder, Lot, Check,
                StockMove,
                Account, JournalEntry, JournalLine, Payable,
                Customer, CustomerQuote, QuoteLine, SalesOrder, OrderLine
        ] as Class[]
    }

    def setup() {
        catalogueService = new CatalogueService()
        vendorService = new ScmVendorService()
        productService = new ScmProductService(catalogueService: catalogueService)
        inventoryService = new ScmInventoryService(catalogueService: catalogueService)
        ledgerService = new FinanceLedgerService()
        payableService = new FinancePayableService(financeLedgerService: ledgerService)
        purchaseService = new ScmPurchaseService(
                scmVendorService: vendorService,
                scmInventoryService: inventoryService,
                financePayableService: payableService,
                catalogueService: catalogueService
        )
        bomService = new ScmBomService(catalogueService: catalogueService)
        qualityService = new ScmQualityService(
                catalogueService: catalogueService,
                scmInventoryService: inventoryService
        )
        productionService = new ScmProductionService(
                catalogueService: catalogueService,
                scmInventoryService: inventoryService,
                scmQualityService: qualityService
        )
    }

    def "vendor and product masters"() {
        when:
        Vendor v = vendorService.createVendor('npub-scm', [name: 'Acme Supply'])
        CatalogueItem p = productService.createProduct('npub-scm', [name: 'Widget', sku: 'W-1'])

        then:
        v.id
        p.code == 'W-1'
        p.nature == CatalogueItem.NATURE_GOODS
    }

    def "confirm PO creates payable; receive posts stock"() {
        given:
        Vendor v = vendorService.createVendor('npub-buy', [name: 'Vendor Co'])
        CatalogueItem p = productService.createProduct('npub-buy', [name: 'Steel', sku: 'ST'])
        PurchaseOrder po = purchaseService.createPurchaseOrder('npub-buy', [
                vendorId: v.id,
                lines   : [[name: 'Steel bar', quantity: 4G, unitPriceEur: 10G, catalogueItemId: p.id]]
        ])

        when:
        Map conf = purchaseService.confirmPurchaseOrder(po.id, 'npub-buy')

        then:
        conf.alreadyConfirmed == false
        conf.financePayableId
        Payable.get(conf.financePayableId).amountEur == 40G
        PurchaseOrder.get(po.id).poStatus == 'CONFIRMED'

        when:
        Map rcv = purchaseService.receiveGoods(po.id, 'npub-buy')

        then:
        rcv.receipt.receiptStatus == 'POSTED'
        PurchaseOrder.get(po.id).poStatus == 'RECEIVED'
        inventoryService.onHand('npub-buy', p.id) == 4G
    }

    def "production complete creates OPEN lot; QA PASS posts stock"() {
        given:
        CatalogueItem p = productService.createProduct('npub-mk', [name: 'Finished'])
        ProductionOrder mo = productionService.createProductionOrder('npub-mk', [
                productId: p.id,
                quantity : 3G
        ])

        when:
        Map result = productionService.complete(mo.id, 'npub-mk')

        then:
        result.productionOrder.prodStatus == 'COMPLETED'
        result.lot?.lotStatus == 'OPEN'
        result.awaitingQa == true
        inventoryService.onHand('npub-mk', p.id) == 0G
        ProductionOrder.get(mo.id).resultingLotId == result.lot.id

        when:
        Check c = qualityService.recordCheck('npub-mk', [
                lotId      : result.lot.id,
                checkResult: 'PASS',
                notes      : 'ok'
        ])

        then:
        c.checkResult == 'PASS'
        Lot.get(result.lot.id).lotStatus == 'RELEASED'
        inventoryService.onHand('npub-mk', p.id) == 3G

        when:
        qualityService.recordCheck('npub-mk', [lotId: result.lot.id, checkResult: 'PASS'])

        then:
        thrown(IllegalStateException)
        inventoryService.onHand('npub-mk', p.id) == 3G
    }

    def "manual lot QA does not post stock on PASS"() {
        given:
        CatalogueItem p = productService.createProduct('npub-qa', [name: 'On hand goods'])
        inventoryService.postMove('npub-qa', [
                catalogueItemId: p.id,
                qtyDelta       : 5G,
                reason         : 'ADJUST',
                name           : 'seed stock'
        ])
        Lot lot = qualityService.createLot('npub-qa', [
                catalogueItemId: p.id,
                quantity       : 2G,
                name           : 'Manual sample'
        ])

        when:
        qualityService.recordCheck('npub-qa', [lotId: lot.id, checkResult: 'PASS'])

        then:
        Lot.get(lot.id).lotStatus == 'RELEASED'
        inventoryService.onHand('npub-qa', p.id) == 5G
    }

    def "bom create with items"() {
        given:
        CatalogueItem finished = productService.createProduct('npub-b', [name: 'Bike'])
        CatalogueItem frame = productService.createProduct('npub-b', [name: 'Frame', sku: 'F1'])

        when:
        BillOfMaterials bom = bomService.createBom('npub-b', [
                productId: finished.id,
                items    : [[componentProductId: frame.id, quantity: 1G]]
        ])

        then:
        bom.id
        BomItem.countByBom(bom) == 1
    }
}
