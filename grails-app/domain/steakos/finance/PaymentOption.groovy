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
 * Per-rail payment option on an Invoice (ADR-009 three-QR flow, FS1, F-S0).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,rail', includeNames = true, includePackage = false)
class PaymentOption implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	Invoice invoice

	/** SEPA | BTC_ONCHAIN | BTC_LIGHTNING */
	String rail

	/** Payment URI / BIP21 / LN invoice / SEPA payload */
	String paymentUri

	/** Optional precomputed QR payload or image URL */
	String qrPayload

	/** Human estimate e.g. "instant", "1-3 days" */
	String estimatedSettle

	/** External provider invoice id (BTCPay) if pre-created */
	String externalInvoiceId

	Boolean active = Boolean.TRUE

	static belongsTo = [invoice: Invoice]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		invoice nullable: false
		rail nullable: false, blank: false, maxSize: 32,
				inList: ['SEPA', 'BTC_ONCHAIN', 'BTC_LIGHTNING']
		paymentUri nullable: true, maxSize: 4000
		qrPayload nullable: true, maxSize: 8000
		estimatedSettle nullable: true, maxSize: 128
		externalInvoiceId nullable: true, maxSize: 256
		active nullable: true
	}

	static mapping = {
		description type: 'text'
		paymentUri type: 'text'
		qrPayload type: 'text'
	}
}
