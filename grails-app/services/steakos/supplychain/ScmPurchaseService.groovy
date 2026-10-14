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

package steakos.supplychain

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.finance.FinancePayableService
import steakos.finance.Payable
import steakos.procurement.PurchaseOrder
import steakos.procurement.PurchaseOrderLine
import steakos.procurement.Receipt
import steakos.procurement.ReceiptLine
import steakos.procurement.Vendor

/**
 * PO + receive (SC). SC7: confirm → Finance payable. Receipt → stock only.
 */
@Slf4j
class ScmPurchaseService {

    ScmVendorService scmVendorService
    ScmInventoryService scmInventoryService
    FinancePayableService financePayableService
    steakos.catalogue.CatalogueService catalogueService
    steakos.configuration.CompanyCurrencyService companyCurrencyService
    steakos.configuration.PartnerLocaleService partnerLocaleService
    steakos.configuration.CompanyTaxService companyTaxService

    @Transactional
    PurchaseOrder createPurchaseOrder(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Vendor vendor = resolveVendor(npub, fields)
        String number = fields.poNumber?.toString()?.trim() ?: nextPoNumber(npub)
        String ccy = (fields.currencyCode ?: vendor?.currencyCode ?: 'EUR') as String
        companyCurrencyService?.validateWritableCode(npub, ccy)
        PurchaseOrder po = new PurchaseOrder(
                npubOwner   : npub,
                name        : (fields.name ?: "PO ${number}") as String,
                description : fields.description as String,
                poNumber    : number,
                vendor      : vendor,
                vendorName  : vendor?.name ?: fields.vendorName as String,
                orderDate   : parseDate(fields.orderDate) ?: new Date(),
                currencyCode: ccy,
                poStatus    : 'DRAFT',
                status      : 'DRAFT',
                amountEur   : BigDecimal.ZERO
        )
        po.save(failOnError: true, flush: true)
        List lineMaps = (fields.lines instanceof List) ? (List) fields.lines : []
        if (lineMaps) replaceLines(po, lineMaps)
        recomputeTotals(po)
        po.save(failOnError: true, flush: true)
        return po
    }

    @Transactional
    PurchaseOrder setLines(Long poId, String npubOwner, List lineMaps) {
        PurchaseOrder po = load(poId, npubOwner)
        assertEditable(po)
        replaceLines(po, lineMaps ?: [])
        recomputeTotals(po)
        po.save(failOnError: true, flush: true)
        return po
    }

    @Transactional
    PurchaseOrder sendPurchaseOrder(Long poId, String npubOwner) {
        PurchaseOrder po = load(poId, npubOwner)
        if (!(po.poStatus in ['DRAFT', 'SENT'])) {
            throw new IllegalStateException("cannot send PO in status ${po.poStatus}")
        }
        if ((po.amountEur ?: BigDecimal.ZERO) <= BigDecimal.ZERO) {
            throw new IllegalStateException('PO has no amount / lines')
        }
        po.poStatus = 'SENT'
        po.status = 'SENT'
        partnerLocaleService?.snapshotPurchaseOrder(po)
        po.save(failOnError: true, flush: true)
        return po
    }

    /**
     * SC7: confirm → Finance payable DRAFT/OPEN.
     */
    @Transactional
    Map confirmPurchaseOrder(Long poId, String npubOwner) {
        PurchaseOrder po = load(poId, npubOwner)
        if (po.poStatus in ['CONFIRMED', 'PART_RECEIVED', 'RECEIVED']) {
            return [purchaseOrder: toMap(po, true), financePayableId: po.financePayableId, alreadyConfirmed: true]
        }
        if (po.poStatus == 'CANCELLED') {
            throw new IllegalStateException('cannot confirm a cancelled PO')
        }
        if ((po.amountEur ?: BigDecimal.ZERO) <= BigDecimal.ZERO) {
            throw new IllegalStateException('PO total must be > 0')
        }

        po.poStatus = 'CONFIRMED'
        po.status = 'CONFIRMED'
        po.confirmedAt = new Date()
        if (!po.documentLocale) {
            partnerLocaleService?.snapshotPurchaseOrder(po)
        }
        po.save(failOnError: true, flush: true)

        Payable pay = null
        if (financePayableService != null) {
            String taxNote = (po.taxAmountEur != null && po.taxAmountEur > BigDecimal.ZERO)
                    ? " (net ${po.netAmountEur}; input VAT ${po.taxAmountEur}; gross ${po.amountEur})"
                    : ''
            pay = financePayableService.createPayable(po.npubOwner, [
                    vendorId   : po.vendor?.id,
                    vendorName : po.vendorName,
                    amountEur  : po.amountEur,
                    amountSats : po.amountSats,
                    currencyCode: po.currencyCode,
                    name       : "AP for ${po.poNumber}",
                    description: "Purchase order ${po.poNumber}${taxNote}"
            ])
            po.financePayableId = pay.id
            po.save(failOnError: true, flush: true)
        }
        log.info('Confirmed PO {} → payable {}', po.poNumber, pay?.id)
        return [
                purchaseOrder   : toMap(po, true),
                financePayableId: po.financePayableId,
                alreadyConfirmed: false
        ]
    }

