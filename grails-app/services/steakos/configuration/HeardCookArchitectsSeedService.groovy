// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.configuration

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.catalogue.CatalogueItem
import steakos.catalogue.CatalogueService
import steakos.capability.Project
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
 * PDR-026 — kitchen-sink demo pack for Heard & Cook Architects
 * (Indian Rocks Beach, FL — residential/commercial design + plan media + kitchen studio).
 */
@Slf4j
class HeardCookArchitectsSeedService {

    static final String MARKER_CUSTOMER = 'H&C Marker — Clearwater Bay Residence'
    static final String NPUB = CompanyContextService.HEARD_COOK_NPUB
    static final String HQ_SITE = 'Indian Rocks Beach Studio'

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

    @Transactional
    Map seed(boolean force) {
        String npub = NPUB
        companyContextService?.ensureBootstrapped()
        LegalEntity company = LegalEntity.findByCode(LegalEntity.CODE_HEARD_COOK)
        if (!company) {
            throw new IllegalStateException('Heard & Cook company shell missing')
        }
        if (company.npubOwner != npub) {
            company.npubOwner = npub
            company.save(failOnError: true)
        }
        // Florida / USD branding
        company.countryCode = 'US'
        company.functionalCurrency = 'USD'
        company.registeredAddressText = '1508 Gulf Blvd, Indian Rocks Beach, FL 33785, USA'
        company.description = 'Heard & Cook Architects — residential & commercial design, plan media products, and Billionaire Kitchen Studio concepts. Demo company.'
        company.save(failOnError: true)

        if (force) {
            wipeDemoData(npub)
        } else if (Customer.findByNpubOwnerAndName(npub, MARKER_CUSTOMER)) {
            return [
                    npubOwner: npub,
                    companyId: company.id,
                    skipped  : true,
                    message  : 'Heard & Cook seed already present',
            ]
        }

        financeLedgerService?.ensureChartSeeded(npub)
        try {
            postingRulesetService?.ensureSeeded(npub)
        } catch (ignored) {
        }
        financeTreasuryService?.ensureDefaultPositions(npub)

        Site hq = ensureSite(company, npub)

        // --- Catalogue: services ---
        List catIds = []
        [
                [name: 'Residential Schematic Design', code: 'HC-RES-SCH', nature: CatalogueItem.NATURE_SERVICE, unit: 'phase', price: '18500.00', sold: true, bought: false],
                [name: 'Commercial Design Development', code: 'HC-COM-DD', nature: CatalogueItem.NATURE_SERVICE, unit: 'phase', price: '42000.00', sold: true, bought: false],
                [name: 'Construction Document Set', code: 'HC-CD-SET', nature: CatalogueItem.NATURE_SERVICE, unit: 'set', price: '28000.00', sold: true, bought: false],
                [name: 'Permit Expediting Package', code: 'HC-PERMIT', nature: CatalogueItem.NATURE_SERVICE, unit: 'pkg', price: '4500.00', sold: true, bought: false],
                [name: 'Billionaire Kitchen Concept Studio', code: 'HC-KIT-CONCEPT', nature: CatalogueItem.NATURE_SERVICE, unit: 'studio', price: '95000.00', sold: true, bought: false],
                [name: 'Kitchen Layout & Millwork Narrative', code: 'HC-KIT-LAYOUT', nature: CatalogueItem.NATURE_SERVICE, unit: 'pkg', price: '22000.00', sold: true, bought: false],
                // Products — plan media & merch
                [name: 'Paper Plan Copy Set (Arch D)', code: 'HC-PLAN-PAPER', nature: CatalogueItem.NATURE_GOODS, unit: 'set', price: '185.00', sold: true, bought: true],
                [name: 'Digital Plans on DVD', code: 'HC-PLAN-DVD', nature: CatalogueItem.NATURE_GOODS, unit: 'disc', price: '95.00', sold: true, bought: true],
                [name: 'Project Demo Video DVD', code: 'HC-VIDEO-DVD', nature: CatalogueItem.NATURE_GOODS, unit: 'disc', price: '49.00', sold: true, bought: true],
                [name: 'Heard & Cook Tote Bag', code: 'HC-MERCH-TOTE', nature: CatalogueItem.NATURE_GOODS, unit: 'ea', price: '28.00', sold: true, bought: true],
                [name: 'Gulf Coast Sketchbook', code: 'HC-MERCH-BOOK', nature: CatalogueItem.NATURE_GOODS, unit: 'ea', price: '36.00', sold: true, bought: true],
                [name: 'IRB Studio Hard Hat (signed)', code: 'HC-MERCH-HAT', nature: CatalogueItem.NATURE_GOODS, unit: 'ea', price: '75.00', sold: true, bought: true],
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
                    description: row.name,
            ])
            catIds << ci?.id
        }

        // --- Clients ---
        Customer marker = upsertCustomer(npub, MARKER_CUSTOMER, 'owner@clearwaterbay.example', 'PERSON')
        Customer condo = upsertCustomer(npub, 'Sunset Key Condo Association', 'board@sunsetkey.example', 'ORGANIZATION')
        Customer retail = upsertCustomer(npub, 'Gulf Blvd Retail Pavilion LLC', 'dev@gulfblvd.example', 'ORGANIZATION')
        Customer billion = upsertCustomer(npub, 'Estate of Sand Key (UHNW)', 'family.office@sandkey.example', 'ORGANIZATION')
        Customer tourism = upsertCustomer(npub, 'Pinellas Heritage Museum', 'curator@pinellasmuseum.example', 'ORGANIZATION')

        // --- Vendors ---
        Vendor print = upsertVendor(npub, 'Tampa Bay Plan Press', 'orders@tbplanpress.example')
        Vendor media = upsertVendor(npub, 'Suncoast Disc Media', 'fulfill@suncoastdisc.example')
        Vendor survey = upsertVendor(npub, 'IRB Coastal Surveyors', 'field@irbsurvey.example')
        Vendor render = upsertVendor(npub, 'LumenViz Render Farm', 'jobs@lumenviz.example')

        // --- Staff story (Candidates hired — no extra logins) ---
        List staff = [
                upsertStaff(npub, 'Maya Heard, AIA', 'maya.heard@heardcook.example', 'CEO · Principal', 'FOUNDER'),
                upsertStaff(npub, 'Julian Cook, RA', 'julian.cook@heardcook.example', 'Design Principal · Ops', 'EMPLOYEE'),
                upsertStaff(npub, 'Sofia Reyes', 'sofia.reyes@heardcook.example', 'Office · Client Concierge', 'EMPLOYEE'),
                upsertStaff(npub, 'Devon Blake', 'devon.blake@heardcook.example', 'Finance · Studio Controller', 'EMPLOYEE'),
                upsertStaff(npub, 'Priya Nair', 'priya.nair@heardcook.example', 'Ops · CD Production Lead', 'EMPLOYEE'),
        ]

        // --- Capability project: Billionaire Kitchen Studio ---
        Project kitchenStudio = Project.findByNpubOwnerAndName(npub, 'Billionaire Kitchen Studio')
        if (!kitchenStudio) {
            kitchenStudio = new Project(
                    npubOwner: npub,
                    name: 'Billionaire Kitchen Studio',
                    description: 'Ultra-high-end kitchen conceptualization for Sand Key estate — layout, millwork narrative, AV/demo video package, and limited merch for the family office.',
                    projectStatus: 'ACTIVE',
                    startDate: daysAgo(14),
            )
            kitchenStudio.save(failOnError: true)
        }

        Project gulfRetail = Project.findByNpubOwnerAndName(npub, 'Gulf Blvd Retail Pavilion')
        if (!gulfRetail) {
            gulfRetail = new Project(
                    npubOwner: npub,
                    name: 'Gulf Blvd Retail Pavilion',
                    description: 'Commercial shell + tenant criteria docs for Indian Rocks Beach retail pavilion.',
                    projectStatus: 'ACTIVE',
                    startDate: daysAgo(40),
            )
            gulfRetail.save(failOnError: true)
        }

        // --- Tasks ---
        Task t1 = upsertTask(npub, 'H&C: Sand Key kitchen mood board review', 'Prepare concept boards for Estate of Sand Key family office.', 3, 3)
        Task t2 = upsertTask(npub, 'H&C: Burn DVD set for Clearwater Bay', 'Paper plans + digital DVD + demo video disc for marker residence.', 2, 2)
        Task t3 = upsertTask(npub, 'H&C: Permit package — Gulf Blvd pavilion', 'Assemble expediting package with IRB Coastal Surveyors topo.', 2, 3)

        try {
            companyConfigService?.put(npub, 'demo.pack', 'heard-cook-architects-v1', 'demo')
            companyConfigService?.put(npub, 'demo.city', 'Indian Rocks Beach, FL', 'demo')
        } catch (ignored) {
        }

        log.info('Heard & Cook seed applied for {}', npub)
        [
                npubOwner : npub,
                companyId : company.id,
                siteId    : hq?.id,
                skipped   : false,
                forced    : force,
                customers : 5,
                vendors   : 4,
                catalogue : catIds.size(),
                staff     : staff.size(),
                projects  : 2,
                tasks     : 3,
                customerIds: [marker.id, condo.id, retail.id, billion.id, tourism.id],
                catalogueIds: catIds,
                message   : force ? 'Heard & Cook reset and reseeded' : 'Heard & Cook Architects seed created',
        ]
    }

    @Transactional
    Map resetDemo() {
        seed(true)
    }

    @Transactional
    void wipeDemoData(String npub) {
        if (npub != NPUB) {
            throw new SecurityException("Refuse wipe for non-Heard-Cook npub: ${npub}")
        }
        safeDeleteAll(Invoice, npub)
        safeDeleteAll(SalesOrder, npub)
        safeDeleteAll(CustomerQuote, npub)
        safeDeleteAll(steakos.procurement.PurchaseOrder, npub)
        safeDeleteAll(Task, npub)
        safeDeleteAll(Project, npub)
        safeDeleteAll(Candidate, npub)
        safeDeleteAll(Customer, npub)
        safeDeleteAll(Vendor, npub)
        safeDeleteAll(CatalogueItem, npub)
        try {
            safeDeleteAll(steakos.supplychain.StockMove, npub)
        } catch (ignored) {
        }
        // Keep LegalEntity + Site shell
        log.info('Wiped Heard & Cook demo data for {}', npub)
    }

    private Site ensureSite(LegalEntity company, String npub) {
        Site site = Site.findByNpubOwnerAndName(npub, HQ_SITE)
        if (site) {
            site.legalEntity = company
            site.addressText = '1508 Gulf Blvd, Indian Rocks Beach, FL 33785, USA'
            site.active = Boolean.TRUE
            site.save(failOnError: true)
            return site
        }
        site = new Site(
                npubOwner: npub,
                name: HQ_SITE,
                legalEntity: company,
                siteType: 'HQ',
                addressText: '1508 Gulf Blvd, Indian Rocks Beach, FL 33785, USA',
                description: 'Waterfront studio — client galleries, print room, DVD burn station, kitchen concept loft.',
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
                source: 'Demo seed · Heard & Cook',
                description: "Studio persona (${kindHint}) — story only, no login.",
                hiredAt: daysAgo(120),
                appliedAt: daysAgo(150),
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
