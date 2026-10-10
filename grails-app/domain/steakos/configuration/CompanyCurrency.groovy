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
import steakos.traits.DomainTraitConstraints

/**
 * Per-company currency in-use flag (OI-CUR01 D1).
 * System catalog remains {@link steakos.helper.Currencies}; this join scopes dropdowns.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'currencyCode')
@ToString(includes = 'currencyCode,inUse', includeNames = true, includePackage = false)
class CompanyCurrency implements NpubOwned, Serializable {

	private static final long serialVersionUID = 1L

	/** ISO 4217 alpha-3 matching Currencies.code */
	String currencyCode

	Boolean inUse = Boolean.TRUE

	Date dateCreated
	Date lastUpdated

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		currencyCode nullable: false, blank: false, maxSize: 3, matches: /[A-Z]{3}/, unique: 'npubOwner'
		inUse nullable: false
	}

	static mapping = {
		table 'company_currency'
		npubOwner index: 'company_currency_npub_idx'
		currencyCode index: 'company_currency_code_idx'
	}
}
