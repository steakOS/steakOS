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

package steakos.sales

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
 * Customer commercial quote (S-S0/S-S2). Lines via {@link QuoteLine}.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'quoteNumber')
@ToString(includes = 'quoteNumber,name,quoteStatus', includeNames = true, includePackage = false)
class CustomerQuote implements NpubOwned, AccessControl, Classifiable, Attachable,
		Auditable, Versioned, NostrPublishable, DynamicExtensible, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String quoteNumber

	Customer customer
	String customerName

	Date issueDate
	Date validUntil

	/** Gross total when tax present (net + tax); else net (OI-F23) */
	BigDecimal amountEur = BigDecimal.ZERO
	/** Sum of line nets */
	BigDecimal netAmountEur
	/** Sum of line tax */
	BigDecimal taxAmountEur
	Long amountSats
	String currencyCode = 'EUR'

	/** Frozen partner locale at send/issue (OI-I18N02 D4) */
	String documentLocale

	/** DRAFT | SENT | ACCEPTED | REJECTED | EXPIRED */
	String quoteStatus = 'DRAFT'

	Long resultingOrderId

	static hasMany = [lines: QuoteLine]

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
		quoteNumber nullable: true, maxSize: 64
		customer nullable: true
		customerName nullable: true, maxSize: 255
		issueDate nullable: true
		validUntil nullable: true
		amountEur nullable: false, scale: 8
		netAmountEur nullable: true, scale: 8
		taxAmountEur nullable: true, scale: 8
		amountSats nullable: true
		currencyCode nullable: false, blank: false, maxSize: 8
		documentLocale nullable: true, maxSize: 16
		quoteStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED']
		resultingOrderId nullable: true
	}

	static mapping = {
		description type: 'text'
		amountEur scale: 8
		netAmountEur scale: 8
		taxAmountEur scale: 8
		quoteNumber index: 'quote_number_idx'
		quoteStatus index: 'quote_status_idx'
	}
}
