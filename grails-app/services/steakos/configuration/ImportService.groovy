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

package steakos.configuration

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import steakos.catalogue.CatalogueItem
import steakos.catalogue.CatalogueService
import steakos.finance.Account
import steakos.finance.FinanceLedgerService
import steakos.procurement.Vendor
import steakos.sales.Customer

/**
 * CSV import packs (Wave B / OI-I01–I02 + CATALOGUE-IMPORT-CONCEPT).
 * XLSX later via same text path after conversion.
 */
@Transactional
class ImportService {

    FinanceLedgerService financeLedgerService
    CatalogueService catalogueService

    /** Target steakOS fields per pack (for CSV column mapping UI). */
    static final Map<String, List<Map>> PACK_FIELDS = [
            CUSTOMERS: [
                    [code: 'name', label: 'Name', required: true],
                    [code: 'email', label: 'Email', required: false],
                    [code: 'phone', label: 'Phone', required: false],
                    [code: 'taxId', label: 'Tax ID / VAT', required: false],
                    [code: 'customerKind', label: 'Customer kind', required: false],
                    [code: 'communicationLocale', label: 'Communication language', required: false],
            ],
            VENDORS: [
                    [code: 'name', label: 'Name', required: true],
                    [code: 'email', label: 'Email', required: false],
                    [code: 'phone', label: 'Phone', required: false],
                    [code: 'taxId', label: 'Tax ID / VAT', required: false],
                    [code: 'communicationLocale', label: 'Communication language', required: false],
            ],
            CATALOGUE_ITEMS: [
                    [code: 'name', label: 'Name', required: true],
                    [code: 'code', label: 'Code / SKU', required: false],
                    [code: 'type', label: 'Type (product / service)', required: false],
                    [code: 'nature', label: 'Nature (GOODS / SERVICE)', required: false],
                    [code: 'unit', label: 'Unit', required: false],
                    [code: 'listPrice', label: 'List price', required: false],
                    [code: 'currency', label: 'Currency', required: false],
                    [code: 'canBeSold', label: 'Can be sold', required: false],
                    [code: 'canBeBought', label: 'Can be bought', required: false],
                    [code: 'active', label: 'Active', required: false],
                    [code: 'description', label: 'Description', required: false],
                    [code: 'status', label: 'Status', required: false],
                    [code: 'classification', label: 'Classification / category', required: false],
                    [code: 'subtype', label: 'Subtype (local)', required: false],
                    [code: 'tags', label: 'Tags', required: false],
                    [code: 'protection', label: 'Protection', required: false],
                    [code: 'access', label: 'Access list', required: false],
            ],
            COA: [
                    [code: 'code', label: 'Account code', required: true],
                    [code: 'name', label: 'Account name', required: false],
                    [code: 'accountType', label: 'Account type', required: false],
                    [code: 'accountSubType', label: 'Account subtype', required: false],
            ],
            OPENING_BALANCES: [
                    [code: 'code', label: 'Account code', required: false],
                    [code: 'debitEur', label: 'Debit EUR', required: false],
                    [code: 'creditEur', label: 'Credit EUR', required: false],
                    [code: 'memo', label: 'Memo', required: false],
            ],
    ].asImmutable()

    static final List PACKS = [
            [code: 'CUSTOMERS', label: 'Customers',
             headers: PACK_FIELDS.CUSTOMERS*.code, fields: PACK_FIELDS.CUSTOMERS],
            [code: 'VENDORS', label: 'Vendors',
             headers: PACK_FIELDS.VENDORS*.code, fields: PACK_FIELDS.VENDORS],
            [code: 'CATALOGUE_ITEMS', label: 'Catalogue items',
             headers: PACK_FIELDS.CATALOGUE_ITEMS*.code, fields: PACK_FIELDS.CATALOGUE_ITEMS],
            [code: 'COA', label: 'Chart of accounts',
             headers: PACK_FIELDS.COA*.code, fields: PACK_FIELDS.COA],
            [code: 'OPENING_BALANCES', label: 'Opening balances (note only)',
             headers: PACK_FIELDS.OPENING_BALANCES*.code, fields: PACK_FIELDS.OPENING_BALANCES],
    ].asImmutable()

    List packs() { PACKS }

    /** steaKOS target fields for a pack (mapping dropdown). */
    List packFields(String packType) {
        String pack = (packType ?: '').toUpperCase()
        PACK_FIELDS[pack] ?: []
    }

