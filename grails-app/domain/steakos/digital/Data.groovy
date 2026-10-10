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
 * Data asset / retention policy (APQC 8.4).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,dataClass', includeNames = true, includePackage = false)
class Data implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Versioned, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	Integer retentionDays
	/** PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED */
	String dataClass = 'INTERNAL'
	String ownerRef
	/** ACTIVE | ARCHIVED */
	String dataStatus = 'ACTIVE'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		retentionDays nullable: true, min: 0
		dataClass nullable: false, blank: false, maxSize: 32,
				inList: ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED']
		ownerRef nullable: true, maxSize: 128
		dataStatus nullable: false, blank: false, maxSize: 32,
				inList: ['ACTIVE', 'ARCHIVED']
	}

	static mapping = {
		description type: 'text'
		dataClass index: 'digital_data_class_idx'
	}
}
