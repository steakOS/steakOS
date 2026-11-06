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

package steakos.service

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints
import steakos.traits.Assignable

/**
 * Thin customer-facing return / RMA case (APQC 6.2.4, AS4 B).
 * Confirm → SCM stock; AfterSales does not own inventory ledger.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'rmaNumber')
@ToString(includes = 'rmaNumber,rmaStatus', includeNames = true, includePackage = false)
class Rma implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Assignable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String rmaNumber

	Long customerId
	String customerName
	Long salesOrderId
	Inquiry inquiry

	Long catalogueItemId
	String productName
	BigDecimal quantity = BigDecimal.ONE

	String reason
	/** Optional expected refund (AS7 thin) */
	BigDecimal expectedRefundEur
	Long expectedRefundSats
	String currencyCode = 'EUR'

	Date requestedAt
	Date confirmedAt
	Date closedAt

	/** StockMove id when confirm posts SCM receipt */
	Long stockMoveId

	/** REQUESTED | CONFIRMED | CLOSED | CANCELLED */
	String rmaStatus = 'REQUESTED'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAssignable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		rmaNumber nullable: true, maxSize: 64
		customerId nullable: true
		customerName nullable: true, maxSize: 255
		salesOrderId nullable: true
		inquiry nullable: true
		catalogueItemId nullable: true
		productName nullable: true, maxSize: 255
		quantity nullable: false, scale: 8
		reason nullable: true, maxSize: 2000
		expectedRefundEur nullable: true, scale: 8
		expectedRefundSats nullable: true
		currencyCode nullable: false, blank: false, maxSize: 8
		requestedAt nullable: true
		confirmedAt nullable: true
		closedAt nullable: true
		stockMoveId nullable: true
		rmaStatus nullable: false, blank: false, maxSize: 32,
				inList: ['REQUESTED', 'CONFIRMED', 'CLOSED', 'CANCELLED']
	}

	static mapping = {
		description type: 'text'
		reason type: 'text'
		quantity scale: 8
		expectedRefundEur scale: 8
		rmaNumber index: 'rma_number_idx'
		rmaStatus index: 'rma_status_idx'
	}
}