    ImportJob createJob(String npubOwner, String packType, String fileName, String rawText) {
        String npub = npubOwner?.trim() ?: SetupService.DEFAULT_NPUB
        String pack = (packType ?: '').toUpperCase()
        if (!PACKS.any { it.code == pack }) {
            throw new IllegalArgumentException("Unknown pack: ${packType}")
        }
        if (!rawText?.trim()) {
            throw new IllegalArgumentException('Empty file')
        }
        ImportJob job = new ImportJob(
                npubOwner  : npub,
                packType   : pack,
                status     : 'PENDING',
                fileName   : fileName ?: 'upload.csv',
                contentType: 'text/csv',
                rawText    : rawText
        )
        job.save(failOnError: true)
        job
    }

    /**
     * Dry-run preview. {@code options.mapping} = steakOS-field → CSV-header.
     * {@code options.defaultNature} = GOODS|SERVICE|product|service applied to every row
     * when set (catalogue packs).
     */
    Map preview(Long jobId, String npubOwner, Map options = null) {
        ImportJob job = load(jobId, npubOwner)
        Map opts = options ?: [:]
        // Support legacy 3rd-arg = mapping map (no defaultNature key)
        Map mappingOverride = null
        String defaultNature = null
        if (opts.containsKey('mapping') || opts.containsKey('defaultNature')) {
            mappingOverride = opts.containsKey('mapping') ? (opts.mapping as Map) : null
            defaultNature = normalizeOptionalNature(opts.defaultNature)
        } else if (opts && !opts.containsKey('confirmed')) {
            // Entire opts looks like a mapping map (steakOS field → csv header)
            mappingOverride = opts
        }
        List<List<String>> rows = parseCsv(job.rawText)
        if (!rows) {
            throw new IllegalArgumentException('No rows parsed')
        }
        List<String> headers = rows[0].collect { normalizeHeader(it) }
        // Preserve original CSV header labels for the mapping UI (display), but keys are normalized
        List<String> rawHeaders = rows[0].collect { (it ?: '').toString().trim() }
        List<Map> sample = []
        int dataRows = Math.max(0, rows.size() - 1)
        for (int i = 1; i < Math.min(rows.size(), 6); i++) {
            sample << rowToMap(headers, rows[i])
        }
        Map mapping
        if (mappingOverride != null) {
            mapping = sanitizeMapping(job.packType, headers, mappingOverride)
        } else if (job.mappingJson) {
            try {
                mapping = sanitizeMapping(job.packType, headers, new JsonSlurper().parseText(job.mappingJson) as Map)
            } catch (ignored) {
                mapping = defaultMapping(job.packType, headers)
            }
        } else {
            mapping = defaultMapping(job.packType, headers)
        }
        Map analysis = analyzeRows(job.packType, job.npubOwner, headers, rows, mapping, defaultNature)
        job.status = 'READY'
        job.rowCount = dataRows
        job.mappingJson = JsonOutput.toJson(mapping)
        // Persist dry-run analysis + defaults for the confirmation UI / audit
        job.reportJson = JsonOutput.toJson([phase: 'analysis', analysis: analysis, defaultNature: defaultNature])
        job.errorCount = analysis.invalidCount as int
        job.errorSummary = analysis.summaryMessage as String
        job.save(failOnError: true)
        [
                id           : job.id,
                packType     : job.packType,
                headers      : headers,
                rawHeaders   : rawHeaders,
                targetFields : packFields(job.packType),
                mapping      : mapping,
                /** CSV-header → steakOS field (UI-friendly inverse of mapping). */
                csvMapping   : invertMapping(headers, mapping),
                defaultNature: defaultNature,
                sample       : sample,
                rowCount     : dataRows,
                status       : job.status,
                analysis     : analysis,
                summary      : analysis.summaryMessage,
        ]
    }

    private static String normalizeOptionalNature(Object raw) {
        if (raw == null || !raw.toString().trim()) return null
        CatalogueService.normalizeNature(raw)
    }

    /** Force nature/type on a mapped row when a pack-wide default is set. */
    private static void applyDefaultNature(Map vals, String defaultNature) {
        if (!defaultNature || vals == null) return
        String n = CatalogueService.normalizeNature(defaultNature)
        vals.nature = n
        vals.type = (n == CatalogueItem.NATURE_SERVICE) ? 'service' : 'product'
    }

