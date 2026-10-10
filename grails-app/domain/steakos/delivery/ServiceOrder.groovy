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

package steakos.delivery

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.Versioned
import steakos.traits.NostrPublishable
import steakos.traits.DynamicExtensible
import steakos.traits.DomainTraitConstraints

/**
 * Service fulfillment order (D-S0 / APQC 5.4). Lines via {@link ServiceOrderLine}.
 * D3: no Finance invoice from Delivery. D4: single optional resource.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'orderNumber')
@ToString(includes = 'orderNumber,fulfillStatus', includeNames = true, includePackage = false)
class ServiceOrder implements NpubOwned, AccessControl, Classifiable, Attachable,
		Auditable, Versioned, NostrPublishable, DynamicExtensible, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String orderNumber

	/** Optional default catalogue SERVICE item */
	Long catalogueItemId

	/** Customer (sales.Customer id — no hard GORM link required) */
	Long customerId
	String customerName

	/** D2: sales demand link */
	Long sourceSalesOrderId

	/** D4: single assignee */
	Resource resource

	Date scheduledAt
	Date startedAt
	Date completedAt

	BigDecimal amountEur = BigDecimal.ZERO
	Long amountSats
	String currencyCode = 'EUR'

	/** DRAFT | SCHEDULED | IN_PROGRESS | COMPLETED | CANCELLED */
	String fulfillStatus = 'DRAFT'

	static hasMany = [lines: ServiceOrderLine]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)
		DomainTraitConstraints.applyNostrPublishable(delegate)
		DomainTraitConstraints.applyDynamicExtensible(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		orderNumber nullable: true, maxSize: 64
		catalogueItemId nullable: true
		customerId nullable: true
		customerName nullable: true, maxSize: 255
		sourceSalesOrderId nullable: true
		resource nullable: true
		scheduledAt nullable: true
		startedAt nullable: true
		completedAt nullable: true
		amountEur nullable: false, scale: 8
		amountSats nullable: true
		currencyCode nullable: false, blank: false, maxSize: 8
		fulfillStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']
	}

	static mapping = {
		description type: 'text'
		amountEur scale: 8
		orderNumber index: 'service_order_number_idx'
		fulfillStatus index: 'service_order_status_idx'
	}
}
