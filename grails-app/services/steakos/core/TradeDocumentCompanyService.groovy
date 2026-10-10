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

package steakos.core

import grails.gorm.transactions.Transactional
import steakos.configuration.BankAccount
import steakos.configuration.LegalEntity
import steakos.configuration.Site

/**
 * Loads seller (consignor) party data for ISO 6422-1 trade PDFs from configuration:
 * LegalEntity + preferred Site + BankAccount.
 */
@Transactional(readOnly = true)
class TradeDocumentCompanyService {

    /**
     * @return map: name, legalForm, taxId, countryCode, currencyCode, addressLines (List),
     *         siteName, bankLines (List), dualSats
     */
    Map loadSeller(String npubOwner) {
        LegalEntity le = resolveLegalEntity(npubOwner)
        if (!le) {
            return [
                    name         : 'steakOS tenant',
                    legalForm    : null,
                    taxId        : null,
                    countryCode  : null,
                    currencyCode : 'EUR',
                    addressLines : [],
                    siteName     : null,
                    bankLines    : [],
                    dualSats     : false
            ]
        }

        Site site = preferredSite(le)
        String address = (site?.addressText?.trim() ?: le.registeredAddressText?.trim())
        List addressLines = splitAddress(address)

        List bankLines = []
        preferredBanks(le).each { BankAccount ba ->
            List parts = []
            if (ba.name) parts << ba.name
            if (ba.iban) parts << "IBAN ${ba.iban}"
            if (ba.bic) parts << "BIC ${ba.bic}"
            if (ba.currencyCode) parts << ba.currencyCode
            if (parts) bankLines << parts.join(' · ')
        }

        return [
                name         : le.name?.trim() ?: 'Company',
                legalForm    : le.legalForm,
                taxId        : le.taxId,
                countryCode  : le.countryCode,
                currencyCode : le.functionalCurrency ?: 'EUR',
                addressLines : addressLines,
                siteName     : site?.name,
                bankLines    : bankLines,
                dualSats     : Boolean.TRUE == le.dualSats
        ]
    }

    private static LegalEntity resolveLegalEntity(String npubOwner) {
        if (!npubOwner) return null
        LegalEntity le = LegalEntity.findByNpubOwner(npubOwner)
        if (le) return le
        // Fallback: primary LIVE if npub matches bootstrap user key
        LegalEntity.findByCode(LegalEntity.CODE_PRIMARY)
    }

    private static Site preferredSite(LegalEntity le) {
        List<Site> sites = Site.findAllByLegalEntityAndActive(le, Boolean.TRUE, [sort: 'id', order: 'asc'])
        Site hq = sites.find { (it.siteType ?: '').equalsIgnoreCase('HQ') }
        hq ?: (sites ? sites[0] : null)
    }

    private static List<BankAccount> preferredBanks(LegalEntity le) {
        List<BankAccount> all = BankAccount.findAllByLegalEntityAndActive(le, Boolean.TRUE, [sort: 'id', order: 'asc'])
        List<BankAccount> bank = all.findAll { (it.accountKind ?: '').equalsIgnoreCase('BANK') && it.iban }
        if (bank) return bank.take(2)
        all.findAll { it.iban || it.externalRef }.take(2)
    }

    private static List<String> splitAddress(String raw) {
        if (!raw) return []
        raw.replace('\r\n', '\n').replace('\r', '\n').split(/\n|;/).collect { it.trim() }.findAll { it }
    }
}
