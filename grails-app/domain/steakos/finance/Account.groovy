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
import steakos.traits.DomainTraitConstraints

/**
 * Chart of accounts entry (ADR-009, D6, F-S0).
 * Functional currency books use EUR (FS2); accountType guides posting.
 * See docs/services/steakos app/finance/FINANCE-SERVICES-CONCEPT.md
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'code')
@ToString(includes = 'code,name', includeNames = true, includePackage = false)
class Account implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Versioned, Serializable {

	private static final long serialVersionUID = 1L

	/** COA code e.g. 1000, 1100, 4000 */
	String code

	String name
	String description

	/**
	 * ASSET | LIABILITY | EQUITY | REVENUE | EXPENSE
	 * (also used for ADR-009 wallet kinds via subType where needed)
	 */
	String accountType

	/**
	 * Optional subtype: BTC_WALLET, LN_WALLET, FIAT_BRIDGE, AR, AP, …
	 */
	String accountSubType

	/** ISO currency for this account; functional books default EUR (FS2) */
	String currencyCode = 'EUR'

	/** Parent COA code for hierarchy (string to keep seed simple) */
	String parentCode

	/** System-seeded accounts must not be deleted casually */
	Boolean systemAccount = Boolean.FALSE

	Boolean active = Boolean.TRUE

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)

		code nullable: false, blank: false, maxSize: 32
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		accountType nullable: false, blank: false, maxSize: 32,
				inList: ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE']
		accountSubType nullable: true, maxSize: 64
		currencyCode nullable: false, blank: false, maxSize: 8
		parentCode nullable: true, maxSize: 32
		systemAccount nullable: true
		active nullable: true
	}

	static mapping = {
		description type: 'text'
		code index: 'account_code_idx'
	}
}
