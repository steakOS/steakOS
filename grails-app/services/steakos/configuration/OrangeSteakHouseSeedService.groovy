// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.configuration

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.catalogue.CatalogueItem
import steakos.catalogue.CatalogueService
import steakos.core.Task
import steakos.finance.FinanceLedgerService
import steakos.finance.FinanceTreasuryService
import steakos.finance.Invoice
import steakos.procurement.Vendor
import steakos.sales.Customer
import steakos.sales.CustomerQuote
import steakos.sales.SalesOrder

/**
 * PDR-026 PR3 — kitchen-sink seed + reset for Orange Steak House (DEMO npub).
 */
@Slf4j
class OrangeSteakHouseSeedService {

    static final String MARKER_CUSTOMER = 'OSH Walk-in Guest'
    static final String DEMO_NPUB = CompanyContextService.DEMO_NPUB

    CatalogueService catalogueService
    FinanceLedgerService financeLedgerService
    FinanceTreasuryService financeTreasuryService
    steakos.finance.PostingRulesetService postingRulesetService
    CompanyContextService companyContextService
    CompanyConfigService companyConfigService

    @Transactional
    Map ensureAndSeed() {
        companyContextService?.ensureBootstrapped()
        seed(false)
    }

    /**
     * @param force if true, wipe demo tenant data first then reseed
     */
    @Transactional
    Map seed(boolean force) {
        String npub = DEMO_NPUB
        companyContextService?.ensureBootstrapped()
        LegalEntity demo = LegalEntity.findByCode(LegalEntity.CODE_ORANGE_STEAK)
        if (!demo) {
            throw new IllegalStateException('Demo company shell missing — run company bootstrap first')
        }
        if (demo.npubOwner != npub) {
            demo.npubOwner = npub
            demo.save(failOnError: true)
        }

        if (force) {
            wipeDemoData(npub)
        } else if (Customer.findByNpubOwnerAndName(npub, MARKER_CUSTOMER)) {
            return [
                    npubOwner: npub,
                    companyId: demo.id,
                    skipped  : true,
                    message  : 'Orange Steak House seed already present',
            ]
        }

        if (financeLedgerService) {
            financeLedgerService.ensureChartSeeded(npub)
        }
        try {
            postingRulesetService?.ensureSeeded(npub)
        } catch (Exception e) {
            log.debug('posting rules seed: {}', e.message)
        }
        if (financeTreasuryService) {
            financeTreasuryService.ensureDefaultPositions(npub)
        }

        // --- Catalogue (steak house menu) ---
        List catIds = []
        [
                [name: 'Ribeye Steak 300g', code: 'OSH-RIBEYE', nature: CatalogueItem.NATURE_GOODS, unit: 'ea', price: '42.00', sold: true, bought: true],
                [name: 'Filet Mignon 200g', code: 'OSH-FILET', nature: CatalogueItem.NATURE_GOODS, unit: 'ea', price: '48.00', sold: true, bought: true],
                [name: 'House Fries', code: 'OSH-FRIES', nature: CatalogueItem.NATURE_GOODS, unit: 'ea', price: '6.50', sold: true, bought: true],
                [name: 'Caesar Salad', code: 'OSH-CAESAR', nature: CatalogueItem.NATURE_GOODS, unit: 'ea', price: '9.00', sold: true, bought: true],
                [name: 'Private Dining Package', code: 'OSH-PRIVATE', nature: CatalogueItem.NATURE_SERVICE, unit: 'event', price: '450.00', sold: true, bought: false],
                [name: 'Wine Pairing Flight', code: 'OSH-WINE', nature: CatalogueItem.NATURE_SERVICE, unit: 'set', price: '55.00', sold: true, bought: false],
        ].each { Map row ->
            CatalogueItem existing = CatalogueItem.findByNpubOwnerAndCode(npub, row.code as String)
            if (existing) {
                catIds << existing.id
                return
            }
            CatalogueItem ci = catalogueService.create(npub, [
                    name       : row.name,
                    code       : row.code,
                    nature     : row.nature,
                    unit       : row.unit,
                    listPrice  : new BigDecimal(row.price as String),
                    canBeSold  : row.sold,
                    canBeBought: row.bought,
                    active     : true,
            ])
            catIds << ci?.id
        }

        // --- Customers ---
        Customer guest = upsertCustomer(npub, MARKER_CUSTOMER, 'guest@orangesteak.example', 'PERSON')
        Customer corp = upsertCustomer(npub, 'Orange Corporate Events', 'events@orangesteak.example', 'ORGANIZATION')
        Customer tourist = upsertCustomer(npub, 'Tour Group Horizon', 'horizon@example.com', 'ORGANIZATION')

        // --- Vendors ---
        Vendor meat = upsertVendor(npub, 'Prairie Prime Meats', 'orders@prairieprime.example')
        Vendor wine = upsertVendor(npub, 'Cellar & Vine Supply', 'sales@cellarvines.example')
        Vendor linen = upsertVendor(npub, 'WhiteCloth Linens', 'hello@whitecloth.example')

        // --- Tasks / story ---
        Task t1 = upsertTask(npub, 'OSH: prep Friday service', 'Confirm ribeye stock and private dining set-up.', 3, 3)
        Task t2 = upsertTask(npub, 'OSH: follow up corporate events quote', 'Orange Corporate Events — wine pairing package.', 2, 2)

        // Metal snapshot note on company
        try {
            companyConfigService?.put(npub, 'demo.pack', 'orange-steak-house-v1', 'demo')
        } catch (ignored) {
        }

        log.info('Orange Steak House seed applied for {}', npub)
        [
                npubOwner   : npub,
                companyId   : demo.id,
                skipped     : false,
                forced      : force,
                customers   : 3,
                vendors     : 3,
                catalogue   : catIds.size(),
                tasks       : 2,
                customerIds : [guest.id, corp.id, tourist.id],
                vendorIds   : [meat.id, wine.id, linen.id],
                catalogueIds: catIds,
                taskIds     : [t1.id, t2.id],
                message     : force ? 'Demo reset and reseeded' : 'Orange Steak House seed created',
        ]
    }

