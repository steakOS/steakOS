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

package steakos.payments

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.finance.*

class PaymentSettlementServiceSpec extends Specification implements DataTest {

    FinanceLedgerService ledger
    FinanceTreasuryService treasury
    PostingRulesetService rulesetService
    PostingEngineService engine
    FinanceRevenueService revenue
    PaymentProviderService providerService
    PaymentSettlementService settlement

    Class[] getDomainClassesToMock() {
        [Account, JournalEntry, JournalLine, Invoice, Payment, PaymentOption, Treasury] as Class[]
    }

    def setup() {
        ledger = new FinanceLedgerService()
        treasury = new FinanceTreasuryService(financeLedgerService: ledger)
        // PDR-027: settlement posts via PostingEngine (ephemeral SBM pack when no CompanyConfigService)
        rulesetService = new PostingRulesetService(financeLedgerService: ledger)
        engine = new PostingEngineService(postingRulesetService: rulesetService, financeLedgerService: ledger)
        revenue = new FinanceRevenueService(
                financeLedgerService: ledger,
                financeTreasuryService: treasury,
                postingEngineService: engine
        )
        providerService = new PaymentProviderService()
        providerService.paymentSettingsService = Stub(PaymentSettingsService) {
            resolvedProviderCode(_) >> PaymentSettingsService.PROVIDER_MOCK
        }
        providerService.mockProvider.reset()
        settlement = new PaymentSettlementService(
                financeRevenueService: revenue,
                paymentProviderService: providerService
        )
    }

    def "createInbound then webhook settles invoice idempotently"() {
        given:
        String npub = 'npub-pay'
        Invoice inv = revenue.createInvoice(npub, [amountEur: 50G, customerName: 'A'])
        revenue.issueInvoice(inv.id, npub)

        when:
        Map created = settlement.createInboundForInvoice(npub, inv.id, [amountEur: 50G])
        Map first = settlement.handleInboundWebhook(npub, [
                externalId: created.externalId,
                eventId   : 'evt-settle-1',
                rail      : 'BTC_LIGHTNING'
        ])
        Map second = settlement.handleInboundWebhook(npub, [
                externalId: created.externalId,
                eventId   : 'evt-settle-1',
                rail      : 'BTC_LIGHTNING'
        ])

        then:
        created.provider == 'MOCK'
        created.payMethods.size() == 2
        first.idempotent != true
        first.invoice.invoiceStatus == 'PAID' || first.invoice.status == 'PAID' ||
                Invoice.get(inv.id).invoiceStatus == 'PAID'
        second.idempotent == true
        Treasury.findByNpubOwnerAndAccountKind(npub, 'LN_WALLET').balanceEur == 50G
    }

    def "legacy webhook body with steakOS invoiceId still settles"() {
        given:
        String npub = 'npub-legacy'
        Invoice inv = revenue.createInvoice(npub, [amountEur: 12G])
        revenue.issueInvoice(inv.id, npub)

        when:
        Map r = settlement.handleInboundWebhook(npub, [
                invoiceId : inv.id,
                amountEur : 12G,
                amountSats: 1_200_000L,
                rail      : 'BTC_LIGHTNING',
                eventId   : 'legacy-1'
        ])

        then:
        r.payment
        Invoice.get(inv.id).invoiceStatus == 'PAID'
    }
}