    /** Keep only known pack fields whose CSV header exists on this file. */
    private static Map sanitizeMapping(String pack, List<String> headers, Map raw) {
        Set headerSet = headers as Set
        Set allowed = (PACK_FIELDS[(pack ?: '').toUpperCase()]*.code ?: []) as Set
        Map out = [:]
        raw?.each { k, v ->
            String field = k?.toString()
            String csv = v?.toString()
            if (!field || !csv) return
            if (allowed && !(field in allowed)) return
            // accept normalized or raw header match
            String hit = headers.find { it == csv || it == normalizeHeader(csv) }
            if (hit) out[field] = hit
        }
        out
    }

    /** Invert steakOS→CSV into CSV→steakOS for every CSV header (empty = ignore). */
    private static Map invertMapping(List<String> headers, Map steakToCsv) {
        Map csvToSteak = [:]
        steakToCsv?.each { steak, csv ->
            if (csv) csvToSteak[csv.toString()] = steak.toString()
        }
        Map out = [:]
        headers.each { String h ->
            out[h] = csvToSteak[h] ?: ''
        }
        out
    }

    /**
     * Commit only after explicit confirmation. Invalid rows are skipped (not written).
     * @param options.confirmed must be true
     * @param options.mapping optional mapping override
     */
    Map commit(Long jobId, String npubOwner, Map options = null) {
        ImportJob job = load(jobId, npubOwner)
        if (!(job.status in ['READY', 'PENDING', 'FAILED'])) {
            throw new IllegalStateException("Job not ready to commit: ${job.status}")
        }
        Map opts = options ?: [:]
        if (!opts.confirmed) {
            throw new IllegalArgumentException(
                    'Import not confirmed. Review the analysis summary and call commit with confirmed=true.')
        }
        Map mapping = (opts.mapping as Map) ?: (job.mappingJson ? new JsonSlurper().parseText(job.mappingJson) as Map : [:])
        String defaultNature = normalizeOptionalNature(opts.defaultNature)
        List<List<String>> rows = parseCsv(job.rawText)
        if (rows.size() < 2) {
            throw new IllegalArgumentException('No data rows')
        }
        List<String> headers = rows[0].collect { normalizeHeader(it) }
        int created = 0, updated = 0, skipped = 0, errors = 0
        List report = []
        String npub = job.npubOwner
        job.status = 'RUNNING'
        job.save(failOnError: true)

        for (int i = 1; i < rows.size(); i++) {
            Map row = rowToMap(headers, rows[i])
            Map vals = applyMapping(row, mapping)
            applyDefaultNature(vals, defaultNature)
            Map check = validateRow(job.packType, npub, vals)
            if (!check.valid) {
                errors++
                report << [row: i, action: 'invalid', message: check.message]
                continue
            }
            try {
                Map r = commitRow(job.packType, npub, vals)
                if (r.action == 'created') created++
                else if (r.action == 'updated') updated++
                else skipped++
                report << [row: i, action: r.action, key: r.key]
            } catch (Exception e) {
                errors++
                report << [row: i, action: 'error', message: e.message]
            }
        }

        job.createdCount = created
        job.updatedCount = updated
        job.skippedCount = skipped
        job.errorCount = errors
        job.rowCount = rows.size() - 1
        job.reportJson = JsonOutput.toJson(report.take(500))
        job.errorSummary = errors ? "${errors} row(s) invalid or failed (not imported)" : null
        job.status = (created == 0 && updated == 0 && errors > 0) ? 'FAILED' : 'COMPLETED'
        job.save(failOnError: true)

        Map result = toMap(job)
        result.importedCount = created + updated
        result
    }

