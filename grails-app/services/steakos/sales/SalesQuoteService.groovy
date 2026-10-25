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

/**
 * Customer quotes (S-S2). SS1 lines; SS2 accept → create order.
 */
@Slf4j
class SalesQuoteService {

    SalesCustomerService salesCustomerService
    SalesOrderService salesOrderService
    steakos.catalogue.CatalogueService catalogueService
    steakos.configuration.CompanyCurrencyService companyCurrencyService
    steakos.configuration.CompanyTaxService companyTaxService
    steakos.configuration.PartnerLocaleService partnerLocaleService

    @Transactional
    CustomerQuote createQuote(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Customer customer = resolveCustomer(npub, fields)
        String number = fields.quoteNumber?.toString()?.trim() ?: nextQuoteNumber(npub)
        String ccy = (fields.currencyCode ?: customer?.currencyCode ?: 'EUR') as String
        companyCurrencyService?.validateWritableCode(npub, ccy)

        CustomerQuote q = new CustomerQuote(
                npubOwner   : npub,
                name        : (fields.name ?: "Quote ${number}") as String,
                description : fields.description as String,
                quoteNumber : number,
                customer    : customer,
                customerName: customer?.name ?: fields.customerName as String,
                issueDate   : parseDate(fields.issueDate) ?: new Date(),
                validUntil  : parseDate(fields.validUntil),
                currencyCode: ccy,
                quoteStatus : 'DRAFT',
                status      : 'DRAFT',
                amountEur   : BigDecimal.ZERO
        )
        q.save(failOnError: true, flush: true)

        List lineMaps = (fields.lines instanceof List) ? (List) fields.lines : []
        if (lineMaps) {
            replaceLines(q, lineMaps)
        } else if (fields.amountEur != null) {
            // convenience: single synthetic line from header amount
            BigDecimal amt = toBd(fields.amountEur)
            if (amt && amt > BigDecimal.ZERO) {
                replaceLines(q, [[
                        name        : fields.lineDescription ?: 'Line item',
                        quantity    : 1,
                        unitPriceEur: amt,
                        unitPriceSats: fields.amountSats
                ]])
            }
        }
        recomputeTotals(q)
        q.save(failOnError: true, flush: true)
        return q
    }

    @Transactional
    CustomerQuote setLines(Long quoteId, String npubOwner, List lineMaps) {
        CustomerQuote q = load(quoteId, npubOwner)
        assertEditable(q)
        replaceLines(q, lineMaps ?: [])
        recomputeTotals(q)
        q.save(failOnError: true, flush: true)
        return q
    }

    @Transactional
    CustomerQuote sendQuote(Long quoteId, String npubOwner) {
        CustomerQuote q = load(quoteId, npubOwner)
        if (q.quoteStatus != 'DRAFT' && q.quoteStatus != 'SENT') {
            throw new IllegalStateException("cannot send quote in status ${q.quoteStatus}")
        }
        if ((q.amountEur ?: BigDecimal.ZERO) <= BigDecimal.ZERO && QuoteLine.countByQuote(q) == 0) {
            throw new IllegalStateException('quote has no lines / amount')
        }
        q.quoteStatus = 'SENT'
        q.status = 'SENT'
        if (!q.issueDate) q.issueDate = new Date()
        partnerLocaleService?.snapshotQuote(q)
        q.save(failOnError: true, flush: true)
        return q
    }

    /**
     * SS2: ACCEPTED + auto-create DRAFT order from quote lines.
     */
    @Transactional
    Map acceptQuote(Long quoteId, String npubOwner) {
        CustomerQuote q = load(quoteId, npubOwner)
        if (q.quoteStatus == 'ACCEPTED' && q.resultingOrderId) {
            return [quote: toMap(q, true), orderId: q.resultingOrderId, alreadyAccepted: true]
        }
        if (!(q.quoteStatus in ['DRAFT', 'SENT'])) {
            throw new IllegalStateException("cannot accept quote in status ${q.quoteStatus}")
        }
        q.quoteStatus = 'ACCEPTED'
        q.status = 'ACCEPTED'
        q.save(failOnError: true, flush: true)

        SalesOrder order = salesOrderService.createFromQuote(q.id, npubOwner)
        q.resultingOrderId = order.id
        q.save(failOnError: true, flush: true)
        log.info('Accepted quote {} → order {}', q.quoteNumber, order.orderNumber)
        return [quote: toMap(q, true), order: salesOrderService.toMap(order, true), alreadyAccepted: false]
    }

