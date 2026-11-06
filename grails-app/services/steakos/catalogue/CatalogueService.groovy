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

package steakos.catalogue

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.helper.AssignableSupport

/**
 * Unified catalogue master (CATALOGUE-ITEM-CONCEPT).
 * Sole commercial master — legacy Product/Service tables removed.
 */
@Slf4j
class CatalogueService {

    steakos.configuration.CompanyCurrencyService companyCurrencyService

    @Transactional
    CatalogueItem create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        String nature = normalizeNature(fields.nature ?: fields.type ?: fields.itemType)
        Boolean sold = fields.containsKey('canBeSold') ? fields.canBeSold as Boolean : Boolean.TRUE
        Boolean bought = fields.containsKey('canBeBought') ? fields.canBeBought as Boolean : Boolean.TRUE
        if (sold == Boolean.FALSE && bought == Boolean.FALSE) {
            throw new IllegalArgumentException('at least one of canBeSold or canBeBought must be true')
        }
        String ccy = (fields.currency ?: fields.currencyCode ?: 'EUR') as String
        companyCurrencyService?.validateWritableCode(npub, ccy)
        CatalogueItem ci = new CatalogueItem(
                npubOwner   : npub,
                name        : name,
                description : fields.description as String,
                code        : normalizeCode(fields.code ?: fields.sku),
                unit        : (fields.unit ?: (nature == CatalogueItem.NATURE_SERVICE ? 'HOUR' : 'EA')) as String,
                nature      : nature,
                canBeSold   : sold,
                canBeBought : bought,
                active      : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE,
                listPrice   : toBd(fields.listPrice ?: fields.listPriceEur),
                currency    : ccy,
                defaultTaxCode: fields.defaultTaxCode as String ?: fields.taxCode as String,
                status      : (fields.status ?: 'ACTIVE') as String,
                *: AssignableSupport.initialPair(fields)
        )
        applyClassifiableAndAccess(ci, fields, true)
        ci.save(failOnError: true, flush: true)
        ci
    }

    @Transactional
    CatalogueItem update(Long id, String npubOwner, Map fields) {
        CatalogueItem ci = load(id, npubOwner)
        if (fields.name) ci.name = fields.name as String
        if (fields.containsKey('description')) ci.description = fields.description as String
        if (fields.containsKey('code') || fields.containsKey('sku')) {
            ci.code = normalizeCode(fields.code ?: fields.sku)
        }
        if (fields.unit) ci.unit = fields.unit as String
        if (fields.nature || fields.type || fields.itemType) {
            ci.nature = normalizeNature(fields.nature ?: fields.type ?: fields.itemType)
        }
        if (fields.containsKey('canBeSold')) ci.canBeSold = fields.canBeSold as Boolean
        if (fields.containsKey('canBeBought')) ci.canBeBought = fields.canBeBought as Boolean
        if (fields.containsKey('active')) ci.active = fields.active as Boolean
        if (fields.containsKey('listPrice') || fields.containsKey('listPriceEur')) {
            ci.listPrice = toBd(fields.listPrice ?: fields.listPriceEur)
        }
        if (fields.currency || fields.currencyCode) {
            String ccy = (fields.currency ?: fields.currencyCode) as String
            companyCurrencyService?.validateWritableCode(npubOwner, ccy)
            ci.currency = ccy
        }
        if (fields.containsKey('defaultTaxCode') || fields.containsKey('taxCode')) {
            ci.defaultTaxCode = (fields.defaultTaxCode ?: fields.taxCode) as String
        }
        applyClassifiableAndAccess(ci, fields, false)
        AssignableSupport.apply(ci, fields)
        if (ci.canBeSold == Boolean.FALSE && ci.canBeBought == Boolean.FALSE && ci.active) {
            throw new IllegalArgumentException('active item must be buyable and/or sellable')
        }
        ci.save(failOnError: true, flush: true)
        ci
    }

    @Transactional(readOnly = true)
    CatalogueItem get(Long id, String npubOwner) { load(id, npubOwner) }

    /**
     * Search catalogue for pickers (legacy). Prefer {@link #searchPage} for lists.
     */
    @Transactional(readOnly = true)
    List<CatalogueItem> search(String npubOwner, String q = null, String forChannel = null,
                               String nature = null, boolean activeOnly = true, int max = 500) {
        (searchPage(npubOwner, q, forChannel, nature, activeOnly, max, 0).items as List<CatalogueItem>)
    }

    /**
     * Paged catalogue search — full-dataset total under filters (LIST-PAGINATION).
     */
    @Transactional(readOnly = true)
    Map searchPage(String npubOwner, String q = null, String forChannel = null,
                   String nature = null, boolean activeOnly = true, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        int limit = Math.min(Math.max(max ?: 25, 1), 2000)
        int off = Math.max(offset ?: 0, 0)
        List items = CatalogueItem.createCriteria().list(max: limit, offset: off, sort: 'name', order: 'asc') {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            if (nature) eq('nature', normalizeNature(nature))
            if (forChannel == 'sale' || forChannel == 'sold' || forChannel == 'sell') {
                eq('canBeSold', Boolean.TRUE)
            } else if (forChannel == 'purchase' || forChannel == 'buy' || forChannel == 'bought') {
                eq('canBeBought', Boolean.TRUE)
            }
            if (q?.trim()) {
                String term = "%${q.trim()}%"
                or {
                    ilike('name', term)
                    ilike('code', term)
                }
            }
        } as List
        Number total = CatalogueItem.createCriteria().count {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            if (nature) eq('nature', normalizeNature(nature))
            if (forChannel == 'sale' || forChannel == 'sold' || forChannel == 'sell') {
                eq('canBeSold', Boolean.TRUE)
            } else if (forChannel == 'purchase' || forChannel == 'buy' || forChannel == 'bought') {
                eq('canBeBought', Boolean.TRUE)
            }
            if (q?.trim()) {
                String term = "%${q.trim()}%"
                or {
                    ilike('name', term)
                    ilike('code', term)
                }
            }
        } as Number
        [items: items, total: total?.intValue() ?: 0, max: limit, offset: off]
    }

    /**
     * Resolve catalogue id + defaults for a document line map.
     * Prefer catalogueItemId; productRefId / serviceId treated as catalogue id aliases.
     */
    @Transactional(readOnly = true)
    Map resolveForLine(String npubOwner, Map lm) {
        String npub = requireNpub(npubOwner)
        Long catId = firstId(lm?.catalogueItemId, lm?.productRefId, lm?.serviceId, lm?.serviceRefId)
        if (catId) {
            try {
                CatalogueItem ci = load(catId, npub)
                return [
                        catalogueItemId: ci.id,
                        name           : ci.name,
                        listPrice      : ci.listPrice,
                        unit           : ci.unit,
                        nature         : ci.nature,
                        currency       : ci.currency,
                        defaultTaxCode : ci.defaultTaxCode
                ]
            } catch (Exception ignored) { /* fall through */ }
        }
        [catalogueItemId: null, name: lm?.name, listPrice: null, unit: null, nature: null, currency: null, defaultTaxCode: null]
    }

    Map toMap(CatalogueItem ci) {
        if (!ci) return null
        [
                id            : ci.id,
                name          : ci.name,
                description   : ci.description,
                code          : ci.code,
                unit          : ci.unit,
                nature        : ci.nature,
                /** Convenience alias for APIs / CSV (GOODS→product, SERVICE→service). */
                type          : ci.nature == CatalogueItem.NATURE_SERVICE ? 'service' : 'product',
                canBeSold     : ci.canBeSold,
                canBeBought   : ci.canBeBought,
                active        : ci.active,
                listPrice     : ci.listPrice,
                currency      : ci.currency,
                defaultTaxCode: ci.defaultTaxCode,
                npubOwner     : ci.npubOwner,
                status        : ci.status,
                classification: ci.classification,
                subtype       : ci.type,
                tags          : ci.tags,
                protection    : ci.protection,
                access        : ci.access,
                *: AssignableSupport.toMapFragment(ci)
        ]
    }

    /**
     * Load item; optional nature constraint (GOODS | SERVICE).
     * Accepts catalogueItemId, productId, or id as field aliases at call sites.
     */
    @Transactional(readOnly = true)
    CatalogueItem requireItem(String npubOwner, Object idRaw, String nature = null) {
        if (idRaw == null || idRaw == '') {
            throw new IllegalArgumentException('catalogueItemId is required')
        }
        CatalogueItem ci = load(idRaw as Long, npubOwner)
        if (nature) {
            String want = normalizeNature(nature)
            if (ci.nature != want) {
                throw new IllegalArgumentException(
                        "catalogue item ${ci.id} nature is ${ci.nature}, expected ${want}")
            }
        }
        ci
    }

    @Transactional(readOnly = true)
    CatalogueItem requireGoods(String npubOwner, Object idRaw) {
        requireItem(npubOwner, idRaw, CatalogueItem.NATURE_GOODS)
    }

    /** Accept productId or catalogueItemId from API bodies. */
    static Object resolveId(Map fields) {
        fields?.catalogueItemId ?: fields?.productId ?: fields?.componentProductId ?:
                fields?.componentItemId ?: fields?.serviceId ?: fields?.id
    }

    private CatalogueItem load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        CatalogueItem ci = CatalogueItem.get(id)
        if (!ci || ci.npubOwner != npub) {
            throw new IllegalArgumentException("catalogue item not found: ${id}")
        }
        ci
    }

    private static Long firstId(Object... raws) {
        for (Object raw : raws) {
            if (raw != null && raw != '') {
                try {
                    return raw as Long
                } catch (Exception ignored) { }
            }
        }
        null
    }

    /**
     * Normalize catalogue nature from CSV/API: GOODS|SERVICE plus common aliases
     * (product/products, service/services, type=product|service, etc.).
     */
    static String normalizeNature(Object raw) {
        String n = raw?.toString()?.trim()?.toUpperCase()
        if (!n) return CatalogueItem.NATURE_GOODS
        // strip punctuation / spaces: "Product / Goods" → PRODUCTGOODS handled via token check
        String compact = n.replaceAll(/[^A-Z0-9]/, '')
        if (compact in ['SERVICE', 'SERVICES', 'SVC', 'SERV'] ||
                n in ['SERVICE', 'SERVICES', 'SVC']) {
            return CatalogueItem.NATURE_SERVICE
        }
        if (compact in ['GOODS', 'PRODUCT', 'PRODUCTS', 'GOOD', 'MATERIAL', 'ITEM', 'SKU'] ||
                n in ['GOODS', 'PRODUCT', 'PRODUCTS', 'GOOD', 'MATERIAL']) {
            return CatalogueItem.NATURE_GOODS
        }
        if (n == CatalogueItem.NATURE_SERVICE || n == CatalogueItem.NATURE_GOODS) return n
        throw new IllegalArgumentException(
                "invalid nature/type: ${raw} (use product|goods or service)")
    }

    /** Apply Classifiable + AccessControl fields from a create/update map. */
    private static void applyClassifiableAndAccess(CatalogueItem ci, Map fields, boolean creating) {
        if (fields.containsKey('status') || creating) {
            if (fields.status) ci.status = fields.status.toString().take(64)
            else if (creating && !ci.status) ci.status = 'ACTIVE'
        }
        if (fields.containsKey('classification')) {
            ci.classification = fields.classification?.toString()?.take(128)
        }
        // Classifiable.type = local subtype (not product/service — that is nature)
        Object subtype = fields.subtype ?: fields.localType ?: fields.itemSubtype
        if (fields.containsKey('subtype') || fields.containsKey('localType') || fields.containsKey('itemSubtype') || fields.containsKey('typeLocal')) {
            ci.type = subtype?.toString()?.take(64)
        }
        if (fields.containsKey('tags')) {
            ci.tags = fields.tags?.toString()?.take(2000)
        }
        if (fields.containsKey('protection') && fields.protection) {
            ci.protection = fields.protection.toString().take(32)
        }
        if (fields.containsKey('access')) {
            ci.access = fields.access?.toString()?.take(4000)
        }
    }

    private static String normalizeCode(Object raw) {
        String c = raw?.toString()?.trim()
        c ? c.take(64) : null
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') return null
        if (v instanceof BigDecimal) return v
        new BigDecimal(v.toString())
    }
}
