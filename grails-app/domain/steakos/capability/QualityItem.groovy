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
 * Quality requirement or NCR line (APQC 13.3 light).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,itemKind', includeNames = true, includePackage = false)
class QualityItem implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	Long qualityId
	/** REQ | NCR */
	String itemKind = 'REQ'
	/** OPEN | CLOSED */
	String itemStatus = 'OPEN'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		qualityId nullable: false
		itemKind nullable: false, blank: false, maxSize: 16,
				inList: ['REQ', 'NCR']
		itemStatus nullable: false, blank: false, maxSize: 32,
				inList: ['OPEN', 'CLOSED']
	}

	static mapping = {
		description type: 'text'
		qualityId index: 'cap_quality_item_q_idx'
	}
}
