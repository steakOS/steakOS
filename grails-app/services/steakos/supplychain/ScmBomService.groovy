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
import steakos.production.BillOfMaterials
import steakos.production.BomItem

@Slf4j
class ScmBomService {

    CatalogueService catalogueService

    @Transactional
    BillOfMaterials createBom(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        Object cid = CatalogueService.resolveId(fields)
        if (!cid) throw new IllegalArgumentException('catalogueItemId (or productId) is required')
        CatalogueItem item = catalogueService.requireGoods(npub, cid)
        BillOfMaterials bom = new BillOfMaterials(
                npubOwner     : npub,
                name          : (fields.name ?: "BOM ${item.name}") as String,
                description   : fields.description as String,
                catalogueItem : item,
                bomStatus     : (fields.bomStatus ?: 'ACTIVE') as String,
                status        : 'ACTIVE'
        )
        bom.save(failOnError: true, flush: true)

        List items = (fields.items instanceof List) ? (List) fields.items : []
        if (items) replaceItems(bom, items)
        return bom
    }

    @Transactional
    BillOfMaterials setItems(Long bomId, String npubOwner, List items) {
        BillOfMaterials bom = load(bomId, npubOwner)
        replaceItems(bom, items ?: [])
        bom.save(failOnError: true, flush: true)
        return bom
    }

    @Transactional(readOnly = true)
    BillOfMaterials getBom(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<BillOfMaterials> listBoms(String npubOwner) {
        String npub = requireNpub(npubOwner)
        BillOfMaterials.findAllByNpubOwner(npub, [sort: 'id', order: 'desc', max: 200])
    }

    @Transactional(readOnly = true)
    BillOfMaterials getBomForProduct(Long catalogueItemId, String npubOwner) {
        String npub = requireNpub(npubOwner)
        CatalogueItem p = catalogueService.requireGoods(npub, catalogueItemId)
        BillOfMaterials.findByNpubOwnerAndCatalogueItemAndBomStatus(npub, p, 'ACTIVE')
    }

    Map toMap(BillOfMaterials bom, boolean includeItems = true) {
        if (!bom) return null
        Map m = [
                id              : bom.id,
                name            : bom.name,
                description     : bom.description,
                productId       : bom.catalogueItem?.id,
                catalogueItemId : bom.catalogueItem?.id,
                productName     : bom.catalogueItem?.name,
                bomStatus       : bom.bomStatus,
                npubOwner       : bom.npubOwner
        ]
        if (includeItems) {
            m.items = BomItem.findAllByBom(bom, [sort: 'lineNo']).collect { itemToMap(it) }
        }
        return m
    }

    Map itemToMap(BomItem i) {
        [
                id                 : i.id,
                lineNo             : i.lineNo,
                name               : i.name,
                componentProductId : i.componentItem?.id,
                componentItemId    : i.componentItem?.id,
                catalogueItemId    : i.componentItem?.id,
                componentName      : i.componentItem?.name,
                quantity           : i.quantity,
                unit               : i.unit
        ]
    }

    private void replaceItems(BillOfMaterials bom, List items) {
        BomItem.findAllByBom(bom).each { it.delete(flush: true) }
        int n = 1
        items.each { Object raw ->
            Map im = raw as Map
            Long compId = im.componentItemId != null ? im.componentItemId as Long :
                    (im.componentProductId != null ? im.componentProductId as Long :
                            (im.catalogueItemId != null ? im.catalogueItemId as Long : (im.productId as Long)))
            if (!compId) return
            CatalogueItem comp = catalogueService.requireGoods(bom.npubOwner, compId)
            BigDecimal qty = toBd(im.quantity) ?: BigDecimal.ONE
            new BomItem(
                    npubOwner      : bom.npubOwner,
                    bom            : bom,
                    lineNo         : n++,
                    name           : (im.name ?: comp.name) as String,
                    componentItem  : comp,
                    quantity       : qty,
                    unit           : (im.unit ?: comp.unit ?: 'EA') as String
            ).save(failOnError: true, flush: true)
        }
    }

    private BillOfMaterials load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        BillOfMaterials b = BillOfMaterials.get(id)
        if (!b || b.npubOwner != npub) throw new IllegalArgumentException("bom not found: ${id}")
        b
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
