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
import steakos.traits.Auditable
import steakos.traits.Versioned
import steakos.traits.NostrPublishable
import steakos.traits.DynamicExtensible
import steakos.traits.DomainTraitConstraints

/**
 * Purchase order (SC-S0 / APQC 4.2.4). Lines via {@link PurchaseOrderLine}.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'poNumber')
@ToString(includes = 'poNumber,name,poStatus', includeNames = true, includePackage = false)
class PurchaseOrder implements NpubOwned, AccessControl, Classifiable, Attachable,
		Auditable, Versioned, NostrPublishable, DynamicExtensible, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String poNumber

	Vendor vendor
	String vendorName

	Date orderDate
	Date confirmedAt

	BigDecimal amountEur = BigDecimal.ZERO
	Long amountSats
	String currencyCode = 'EUR'

	/** Frozen partner locale at send/issue (OI-I18N02 D4) */
	String documentLocale

	/** DRAFT | SENT | CONFIRMED | PART_RECEIVED | RECEIVED | CANCELLED */
	String poStatus = 'DRAFT'

	/** Finance payable id after confirm (SC7) */
	Long financePayableId

	static hasMany = [lines: PurchaseOrderLine]

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
		poNumber nullable: true, maxSize: 64
		vendor nullable: true
		vendorName nullable: true, maxSize: 255
		orderDate nullable: true
		confirmedAt nullable: true
		amountEur nullable: false, scale: 8
		amountSats nullable: true
		currencyCode nullable: false, blank: false, maxSize: 8
		documentLocale nullable: true, maxSize: 16
		poStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'SENT', 'CONFIRMED', 'PART_RECEIVED', 'RECEIVED', 'CANCELLED']
		financePayableId nullable: true
	}

	static mapping = {
		description type: 'text'
		amountEur scale: 8
		poNumber index: 'po_number_idx'
		poStatus index: 'po_status_idx'
	}
}
