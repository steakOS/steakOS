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

package steakos.production

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints
import steakos.catalogue.CatalogueItem

/**
 * Lot / batch for provenance (SC-S0 / APQC 4.3.4) — catalogue GOODS only.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'lotCode')
@ToString(includes = 'lotCode,lotStatus', includeNames = true, includePackage = false)
class Lot implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String lotCode
	CatalogueItem catalogueItem
	BigDecimal quantity = BigDecimal.ZERO
	Date manufacturedOn

	/** OPEN | QUARANTINE | RELEASED | REJECTED */
	String lotStatus = 'OPEN'

	Long sourceProductionOrderId

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		lotCode nullable: false, blank: false, maxSize: 64
		catalogueItem nullable: false
		quantity nullable: false, scale: 8, min: 0.0G
		manufacturedOn nullable: true
		lotStatus nullable: false, blank: false, maxSize: 32,
				inList: ['OPEN', 'QUARANTINE', 'RELEASED', 'REJECTED']
		sourceProductionOrderId nullable: true
	}

	static mapping = {
		description type: 'text'
		quantity scale: 8
		lotCode index: 'lot_code_idx'
	}
}
