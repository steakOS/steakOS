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

package steakos.digital

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
 * CMDB-lite configuration item (DIG0). Manual — no discovery.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,ciKind,name', includeNames = true, includePackage = false)
class ConfigItem implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** HOST | APP | INTEGRATION | NETWORK | OTHER */
	String ciKind = 'OTHER'
	String hostname
	String ipAddress
	/** ACTIVE | INACTIVE | RETIRED */
	String ciStatus = 'ACTIVE'
	Long systemId
	Long solutionId
	/** Permanent reverse link from AssetBasics PhysicalAsset when IT-managed (G4) */
	Long physicalAssetId

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		ciKind nullable: false, blank: false, maxSize: 32,
				inList: ['HOST', 'APP', 'INTEGRATION', 'NETWORK', 'OTHER']
		hostname nullable: true, maxSize: 255
		ipAddress nullable: true, maxSize: 64
		ciStatus nullable: false, blank: false, maxSize: 32,
				inList: ['ACTIVE', 'INACTIVE', 'RETIRED']
		systemId nullable: true
		solutionId nullable: true
		physicalAssetId nullable: true
	}

	static mapping = {
		description type: 'text'
		ciKind index: 'digital_ci_kind_idx'
		ciStatus index: 'digital_ci_status_idx'
		physicalAssetId index: 'digital_ci_physical_asset_idx'
	}
}
