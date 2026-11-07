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
import steakos.human.Candidate
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
 * PDR-026 — kitchen-sink demo pack for <b>21 Toys Co.</b>
 * Pure product company: Bitcoin-themed kids toys (catalogue GOODS only).
 */
@Slf4j
class TwentyOneToysSeedService {

    static final String MARKER_CUSTOMER = '21T Marker — Satoshi Playroom'
    static final String STORY_QUOTE_MARKER = '21T: Satoshi Playroom starter pack (plush + stack cups)'
    static final String NPUB = CompanyContextService.TWENTY_ONE_TOYS_NPUB
    static final String HQ_SITE = 'Austin Fulfillment Loft'

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
    ScmPurchaseService scmPurchaseService
    SalesCrmService salesCrmService

    @Transactional
    Map ensureAndSeed() {
        companyContextService?.ensureBootstrapped()
        seed(false)
    }

    @Transactional
    Map seed(boolean force) {
        String npub = NPUB
        companyContextService?.ensureBootstrapped()
        // Reload after bootstrap so we do not hit a stale LegalEntity version.
        LegalEntity company = LegalEntity.findByCode(LegalEntity.CODE_TWENTY_ONE_TOYS)
        if (!company) {
            throw new IllegalStateException('21 Toys Co. company shell missing')
        }
        company = LegalEntity.get(company.id)

        if (force) {
            wipeDemoData(npub)
            company = LegalEntity.get(company.id)
        } else if (Customer.findByNpubOwnerAndName(npub, MARKER_CUSTOMER)
                && CustomerQuote.findByNpubOwnerAndName(npub, STORY_QUOTE_MARKER)) {
            return [
                    npubOwner: npub,
                    companyId: company.id,
                    skipped  : true,
                    message  : '21 Toys Co. seed already present',
            ]
        }

        if (company.npubOwner != npub) {
            company.npubOwner = npub
        }
        company.name = CompanyContextService.TWENTY_ONE_TOYS_NAME
        company.countryCode = 'US'
        company.functionalCurrency = 'USD'
        company.dualSats = Boolean.TRUE
        company.registeredAddressText = '2100 Satoshi Ave, Austin, TX 78702, USA'
        company.website = 'https://21toys.example'
        company.mainEmail = 'hello@21toys.example'
        company.description = '21 Toys Co. — Bitcoin-themed kids toys and playsets. Pure product demo company (no services).'
        company.legalForm = 'LLC'
        company.save(failOnError: true)

        financeLedgerService?.ensureChartSeeded(npub)
        try {
            postingRulesetService?.ensureSeeded(npub)
        } catch (ignored) {
        }
        financeTreasuryService?.ensureDefaultPositions(npub)
        try {
            companyCurrencyService?.ensureInUse(npub, 'USD')
        } catch (ignored) {
        }

        Site hq = ensureSite(company, npub)

        // --- Catalogue: GOODS only (pure product) ---
        List catIds = []
        [
                [name: 'Soft Plush Satoshi', code: '21T-PLUSH-SAT', unit: 'ea', price: '24.00'],
                [name: 'Wooden “21” Puzzle', code: '21T-BLOCK-21', unit: 'set', price: '18.50'],
                [name: 'Lightning Bolt Rattle', code: '21T-BOLT-RATTLE', unit: 'ea', price: '12.00'],
                [name: 'HODL Bedtime Storybook', code: '21T-HODL-BOOK', unit: 'ea', price: '16.00'],
                [name: 'Orange-Pill Crayon Set', code: '21T-PILL-CRAYONS', unit: 'set', price: '9.50'],
                [name: 'Blockchain Building Blocks', code: '21T-CHAIN-BLOCKS', unit: 'set', price: '32.00'],
                [name: 'Mini Hardware-Wallet Toy', code: '21T-WALLET-TOY', unit: 'ea', price: '14.00'],
                [name: 'Peer-to-Peer Board Game', code: '21T-P2P-GAME', unit: 'box', price: '29.00'],
                [name: 'Stack Sats Stacking Cups', code: '21T-STACK-CUPS', unit: 'set', price: '15.00'],
                [name: 'Node Night-Light', code: '21T-NODE-LIGHT', unit: 'ea', price: '22.00'],
        ].each { Map row ->
            CatalogueItem existing = CatalogueItem.findByNpubOwnerAndCode(npub, row.code as String)
            if (existing) {
                catIds << existing.id
                return
            }
            CatalogueItem ci = catalogueService.create(npub, [
                    name        : row.name,
                    code        : row.code,
                    nature      : CatalogueItem.NATURE_GOODS,
                    unit        : row.unit,
                    listPrice   : new BigDecimal(row.price as String),
                    currencyCode: 'USD',
                    canBeSold   : true,
                    canBeBought : true,
                    active      : true,
                    description : "${row.name} — 21 Toys Co. demo SKU (GOODS).",
            ])
            catIds << ci?.id
        }

        // --- Customers ---
        Customer marker = upsertCustomer(npub, MARKER_CUSTOMER, 'play@satoshiplayroom.example', 'PERSON')
        Customer meetup = upsertCustomer(npub, 'Austin Bitcoin Kids Meetup', 'kids@atxbitcoin.example', 'ORGANIZATION')
        Customer boutique = upsertCustomer(npub, 'Orange Pill Toy Boutique', 'buy@orangepilltoys.example', 'ORGANIZATION')
        Customer school = upsertCustomer(npub, 'Westlake STEM Gift Shop', 'gifts@westlakestem.example', 'ORGANIZATION')
        Customer family = upsertCustomer(npub, 'Rivera Family (wholesale inquiry)', 'rivera.family@example.com', 'PERSON')

        // --- Vendors ---
        Vendor plush = upsertVendor(npub, 'Hill Country Plush Works', 'orders@hcplush.example')
        Vendor wood = upsertVendor(npub, 'Cedar Block Mill', 'mill@cedarblock.example')
        Vendor print = upsertVendor(npub, 'Lightning Print Co-op', 'press@lnprint.example')
        Vendor pack = upsertVendor(npub, 'Sats Packaging Supply', 'ship@satspack.example')
        Vendor elec = upsertVendor(npub, 'Toytronics Novelty LED', 'parts@toytronics.example')

        // --- Staff story (Candidates hired — no extra logins) ---
        List staff = [
                upsertStaff(npub, 'Nova Satoshi', 'nova@21toys.example', 'CEO · Founder', 'FOUNDER'),
                upsertStaff(npub, 'Jules Merkle', 'jules@21toys.example', 'Ops · Fulfillment Lead', 'EMPLOYEE'),
                upsertStaff(npub, 'Kim UTXO', 'kim@21toys.example', 'Product · Toy Design', 'EMPLOYEE'),
                upsertStaff(npub, 'Sam Channel', 'sam@21toys.example', 'Finance · Controller', 'EMPLOYEE'),
                upsertStaff(npub, 'Riley Relay', 'riley@21toys.example', 'Sales · Wholesale', 'EMPLOYEE'),
        ]

        // --- Tasks ---
        Task t1 = upsertTask(npub, '21T: Restock plush Satoshi for meetup', 'Austin Bitcoin Kids Meetup — soft plush + stack cups.', 3, 3)
        Task t2 = upsertTask(npub, '21T: Quote Orange Pill Boutique spring pack', 'Crayons + storybook + P2P game wholesale carton.', 2, 3)
        Task t3 = upsertTask(npub, '21T: QC Node Night-Light batch', 'Toytronics LED batch — brightness + battery door.', 2, 2)

        Map story = seedStoryRecords(npub, [
                marker  : marker,
                meetup  : meetup,
                boutique: boutique,
                school  : school,
                family  : family,
        ], [
                plush: plush,
                wood : wood,
                print: print,
                pack : pack,
                elec : elec,
        ])

        try {
            companyConfigService?.put(npub, 'demo.pack', 'twenty-one-toys-v1', 'demo')
            companyConfigService?.put(npub, 'demo.city', 'Austin, TX', 'demo')
            companyConfigService?.put(npub, 'demo.archetype', 'pure-product', 'demo')
        } catch (ignored) {
        }

        log.info('21 Toys Co. seed applied for {}', npub)
        [
                npubOwner   : npub,
                companyId   : company.id,
                siteId      : hq?.id,
                skipped     : false,
                forced      : force,
                customers   : 5,
                vendors     : 5,
                catalogue   : catIds.size(),
                staff       : staff.size(),
                tasks       : 3,
                quotes      : story.quotes,
                orders      : story.orders,
                invoices    : story.invoices,
                purchaseOrders: story.purchaseOrders,
                opportunities: story.opportunities,
                activities  : story.activities,
                customerIds : [marker.id, meetup.id, boutique.id, school.id, family.id],
                vendorIds   : [plush.id, wood.id, print.id, pack.id, elec.id],
                catalogueIds: catIds,
                message     : force ? '21 Toys Co. reset and reseeded' : '21 Toys Co. seed created',
        ]
    }