    /**
     * Dry-run: classify every data row as valid/invalid without writing.
     * Returns counts + human-readable summary for confirmation UI.
     */
    Map analyzeRows(String pack, String npub, List<String> headers, List<List<String>> rows, Map mapping,
                    String defaultNature = null) {
        int total = Math.max(0, rows.size() - 1)
        int valid = 0
        int invalid = 0
        int willCreate = 0
        int willUpdate = 0
        // Catalogue breakdown
        int goodsActive = 0, goodsInactive = 0, serviceActive = 0, serviceInactive = 0
        List invalidSamples = []
        Set codesInFile = [] as Set

        for (int i = 1; i < rows.size(); i++) {
            Map row = rowToMap(headers, rows[i])
            Map vals = applyMapping(row, mapping)
            applyDefaultNature(vals, defaultNature)
            Map check = validateRow(pack, npub, vals)
            if (!check.valid) {
                invalid++
                if (invalidSamples.size() < 25) {
                    invalidSamples << [row: i, message: check.message]
                }
                continue
            }
            valid++
            if (pack == 'CATALOGUE_ITEMS') {
                Map norm = normalizeCatalogueVals(vals)
                String nature = (norm.nature ?: CatalogueItem.NATURE_GOODS).toString()
                Boolean active = norm.containsKey('active') ? (norm.active as Boolean) : Boolean.TRUE
                if (nature == CatalogueItem.NATURE_SERVICE) {
                    if (active != Boolean.FALSE) serviceActive++ else serviceInactive++
                } else {
                    if (active != Boolean.FALSE) goodsActive++ else goodsInactive++
                }
                String code = norm.code?.toString()?.trim()
                if (code) {
                    if (codesInFile.contains(code)) {
                        // second occurrence of same code in file → still valid upsert, counts as update intent
                    }
                    codesInFile.add(code)
                    if (CatalogueItem.findByNpubOwnerAndCode(npub, code)) willUpdate++
                    else willCreate++
                } else {
                    willCreate++
                }
            } else if (pack == 'CUSTOMERS') {
                String name = vals.name?.toString()?.trim()
                if (name && Customer.findByNpubOwnerAndName(npub, name)) willUpdate++
                else willCreate++
            } else if (pack == 'VENDORS') {
                String name = vals.name?.toString()?.trim()
                if (name && Vendor.findByNpubOwnerAndName(npub, name)) willUpdate++
                else willCreate++
            } else if (pack == 'COA') {
                String code = vals.code?.toString()?.trim()
                if (code && Account.findByNpubOwnerAndCode(npub, code)) willUpdate++
                else willCreate++
            } else {
                willCreate++
            }
        }

        String label = PACKS.find { it.code == pack }?.label ?: pack
        String summary
        if (pack == 'CATALOGUE_ITEMS') {
            summary = "You are going to import ${valid} out of ${total} catalogue items " +
                    "(${goodsActive} products active, ${goodsInactive} products inactive, " +
                    "${serviceActive} services active, ${serviceInactive} services inactive). " +
                    "${invalid} record(s) are invalid and will not be imported."
        } else {
            summary = "You are going to import ${valid} out of ${total} ${label.toLowerCase()} " +
                    "(~${willCreate} new, ~${willUpdate} updates). " +
                    "${invalid} record(s) are invalid and will not be imported."
        }

        [
                packType          : pack,
                packLabel         : label,
                totalRows         : total,
                validCount        : valid,
                invalidCount      : invalid,
                willCreate        : willCreate,
                willUpdate        : willUpdate,
                goodsActive       : goodsActive,
                goodsInactive     : goodsInactive,
                serviceActive     : serviceActive,
                serviceInactive   : serviceInactive,
                invalidSamples    : invalidSamples,
                summaryMessage    : summary,
                canProceed        : valid > 0,
        ]
    }

    /** Non-writing validation for one mapped row. */
    private Map validateRow(String pack, String npub, Map vals) {
        try {
            switch (pack) {
                case 'CUSTOMERS':
                case 'VENDORS':
                    if (!vals.name?.toString()?.trim()) {
                        return [valid: false, message: 'name required']
                    }
                    return [valid: true]
                case 'CATALOGUE_ITEMS':
                    return validateCatalogueRow(vals)
                case 'COA':
                    if (!vals.code?.toString()?.trim()) {
                        return [valid: false, message: 'code required']
                    }
                    return [valid: true]
                case 'OPENING_BALANCES':
                    return [valid: true]
                default:
                    return [valid: false, message: "unknown pack ${pack}"]
            }
        } catch (Exception e) {
            return [valid: false, message: e.message ?: 'invalid row']
        }
    }

    private Map validateCatalogueRow(Map vals) {
        Map norm
        try {
            norm = normalizeCatalogueVals(vals)
        } catch (Exception e) {
            return [valid: false, message: e.message ?: 'invalid fields']
        }
        if (!norm.name?.toString()?.trim()) {
            return [valid: false, message: 'name required']
        }
        try {
            CatalogueService.normalizeNature(norm.nature ?: norm.type)
        } catch (Exception e) {
            return [valid: false, message: e.message ?: "invalid type/nature: ${norm.nature ?: norm.type}"]
        }
        Boolean sold = norm.containsKey('canBeSold') ? norm.canBeSold as Boolean : Boolean.TRUE
        Boolean bought = norm.containsKey('canBeBought') ? norm.canBeBought as Boolean : Boolean.TRUE
        if (sold == Boolean.FALSE && bought == Boolean.FALSE) {
            return [valid: false, message: 'at least one of canBeSold or canBeBought must be true']
        }
        // Non-numeric listPrice (column shift / prose) → treat as null, do not reject the row
        if (norm.listPrice != null && !(norm.listPrice instanceof Number)) {
            BigDecimal parsed = parseLooseDecimal(norm.listPrice)
            norm.listPrice = parsed
        }
        return [valid: true]
    }

