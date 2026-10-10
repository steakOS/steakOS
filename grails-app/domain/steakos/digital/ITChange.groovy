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
import steakos.traits.Versioned
import steakos.traits.DomainTraitConstraints

/**
 * Change / backup / DR / security posture (APQC 8.3, DIG2 changeKind).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,changeKind,changeStatus', includeNames = true, includePackage = false)
class ITChange implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Versioned, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** CHANGE | BACKUP | DR | SECURITY | OTHER — DIG2 */
	String changeKind = 'CHANGE'
	/** LOW | MED | HIGH */
	String riskLevel = 'LOW'
	Date changeWindowStart
	Date changeWindowEnd
	/** PLANNED | IN_PROGRESS | DONE | CANCELLED */
	String changeStatus = 'PLANNED'
	Long solutionId
	Long systemId
	Long configItemId
	String cabNote

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		changeKind nullable: false, blank: false, maxSize: 32,
				inList: ['CHANGE', 'BACKUP', 'DR', 'SECURITY', 'OTHER']
		riskLevel nullable: false, blank: false, maxSize: 16,
				inList: ['LOW', 'MED', 'HIGH']
		changeWindowStart nullable: true
		changeWindowEnd nullable: true
		changeStatus nullable: false, blank: false, maxSize: 32,
				inList: ['PLANNED', 'IN_PROGRESS', 'DONE', 'CANCELLED']
		solutionId nullable: true
		systemId nullable: true
		configItemId nullable: true
		cabNote nullable: true, maxSize: 4000
	}

	static mapping = {
		description type: 'text'
		cabNote type: 'text'
		changeStatus index: 'digital_change_status_idx'
		changeKind index: 'digital_change_kind_idx'
	}
}
