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

package steakos.supplychain

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints

/**
 * Inventory ledger line (SC2). onHand = sum(qtyDelta). No warehouse/bin.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'catalogueItemId,qtyDelta,reason', includeNames = true, includePackage = false)
class StockMove implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** Inventory key = catalogue GOODS id */
	Long catalogueItemId
	BigDecimal qtyDelta = BigDecimal.ZERO

	/** RECEIPT | PRODUCTION | ADJUST | ISSUE */
	String reason

	String sourceType
	String sourceId

	Long lotId
	Date movedAt

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		catalogueItemId nullable: false
		qtyDelta nullable: false, scale: 8
		reason nullable: false, blank: false, maxSize: 32,
				inList: ['RECEIPT', 'PRODUCTION', 'ADJUST', 'ISSUE']
		sourceType nullable: true, maxSize: 64
		sourceId nullable: true, maxSize: 64
		lotId nullable: true
		movedAt nullable: true
	}

	static mapping = {
		description type: 'text'
		qtyDelta scale: 8
		catalogueItemId index: 'stock_move_catalogue_idx'
		reason index: 'stock_move_reason_idx'
	}
}
