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

package steakos.sales

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import java.math.RoundingMode
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames

/**
 * SalesAdvanced price lists (OI-S04 / G5). Company-scoped via npubOwner.
 * Apply-to-quote hooks stay thin here; full CPQ is SalesPro.
 */
@Slf4j
class SalesPriceListService {

    DomainEventBusService domainEventBusService
    steakos.configuration.CompanyCurrencyService companyCurrencyService
    SalesCustomerService salesCustomerService

    @Transactional
    PriceList createPriceList(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')

        String ccy = (fields.currencyCode ?: 'EUR') as String
        companyCurrencyService?.validateWritableCode(npub, ccy)

        String kind = (fields.listKind ?: PriceList.KIND_STANDARD) as String
        Long customerId = parseLong(fields.customerId)
        if (kind == PriceList.KIND_CUSTOMER && !customerId) {
            throw new IllegalArgumentException('customerId is required for CUSTOMER price lists')
        }
        if (customerId) {
            salesCustomerService?.getCustomer(customerId, npub)
        }

        String code = blankToNull(fields.code)
        if (code) {
            PriceList dup = PriceList.findByNpubOwnerAndCode(npub, code)
            if (dup) throw new IllegalArgumentException("price list code already exists: ${code}")
        }

        PriceList pl = new PriceList(
                npubOwner               : npub,
                name                    : name,
                description             : fields.description as String,
                code                    : code,
                listKind                : kind,
                currencyCode            : ccy,
                active                  : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE,
                validFrom               : parseDate(fields.validFrom),
                validTo                 : parseDate(fields.validTo),
                customerId              : customerId,
                defaultDiscountPercent  : parseDecimal(fields.defaultDiscountPercent),
                status                  : (fields.status ?: 'ACTIVE') as String
        )
        pl.save(failOnError: true, flush: true)

        List lines = (fields.lines instanceof List) ? (fields.lines as List) : null
        if (lines != null) {
            replaceLines(pl, npub, lines)
        }

        emitUpdated(pl, 'created')
        return pl
    }

    @Transactional
    PriceList updatePriceList(Long id, String npubOwner, Map fields) {
        PriceList pl = load(id, npubOwner)
        if (fields.name) pl.name = fields.name as String
        if (fields.containsKey('description')) pl.description = fields.description as String
        if (fields.containsKey('code')) {
            String code = blankToNull(fields.code)
            if (code) {
                PriceList dup = PriceList.findByNpubOwnerAndCode(pl.npubOwner, code)
                if (dup && dup.id != pl.id) {
                    throw new IllegalArgumentException("price list code already exists: ${code}")
                }
            }
            pl.code = code
        }
        if (fields.listKind) pl.listKind = fields.listKind as String
        if (fields.currencyCode) {
            companyCurrencyService?.validateWritableCode(npubOwner, fields.currencyCode as String)
            pl.currencyCode = fields.currencyCode as String
        }
        if (fields.containsKey('active')) pl.active = fields.active as Boolean
        if (fields.containsKey('validFrom')) pl.validFrom = parseDate(fields.validFrom)
        if (fields.containsKey('validTo')) pl.validTo = parseDate(fields.validTo)
        if (fields.containsKey('customerId')) {
            Long customerId = parseLong(fields.customerId)
            if (customerId) {
                salesCustomerService?.getCustomer(customerId, npubOwner)
            }
            pl.customerId = customerId
        }
        if (fields.containsKey('defaultDiscountPercent')) {
            pl.defaultDiscountPercent = parseDecimal(fields.defaultDiscountPercent)
        }
        if (fields.status) pl.status = fields.status as String

        if (pl.listKind == PriceList.KIND_CUSTOMER && !pl.customerId) {
            throw new IllegalArgumentException('customerId is required for CUSTOMER price lists')
        }

        pl.save(failOnError: true, flush: true)

        if (fields.lines instanceof List) {
            replaceLines(pl, npubOwner, fields.lines as List)
        }

        emitUpdated(pl, 'updated')
        return pl
    }

    @Transactional
    PriceList setLines(Long id, String npubOwner, List lines) {
        PriceList pl = load(id, npubOwner)
        replaceLines(pl, npubOwner, lines ?: [])
        emitUpdated(pl, 'lines')
        return pl
    }

    @Transactional
    PriceList deactivate(Long id, String npubOwner) {
        PriceList pl = load(id, npubOwner)
        pl.active = Boolean.FALSE
        pl.status = 'INACTIVE'
        pl.save(failOnError: true, flush: true)
        emitUpdated(pl, 'deactivated')
        return pl
    }

    @Transactional
    void deletePriceList(Long id, String npubOwner) {
        PriceList pl = load(id, npubOwner)
        Long listId = pl.id
        String code = pl.code
        PriceListLine.findAllByPriceList(pl).each { it.delete(flush: false) }
        pl.delete(flush: true)
        try {
            domainEventBusService?.publish(
                    DomainEventNames.SALES_PRICELIST_UPDATED,
                    'sales',
                    npubOwner,
                    [priceListId: listId, code: code, action: 'deleted']
            )
        } catch (Exception e) {
            log.warn("sales.pricelist.updated publish failed: ${e.message}")
        }
    }

