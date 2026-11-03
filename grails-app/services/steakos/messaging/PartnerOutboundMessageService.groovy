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

package steakos.messaging

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.configuration.PartnerLocaleService
import steakos.events.DomainEventNames
import steakos.procurement.Vendor
import steakos.sales.Customer

/**
 * OI-I18N02 PR4a / PL-TR3 — compose partner-locale email subject/body from messaging packs.
 * Resolves locale via {@link PartnerLocaleService}; never uses operator UI locale.
 */
@Slf4j
@Transactional(readOnly = true)
class PartnerOutboundMessageService {

    PartnerLocaleService partnerLocaleService

    /**
     * Compose subject/text (and optional {@code to}) for an outbound domain event.
     * Returns empty map when neither customerId nor vendorId is present.
     */
    Map composeForEvent(String eventName, Map payload) {
        Map p = payload ?: [:]
        Long customerId = toLong(p.customerId)
        Long vendorId = toLong(p.vendorId)
        if (customerId == null && vendorId == null) {
            return [:]
        }

        Customer customer = customerId != null ? Customer.get(customerId) : null
        Vendor vendor = vendorId != null ? Vendor.get(vendorId) : null

        String locale = resolveLocale(customer, vendor, p)
        MessagingMessageBundle bundle = MessagingMessageBundle.forLocale(locale)

        Map out = new LinkedHashMap()
        out.locale = locale

        String to = firstNonBlank(p.to, p.email)
        if (!to) {
            to = firstNonBlank(customer?.email, vendor?.email)
        }
        if (to) {
            out.to = to
            out.email = to
        }

        if (!p.subject || (!p.text && !p.body)) {
            Map composed = composeBody(eventName, p, bundle, customer, vendor)
            if (!p.subject && composed.subject) out.subject = composed.subject
            if (!p.text && !p.body && composed.text) out.text = composed.text
        }

        out
    }

    private Map composeBody(String eventName, Map p, MessagingMessageBundle bundle,
                            Customer customer, Vendor vendor) {
        if (eventName == DomainEventNames.FINANCE_INVOICE_OVERDUE) {
            String invNo = (p.invoiceNumber ?: p.invoiceId ?: '').toString()
            String amount = formatAmount(p.amountOpenEur ?: p.amountEur, p.currencyCode)
            String due = formatDate(p.dueDate)
            String partnerName = firstNonBlank(
                    p.customerName, customer?.name, p.vendorName, vendor?.name, '')
            return [
                    subject: bundle.format('msg.invoice.overdue.subject', invNo),
                    text   : bundle.format('msg.invoice.overdue.body', invNo, amount, due, partnerName)
            ]
        }
        String details = (p.findAll { k, v ->
            !(k in ['to', 'email', 'subject', 'body', 'text', 'chatId', 'telegramChatId', 'locale'])
        }.collect { k, v -> "${k}=${v}" }.join('\n')) ?: ''
        [
                subject: bundle.format('msg.event.subject', eventName ?: 'event'),
                text   : bundle.format('msg.event.body', eventName ?: 'event', details)
        ]
    }

    private String resolveLocale(Customer customer, Vendor vendor, Map p) {
        if (partnerLocaleService) {
            if (customer) return partnerLocaleService.resolveCustomer(customer)
            if (vendor) return partnerLocaleService.resolveVendor(vendor)
            if (p.communicationLocale || p.documentLocale) {
                return partnerLocaleService.resolve(
                        (p.documentLocale ?: p.communicationLocale) as String)
            }
        }
        String raw = (p.documentLocale ?: p.communicationLocale ?: p.locale) as String
        if (raw?.trim()) {
            String primary = raw.replace('-', '_').split('_')[0]?.toLowerCase()
            return primary == 'de' ? 'de_DE' : (raw.contains('_') ? raw : 'en_US')
        }
        PartnerLocaleService.FALLBACK
    }

    private static String formatAmount(Object amount, Object currencyCode) {
        if (amount == null) return ''
        String cur = (currencyCode ?: 'EUR').toString()
        "${amount} ${cur}"
    }

    private static String formatDate(Object raw) {
        if (raw == null) return ''
        if (raw instanceof Date) {
            return ((Date) raw).format('yyyy-MM-dd')
        }
        String s = raw.toString().trim()
        s.length() >= 10 ? s.take(10) : s
    }

    private static Long toLong(Object v) {
        if (v == null || v == '') return null
        if (v instanceof Number) return ((Number) v).longValue()
        try {
            return Long.parseLong(v.toString().trim())
        } catch (ignored) {
            return null
        }
    }

    private static String firstNonBlank(Object... vals) {
        for (Object v : vals) {
            String s = v?.toString()?.trim()
            if (s) return s
        }
        null
    }
}
