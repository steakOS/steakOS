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
import steakos.traits.NostrPublishable
import steakos.traits.DynamicExtensible
import steakos.traits.DomainTraitConstraints

/**
 * Settlement against an Invoice (FS1, ADR-009, F-S0).
 * Cash-oriented recognition (FS8): settled payments drive ledger posts via FinanceLedgerService.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,rail,paymentStatus', includeNames = true, includePackage = false)
class Payment implements NpubOwned, AccessControl, Classifiable, Attachable,
		Auditable, NostrPublishable, DynamicExtensible, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	Invoice invoice

	/** SEPA | BTC_ONCHAIN | BTC_LIGHTNING | BTC_WALLET_ADDRESS | OTHER */
	String rail

	BigDecimal amountEur
	Long amountSats

	Date receivedAt

	/** PENDING | SETTLED | FAILED | REFUNDED */
	String paymentStatus = 'PENDING'

	/** BTCPay / bank external id */
	String externalTxId

	/** Idempotency key for webhooks */
	String eventId

	Long journalEntryId

	static belongsTo = [invoice: Invoice]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyNostrPublishable(delegate)
		DomainTraitConstraints.applyDynamicExtensible(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		invoice nullable: false
		rail nullable: false, blank: false, maxSize: 32,
				inList: ['SEPA', 'BTC_ONCHAIN', 'BTC_LIGHTNING', 'BTC_WALLET_ADDRESS', 'OTHER']
		amountEur nullable: false, scale: 8, min: 0.0G
		amountSats nullable: true, min: 0L
		receivedAt nullable: true
		paymentStatus nullable: false, blank: false, maxSize: 32,
				inList: ['PENDING', 'SETTLED', 'FAILED', 'REFUNDED']
		externalTxId nullable: true, maxSize: 256
		eventId nullable: true, maxSize: 128
		journalEntryId nullable: true
	}

	static mapping = {
		description type: 'text'
		amountEur scale: 8
		eventId index: 'payment_event_idx'
		externalTxId index: 'payment_ext_tx_idx'
	}
}
