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
import steakos.finance.FinanceRevenueService
import steakos.finance.FinanceTreasuryService
import steakos.finance.Invoice
import steakos.human.Candidate
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
 * PDR-026 — kitchen-sink demo pack for Heard & Cook Architects
 * (Indian Rocks Beach, FL — residential/commercial design + plan media + kitchen studio).
 */
@Slf4j
class HeardCookArchitectsSeedService {

    static final String MARKER_CUSTOMER = 'H&C Marker — Clearwater Bay Residence'
    static final String STORY_QUOTE_MARKER = 'H&C: Clearwater Bay schematic — porch that catches every sunset'
    static final String NPUB = CompanyContextService.HEARD_COOK_NPUB
    static final String HQ_SITE = 'Indian Rocks Beach Studio'

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

        boolean markerPresent = Customer.findByNpubOwnerAndName(npub, MARKER_CUSTOMER) != null
        boolean storyPresent = CustomerQuote.findByNpubOwnerAndName(npub, STORY_QUOTE_MARKER) != null
        if (force) {
            wipeDemoData(npub)
        } else if (markerPresent && storyPresent) {
            return [
                    npubOwner: npub,
                    companyId: company.id,
                    skipped  : true,
                    message  : 'Heard & Cook seed already present (including story records)',
            ]
        }

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

        Map story = seedStoryRecords(npub, [
                marker : marker,
                condo  : condo,
                retail : retail,
                billion: billion,
                tourism: tourism,
                print  : print,
                media  : media,
                survey : survey,
                render : render,
        ])

        try {
            companyConfigService?.put(npub, 'demo.pack', 'heard-cook-architects-v1', 'demo')
            companyConfigService?.put(npub, 'demo.city', 'Indian Rocks Beach, FL', 'demo')
        } catch (ignored) {
        }

