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
 * Full employment file (APQC 7.7.3). HR1: requires User; names encrypted at rest.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,employmentStatus', includeNames = true, includePackage = false)
class HRFile implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	/** Display / legal name — encrypted at rest (HR1) */
	@Convert(converter = EncryptedStringConverter)
	String name

	@Convert(converter = EncryptedStringConverter)
	String description

	/** Required link to Spring Security User (HR1) */
	Long userId
	String username

	/** EMPLOYEE | CONTRACTOR | TEMP_WITH_FILE | FOUNDER | OTHER */
	String employmentKind = 'EMPLOYEE'
	/** ACTIVE | ON_LEAVE | EXITED */
	String employmentStatus = 'ACTIVE'

	Date hireDate
	Date exitDate
	String email
	String personRef

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 2000
		description nullable: true, maxSize: 8000
		userId nullable: false
		username nullable: true, maxSize: 255
		employmentKind nullable: false, blank: false, maxSize: 32,
				inList: ['EMPLOYEE', 'CONTRACTOR', 'TEMP_WITH_FILE', 'FOUNDER', 'OTHER']
		employmentStatus nullable: false, blank: false, maxSize: 32,
				inList: ['ACTIVE', 'ON_LEAVE', 'EXITED']
		hireDate nullable: true
		exitDate nullable: true
		email nullable: true, maxSize: 255
		personRef nullable: true, maxSize: 128
	}

	static mapping = {
		description type: 'text'
		name type: 'text'
		userId index: 'hr_file_user_idx'
		employmentStatus index: 'hr_file_status_idx'
	}
}