    @Transactional(readOnly = true)
    PriceList getPriceList(Long id, String npubOwner) {
        load(id, npubOwner)
    }

    @Transactional(readOnly = true)
    Map listPriceListsPage(String npubOwner, boolean activeOnly = true, int max = 25, int offset = 0,
                           String q = null, String listKind = null, Long customerId = null) {
        String npub = requireNpub(npubOwner)
        String term = q?.trim() ? "%${q.trim()}%" : null
        String kind = blankToNull(listKind)
        List items = PriceList.createCriteria().list(max: max, offset: offset, sort: 'name', order: 'asc') {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            if (kind) eq('listKind', kind)
            if (customerId != null) eq('customerId', customerId)
            if (term) {
                or {
                    ilike('name', term)
                    ilike('code', term)
                    ilike('description', term)
                }
            }
        } as List
        Number total = PriceList.createCriteria().count {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            if (kind) eq('listKind', kind)
            if (customerId != null) eq('customerId', customerId)
            if (term) {
                or {
                    ilike('name', term)
                    ilike('code', term)
                    ilike('description', term)
                }
            }
        } as Number
        [items: items, total: total?.intValue() ?: 0]
    }

    /**
     * Resolve unit price for a catalogue item (and optional customer/qty).
     * Prefers active CUSTOMER list for the customer, else STANDARD/VOLUME lists in validity window.
     */
    @Transactional(readOnly = true)
    Map resolvePrice(String npubOwner, Long catalogueItemId, Long customerId = null,
                     BigDecimal quantity = BigDecimal.ONE, Date asOf = null) {
        String npub = requireNpub(npubOwner)
        if (!catalogueItemId) throw new IllegalArgumentException('catalogueItemId is required')
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ONE
        Date when = asOf ?: new Date()

        Map matched = null
        if (customerId) {
            List customerLists = PriceList.findAllByNpubOwnerAndActiveAndCustomerId(npub, Boolean.TRUE, customerId)
            matched = bestLineAmong(customerLists, catalogueItemId, qty, when)
        }
        if (!matched) {
            List general = PriceList.createCriteria().list {
                eq('npubOwner', npub)
                eq('active', Boolean.TRUE)
                inList('listKind', [PriceList.KIND_STANDARD, PriceList.KIND_VOLUME])
                isNull('customerId')
            } as List
            matched = bestLineAmong(general, catalogueItemId, qty, when)
        }

        if (!matched) {
            return [
                    found          : false,
                    catalogueItemId: catalogueItemId,
                    customerId     : customerId,
                    quantity       : qty
            ]
        }

        PriceListLine bestLine = matched.line as PriceListLine
        PriceList bestList = matched.list as PriceList

        BigDecimal disc = bestLine.discountPercent != null
                ? bestLine.discountPercent
                : (bestList.defaultDiscountPercent ?: BigDecimal.ZERO)
        BigDecimal unit = bestLine.unitPrice ?: BigDecimal.ZERO
        BigDecimal net = unit
        if (disc && disc > 0G) {
            net = unit * (BigDecimal.ONE - (disc / 100G))
        }
        net = net.setScale(8, RoundingMode.HALF_UP)

        return [
                found            : true,
                catalogueItemId  : catalogueItemId,
                customerId       : customerId,
                quantity         : qty,
                priceListId      : bestList.id,
                priceListCode    : bestList.code,
                priceListName    : bestList.name,
                lineId           : bestLine.id,
                lineNo           : bestLine.lineNo,
                unitPrice        : unit,
                discountPercent  : disc,
                netUnitPrice     : net,
                currencyCode     : bestLine.currencyCode ?: bestList.currencyCode,
                minQuantity      : bestLine.minQuantity
        ]
    }

    Map toMap(PriceList pl, boolean includeLines = false) {
        if (!pl) return null
        Map m = [
                id                     : pl.id,
                name                   : pl.name,
                description            : pl.description,
                code                   : pl.code,
                listKind               : pl.listKind,
                currencyCode           : pl.currencyCode,
                active                 : pl.active,
                validFrom              : pl.validFrom?.time,
                validTo                : pl.validTo?.time,
                customerId             : pl.customerId,
                defaultDiscountPercent : pl.defaultDiscountPercent,
                status                 : pl.status,
                npubOwner              : pl.npubOwner,
                lineCount              : PriceListLine.countByPriceList(pl)
        ]
        if (includeLines) {
            m.lines = PriceListLine.findAllByPriceList(pl, [sort: 'lineNo', order: 'asc']).collect { toLineMap(it) }
        }
        m
    }

