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

package steakos.human

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import jakarta.persistence.Convert
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints
import steakos.security.EncryptedStringConverter

/**
 * Non-employee party with system access but no full HRFile (HR1).
 * BUSINESS_PARTNER | EXTERNAL_WORKER | STUDENT | TEMPORARY | OTHER
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,partyKind', includeNames = true, includePackage = false)
class PartyAccess implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	@Convert(converter = EncryptedStringConverter)
	String name

	String description

	Long userId
	String username

	/** BUSINESS_PARTNER | EXTERNAL_WORKER | STUDENT | TEMPORARY | OTHER */
	String partyKind = 'OTHER'
	Boolean active = Boolean.TRUE
	String notes

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 2000
		description nullable: true, maxSize: 4000
		userId nullable: false
		username nullable: true, maxSize: 255
		partyKind nullable: false, blank: false, maxSize: 32,
				inList: ['BUSINESS_PARTNER', 'EXTERNAL_WORKER', 'STUDENT', 'TEMPORARY', 'OTHER']
		active nullable: true
		notes nullable: true, maxSize: 2000
	}

	static mapping = {
		description type: 'text'
		name type: 'text'
		notes type: 'text'
		userId index: 'party_access_user_idx'
		partyKind index: 'party_access_kind_idx'
	}
}