    /**
     * Accept "12.50", "12,50", "€ 12,50", empty → null. Reject prose (column shift).
     */
    private static BigDecimal parseLooseDecimal(Object raw) {
        if (raw == null) return null
        String s = raw.toString().trim()
        if (!s) return null
        // Must look numeric after stripping currency junk
        // Single-quoted pattern: $ must stay literal (slashy /…/ would interpolate)
        String cleaned = s.replaceAll('[€$£\\s]', '')
        // German thousands/decimal: 1.234,56 → 1234.56 ; plain 12,50 → 12.50
        if (cleaned.matches(/^-?\d{1,3}(\.\d{3})+(,\d+)?$/)) {
            cleaned = cleaned.replace('.', '').replace(',', '.')
        } else if (cleaned.contains(',') && !cleaned.contains('.')) {
            cleaned = cleaned.replace(',', '.')
        }
        if (!cleaned.matches(/^-?\d+(\.\d+)?$/)) return null
        try {
            return new BigDecimal(cleaned)
        } catch (Exception ignored) {
            return null
        }
    }

    Map toMap(ImportJob job) {
        [
                id          : job.id,
                packType    : job.packType,
                status      : job.status,
                fileName    : job.fileName,
                rowCount    : job.rowCount,
                createdCount: job.createdCount,
                updatedCount: job.updatedCount,
                skippedCount: job.skippedCount,
                errorCount  : job.errorCount,
                errorSummary: job.errorSummary,
                report      : job.reportJson ? new JsonSlurper().parseText(job.reportJson) : []
        ]
    }

    List listJobs(String npubOwner) {
        String npub = npubOwner?.trim() ?: SetupService.DEFAULT_NPUB
        ImportJob.findAllByNpubOwner(npub, [sort: 'id', order: 'desc', max: 50]).collect { toMap(it) }
    }

    private Map commitRow(String pack, String npub, Map vals) {
        switch (pack) {
            case 'CUSTOMERS':
                return upsertCustomer(npub, vals)
            case 'VENDORS':
                return upsertVendor(npub, vals)
            case 'CATALOGUE_ITEMS':
                return upsertCatalogueItem(npub, vals)
            case 'COA':
                return upsertAccount(npub, vals)
            case 'OPENING_BALANCES':
                // Wave B: record as skipped note — full JE pack needs balanced multi-row posts
                return [action: 'skipped', key: vals.code ?: vals.memo ?: 'balance']
            default:
                throw new IllegalArgumentException("pack ${pack}")
        }
    }

    private Map upsertCustomer(String npub, Map vals) {
        String name = vals.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name required')
        Customer c = Customer.findByNpubOwnerAndName(npub, name)
        boolean created = false
        if (!c) {
            c = new Customer(npubOwner: npub, name: name)
            created = true
        }
        if (vals.email) c.email = vals.email.toString()
        if (vals.phone) c.phone = vals.phone.toString()
        if (vals.taxId) c.taxId = vals.taxId.toString()
        if (vals.customerKind) c.customerKind = vals.customerKind.toString()
        String loc = firstNonBlank(vals, 'communicationLocale', 'communicationlocale',
                'language', 'locale', 'sprache')
        if (loc != null) {
            c.communicationLocale = normalizePartnerLocale(loc)
        }
        c.active = Boolean.TRUE
        c.save(failOnError: true)
        [action: created ? 'created' : 'updated', key: name]
    }

    private Map upsertVendor(String npub, Map vals) {
        String name = vals.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name required')
        Vendor v = Vendor.findByNpubOwnerAndName(npub, name)
        boolean created = false
        if (!v) {
            v = new Vendor(npubOwner: npub, name: name)
            created = true
        }
        if (vals.email) v.email = vals.email.toString()
        if (vals.phone) v.phone = vals.phone.toString()
        if (vals.taxId) v.taxId = vals.taxId.toString()
        String loc = firstNonBlank(vals, 'communicationLocale', 'communicationlocale',
                'language', 'locale', 'sprache')
        if (loc != null) {
            v.communicationLocale = normalizePartnerLocale(loc)
        }
        v.active = Boolean.TRUE
        v.save(failOnError: true)
        [action: created ? 'created' : 'updated', key: name]
    }