    Map toLineMap(PriceListLine line) {
        if (!line) return null
        [
                id              : line.id,
                lineNo          : line.lineNo,
                name            : line.name,
                description     : line.description,
                catalogueItemId : line.catalogueItemId,
                itemCode        : line.itemCode,
                minQuantity     : line.minQuantity,
                unitPrice       : line.unitPrice,
                discountPercent : line.discountPercent,
                currencyCode    : line.currencyCode,
                priceListId     : line.priceList?.id
        ]
    }

    private void replaceLines(PriceList pl, String npub, List lines) {
        PriceListLine.findAllByPriceList(pl).each { it.delete(flush: false) }
        int i = 1
        for (Object raw : lines) {
            if (!(raw instanceof Map)) continue
            Map row = raw as Map
            String name = row.name?.toString()?.trim()
            if (!name && row.catalogueItemId) {
                name = "Item ${row.catalogueItemId}"
            }
            if (!name) throw new IllegalArgumentException("lines[${i - 1}].name is required")
            BigDecimal unitPrice = parseDecimal(row.unitPrice)
            if (unitPrice == null) throw new IllegalArgumentException("lines[${i - 1}].unitPrice is required")

            PriceListLine line = new PriceListLine(
                    npubOwner       : npub,
                    priceList       : pl,
                    lineNo          : row.lineNo != null ? (row.lineNo as Integer) : i,
                    name            : name,
                    description     : row.description as String,
                    catalogueItemId : parseLong(row.catalogueItemId),
                    itemCode        : blankToNull(row.itemCode),
                    minQuantity     : parseDecimal(row.minQuantity) ?: BigDecimal.ONE,
                    unitPrice       : unitPrice,
                    discountPercent : parseDecimal(row.discountPercent),
                    currencyCode    : blankToNull(row.currencyCode) ?: pl.currencyCode,
                    status          : 'ACTIVE'
            )
            line.save(failOnError: true, flush: false)
            i++
        }
        pl.save(failOnError: true, flush: true)
    }

    private void emitUpdated(PriceList pl, String action) {
        try {
            domainEventBusService?.publish(
                    DomainEventNames.SALES_PRICELIST_UPDATED,
                    'sales',
                    pl.npubOwner,
                    [priceListId: pl.id, code: pl.code, listKind: pl.listKind, action: action]
            )
        } catch (Exception e) {
            log.warn("sales.pricelist.updated publish failed: ${e.message}")
        }
    }

    private static Map bestLineAmong(List lists, Long catalogueItemId, BigDecimal qty, Date when) {
        PriceListLine bestLine = null
        PriceList bestList = null
        BigDecimal bestMinQty = null
        for (Object raw : lists) {
            PriceList pl = raw as PriceList
            if (!isValidOn(pl, when)) continue
            List<PriceListLine> lines = PriceListLine.findAllByPriceListAndCatalogueItemId(pl, catalogueItemId)
            for (PriceListLine line : lines) {
                if (line.minQuantity != null && line.minQuantity > qty) continue
                if (bestLine == null || (line.minQuantity ?: 0G) > (bestMinQty ?: -1G)) {
                    bestLine = line
                    bestList = pl
                    bestMinQty = line.minQuantity ?: BigDecimal.ZERO
                }
            }
        }
        bestLine ? [line: bestLine, list: bestList] : null
    }

    private static boolean isValidOn(PriceList pl, Date when) {
        if (pl.validFrom && when.before(pl.validFrom)) return false
        if (pl.validTo && when.after(pl.validTo)) return false
        true
    }

    private PriceList load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        PriceList pl = PriceList.get(id)
        if (!pl || pl.npubOwner != npub) throw new IllegalArgumentException("price list not found: ${id}")
        pl
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }

    private static String blankToNull(Object v) {
        if (v == null) return null
        String s = v.toString().trim()
        s ? s : null
    }

    private static Long parseLong(Object v) {
        if (v == null || v.toString().trim().isEmpty()) return null
        if (v instanceof Number) return (v as Number).longValue()
        try {
            return Long.parseLong(v.toString().trim())
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid id: ${v}")
        }
    }

    private static BigDecimal parseDecimal(Object v) {
        if (v == null || v.toString().trim().isEmpty()) return null
        if (v instanceof BigDecimal) return v as BigDecimal
        if (v instanceof Number) return new BigDecimal(v.toString())
        try {
            return new BigDecimal(v.toString().trim())
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid decimal: ${v}")
        }
    }

    private static Date parseDate(Object v) {
        if (v == null || v.toString().trim().isEmpty()) return null
        if (v instanceof Date) return v as Date
        if (v instanceof Number) return new Date((v as Number).longValue())
        String s = v.toString().trim()
        if (s.isLong()) return new Date(Long.parseLong(s))
        // ISO-8601 date yyyy-MM-dd
        try {
            return Date.parse('yyyy-MM-dd', s)
        } catch (Exception ignored) {
            throw new IllegalArgumentException("invalid date: ${v}")
        }
    }
}
