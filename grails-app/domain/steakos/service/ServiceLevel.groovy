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

package steakos.service

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
 * Post-sale service level / SLA labels (APQC 6.1, AS-S0).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'code')
@ToString(includes = 'code,name', includeNames = true, includePackage = false)
class ServiceLevel implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Versioned, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String code
	/** Target first response hours */
	Integer responseHours
	/** Target resolve hours */
	Integer resolveHours
	/** EMAIL | CHAT | PHONE | OTHER | ANY */
	String channel = 'ANY'
	Boolean active = Boolean.TRUE

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		code nullable: true, maxSize: 64
		responseHours nullable: true, min: 0
		resolveHours nullable: true, min: 0
		channel nullable: false, blank: false, maxSize: 16,
				inList: ['EMAIL', 'CHAT', 'PHONE', 'OTHER', 'ANY']
		active nullable: true
	}

	static mapping = {
		description type: 'text'
		code index: 'service_level_code_idx'
	}
}
