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
import steakos.human.Candidate
import steakos.procurement.Vendor
import steakos.sales.Customer
import steakos.sales.CustomerQuote
import steakos.sales.SalesOrder

/**
 * PDR-026 — kitchen-sink demo pack for <b>21 Toys Co.</b>
 * Pure product company: Bitcoin-themed kids toys (catalogue GOODS only).
 */
@Slf4j
class TwentyOneToysSeedService {

    static final String MARKER_CUSTOMER = '21T Marker — Satoshi Playroom'
    static final String NPUB = CompanyContextService.TWENTY_ONE_TOYS_NPUB
    static final String HQ_SITE = 'Austin Fulfillment Loft'

    CatalogueService catalogueService
    FinanceLedgerService financeLedgerService
    FinanceTreasuryService financeTreasuryService
    steakos.finance.PostingRulesetService postingRulesetService
    CompanyContextService companyContextService
    CompanyConfigService companyConfigService
    CompanyCurrencyService companyCurrencyService

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
        } else if (Customer.findByNpubOwnerAndName(npub, MARKER_CUSTOMER)) {
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
        safeDeleteAll(Invoice, npub)
        safeDeleteAll(SalesOrder, npub)
        safeDeleteAll(CustomerQuote, npub)
        safeDeleteAll(steakos.procurement.PurchaseOrder, npub)
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
