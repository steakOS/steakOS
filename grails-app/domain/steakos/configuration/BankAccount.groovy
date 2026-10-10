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

package steakos.configuration

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints

/**
 * Banking / payment rail placeholder (ADR-029 Path B).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name', includeNames = true, includePackage = false)
class BankAccount implements NpubOwned, AccessControl, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	LegalEntity legalEntity
	/** BANK, BTC_ONCHAIN, LIGHTNING, BTCPAY, OTHER */
	String accountKind = 'BANK'
	/**
	 * Domestic/clearing scheme for BANK accounts: SEPA (IBAN/BIC) or ACH (US routing + account).
	 * Null/blank treated as SEPA for legacy rows.
	 */
	String paymentScheme = 'SEPA'
	String iban
	String bic
	/** ACH / US: bank branch or branch name (optional). */
	String branchName
	/** ACH / US: domestic account number (not IBAN). */
	String accountNumber
	/** ACH / US: ABA routing number (typically 9 digits). */
	String routingNumber
	String currencyCode = 'EUR'
	/** BTCPay store URL or NWC URI placeholder — not secrets vault */
	String externalRef
	Boolean active = Boolean.TRUE

	static belongsTo = [legalEntity: LegalEntity]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		legalEntity nullable: false
		accountKind nullable: false, blank: false, maxSize: 32
		paymentScheme nullable: true, blank: false, maxSize: 16, inList: ['SEPA', 'ACH']
		iban nullable: true, maxSize: 64
		bic nullable: true, maxSize: 32
		branchName nullable: true, maxSize: 255
		accountNumber nullable: true, maxSize: 64
		routingNumber nullable: true, maxSize: 32
		currencyCode nullable: false, blank: false, maxSize: 8
		externalRef nullable: true, maxSize: 2000
		active nullable: false
	}

	static mapping = {
		description type: 'text'
		externalRef type: 'text'
		table 'bank_account'
	}
}
