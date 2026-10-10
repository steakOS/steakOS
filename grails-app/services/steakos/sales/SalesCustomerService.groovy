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
 * Customer master (S-S1 / APQC 3.5.2).
 */
@Slf4j
class SalesCustomerService {

    steakos.configuration.CompanyCurrencyService companyCurrencyService
    steakos.configuration.PartnerLocaleService partnerLocaleService

    @Transactional
    Customer createCustomer(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        String ccy = (fields.currencyCode ?: 'EUR') as String
        companyCurrencyService?.validateWritableCode(npub, ccy)
        String locale = null
        if (fields.containsKey('communicationLocale')) {
            locale = partnerLocaleService?.normalizeWritable(fields.communicationLocale as String)
        }
        Customer c = new Customer(
                npubOwner            : npub,
                name                 : name,
                description          : fields.description as String,
                customerKind         : (fields.customerKind ?: 'ORGANIZATION') as String,
                email                : fields.email as String,
                phone                : fields.phone as String,
                contactName          : fields.contactName as String,
                customerNpub         : fields.customerNpub as String,
                taxId                : fields.taxId as String,
                currencyCode         : ccy,
                communicationLocale  : locale,
                active               : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE,
                status               : (fields.status ?: 'ACTIVE') as String
        )
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    Customer updateCustomer(Long id, String npubOwner, Map fields) {
        Customer c = load(id, npubOwner)
        if (fields.name) c.name = fields.name as String
        if (fields.containsKey('description')) c.description = fields.description as String
        if (fields.customerKind) c.customerKind = fields.customerKind as String
        if (fields.containsKey('email')) c.email = fields.email as String
        if (fields.containsKey('phone')) c.phone = fields.phone as String
        if (fields.containsKey('contactName')) c.contactName = fields.contactName as String
        if (fields.containsKey('customerNpub')) c.customerNpub = fields.customerNpub as String
        if (fields.containsKey('taxId')) c.taxId = fields.taxId as String
        if (fields.currencyCode) {
            companyCurrencyService?.validateWritableCode(npubOwner, fields.currencyCode as String)
            c.currencyCode = fields.currencyCode as String
        }
        if (fields.containsKey('communicationLocale')) {
            c.communicationLocale = partnerLocaleService?.normalizeWritable(fields.communicationLocale as String)
        }
        if (fields.containsKey('active')) c.active = fields.active as Boolean
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional(readOnly = true)
    Customer getCustomer(Long id, String npubOwner) {
        load(id, npubOwner)
    }

    @Transactional(readOnly = true)
    List<Customer> listCustomers(String npubOwner, boolean activeOnly = true) {
        listCustomersPage(npubOwner, activeOnly, 500, 0).items as List<Customer>
    }

    /** Paged list — full-dataset count + page (LIST-PAGINATION-CONCEPT). Optional free-text `q`. */
    @Transactional(readOnly = true)
    Map listCustomersPage(String npubOwner, boolean activeOnly = true, int max = 25, int offset = 0,
                          String q = null) {
        String npub = requireNpub(npubOwner)
        String term = q?.trim() ? "%${q.trim()}%" : null
        List items = Customer.createCriteria().list(max: max, offset: offset, sort: 'name', order: 'asc') {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            if (term) {
                or {
                    ilike('name', term)
                    ilike('email', term)
                    ilike('contactName', term)
                    ilike('taxId', term)
                }
            }
        } as List
        Number total = Customer.createCriteria().count {
            eq('npubOwner', npub)
            if (activeOnly) eq('active', Boolean.TRUE)
            if (term) {
                or {
                    ilike('name', term)
                    ilike('email', term)
                    ilike('contactName', term)
                    ilike('taxId', term)
                }
            }
        } as Number
        [items: items, total: total?.intValue() ?: 0]
    }

    Map toMap(Customer c) {
        if (!c) return null
        [
                id          : c.id,
                name        : c.name,
                description : c.description,
                customerKind: c.customerKind,
                email       : c.email,
                phone       : c.phone,
                contactName : c.contactName,
                customerNpub: c.customerNpub,
                taxId               : c.taxId,
                currencyCode        : c.currencyCode,
                communicationLocale : c.communicationLocale,
                active              : c.active,
                status              : c.status,
                npubOwner           : c.npubOwner
        ]
    }

    private Customer load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Customer c = Customer.get(id)
        if (!c || c.npubOwner != npub) throw new IllegalArgumentException("customer not found: ${id}")
        c
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
