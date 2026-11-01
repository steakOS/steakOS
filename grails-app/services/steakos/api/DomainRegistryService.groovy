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

package steakos.api

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.finance.Account
import steakos.finance.Invoice
import steakos.finance.JournalEntry
import steakos.finance.JournalLine
import steakos.helper.Countries
import steakos.human.Payroll
import steakos.production.BillOfMaterials
import steakos.production.BomItem
import steakos.catalogue.CatalogueItem
import steakos.helper.DateParse

/**
 * Maps package/resource path segments to GORM domain classes (FE-2 dynamic CRUD).
 * F-S0: finance scalar/association binding for ledger + invoice model.
 * P2: schemaFor() drives generic master-data forms.
 */
@Slf4j
class DomainRegistryService {

    GrailsApplication grailsApplication
    steakos.catalogue.CatalogueService catalogueService
    steakos.asset.AssetBasicsService assetBasicsService
    steakos.configuration.CompanyCurrencyService companyCurrencyService
    steakos.document.DocumentChainLockService documentChainLockService

    private Map<String, Class> cache

    private static final List ADDRESS_FIELDS = [
            'addressType', 'street', 'streetNo',
            'street2', 'street3', 'street4', 'street5', 'street6',
            'lot', 'building', 'room', 'locality',
            'postalCode', 'city', 'region', 'county'
    ].asImmutable()

    private static final List STRING_FIELDS = [
            'status', 'type', 'tags', 'code', 'kind',
            'npubOwner', 'protection', 'classification',
            'contentTypeCode', 'languageCode', 'countryCode', 'currencyCode',
            // finance F-S0
            'accountType', 'accountSubType', 'parentCode',
            'period', 'functionalCurrency', 'sourceType', 'sourceId', 'eventId', 'memo',
            'invoiceNumber', 'customerName', 'customerContact', 'invoiceStatus', 'referenceText',
            'rail', 'paymentStatus', 'externalTxId', 'paymentUri', 'qrPayload',
            'estimatedSettle', 'externalInvoiceId', 'accountKind', 'externalRef',
            'vendorName', 'payableStatus', 'accountCode',
            'taxStatus', 'jurisdiction', 'payrollStatus', 'personRef', 'period',
            // sales S-S0
            'customerKind', 'email', 'phone', 'contactName', 'customerNpub', 'taxId',
            'quoteNumber', 'quoteStatus', 'orderNumber', 'orderStatus', 'productRefType',
            // sales Advanced price lists (OI-S04)
            'listKind',
            // scm SC-S0
            'vendorNpub', 'poNumber', 'poStatus', 'receiptNumber', 'receiptStatus',
            'sku', 'unit', 'bomStatus', 'prodStatus', 'lotCode', 'lotStatus',
            'checkResult', 'notes', 'reason', 'sourceType', 'sourceId',
            // delivery D-S0
            'slaCode', 'resourceKind', 'personRef', 'fulfillStatus', 'orderNumber',
            // aftersales SLA
            'channel',
            // core Task (OI-WK01)
            'assigneeHumanNpub',
            // capability processes
            'processKind', 'processCode', 'processStatus', 'versionLabel', 'ownerRef',
            'stepCode', 'deskHint', 'outcomeStatus', 'actorHint',
            // strategy / development / asset G4 thin
            'itemKind', 'projectKind', 'projectStatus', 'assetTag', 'location', 'ownerName',
            // configuration LegalEntity (Task 7623)
            'legalForm', 'registeredAddressText', 'website', 'mainEmail'
    ].asImmutable()

    private static final List DECIMAL_FIELDS = [
            'latitude', 'longitude',
            'debitEur', 'creditEur', 'amountEur', 'exchangeRate', 'balanceEur',
            'quantity', 'unitPriceEur', 'lineTotalEur', 'quantityReceived', 'qtyDelta',
            'defaultDuration', 'listPriceEur', 'capacityHours',
            'defaultDiscountPercent'
    ].asImmutable()

    private static final List LONG_FIELDS = [
            'amountSats', 'balanceSats', 'customerId', 'vendorId',
            'ledgerAccountId', 'journalEntryId', 'reversesEntryId', 'lineNo',
            'unitPriceSats', 'lineTotalSats', 'productRefId', 'resultingOrderId',
            'financeInvoiceId', 'financePayableId', 'productId', 'lotId',
            'sourceSalesOrderId', 'resultingLotId', 'sourceProductionOrderId',
            'listPriceSats', 'agentRefId', 'serviceRefId',
            'componentProductId', 'bomId', 'catalogueItemId', 'warrantyId', 'hrFileId',
            // core Task / capability links
            'assigneeAgentId', 'taskId', 'projectId',
            // asset / digital G4
            'configItemId', 'physicalAssetId', 'systemId', 'solutionId'
    ].asImmutable()

    private static final List INTEGER_FIELDS = [
            'urgency', 'importance', 'itemNo', 'stepNo', 'sortOrder', 'decimals',
            'responseHours', 'resolveHours'
    ].asImmutable()

    private static final Set SCHEMA_SKIP = [
            'id', 'version', 'errors', 'attached', 'dirty', 'class', 'metaClass',
            'constraints', 'mapping', 'hasMany', 'belongsTo', 'mappedBy',
            'npubOwner', 'revision', 'revisionNote', 'createdByNpub', 'lastUpdatedByNpub',
            'dateCreated', 'lastUpdated', 'protection', 'classification',
            'contentTypeCode', 'languageCode',
            // Attachable.url removed — links via Object Companion (ObjectContent.url kept elsewhere)
            'url', 'attachmentMeta', 'attachmentCount'
    ].asImmutable() as Set

