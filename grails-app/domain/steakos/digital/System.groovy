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
import steakos.traits.DynamicExtensible
import steakos.traits.DomainTraitConstraints

/**
 * Platform / runtime system (APQC 8.2, DIG1).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'systemKey')
@ToString(includes = 'systemKey,name', includeNames = true, includePackage = false)
class System implements NpubOwned, AccessControl, Classifiable, Attachable,
		Auditable, Versioned, DynamicExtensible, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String systemKey
	/** DEV | STAGE | PROD | OTHER */
	String environment = 'PROD'
	String architectureNote
	Boolean active = Boolean.TRUE

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)
		DomainTraitConstraints.applyDynamicExtensible(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		systemKey nullable: true, maxSize: 64
		environment nullable: false, blank: false, maxSize: 16,
				inList: ['DEV', 'STAGE', 'PROD', 'OTHER']
		architectureNote nullable: true, maxSize: 4000
		active nullable: true
	}

	static mapping = {
		description type: 'text'
		architectureNote type: 'text'
		systemKey index: 'digital_system_key_idx'
	}
}