    @Transactional
    Map resetDemo() {
        seed(true)
    }

    @Transactional
    void wipeDemoData(String npub) {
        if (npub != NPUB) {
            throw new SecurityException("Refuse wipe for non-21-Toys npub: ${npub}")
        }
        safeDeleteAll(CrmActivity, npub)
        safeDeleteAll(Opportunity, npub)
        safeDeleteAll(Invoice, npub)
        safeDeleteAll(SalesOrder, npub)
        safeDeleteAll(CustomerQuote, npub)
        safeDeleteAll(PurchaseOrder, npub)
        safeDeleteAll(Task, npub)
        safeDeleteAll(Candidate, npub)
        safeDeleteAll(Customer, npub)
        safeDeleteAll(Vendor, npub)
        safeDeleteAll(CatalogueItem, npub)
        try {
            safeDeleteAll(steakos.supplychain.StockMove, npub)
        } catch (ignored) {
        }
        log.info('Wiped 21 Toys Co. demo data for {}', npub)
    }

    /**
     * Idempotent commercial story pack — keyed by document / opportunity name.
     * GOODS catalogue lines only; currency USD; DRAFT/SENT (no settle).
     */
    private Map seedStoryRecords(String npub, Map customers, Map vendors) {
        CatalogueItem plushSku = cat(npub, '21T-PLUSH-SAT')
        CatalogueItem blockSku = cat(npub, '21T-BLOCK-21')
        CatalogueItem boltSku = cat(npub, '21T-BOLT-RATTLE')
        CatalogueItem bookSku = cat(npub, '21T-HODL-BOOK')
        CatalogueItem crayonSku = cat(npub, '21T-PILL-CRAYONS')
        CatalogueItem chainSku = cat(npub, '21T-CHAIN-BLOCKS')
        CatalogueItem walletSku = cat(npub, '21T-WALLET-TOY')
        CatalogueItem gameSku = cat(npub, '21T-P2P-GAME')
        CatalogueItem cupsSku = cat(npub, '21T-STACK-CUPS')
        CatalogueItem lightSku = cat(npub, '21T-NODE-LIGHT')

        int quotes = 0, orders = 0, invoices = 0, pos = 0, opps = 0, acts = 0

        // --- Quotes (customers) ---
        if (ensureQuote(npub, STORY_QUOTE_MARKER, customers.marker as Customer,
                'Residential starter: Soft Plush Satoshi + Stack Sats cups. HODL the bedtime ritual.',
                [goodsLine(plushSku, 2), goodsLine(cupsSku, 1)], true)) {
            quotes++
        }
        if (ensureQuote(npub, '21T: Meetup merch carton — don\'t lose the seed phrase sticker sheet',
                customers.meetup as Customer,
                'Wholesale gift carton for Austin Bitcoin Kids Meetup — plush, wallet toy, stack cups. Seed-phrase sticker sheet reminder in the pack note.',
                [goodsLine(plushSku, 12), goodsLine(walletSku, 12), goodsLine(cupsSku, 12)], true)) {
            quotes++
        }
        if (ensureQuote(npub, '21T: Boutique spring orange-pill crayon crate',
                customers.boutique as Customer,
                'Orange Pill Toy Boutique spring crate — crayons, HODL storybook, P2P board game.',
                [goodsLine(crayonSku, 24), goodsLine(bookSku, 12), goodsLine(gameSku, 6)], false)) {
            quotes++
        }
        if (ensureQuote(npub, '21T: Westlake STEM aisle — blocks + wooden 21',
                customers.school as Customer,
                'STEM gift-shop aisle refresh: Blockchain Building Blocks + Wooden “21” Puzzle.',
                [goodsLine(chainSku, 8), goodsLine(blockSku, 10)], false)) {
            quotes++
        }
        if (ensureQuote(npub, '21T: Rivera family HODL starter (inquiry quote)',
                customers.family as Customer,
                'Family wholesale inquiry — soft plush + night-light for the kids\' node corner.',
                [goodsLine(plushSku, 4), goodsLine(lightSku, 2)], false)) {
            quotes++
        }

        // --- Sales orders (DRAFT — playful HODL / Lightning copy) ---
        if (ensureOrder(npub, '21T: Order — Satoshi Playroom HODL restock (Lightning never sleeps)',
                customers.marker as Customer,
                'Playroom restock: plush + bolt rattle + Node Night-Light. Pay when the channel opens.',
                [goodsLine(plushSku, 3), goodsLine(boltSku, 2), goodsLine(lightSku, 1)])) {
            orders++
        }
        if (ensureOrder(npub, '21T: Order — Meetup Node Night gifts for volunteers',
                customers.meetup as Customer,
                'Volunteer thank-you: Node Night-Lights that sync with the block clock (figuratively).',
                [goodsLine(lightSku, 8), goodsLine(bookSku, 8)])) {
            orders++
        }
        if (ensureOrder(npub, '21T: Order — Boutique carton (orange-pill aisle)',
                customers.boutique as Customer,
                'Boutique confirmed carton — crayons + storybooks. Not financial advice; just crayons.',
                [goodsLine(crayonSku, 36), goodsLine(bookSku, 18)])) {
            orders++
        }

        // --- Invoices (DRAFT — mock-payment safe) ---
        if (ensureInvoice(npub, '21T: Invoice — Satoshi Playroom (stack those sats cups)',
                customers.marker as Customer,
                'DRAFT AR for playroom starter. DualSats showcase OK — settle later over Lightning.',
                '87.00')) {
            invoices++
        }
        if (ensureInvoice(npub, '21T: Invoice — Meetup merch (pay when the mempool clears)',
                customers.meetup as Customer,
                'Meetup carton AR. HODL the invoice until the treasurer\'s channel is online.',
                '612.00')) {
            invoices++
        }
        if (ensureInvoice(npub, '21T: Invoice — Boutique spring crate (orange pill, green light)',
                customers.boutique as Customer,
                'Boutique spring pack AR — DRAFT, mock rails only.',
                '522.00')) {
            invoices++
        }

        // --- Purchase orders (vendors) ---
        if (ensurePo(npub, '21T: Soft plush Satoshi restock (extra squish)',
                vendors.plush as Vendor,
                'Hill Country Plush — soft Satoshi restock, extra squish QC.',
                [goodsLine(plushSku, 48, '9.50')])) {
            pos++
        }
        if (ensurePo(npub, '21T: Wooden 21 puzzle blanks',
                vendors.wood as Vendor,
                'Cedar Block Mill — unfinished “21” puzzle blanks for Kim\'s laser pass.',
                [goodsLine(blockSku, 60, '6.25')])) {
            pos++
        }
        if (ensurePo(npub, '21T: Seed-phrase sticker print run',
                vendors.print as Vendor,
                'Lightning Print Co-op — seed-phrase sticker sheets (demo art, not real seeds) + HODL book covers.',
                [goodsLine(bookSku, 100, '3.00'), goodsLine(walletSku, 40, '4.50')])) {
            pos++
        }
        if (ensurePo(npub, '21T: LED night-light burn-in batch',
                vendors.elec as Vendor,
                'Toytronics — Node Night-Light PCB/LED burn-in batch before loft QC.',
                [goodsLine(lightSku, 36, '8.75')])) {
            pos++
        }
        if (ensurePo(npub, '21T: Sats Packaging — meetup carton sleeves',
                vendors.pack as Vendor,
                'Sats Packaging — branded sleeves for meetup merch cartons (stack cups insert trays).',
                [goodsLine(cupsSku, 24, '2.10')])) {
            pos++
        }

        // --- Opportunities + CRM activities ---
        Map boutiqueOpp = ensureOpportunity(npub,
                '21T: Opp — Orange Pill Boutique wholesale spring',
                customers.boutique as Customer,
                'Wholesale spring aisle for Orange Pill Toy Boutique — crayons + games.',
                Opportunity.STAGE_PROPOSAL, '2400.00', 'Riley Relay', 'PARTNER')
        if (boutiqueOpp.created) opps++
        Opportunity oppBoutique = boutiqueOpp.opportunity as Opportunity
        if (ensureActivity(npub, oppBoutique, '21T: EMAIL — Boutique spring pricing sheet',
                'EMAIL', 'EMAIL', 'Sent wholesale pricing + crayon crate MOQ. Orange-pill them gently.')) {
            acts++
        }
        if (ensureActivity(npub, oppBoutique, '21T: CALL — Boutique buyer walkthrough',
                'CALL', 'PHONE', 'Walked spring crate SKUs; they want extra squish plush samples next.')) {
            acts++
        }

        Map meetupOpp = ensureOpportunity(npub,
                '21T: Opp — Austin Kids Meetup gift packs',
                customers.meetup as Customer,
                'Recurring meetup gift packs — Node Night-Lights + stack cups for new families.',
                Opportunity.STAGE_QUALIFIED, '900.00', 'Riley Relay', 'REFERRAL')
        if (meetupOpp.created) opps++
        Opportunity oppMeetup = meetupOpp.opportunity as Opportunity
        if (ensureActivity(npub, oppMeetup, '21T: NOTE — Meetup volunteer count',
                'NOTE', null, 'About 40 kids expected; order Node Night-Lights for volunteers first.')) {
            acts++
        }
        if (ensureActivity(npub, oppMeetup, '21T: EMAIL — Meetup merch carton ETA',
                'EMAIL', 'EMAIL', 'Confirmed carton ETA before Saturday Node Night. Don\'t lose the seed phrase sticker sheet.')) {
            acts++
        }

        Map stemOpp = ensureOpportunity(npub,
                '21T: Opp — Westlake STEM aisle refresh',
                customers.school as Customer,
                'STEM gift shop wants Blockchain Blocks + Wooden 21 for the science aisle.',
                Opportunity.STAGE_LEAD, '650.00', 'Riley Relay', 'COLD')
        if (stemOpp.created) opps++
        Opportunity oppStem = stemOpp.opportunity as Opportunity
        if (ensureActivity(npub, oppStem, '21T: CALL — STEM shop intro',
                'CALL', 'PHONE', 'Intro call with gift-shop lead — interested in wooden puzzles for Maker Week.')) {
            acts++
        }

        Map familyOpp = ensureOpportunity(npub,
                '21T: Opp — Rivera Family wholesale inquiry',
                customers.family as Customer,
                'Rivera family exploring small wholesale for cousins\' HODL playroom corner.',
                Opportunity.STAGE_QUALIFIED, '280.00', 'Riley Relay', 'WEB')
        if (familyOpp.created) opps++
        Opportunity oppFamily = familyOpp.opportunity as Opportunity
        if (ensureActivity(npub, oppFamily, '21T: EMAIL — Rivera starter SKU list',
                'EMAIL', 'EMAIL', 'Sent plush + night-light starter list. Lightning settlement optional on DEMO.')) {
            acts++
        }
        if (ensureActivity(npub, oppFamily, '21T: NOTE — Family prefers USD list prices',
                'NOTE', null, 'Keep quotes in USD; dualSats hint only — no live payment on DEMO.')) {
            acts++
        }

        [
                quotes         : quotes,
                orders         : orders,
                invoices       : invoices,
                purchaseOrders : pos,
                opportunities  : opps,
                activities     : acts,
        ]
    }

