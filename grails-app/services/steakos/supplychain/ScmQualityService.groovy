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
import steakos.catalogue.CatalogueItem
import steakos.catalogue.CatalogueService
import steakos.production.Check
import steakos.production.Lot
import steakos.supplychain.StockMove

@Slf4j
class ScmQualityService {

    CatalogueService catalogueService
    ScmInventoryService scmInventoryService

    @Transactional
    Lot createLot(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Object cid = CatalogueService.resolveId(fields)
        if (!cid) throw new IllegalArgumentException('catalogueItemId is required')
        CatalogueItem item = catalogueService.requireGoods(npub, cid)
        String code = fields.lotCode?.toString()?.trim() ?: nextLotCode(npub)
        String status = (fields.lotStatus ?: 'OPEN') as String
        Lot lot = new Lot(
                npubOwner              : npub,
                name                   : (fields.name ?: "Lot ${code}") as String,
                description            : fields.description as String,
                lotCode                : code,
                catalogueItem          : item,
                quantity               : toBd(fields.quantity) ?: BigDecimal.ZERO,
                manufacturedOn         : fields.manufacturedOn instanceof Date ? fields.manufacturedOn as Date : new Date(),
                lotStatus              : status,
                sourceProductionOrderId: fields.sourceProductionOrderId != null && fields.sourceProductionOrderId != ''
                        ? fields.sourceProductionOrderId as Long : null,
                status                 : status
        )
        lot.save(failOnError: true, flush: true)
        return lot
    }

    /**
     * Record QA on a lot. PASS releases production lots into stock (once).
     * Manual lots (no production source) only update lot status — stock already on hand.
     */
    @Transactional
    Check recordCheck(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        if (!fields.lotId) throw new IllegalArgumentException('lotId is required')
        Lot lot = loadLot(fields.lotId as Long, npub)
        if (lot.lotStatus in ['RELEASED', 'REJECTED']) {
            throw new IllegalStateException("lot ${lot.lotCode} already ${lot.lotStatus}")
        }
        String result = (fields.checkResult ?: fields.result ?: 'PASS') as String
        if (!(result in ['PASS', 'FAIL', 'HOLD'])) {
            throw new IllegalArgumentException('checkResult must be PASS, FAIL, or HOLD')
        }
        Check c = new Check(
                npubOwner  : npub,
                name       : (fields.name ?: "Check ${lot.lotCode}") as String,
                description: fields.description as String,
                lot        : lot,
                checkedOn  : new Date(),
                checkResult: result,
                notes      : fields.notes as String,
                status     : result
        )
        c.save(failOnError: true, flush: true)

        if (result == 'PASS') {
            lot.lotStatus = 'RELEASED'
        } else if (result == 'FAIL') {
            lot.lotStatus = 'REJECTED'
        } else if (result == 'HOLD') {
            lot.lotStatus = 'QUARANTINE'
        }
        lot.status = lot.lotStatus
        lot.save(failOnError: true, flush: true)

        if (result == 'PASS') {
            releaseProductionLotToStock(lot)
        }
        return c
    }

    /**
     * Post PRODUCTION stock once when a production-sourced lot is released.
     */
    private void releaseProductionLotToStock(Lot lot) {
        if (!lot?.sourceProductionOrderId || scmInventoryService == null) {
            return
        }
        String npub = lot.npubOwner
        String sourceId = lot.sourceProductionOrderId.toString()
        List existing = StockMove.findAllByNpubOwnerAndSourceTypeAndSourceId(
                npub, 'PRODUCTION_ORDER', sourceId, [max: 1]
        )
        if (existing) {
            log.info('Stock already posted for production order {} — skip on lot {}', sourceId, lot.lotCode)
            return
        }
        CatalogueItem item = lot.catalogueItem
        if (!item) return
        BigDecimal qty = lot.quantity ?: BigDecimal.ZERO
        if (qty <= BigDecimal.ZERO) {
            throw new IllegalStateException("lot ${lot.lotCode} quantity must be > 0 to release to stock")
        }
        scmInventoryService.postMove(npub, [
                catalogueItemId: item.id,
                qtyDelta       : qty,
                reason         : 'PRODUCTION',
                sourceType     : 'PRODUCTION_ORDER',
                sourceId       : lot.sourceProductionOrderId,
                lotId          : lot.id,
                name           : "QA release ${lot.lotCode}"
        ])
        log.info('Released lot {} to stock qty={}', lot.lotCode, qty)
    }

    @Transactional(readOnly = true)
    List<Lot> listLots(String npubOwner) {
        listLotsPage(npubOwner, 200, 0).items as List<Lot>
    }

    static final List LOT_SORT_FIELDS = [
            'id', 'name', 'lotCode', 'lotStatus', 'status', 'quantity', 'manufacturedOn'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listLotsPage(String npubOwner, int max = 25, int offset = 0, Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', LOT_SORT_FIELDS)
        List items = Lot.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Lot.createCriteria().count {
            eq('npubOwner', npub)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    @Transactional(readOnly = true)
    Lot getLot(Long id, String npubOwner) { loadLot(id, npubOwner) }

    Map lotToMap(Lot l) {
        if (!l) return null
        [
                id                     : l.id,
                name                   : l.name,
                lotCode                : l.lotCode,
                productId              : l.catalogueItem?.id,
                catalogueItemId        : l.catalogueItem?.id,
                productName            : l.catalogueItem?.name,
                quantity               : l.quantity,
                manufacturedOn         : l.manufacturedOn,
                lotStatus              : l.lotStatus,
                sourceProductionOrderId: l.sourceProductionOrderId,
                npubOwner              : l.npubOwner
        ]
    }

    Map checkToMap(Check c) {
        if (!c) return null
        [
                id         : c.id,
                name       : c.name,
                lotId      : c.lot?.id,
                lotCode    : c.lot?.lotCode,
                checkedOn  : c.checkedOn,
                checkResult: c.checkResult,
                notes      : c.notes,
                npubOwner  : c.npubOwner
        ]
    }

    private Lot loadLot(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Lot l = Lot.get(id)
        if (!l || l.npubOwner != npub) throw new IllegalArgumentException("lot not found: ${id}")
        l
    }

    private String nextLotCode(String npub) {
        String period = new java.text.SimpleDateFormat('yyyyMM').format(new Date())
        String prefix = "LOT-${period}-"
        int seq = Lot.countByNpubOwner(npub) + 1
        String candidate = "${prefix}${String.format('%04d', seq)}"
        while (Lot.findByNpubOwnerAndLotCode(npub, candidate)) {
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
}
