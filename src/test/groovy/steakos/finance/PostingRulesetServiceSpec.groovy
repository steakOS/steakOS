// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.finance

import spock.lang.Specification

class PostingRulesetServiceSpec extends Specification {

    void 'default SBM pack has required events and payroll COA refs'() {
        when:
        Map pack = PostingRulesetService.defaultSbmPack()

        then:
        pack.version == PostingRulesetService.RULES_VERSION
        pack.accountingMode == PostingRulesetService.MODE_CASH
        pack.events['invoice.settle'].legs.size() >= 2
        pack.events['payable.pay'].legs.size() >= 2
        pack.events['payroll.recognize'].legs.size() >= 2
        pack.events['payroll.pay'].legs.size() >= 2
        pack.railWallet.SEPA == '1000'
        pack.events['payroll.recognize'].legs.any { it.accountCode == '5200' }
        pack.events['payroll.recognize'].legs.any { it.accountCode == '2200' }
        pack.events['payroll.pay'].legs.any { it.accountCode == '2200' }
    }

    void 'default pack includes ACCRUAL stub events with AR/AP codes'() {
        when:
        Map pack = PostingRulesetService.defaultSbmPack()

        then:
        pack.events['invoice.issue'].stub == true
        pack.events['invoice.issue'].legs.any { it.accountCode == '1100' }
        pack.events['payable.create'].stub == true
        pack.events['payable.create'].legs.any { it.accountCode == '2000' }
        PostingRulesetService.validateStructureStatic(pack)
    }

    void 'validateStructure rejects single-leg event'() {
        given:
        Map bad = PostingRulesetService.defaultSbmPack()
        bad.events['invoice.settle'] = [legs: [[side: 'DEBIT', accountCode: '1000', amountFrom: 'x']]]

        when:
        PostingRulesetService.validateStructureStatic(bad)

        then:
        thrown(IllegalArgumentException)
    }

    void 'walletAccountForRail reads rail map'() {
        given:
        Map pack = PostingRulesetService.defaultSbmPack()

        expect:
        PostingRulesetService.walletAccountForRailStatic(pack, 'BTC_LIGHTNING') == '1020'
        PostingRulesetService.walletAccountForRailStatic(pack, 'UNKNOWN') == '1000'
    }
}
