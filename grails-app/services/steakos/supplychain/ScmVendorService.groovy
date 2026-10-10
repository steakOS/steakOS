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
import steakos.procurement.Vendor

@Slf4j
class ScmVendorService {

    steakos.configuration.CompanyCurrencyService companyCurrencyService
    steakos.configuration.PartnerLocaleService partnerLocaleService

    @Transactional
    Vendor createVendor(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        String ccy = (fields.currencyCode ?: 'EUR') as String
        companyCurrencyService?.validateWritableCode(npub, ccy)
        String locale = null
        if (fields.containsKey('communicationLocale')) {
            locale = partnerLocaleService?.normalizeWritable(fields.communicationLocale as String)
        }
        Vendor v = new Vendor(
                npubOwner           : npub,
                name                : name,
                description         : fields.description as String,
                email               : fields.email as String,
                phone               : fields.phone as String,
                contactName         : fields.contactName as String,
                vendorNpub          : fields.vendorNpub as String,
                taxId               : fields.taxId as String,
                currencyCode        : ccy,
                communicationLocale : locale,
                active              : fields.containsKey('active') ? fields.active as Boolean : Boolean.TRUE,
                status              : (fields.status ?: 'ACTIVE') as String
        )
        v.save(failOnError: true, flush: true)
        return v
    }

    @Transactional
    Vendor updateVendor(Long id, String npubOwner, Map fields) {
        Vendor v = load(id, npubOwner)
        if (fields.name) v.name = fields.name as String
        if (fields.containsKey('description')) v.description = fields.description as String
        if (fields.containsKey('email')) v.email = fields.email as String
        if (fields.containsKey('phone')) v.phone = fields.phone as String
        if (fields.containsKey('contactName')) v.contactName = fields.contactName as String
        if (fields.containsKey('vendorNpub')) v.vendorNpub = fields.vendorNpub as String
        if (fields.containsKey('taxId')) v.taxId = fields.taxId as String
        if (fields.currencyCode) {
            companyCurrencyService?.validateWritableCode(npubOwner, fields.currencyCode as String)
            v.currencyCode = fields.currencyCode as String
        }
        if (fields.containsKey('communicationLocale')) {
            v.communicationLocale = partnerLocaleService?.normalizeWritable(fields.communicationLocale as String)
        }
        if (fields.containsKey('active')) v.active = fields.active as Boolean
        v.save(failOnError: true, flush: true)
        return v
    }

    @Transactional(readOnly = true)
    Vendor getVendor(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Vendor> listVendors(String npubOwner, boolean activeOnly = true) {
        listVendorsPage(npubOwner, activeOnly, 500, 0).items as List<Vendor>
    }

    /** Paged vendors — optional free-text `q` (name/email/contact/taxId). */
    @Transactional(readOnly = true)
    Map listVendorsPage(String npubOwner, boolean activeOnly = true, int max = 25, int offset = 0,
                        String q = null) {
        String npub = requireNpub(npubOwner)
        String term = q?.trim() ? "%${q.trim()}%" : null
        List items = Vendor.createCriteria().list(max: max, offset: offset, sort: 'name', order: 'asc') {
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
        Number total = Vendor.createCriteria().count {
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

    Map toMap(Vendor v) {
        if (!v) return null
        [
                id          : v.id,
                name        : v.name,
                description : v.description,
                email       : v.email,
                phone       : v.phone,
                contactName : v.contactName,
                vendorNpub  : v.vendorNpub,
                taxId               : v.taxId,
                currencyCode        : v.currencyCode,
                communicationLocale : v.communicationLocale,
                active              : v.active,
                status              : v.status,
                npubOwner           : v.npubOwner
        ]
    }

    private Vendor load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Vendor v = Vendor.get(id)
        if (!v || v.npubOwner != npub) throw new IllegalArgumentException("vendor not found: ${id}")
        v
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
