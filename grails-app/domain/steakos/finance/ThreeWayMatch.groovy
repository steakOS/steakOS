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

package steakos.finance

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints

/**
 * FinanceAdvanced light three-way match (OI-F21): PO ↔ goods receipt ↔ supplier invoice (Payable).
 * Header/amount-level; Payable has no lines — qty check uses PO/Receipt lines only.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'matchStatus,purchaseOrderId,payableId', includeNames = true, includePackage = false)
class ThreeWayMatch implements NpubOwned, AccessControl, Classifiable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** steakos.procurement.PurchaseOrder.id */
	Long purchaseOrderId

	/** steakos.procurement.Receipt.id */
	Long receiptId

	/** steakos.finance.Payable.id — supplier invoice stand-in */
	Long payableId

	/** PROPOSED | CONFIRMED | REJECTED | CANCELLED */
	String matchStatus = 'PROPOSED'

	BigDecimal amountPoEur
	BigDecimal amountPayableEur
	BigDecimal varianceEur

	Boolean amountOk
	Boolean qtyOk

	Date proposedAt
	Date confirmedAt
	String confirmedByNpub

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		purchaseOrderId nullable: false
		receiptId nullable: false
		payableId nullable: false
		matchStatus nullable: false, blank: false, maxSize: 32,
				inList: ['PROPOSED', 'CONFIRMED', 'REJECTED', 'CANCELLED']
		amountPoEur nullable: true, scale: 8
		amountPayableEur nullable: true, scale: 8
		varianceEur nullable: true, scale: 8
		amountOk nullable: true
		qtyOk nullable: true
		proposedAt nullable: false
		confirmedAt nullable: true
		confirmedByNpub nullable: true, maxSize: 128
	}

	static mapping = {
		description type: 'text'
		purchaseOrderId index: 'three_way_match_po_idx'
		receiptId index: 'three_way_match_receipt_idx'
		payableId index: 'three_way_match_payable_idx'
		matchStatus index: 'three_way_match_status_idx'
		amountPoEur scale: 8
		amountPayableEur scale: 8
		varianceEur scale: 8
	}

	def beforeValidate() {
		if (!proposedAt) {
			proposedAt = new Date()
		}
		if (!name) {
			name = "Match PO-${purchaseOrderId ?: '?'} / AP-${payableId ?: '?'}"
		}
		if (!status) {
			status = matchStatus
		}
	}
}
