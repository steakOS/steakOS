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

package steakos.search

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.User
import steakos.catalogue.CatalogueItem
import steakos.configuration.LegalEntity
import steakos.core.Task
import steakos.finance.Invoice
import steakos.procurement.PurchaseOrder
import steakos.procurement.Vendor
import steakos.sales.Customer
import steakos.sales.CustomerQuote
import steakos.sales.SalesOrder
import steakos.api.ListPaging
import steakos.security.RoleProfileService

/**
 * S0 fan-out global search (Task 7642) — SearchRegistry of type → fetcher.
 * Active-company tenancy; skip types the operator cannot See.
 */
@Slf4j
@Transactional(readOnly = true)
class SearchService {

    static final int DEFAULT_MAX_PER_TYPE = 5
    static final int HARD_MAX_PER_TYPE = 20
    static final int GLOBAL_MAX = 40
    static final int MIN_QUERY_LEN = 2

    RoleProfileService roleProfileService

    /**
     * @param q query (trimmed; empty / short → empty items)
     * @param types optional allowlist of type keys; null = all registered
     * @param maxPerType clamp per type (default 5)
     * @param npub active company npub
     * @param user acting user (permission checks)
     * @param enabledFamilies optional nav families from company config
     * @param templateTier metal tier for matrix
     * @param elevated admin/ceo — unlocks legalEntity
     */
    Map search(String q, Collection<String> types, Integer maxPerType,
               String npub, User user, Collection<String> enabledFamilies,
               String templateTier, boolean elevated) {
        String term = q?.trim() ?: ''
        if (term.length() < MIN_QUERY_LEN) {
            return [q: term, items: [], truncated: false]
        }
        int perType = Math.min(Math.max(maxPerType ?: DEFAULT_MAX_PER_TYPE, 1), HARD_MAX_PER_TYPE)
        Set<String> want = types?.findAll { it }?.collect { it.toString().trim().toLowerCase() } as Set
        List<Map> registry = buildRegistry()
        List<Map> items = []
        boolean truncated = false

        for (Map entry : registry) {
            String type = entry.type as String
            if (want && !(type in want)) {
                continue
            }
            if (!maySee(entry, user, enabledFamilies, templateTier, elevated)) {
                continue
            }
            if (items.size() >= GLOBAL_MAX) {
                truncated = true
                break
            }
            int room = Math.min(perType, GLOBAL_MAX - items.size())
            try {
                List<Map> hits = (entry.fetch as Closure).call(term, npub, room) as List<Map>
                if (hits) {
                    items.addAll(hits.take(room))
                    if (hits.size() > room) {
                        truncated = true
                    }
                }
            } catch (Exception ex) {
                log.debug('search type {} failed: {}', type, ex.message)
                // skip type quietly
            }
        }
        if (items.size() > GLOBAL_MAX) {
            items = items.take(GLOBAL_MAX)
            truncated = true
        }
        [q: term, items: items, truncated: truncated]
    }

    private boolean maySee(Map entry, User user, Collection<String> enabledFamilies,
                           String templateTier, boolean elevated) {
        if (entry.elevatedOnly) {
            return elevated
        }
        String family = entry.family as String
        if (!family) {
            return true
        }
        try {
            return roleProfileService.canSeeDesk(user, family, enabledFamilies, templateTier)
        } catch (Exception ex) {
            log.debug('canSeeDesk {} failed: {}', family, ex.message)
            return false
        }
    }