    /** OI-I18N02 — blank clears; en/de shorthand → full locale. */
    private static String normalizePartnerLocale(String raw) {
        if (raw == null || !raw.toString().trim()) return null
        String c = raw.toString().trim().replace('-', '_')
        if (c.equalsIgnoreCase('en')) return 'en_US'
        if (c.equalsIgnoreCase('de')) return 'de_DE'
        return c
    }

    /**
     * CATALOGUE-IMPORT-CONCEPT: match by code (or sku alias); no dual-write; per-row errors at caller.
     */
    private Map upsertCatalogueItem(String npub, Map vals) {
        Map norm = normalizeCatalogueVals(vals)
        String name = norm.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name required')

        String code = norm.code?.toString()?.trim()
        CatalogueItem existing = null
        if (code) {
            existing = CatalogueItem.findByNpubOwnerAndCode(npub, code)
        }

        if (existing) {
            if (!catalogueService) {
                throw new IllegalStateException('CatalogueService not available')
            }
            catalogueService.update(existing.id, npub, norm)
            return [action: 'updated', key: code ?: name]
        }

        if (!catalogueService) {
            throw new IllegalStateException('CatalogueService not available')
        }
        CatalogueItem created = catalogueService.create(npub, norm)
        [action: 'created', key: created.code ?: created.name]
    }

    /**
     * Normalize catalogue CSV / mapped row into CatalogueService.create/update fields.
     * Accepts {@code type=product|service} (and aliases) as nature; keeps {@code subtype}
     * for Classifiable.local type so it does not collide with product/service.
     */
    private static Map normalizeCatalogueVals(Map vals) {
        Map m = [:]
        // support both mapped camelCase and raw normalized headers
        m.name = firstNonBlank(vals, 'name', 'item', 'itemname', 'product', 'productname', 'servicename')
        m.code = firstNonBlank(vals, 'code', 'sku', 'itemcode', 'productcode', 'servicecode')
        m.unit = firstNonBlank(vals, 'unit', 'uom')
        String priceRaw = firstNonBlank(vals, 'listPrice', 'listPriceEur', 'listprice', 'listpriceeur', 'price', 'unitprice', 'vkpreis', 'preis')
        if (priceRaw) {
            BigDecimal pd = parseLooseDecimal(priceRaw)
            // If mapped column is not numeric (CSV shift), drop price rather than failing the row later
            m.listPrice = pd
        }
        m.currency = firstNonBlank(vals, 'currency', 'currencyCode', 'currencycode', 'ccy', 'curr', 'waehrung')
        m.description = firstNonBlank(vals, 'description', 'description_en', 'desc', 'details', 'note', 'notes', 'beschreibung')
        // Prefer German then English product names when present as raw keys
        if (!m.name) {
            m.name = firstNonBlank(vals, 'name_de', 'namede', 'name_en', 'nameen', 'name_de_shop', 'bezeichnung', 'title')
        }
        if (!m.code) {
            m.code = firstNonBlank(vals, 'article_number', 'articlenumber', 'artikelnummer', 'sku', 'item_id')
        }

        // Nature: prefer explicit nature, else type/itemType when product|service|goods
        String natureRaw = firstNonBlank(vals, 'nature', 'itemnature', 'goodsservice')
        String typeRaw = firstNonBlank(vals, 'type', 'itemtype', 'producttype', 'cataloguetype')
        if (natureRaw) {
            m.nature = CatalogueService.normalizeNature(natureRaw)
        } else if (typeRaw) {
            m.nature = CatalogueService.normalizeNature(typeRaw)
        } else {
            m.nature = CatalogueItem.NATURE_GOODS
        }
        // Convenience echo for callers / reports
        m.type = m.nature == CatalogueItem.NATURE_SERVICE ? 'service' : 'product'

        Boolean sold = parseBool(firstNonBlank(vals, 'canBeSold', 'canbesold', 'sold', 'sellable', 'sell'))
        Boolean bought = parseBool(firstNonBlank(vals, 'canBeBought', 'canbebought', 'bought', 'buyable', 'buy', 'purchasable'))
        Boolean active = parseBool(firstNonBlank(vals, 'active', 'enabled', 'isactive'))

        if (sold != null) m.canBeSold = sold
        if (bought != null) m.canBeBought = bought
        if (active != null) m.active = active

        // Classifiable + AccessControl
        String status = firstNonBlank(vals, 'status', 'itemstatus', 'lifecycle')
        if (status) m.status = status
        String classification = firstNonBlank(vals, 'classification', 'class', 'category', 'taxon')
        if (classification != null) m.classification = classification
        String subtype = firstNonBlank(vals, 'subtype', 'localType', 'localtype', 'itemSubtype', 'itemsubtype', 'typeLocal', 'typelocal')
        if (subtype != null) m.subtype = subtype
        String tags = firstNonBlank(vals, 'tags', 'tag', 'labels')
        if (tags != null) m.tags = tags
        String protection = firstNonBlank(vals, 'protection', 'accessmode')
        if (protection) m.protection = protection
        String access = firstNonBlank(vals, 'access', 'accesslist', 'acl')
        if (access != null) m.access = access

        m
    }

