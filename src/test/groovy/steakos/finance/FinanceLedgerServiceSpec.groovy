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
import steakos.configuration.TenantMoneyService

/**
 * F-S1 ledger kernel: COA seed, balanced post, reverse, trial balance, period close.
 */
class FinanceLedgerServiceSpec extends Specification implements DataTest {

    FinanceLedgerService service

    Class[] getDomainClassesToMock() {
        [Account, JournalEntry, JournalLine] as Class[]
    }

    def setup() {
        service = new FinanceLedgerService()
    }

    def "postEntry uses tenant functional currency when TenantMoneyService set"() {
        given:
        service.tenantMoneyService = Stub(TenantMoneyService) {
            functionalCurrency('npub1btc') >> 'BTC'
        }
        service.ensureChartSeeded('npub1btc')
        def cmd = new PostJournalCommand(
                npubOwner : 'npub1btc',
                name      : 'BTC open',
                entryDate : Date.parse('yyyy-MM-dd', '2026-08-01'),
                period    : '2026-08',
                sourceType: 'MANUAL',
                eventId   : 'evt-btc-1',
                lines     : [
                        new PostJournalLineCommand(accountCode: '1010', debitEur: 0.5G, creditEur: 0G, amountSats: 50_000_000L),
                        new PostJournalLineCommand(accountCode: '3000', debitEur: 0G, creditEur: 0.5G),
                ]
        )

        when:
        JournalEntry e = service.postEntry(cmd)
        Map tb = service.trialBalance('npub1btc', '2026-08')

        then:
        e.functionalCurrency == 'BTC'
        tb.currency == 'BTC'
        tb.balanced
    }

    def "ensureChartSeeded creates SBM COA once"() {
        when:
        List first = service.ensureChartSeeded('npub1a')
        List second = service.ensureChartSeeded('npub1a')

        then:
        // SBM seed includes payroll 2200/2210/5200 (PDR-027) — currently 13 accounts
        first.size() == 13
        second.size() == 13
        Account.countByNpubOwner('npub1a') == 13
        service.findAccount('npub1a', '1010')?.accountSubType == 'BTC_WALLET'
        service.findAccount('npub1a', '5200')?.accountSubType == 'PAYROLL'
    }

    def "postEntry requires balance and seeds usage by code"() {
        given:
        service.ensureChartSeeded('npub1b')
        def cmd = new PostJournalCommand(
                npubOwner : 'npub1b',
                name      : 'Open cash',
                entryDate : Date.parse('yyyy-MM-dd', '2026-07-15'),
                period    : '2026-07',
                sourceType: 'MANUAL',
                sourceId  : '1',
                eventId   : 'evt-1',
                lines     : [
                        new PostJournalLineCommand(accountCode: '1000', debitEur: 100.00G, creditEur: 0G),
                        new PostJournalLineCommand(accountCode: '3000', debitEur: 0G, creditEur: 100.00G),
                ]
        )

        when:
        JournalEntry e = service.postEntry(cmd)

        then:
        e.id
        e.posted
        e.period == '2026-07'
        JournalLine.countByJournalEntry(e) == 2

        when: // unbalanced
        service.postEntry(new PostJournalCommand(
                npubOwner: 'npub1b',
                lines: [
                        new PostJournalLineCommand(accountCode: '1000', debitEur: 50G, creditEur: 0G),
                        new PostJournalLineCommand(accountCode: '3000', debitEur: 0G, creditEur: 40G),
                ]
        ))

        then:
        thrown(IllegalArgumentException)
    }

    def "postEntry is idempotent on eventId"() {
        given:
        service.ensureChartSeeded('npub1c')
        def cmd = {
            new PostJournalCommand(
                    npubOwner : 'npub1c',
                    name      : 'Pay',
                    eventId   : 'same-event',
                    sourceType: 'PAYMENT',
                    sourceId  : '9',
                    lines     : [
                            new PostJournalLineCommand(accountCode: '1010', debitEur: 10G, creditEur: 0G),
                            new PostJournalLineCommand(accountCode: '4000', debitEur: 0G, creditEur: 10G),
                    ]
            )
        }

        when:
        JournalEntry a = service.postEntry(cmd())
        JournalEntry b = service.postEntry(cmd())

        then:
        a.id == b.id
        JournalEntry.countByNpubOwner('npub1c') == 1
    }

    def "reverseEntry swaps sides"() {
        given:
        service.ensureChartSeeded('npub1d')
        JournalEntry original = service.postEntry(new PostJournalCommand(
                npubOwner: 'npub1d',
                name: 'Rev me',
                eventId: 'to-rev',
                lines: [
                        new PostJournalLineCommand(accountCode: '5000', debitEur: 25G, creditEur: 0G),
                        new PostJournalLineCommand(accountCode: '1000', debitEur: 0G, creditEur: 25G),
                ]
        ))

        when:
        JournalEntry rev = service.reverseEntry(original.id, 'npub1d', 'mistake')

        then:
        rev.reversesEntryId == original.id
        rev.sourceType == 'REVERSAL'
        List lines = JournalLine.findAllByJournalEntry(rev, [sort: 'lineNo'])
        lines[0].debitEur == 0G || lines[0].creditEur == 25G || lines[1].debitEur == 25G
        // cash was credited 25 → reverse debits cash 25
        lines.find { it.account.code == '1000' }.debitEur == 25G
        lines.find { it.account.code == '5000' }.creditEur == 25G
    }

    def "trialBalance and account balance"() {
        given:
        service.ensureChartSeeded('npub1e')
        service.postEntry(new PostJournalCommand(
                npubOwner: 'npub1e',
                period: '2026-07',
                eventId: 'tb-1',
                lines: [
                        new PostJournalLineCommand(accountCode: '1000', debitEur: 200G, creditEur: 0G),
                        new PostJournalLineCommand(accountCode: '4000', debitEur: 0G, creditEur: 200G),
                ]
        ))

        when:
        Map tb = service.trialBalance('npub1e', '2026-07')
        Account cash = service.findAccount('npub1e', '1000')
        Map bal = service.getAccountBalance('npub1e', cash.id)

        then:
        tb.balanced
        tb.totalDebits == 200G
        tb.totalCredits == 200G
        bal.balanceEur == 200G
    }

    def "closePeriod blocks further posts"() {
        given:
        service.ensureChartSeeded('npub1f')
        service.postEntry(new PostJournalCommand(
                npubOwner: 'npub1f',
                period: '2026-06',
                eventId: 'before-close',
                lines: [
                        new PostJournalLineCommand(accountCode: '1000', debitEur: 5G, creditEur: 0G),
                        new PostJournalLineCommand(accountCode: '3000', debitEur: 0G, creditEur: 5G),
                ]
        ))

        when:
        Map closed = service.closePeriod('npub1f', '2026-06')

        then:
        closed.closed
        service.isPeriodClosed('npub1f', '2026-06')

        when:
        service.postEntry(new PostJournalCommand(
                npubOwner: 'npub1f',
                period: '2026-06',
                eventId: 'after-close',
                lines: [
                        new PostJournalLineCommand(accountCode: '1000', debitEur: 1G, creditEur: 0G),
                        new PostJournalLineCommand(accountCode: '3000', debitEur: 0G, creditEur: 1G),
                ]
        ))

        then:
        thrown(IllegalStateException)
    }
}