    @Transactional
    Map resetDemo() {
        seed(true)
    }

    /** Delete business rows for the DEMO npub only — never LIVE. */
    @Transactional
    void wipeDemoData(String npub) {
        if (npub != DEMO_NPUB) {
            throw new SecurityException("Refuse wipe for non-demo npub: ${npub}")
        }
        // Child-ish / docs first where FKs may exist
        safeDeleteAll(Invoice, npub)
        safeDeleteAll(SalesOrder, npub)
        safeDeleteAll(CustomerQuote, npub)
        safeDeleteAll(steakos.procurement.PurchaseOrder, npub)
        safeDeleteAll(Task, npub)
        safeDeleteAll(Customer, npub)
        safeDeleteAll(Vendor, npub)
        safeDeleteAll(CatalogueItem, npub)
        // Stock moves / BOM if present
        try {
            safeDeleteAll(steakos.supplychain.StockMove, npub)
        } catch (ignored) {
        }
        try {
            safeDeleteAll(steakos.production.BillOfMaterials, npub)
        } catch (ignored) {
        }
        log.info('Wiped demo tenant data for {}', npub)
    }

    private static void safeDeleteAll(Class domain, String npub) {
        domain.findAllByNpubOwner(npub).each { row ->
            try {
                row.delete(flush: false)
            } catch (Exception e) {
                // continue best-effort
            }
        }
        try {
            domain.withSession { s -> s.flush(); s.clear() }
        } catch (ignored) {
        }
    }

    private static Customer upsertCustomer(String npub, String name, String email, String kind) {
        Customer c = Customer.findByNpubOwnerAndName(npub, name)
        if (c) return c
        c = new Customer(npubOwner: npub, name: name, email: email, customerKind: kind, active: Boolean.TRUE)
        c.save(failOnError: true)
        c
    }

    private static Vendor upsertVendor(String npub, String name, String email) {
        Vendor v = Vendor.findByNpubOwnerAndName(npub, name)
        if (v) return v
        v = new Vendor(npubOwner: npub, name: name, email: email, active: Boolean.TRUE)
        v.save(failOnError: true)
        v
    }

    private static Task upsertTask(String npub, String name, String desc, int urgency, int importance) {
        Task t = Task.findByNpubOwnerAndName(npub, name)
        if (t) return t
        t = new Task(
                npubOwner: npub,
                name: name,
                description: desc,
                status: 'OPEN',
                urgency: urgency,
                importance: importance
        )
        t.save(failOnError: true)
        t
    }
}