    private static String firstNonBlank(Map vals, String... keys) {
        for (String k : keys) {
            def v = vals[k]
            if (v != null && v.toString().trim()) return v.toString().trim()
        }
        null
    }

    private static Boolean parseBool(String raw) {
        if (raw == null || !raw.toString().trim()) return null
        String s = raw.toString().trim().toLowerCase()
        if (s in ['true', '1', 'yes', 'y', 't']) return Boolean.TRUE
        if (s in ['false', '0', 'no', 'n', 'f']) return Boolean.FALSE
        throw new IllegalArgumentException("invalid boolean: ${raw}")
    }

    private Map upsertAccount(String npub, Map vals) {
        String code = vals.code?.toString()?.trim()
        if (!code) throw new IllegalArgumentException('code required')
        financeLedgerService?.ensureChartSeeded(npub)
        Account a = Account.findByNpubOwnerAndCode(npub, code)
        boolean created = false
        if (!a) {
            a = new Account(
                    npubOwner  : npub,
                    code       : code,
                    name       : (vals.name ?: code).toString(),
                    accountType: (vals.accountType ?: 'ASSET').toString(),
                    currencyCode: 'EUR',
                    active     : Boolean.TRUE
            )
            created = true
        } else if (vals.name) {
            a.name = vals.name.toString()
        }
        if (vals.accountSubType) a.accountSubType = vals.accountSubType.toString()
        if (vals.accountType) a.accountType = vals.accountType.toString()
        a.save(failOnError: true)
        [action: created ? 'created' : 'updated', key: code]
    }

    private ImportJob load(Long id, String npubOwner) {
        String npub = npubOwner?.trim() ?: SetupService.DEFAULT_NPUB
        ImportJob job = ImportJob.get(id)
        if (!job || job.npubOwner != npub) {
            throw new IllegalArgumentException("Import job not found: ${id}")
        }
        job
    }

    private static List<List<String>> parseCsv(String text) {
        // Strip UTF-8 BOM so the first header is not "\uFEFFarticle_number"
        String cleaned = (text ?: '').replaceFirst(/^\uFEFF/, '')
        char delim = detectCsvDelimiter(cleaned)
        List<List<String>> rows = []
        cleaned.eachLine { String line ->
            if (line == null) return
            String t = line.trim()
            if (!t || t.startsWith('#')) return
            rows << splitCsvLine(t, delim)
        }
        rows
    }

    /**
     * Prefer semicolon when it dominates the header (EU/DE exports); otherwise comma.
     * Never treat both as delimiters — commas inside German decimals / prose must stay intact.
     */
    private static char detectCsvDelimiter(String text) {
        String header = text?.readLines()?.find { String l ->
            String t = l?.trim()
            t && !t.startsWith('#')
        }
        if (!header) return ',' as char
        int semis = header.count(';')
        int commas = header.count(',')
        int tabs = header.count('\t')
        if (tabs > semis && tabs > commas) return '\t' as char
        if (semis > commas) return ';' as char
        return ',' as char
    }