    @Transactional
    CustomerQuote rejectQuote(Long quoteId, String npubOwner) {
        CustomerQuote q = load(quoteId, npubOwner)
        q.quoteStatus = 'REJECTED'
        q.status = 'REJECTED'
        q.save(failOnError: true, flush: true)
        return q
    }

    @Transactional(readOnly = true)
    List<CustomerQuote> listQuotes(String npubOwner, String status = null) {
        listQuotesPage(npubOwner, status, 200, 0).items as List<CustomerQuote>
    }

    static final List QUOTE_SORT_FIELDS = [
            'id', 'name', 'code', 'status', 'quoteStatus', 'quoteNumber',
            'amountEur', 'customerName', 'dateCreated', 'lastUpdated'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listQuotesPage(String npubOwner, String status = null, int max = 25, int offset = 0,
                       Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', QUOTE_SORT_FIELDS)
        String term = steakos.api.ListPaging.likeContains(listOpts?.q as String)
        List items = CustomerQuote.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (status) eq('quoteStatus', status)
            if (term) {
                or {
                    ilike('name', term)
                    ilike('quoteNumber', term)
                    ilike('customerName', term)
                    ilike('description', term)
                }
            }
            order(so.sort as String, so.order as String)
            if (so.sort != 'id') order('id', 'asc')
        } as List
        Number total = CustomerQuote.createCriteria().count {
            eq('npubOwner', npub)
            if (status) eq('quoteStatus', status)
            if (term) {
                or {
                    ilike('name', term)
                    ilike('quoteNumber', term)
                    ilike('customerName', term)
                    ilike('description', term)
                }
            }
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    CustomerQuote getQuote(Long id, String npubOwner) {
        load(id, npubOwner)
    }

    Map toMap(CustomerQuote q, boolean includeLines = false) {
        if (!q) return null
        Map m = [
                id              : q.id,
                name            : q.name,
                description     : q.description,
                quoteNumber     : q.quoteNumber,
                customerId      : q.customer?.id,
                customerName    : q.customerName,
                issueDate       : q.issueDate,
                validUntil      : q.validUntil,
                amountEur       : q.amountEur,
                netAmountEur    : q.netAmountEur,
                taxAmountEur    : q.taxAmountEur,
                amountSats      : q.amountSats,
                currencyCode    : q.currencyCode,
                documentLocale  : q.documentLocale,
                quoteStatus     : q.quoteStatus,
                resultingOrderId: q.resultingOrderId,
                npubOwner       : q.npubOwner
        ]
        if (includeLines) {
            m.lines = QuoteLine.findAllByQuote(q, [sort: 'lineNo']).collect { lineToMap(it) }
        }
        m.warnings = buildWarnings(q, includeLines ? (m.lines as List) : null)
        List linesForTax = includeLines ? (m.lines as List) : QuoteLine.findAllByQuote(q, [sort: 'lineNo']).collect { lineToMap(it) }
        m.taxNotes = steakos.configuration.CompanyTaxService.legalPhrasesForLines(linesForTax)
        return m
    }

    List<Map> buildWarnings(CustomerQuote q, List lines = null) {
        if (!q || companyCurrencyService == null) return []
        List lineItems = []
        List src = lines
        if (src == null) {
            src = QuoteLine.findAllByQuote(q, [sort: 'lineNo']).collect { lineToMap(it) }
        }
        src.each { Object raw ->
            Map lm = raw as Map
            Map resolved = catalogueService != null ? catalogueService.resolveForLine(q.npubOwner, lm) : [:]
            if (resolved.currency) {
                lineItems << [name: (lm.name ?: resolved.name), currency: resolved.currency]
            }
        }
        companyCurrencyService.collectDocumentWarnings(
                q.npubOwner, q.currencyCode, q.customer?.currencyCode, 'Customer', lineItems)
    }

    Map lineToMap(QuoteLine l) {
        [
                id           : l.id,
                lineNo       : l.lineNo,
                name         : l.name,
                description  : l.description,
                quantity     : l.quantity,
                unitPriceEur : l.unitPriceEur,
                lineTotalEur : l.lineTotalEur,
                unitPriceSats   : l.unitPriceSats,
                lineTotalSats   : l.lineTotalSats,
                productRefId    : l.productRefId,
                productRefType  : l.productRefType,
                catalogueItemId : l.catalogueItemId,
                taxCode         : l.taxCode,
                taxRatePercent  : l.taxRatePercent,
                taxAmountEur    : l.taxAmountEur,
                reverseCharge   : l.reverseCharge == Boolean.TRUE,
                grossAmountEur  : (l.lineTotalEur ?: BigDecimal.ZERO).add(l.taxAmountEur ?: BigDecimal.ZERO)
        ]
    }

    private void replaceLines(CustomerQuote q, List lineMaps) {
        QuoteLine.findAllByQuote(q).each { it.delete(flush: true) }
        int n = 1
        lineMaps.each { Object raw ->
            Map lm = raw as Map
            Map resolved = catalogueService != null ? catalogueService.resolveForLine(q.npubOwner, lm) : [:]
            BigDecimal qty = toBd(lm.quantity) ?: BigDecimal.ONE
            BigDecimal unit = toBd(lm.unitPriceEur) ?: (resolved.listPrice as BigDecimal) ?: BigDecimal.ZERO
            BigDecimal total = toBd(lm.lineTotalEur)
            if (total == null) {
                total = qty.multiply(unit)
            }
            Map tax = resolveTax(q.npubOwner, total, lm.taxCode as String, resolved.defaultTaxCode as String)
            // PDR-023: primary-only lines — no sats on commercial docs
            new QuoteLine(
                    npubOwner       : q.npubOwner,
                    quote           : q,
                    lineNo          : n++,
                    name            : (lm.name ?: resolved.name ?: lm.description ?: "Line ${n - 1}") as String,
                    description     : lm.description as String,
                    quantity        : qty,
                    unitPriceEur    : unit,
                    lineTotalEur    : total,
                    unitPriceSats   : null,
                    lineTotalSats   : null,
                    productRefId    : lm.productRefId != null && lm.productRefId != '' ? lm.productRefId as Long : null,
                    productRefType  : lm.productRefType as String,
                    catalogueItemId : resolved.catalogueItemId as Long,
                    taxCode         : tax.taxCode as String,
                    taxRatePercent  : tax.ratePercent as BigDecimal,
                    taxAmountEur    : tax.taxAmountEur as BigDecimal,
                    reverseCharge   : tax.reverseCharge as Boolean
            ).save(failOnError: true, flush: true)
        }
    }

    private Map resolveTax(String npub, BigDecimal net, String taxCode, String catalogueDefault) {
        if (companyTaxService == null) {
            return [taxCode: taxCode, ratePercent: null, taxAmountEur: BigDecimal.ZERO,
                    reverseCharge: false, intraCommunity: false]
        }
        companyTaxService.resolveLineTax(npub, net, taxCode, catalogueDefault)
    }

    private void recomputeTotals(CustomerQuote q) {
        List<QuoteLine> lines = QuoteLine.findAllByQuote(q)
        BigDecimal net = BigDecimal.ZERO
        BigDecimal tax = BigDecimal.ZERO
        lines.each { QuoteLine l ->
            net = net.add(l.lineTotalEur ?: BigDecimal.ZERO)
            tax = tax.add(l.taxAmountEur ?: BigDecimal.ZERO)
        }
        q.netAmountEur = net
        q.taxAmountEur = tax
        q.amountEur = net.add(tax)
        q.amountSats = null
    }

    private void assertEditable(CustomerQuote q) {
        if (!(q.quoteStatus in ['DRAFT', 'SENT'])) {
            throw new IllegalStateException("quote is ${q.quoteStatus}; only DRAFT/SENT can edit lines")
        }
    }

    private Customer resolveCustomer(String npub, Map fields) {
        if (fields.customerId) {
            return salesCustomerService.getCustomer(fields.customerId as Long, npub)
        }
        null
    }

    private CustomerQuote load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        CustomerQuote q = CustomerQuote.get(id)
        if (!q || q.npubOwner != npub) throw new IllegalArgumentException("quote not found: ${id}")
        q
    }

    private String nextQuoteNumber(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String prefix = "Q-${period}-"
        int seq = CustomerQuote.countByNpubOwner(npub) + 1
        String candidate = "${prefix}${String.format('%04d', seq)}"
        while (CustomerQuote.findByNpubOwnerAndQuoteNumber(npub, candidate)) {
            seq++
            candidate = "${prefix}${String.format('%04d', seq)}"
        }
        return candidate
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

    private static Date parseDate(Object raw) {
        if (raw == null || raw == '') return null
        if (raw instanceof Date) return raw
        String s = raw.toString().trim()
        if (s.length() >= 10) {
            try { return Date.parse('yyyy-MM-dd', s.substring(0, 10)) } catch (ignored) {}
        }
        null
    }
}
