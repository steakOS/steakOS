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

package steakos.configuration

import grails.gorm.transactions.Transactional
import steakos.finance.Invoice
import steakos.helper.SupportedLocaleCodes
import steakos.procurement.PurchaseOrder
import steakos.procurement.Vendor
import steakos.sales.Customer
import steakos.sales.CustomerQuote
import steakos.sales.SalesOrder

/**
 * OI-I18N02 — resolve partner communication locale for outbound PDFs and email (PR4a).
 * Nostr partner DMs remain residual (PL-TR4 giftwrap). Never uses operator UI locale.
 * Fallback {@link #FALLBACK} (en_US).
 */
@Transactional
class PartnerLocaleService {

    static final String FALLBACK = LocaleService.DEFAULT_LOCALE // en_US

    /** PDF packs shipped in v1 (D9). Other locales fall back to en. */
    static final Set<String> PDF_PACK_LANGS = (['en', 'de'] as Set).asImmutable()

    LocaleService localeService

    /** Resolve from Customer.communicationLocale → en_US. */
    String resolveCustomer(Customer customer) {
        resolveRaw(customer?.communicationLocale)
    }

    /** Resolve from Vendor.communicationLocale → en_US. */
    String resolveVendor(Vendor vendor) {
        resolveRaw(vendor?.communicationLocale)
    }

    /** Resolve a raw locale string (blank → en_US). */
    String resolve(String communicationLocale) {
        resolveRaw(communicationLocale)
    }

    /**
     * Draft: live partner locale. Issued: frozen {@code documentLocale} when set.
     */
    String resolveForDocument(CustomerQuote quote) {
        if (quote?.documentLocale) return resolveRaw(quote.documentLocale)
        resolveCustomer(quote?.customer)
    }

    String resolveForDocument(SalesOrder order) {
        if (order?.documentLocale) return resolveRaw(order.documentLocale)
        resolveCustomer(order?.customer)
    }

    String resolveForDocument(PurchaseOrder po) {
        if (po?.documentLocale) return resolveRaw(po.documentLocale)
        resolveVendor(po?.vendor)
    }

    String resolveForDocument(Invoice inv) {
        if (inv?.documentLocale) return resolveRaw(inv.documentLocale)
        Customer c = inv?.customerId ? Customer.get(inv.customerId as Long) : null
        resolveCustomer(c)
    }

    /**
     * Language code for PDF message pack (D9): de_* → de; else en.
     */
    String packLanguage(String localeCode) {
        String n = resolveRaw(localeCode)
        String lang = localeService?.languageOf(n) ?: languageCodeOf(n)
        PDF_PACK_LANGS.contains(lang) ? lang : 'en'
    }

    /** Normalize writable partner locale; blank/invalid → null (store blank). */
    String normalizeWritable(String raw) {
        if (!raw?.trim()) return null
        String n = localeService?.normalize(raw) ?: normalizeLocal(raw)
        if (!n) throw new IllegalArgumentException("Invalid communication locale: ${raw}")
        n
    }

    void snapshotQuote(CustomerQuote q) {
        if (!q) return
        q.documentLocale = resolveCustomer(q.customer)
    }

    void snapshotSalesOrder(SalesOrder o) {
        if (!o) return
        o.documentLocale = resolveCustomer(o.customer)
    }

    void snapshotPurchaseOrder(PurchaseOrder po) {
        if (!po) return
        po.documentLocale = resolveVendor(po.vendor)
    }

    void snapshotInvoice(Invoice inv) {
        if (!inv) return
        Customer c = inv.customerId ? Customer.get(inv.customerId as Long) : null
        inv.documentLocale = resolveCustomer(c)
    }

    private String resolveRaw(String raw) {
        if (!raw?.trim()) return FALLBACK
        String n = localeService?.normalize(raw) ?: normalizeLocal(raw)
        n ?: FALLBACK
    }

    private static String normalizeLocal(String code) {
        if (!code?.trim()) return null
        String c = code.trim().replace('-', '_')
        if (c == 'en') return 'en_US'
        if (c == 'de') return 'de_DE'
        SupportedLocaleCodes.isValid(c) ? c : null
    }

    private static String languageCodeOf(String localeCode) {
        if (!localeCode) return 'en'
        String[] parts = localeCode.replace('-', '_').split('_')
        parts[0]?.toLowerCase() ?: 'en'
    }
}
