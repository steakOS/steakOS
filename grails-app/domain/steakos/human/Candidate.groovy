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
 * Recruiting pipeline (APQC 7.2). Names encrypted (HR1).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,stage', includeNames = true, includePackage = false)
class Candidate implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	@Convert(converter = EncryptedStringConverter)
	String name

	@Convert(converter = EncryptedStringConverter)
	String description

	String email
	String source
	String roleLabel

	/** APPLIED | SCREEN | OFFER | HIRED | REJECTED | WITHDRAWN */
	String stage = 'APPLIED'

	Long hrFileId
	Date appliedAt
	Date hiredAt

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 2000
		description nullable: true, maxSize: 8000
		email nullable: true, maxSize: 255
		source nullable: true, maxSize: 128
		roleLabel nullable: true, maxSize: 255
		stage nullable: false, blank: false, maxSize: 32,
				inList: ['APPLIED', 'SCREEN', 'OFFER', 'HIRED', 'REJECTED', 'WITHDRAWN']
		hrFileId nullable: true
		appliedAt nullable: true
		hiredAt nullable: true
	}

	static mapping = {
		description type: 'text'
		name type: 'text'
		stage index: 'candidate_stage_idx'
	}
}
