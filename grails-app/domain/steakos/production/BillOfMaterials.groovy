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
import steakos.traits.Versioned
import steakos.traits.DomainTraitConstraints
import steakos.catalogue.CatalogueItem

/**
 * Simple single-level BOM (SC-S0 / APQC 4.3.2).
 * Parent finished good = catalogue item (nature GOODS).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,catalogueItem', includeNames = true, includePackage = false)
class BillOfMaterials implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Versioned, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** Finished good (catalogue GOODS) */
	CatalogueItem catalogueItem

	/** ACTIVE | DRAFT | OBSOLETE */
	String bomStatus = 'ACTIVE'

	static hasMany = [items: BomItem]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		catalogueItem nullable: false
		bomStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'ACTIVE', 'OBSOLETE']
	}

	static mapping = {
		description type: 'text'
	}
}
