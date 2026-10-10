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

package steakos.delivery

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.catalogue.CatalogueItem
import steakos.catalogue.CatalogueService

/**
 * Service catalogue facade (nature=SERVICE). Replaces legacy delivery.Service master.
 * API still under /api/delivery/services; ids are catalogue item ids.
 */
@Slf4j
class DelServiceService {

    CatalogueService catalogueService

    @Transactional
    CatalogueItem createService(String npubOwner, Map fields) {
        Map f = new LinkedHashMap(fields ?: [:])
        f.nature = CatalogueItem.NATURE_SERVICE
        if (f.listPriceEur != null && f.listPrice == null) f.listPrice = f.listPriceEur
        if (!f.unit) f.unit = 'HOUR'
        if (!f.containsKey('canBeSold')) f.canBeSold = true
        if (!f.containsKey('canBeBought')) f.canBeBought = false
        catalogueService.create(npubOwner, f)
    }

    @Transactional
    CatalogueItem updateService(Long id, String npubOwner, Map fields) {
        Map f = new LinkedHashMap(fields ?: [:])
        f.nature = CatalogueItem.NATURE_SERVICE
        if (f.listPriceEur != null && f.listPrice == null) f.listPrice = f.listPriceEur
        catalogueService.update(id, npubOwner, f)
    }

    @Transactional(readOnly = true)
    CatalogueItem getService(Long id, String npubOwner) {
        catalogueService.requireItem(npubOwner, id, CatalogueItem.NATURE_SERVICE)
    }

    @Transactional(readOnly = true)
    List<CatalogueItem> listServices(String npubOwner, boolean activeOnly = true) {
        listServicesPage(npubOwner, activeOnly, 200, 0).items as List<CatalogueItem>
    }

    /**
     * Paged service catalogue (nature=SERVICE). Returns [items, total].
     */
    @Transactional(readOnly = true)
    Map listServicesPage(String npubOwner, boolean activeOnly = true, int max = 25, int offset = 0) {
        catalogueService.searchPage(
                npubOwner, null, null, CatalogueItem.NATURE_SERVICE, activeOnly, max, offset)
    }

    Map toMap(CatalogueItem s) {
        if (!s) return null
        [
                id              : s.id,
                name            : s.name,
                description     : s.description,
                code            : s.code,
                slaCode         : null,
                unit            : s.unit,
                defaultDuration : null,
                listPriceEur    : s.listPrice,
                listPrice       : s.listPrice,
                listPriceSats   : null,
                active          : s.active,
                status          : s.status,
                nature          : s.nature,
                canBeSold       : s.canBeSold,
                canBeBought     : s.canBeBought,
                catalogueItemId : s.id,
                npubOwner       : s.npubOwner
        ]
    }
}