    @Transactional
    Map receiveGoods(Long poId, String npubOwner, Map fields = [:]) {
        PurchaseOrder po = load(poId, npubOwner)
        if (!(po.poStatus in ['CONFIRMED', 'PART_RECEIVED', 'SENT'])) {
            // allow SENT for simplicity of SBM demo path
            if (po.poStatus == 'DRAFT') {
                throw new IllegalStateException('confirm PO before receiving')
            }
            if (po.poStatus == 'CANCELLED' || po.poStatus == 'RECEIVED') {
                throw new IllegalStateException("cannot receive PO in status ${po.poStatus}")
            }
        }
        if (po.poStatus == 'DRAFT') {
            throw new IllegalStateException('confirm PO before receiving')
        }

        List lineMaps = (fields.lines instanceof List) ? (List) fields.lines : null
        List<PurchaseOrderLine> poLines = PurchaseOrderLine.findAllByPurchaseOrder(po, [sort: 'lineNo'])
        if (!lineMaps) {
            // receive remaining qty on all lines
            lineMaps = poLines.collect { PurchaseOrderLine pl ->
                BigDecimal remaining = (pl.quantity ?: BigDecimal.ZERO).subtract(pl.quantityReceived ?: BigDecimal.ZERO)
                [purchaseOrderLineId: pl.id, quantityReceived: remaining, name: pl.name, productRefId: pl.productRefId]
            }.findAll { (it.quantityReceived as BigDecimal) > BigDecimal.ZERO }
        }
        if (!lineMaps) {
            throw new IllegalStateException('nothing to receive')
        }

        String rnum = nextReceiptNumber(npubOwner)
        Receipt receipt = new Receipt(
                npubOwner     : po.npubOwner,
                name          : "Receipt ${rnum}",
                receiptNumber : rnum,
                purchaseOrder : po,
                receivedDate  : new Date(),
                receiptStatus : 'POSTED',
                status        : 'POSTED'
        )
        receipt.save(failOnError: true, flush: true)

        int n = 1
        lineMaps.each { Object raw ->
            Map lm = raw as Map
            BigDecimal qty = toBd(lm.quantityReceived) ?: toBd(lm.quantity) ?: BigDecimal.ZERO
            if (qty <= BigDecimal.ZERO) return
            PurchaseOrderLine pol = null
            if (lm.purchaseOrderLineId) {
                pol = PurchaseOrderLine.get(lm.purchaseOrderLineId as Long)
            } else if (lm.lineNo) {
                pol = poLines.find { it.lineNo == (lm.lineNo as Integer) }
            }
            Long catId = lm.catalogueItemId != null && lm.catalogueItemId != '' ? lm.catalogueItemId as Long :
                    (lm.productRefId != null && lm.productRefId != '' ? lm.productRefId as Long :
                            (pol?.catalogueItemId ?: pol?.productRefId))
            new ReceiptLine(
                    npubOwner        : po.npubOwner,
                    receipt          : receipt,
                    lineNo           : n++,
                    name             : (lm.name ?: pol?.name ?: "Line ${n - 1}") as String,
                    purchaseOrderLine: pol,
                    quantityReceived : qty,
                    catalogueItemId  : catId
            ).save(failOnError: true, flush: true)

            if (pol) {
                pol.quantityReceived = (pol.quantityReceived ?: BigDecimal.ZERO).add(qty)
                pol.save(failOnError: true, flush: true)
            }

            // SC2: stock move (GOODS catalogue)
            if (catId && scmInventoryService != null) {
                try {
                    scmInventoryService.postMove(po.npubOwner, [
                            catalogueItemId: catId,
                            qtyDelta       : qty,
                            reason         : 'RECEIPT',
                            sourceType     : 'RECEIPT',
                            sourceId       : receipt.id,
                            name           : "Receipt ${rnum}"
                    ])
                } catch (Exception e) {
                    log.warn("receipt stock skip catalogue {}: {}", catId, e.message)
                }
            }
        }

        // update PO status from received qty
        boolean allDone = true
        boolean any = false
        PurchaseOrderLine.findAllByPurchaseOrder(po).each { PurchaseOrderLine pl ->
            BigDecimal rec = pl.quantityReceived ?: BigDecimal.ZERO
            BigDecimal ord = pl.quantity ?: BigDecimal.ZERO
            if (rec > BigDecimal.ZERO) any = true
            if (rec < ord) allDone = false
        }
        if (allDone && any) {
            po.poStatus = 'RECEIVED'
        } else if (any) {
            po.poStatus = 'PART_RECEIVED'
        }
        po.status = po.poStatus
        po.save(failOnError: true, flush: true)

        return [receipt: receiptToMap(receipt, true), purchaseOrder: toMap(po, true)]
    }

