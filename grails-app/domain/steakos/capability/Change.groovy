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

package steakos.capability

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.DomainTraitConstraints

/**
 * Organizational change header (APQC 13.4). Not Digital ITChange (CAP3 A).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,changeStatus', includeNames = true, includePackage = false)
class Change implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** PLANNED | IN_PROGRESS | DONE | CANCELLED */
	String changeStatus = 'PLANNED'
	Date plannedStart
	Date plannedEnd
	String impactNote

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		changeStatus nullable: false, blank: false, maxSize: 32,
				inList: ['PLANNED', 'IN_PROGRESS', 'DONE', 'CANCELLED']
		plannedStart nullable: true
		plannedEnd nullable: true
		impactNote nullable: true, maxSize: 4000
	}

	static mapping = {
		table 'change_request'
		description type: 'text'
		impactNote type: 'text'
		changeStatus index: 'cap_change_status_idx'
	}
}
