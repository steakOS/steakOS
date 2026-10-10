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

/**
 * Stock ledger (SC2). Key = catalogueItemId (GOODS only). onHand = sum(qtyDelta).
 */
@Slf4j
class ScmInventoryService {

    CatalogueService catalogueService

    @Transactional
    StockMove postMove(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Object cid = CatalogueService.resolveId(fields)
        if (!cid) throw new IllegalArgumentException('catalogueItemId is required')
        CatalogueItem item = catalogueService.requireGoods(npub, cid)
        BigDecimal qty = toBd(fields.qtyDelta)
        if (qty == null || qty == BigDecimal.ZERO) {
            throw new IllegalArgumentException('qtyDelta must be non-zero')
        }
        String reason = (fields.reason ?: 'ADJUST') as String
        StockMove m = new StockMove(
                npubOwner         : npub,
                name              : (fields.name ?: "${reason} ${item.name}") as String,
                description       : fields.description as String,
                catalogueItemId   : item.id,
                qtyDelta          : qty,
                reason            : reason,
                sourceType        : fields.sourceType as String,
                sourceId          : fields.sourceId?.toString(),
                lotId             : fields.lotId != null && fields.lotId != '' ? fields.lotId as Long : null,
                movedAt           : fields.movedAt instanceof Date ? fields.movedAt as Date : new Date(),
                status            : 'POSTED'
        )
        m.save(failOnError: true, flush: true)
        log.info('StockMove {} catalogueItem={} qty={}', reason, item.id, qty)
        return m
    }

    @Transactional(readOnly = true)
    BigDecimal onHand(String npubOwner, Long catalogueItemId) {
        String npub = requireNpub(npubOwner)
        List<StockMove> moves = StockMove.findAllByNpubOwnerAndCatalogueItemId(npub, catalogueItemId)
        BigDecimal sum = BigDecimal.ZERO
        moves.each { sum = sum.add(it.qtyDelta ?: BigDecimal.ZERO) }
        return sum
    }

    @Transactional(readOnly = true)
    List<Map> listMoves(String npubOwner, Long catalogueItemId = null) {
        String npub = requireNpub(npubOwner)
        List<StockMove> moves
        if (catalogueItemId) {
            moves = StockMove.findAllByNpubOwnerAndCatalogueItemId(npub, catalogueItemId, [sort: 'id', order: 'desc', max: 200])
        } else {
            moves = StockMove.findAllByNpubOwner(npub, [sort: 'id', order: 'desc', max: 200])
        }
        moves.collect { toMap(it) }
    }

    @Transactional(readOnly = true)
    List<Map> onHandSummary(String npubOwner) {
        String npub = requireNpub(npubOwner)
        Map<Long, BigDecimal> byItem = [:]
        StockMove.findAllByNpubOwner(npub, [max: 5000]).each { StockMove m ->
            byItem[m.catalogueItemId] = (byItem[m.catalogueItemId] ?: BigDecimal.ZERO).add(m.qtyDelta ?: BigDecimal.ZERO)
        }
        byItem.collect { Long cid, BigDecimal qty ->
            [catalogueItemId: cid, productId: cid, onHand: qty]
        }.findAll { (it.onHand as BigDecimal) != BigDecimal.ZERO }
    }

    Map toMap(StockMove m) {
        if (!m) return null
        [
                id              : m.id,
                name            : m.name,
                productId       : m.catalogueItemId,
                catalogueItemId : m.catalogueItemId,
                qtyDelta        : m.qtyDelta,
                reason          : m.reason,
                sourceType      : m.sourceType,
                sourceId        : m.sourceId,
                lotId           : m.lotId,
                movedAt         : m.movedAt,
                npubOwner       : m.npubOwner
        ]
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
