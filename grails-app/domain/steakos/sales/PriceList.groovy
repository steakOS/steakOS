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

package steakos.sales

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints

/**
 * SalesAdvanced structured price list (OI-S04 / G5). Not CPQ — list + optional discount only.
 * Company-scoped via {@code npubOwner}.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'code')
@ToString(includes = 'code,name,listKind,active', includeNames = true, includePackage = false)
class PriceList implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	static final String KIND_STANDARD = 'STANDARD'
	static final String KIND_CUSTOMER = 'CUSTOMER'
	static final String KIND_VOLUME = 'VOLUME'

	String name
	String description
	/** Optional unique code per tenant */
	String code

	/** STANDARD | CUSTOMER | VOLUME */
	String listKind = KIND_STANDARD

	String currencyCode = 'EUR'
	Boolean active = Boolean.TRUE

	Date validFrom
	Date validTo

	/** Optional customer-specific list (CUSTOMER kind) */
	Long customerId

	/** Default line discount when line has none (0–100) */
	BigDecimal defaultDiscountPercent

	static hasMany = [lines: PriceListLine]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		code nullable: true, blank: true, maxSize: 64, unique: 'npubOwner'
		listKind nullable: false, blank: false, maxSize: 32,
				inList: [KIND_STANDARD, KIND_CUSTOMER, KIND_VOLUME]
		currencyCode nullable: false, blank: false, maxSize: 8
		active nullable: false
		validFrom nullable: true
		validTo nullable: true
		customerId nullable: true
		defaultDiscountPercent nullable: true, scale: 4, min: 0.0G, max: 100.0G
	}

	static mapping = {
		table 'sales_price_list'
		description type: 'text'
		defaultDiscountPercent scale: 4
		code index: 'sales_price_list_code_idx'
		listKind index: 'sales_price_list_kind_idx'
	}
}
