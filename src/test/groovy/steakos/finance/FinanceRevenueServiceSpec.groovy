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

package steakos.finance

import grails.testing.gorm.DataTest
import spock.lang.Specification

/**
 * F-S2 revenue: create → issue → settle (cash post) → void.
 */
class FinanceRevenueServiceSpec extends Specification implements DataTest {

    FinanceLedgerService ledger
    PostingRulesetService rulesetService
    PostingEngineService engine
    FinanceRevenueService service

    Class[] getDomainClassesToMock() {
        [Account, JournalEntry, JournalLine, Invoice, Payment, PaymentOption] as Class[]
    }

    def setup() {
        ledger = new FinanceLedgerService()
        rulesetService = new PostingRulesetService(financeLedgerService: ledger)
        engine = new PostingEngineService(postingRulesetService: rulesetService, financeLedgerService: ledger)
        service = new FinanceRevenueService(financeLedgerService: ledger, postingEngineService: engine)
    }

    def "create and issue with default rails"() {
        when:
        Invoice inv = service.createInvoice('npub1r', [
                customerName: 'Acme',
                amountEur   : 120.00G,
                amountSats  : 200000L
        ])
        inv = service.issueInvoice(inv.id, 'npub1r')

        then:
        inv.invoiceStatus == 'ISSUED'
        inv.invoiceNumber
        PaymentOption.countByInvoice(inv) == 3
    }

    def "settle posts Dr wallet Cr revenue and marks PAID"() {
        given:
        Invoice inv = service.createInvoice('npub1s', [amountEur: 50G, customerName: 'Bob'])
        inv = service.issueInvoice(inv.id, 'npub1s')

        when:
        Map result = service.recordSettlement(inv.id, 'npub1s', [
                rail   : 'BTC_LIGHTNING',
                eventId: 'pay-evt-1'
        ])

        then:
        result.payment.paymentStatus == 'SETTLED'
        result.journalEntryId
        Invoice.get(inv.id).invoiceStatus == 'PAID'
        // LN wallet 1020 debited, revenue 4000 credited
        JournalLine.findAll().any { it.account.code == '1020' && it.debitEur == 50G }
        JournalLine.findAll().any { it.account.code == '4000' && it.creditEur == 50G }

        when: // idempotent
        Map again = service.recordSettlement(inv.id, 'npub1s', [
                rail   : 'BTC_LIGHTNING',
                eventId: 'pay-evt-1'
        ])

        then:
        again.idempotent
        again.payment.id == result.payment.id
    }

    def "partial settle yields PART_PAID"() {
        given:
        Invoice inv = service.createInvoice('npub1t', [amountEur: 100G])
        service.issueInvoice(inv.id, 'npub1t')

        when:
        service.recordSettlement(inv.id, 'npub1t', [
                rail     : 'SEPA',
                amountEur: 40G,
                eventId  : 'part-1'
        ])

        then:
        Invoice.get(inv.id).invoiceStatus == 'PART_PAID'
    }

    def "void reverses settlement"() {
        given:
        Invoice inv = service.createInvoice('npub1u', [amountEur: 30G])
        service.issueInvoice(inv.id, 'npub1u')
        service.recordSettlement(inv.id, 'npub1u', [rail: 'SEPA', eventId: 'v1'])

        when:
        Invoice voided = service.voidInvoice(inv.id, 'npub1u', 'customer cancel')

        then:
        voided.invoiceStatus == 'VOID'
        Payment.findAllByInvoice(voided).every { it.paymentStatus == 'REFUNDED' }
    }

    def "cannot settle draft"() {
        given:
        Invoice inv = service.createInvoice('npub1v', [amountEur: 10G])

        when:
        service.recordSettlement(inv.id, 'npub1v', [rail: 'SEPA'])

        then:
        thrown(IllegalStateException)
    }
}
