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
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.Versioned
import steakos.traits.NostrPublishable
import steakos.traits.DynamicExtensible
import steakos.traits.DomainTraitConstraints

/**
 * Accounts payable / expense (F-S0 light properties; service wave later).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,payableStatus', includeNames = true, includePackage = false)
class Payable implements NpubOwned, AccessControl, Classifiable, Attachable,
		Auditable, Versioned, NostrPublishable, DynamicExtensible, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	Long vendorId
	String vendorName

	BigDecimal amountEur
	Long amountSats
	String currencyCode = 'EUR'

	Date issueDate
	Date dueDate
	Date paidAt

	/** DRAFT | OPEN | PAID | VOID */
	String payableStatus = 'DRAFT'

	Long journalEntryId

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
		vendorId nullable: true
		vendorName nullable: true, maxSize: 255
		amountEur nullable: false, scale: 8, min: 0.0G
		amountSats nullable: true, min: 0L
		currencyCode nullable: false, blank: false, maxSize: 8
		issueDate nullable: true
		dueDate nullable: true
		paidAt nullable: true
		payableStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'OPEN', 'PAID', 'VOID']
		journalEntryId nullable: true
	}

	static mapping = {
		description type: 'text'
		amountEur scale: 8
	}
}