    private List<Map> buildRegistry() {
        [
                [
                        type    : 'customer',
                        family  : 'sales',
                        fetch   : { String term, String npub, int max ->
                            searchCustomer(term, npub, max)
                        }
                ],
                [
                        type    : 'vendor',
                        family  : 'scm',
                        fetch   : { String term, String npub, int max ->
                            searchVendor(term, npub, max)
                        }
                ],
                [
                        type    : 'catalogueItem',
                        family  : 'scm',
                        fetch   : { String term, String npub, int max ->
                            searchCatalogueItem(term, npub, max)
                        }
                ],
                [
                        type    : 'customerQuote',
                        family  : 'sales',
                        fetch   : { String term, String npub, int max ->
                            searchCustomerQuote(term, npub, max)
                        }
                ],
                [
                        type    : 'salesOrder',
                        family  : 'sales',
                        fetch   : { String term, String npub, int max ->
                            searchSalesOrder(term, npub, max)
                        }
                ],
                [
                        type    : 'invoice',
                        family  : 'finance',
                        fetch   : { String term, String npub, int max ->
                            searchInvoice(term, npub, max)
                        }
                ],
                [
                        type    : 'purchaseOrder',
                        family  : 'scm',
                        fetch   : { String term, String npub, int max ->
                            searchPurchaseOrder(term, npub, max)
                        }
                ],
                [
                        type    : 'task',
                        family  : 'core',
                        fetch   : { String term, String npub, int max ->
                            searchTask(term, npub, max)
                        }
                ],
                [
                        type         : 'legalEntity',
                        family       : null,
                        elevatedOnly : true,
                        fetch        : { String term, String npub, int max ->
                            searchLegalEntity(term, max)
                        }
                ],
        ]
    }

    private List<Map> searchCustomer(String term, String npub, int max) {
        String like = ListPaging.likeContains(term)
        if (!like) return []
        List rows = Customer.createCriteria().list(max: max, offset: 0, sort: 'name', order: 'asc') {
            if (npub) eq('npubOwner', npub)
            or {
                ilike('name', like)
                ilike('email', like)
                ilike('contactName', like)
                ilike('taxId', like)
                ilike('description', like)
            }
        } as List
        rows.collect { Customer c ->
            // Sales desk customers tab — not Opportunities desk (?id= is opportunity id space; Task 7656)
            item('customer', c.id, c.name, statusOf(c), c.email ?: c.taxId,
                    "/sales?tab=customers&id=${c.id}", 'sales')
        }
    }

    private List<Map> searchVendor(String term, String npub, int max) {
        String like = ListPaging.likeContains(term)
        if (!like) return []
        List rows = Vendor.createCriteria().list(max: max, offset: 0, sort: 'name', order: 'asc') {
            if (npub) eq('npubOwner', npub)
            or {
                ilike('name', like)
                ilike('email', like)
                ilike('contactName', like)
                ilike('taxId', like)
                ilike('description', like)
            }
        } as List
        rows.collect { Vendor v ->
            item('vendor', v.id, v.name, statusOf(v), v.email ?: v.taxId, "/scm/buyers?tab=vendors&id=${v.id}", 'scm')
        }
    }

    private List<Map> searchCatalogueItem(String term, String npub, int max) {
        String like = ListPaging.likeContains(term)
        if (!like) return []
        List rows = CatalogueItem.createCriteria().list(max: max, offset: 0, sort: 'name', order: 'asc') {
            if (npub) eq('npubOwner', npub)
            or {
                ilike('name', like)
                ilike('code', like)
                ilike('description', like)
            }
        } as List
        rows.collect { CatalogueItem ci ->
            String title = ci.code ? "${ci.code} · ${ci.name}" : ci.name
            item('catalogueItem', ci.id, title, statusOf(ci), ci.nature, "/domains/catalogue/CatalogueItem/${ci.id}", 'scm')
        }
    }

    private List<Map> searchCustomerQuote(String term, String npub, int max) {
        String like = ListPaging.likeContains(term)
        if (!like) return []
        List rows = CustomerQuote.createCriteria().list(max: max, offset: 0, sort: 'id', order: 'desc') {
            if (npub) eq('npubOwner', npub)
            or {
                ilike('name', like)
                ilike('quoteNumber', like)
                ilike('customerName', like)
                ilike('description', like)
            }
        } as List
        rows.collect { CustomerQuote q ->
            String title = q.quoteNumber ?: q.name
            item('customerQuote', q.id, title, q.quoteStatus ?: statusOf(q), q.customerName ?: q.name,
                    "/sales?tab=quotes&id=${q.id}", 'sales')
        }
    }

