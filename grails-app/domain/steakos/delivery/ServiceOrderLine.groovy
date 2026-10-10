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

package steakos.delivery

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.DomainTraitConstraints

/**
 * Service order line (D1) — qty may be hours or units.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,lineNo,lineTotalEur', includeNames = true, includePackage = false)
class ServiceOrderLine implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	ServiceOrder serviceOrder
	Integer lineNo = 1

	BigDecimal quantity = BigDecimal.ONE
	BigDecimal unitPriceEur = BigDecimal.ZERO
	BigDecimal lineTotalEur = BigDecimal.ZERO

	Long unitPriceSats
	Long lineTotalSats

	Long serviceRefId
	Long productRefId
	String productRefType
	/** Unified catalogue master (CATALOGUE-ITEM-CONCEPT) */
	Long catalogueItemId

	static belongsTo = [serviceOrder: ServiceOrder]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		serviceOrder nullable: false
		lineNo nullable: false, min: 1
		quantity nullable: false, scale: 8, min: 0.0G
		unitPriceEur nullable: false, scale: 8
		lineTotalEur nullable: false, scale: 8
		unitPriceSats nullable: true
		lineTotalSats nullable: true
		serviceRefId nullable: true
		productRefId nullable: true
		productRefType nullable: true, maxSize: 64
		catalogueItemId nullable: true
	}

	static mapping = {
		description type: 'text'
		quantity scale: 8
		unitPriceEur scale: 8
		lineTotalEur scale: 8
	}
}
