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

package steakos.procurement

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.DomainTraitConstraints

/**
 * Simple PO line (SC1) — no configurator.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,lineNo,lineTotalEur', includeNames = true, includePackage = false)
class PurchaseOrderLine implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	PurchaseOrder purchaseOrder
	Integer lineNo = 1

	BigDecimal quantity = BigDecimal.ONE
	BigDecimal unitPriceEur = BigDecimal.ZERO
	BigDecimal lineTotalEur = BigDecimal.ZERO

	Long unitPriceSats
	Long lineTotalSats

	Long productRefId
	String productRefType
	/** Unified catalogue master (CATALOGUE-ITEM-CONCEPT) */
	Long catalogueItemId

	/** Qty already received against this line */
	BigDecimal quantityReceived = BigDecimal.ZERO

	/** Input VAT on PO line (OI-F23 / L14) — net is lineTotalEur */
	String taxCode
	BigDecimal taxRatePercent
	BigDecimal taxAmountEur = BigDecimal.ZERO

	static belongsTo = [purchaseOrder: PurchaseOrder]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		purchaseOrder nullable: false
		lineNo nullable: false, min: 1
		quantity nullable: false, scale: 8, min: 0.0G
		unitPriceEur nullable: false, scale: 8
		lineTotalEur nullable: false, scale: 8
		unitPriceSats nullable: true
		lineTotalSats nullable: true
		productRefId nullable: true
		productRefType nullable: true, maxSize: 64
		catalogueItemId nullable: true
		quantityReceived nullable: false, scale: 8, min: 0.0G
		taxCode nullable: true, maxSize: 32
		taxRatePercent nullable: true, scale: 4
		taxAmountEur nullable: true, scale: 8
	}

	static mapping = {
		description type: 'text'
		quantity scale: 8
		unitPriceEur scale: 8
		lineTotalEur scale: 8
		quantityReceived scale: 8
		taxRatePercent scale: 4
		taxAmountEur scale: 8
	}
}