    private static CatalogueItem cat(String npub, String code) {
        CatalogueItem ci = CatalogueItem.findByNpubOwnerAndCode(npub, code)
        if (!ci) {
            throw new IllegalStateException("21 Toys catalogue SKU missing: ${code}")
        }
        ci
    }

    private static Map goodsLine(CatalogueItem ci, Number qty, String unitOverride = null) {
        [
                name           : ci.name,
                quantity       : qty,
                unitPriceEur   : unitOverride != null ? new BigDecimal(unitOverride) : ci.listPrice,
                catalogueItemId: ci.id,
                productRefType : 'GOODS',
        ]
    }

    /** @return true when newly created */
    private boolean ensureQuote(String npub, String name, Customer customer, String desc, List lines, boolean send) {
        if (CustomerQuote.findByNpubOwnerAndName(npub, name)) return false
        CustomerQuote q = salesQuoteService.createQuote(npub, [
                name        : name,
                description : desc,
                customerId  : customer.id,
                currencyCode: 'USD',
                lines       : lines,
        ])
        if (send) {
            try {
                salesQuoteService.sendQuote(q.id, npub)
            } catch (ignored) {
            }
        }
        true
    }

    private boolean ensureOrder(String npub, String name, Customer customer, String desc, List lines) {
        if (SalesOrder.findByNpubOwnerAndName(npub, name)) return false
        salesOrderService.createOrder(npub, [
                name        : name,
                description : desc,
                customerId  : customer.id,
                currencyCode: 'USD',
                lines       : lines,
        ])
        true
    }

