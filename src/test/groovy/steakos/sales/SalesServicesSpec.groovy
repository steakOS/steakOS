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

package steakos.sales

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.finance.Account
import steakos.finance.FinanceLedgerService
import steakos.finance.FinanceRevenueService
import steakos.finance.Invoice
import steakos.finance.JournalEntry
import steakos.finance.JournalLine
import steakos.finance.Payment
import steakos.finance.PaymentOption

/**
 * S-S0–S-S3 commercial loop: customer → quote(+lines) → accept→order → confirm→DRAFT invoice.
 */
class SalesServicesSpec extends Specification implements DataTest {

    SalesCustomerService customerService
    SalesQuoteService quoteService
    SalesOrderService orderService
    FinanceRevenueService revenueService
    FinanceLedgerService ledgerService

    Class[] getDomainClassesToMock() {
        [
                Customer, CustomerQuote, QuoteLine, SalesOrder, OrderLine,
                Account, JournalEntry, JournalLine, Invoice, Payment, PaymentOption
        ] as Class[]
    }

    def setup() {
        customerService = new SalesCustomerService()
        ledgerService = new FinanceLedgerService()
        revenueService = new FinanceRevenueService(financeLedgerService: ledgerService)
        orderService = new SalesOrderService(
                salesCustomerService: customerService,
                financeRevenueService: revenueService
        )
        quoteService = new SalesQuoteService(
                salesCustomerService: customerService,
                salesOrderService: orderService
        )
    }

    def "create customer"() {
        when:
        Customer c = customerService.createCustomer('npub-sales', [
                name        : 'Acme GmbH',
                email       : 'a@acme.test',
                customerKind: 'ORGANIZATION'
        ])

        then:
        c.id
        c.name == 'Acme GmbH'
        c.currencyCode == 'EUR'
        c.active
    }

    def "quote lines recompute header total EUR (PDR-023: no sats on commercial docs)"() {
        given:
        Customer c = customerService.createCustomer('npub-q', [name: 'Buyer'])

        when:
        CustomerQuote q = quoteService.createQuote('npub-q', [
                customerId: c.id,
                lines     : [
                        [name: 'Day rate', quantity: 2, unitPriceEur: 500G, unitPriceSats: 1000L],
                        [name: 'Addon', quantity: 1, unitPriceEur: 50G]
                ]
        ])

        then:
        q.quoteStatus == 'DRAFT'
        q.quoteNumber
        q.amountEur == 1050G
        q.netAmountEur == 1050G
        (q.taxAmountEur ?: 0G) == 0G
        q.amountSats == null
        QuoteLine.countByQuote(q) == 2
    }

    def "accept quote creates DRAFT order with copied lines"() {
        given:
        Customer c = customerService.createCustomer('npub-a', [name: 'Client Co'])
        CustomerQuote q = quoteService.createQuote('npub-a', [
                customerId: c.id,
                lines     : [[name: 'Widget', quantity: 3, unitPriceEur: 10G]]
        ])
        quoteService.sendQuote(q.id, 'npub-a')

        when:
        Map result = quoteService.acceptQuote(q.id, 'npub-a')

        then:
        result.alreadyAccepted == false
        result.quote.quoteStatus == 'ACCEPTED'
        result.order.orderStatus == 'DRAFT'
        result.order.amountEur == 30G
        result.order.lines.size() == 1
        CustomerQuote.get(q.id).resultingOrderId == result.order.id
    }

    def "confirm order creates Finance DRAFT invoice"() {
        given:
        Customer c = customerService.createCustomer('npub-o', [name: 'Payor'])
        SalesOrder order = orderService.createOrder('npub-o', [
                customerId: c.id,
                lines     : [[name: 'Service', quantity: 1, unitPriceEur: 250G, unitPriceSats: 50000L]]
        ])

        when:
        Map result = orderService.confirmOrder(order.id, 'npub-o')

        then:
        result.alreadyConfirmed == false
        result.order.orderStatus == 'CONFIRMED'
        result.financeInvoiceId
        Invoice inv = Invoice.get(result.financeInvoiceId)
        inv.invoiceStatus == 'DRAFT'
        inv.amountEur == 250G
        inv.amountSats == null
        inv.referenceText.contains(order.orderNumber)
        SalesOrder.get(order.id).financeInvoiceId == inv.id
    }

    def "confirm is idempotent when already confirmed"() {
        given:
        SalesOrder order = orderService.createOrder('npub-id', [
                lines: [[name: 'X', quantity: 1, unitPriceEur: 10G]]
        ])
        Map first = orderService.confirmOrder(order.id, 'npub-id')

        when:
        Map second = orderService.confirmOrder(order.id, 'npub-id')

        then:
        second.alreadyConfirmed == true
        second.financeInvoiceId == first.financeInvoiceId
        Invoice.count() == 1
    }

    def "cannot confirm zero-total order"() {
        when:
        SalesOrder order = orderService.createOrder('npub-z', [name: 'Empty'])
        orderService.confirmOrder(order.id, 'npub-z')

        then:
        thrown(IllegalStateException)
    }
}
