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

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.finance.PaymentOption

/**
 * Safety net when webhooks are missed (OI-F01 Phase 1).
 */
@Slf4j
class PaymentReconcileService {

    PaymentSettlementService paymentSettlementService

    @Transactional
    Map reconcileOpen(String npubOwner, int max = 50) {
        String npub = npubOwner?.trim()
        if (!npub) throw new IllegalArgumentException('npubOwner is required')

        List<PaymentOption> all = PaymentOption.findAllByNpubOwner(npub, [max: 500])
        List<PaymentOption> opts = all.findAll { PaymentOption o ->
            o.externalInvoiceId &&
                    o.active != Boolean.FALSE &&
                    o.invoice &&
                    (o.invoice.invoiceStatus in ['ISSUED', 'PART_PAID'])
        }.take(max)

        List results = []
        int settled = 0
        opts.each { PaymentOption o ->
            try {
                Map r = paymentSettlementService.reconcileExternal(
                        npub, o.externalInvoiceId, o.invoice?.id
                )
                if (r.settled || r.payment) settled++
                results << [externalId: o.externalInvoiceId, invoiceId: o.invoice?.id, result: r]
            } catch (Exception e) {
                log.warn('reconcile failed {} : {}', o.externalInvoiceId, e.message)
                results << [externalId: o.externalInvoiceId, error: e.message]
            }
        }
        [npubOwner: npub, scanned: opts.size(), settled: settled, items: results]
    }
}
