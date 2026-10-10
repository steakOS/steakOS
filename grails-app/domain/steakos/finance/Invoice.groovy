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
import steakos.traits.AiTagged
import steakos.traits.DomainTraitConstraints

/**
 * Customer invoice / AR document (FS1, ADR-009/010, F-S0).
 * Primary amount in amountEur (functional units per PDR-023); amountSats/exchangeRate legacy nullable.
 * Cash-oriented recognition (FS8): primary ledger post on Payment settle.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'invoiceNumber')
@ToString(includes = 'invoiceNumber,name,invoiceStatus', includeNames = true, includePackage = false)
class Invoice implements NpubOwned, AccessControl, Classifiable, Attachable,
		Auditable, Versioned, NostrPublishable, DynamicExtensible, AiTagged, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** Business invoice number (unique per npub when set) */
	String invoiceNumber

	/** Optional FK to sales.Customer id (loose for monorepo) */
	Long customerId

	/** Denormalized party label for display without join */
	String customerName

	String customerContact

	Date issueDate
	Date dueDate

	/** Functional / primary amount due (gross when tax present) — PDR-023 / OI-F23 */
	BigDecimal amountEur

	/** OI-F23 — net / tax breakdown when line tax is used */
	BigDecimal netAmountEur
	BigDecimal taxAmountEur

	/** Legacy; not written on create (hints use live FX). Settlement may still use sats on Payment. */
	Long amountSats

	/** Legacy informational rate; not written on create */
	BigDecimal exchangeRate

	String currencyCode = 'EUR'

	/** Frozen partner locale at issue (OI-I18N02 D4) */
	String documentLocale

	/**
	 * DRAFT | ISSUED | PART_PAID | PAID | VOID
	 * (Classifiable.status may mirror; this is the finance lifecycle)
	 */
	String invoiceStatus = 'DRAFT'

	Boolean recurring = Boolean.FALSE

	Date settledAt

	String referenceText

	static hasMany = [paymentOptions: PaymentOption, payments: Payment]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)
		DomainTraitConstraints.applyNostrPublishable(delegate)
		DomainTraitConstraints.applyDynamicExtensible(delegate)
		DomainTraitConstraints.applyAiTagged(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		invoiceNumber nullable: true, maxSize: 64
		customerId nullable: true
		customerName nullable: true, maxSize: 255
		customerContact nullable: true, maxSize: 512
		issueDate nullable: true
		dueDate nullable: true
		amountEur nullable: false, scale: 8, min: 0.0G
		netAmountEur nullable: true, scale: 8
		taxAmountEur nullable: true, scale: 8
		amountSats nullable: true, min: 0L
		exchangeRate nullable: true, scale: 12
		currencyCode nullable: false, blank: false, maxSize: 8
		documentLocale nullable: true, maxSize: 16
		invoiceStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'ISSUED', 'PART_PAID', 'PAID', 'VOID']
		recurring nullable: true
		settledAt nullable: true
		referenceText nullable: true, maxSize: 2000
	}

	static mapping = {
		description type: 'text'
		referenceText type: 'text'
		amountEur scale: 8
		netAmountEur scale: 8
		taxAmountEur scale: 8
		exchangeRate scale: 12
		invoiceNumber index: 'invoice_number_idx'
		invoiceStatus index: 'invoice_status_idx'
	}
}
