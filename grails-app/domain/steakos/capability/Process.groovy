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
 * Process management definition (APQC 13.1).
 * Table {@code business_process} — avoid SQL keyword {@code process}.
 * processKind: SYSTEM | HUMAN | AGENTIC | OTHER (PROCESS-CATALOG-VISUAL-CONCEPT D1).
 * Fully qualify as {@code steakos.capability.Process} in services (vs java.lang.Process).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,processStatus', includeNames = true, includePackage = false)
class Process implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String processCode
	/** DRAFT | ACTIVE | RETIRED (D2: no LIVE alias) */
	String processStatus = 'DRAFT'
	/** SYSTEM | HUMAN | AGENTIC | OTHER */
	String processKind = 'HUMAN'
	String versionLabel
	String ownerRef

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		processCode nullable: true, maxSize: 64
		processStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'ACTIVE', 'RETIRED']
		processKind nullable: false, blank: false, maxSize: 32,
				inList: ['SYSTEM', 'HUMAN', 'AGENTIC', 'OTHER']
		versionLabel nullable: true, maxSize: 64
		ownerRef nullable: true, maxSize: 255
	}

	static mapping = {
		table 'business_process'
		description type: 'text'
		processStatus index: 'cap_process_status_idx'
		processKind index: 'cap_process_kind_idx'
		processCode index: 'cap_process_code_idx'
	}

	boolean isSystemKind() {
		'SYSTEM' == processKind
	}
}