    private boolean ensureInvoice(String npub, String name, Customer customer, String desc, String amount) {
        if (Invoice.findByNpubOwnerAndName(npub, name)) return false
        financeRevenueService.createInvoice(npub, [
                name           : name,
                description    : desc,
                customerId     : customer.id,
                customerName   : customer.name,
                customerContact: customer.email,
                amountEur      : new BigDecimal(amount),
                currencyCode   : 'USD',
                referenceText  : name,
                issue          : false,
        ])
        true
    }

    private boolean ensurePo(String npub, String name, Vendor vendor, String desc, List lines) {
        if (PurchaseOrder.findByNpubOwnerAndName(npub, name)) return false
        scmPurchaseService.createPurchaseOrder(npub, [
                name        : name,
                description : desc,
                vendorId    : vendor.id,
                currencyCode: 'USD',
                lines       : lines,
        ])
        true
    }

    /** @return [opportunity: Opportunity, created: boolean] */
    private Map ensureOpportunity(String npub, String name, Customer customer, String desc,
                                  String stage, String expectedAmount, String owner, String source) {
        Opportunity existing = Opportunity.findByNpubOwnerAndName(npub, name)
        if (existing) {
            return [opportunity: existing, created: false]
        }
        Opportunity opp = salesCrmService.createOpportunity(npub, [
                name             : name,
                description      : desc,
                customerId       : customer.id,
                contactName      : customer.name,
                contactEmail     : customer.email,
                stage            : stage,
                expectedAmountEur: new BigDecimal(expectedAmount),
                currencyCode     : 'USD',
                ownerName        : owner,
                source           : source,
        ])
        [opportunity: opp, created: true]
    }

