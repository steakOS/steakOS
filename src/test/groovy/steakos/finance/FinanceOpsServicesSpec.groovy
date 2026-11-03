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
import steakos.configuration.CompanyConfigService
import steakos.configuration.CompanySetting
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
         Treasury, Payable, Budget, Tax, PayrollItem, CompanySetting] as Class[]
    }

    def setup() {
        ledger = new FinanceLedgerService()
        treasury = new FinanceTreasuryService(financeLedgerService: ledger)
        rulesetService = new PostingRulesetService(
                companyConfigService: new CompanyConfigService(),
                financeLedgerService: ledger)
        engine = new PostingEngineService(postingRulesetService: rulesetService, financeLedgerService: ledger)
        payables = new FinancePayableService(
                financeLedgerService: ledger,
                financeTreasuryService: treasury,
                postingEngineService: engine,
                postingRulesetService: rulesetService)
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
                postingEngineService: engine,
                postingRulesetService: rulesetService)
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
        !p.createJournalEntryId
        Treasury.findByNpubOwnerAndAccountKind('npub-ap', 'FIAT_BANK').balanceEur == -20G
    }

    def "CASH payable create has no create journal"() {
        given:
        rulesetService.ensureSeeded('npub-ap-cash')
        rulesetService.saveAccountingMode('npub-ap-cash', PostingRulesetService.MODE_CASH)

        when:
        def p = payables.createPayable('npub-ap-cash', [vendorName: 'CashVendor', amountEur: 15G])

        then:
        p.payableStatus == 'OPEN'
        !p.createJournalEntryId
    }

    def "ACCRUAL payable create posts Dr expense Cr AP; pay clears AP"() {
        given:
        rulesetService.ensureSeeded('npub-ap-acc')
        rulesetService.saveAccountingMode('npub-ap-acc', PostingRulesetService.MODE_ACCRUAL)
        treasury.ensureDefaultPositions('npub-ap-acc')

        when:
        def p = payables.createPayable('npub-ap-acc', [vendorName: 'AccVendor', amountEur: 30G])

        then:
        p.createJournalEntryId
        JournalLine.findAllByJournalEntry(JournalEntry.get(p.createJournalEntryId))
                .any { it.account.code == '5000' && it.debitEur == 30G }
        JournalLine.findAllByJournalEntry(JournalEntry.get(p.createJournalEntryId))
                .any { it.account.code == '2000' && it.creditEur == 30G }

        when:
        def r = payables.recordPayment(p.id, 'npub-ap-acc', [eventId: 'ap-acc-1'])

        then:
        r.journalEntryId
        JournalLine.findAllByJournalEntry(JournalEntry.get(r.journalEntryId as Long))
                .any { it.account.code == '2000' && it.debitEur == 30G }
        !JournalLine.findAllByJournalEntry(JournalEntry.get(r.journalEntryId as Long))
                .any { it.account.code == '5000' }
    }

    def "ACCRUAL approve DRAFT payable posts create journal"() {
        given:
        rulesetService.ensureSeeded('npub-ap-apr')
        rulesetService.saveAccountingMode('npub-ap-apr', PostingRulesetService.MODE_ACCRUAL)

        when:
        def p = payables.createPayable('npub-ap-apr', [
                vendorName: 'DraftVendor', amountEur: 12G, payableStatus: 'DRAFT'])

        then:
        p.payableStatus == 'DRAFT'
        !p.createJournalEntryId

        when:
        p = payables.approvePayable(p.id, 'npub-ap-apr')

        then:
        p.payableStatus == 'OPEN'
        p.createJournalEntryId
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

    def "period tax sums invoice tax when pack in use (D10 / L14)"() {
        given:
        def companyTax = Stub(steakos.configuration.CompanyTaxService) {
            companyHasLineTax('npub-d10') >> true
        }
        tax.companyTaxService = companyTax
        Calendar cal = Calendar.getInstance()
        cal.set(2026, Calendar.OCTOBER, 5, 12, 0, 0)
        Date issue = cal.time
        revenue.createInvoice('npub-d10', [
                amountEur   : 119G,
                netAmountEur: 100G,
                taxAmountEur: 19G,
                issueDate   : issue
        ])
        revenue.createInvoice('npub-d10', [
                amountEur   : 214G,
                netAmountEur: 200G,
                taxAmountEur: 14G,
                issueDate   : issue
        ])

        when:
        def t = tax.estimatePeriodTax('npub-d10', '2026-10')

        then:
        t.amountEur == 33.00G
        t.description.toString().contains('Sum of invoice tax')
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
