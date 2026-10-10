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
import steakos.production.Component

/**
 * Goods catalogue facade (nature=GOODS). Replaces legacy Product master.
 * API still under /api/scm/products; ids are catalogue item ids.
 */
@Slf4j
class ScmProductService {

    CatalogueService catalogueService

    @Transactional
    CatalogueItem createProduct(String npubOwner, Map fields) {
        Map f = new LinkedHashMap(fields ?: [:])
        f.nature = CatalogueItem.NATURE_GOODS
        if (f.sku && !f.code) f.code = f.sku
        if (!f.containsKey('canBeSold')) f.canBeSold = true
        if (!f.containsKey('canBeBought')) f.canBeBought = true
        catalogueService.create(npubOwner, f)
    }

    @Transactional
    CatalogueItem updateProduct(Long id, String npubOwner, Map fields) {
        Map f = new LinkedHashMap(fields ?: [:])
        if (f.sku && !f.code) f.code = f.sku
        f.nature = CatalogueItem.NATURE_GOODS
        catalogueService.update(id, npubOwner, f)
    }

    @Transactional
    Component createComponent(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        CatalogueItem item = null
        Object cid = CatalogueService.resolveId(fields)
        if (cid) {
            item = catalogueService.requireGoods(npub, cid)
        }
        Component c = new Component(
                npubOwner     : npub,
                name          : name,
                description   : fields.description as String,
                sku           : (fields.sku ?: fields.code) as String,
                unit          : (fields.unit ?: item?.unit ?: 'EA') as String,
                catalogueItem : item
        )
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional(readOnly = true)
    CatalogueItem getProduct(Long id, String npubOwner) {
        catalogueService.requireGoods(npubOwner, id)
    }

    @Transactional(readOnly = true)
    List<CatalogueItem> listProducts(String npubOwner, boolean activeOnly = true) {
        catalogueService.search(npubOwner, null, null, CatalogueItem.NATURE_GOODS, activeOnly, 500)
    }

    /** Product-shaped map for SPA compatibility (id = catalogue id, sku = code). */
    Map toMap(CatalogueItem p) {
        if (!p) return null
        [
                id              : p.id,
                name            : p.name,
                description     : p.description,
                sku             : p.code,
                code            : p.code,
                unit            : p.unit,
                isService       : false,
                trackLots       : false,
                active          : p.active,
                status          : p.status,
                nature          : p.nature,
                listPrice       : p.listPrice,
                currency        : p.currency,
                canBeSold       : p.canBeSold,
                canBeBought     : p.canBeBought,
                catalogueItemId : p.id,
                npubOwner       : p.npubOwner
        ]
    }

    Map componentToMap(Component c) {
        if (!c) return null
        [
                id              : c.id,
                name            : c.name,
                sku             : c.sku,
                unit            : c.unit,
                productId       : c.catalogueItem?.id,
                catalogueItemId : c.catalogueItem?.id,
                npubOwner       : c.npubOwner
        ]
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
