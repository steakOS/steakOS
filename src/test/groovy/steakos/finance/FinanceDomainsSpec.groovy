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
 * F-S0 domain constraint smoke tests (FS1–FS3 field model) with GORM DataTest.
 */
class FinanceDomainsSpec extends Specification implements DataTest {

    Class[] getDomainClassesToMock() {
        [Account, JournalEntry, JournalLine, Invoice, Payment, PaymentOption, Treasury, Budget, Payable] as Class[]
    }

    def "Account requires code and accountType"() {
        when:
        def a = new Account(npubOwner: 'npub1test', name: 'Cash', code: '1000', accountType: 'ASSET')

        then:
        a.validate()
    }

    def "Account rejects unknown accountType"() {
        when:
        def a = new Account(npubOwner: 'npub1test', name: 'X', code: '1', accountType: 'NOT_A_TYPE')

        then:
        !a.validate()
        a.errors['accountType']
    }

    def "JournalEntry period must be yyyy-MM (FS3)"() {
        when:
        def e = new JournalEntry(
                npubOwner: 'npub1test',
                name: 'Opening',
                entryDate: new Date(),
                period: '2026-07'
        )

        then:
        e.validate()

        when:
        e.period = '2026/07'

        then:
        !e.validate()
    }

    def "Invoice dual amounts and status (FS1/FS2)"() {
        when:
        def inv = new Invoice(
                npubOwner: 'npub1test',
                name: 'Test invoice',
                invoiceNumber: 'INV-1',
                amountEur: new BigDecimal('100.00'),
                amountSats: 150000L,
                currencyCode: 'EUR',
                invoiceStatus: 'DRAFT'
        )

        then:
        inv.validate()
        inv.currencyCode == 'EUR'
    }

    def "Payment rails constrained"() {
        given:
        def inv = new Invoice(
                npubOwner: 'npub1test',
                name: 'i',
                amountEur: 1.0G,
                invoiceStatus: 'ISSUED'
        ).save(flush: true, failOnError: true)

        when:
        def p = new Payment(
                npubOwner: 'npub1test',
                name: 'LN pay',
                invoice: inv,
                rail: 'BTC_LIGHTNING',
                amountEur: 1.0G,
                paymentStatus: 'SETTLED'
        )

        then:
        p.validate()

        when:
        p.rail = 'DOG_COIN'

        then:
        !p.validate()
    }

    def "Treasury kinds include BTC and LN"() {
        expect:
        new Treasury(npubOwner: 'n', name: 'LN', accountKind: 'LN_WALLET', balanceEur: 0G, balanceSats: 0L).validate()
        !new Treasury(npubOwner: 'n', name: 'X', accountKind: 'HEDGE_BOOK', balanceEur: 0G, balanceSats: 0L).validate()
    }

    def "Budget period calendar month"() {
        expect:
        new Budget(npubOwner: 'n', name: 'Ops', period: '2026-07', amountEur: 500G).validate()
        !new Budget(npubOwner: 'n', name: 'Ops', period: 'Q3-2026', amountEur: 500G).validate()
    }
}