    private List<Map> searchSalesOrder(String term, String npub, int max) {
        String like = ListPaging.likeContains(term)
        if (!like) return []
        List rows = SalesOrder.createCriteria().list(max: max, offset: 0, sort: 'id', order: 'desc') {
            if (npub) eq('npubOwner', npub)
            or {
                ilike('name', like)
                ilike('orderNumber', like)
                ilike('customerName', like)
                ilike('description', like)
            }
        } as List
        rows.collect { SalesOrder o ->
            String title = o.orderNumber ?: o.name
            item('salesOrder', o.id, title, o.orderStatus ?: statusOf(o), o.customerName ?: o.name,
                    "/sales?tab=orders&id=${o.id}", 'sales')
        }
    }

    private List<Map> searchInvoice(String term, String npub, int max) {
        String like = ListPaging.likeContains(term)
        if (!like) return []
        List rows = Invoice.createCriteria().list(max: max, offset: 0, sort: 'id', order: 'desc') {
            if (npub) eq('npubOwner', npub)
            or {
                ilike('name', like)
                ilike('invoiceNumber', like)
                ilike('customerName', like)
                ilike('description', like)
                ilike('referenceText', like)
            }
        } as List
        rows.collect { Invoice inv ->
            String title = inv.invoiceNumber ?: inv.name
            item('invoice', inv.id, title, inv.invoiceStatus ?: statusOf(inv), inv.customerName ?: inv.name,
                    "/finance/invoices?id=${inv.id}", 'finance')
        }
    }

    private List<Map> searchPurchaseOrder(String term, String npub, int max) {
        String like = ListPaging.likeContains(term)
        if (!like) return []
        List rows = PurchaseOrder.createCriteria().list(max: max, offset: 0, sort: 'id', order: 'desc') {
            if (npub) eq('npubOwner', npub)
            or {
                ilike('name', like)
                ilike('poNumber', like)
                ilike('vendorName', like)
                ilike('description', like)
            }
        } as List
        rows.collect { PurchaseOrder po ->
            String title = po.poNumber ?: po.name
            item('purchaseOrder', po.id, title, po.poStatus ?: statusOf(po), po.vendorName ?: po.name,
                    "/scm/buyers?tab=purchase&id=${po.id}", 'scm')
        }
    }

    private List<Map> searchTask(String term, String npub, int max) {
        String like = ListPaging.likeContains(term)
        if (!like) return []
        List rows = Task.createCriteria().list(max: max, offset: 0, sort: 'id', order: 'desc') {
            if (npub) eq('npubOwner', npub)
            or {
                ilike('name', like)
                ilike('description', like)
            }
        } as List
        rows.collect { Task t ->
            item('task', t.id, t.name, statusOf(t), null, "/domains/core/Task/${t.id}", 'core')
        }
    }

    private List<Map> searchLegalEntity(String term, int max) {
        String like = ListPaging.likeContains(term)
        if (!like) return []
        List rows = LegalEntity.createCriteria().list(max: max, offset: 0, sort: 'name', order: 'asc') {
            or {
                ilike('name', like)
                ilike('code', like)
                ilike('taxId', like)
                ilike('description', like)
            }
        } as List
        rows.collect { LegalEntity le ->
            String subtitle = [le.code, le.kind].findAll { it }.join(' · ')
            item('legalEntity', le.id, le.name, le.kind, subtitle ?: null, '/company', 'configuration')
        }
    }

    private static Map item(String type, Object id, String title, String status, String subtitle,
                            String href, String family) {
        [
                type    : type,
                id      : id,
                title   : title ?: type,
                status  : status,
                subtitle: subtitle,
                href    : href,
                family  : family,
        ]
    }

    private static String statusOf(Object row) {
        try {
            if (row.hasProperty('status') && row['status']) {
                return row['status'] as String
            }
            if (row.hasProperty('active')) {
                return (row['active'] as Boolean) ? 'ACTIVE' : 'INACTIVE'
            }
        } catch (ignored) {
        }
        null
    }
}
