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

/**
 * System-wide tax code catalog (OI-F23 D1–D2). Seeded from {@link TaxCatalogCodes}.
 * Company enables codes via {@link steakos.configuration.CompanyTaxCode}.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'code')
@ToString(includes = 'code,ratePercent,jurisdiction', includeNames = true, includePackage = false)
class TaxCode implements Serializable {

	private static final long serialVersionUID = 1L

	String code
	String jurisdiction
	String name
	String description
	/** Percent points, e.g. 19.00 for 19% */
	BigDecimal ratePercent = BigDecimal.ZERO
	/** VAT | SALES_TAX | REVERSE_CHARGE | ZERO | EXEMPT */
	String taxKind = TaxCatalogCodes.KIND_VAT
	Boolean reverseCharge = Boolean.FALSE
	Boolean intraCommunity = Boolean.FALSE
	Boolean enabled = Boolean.TRUE

	static constraints = {
		code nullable: false, blank: false, maxSize: 32, unique: true
		jurisdiction nullable: false, blank: false, maxSize: 16
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		ratePercent nullable: false, scale: 4, min: 0.0G
		taxKind nullable: false, blank: false, maxSize: 32,
				inList: [TaxCatalogCodes.KIND_VAT, TaxCatalogCodes.KIND_SALES_TAX,
				         TaxCatalogCodes.KIND_REVERSE_CHARGE, TaxCatalogCodes.KIND_ZERO,
				         TaxCatalogCodes.KIND_EXEMPT]
		reverseCharge nullable: false
		intraCommunity nullable: false
		enabled nullable: true
	}

	static mapping = {
		table 'tax_code'
		description type: 'text'
		ratePercent scale: 4
		code index: 'tax_code_code_idx'
		jurisdiction index: 'tax_code_jur_idx'
	}
}