    @Transactional
    PurchaseOrder cancelPurchaseOrder(Long poId, String npubOwner) {
        PurchaseOrder po = load(poId, npubOwner)
        if (po.poStatus == 'CANCELLED') return po
        if (po.financePayableId) {
            throw new IllegalStateException('cancel via Finance void when payable exists (manual for now)')
        }
        if (po.poStatus in ['PART_RECEIVED', 'RECEIVED']) {
            throw new IllegalStateException('cannot cancel after receipt')
        }
        po.poStatus = 'CANCELLED'
        po.status = 'CANCELLED'
        po.save(failOnError: true, flush: true)
        return po
    }

    @Transactional(readOnly = true)
    List<PurchaseOrder> listPurchaseOrders(String npubOwner, String status = null) {
        listPurchaseOrdersPage(npubOwner, status, 200, 0).items as List<PurchaseOrder>
    }

    @Transactional(readOnly = true)
    Map listPurchaseOrdersPage(String npubOwner, String status = null, int max = 25, int offset = 0) {
        String npub = requireNpub(npubOwner)
        Map opts = [sort: 'id', order: 'desc', max: max, offset: offset]
        if (status) {
            return [
                    items: PurchaseOrder.findAllByNpubOwnerAndPoStatus(npub, status, opts),
                    total: PurchaseOrder.countByNpubOwnerAndPoStatus(npub, status) as int
            ]
        }
        return [
                items: PurchaseOrder.findAllByNpubOwner(npub, opts),
                total: PurchaseOrder.countByNpubOwner(npub) as int
        ]
    }

