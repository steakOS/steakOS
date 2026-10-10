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
import steakos.traits.DomainTraitConstraints
import steakos.catalogue.CatalogueItem

/**
 * Single-level BOM line (Basics) — component = catalogue GOODS.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'lineNo')
@ToString(includes = 'lineNo,quantity', includeNames = true, includePackage = false)
class BomItem implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	BillOfMaterials bom
	Integer lineNo = 1

	/** Component material (catalogue GOODS) */
	CatalogueItem componentItem
	BigDecimal quantity = BigDecimal.ONE
	String unit = 'EA'

	static belongsTo = [bom: BillOfMaterials]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)

		name nullable: true, maxSize: 255
		description nullable: true, maxSize: 4000
		bom nullable: false
		lineNo nullable: false, min: 1
		componentItem nullable: false
		quantity nullable: false, scale: 8, min: 0.0G
		unit nullable: false, blank: false, maxSize: 16
	}

	static mapping = {
		description type: 'text'
		quantity scale: 8
	}
}