    /** Hidden on Strategy / Development / Asset light create forms (G4 entry-level UI). */
    private static final Set G4_LIGHT_FORM_HIDE = [
            'access', 'catalogueItemId', 'deputyNpub',
            // Asset CI link is server-managed when itManaged; do not edit on Asset form
            'configItemId'
    ].asImmutable() as Set

    /** Trait / noise fields hidden on inventory-style masters (keep catalogueItemId visible). */
    private static final Set SUPPLYCHAIN_FORM_HIDE = [
            'access', 'deputyNpub'
    ].asImmutable() as Set

    /** Known Long *Id → catalogue / domain ref metadata for schema-driven pickers. */
    private static final Map REF_META = [
            catalogueItemId   : [refPackage: 'catalogue', refResource: 'CatalogueItem'],
            productId         : [refPackage: 'catalogue', refResource: 'CatalogueItem'],
            componentProductId: [refPackage: 'catalogue', refResource: 'CatalogueItem'],
            componentItemId   : [refPackage: 'catalogue', refResource: 'CatalogueItem'],
            customerId        : [refPackage: 'sales', refResource: 'Customer'],
            vendorId          : [refPackage: 'procurement', refResource: 'Vendor'],
            bomId             : [refPackage: 'production', refResource: 'BillOfMaterials'],
            lotId             : [refPackage: 'production', refResource: 'Lot'],
    ].asImmutable()

    private static final List DATE_FIELDS = [
            'entryDate', 'postedAt', 'issueDate', 'dueDate', 'settledAt',
            'receivedAt', 'paidAt', 'lastReconciledAt',
            'validUntil', 'validFrom', 'validTo', 'orderDate', 'confirmedAt',
            'receivedDate', 'scheduledStart', 'scheduledEnd', 'completedAt',
            'manufacturedOn', 'checkedOn', 'movedAt',
            'scheduledAt', 'startedAt',
            // core Task schedule
            'startAt', 'endAt', 'deadlineAt'
    ].asImmutable()

    private static final List BOOLEAN_FIELDS = [
            'enabled', 'active', 'systemAccount', 'posted', 'recurring',
            'isService', 'trackLots', 'itManaged',
            'financialDepreciation', 'requiresMaintenance'
    ].asImmutable()

    Class resolve(String pkg, String resource) {
        if (!pkg || !resource) {
            return null
        }
        ensureCache()
        return cache["${pkg}/${resource}"]
    }

    List<Map> listRegistry() {
        ensureCache()
        cache.collect { String key, Class clazz ->
            def parts = key.split('/')
            [
                    package   : parts[0],
                    resource  : parts[1],
                    className : clazz.name,
                    simpleName: clazz.simpleName
            ]
        }.sort { a, b ->
            a.package <=> b.package ?: a.resource <=> b.resource
        }
    }

    boolean isAllowed(Class domainClass) {
        if (!domainClass) {
            return false
        }
        String name = domainClass.name
        if (name in ['steakos.User', 'steakos.Role', 'steakos.UserRole']) {
            return false
        }
        return name.startsWith('steakos.')
    }

    /**
     * B4 — tenant-scoped when the domain has {@code npubOwner} (NpubOwned or equivalent).
     * Shared catalogs (Currencies, SupportedLocale, Countries, …) have no npubOwner → global.
     */
    boolean isTenantScoped(Class domainClass) {
        if (!domainClass) {
            return false
        }
        if (steakos.traits.NpubOwned.isAssignableFrom(domainClass)) {
            return true
        }
        try {
            return domainClass.metaClass.getMetaProperty('npubOwner') != null
        } catch (Exception ignored) {
            return false
        }
    }

    @Transactional(readOnly = true)
    List listAll(Class domainClass, Integer max = 25, Integer offset = 0, Map filters = [:]) {
        listPage(domainClass, max, offset, filters ?: [:]).items as List
    }

    /**
     * Curated sort allowlist (Task 7640 D4). resolveListSort keeps only properties
     * that exist on the domain class — extra keys here are safe no-ops per resource.
     */
    static final List DOMAIN_SORT_FIELDS = [
            'id', 'name', 'code', 'status', 'dateCreated', 'lastUpdated',
            'amountEur', 'customerName', 'kind', 'active', 'priority',
            'referenceText', 'description'
    ].asImmutable()

    static final List TASK_SORT_FIELDS = (
            DOMAIN_SORT_FIELDS + [
                    'deadlineAt', 'urgency', 'importance', 'assigneeHumanNpub'
            ]
    ).asImmutable()

