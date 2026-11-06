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

package steakos.asset

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints
import steakos.traits.Assignable

/**
 * AssetBasics thin: plant / vehicles / tools register (G4).
 * Optional permanent link to digital.ConfigItem when itManaged (IT is master for CI fields).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,assetTag,status', includeNames = true, includePackage = false)
class PhysicalAsset implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Assignable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description
	/** Inventory / plate / serial tag */
	String assetTag
	String location
	/** Person or org responsible (free text, thin) */
	String ownerName
	/** DRAFT | IN_SERVICE | MAINTENANCE | RETIRED */
	String status = 'DRAFT'
	/**
	 * When true, ensure a permanent ConfigItem link (IT CMDB). Once linked, stays linked.
	 * IT fields are edited only on the Digital / ConfigItem side.
	 */
	Boolean itManaged = Boolean.FALSE
	/** Permanent FK to digital.ConfigItem when IT-managed */
	Long configItemId
	/**
	 * When true, Finance-module agents may pick this asset up and post depreciation
	 * values in a financial period (Basics flag; posting engine later).
	 */
	Boolean financialDepreciation = Boolean.FALSE
	/**
	 * When true, physical maintenance is expected. Attach manufacturer schedule
	 * (XLSX/PDF) via Object Companion; later agents may create shopfloor/maintenance activities.
	 */
	Boolean requiresMaintenance = Boolean.FALSE

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAssignable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		assetTag nullable: true, maxSize: 128
		location nullable: true, maxSize: 255
		ownerName nullable: true, maxSize: 255
		status nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'IN_SERVICE', 'MAINTENANCE', 'RETIRED']
		itManaged nullable: false
		configItemId nullable: true
		financialDepreciation nullable: false
		requiresMaintenance nullable: false
	}

	static mapping = {
		description type: 'text'
		status index: 'asset_physical_status_idx'
		assetTag index: 'asset_physical_tag_idx'
		configItemId index: 'asset_physical_ci_idx'
	}
}
