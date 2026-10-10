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
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints

/**
 * Delivery resource — human / agent / other capacity (D-S0 / APQC 5.2).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,resourceKind', includeNames = true, includePackage = false)
class Resource implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** HUMAN | AGENT | OTHER */
	String resourceKind = 'HUMAN'
	BigDecimal capacityHours
	/** Optional link to agents.Agent id */
	Long agentRefId
	/** Optional link to human.HRFile id — assignee humans come from active employment */
	Long hrFileId
	String personRef
	String email
	Boolean active = Boolean.TRUE

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		resourceKind nullable: false, blank: false, maxSize: 32,
				inList: ['HUMAN', 'AGENT', 'OTHER']
		capacityHours nullable: true, scale: 8
		agentRefId nullable: true
		hrFileId nullable: true
		personRef nullable: true, maxSize: 255
		email nullable: true, maxSize: 255
		active nullable: true
	}

	static mapping = {
		description type: 'text'
		capacityHours scale: 8
		resourceKind index: 'delivery_resource_kind_idx'
		hrFileId index: 'delivery_resource_hr_file_idx'
	}
}
