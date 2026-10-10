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
import steakos.traits.Versioned
import steakos.traits.DomainTraitConstraints

/**
 * Warranty claim (APQC 6.3.2). AS2: warranty required. AS7: dual EUR + sats.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'claimNumber')
@ToString(includes = 'claimNumber,claimStatus', includeNames = true, includePackage = false)
class Claims implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Versioned, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String claimNumber

	/** AS2: required cover */
	Warranty warranty
	Inquiry inquiry

	BigDecimal claimAmountEur = BigDecimal.ZERO
	Long claimAmountSats
	String currencyCode = 'EUR'

	String investigationNotes
	Boolean needsFieldVisit = Boolean.FALSE

	Date receivedAt
	Date validatedAt
	Date approvedAt
	Date closedAt

	String resolution
	/** Finance credit invoice id when approve hands off (AS3 B) */
	Long financeCreditInvoiceId

	/** RECEIVED | VALIDATED | APPROVED | REJECTED | CLOSED */
	String claimStatus = 'RECEIVED'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		claimNumber nullable: true, maxSize: 64
		warranty nullable: false
		inquiry nullable: true
		claimAmountEur nullable: false, scale: 8
		claimAmountSats nullable: true
		currencyCode nullable: false, blank: false, maxSize: 8
		investigationNotes nullable: true, maxSize: 8000
		needsFieldVisit nullable: true
		receivedAt nullable: true
		validatedAt nullable: true
		approvedAt nullable: true
		closedAt nullable: true
		resolution nullable: true, maxSize: 4000
		financeCreditInvoiceId nullable: true
		claimStatus nullable: false, blank: false, maxSize: 32,
				inList: ['RECEIVED', 'VALIDATED', 'APPROVED', 'REJECTED', 'CLOSED']
	}

	static mapping = {
		description type: 'text'
		investigationNotes type: 'text'
		resolution type: 'text'
		claimAmountEur scale: 8
		claimNumber index: 'claims_number_idx'
		claimStatus index: 'claims_status_idx'
	}
}
