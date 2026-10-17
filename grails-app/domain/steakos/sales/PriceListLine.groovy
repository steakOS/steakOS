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
import steakos.traits.DomainTraitConstraints

/**
 * Line on a SalesAdvanced {@link PriceList}. Links optional catalogue item + unit price / discount.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'lineNo')
@ToString(includes = 'lineNo,name,unitPrice,minQuantity', includeNames = true, includePackage = false)
class PriceListLine implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	PriceList priceList
	Integer lineNo = 1

	/** Optional catalogue master id */
	Long catalogueItemId
	/** Optional SKU / item code snapshot */
	String itemCode

	BigDecimal minQuantity = BigDecimal.ONE
	BigDecimal unitPrice = BigDecimal.ZERO
	/** Line discount 0–100; null → list defaultDiscountPercent */
	BigDecimal discountPercent

	String currencyCode

	static belongsTo = [priceList: PriceList]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		priceList nullable: false
		lineNo nullable: false, min: 1
		catalogueItemId nullable: true
		itemCode nullable: true, maxSize: 64
		minQuantity nullable: false, scale: 8, min: 0.0G
		unitPrice nullable: false, scale: 8
		discountPercent nullable: true, scale: 4, min: 0.0G, max: 100.0G
		currencyCode nullable: true, maxSize: 8
	}

	static mapping = {
		table 'sales_price_list_line'
		description type: 'text'
		unitPrice scale: 8
		minQuantity scale: 8
		discountPercent scale: 4
		catalogueItemId index: 'sales_pll_catalogue_idx'
	}
}