    private boolean ensureActivity(String npub, Opportunity opp, String name, String kind, String channel, String desc) {
        if (CrmActivity.findByNpubOwnerAndName(npub, name)) return false
        Map fields = [
                name       : name,
                kind       : kind,
                description: desc,
                status     : 'OPEN',
        ]
        if (channel) fields.channel = channel
        salesCrmService.createActivity(opp.id, npub, fields)
        true
    }

    private Site ensureSite(LegalEntity company, String npub) {
        Site site = Site.findByNpubOwnerAndName(npub, HQ_SITE)
        if (site) {
            site.legalEntity = company
            site.addressText = '2100 Satoshi Ave, Austin, TX 78702, USA'
            site.active = Boolean.TRUE
            site.save(failOnError: true)
            return site
        }
        site = new Site(
                npubOwner: npub,
                name: HQ_SITE,
                legalEntity: company,
                siteType: 'WAREHOUSE',
                addressText: '2100 Satoshi Ave, Austin, TX 78702, USA',
                description: 'Pick/pack loft — plush QC table, wood puzzle press, LED toy burn-in shelf.',
                active: Boolean.TRUE
        )
        site.markCreatedBy(npub)
        site.save(failOnError: true)
        site
    }

    private static void safeDeleteAll(Class domain, String npub) {
        domain.findAllByNpubOwner(npub).each { row ->
            try {
                row.delete(flush: false)
            } catch (Exception ignored) {
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
                currencyCode: 'USD',
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

    private static Candidate upsertStaff(String npub, String name, String email, String roleLabel, String kindHint) {
        Candidate c = Candidate.findByNpubOwnerAndEmail(npub, email)
        if (c) return c
        c = new Candidate(
                npubOwner: npub,
                name: name,
                email: email,
                roleLabel: roleLabel,
                stage: 'HIRED',
                source: 'Demo seed · 21 Toys Co.',
                description: "Toys persona (${kindHint}) — story only, no login.",
                hiredAt: daysAgo(90),
                appliedAt: daysAgo(120),
        )
        c.save(failOnError: true)
        c
    }

    private static Date daysAgo(int days) {
        Calendar cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -days)
        cal.time
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
