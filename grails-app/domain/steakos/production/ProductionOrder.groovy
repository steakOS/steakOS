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

package steakos.production

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.Versioned
import steakos.traits.DomainTraitConstraints

/**
 * Production / make order (SC-S0 / APQC 4.3.1–4.3.2).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'orderNumber')
@ToString(includes = 'orderNumber,prodStatus', includeNames = true, includePackage = false)
class ProductionOrder implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Versioned, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String orderNumber

	/** What to make — catalogue GOODS or SERVICE (effort) */
	steakos.catalogue.CatalogueItem catalogueItem
	BigDecimal quantity = BigDecimal.ONE
	String unit = 'EA'

	Date scheduledStart
	Date scheduledEnd
	Date completedAt

	/** DRAFT | RELEASED | IN_PROGRESS | COMPLETED | CANCELLED */
	String prodStatus = 'DRAFT'

	/** SC6: optional demand link from sales */
	Long sourceSalesOrderId

	Long resultingLotId

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		orderNumber nullable: true, maxSize: 64
		catalogueItem nullable: false
		quantity nullable: false, scale: 8, min: 0.0G
		unit nullable: false, blank: false, maxSize: 16
		scheduledStart nullable: true
		scheduledEnd nullable: true
		completedAt nullable: true
		prodStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'RELEASED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']
		sourceSalesOrderId nullable: true
		resultingLotId nullable: true
	}

	static mapping = {
		description type: 'text'
		quantity scale: 8
		orderNumber index: 'prod_order_number_idx'
		prodStatus index: 'prod_status_idx'
	}
}
