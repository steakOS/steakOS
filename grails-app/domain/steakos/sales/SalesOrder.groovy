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
 * Sales order capture (S-S0/S-S3). Lines via {@link OrderLine}.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'orderNumber')
@ToString(includes = 'orderNumber,name,orderStatus', includeNames = true, includePackage = false)
class SalesOrder implements NpubOwned, AccessControl, Classifiable, Attachable,
		Auditable, Versioned, NostrPublishable, DynamicExtensible, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String orderNumber

	Customer customer
	String customerName

	CustomerQuote sourceQuote

	Date orderDate
	Date confirmedAt

	/** Gross total when tax present (net + tax); else net (OI-F23) */
	BigDecimal amountEur = BigDecimal.ZERO
	BigDecimal netAmountEur
	BigDecimal taxAmountEur
	Long amountSats
	String currencyCode = 'EUR'

	/** Frozen partner locale at confirm (OI-I18N02 D4) */
	String documentLocale

	/** DRAFT | CONFIRMED | CLOSED | CANCELLED */
	String orderStatus = 'DRAFT'

	/** Finance invoice id after confirm (SS3) */
	Long financeInvoiceId

	static hasMany = [lines: OrderLine]

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
		customer nullable: true
		customerName nullable: true, maxSize: 255
		sourceQuote nullable: true
		orderDate nullable: true
		confirmedAt nullable: true
		amountEur nullable: false, scale: 8
		netAmountEur nullable: true, scale: 8
		taxAmountEur nullable: true, scale: 8
		amountSats nullable: true
		currencyCode nullable: false, blank: false, maxSize: 8
		documentLocale nullable: true, maxSize: 16
		orderStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'CONFIRMED', 'CLOSED', 'CANCELLED']
		financeInvoiceId nullable: true
	}

	static mapping = {
		description type: 'text'
		amountEur scale: 8
		netAmountEur scale: 8
		taxAmountEur scale: 8
		orderNumber index: 'order_number_idx'
		orderStatus index: 'order_status_idx'
	}
}
