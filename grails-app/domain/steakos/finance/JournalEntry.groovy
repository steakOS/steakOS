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
import steakos.traits.DomainTraitConstraints

/**
 * Double-entry journal header (ADR-009, D6, F-S0).
 * Posted entries are immutable at service layer (FS6); reverse via new entry.
 * Period key = calendar month yyyy-MM (FS3).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,period,posted', includeNames = true, includePackage = false)
class JournalEntry implements NpubOwned, AccessControl, Classifiable, Attachable,
		Auditable, Versioned, NostrPublishable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** Business date of the entry */
	Date entryDate

	/** Accounting period yyyy-MM (FS3) */
	String period

	/** Functional currency of amounts on lines — EUR for Basics (FS2) */
	String functionalCurrency = 'EUR'

	/** e.g. INVOICE, PAYMENT, PAYABLE, MANUAL, PAYROLL, OPENING */
	String sourceType

	/** Id of source domain row (string for flexibility) */
	String sourceId

	/** External idempotency key (webhook event id, etc.) */
	String eventId

	String memo

	Boolean posted = Boolean.FALSE
	Date postedAt

	/** If this entry reverses another */
	Long reversesEntryId

	static hasMany = [lines: JournalLine]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)
		DomainTraitConstraints.applyNostrPublishable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		entryDate nullable: false
		period nullable: false, blank: false, maxSize: 7, matches: /^\d{4}-\d{2}$/
		functionalCurrency nullable: false, blank: false, maxSize: 8
		sourceType nullable: true, maxSize: 64
		sourceId nullable: true, maxSize: 128
		eventId nullable: true, maxSize: 128
		memo nullable: true, maxSize: 2000
		posted nullable: true
		postedAt nullable: true
		reversesEntryId nullable: true
	}

	static mapping = {
		description type: 'text'
		memo type: 'text'
		period index: 'je_period_idx'
		eventId index: 'je_event_idx'
	}
}