    @Transactional(readOnly = true)
    PurchaseOrder getPurchaseOrder(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(PurchaseOrder po, boolean includeLines = false) {
        if (!po) return null
        Map m = [
                id              : po.id,
                name            : po.name,
                description     : po.description,
                poNumber        : po.poNumber,
                vendorId        : po.vendor?.id,
                vendorName      : po.vendorName,
                orderDate       : po.orderDate,
                confirmedAt     : po.confirmedAt,
                amountEur       : po.amountEur,
                netAmountEur    : po.netAmountEur,
                taxAmountEur    : po.taxAmountEur,
                amountSats      : po.amountSats,
                currencyCode    : po.currencyCode,
                documentLocale  : po.documentLocale,
                poStatus        : po.poStatus,
                financePayableId: po.financePayableId,
                npubOwner       : po.npubOwner
        ]
        if (includeLines) {
            m.lines = PurchaseOrderLine.findAllByPurchaseOrder(po, [sort: 'lineNo']).collect { lineToMap(it) }
        }
        m.warnings = buildWarnings(po, includeLines ? (m.lines as List) : null)
        return m
    }

    List<Map> buildWarnings(PurchaseOrder po, List lines = null) {
        if (!po || companyCurrencyService == null) return []
        List lineItems = []
        List src = lines
        if (src == null) {
            src = PurchaseOrderLine.findAllByPurchaseOrder(po, [sort: 'lineNo']).collect { lineToMap(it) }
        }
        src.each { Object raw ->
            Map lm = raw as Map
            Map resolved = catalogueService != null ? catalogueService.resolveForLine(po.npubOwner, lm) : [:]
            if (resolved.currency) {
                lineItems << [name: (lm.name ?: resolved.name), currency: resolved.currency]
            }
        }
        companyCurrencyService.collectDocumentWarnings(
                po.npubOwner, po.currencyCode, po.vendor?.currencyCode, 'Vendor', lineItems)
    }

    Map lineToMap(PurchaseOrderLine l) {
        [
                id              : l.id,
                lineNo          : l.lineNo,
                name            : l.name,
                description     : l.description,
                quantity        : l.quantity,
                unitPriceEur    : l.unitPriceEur,
                lineTotalEur    : l.lineTotalEur,
                unitPriceSats   : l.unitPriceSats,
                lineTotalSats   : l.lineTotalSats,
                productRefId    : l.productRefId,
                productRefType  : l.productRefType,
                catalogueItemId : l.catalogueItemId,
                quantityReceived: l.quantityReceived,
                taxCode         : l.taxCode,
                taxRatePercent  : l.taxRatePercent,
                taxAmountEur    : l.taxAmountEur,
                grossAmountEur  : (l.lineTotalEur ?: BigDecimal.ZERO).add(l.taxAmountEur ?: BigDecimal.ZERO)
        ]
    }

    Map receiptToMap(Receipt r, boolean includeLines = false) {
        if (!r) return null
        Map m = [
                id             : r.id,
                name           : r.name,
                receiptNumber  : r.receiptNumber,
                purchaseOrderId: r.purchaseOrder?.id,
                receivedDate   : r.receivedDate,
                receiptStatus  : r.receiptStatus,
                npubOwner      : r.npubOwner
        ]
        if (includeLines) {
            m.lines = ReceiptLine.findAllByReceipt(r, [sort: 'lineNo']).collect {
                [
                        id                 : it.id,
                        lineNo             : it.lineNo,
                        name               : it.name,
                        quantityReceived   : it.quantityReceived,
                        catalogueItemId    : it.catalogueItemId,
                        purchaseOrderLineId: it.purchaseOrderLine?.id
                ]
            }
        }
        return m
    }

    private void replaceLines(PurchaseOrder po, List lineMaps) {
        PurchaseOrderLine.findAllByPurchaseOrder(po).each { it.delete(flush: true) }
        int n = 1
        lineMaps.each { Object raw ->
            Map lm = raw as Map
            Map resolved = catalogueService != null ? catalogueService.resolveForLine(po.npubOwner, lm) : [:]
            BigDecimal qty = toBd(lm.quantity) ?: BigDecimal.ONE
            BigDecimal unit = toBd(lm.unitPriceEur) ?: (resolved.listPrice as BigDecimal) ?: BigDecimal.ZERO
            BigDecimal total = toBd(lm.lineTotalEur)
            if (total == null) total = qty.multiply(unit)
            Map tax = resolveInputTax(po.npubOwner, total, lm.taxCode as String, resolved.defaultTaxCode as String)
            Long unitSats = null
            Long totalSats = null
            Long productRef = lm.productRefId != null && lm.productRefId != '' ? lm.productRefId as Long : null
            // productRefId on PO line stores catalogue id when nature GOODS (ops FK cutover)
            if (!productRef && resolved.catalogueItemId) {
                productRef = resolved.catalogueItemId as Long
            }
            new PurchaseOrderLine(
                    npubOwner       : po.npubOwner,
                    purchaseOrder   : po,
                    lineNo          : n++,
                    name            : (lm.name ?: resolved.name ?: lm.description ?: "Line ${n - 1}") as String,
                    description     : lm.description as String,
                    quantity        : qty,
                    unitPriceEur    : unit,
                    lineTotalEur    : total,
                    unitPriceSats   : unitSats,
                    lineTotalSats   : totalSats,
                    productRefId    : productRef,
                    productRefType  : lm.productRefType as String ?: (productRef ? 'PRODUCT' : null),
                    catalogueItemId : resolved.catalogueItemId as Long,
                    quantityReceived: BigDecimal.ZERO,
                    taxCode         : tax.taxCode as String,
                    taxRatePercent  : tax.ratePercent as BigDecimal,
                    taxAmountEur    : tax.taxAmountEur as BigDecimal
            ).save(failOnError: true, flush: true)
        }
    }

    /**
     * Input VAT for AP honesty (L14). Uses system catalog rates even when the company
     * regime suppresses *output* VAT (Kleinunternehmer / NONE).
     */
    private Map resolveInputTax(String npub, BigDecimal net, String taxCode, String catalogueDefault) {
        String code = steakos.configuration.CompanyTaxService.normalizeCode(taxCode)
        if (!code) {
            code = steakos.configuration.CompanyTaxService.normalizeCode(catalogueDefault)
        }
        if (!code && companyTaxService != null) {
            // Prefer stored default; fall back to DE/AT pack default from LE country regime.
            String regime = companyTaxService.taxRegime(npub)
            if (steakos.configuration.CompanyTaxService.suppressesOutputVat(regime)) {
                code = steakos.configuration.CompanyTaxService.defaultCodeForRegime(
                        steakos.configuration.CompanyTaxService.REGIME_DE_VAT)
            } else {
                code = companyTaxService.defaultTaxCode(npub)
            }
        }
        if (!code) {
            return [taxCode: null, ratePercent: BigDecimal.ZERO, taxAmountEur: BigDecimal.ZERO]
        }
        steakos.finance.TaxCode cat = steakos.finance.TaxCode.findByCode(code)
        if (!cat || cat.enabled == Boolean.FALSE) {
            return [taxCode: null, ratePercent: BigDecimal.ZERO, taxAmountEur: BigDecimal.ZERO]
        }
        BigDecimal taxAmt = steakos.configuration.CompanyTaxService.computeTaxFromNet(
                net ?: BigDecimal.ZERO, cat.ratePercent)
        return [
                taxCode      : code,
                ratePercent  : cat.ratePercent,
                taxAmountEur : taxAmt
        ]
    }

    private void recomputeTotals(PurchaseOrder po) {
        List<PurchaseOrderLine> lines = PurchaseOrderLine.findAllByPurchaseOrder(po)
        BigDecimal net = BigDecimal.ZERO
        BigDecimal tax = BigDecimal.ZERO
        lines.each { PurchaseOrderLine l ->
            net = net.add(l.lineTotalEur ?: BigDecimal.ZERO)
            tax = tax.add(l.taxAmountEur ?: BigDecimal.ZERO)
        }
        po.netAmountEur = net
        po.taxAmountEur = tax
        po.amountEur = net.add(tax)
        po.amountSats = null
    }

    private void assertEditable(PurchaseOrder po) {
        if (!(po.poStatus in ['DRAFT', 'SENT'])) {
            throw new IllegalStateException("PO is ${po.poStatus}; only DRAFT/SENT can edit lines")
        }
    }

    private Vendor resolveVendor(String npub, Map fields) {
        if (fields.vendorId) {
            return scmVendorService.getVendor(fields.vendorId as Long, npub)
        }
        null
    }

    private PurchaseOrder load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        PurchaseOrder po = PurchaseOrder.get(id)
        if (!po || po.npubOwner != npub) throw new IllegalArgumentException("PO not found: ${id}")
        po
    }

    private String nextPoNumber(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String prefix = "PO-${period}-"
        int seq = PurchaseOrder.countByNpubOwner(npub) + 1
        String candidate = "${prefix}${String.format('%04d', seq)}"
        while (PurchaseOrder.findByNpubOwnerAndPoNumber(npub, candidate)) {
            seq++
            candidate = "${prefix}${String.format('%04d', seq)}"
        }
        return candidate
    }

    private String nextReceiptNumber(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String prefix = "RCV-${period}-"
        int seq = Receipt.countByNpubOwner(npub) + 1
        String candidate = "${prefix}${String.format('%04d', seq)}"
        while (Receipt.findByNpubOwnerAndReceiptNumber(npub, candidate)) {
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