        log.info('Heard & Cook seed applied for {} (story={})', npub, story)
        [
                npubOwner  : npub,
                companyId  : company.id,
                siteId     : hq?.id,
                skipped    : false,
                forced     : force,
                customers  : 5,
                vendors    : 4,
                catalogue  : catIds.size(),
                staff      : staff.size(),
                projects   : 2,
                tasks      : 3,
                quotes     : story.quotes,
                orders     : story.orders,
                invoices   : story.invoices,
                purchaseOrders: story.purchaseOrders,
                opportunities: story.opportunities,
                activities : story.activities,
                customerIds: [marker.id, condo.id, retail.id, billion.id, tourism.id],
                catalogueIds: catIds,
                message    : force ? 'Heard & Cook reset and reseeded' : 'Heard & Cook Architects seed created',
        ]
    }

    /**
     * Idempotent commercial story docs keyed by document {@code name}. USD only; DRAFT/SENT preferred.
     */
    private Map seedStoryRecords(String npub, Map actors) {
        try {
            companyCurrencyService?.ensureInUse(npub, 'USD')
        } catch (ignored) {
        }

        Customer marker = actors.marker as Customer
        Customer condo = actors.condo as Customer
        Customer retail = actors.retail as Customer
        Customer billion = actors.billion as Customer
        Customer tourism = actors.tourism as Customer
        Vendor print = actors.print as Vendor
        Vendor media = actors.media as Vendor
        Vendor survey = actors.survey as Vendor
        Vendor render = actors.render as Vendor

        Long resSch = catId(npub, 'HC-RES-SCH')
        Long comDd = catId(npub, 'HC-COM-DD')
        Long permit = catId(npub, 'HC-PERMIT')
        Long kitConcept = catId(npub, 'HC-KIT-CONCEPT')
        Long kitLayout = catId(npub, 'HC-KIT-LAYOUT')
        Long planPaper = catId(npub, 'HC-PLAN-PAPER')
        Long planDvd = catId(npub, 'HC-PLAN-DVD')
        Long videoDvd = catId(npub, 'HC-VIDEO-DVD')
        Long tote = catId(npub, 'HC-MERCH-TOTE')
        Long book = catId(npub, 'HC-MERCH-BOOK')

        int quotes = 0, orders = 0, invoices = 0, pos = 0, opps = 0, acts = 0

        // --- Clearwater Bay (marker) ---
        ensureQuote(npub, STORY_QUOTE_MARKER, [
                customerId : marker.id,
                description: 'Schematic for the porch that faces every Clearwater sunset — plus Arch-D paper and a DVD for the builder.',
                lines      : [
                        [name: 'Residential Schematic Design', quantity: 1, unitPriceEur: 18500G, catalogueItemId: resSch],
                        [name: 'Paper Plan Copy Set (Arch D)', quantity: 2, unitPriceEur: 185G, catalogueItemId: planPaper],
                        [name: 'Digital Plans on DVD', quantity: 1, unitPriceEur: 95G, catalogueItemId: planDvd],
                ],
                send: true,
        ], { quotes++ })
        ensureOrder(npub, 'H&C: Clearwater Bay — schematic + plan media kit', [
                customerId : marker.id,
                description: 'Order tied to Clearwater Bay schematic + HC-RES-SCH / HC-PLAN-* media.',
                lines      : [
                        [name: 'Residential Schematic Design', quantity: 1, unitPriceEur: 18500G, catalogueItemId: resSch],
                        [name: 'Paper Plan Copy Set (Arch D)', quantity: 2, unitPriceEur: 185G, catalogueItemId: planPaper],
                        [name: 'Digital Plans on DVD', quantity: 1, unitPriceEur: 95G, catalogueItemId: planDvd],
                ],
        ], { orders++ })
        ensureInvoice(npub, 'H&C: Clearwater Bay schematic retainer (DRAFT)', [
                customerId     : marker.id,
                customerName   : marker.name,
                customerContact: marker.email,
                amountEur      : 5000G,
                description    : 'Retainer against HC-RES-SCH — porch that catches every sunset.',
                referenceText  : 'Clearwater Bay schematic retainer',
        ], { invoices++ })
        Opportunity oppClearwater = ensureOpportunity(npub, 'H&C: Clearwater Bay waterfront residence', [
                customerId       : marker.id,
                contactName      : 'Clearwater Bay owner',
                contactEmail     : marker.email,
                stage            : Opportunity.STAGE_QUALIFIED,
                expectedAmountEur: 18500G,
                ownerName        : 'Maya Heard, AIA',
                source           : 'REFERRAL',
                description      : 'Waterfront residential — client wants the porch oriented for every sunset.',
        ], { opps++ })
        ensureActivity(oppClearwater, npub, 'H&C NOTE: Porch that catches every sunset', [
                kind       : 'NOTE',
                description: 'Client sketched the porch on a napkin at Crabby Bill\'s. Orient west; deep overhang for summer sun.',
        ], { acts++ })
        ensureActivity(oppClearwater, npub, 'H&C CALL: Clearwater Bay site walk', [
                kind       : 'CALL',
                channel    : 'PHONE',
                description: 'Walked the lot with the owner — tide line, neighbor setbacks, porch depth.',
        ], { acts++ })

        // --- Sunset Key Condo ---
        ensureQuote(npub, 'H&C: Condo lobby refresh (no more beige)', [
                customerId : condo.id,
                description: 'Lobby refresh package — commercial DD slice + sketchbooks so the board stops saying beige.',
                lines      : [
                        [name: 'Commercial Design Development', quantity: 1, unitPriceEur: 12000G, catalogueItemId: comDd],
                        [name: 'Gulf Coast Sketchbook', quantity: 12, unitPriceEur: 36G, catalogueItemId: book],
                ],
                send: true,
        ], { quotes++ })
        ensureOrder(npub, 'H&C: Sunset Key lobby package', [
                customerId : condo.id,
                description: 'Lobby refresh order — no more beige, please.',
                lines      : [
                        [name: 'Commercial Design Development', quantity: 1, unitPriceEur: 12000G, catalogueItemId: comDd],
                        [name: 'Gulf Coast Sketchbook', quantity: 12, unitPriceEur: 36G, catalogueItemId: book],
                ],
        ], { orders++ })
        ensureInvoice(npub, 'H&C: Sunset Key lobby deposit (DRAFT)', [
                customerId     : condo.id,
                customerName   : condo.name,
                customerContact: condo.email,
                amountEur      : 3500G,
                description    : 'Deposit on condo lobby refresh — anti-beige campaign.',
                referenceText  : 'Sunset Key lobby',
        ], { invoices++ })
        Opportunity oppCondo = ensureOpportunity(npub, 'H&C: Sunset Key — board wants less beige', [
                customerId       : condo.id,
                contactName      : 'Condo board chair',
                contactEmail     : condo.email,
                stage            : Opportunity.STAGE_PROPOSAL,
                expectedAmountEur: 12000G,
                ownerName        : 'Julian Cook, RA',
                source           : 'REFERRAL',
                description      : 'Association board is tired of beige. Lobby refresh + tenant criteria vibes.',
        ], { opps++ })
        ensureActivity(oppCondo, npub, 'H&C CALL: Board anti-beige summit', [
                kind       : 'CALL',
                channel    : 'PHONE',
                description: 'Hour-long board call. Consensus: warm terrazzo, no beige paint, tote bags for the AGM.',
        ], { acts++ })

        // --- Gulf Blvd Retail ---
        ensureQuote(npub, 'H&C: Gulf Blvd pavilion — DD + permit expedite', [
                customerId : retail.id,
                description: 'Commercial DD phase + permit expediting before king tide season.',
                lines      : [
                        [name: 'Commercial Design Development', quantity: 1, unitPriceEur: 42000G, catalogueItemId: comDd],
                        [name: 'Permit Expediting Package', quantity: 1, unitPriceEur: 4500G, catalogueItemId: permit],
                ],
        ], { quotes++ })
        ensureOrder(npub, 'H&C: Gulf Blvd pavilion commercial package', [
                customerId : retail.id,
                description: 'Order for HC-COM-DD + HC-PERMIT on Gulf Blvd Retail Pavilion.',
                lines      : [
                        [name: 'Commercial Design Development', quantity: 1, unitPriceEur: 42000G, catalogueItemId: comDd],
                        [name: 'Permit Expediting Package', quantity: 1, unitPriceEur: 4500G, catalogueItemId: permit],
                ],
        ], { orders++ })
        Opportunity oppRetail = ensureOpportunity(npub, 'H&C: Gulf Blvd pavilion — permit drama', [
                customerId       : retail.id,
                contactName      : 'Gulf Blvd developer',
                contactEmail     : retail.email,
                stage            : Opportunity.STAGE_NEGOTIATION,
                expectedAmountEur: 46500G,
                ownerName        : 'Priya Nair',
                source           : 'PARTNER',
                description      : 'Retail shell + tenant criteria. County permit queue meets king tide calendar.',
        ], { opps++ })
        ensureActivity(oppRetail, npub, 'H&C NOTE: Permit drama vs king tide', [
                kind       : 'NOTE',
                description: 'County wants revised topo before the next king tide. Loop in IRB Coastal Surveyors overnight.',
        ], { acts++ })

        // --- Estate of Sand Key (UHNW) ---
        ensureQuote(npub, 'H&C: Billionaire Kitchen mood board retainer', [
                customerId : billion.id,
                description: 'UHNW kitchen concept studio + layout narrative + demo video DVD for the family office.',
                lines      : [
                        [name: 'Billionaire Kitchen Concept Studio', quantity: 1, unitPriceEur: 95000G, catalogueItemId: kitConcept],
                        [name: 'Kitchen Layout & Millwork Narrative', quantity: 1, unitPriceEur: 22000G, catalogueItemId: kitLayout],
                        [name: 'Project Demo Video DVD', quantity: 3, unitPriceEur: 49G, catalogueItemId: videoDvd],
                ],
                send: true,
        ], { quotes++ })
        ensureOrder(npub, 'H&C: Sand Key kitchen studio package', [
                customerId : billion.id,
                description: 'Order for HC-KIT-CONCEPT + HC-KIT-LAYOUT + HC-VIDEO-DVD.',
                lines      : [
                        [name: 'Billionaire Kitchen Concept Studio', quantity: 1, unitPriceEur: 95000G, catalogueItemId: kitConcept],
                        [name: 'Kitchen Layout & Millwork Narrative', quantity: 1, unitPriceEur: 22000G, catalogueItemId: kitLayout],
                        [name: 'Project Demo Video DVD', quantity: 3, unitPriceEur: 49G, catalogueItemId: videoDvd],
                ],
        ], { orders++ })
        ensureInvoice(npub, 'H&C: Sand Key kitchen concept retainer (DRAFT)', [
                customerId     : billion.id,
                customerName   : billion.name,
                customerContact: billion.email,
                amountEur      : 25000G,
                description    : 'Retainer against Billionaire Kitchen Concept Studio.',
                referenceText  : 'HC-KIT-CONCEPT retainer',
        ], { invoices++ })
        Opportunity oppKitchen = ensureOpportunity(npub, 'H&C: UHNW Sand Key kitchen pipeline', [
                customerId       : billion.id,
                contactName      : 'Sand Key family office',
                contactEmail     : billion.email,
                stage            : Opportunity.STAGE_PROPOSAL,
                expectedAmountEur: 117000G,
                ownerName        : 'Maya Heard, AIA',
                source           : 'REFERRAL',
                description      : 'Ultra-high-end kitchen conceptualization — mood boards, millwork narrative, overnight viz.',
        ], { opps++ })
        ensureActivity(oppKitchen, npub, 'H&C NOTE: UHNW kitchen mood board', [
                kind       : 'NOTE',
                description: 'Family office wants Calacatta, hidden appliance walls, and a wine stair. No beige. Ever.',
        ], { acts++ })
        ensureActivity(oppKitchen, npub, 'H&C CALL: Millwork narrative walkthrough', [
                kind       : 'CALL',
                channel    : 'PHONE',
                description: 'Walked millwork narrative with family office — island seating for twelve, AV demo disc for absentee principals.',
        ], { acts++ })

        // --- Pinellas Heritage Museum ---
        ensureQuote(npub, 'H&C: Museum gift shop — DVD + tote merch', [
                customerId : tourism.id,
                description: 'Gift shop run: project demo DVDs, plan DVDs, and tote bags for the Gulf Coast exhibit.',
                lines      : [
                        [name: 'Project Demo Video DVD', quantity: 40, unitPriceEur: 49G, catalogueItemId: videoDvd],
                        [name: 'Digital Plans on DVD', quantity: 20, unitPriceEur: 95G, catalogueItemId: planDvd],
                        [name: 'Heard & Cook Tote Bag', quantity: 60, unitPriceEur: 28G, catalogueItemId: tote],
                ],
                send: true,
        ], { quotes++ })
        ensureOrder(npub, 'H&C: Museum gift shop merch carton', [
                customerId : tourism.id,
                description: 'Order for HC-VIDEO-DVD / HC-PLAN-DVD / HC-MERCH-TOTE museum run.',
                lines      : [
                        [name: 'Project Demo Video DVD', quantity: 40, unitPriceEur: 49G, catalogueItemId: videoDvd],
                        [name: 'Digital Plans on DVD', quantity: 20, unitPriceEur: 95G, catalogueItemId: planDvd],
                        [name: 'Heard & Cook Tote Bag', quantity: 60, unitPriceEur: 28G, catalogueItemId: tote],
                ],
        ], { orders++ })
        ensureInvoice(npub, 'H&C: Museum merch deposit (DRAFT)', [
                customerId     : tourism.id,
                customerName   : tourism.name,
                customerContact: tourism.email,
                amountEur      : 1500G,
                description    : 'Deposit on gift shop DVD + tote run.',
                referenceText  : 'Pinellas Heritage merch',
        ], { invoices++ })
        Opportunity oppMuseum = ensureOpportunity(npub, 'H&C: Museum tote-bag merch + plan DVDs', [
                customerId       : tourism.id,
                contactName      : 'Museum curator',
                contactEmail     : tourism.email,
                stage            : Opportunity.STAGE_QUALIFIED,
                expectedAmountEur: 5600G,
                ownerName        : 'Sofia Reyes',
                source           : 'PARTNER',
                description      : 'Curator wants Gulf Coast exhibit gift shop stocked with plan DVDs and H&C totes.',
        ], { opps++ })
        ensureActivity(oppMuseum, npub, 'H&C NOTE: Tote-bag merch for gift shop', [
                kind       : 'NOTE',
                description: 'Curator insists totes must say Indian Rocks Beach on one side and Heard & Cook on the other.',
        ], { acts++ })

        // --- Vendor POs ---
        ensurePurchaseOrder(npub, 'H&C: Arch-D paper blizzard for Clearwater Bay', [
                vendorId   : print.id,
                description: 'Arch-D paper plan sets for Clearwater Bay builder package.',
                lines      : [
                        [name: 'Paper Plan Copy Set (Arch D)', quantity: 8, unitPriceEur: 95G, catalogueItemId: planPaper],
                ],
                send: true,
        ], { pos++ })
        ensurePurchaseOrder(npub, 'H&C: DVD burn night — museum gift shop', [
                vendorId   : media.id,
                description: 'Suncoast Disc Media overnight burn for Pinellas Heritage gift shop.',
                lines      : [
                        [name: 'Project Demo Video DVD', quantity: 40, unitPriceEur: 18G, catalogueItemId: videoDvd],
                        [name: 'Digital Plans on DVD', quantity: 20, unitPriceEur: 22G, catalogueItemId: planDvd],
                ],
                send: true,
        ], { pos++ })
        ensurePurchaseOrder(npub, 'H&C: Topo before the king tide', [
                vendorId   : survey.id,
                description: 'IRB Coastal Surveyors — updated topo for Gulf Blvd pavilion permit package.',
                lines      : [
                        [name: 'Coastal topo survey (king tide window)', quantity: 1, unitPriceEur: 3200G],
                ],
        ], { pos++ })
        ensurePurchaseOrder(npub, 'H&C: Overnight kitchen viz render farm', [
                vendorId   : render.id,
                description: 'LumenViz overnight render farm for Sand Key Billionaire Kitchen mood boards.',
                lines      : [
                        [name: 'Overnight kitchen viz render package', quantity: 1, unitPriceEur: 4800G],
                ],
                send: true,
        ], { pos++ })

        [
                quotes         : quotes,
                orders         : orders,
                invoices       : invoices,
                purchaseOrders : pos,
                opportunities  : opps,
                activities     : acts,
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
        safeDeleteAll(CrmActivity, npub)
        safeDeleteAll(Opportunity, npub)
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

    private static Long catId(String npub, String code) {
        CatalogueItem.findByNpubOwnerAndCode(npub, code)?.id
    }

    private CustomerQuote ensureQuote(String npub, String name, Map fields, Closure onPresent = null) {
        CustomerQuote existing = CustomerQuote.findByNpubOwnerAndName(npub, name)
        if (existing) {
            onPresent?.call()
            return existing
        }
        Map body = new LinkedHashMap(fields)
        boolean send = body.remove('send') == true
        CustomerQuote q = salesQuoteService.createQuote(npub, body + [name: name, currencyCode: 'USD'])
        if (send && q) {
            try {
                salesQuoteService.sendQuote(q.id, npub)
            } catch (ignored) {
            }
        }
        onPresent?.call()
        q
    }

    private SalesOrder ensureOrder(String npub, String name, Map fields, Closure onPresent = null) {
        SalesOrder existing = SalesOrder.findByNpubOwnerAndName(npub, name)
        if (existing) {
            onPresent?.call()
            return existing
        }
        SalesOrder o = salesOrderService.createOrder(npub, fields + [name: name, currencyCode: 'USD'])
        onPresent?.call()
        o
    }

    private Invoice ensureInvoice(String npub, String name, Map fields, Closure onPresent = null) {
        Invoice existing = Invoice.findByNpubOwnerAndName(npub, name)
        if (existing) {
            onPresent?.call()
            return existing
        }
        Invoice inv = financeRevenueService.createInvoice(npub, fields + [name: name, currencyCode: 'USD'])
        onPresent?.call()
        inv
    }

    private steakos.procurement.PurchaseOrder ensurePurchaseOrder(String npub, String name, Map fields, Closure onPresent = null) {
        steakos.procurement.PurchaseOrder existing =
                steakos.procurement.PurchaseOrder.findByNpubOwnerAndName(npub, name)
        if (existing) {
            onPresent?.call()
            return existing
        }
        Map body = new LinkedHashMap(fields)
        boolean send = body.remove('send') == true
        steakos.procurement.PurchaseOrder po =
                scmPurchaseService.createPurchaseOrder(npub, body + [name: name, currencyCode: 'USD'])
        if (send && po) {
            try {
                scmPurchaseService.sendPurchaseOrder(po.id, npub)
            } catch (ignored) {
            }
        }
        onPresent?.call()
        po
    }

    private Opportunity ensureOpportunity(String npub, String name, Map fields, Closure onPresent = null) {
        Opportunity existing = Opportunity.findByNpubOwnerAndName(npub, name)
        if (existing) {
            onPresent?.call()
            return existing
        }
        Opportunity opp = salesCrmService.createOpportunity(npub, fields + [name: name, currencyCode: 'USD'])
        onPresent?.call()
        opp
    }

    private CrmActivity ensureActivity(Opportunity opp, String npub, String name, Map fields, Closure onPresent = null) {
        if (!opp) return null
        CrmActivity existing = CrmActivity.findByNpubOwnerAndName(npub, name)
        if (existing) {
            onPresent?.call()
            return existing
        }
        CrmActivity act = salesCrmService.createActivity(opp.id, npub, fields + [name: name])
        onPresent?.call()
        act
    }
}
