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
import steakos.traits.DomainTraitConstraints

/**
 * Payroll compensation line (PDR-027 / OI-F30). Recognize + pay post via PostingEngine;
 * tax / employer splits remain stub until HR supplies fields.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,period', includeNames = true, includePackage = false)
class PayrollItem implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String period
	String personRef
	BigDecimal amountEur = BigDecimal.ZERO
	/** Optional sats (HR7 dual currency) */
	Long amountSats
	String currencyCode = 'EUR'

	/** DRAFT | READY | POSTED (recognized) | PAID */
	String payrollStatus = 'DRAFT'

	/** Recognize JE (payroll.recognize). */
	Long journalEntryId
	/** Pay wages JE (payroll.pay). */
	Long payJournalEntryId

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		period nullable: true, maxSize: 7
		personRef nullable: true, maxSize: 128
		amountEur nullable: false, scale: 8
		amountSats nullable: true
		currencyCode nullable: false, blank: false, maxSize: 8
		payrollStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'READY', 'POSTED', 'PAID']
		journalEntryId nullable: true
		payJournalEntryId nullable: true
	}

	static mapping = {
		description type: 'text'
		amountEur scale: 8
	}
}
