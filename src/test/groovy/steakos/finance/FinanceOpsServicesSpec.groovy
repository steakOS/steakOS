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

class FinanceOpsServicesSpec extends Specification implements DataTest {

    FinanceLedgerService ledger
    FinanceTreasuryService treasury
    PostingRulesetService rulesetService
    PostingEngineService engine
    FinancePayableService payables
    FinancePlanService plan
    FinanceTaxService tax
    FinancePayrollService payroll
    FinanceControlService control
    FinanceRevenueService revenue

    Class[] getDomainClassesToMock() {
        [Account, JournalEntry, JournalLine, Invoice, Payment, PaymentOption,
         Treasury, Payable, Budget, Tax, PayrollItem] as Class[]
    }

    def setup() {
        ledger = new FinanceLedgerService()
        treasury = new FinanceTreasuryService(financeLedgerService: ledger)
        rulesetService = new PostingRulesetService(financeLedgerService: ledger)
        engine = new PostingEngineService(postingRulesetService: rulesetService, financeLedgerService: ledger)
        payables = new FinancePayableService(
                financeLedgerService: ledger,
                financeTreasuryService: treasury,
                postingEngineService: engine)
        plan = new FinancePlanService(financeLedgerService: ledger)
        tax = new FinanceTaxService(financeLedgerService: ledger)
        payroll = new FinancePayrollService(
                postingEngineService: engine,
                financeLedgerService: ledger,
                financeTreasuryService: treasury)
        control = new FinanceControlService(financeLedgerService: ledger)
        revenue = new FinanceRevenueService(
                financeLedgerService: ledger,
                financeTreasuryService: treasury,
                postingEngineService: engine)
    }

    def "treasury seed and inbound"() {
        when:
        def list = treasury.ensureDefaultPositions('npub-ops')
        def pos = treasury.recordInbound('npub-ops', [rail: 'BTC_LIGHTNING', amountEur: 10G, amountSats: 1000L])

        then:
        list.size() == 3
        pos.accountKind == 'LN_WALLET'
        pos.balanceEur == 10G
    }

    def "payable pay posts expense and debits treasury"() {
        given:
        treasury.ensureDefaultPositions('npub-ap')
        def p = payables.createPayable('npub-ap', [vendorName: 'Vendor', amountEur: 20G])

        when:
        def r = payables.recordPayment(p.id, 'npub-ap', [eventId: 'ap1'])

        then:
        r.payable.payableStatus == 'PAID'
        r.journalEntryId
        Treasury.findByNpubOwnerAndAccountKind('npub-ap', 'FIAT_BANK').balanceEur == -20G
    }

    def "budget variance runs"() {
        given:
        ledger.ensureChartSeeded('npub-bud')
        plan.createBudget('npub-bud', [period: '2026-07', amountEur: 500G, name: 'Ops'])

        when:
        Map v = plan.compareToActuals('npub-bud', '2026-07')

        then:
        v.budgetTotal == 500G
        v.period == '2026-07'
    }

    def "tax estimate still runs"() {
        given:
        ledger.ensureChartSeeded('npub-tx')
        revenue.createInvoice('npub-tx', [amountEur: 100G])
        // no settle → revenue 0 estimate
        when:
        def t = tax.estimatePeriodTax('npub-tx', '2026-07')

        then:
        t.taxStatus == 'ESTIMATE'
    }

    def "payroll recognize then pay posts 5200/2200 then clears payable to cash"() {
        given:
        treasury.ensureDefaultPositions('npub-pr')
        payroll.createLine('npub-pr', [period: '2026-09', personRef: 'alice', amountEur: 80G])
        payroll.createLine('npub-pr', [period: '2026-09', personRef: 'bob', amountEur: 20G])

        when:
        def rec = payroll.recognizePayroll('npub-pr', '2026-09')
        def pay = payroll.payPayroll('npub-pr', '2026-09')

        then:
        !rec.stub
        rec.action == 'recognize'
        rec.totalEur == 100G
        rec.journalEntryId
        JournalLine.findAllByJournalEntry(JournalEntry.get(rec.journalEntryId as Long))
                .any { it.account.code == '5200' && it.debitEur == 100G }
        JournalLine.findAllByJournalEntry(JournalEntry.get(rec.journalEntryId as Long))
                .any { it.account.code == '2200' && it.creditEur == 100G }

        pay.action == 'pay'
        pay.payJournalEntryId
        JournalLine.findAllByJournalEntry(JournalEntry.get(pay.payJournalEntryId as Long))
                .any { it.account.code == '2200' && it.debitEur == 100G }
        JournalLine.findAllByJournalEntry(JournalEntry.get(pay.payJournalEntryId as Long))
                .any { it.account.code == '1000' && it.creditEur == 100G }
        Treasury.findByNpubOwnerAndAccountKind('npub-pr', 'FIAT_BANK').balanceEur == -100G
        PayrollItem.findAllByNpubOwnerAndPeriod('npub-pr', '2026-09').every { it.payrollStatus == 'PAID' }
    }

    def "payroll pay without recognize is blocked"() {
        given:
        payroll.createLine('npub-pr2', [period: '2026-08', amountEur: 50G])

        when:
        payroll.payPayroll('npub-pr2', '2026-08')

        then:
        thrown(IllegalStateException)
    }

    def "control close period"() {
        when:
        control.lockPeriod('npub-c', '2026-05')

        then:
        control.periodStatus('npub-c', '2026-05').closed
    }
}
