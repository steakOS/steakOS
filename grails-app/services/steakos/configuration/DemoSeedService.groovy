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

package steakos.configuration

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.catalogue.CatalogueItem
import steakos.catalogue.CatalogueService
import steakos.core.Task
import steakos.finance.FinanceLedgerService
import steakos.finance.FinanceTreasuryService
import steakos.procurement.Vendor
import steakos.sales.Customer

/**
 * Iron demo seed (IRON-COMPLETE IR-05): sample customers, vendors, catalogue, treasury, task.
 * Idempotent-ish: skips when a marker customer already exists for the tenant.
 */
@Slf4j
class DemoSeedService {

    static final String MARKER_CUSTOMER = 'Iron Demo Customer'

    CatalogueService catalogueService
    FinanceLedgerService financeLedgerService
    FinanceTreasuryService financeTreasuryService

    @Transactional
    Map seedDemo(String npubOwner) {
        String npub = npubOwner?.trim() ?: SetupService.DEFAULT_NPUB
        if (Customer.findByNpubOwnerAndName(npub, MARKER_CUSTOMER)) {
            return [
                    npubOwner: npub,
                    skipped  : true,
                    message  : 'Demo seed already present (Iron Demo Customer exists)',
            ]
        }

        if (financeLedgerService) {
            financeLedgerService.ensureChartSeeded(npub)
        }
        if (financeTreasuryService) {
            financeTreasuryService.ensureDefaultPositions(npub)
        }

        Customer c1 = new Customer(
                npubOwner: npub,
                name: MARKER_CUSTOMER,
                email: 'demo@example.com',
                customerKind: 'ORGANIZATION',
                active: Boolean.TRUE
        )
        c1.save(failOnError: true)

        Customer c2 = new Customer(
                npubOwner: npub,
                name: 'Iron Demo Retail',
                email: 'retail@example.com',
                customerKind: 'ORGANIZATION',
                active: Boolean.TRUE
        )
        c2.save(failOnError: true)

        Vendor v1 = new Vendor(
                npubOwner: npub,
                name: 'Iron Demo Vendor',
                email: 'vendor@example.com',
                active: Boolean.TRUE
        )
        v1.save(failOnError: true)

        CatalogueItem goods = catalogueService.create(npub, [
                name      : 'Iron Demo Widget',
                code      : 'IRON-WIDGET',
                nature    : CatalogueItem.NATURE_GOODS,
                unit      : 'ea',
                listPrice : new BigDecimal('49.00'),
                canBeSold : true,
                canBeBought: true,
                active    : true,
        ])
        CatalogueItem svc = catalogueService.create(npub, [
                name      : 'Iron Demo Support Hour',
                code      : 'IRON-SVC-H',
                nature    : CatalogueItem.NATURE_SERVICE,
                unit      : 'h',
                listPrice : new BigDecimal('95.00'),
                canBeSold : true,
                canBeBought: false,
                active    : true,
        ])

        Task task = new Task(
                npubOwner: npub,
                name: 'Iron demo: complete golden path',
                description: 'Try quote → order → invoice → Mock settle and a PO receive.',
                status: 'OPEN',
                urgency: 2,
                importance: 3
        )
        task.save(failOnError: true)

        log.info('Demo seed applied for {}', npub)
        [
                npubOwner   : npub,
                skipped     : false,
                customers   : 2,
                vendors     : 1,
                catalogue   : 2,
                tasks       : 1,
                customerIds : [c1.id, c2.id],
                vendorId    : v1.id,
                catalogueIds: [goods?.id, svc?.id],
                taskId      : task.id,
                message     : 'Demo seed created',
        ]
    }
}