    /**
     * Paged domain list with optional filters (LIST-PAGINATION F1 + Task 7640 sort/q).
     * Task supports view=open|overdue|mine|all, status=, q=, sort=, order=.
     * Other resources: optional q matches name (and description when present).
     * Tenant domains: pass filters.npubOwner (active company) to scope rows (B4).
     * Unknown sort → IllegalArgumentException (API 400). Tie-break always id asc.
     */
    @Transactional(readOnly = true)
    Map listPage(Class domainClass, Integer max = 25, Integer offset = 0, Map filters = [:]) {
        int limit = Math.min(Math.max(max ?: 25, 1), 100)
        int off = Math.max(offset ?: 0, 0)
        String activeNpub = tenantNpubFromFilters(filters)
        if (domainClass == steakos.core.Task) {
            return listTasksPage(limit, off, filters ?: [:])
        }
        Map so = resolveListSort(domainClass, filters, DOMAIN_SORT_FIELDS)
        String term = ListPaging.likeContains(filters?.q?.toString())
        Map assocFilters = associationIdFilters(domainClass, filters)
        boolean hasAssoc = !assocFilters.isEmpty()
        boolean scoped = isTenantScoped(domainClass) && activeNpub

        if (term || hasAssoc) {
            try {
                List items = domainClass.createCriteria().list(max: limit, offset: off) {
                    if (scoped) {
                        eq('npubOwner', activeNpub)
                    }
                    assocFilters.each { String assoc, Long id ->
                        eq("${assoc}.id", id)
                    }
                    if (term) {
                        or {
                            ilike('name', term)
                            ilike('description', term)
                        }
                    }
                    applyListOrders(delegate, so)
                } as List
                Number total = domainClass.createCriteria().count {
                    if (scoped) {
                        eq('npubOwner', activeNpub)
                    }
                    assocFilters.each { String assoc, Long id ->
                        eq("${assoc}.id", id)
                    }
                    if (term) {
                        or {
                            ilike('name', term)
                            ilike('description', term)
                        }
                    }
                } as Number
                return listEnvelope(items, total?.intValue() ?: 0, so)
            } catch (IllegalArgumentException ex) {
                throw ex
            } catch (Exception ex) {
                if (hasAssoc && !term) {
                    throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex)
                }
                // resource without name/description — fall through when only q failed
            }
        }
        if (scoped) {
            List items = domainClass.createCriteria().list(max: limit, offset: off) {
                eq('npubOwner', activeNpub)
                applyListOrders(delegate, so)
            } as List
            Number total = domainClass.countByNpubOwner(activeNpub) as Number
            return listEnvelope(items ?: [], total?.intValue() ?: 0, so)
        }
        List items = domainClass.createCriteria().list(max: limit, offset: off) {
            applyListOrders(delegate, so)
        } as List
        listEnvelope(items ?: [], (domainClass.count() ?: 0) as int, so)
    }

    private static Map resolveListSort(Class domainClass, Map filters, Collection baseAllowed) {
        List allowed = (baseAllowed ?: []).findAll { String f ->
            f == 'id' || domainHasProperty(domainClass, f)
        } as List
        if (!allowed.contains('id')) {
            allowed = ['id'] + allowed
        }
        ListPaging.requireSortParams(filters ?: [:], 'id', 'desc', allowed)
    }

    private static void applyListOrders(Object criteria, Map so) {
        String sort = (so?.sort ?: 'id') as String
        String orderDir = (so?.order ?: 'desc') as String
        criteria.order(sort, orderDir)
        if (sort != 'id') {
            criteria.order('id', 'asc')
        }
    }

    private static Map listEnvelope(List items, int total, Map so) {
        [
                items: items ?: [],
                total: total,
                sort : so?.sort ?: 'id',
                order: so?.order ?: 'desc'
        ]
    }

    /**
     * Parent-document FK filters for line children (L3 DomainList nested lines).
     * Accepts quoteId → eq('quote.id', …) when the domain declares association {@code quote}.
     */
    private static Map associationIdFilters(Class domainClass, Map filters) {
        Map out = [:]
        if (!filters) {
            return out
        }
        filters.each { k, v ->
            String key = k?.toString()
            if (!key?.endsWith('Id') || key == 'id' || v == null || v.toString().trim() == '') {
                return
            }
            String assoc = key.substring(0, key.length() - 2)
            if (!assoc || !domainHasProperty(domainClass, assoc)) {
                return
            }
            try {
                out[assoc] = Long.valueOf(v.toString().trim())
            } catch (ignored) {
            }
        }
        out
    }

    private static boolean domainHasProperty(Class domainClass, String name) {
        try {
            return domainClass.metaClass.getMetaProperty(name) != null
        } catch (ignored) {
            return false
        }
    }

    @Transactional(readOnly = true)
    Map listTasksPage(int max, int offset, Map filters) {
        String view = filters?.view?.toString()?.trim()?.toLowerCase()
        String status = filters?.status?.toString()?.trim()
        String term = ListPaging.likeContains(filters?.q?.toString())
        String activeNpub = tenantNpubFromFilters(filters)
        Map so = resolveListSort(steakos.core.Task, filters, TASK_SORT_FIELDS)
        Date now = new Date()

        List items = steakos.core.Task.createCriteria().list(max: max, offset: offset) {
            applyTaskViewFilters(delegate, activeNpub, status, view, term, now)
            applyListOrders(delegate, so)
        } as List
        Number total = steakos.core.Task.createCriteria().count {
            applyTaskViewFilters(delegate, activeNpub, status, view, term, now)
        } as Number
        listEnvelope(items, total?.intValue() ?: 0, so)
    }

    private static void applyTaskViewFilters(Object c, String activeNpub, String status,
                                             String view, String term, Date now) {
        if (activeNpub) {
            c.eq('npubOwner', activeNpub)
        }
        if (status) {
            c.eq('status', status)
        } else if (view == 'open') {
            c.or {
                eq('status', 'OPEN')
                eq('status', 'IN_PROGRESS')
                isNull('status')
                eq('status', '')
                'in'('status', steakos.configuration.DevelopmentModeService.AI_OPEN_STATUSES)
            }
        } else if (view == 'overdue') {
            c.isNotNull('deadlineAt')
            c.lt('deadlineAt', now)
            c.not {
                'in'('status', ['DONE', 'CANCELLED', 'AI:done'])
            }
        } else if (view == 'mine') {
            c.or {
                and {
                    isNotNull('assigneeHumanNpub')
                    ne('assigneeHumanNpub', '')
                }
                isNotNull('assigneeAgentId')
            }
        }
        if (term) {
            c.or {
                ilike('name', term)
                ilike('description', term)
            }
        }
    }

    @Transactional(readOnly = true)
    Object get(Class domainClass, Long id, String activeNpub = null) {
        loadOwned(domainClass, id, activeNpub)
    }

    @Transactional
    Object create(Class domainClass, Map body, String activeNpub = null) {
        Map safeBody = body != null ? new LinkedHashMap(body) : [:]
        enforceCreateNpub(domainClass, safeBody, activeNpub)
        Object instance = domainClass.newInstance()
        bindBasics(instance, safeBody)
        // New assets must not set configItemId from client — link is server-created
        if (instance instanceof steakos.asset.PhysicalAsset) {
            ((steakos.asset.PhysicalAsset) instance).configItemId = null
        }
        if (isTenantScoped(domainClass) && activeNpub?.trim()) {
            instance['npubOwner'] = activeNpub.trim()
        }
        validateCurrencyWrite(instance)
        applyStockMoveDefaults(instance)
        if (!instance.save(flush: true)) {
            throw new IllegalArgumentException(formatValidationErrors(instance))
        }
        afterWrite(instance, safeBody)
        return instance
    }

    @Transactional
    Object update(Class domainClass, Long id, Map body, String activeNpub = null) {
        Object instance = loadOwned(domainClass, id, activeNpub)
        if (!instance) {
            return null
        }
        documentChainLockService?.assertDomainUnlocked(domainClass, id, activeNpub)
        Map safeBody = body != null ? new LinkedHashMap(body) : [:]
        rejectForeignNpubClaim(safeBody, activeNpub)
        // Never reassign tenant via body
        safeBody.remove('npubOwner')
        Long priorCi = null
        if (instance instanceof steakos.asset.PhysicalAsset) {
            priorCi = ((steakos.asset.PhysicalAsset) instance).configItemId
        }
        bindBasics(instance, safeBody)
        if (isTenantScoped(domainClass) && activeNpub?.trim()) {
            instance['npubOwner'] = activeNpub.trim()
        }
        if (instance instanceof steakos.asset.PhysicalAsset) {
            steakos.asset.PhysicalAsset asset = (steakos.asset.PhysicalAsset) instance
            // Permanent CI link: never clear once set
            if (priorCi) {
                asset.configItemId = priorCi
                asset.itManaged = Boolean.TRUE
            }
        }
        validateCurrencyWrite(instance)
        applyStockMoveDefaults(instance)
        if (!instance.save(flush: true)) {
            throw new IllegalArgumentException(formatValidationErrors(instance))
        }
        afterWrite(instance, safeBody)
        return instance
    }

    private static void applyStockMoveDefaults(Object instance) {
        if (!(instance instanceof steakos.supplychain.StockMove)) {
            return
        }
        steakos.supplychain.StockMove sm = (steakos.supplychain.StockMove) instance
        if ((!sm.name || !sm.name.trim()) && sm.catalogueItemId) {
            def ci = steakos.catalogue.CatalogueItem.get(sm.catalogueItemId)
            if (ci?.name) {
                sm.name = "${sm.reason ?: 'MOVE'} ${ci.name}".toString()
            }
        }
        if (!sm.movedAt) {
            sm.movedAt = new Date()
        }
    }

    private static String formatValidationErrors(Object instance) {
        try {
            List msgs = instance.errors?.fieldErrors?.collect { fe ->
                String field = fe.field ?: 'field'
                String why = fe.rejectedValue == null ? 'is required' : "rejected value ${fe.rejectedValue}"
                "${field} ${why}"
            } ?: []
            if (msgs) {
                return msgs.join('; ')
            }
        } catch (ignored) {
        }
        return instance?.errors?.toString() ?: 'Validation failed'
    }

    /** OI-CUR01 D8 — BankAccount / CatalogueItem currency must be system-enabled + in-use. */
    private void validateCurrencyWrite(Object instance) {
        if (companyCurrencyService == null || instance == null) return
        if (instance instanceof steakos.configuration.BankAccount) {
            steakos.configuration.BankAccount ba = (steakos.configuration.BankAccount) instance
            if (ba.npubOwner) {
                companyCurrencyService.validateWritableCode(ba.npubOwner, ba.currencyCode)
            }
        } else if (instance instanceof CatalogueItem) {
            CatalogueItem ci = (CatalogueItem) instance
            if (ci.npubOwner) {
                companyCurrencyService.validateWritableCode(ci.npubOwner, ci.currency)
            }
        }
    }

    /**
     * P2: field metadata for schema-driven master forms.
     * Prefer GORM constrainedProperties; fall back to bind allow-lists.
     */
    @Transactional(readOnly = true)
    Map schemaFor(Class domainClass, String pkg = null, String resource = null) {
        List fields = []
        Set seen = new HashSet()

        try {
            Map cps = domainClass.constrainedProperties
            if (cps) {
                cps.each { Object key, Object cp ->
                    String name = key?.toString()
                    if (!name || name in SCHEMA_SKIP || seen.contains(name)) return
                    // skip associations (GORM entities)
                    Class pt = null
                    try {
                        pt = cp.propertyType as Class
                    } catch (ignored) { }
                    if (pt != null && pt.name?.startsWith('steakos.') && !pt.isEnum()
                            && pt != String && !Number.isAssignableFrom(pt)
                            && pt != Boolean && pt != boolean && pt != Date) {
                        // expose FK id if present as Long property with same name+Id pattern elsewhere
                        return
                    }
                    Map f = fieldFromConstraint(name, cp)
                    if (f) {
                        fields << f
                        seen.add(name)
                    }
                }
            }
        } catch (Exception e) {
            log.debug("constrainedProperties for ${domainClass?.simpleName}: ${e.message}")
        }

        // Ensure allow-listed bindable props appear even if not in constrainedProperties
        ensureAllowListed(domainClass, fields, seen)

        // G4 light families: hide trait noise and optional FKs on create/edit forms
        String shortPkg = pkg?.toString()?.toLowerCase()
        if (!shortPkg && domainClass?.package?.name) {
            String full = domainClass.package.name
            shortPkg = full.startsWith('steakos.') ? full.substring('steakos.'.length()).split('\\.')[0] : full
        }
        if (shortPkg in ['strategy', 'development', 'asset']) {
            fields = fields.findAll { Map f -> !(f.name in G4_LIGHT_FORM_HIDE) }
        }
        if (shortPkg in ['supplychain']) {
            fields = fields.findAll { Map f -> !(f.name in SUPPLYCHAIN_FORM_HIDE) }
        }

        // Attach picker metadata for known FK id fields
        fields.each { Map f ->
            Map meta = REF_META[f.name]
            if (meta) {
                f.refPackage = meta.refPackage
                f.refResource = meta.refResource
                if (f.type != 'ref') {
                    f.type = 'ref'
                }
            }
        }

        // Stable order: name, description, key FKs / qty, then alpha
        fields.sort { Map a, Map b ->
            int ra = schemaFieldRank(a.name as String)
            int rb = schemaFieldRank(b.name as String)
            ra <=> rb ?: (a.name as String) <=> (b.name as String)
        }

        return [
                package : pkg,
                resource: resource ?: domainClass.simpleName,
                className: domainClass.name,
                fields  : fields
        ]
    }

    private void afterWrite(Object instance, Map body = null) {
        // CatalogueItem is the sole commercial master (legacy Product/Service removed)
        if (instance instanceof steakos.asset.PhysicalAsset && assetBasicsService != null) {
            assetBasicsService.ensureConfigItemLink((steakos.asset.PhysicalAsset) instance)
        }
    }

    private Map fieldFromConstraint(String name, Object cp) {
        Map f = [name: name, label: name]
        boolean nullable = true
        try { nullable = cp.nullable as boolean } catch (ignored) { }
        boolean blankOk = true
        try { blankOk = cp.blank as boolean } catch (ignored) { }

        Class pt = null
        try { pt = cp.propertyType as Class } catch (ignored) { }
        // Long/Number/Date/Boolean: blank is meaningless — nullable alone drives required
        boolean stringish = (pt == null || pt == String || CharSequence.isAssignableFrom(pt))
        f.required = stringish ? (!nullable && !blankOk) : !nullable

        try {
            if (cp.maxSize != null) f.maxSize = cp.maxSize as Integer
        } catch (ignored) { }
        try {
            if (cp.inList) {
                f.type = 'enum'
                f.inList = (cp.inList as List).collect { it?.toString() }
                return f
            }
        } catch (ignored) { }

        if (pt == Boolean || pt == boolean) {
            f.type = 'boolean'
        } else if (pt == Integer || pt == int || pt == Short || pt == short) {
            f.type = 'integer'
        } else if (pt == Long || pt == long) {
            f.type = name.endsWith('Id') ? 'ref' : 'long'
        } else if (pt == BigDecimal || pt == Double || pt == double || pt == Float || pt == float) {
            f.type = 'decimal'
            try { if (cp.scale != null) f.scale = cp.scale as Integer } catch (ignored) { }
        } else if (pt == Date || (pt != null && Date.isAssignableFrom(pt))) {
            f.type = 'date'
        } else if (name == 'description' || (f.maxSize != null && (f.maxSize as int) > 500)) {
            f.type = 'text'
        } else {
            f.type = 'string'
        }
        try {
            if (cp.defaultValue != null) f.defaultValue = cp.defaultValue
        } catch (ignored) { }
        return f
    }

    private static int schemaFieldRank(String name) {
        switch (name) {
            case 'name': return 0
            case 'description': return 1
            case 'catalogueItemId':
            case 'productId': return 2
            case 'qtyDelta':
            case 'quantity': return 3
            case 'reason': return 4
            case 'status': return 90
            case 'itManaged': return 94
            case 'configItemId': return 95
            case 'financialDepreciation': return 96
            case 'requiresMaintenance': return 97
            default: return 50
        }
    }

    private void ensureAllowListed(Class domainClass, List fields, Set seen) {
        def probe
        try {
            probe = domainClass.newInstance()
        } catch (Exception e) {
            return
        }
        Closure add = { String name, String type, boolean required = false ->
            if (name in SCHEMA_SKIP || seen.contains(name)) return
            if (!probe.hasProperty(name)) return
            fields << [name: name, type: type, required: required, label: name]
            seen.add(name)
        }
        add('name', 'string', true)
        add('description', 'text', false)
        STRING_FIELDS.each { add(it as String, 'string') }
        DECIMAL_FIELDS.each { add(it as String, 'decimal') }
        LONG_FIELDS.each { String n ->
            add(n, n.endsWith('Id') ? 'ref' : 'long')
        }
        INTEGER_FIELDS.each { add(it as String, 'integer') }
        BOOLEAN_FIELDS.each { add(it as String, 'boolean') }
        DATE_FIELDS.each { add(it as String, 'date') }
        // common FK-style longs on masters
        add('productId', 'ref')
        add('customerId', 'ref')
        add('vendorId', 'ref')
        add('bomId', 'ref')
        add('componentProductId', 'ref')
    }

    @Transactional
    boolean delete(Class domainClass, Long id, String activeNpub = null) {
        Object instance = loadOwned(domainClass, id, activeNpub)
        if (!instance) {
            return false
        }
        documentChainLockService?.assertDomainUnlocked(domainClass, id, activeNpub)
        try {
            instance.delete(flush: true)
            return true
        } catch (Exception e) {
            // Surface FK / integrity issues as IllegalStateException for DomainApiController 409
            Throwable t = e
            while (t.cause != null && t.cause != t) {
                t = t.cause
            }
            String msg = t?.message ?: e.message ?: 'delete failed'
            if (msg.toLowerCase().contains('foreign key') || msg.toLowerCase().contains('still referenced')
                    || msg.toLowerCase().contains('constraint')) {
                throw new IllegalStateException(msg, e)
            }
            throw e
        }
    }

    @Transactional(readOnly = true)
    int count(Class domainClass, String activeNpub = null) {
        if (isTenantScoped(domainClass) && activeNpub?.trim()) {
            Number n = domainClass.countByNpubOwner(activeNpub.trim()) as Number
            return n?.intValue() ?: 0
        }
        (domainClass.count() ?: 0) as int
    }

    /** Load row; for tenant domains return null when npubOwner ≠ active company (404, no leak). */
    private Object loadOwned(Class domainClass, Long id, String activeNpub) {
        Object instance = domainClass.get(id)
        if (!instance) {
            return null
        }
        if (isTenantScoped(domainClass) && activeNpub?.trim()) {
            String owner = instance.hasProperty('npubOwner') ? (instance['npubOwner'] as String) : null
            if (owner != activeNpub.trim()) {
                return null
            }
        }
        return instance
    }

    private static String tenantNpubFromFilters(Map filters) {
        if (!filters) return null
        String n = (filters.npubOwner ?: filters.activeNpub)?.toString()?.trim()
        return n ?: null
    }

    private void enforceCreateNpub(Class domainClass, Map body, String activeNpub) {
        if (!isTenantScoped(domainClass)) {
            return
        }
        if (!activeNpub?.trim()) {
            throw new IllegalArgumentException('active company npub required for tenant domain')
        }
        rejectForeignNpubClaim(body, activeNpub)
        body.npubOwner = activeNpub.trim()
    }

    private static void rejectForeignNpubClaim(Map body, String activeNpub) {
        if (!body || !activeNpub?.trim()) {
            return
        }
        String claimed = body.npubOwner?.toString()?.trim()
        if (claimed && claimed != activeNpub.trim()) {
            throw new IllegalArgumentException('npubOwner must match active company')
        }
    }

    Map toMap(Object instance) {
        if (!instance) {
            return null
        }
        Map m = [
                id         : instance.hasProperty('id') ? instance['id'] : null,
                name       : instance.hasProperty('name') ? instance['name'] : null,
                description: instance.hasProperty('description') ? instance['description'] : null,
                class      : instance.getClass().name
        ]
        (STRING_FIELDS + ['revision']).each { String p ->
            putIfPresent(m, instance, p)
        }
        DECIMAL_FIELDS.each { String p -> putIfPresent(m, instance, p) }
        LONG_FIELDS.each { String p -> putIfPresent(m, instance, p) }
        INTEGER_FIELDS.each { String p -> putIfPresent(m, instance, p) }
        BOOLEAN_FIELDS.each { String p -> putIfPresent(m, instance, p) }
        DATE_FIELDS.each { String p ->
            if (instance.hasProperty(p) && instance[p] != null) {
                try {
                    Date d = instance[p] as Date
                    m[p] = d ? new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").with {
                        timeZone = TimeZone.getTimeZone('UTC')
                        format(d)
                    } : null
                } catch (ignored) {
                    try {
                        m[p] = instance[p]?.toString()
                    } catch (ignored2) {
                    }
                }
            }
        }
        ADDRESS_FIELDS.each { String p -> putIfPresent(m, instance, p) }

        if (instance.hasProperty('country') && instance['country']) {
            try {
                def c = instance['country']
                m.countryId = c.id
                m.countryCode = c.code
                m.countryName = c.name
            } catch (ignored) {
            }
        }
        if (instance.hasProperty('account') && instance['account']) {
            try {
                def a = instance['account']
                m.accountId = a.id
                m.accountCode = a.code
                m.accountName = a.name
            } catch (ignored) {
            }
        }
        if (instance.hasProperty('journalEntry') && instance['journalEntry']) {
            try {
                m.journalEntryId = instance['journalEntry'].id
            } catch (ignored) {
            }
        }
        if (instance.hasProperty('invoice') && instance['invoice']) {
            try {
                def inv = instance['invoice']
                m.invoiceId = inv.id
                m.invoiceNumber = inv.invoiceNumber
            } catch (ignored) {
            }
        }
        if (instance.hasProperty('catalogueItem') && instance['catalogueItem']) {
            try {
                def p = instance['catalogueItem']
                m.catalogueItemId = p.id
                m.productId = p.id
                m.productName = p.name
                m.productSku = p.hasProperty('code') ? p.code : null
            } catch (ignored) {
            }
        }
        if (instance.hasProperty('componentItem') && instance['componentItem']) {
            try {
                def p = instance['componentItem']
                m.componentItemId = p.id
                m.componentProductId = p.id
                m.componentProductName = p.name
                m.catalogueItemId = p.id
            } catch (ignored) {
            }
        }
        if (instance.hasProperty('product') && instance['product']) {
            try {
                def p = instance['product']
                m.productId = p.id
                m.productName = p.name
                m.productSku = p.hasProperty('sku') ? p.sku : null
            } catch (ignored) {
            }
        }
        if (instance.hasProperty('bom') && instance['bom']) {
            try {
                def b = instance['bom']
                m.bomId = b.id
                m.bomName = b.name
            } catch (ignored) {
            }
        }
        if (instance.respondsTo('formatLine')) {
            try {
                m.formatted = instance.formatLine()
            } catch (ignored) {
            }
        }
        return m
    }

    private static void putIfPresent(Map m, Object instance, String p) {
        if (instance.hasProperty(p)) {
            try {
                def v = instance[p]
                if (v != null) {
                    m[p] = v
                }
            } catch (ignored) {
            }
        }
    }

    private void bindBasics(Object instance, Map body) {
        if (body == null) {
            return
        }
        ['name', 'description'].each { String p ->
            if (body.containsKey(p) && instance.hasProperty(p)) {
                instance[p] = body[p]
            }
        }
        STRING_FIELDS.each { String p ->
            if (body.containsKey(p) && instance.hasProperty(p)) {
                instance[p] = body[p]
            }
        }
        ADDRESS_FIELDS.each { String p ->
            if (body.containsKey(p) && instance.hasProperty(p)) {
                instance[p] = body[p]
            }
        }
        BOOLEAN_FIELDS.each { String p ->
            if (body.containsKey(p) && instance.hasProperty(p)) {
                instance[p] = body[p] as Boolean
            }
        }
        DECIMAL_FIELDS.each { String p ->
            if (body.containsKey(p) && instance.hasProperty(p)) {
                instance[p] = toBigDecimal(body[p])
            }
        }
        LONG_FIELDS.each { String p ->
            if (body.containsKey(p) && instance.hasProperty(p) && body[p] != null && body[p] != '') {
                // Skip GORM association synthetic FKs (read-only); bound via association below.
                // StockMove has a real Long productId (no Product association) — still assign there.
                if (p == 'productId' && (instance.hasProperty('product') || instance.hasProperty('catalogueItem'))) {
                    return
                }
                if (p == 'catalogueItemId' && instance.hasProperty('catalogueItem')) {
                    return
                }
                if (p == 'componentProductId' && (instance.hasProperty('componentProduct') || instance.hasProperty('componentItem'))) {
                    return
                }
                if (p == 'componentItemId' && instance.hasProperty('componentItem')) {
                    return
                }
                if (p == 'bomId' && instance.hasProperty('bom')) {
                    return
                }
                instance[p] = body[p] as Long
            }
        }
        INTEGER_FIELDS.each { String p ->
            if (body.containsKey(p) && instance.hasProperty(p) && body[p] != null && body[p] != '') {
                instance[p] = body[p] as Integer
            }
        }
        DATE_FIELDS.each { String p ->
            if (body.containsKey(p) && instance.hasProperty(p)) {
                instance[p] = toDate(body[p])
            }
        }

        if (instance.hasProperty('country')) {
            if (body.countryId) {
                instance['country'] = Countries.get(body.countryId as Long)
            } else if (body.countryCode) {
                instance['country'] = Countries.findByCode(body.countryCode as String)
            }
        }
        if (instance.hasProperty('account') && (body.accountId || body.accountCode)) {
            Account acc = null
            if (body.accountId) {
                acc = Account.get(body.accountId as Long)
            } else if (body.accountCode) {
                String npub = (instance.hasProperty('npubOwner') ? instance['npubOwner'] : null) as String
                acc = npub ? Account.findByNpubOwnerAndCode(npub, body.accountCode as String)
                        : Account.findByCode(body.accountCode as String)
            }
            instance['account'] = acc
        }
        if (instance.hasProperty('journalEntry') && body.journalEntryId) {
            instance['journalEntry'] = JournalEntry.get(body.journalEntryId as Long)
        }
        if (instance.hasProperty('invoice') && body.invoiceId) {
            instance['invoice'] = Invoice.get(body.invoiceId as Long)
        }
        // Ops masters: catalogueItem association (productId alias accepted)
        if (instance.hasProperty('catalogueItem')) {
            Object cid = body.catalogueItemId ?: body.productId
            if (cid != null && cid != '') {
                CatalogueItem ci = CatalogueItem.get(cid as Long)
                if (!ci) throw new IllegalArgumentException("catalogue item not found: ${cid}")
                instance['catalogueItem'] = ci
            }
        }
        if (instance.hasProperty('componentItem')) {
            Object cid = body.componentItemId ?: body.componentProductId ?: body.catalogueItemId ?: body.productId
            if (cid != null && cid != '') {
                CatalogueItem ci = CatalogueItem.get(cid as Long)
                if (!ci) throw new IllegalArgumentException("component catalogue item not found: ${cid}")
                instance['componentItem'] = ci
            }
        }
        if (instance.hasProperty('bom') && (body.bomId != null && body.bomId != '')) {
            BillOfMaterials parentBom = BillOfMaterials.get(body.bomId as Long)
            if (!parentBom) {
                throw new IllegalArgumentException("bom not found: ${body.bomId}")
            }
            instance['bom'] = parentBom
            if (instance.hasProperty('npubOwner') && !instance['npubOwner']) {
                instance['npubOwner'] = parentBom.npubOwner
            }
        }

        // Tenant create paths set npubOwner via enforceCreateNpub; keep body fallback for shared callers
        if (instance.hasProperty('npubOwner') && !instance['npubOwner']) {
            instance['npubOwner'] = body.npubOwner
        }

        // Defaults for production / BOM
        if (instance instanceof BillOfMaterials) {
            if (!instance.name && instance.catalogueItem) {
                instance.name = "BOM ${instance.catalogueItem.name}"
            }
            if (!instance.bomStatus) {
                instance.bomStatus = 'ACTIVE'
            }
        }
        if (instance instanceof BomItem) {
            if (instance.lineNo == null) {
                instance.lineNo = 1
            }
            if (instance.quantity == null) {
                instance.quantity = BigDecimal.ONE
            }
            if (!instance.unit) {
                instance.unit = instance.componentItem?.unit ?: 'EA'
            }
            if (!instance.name && instance.componentItem) {
                instance.name = instance.componentItem.name
            }
        }
        if (instance instanceof steakos.production.ProductionOrder) {
            if (!instance.prodStatus) {
                instance.prodStatus = 'DRAFT'
            }
            if (instance.quantity == null) {
                instance.quantity = BigDecimal.ONE
            }
            if (!instance.unit) {
                instance.unit = instance.catalogueItem?.unit ?: 'EA'
            }
        }

        // Defaults for core Task (OI-WK01)
        if (instance instanceof steakos.core.Task) {
            if (instance.urgency == null) {
                instance.urgency = 2
            }
            if (instance.importance == null) {
                instance.importance = 2
            }
            if (!instance.status) {
                instance.status = 'OPEN'
            }
        }

        // Defaults for HR Payroll (period is required YYYY-MM)
        if (instance instanceof Payroll) {
            if (!instance.period?.toString()?.trim()) {
                String raw = (body.period ?: body.periodCode)?.toString()?.trim()
                if (raw && raw.length() > 7) raw = raw.substring(0, 7)
                instance.period = raw ?: new java.text.SimpleDateFormat('yyyy-MM').format(new Date())
            }
            if (instance.period && instance.period.length() > 7) {
                instance.period = instance.period.substring(0, 7)
            }
            if (!instance.payrollStatus) {
                instance.payrollStatus = 'OPEN'
            }
            if (!instance.currencyCode) {
                instance.currencyCode = 'EUR'
            }
            if (instance.totalEur == null) {
                instance.totalEur = BigDecimal.ZERO
            }
            if (!instance.name?.trim()) {
                instance.name = "Payroll ${instance.period}"
            }
        }

        // Defaults for finance create paths
        if (instance instanceof JournalEntry && !instance.entryDate) {
            instance.entryDate = new Date()
        }
        if (instance instanceof JournalEntry && !instance.period) {
            instance.period = new Date().format('yyyy-MM')
        }
        if (instance instanceof Invoice && instance.amountEur == null && body.amountEur == null) {
            // leave for constraint failure if required
        }
        if (instance instanceof Invoice && !instance.issueDate && body.issueDate == null) {
            instance.issueDate = new Date()
        }
        if (instance instanceof Invoice && !instance.name && instance.invoiceNumber) {
            instance.name = "Invoice ${instance.invoiceNumber}"
        }
        if (instance instanceof JournalLine && !instance.name && instance.account) {
            instance.name = "Line ${instance.lineNo ?: 1} ${instance.account.code}"
        }
        // Budget / Payable — master-data edit parity with FinanceHub (L3)
        if (instance instanceof steakos.finance.Budget) {
            if (!instance.period?.toString()?.trim()) {
                instance.period = new java.text.SimpleDateFormat('yyyy-MM').format(new Date())
            } else if (instance.period.length() > 7) {
                instance.period = instance.period.substring(0, 7)
            }
            if (!instance.currencyCode) {
                instance.currencyCode = 'EUR'
            }
            if (instance.amountEur == null) {
                instance.amountEur = BigDecimal.ZERO
            }
            if (!instance.name?.trim()) {
                instance.name = "Budget ${instance.period}"
            }
        }
        if (instance instanceof steakos.finance.Payable) {
            if (!instance.payableStatus) {
                instance.payableStatus = 'OPEN'
            }
            if (!instance.currencyCode) {
                instance.currencyCode = 'EUR'
            }
            if (instance.amountEur == null) {
                instance.amountEur = BigDecimal.ZERO
            }
            if (!instance.name?.trim()) {
                instance.name = instance.vendorName ? "Payable ${instance.vendorName}" : 'Payable'
            }
        }
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw == null || raw == '') {
            return null
        }
        if (raw instanceof BigDecimal) {
            return raw
        }
        return new BigDecimal(raw.toString())
    }

    private static Date toDate(Object raw) {
        // Groovy 4: Date.parse(fmt, str) needs groovy-dateutil; use java.time helper instead
        return DateParse.toDate(raw)
    }

    private void ensureCache() {
        if (cache != null) {
            return
        }
        Map<String, Class> map = [:]
        grailsApplication.getArtefacts('Domain')?.each { GrailsClass gc ->
            Class clazz = gc.clazz
            if (!isAllowed(clazz)) {
                return
            }
            String simple = clazz.simpleName
            String pkgFull = clazz.package?.name ?: ''
            String shortPkg = pkgFull.startsWith('steakos.') ? pkgFull.substring('steakos.'.length()) : pkgFull
            if (shortPkg?.contains('.')) {
                shortPkg = shortPkg.split('\\.')[0]
            }
            if (shortPkg && simple) {
                map["${shortPkg}/${simple}"] = clazz
            }
        }
        cache = Collections.unmodifiableMap(map)
    }
}
