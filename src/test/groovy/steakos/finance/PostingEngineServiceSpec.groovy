// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.finance

import grails.testing.gorm.DataTest
import steakos.configuration.CompanyConfigService
import steakos.configuration.CompanySetting
import spock.lang.Specification

/**
 * PDR-027 PR2 — engine happy path + D8 missing-rule block.
 */
class PostingEngineServiceSpec extends Specification implements DataTest {

    FinanceLedgerService ledger
    PostingRulesetService rulesetService
    PostingEngineService engine

    Class[] getDomainClassesToMock() {
        [Account, JournalEntry, JournalLine, CompanySetting] as Class[]
    }

    def setup() {
        ledger = new FinanceLedgerService()
        rulesetService = new PostingRulesetService(
                companyConfigService: new CompanyConfigService(),
                financeLedgerService: ledger
        )
        engine = new PostingEngineService(
                postingRulesetService: rulesetService,
                financeLedgerService: ledger
        )
    }

    def "invoice.settle posts Dr rail wallet Cr 4000 from SBM pack"() {
        given:
        String npub = 'npub-pe-settle'
        rulesetService.ensureSeeded(npub)

        when:
        JournalEntry je = engine.postEvent(npub, PostingEngineService.EVENT_INVOICE_SETTLE,
                ['payment.functional': 75G],
                [
                        name      : 'Pay INV-1 BTC_LIGHTNING',
                        sourceType: 'PAYMENT',
                        sourceId  : '1',
                        eventId   : 'pe-settle-1',
                        rail      : 'BTC_LIGHTNING',
                ])

        then:
        je.id
        JournalLine.findAllByJournalEntry(je).any { it.account.code == '1020' && it.debitEur == 75G }
        JournalLine.findAllByJournalEntry(je).any { it.account.code == '4000' && it.creditEur == 75G }
    }

    def "payable.pay posts Dr 5000 Cr 1000 from SBM pack"() {
        given:
        String npub = 'npub-pe-pay'
        rulesetService.ensureSeeded(npub)

        when:
        JournalEntry je = engine.postEvent(npub, PostingEngineService.EVENT_PAYABLE_PAY,
                ['payable.functional': 40G],
                [
                        name      : 'AP pay Vendor',
                        sourceType: 'PAYABLE',
                        sourceId  : '9',
                        eventId   : 'pe-pay-1',
                        rail      : 'SEPA',
                ])

        then:
        je.id
        JournalLine.findAllByJournalEntry(je).any { it.account.code == '5000' && it.debitEur == 40G }
        JournalLine.findAllByJournalEntry(je).any { it.account.code == '1000' && it.creditEur == 40G }
    }

    def "missing event blocks with POSTING_RULES_MISSING"() {
        given:
        String npub = 'npub-pe-miss'
        rulesetService.ensureSeeded(npub)
        Map pack = new LinkedHashMap(rulesetService.getRuleset(npub))
        Map events = new LinkedHashMap(pack.events as Map)
        events.remove('invoice.settle')
        pack.events = events
        rulesetService.saveRuleset(npub, [ruleset: pack])

        when:
        engine.postEvent(npub, PostingEngineService.EVENT_INVOICE_SETTLE,
                ['payment.functional': 10G],
                [sourceType: 'PAYMENT', sourceId: '1', eventId: 'pe-miss-1', rail: 'SEPA'])

        then:
        PostingRulesException ex = thrown()
        ex.code == PostingEngineService.CODE_MISSING
        JournalEntry.countByNpubOwner(npub) == 0
    }

    def "invalid single-leg event blocks with POSTING_RULES_INVALID"() {
        given:
        String npub = 'npub-pe-bad'
        rulesetService.ensureSeeded(npub)
        Map pack = new LinkedHashMap(rulesetService.getRuleset(npub))
        Map events = new LinkedHashMap(pack.events as Map)
        events['payable.pay'] = [legs: [[side: 'DEBIT', accountCode: '5000', amountFrom: 'payable.functional']]]
        pack.events = events
        // bypass validateStructure via direct put
        rulesetService.companyConfigService.putJson(npub, PostingRulesetService.KEY_RULES, pack, 'finance')

        when:
        engine.postEvent(npub, PostingEngineService.EVENT_PAYABLE_PAY,
                ['payable.functional': 5G],
                [sourceType: 'PAYABLE', sourceId: '2', eventId: 'pe-bad-1'])

        then:
        PostingRulesException ex = thrown()
        ex.code == PostingEngineService.CODE_INVALID
    }

    def "payroll.recognize posts Dr 5200 Cr 2200; skips optional tax when zero"() {
        given:
        String npub = 'npub-pe-pr'
        rulesetService.ensureSeeded(npub)

        when:
        JournalEntry je = engine.postEvent(npub, PostingEngineService.EVENT_PAYROLL_RECOGNIZE,
                ['payroll.gross': 100G, 'payroll.net': 100G, 'payroll.tax': 0G],
                [
                        name      : 'Payroll recognize 2026-09',
                        sourceType: 'PAYROLL',
                        sourceId  : '2026-09',
                        eventId   : 'pe-pr-1',
                        period    : '2026-09',
                ])

        then:
        je.id
        JournalLine.findAllByJournalEntry(je).any { it.account.code == '5200' && it.debitEur == 100G }
        JournalLine.findAllByJournalEntry(je).any { it.account.code == '2200' && it.creditEur == 100G }
        !JournalLine.findAllByJournalEntry(je).any { it.account.code == '2210' }
    }

    def "payroll.pay posts Dr 2200 Cr 1000 from SBM pack"() {
        given:
        String npub = 'npub-pe-pp'
        rulesetService.ensureSeeded(npub)

        when:
        JournalEntry je = engine.postEvent(npub, PostingEngineService.EVENT_PAYROLL_PAY,
                ['payroll.net': 100G],
                [
                        name      : 'Payroll pay 2026-09',
                        sourceType: 'PAYROLL',
                        sourceId  : '2026-09',
                        eventId   : 'pe-pp-1',
                        rail      : 'SEPA',
                ])

        then:
        je.id
        JournalLine.findAllByJournalEntry(je).any { it.account.code == '2200' && it.debitEur == 100G }
        JournalLine.findAllByJournalEntry(je).any { it.account.code == '1000' && it.creditEur == 100G }
    }
}
