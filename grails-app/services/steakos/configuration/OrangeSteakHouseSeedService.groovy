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
import steakos.finance.FinanceRevenueService
import steakos.finance.FinanceTreasuryService
import steakos.finance.Invoice
import steakos.procurement.PurchaseOrder
import steakos.procurement.Vendor
import steakos.sales.CrmActivity
import steakos.sales.Customer
import steakos.sales.CustomerQuote
import steakos.sales.Opportunity
import steakos.sales.SalesCrmService
import steakos.sales.SalesOrder
import steakos.sales.SalesOrderService
import steakos.sales.SalesQuoteService
import steakos.supplychain.ScmPurchaseService

/**
 * PDR-026 PR3 — kitchen-sink seed + reset for Orange Steak House (DEMO npub).
 */
@Slf4j
class OrangeSteakHouseSeedService {

    static final String MARKER_CUSTOMER = 'OSH Walk-in Guest'
    /** Story-seed presence marker — skip only when base + story already exist. */
    static final String MARKER_STORY_QUOTE = 'OSH: Anniversary ribeye for two'
    static final String DEMO_NPUB = CompanyContextService.DEMO_NPUB

    CatalogueService catalogueService
    FinanceLedgerService financeLedgerService
    FinanceTreasuryService financeTreasuryService
    FinanceRevenueService financeRevenueService
    steakos.finance.PostingRulesetService postingRulesetService
    CompanyContextService companyContextService
    CompanyConfigService companyConfigService
    CompanyCurrencyService companyCurrencyService
    SalesQuoteService salesQuoteService
    SalesOrderService salesOrderService
    SalesCrmService salesCrmService
    ScmPurchaseService scmPurchaseService

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
        } else if (Customer.findByNpubOwnerAndName(npub, MARKER_CUSTOMER)
                && CustomerQuote.findByNpubOwnerAndName(npub, MARKER_STORY_QUOTE)) {
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
        try {
            companyCurrencyService?.ensureInUse(npub, 'EUR')
        } catch (ignored) {
        }

        // --- Catalogue (steak house menu) ---
        Map<String, Long> catByCode = [:]
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
                catByCode[row.code as String] = existing.id
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
            catByCode[row.code as String] = ci?.id
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

        Map story = seedStoryRecords(npub, guest, corp, tourist, meat, wine, linen, catByCode)

        // Metal snapshot note on company
        try {
            companyConfigService?.put(npub, 'demo.pack', 'orange-steak-house-v1', 'demo')
        } catch (ignored) {
        }

        log.info('Orange Steak House seed applied for {}', npub)
        [
                npubOwner     : npub,
                companyId     : demo.id,
                skipped       : false,
                forced        : force,
                customers     : 3,
                vendors       : 3,
                catalogue     : catByCode.size(),
                tasks         : 2,
                quotes        : story.quotes,
                orders        : story.orders,
                invoices      : story.invoices,
                pos           : story.pos,
                opportunities : story.opportunities,
                activities    : story.activities,
                customerIds   : [guest.id, corp.id, tourist.id],
                vendorIds     : [meat.id, wine.id, linen.id],
                catalogueIds  : catByCode.values() as List,
                taskIds       : [t1.id, t2.id],
                message       : force ? 'Demo reset and reseeded' : 'Orange Steak House seed created',
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
        safeDeleteAll(CrmActivity, npub)
        safeDeleteAll(Opportunity, npub)
        safeDeleteAll(Invoice, npub)
        safeDeleteAll(SalesOrder, npub)
        safeDeleteAll(CustomerQuote, npub)
        safeDeleteAll(PurchaseOrder, npub)
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

    /**
     * Related commercial docs with restaurant story spins. Idempotent by document name.
     */
    private Map seedStoryRecords(String npub,
                                 Customer guest, Customer corp, Customer tourist,
                                 Vendor meat, Vendor wine, Vendor linen,
                                 Map<String, Long> cat) {
        Long ribeye = cat['OSH-RIBEYE']
        Long filet = cat['OSH-FILET']
        Long fries = cat['OSH-FRIES']
        Long caesar = cat['OSH-CAESAR']
        Long priv = cat['OSH-PRIVATE']
        Long wineCat = cat['OSH-WINE']

        // --- Guest ---
        upsertQuote(npub, MARKER_STORY_QUOTE, guest, [
                [name: 'Ribeye Steak 300g', quantity: 2, unitPriceEur: 42.00G, catalogueItemId: ribeye],
                [name: 'Wine Pairing Flight', quantity: 1, unitPriceEur: 55.00G, catalogueItemId: wineCat],
                [name: 'House Fries', quantity: 2, unitPriceEur: 6.50G, catalogueItemId: fries],
        ], 'Candlelight anniversary — medium-rare, share the fries, spoil them with Pinot.')

        upsertOrder(npub, 'OSH: Walk-in ribeye night', guest, [
                [name: 'Ribeye Steak 300g', quantity: 1, unitPriceEur: 42.00G, catalogueItemId: ribeye],
                [name: 'Caesar Salad', quantity: 1, unitPriceEur: 9.00G, catalogueItemId: caesar],
                [name: 'House Fries', quantity: 1, unitPriceEur: 6.50G, catalogueItemId: fries],
        ], 'Solo walk-in who ordered like it was a celebration.')

        upsertInvoice(npub, 'OSH: Anniversary dinner check', guest,
                110.00G, 'Guest tab — ribeye for two + flight (DRAFT demo).')

        // --- Corporate ---
        upsertQuote(npub, 'OSH: Corporate wine pairing package', corp, [
                [name: 'Private Dining Package', quantity: 1, unitPriceEur: 450.00G, catalogueItemId: priv],
                [name: 'Wine Pairing Flight', quantity: 12, unitPriceEur: 55.00G, catalogueItemId: wineCat],
                [name: 'Filet Mignon 200g', quantity: 12, unitPriceEur: 48.00G, catalogueItemId: filet],
        ], 'Board dinner — private room, filets all around, flights that impress the CFO.')

        upsertOrder(npub, 'OSH: Board dinner ×12', corp, [
                [name: 'Private Dining Package', quantity: 1, unitPriceEur: 450.00G, catalogueItemId: priv],
                [name: 'Filet Mignon 200g', quantity: 12, unitPriceEur: 48.00G, catalogueItemId: filet],
                [name: 'Wine Pairing Flight', quantity: 12, unitPriceEur: 55.00G, catalogueItemId: wineCat],
        ], 'Confirmed headcount 12 — no speeches longer than a ribeye rest.')

        upsertInvoice(npub, 'OSH: Q4 events retainer', corp,
                1626.00G, 'Corporate events retainer — private dining + pairings (DRAFT).')

        // --- Tour group ---
        upsertQuote(npub, 'OSH: Bus-tour set menus ×40', tourist, [
                [name: 'Ribeye Steak 300g', quantity: 40, unitPriceEur: 38.00G, catalogueItemId: ribeye],
                [name: 'House Fries', quantity: 40, unitPriceEur: 5.00G, catalogueItemId: fries],
                [name: 'Caesar Salad', quantity: 40, unitPriceEur: 7.50G, catalogueItemId: caesar],
        ], 'Coach pulls up at 12:15 — set menus, timed turns, tip jars ready.')

        upsertOrder(npub, 'OSH: Horizon tour lunch', tourist, [
                [name: 'Ribeye Steak 300g', quantity: 40, unitPriceEur: 38.00G, catalogueItemId: ribeye],
                [name: 'House Fries', quantity: 40, unitPriceEur: 5.00G, catalogueItemId: fries],
        ], 'Horizon Tour Group — 40 covers, fries flying, ribeyes sizzling.')

        upsertInvoice(npub, 'OSH: Coach party deposit', tourist,
                860.00G, 'Deposit for bus-tour set menus ×40 (DRAFT).')

        // --- Vendor POs ---
        upsertPo(npub, 'OSH: Friday ribeye drop', meat, [
                [name: 'Ribeye Steak 300g', quantity: 48, unitPriceEur: 18.50G, catalogueItemId: ribeye],
                [name: 'Filet Mignon 200g', quantity: 24, unitPriceEur: 22.00G, catalogueItemId: filet],
        ], 'Prairie Prime — Friday service drop before the rush.')

        upsertPo(npub, 'OSH: Pinot flight restock', wine, [
                [name: 'Wine Pairing Flight', quantity: 30, unitPriceEur: 22.00G, catalogueItemId: wineCat],
        ], 'Cellar & Vine — Pinot + friends for the pairing board.')

        upsertPo(npub, 'OSH: White napkin emergency (wedding spill)', linen, [
                [name: 'White cloth napkins (emergency case)', quantity: 200, unitPriceEur: 0.85G],
        ], 'WhiteCloth — wedding party vs. red wine. Napkins win.')

        // --- CRM opportunities + activities ---
        Opportunity corpOpp = ensureOpportunity(npub, 'OSH: Private dining for Orange Corp', corp, [
                stage            : 'PROPOSAL',
                expectedAmountEur: 1626.00G,
                contactName      : 'Events Desk',
                contactEmail     : 'events@orangesteak.example',
                ownerName        : "Maitre d' Maya",
                source           : 'REFERRAL',
                description      : 'Recurring private dining + wine flights for Orange Corporate Events.',
        ]).opportunity as Opportunity
        upsertActivity(npub, corpOpp, 'OSH: Call — confirm headcount 12', 'CALL', 'PHONE',
                'Quick call: board wants medium filets, no blue cheese drama.')
        upsertActivity(npub, corpOpp, 'OSH: Email — pairing menu PDF', 'EMAIL', 'EMAIL',
                'Sent the Pinot-forward pairing sheet and private-room floor plan.')

        Opportunity tourOpp = ensureOpportunity(npub, 'OSH: Horizon coach repeat bookings', tourist, [
                stage            : 'QUALIFIED',
                expectedAmountEur: 1720.00G,
                contactName      : 'Horizon Dispatch',
                contactEmail     : 'horizon@example.com',
                ownerName        : 'Front-of-house Sam',
                source           : 'PARTNER',
                description      : 'Weekly coach lunches — set menus, timed turns, tip-friendly.',
        ]).opportunity as Opportunity
        upsertActivity(npub, tourOpp, 'OSH: Note — bus parks on side street', 'NOTE', null,
                'Drivers prefer the alley — host seats them by the pass so fries stay hot.')
        upsertActivity(npub, tourOpp, 'OSH: Call — next Thursday ×40', 'CALL', 'PHONE',
                'Dispatch confirmed next Thursday: 40 covers, same ribeye set, salad optional.')

        [
                quotes       : countByNames(CustomerQuote, npub, [
                        MARKER_STORY_QUOTE,
                        'OSH: Corporate wine pairing package',
                        'OSH: Bus-tour set menus ×40',
                ]),
                orders       : countByNames(SalesOrder, npub, [
                        'OSH: Walk-in ribeye night',
                        'OSH: Board dinner ×12',
                        'OSH: Horizon tour lunch',
                ]),
                invoices     : countByNames(Invoice, npub, [
                        'OSH: Anniversary dinner check',
                        'OSH: Q4 events retainer',
                        'OSH: Coach party deposit',
                ]),
                pos          : countByNames(PurchaseOrder, npub, [
                        'OSH: Friday ribeye drop',
                        'OSH: Pinot flight restock',
                        'OSH: White napkin emergency (wedding spill)',
                ]),
                opportunities: countByNames(Opportunity, npub, [
                        'OSH: Private dining for Orange Corp',
                        'OSH: Horizon coach repeat bookings',
                ]),
                activities   : countByNames(CrmActivity, npub, [
                        'OSH: Call — confirm headcount 12',
                        'OSH: Email — pairing menu PDF',
                        'OSH: Note — bus parks on side street',
                        'OSH: Call — next Thursday ×40',
                ]),
        ]
    }

    private static int countByNames(Class domain, String npub, List<String> names) {
        names.count { String n -> domain.findByNpubOwnerAndName(npub, n) != null } as int
    }

    private CustomerQuote upsertQuote(String npub, String name, Customer customer, List lines, String description) {
        CustomerQuote existing = CustomerQuote.findByNpubOwnerAndName(npub, name)
        if (existing) return null
        if (!salesQuoteService) return null
        salesQuoteService.createQuote(npub, [
                name        : name,
                description : description,
                customerId  : customer.id,
                currencyCode: 'EUR',
                lines       : lines,
        ])
    }

    private SalesOrder upsertOrder(String npub, String name, Customer customer, List lines, String description) {
        SalesOrder existing = SalesOrder.findByNpubOwnerAndName(npub, name)
        if (existing) return null
        if (!salesOrderService) return null
        salesOrderService.createOrder(npub, [
                name        : name,
                description : description,
                customerId  : customer.id,
                currencyCode: 'EUR',
                lines       : lines,
        ])
    }

    private Invoice upsertInvoice(String npub, String name, Customer customer, BigDecimal amountEur, String description) {
        Invoice existing = Invoice.findByNpubOwnerAndName(npub, name)
        if (existing) return null
        if (!financeRevenueService) return null
        financeRevenueService.createInvoice(npub, [
                name           : name,
                description    : description,
                customerId     : customer.id,
                customerName   : customer.name,
                customerContact: customer.email,
                amountEur      : amountEur,
                currencyCode   : 'EUR',
                issue          : false,
        ])
    }

    private PurchaseOrder upsertPo(String npub, String name, Vendor vendor, List lines, String description) {
        PurchaseOrder existing = PurchaseOrder.findByNpubOwnerAndName(npub, name)
        if (existing) return null
        if (!scmPurchaseService) return null
        scmPurchaseService.createPurchaseOrder(npub, [
                name        : name,
                description : description,
                vendorId    : vendor.id,
                currencyCode: 'EUR',
                lines       : lines,
        ])
    }

    private Map ensureOpportunity(String npub, String name, Customer customer, Map fields) {
        Opportunity existing = Opportunity.findByNpubOwnerAndName(npub, name)
        if (existing) {
            return [opportunity: existing, created: false]
        }
        if (!salesCrmService) {
            return [opportunity: null, created: false]
        }
        Opportunity opp = salesCrmService.createOpportunity(npub, [
                name             : name,
                description      : fields.description,
                customerId       : customer.id,
                stage            : fields.stage ?: 'LEAD',
                expectedAmountEur: fields.expectedAmountEur,
                currencyCode     : 'EUR',
                contactName      : fields.contactName,
                contactEmail     : fields.contactEmail,
                ownerName        : fields.ownerName,
                source           : fields.source,
        ])
        [opportunity: opp, created: true]
    }

    private CrmActivity upsertActivity(String npub, Opportunity opp, String name, String kind, String channel, String description) {
        if (!opp?.id) return null
        CrmActivity existing = CrmActivity.findByNpubOwnerAndName(npub, name)
        if (existing) return null
        if (!salesCrmService) return null
        Map fields = [
                name       : name,
                kind       : kind,
                description: description,
        ]
        if (channel) fields.channel = channel
        salesCrmService.createActivity(opp.id, npub, fields)
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
        c = new Customer(
                npubOwner: npub,
                name: name,
                email: email,
                customerKind: kind,
                currencyCode: 'EUR',
                active: Boolean.TRUE
        )
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
