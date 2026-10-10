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
 * Double-entry journal line (D6, F-S0).
 * Exactly one of debitEur / creditEur should be non-zero (enforced by FinanceLedgerService).
 * amountSats optional audit for BTC rails (FS2).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,lineNo,debitEur,creditEur', includeNames = true, includePackage = false)
class JournalLine implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	JournalEntry journalEntry

	/** Chart account (FK) */
	Account account

	Integer lineNo = 1

	BigDecimal debitEur = BigDecimal.ZERO
	BigDecimal creditEur = BigDecimal.ZERO

	/** Optional sats for Bitcoin-related lines (audit; books functional = EUR) */
	Long amountSats

	String memo

	static belongsTo = [journalEntry: JournalEntry]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		journalEntry nullable: false
		account nullable: false
		lineNo nullable: false, min: 1
		debitEur nullable: false, scale: 8
		creditEur nullable: false, scale: 8
		amountSats nullable: true
		memo nullable: true, maxSize: 1000
	}

	static mapping = {
		description type: 'text'
		debitEur scale: 8
		creditEur scale: 8
	}
}
