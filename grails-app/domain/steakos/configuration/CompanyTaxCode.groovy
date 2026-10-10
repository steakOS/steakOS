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
 * Per-company tax code in-use flag (OI-F23 D2 — currency-catalog pattern).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'taxCode')
@ToString(includes = 'taxCode,inUse', includeNames = true, includePackage = false)
class CompanyTaxCode implements NpubOwned, Serializable {

	private static final long serialVersionUID = 1L

	/** Matches {@link steakos.finance.TaxCode#code} */
	String taxCode

	Boolean inUse = Boolean.TRUE

	Date dateCreated
	Date lastUpdated

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		taxCode nullable: false, blank: false, maxSize: 32, unique: 'npubOwner'
		inUse nullable: false
	}

	static mapping = {
		table 'company_tax_code'
		npubOwner index: 'company_tax_code_npub_idx'
		taxCode index: 'company_tax_code_code_idx'
	}
}