    private static List<String> splitCsvLine(String line, char delim = ',' as char) {
        List<String> out = []
        StringBuilder cur = new StringBuilder()
        boolean inQ = false
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i)
            if (c == '"') {
                inQ = !inQ
            } else if (c == delim && !inQ) {
                out << cur.toString().trim()
                cur.setLength(0)
            } else {
                cur.append(c)
            }
        }
        out << cur.toString().trim()
        out
    }

    private static String normalizeHeader(String h) {
        (h ?: '').trim().toLowerCase().replaceAll(/[^a-z0-9]+/, '')
    }

    private static Map rowToMap(List<String> headers, List<String> cells) {
        Map m = [:]
        headers.eachWithIndex { String h, int i ->
            m[h] = i < cells.size() ? cells[i] : ''
        }
        m
    }

    private static Map defaultMapping(String pack, List<String> headers) {
        Map aliases = [
                // DE/EU catalogue exports often use name_de / article_number (goki, shopware, …)
                name: ['name', 'namede', 'namedeshop', 'nameen', 'bezeichnung', 'title',
                       'customer', 'customername', 'vendor', 'vendorname', 'company',
                       'item', 'itemname', 'product', 'productname', 'servicename'],
                email: ['email', 'mail', 'e-mail'],
                phone: ['phone', 'tel', 'telephone', 'mobile'],
                taxid: ['taxid', 'vat', 'vatid', 'tax'],
                customerkind: ['customerkind', 'kind', 'type'],
                communicationlocale: ['communicationlocale', 'language', 'locale', 'sprache',
                                      'communication_locale', 'kommunikationssprache'],
                code: ['code', 'articlenumber', 'artikelnummer', 'sku', 'itemid', 'account',
                       'accountcode', 'glcode', 'itemcode', 'productcode', 'servicecode'],
                // Catalogue: type/nature = product|service|goods (CSV "type" preferred in samples)
                type: ['type', 'itemtype', 'producttype', 'cataloguetype', 'nature', 'itemnature', 'goodsservice'],
                nature: ['nature', 'itemnature', 'goodsservice', 'type', 'itemtype', 'producttype'],
                unit: ['unit', 'uom'],
                listprice: ['listprice', 'listpriceeur', 'price', 'unitprice', 'list_price', 'vkpreis', 'preis'],
                currency: ['currency', 'currencycode', 'ccy', 'curr', 'waehrung'],
                canbesold: ['canbesold', 'sold', 'sellable', 'sell'],
                canbebought: ['canbebought', 'bought', 'buyable', 'buy', 'purchasable'],
                active: ['active', 'enabled', 'isactive'],
                description: ['description', 'descriptionen', 'desc', 'details', 'note', 'notes', 'beschreibung'],
                status: ['status', 'itemstatus', 'lifecycle'],
                classification: ['classification', 'class', 'category', 'taxon'],
                subtype: ['subtype', 'localtype', 'itemsubtype', 'typelocal'],
                tags: ['tags', 'tag', 'labels'],
                protection: ['protection', 'accessmode'],
                access: ['access', 'accesslist', 'acl'],
                accounttype: ['accounttype', 'acctype'],
                accountsubtype: ['accountsubtype', 'sub'],
                debiteur: ['debiteur', 'debit', 'dr'],
                crediteur: ['crediteur', 'credit', 'cr'],
                memo: ['memo', 'description', 'note'],
        ]
        Map fieldNames = [
                CUSTOMERS         : ['name', 'email', 'phone', 'taxId', 'customerKind', 'communicationLocale'],
                VENDORS           : ['name', 'email', 'phone', 'taxId', 'communicationLocale'],
                CATALOGUE_ITEMS   : ['name', 'code', 'type', 'nature', 'unit', 'listPrice', 'currency',
                                     'canBeSold', 'canBeBought', 'active', 'description',
                                     'status', 'classification', 'subtype', 'tags', 'protection', 'access'],
                COA               : ['code', 'name', 'accountType', 'accountSubType'],
                OPENING_BALANCES  : ['code', 'debitEur', 'creditEur', 'memo'],
        ]
        Map mapping = [:]
        Set usedCsv = [] as Set
        (fieldNames[pack] ?: []).each { String field ->
            String key = field.toLowerCase().replaceAll(/[^a-z0-9]/, '')
            List al = aliases[key] ?: [key]
            // Prefer an unused CSV column so type + nature do not both bind to the same header
            String hit = headers.find { h -> al.contains(h) && !(h in usedCsv) }
            if (!hit) hit = headers.find { h -> al.contains(h) }
            if (hit) {
                mapping[field] = hit
                usedCsv << hit
            }
        }
        // If type and nature still share one column, keep type only (product/service alias)
        if (mapping.type && mapping.nature && mapping.type == mapping.nature) {
            mapping.remove('nature')
        }
        mapping
    }

    private static Map applyMapping(Map row, Map mapping) {
        Map out = [:]
        mapping.each { k, v ->
            out[k.toString()] = row[v.toString()]
        }
        // if mapping empty, use row as-is with original keys
        if (!out) return row
        out
    }
}
