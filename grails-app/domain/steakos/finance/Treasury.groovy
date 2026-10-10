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
import steakos.traits.DynamicExtensible
import steakos.traits.DomainTraitConstraints

/**
 * Treasury position: cash / BTC / LN / fiat bridge (ADR-009, F-S0).
 * Not full bank treasury (hedging L5 stays Pro).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,accountKind', includeNames = true, includePackage = false)
class Treasury implements NpubOwned, AccessControl, Classifiable, Attachable,
		Auditable, Versioned, DynamicExtensible, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/**
	 * FIAT_BANK | BTC_WALLET | LN_WALLET | FIAT_BRIDGE | OTHER
	 */
	String accountKind = 'FIAT_BANK'

	String currencyCode = 'EUR'

	BigDecimal balanceEur = BigDecimal.ZERO
	Long balanceSats = 0L

	/** Link to COA Account id when mirrored */
	Long ledgerAccountId

	/** External wallet/store id (BTCPay store, IBAN label, …) */
	String externalRef

	Boolean active = Boolean.TRUE

	Date lastReconciledAt

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)
		DomainTraitConstraints.applyDynamicExtensible(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		accountKind nullable: false, blank: false, maxSize: 32,
				inList: ['FIAT_BANK', 'BTC_WALLET', 'LN_WALLET', 'FIAT_BRIDGE', 'OTHER']
		currencyCode nullable: false, blank: false, maxSize: 8
		balanceEur nullable: false, scale: 8
		balanceSats nullable: false
		ledgerAccountId nullable: true
		externalRef nullable: true, maxSize: 256
		active nullable: true
		lastReconciledAt nullable: true
	}

	static mapping = {
		description type: 'text'
		balanceEur scale: 8
		accountKind index: 'treasury_kind_idx'
	}
}
