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

package steakos.production

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
 * Quality check on a lot (SC-S0 / APQC 4.3.3).
 * Table is {@code quality_check} — {@code check} is a reserved SQL keyword in PostgreSQL.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,checkResult', includeNames = true, includePackage = false)
class Check implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	Lot lot
	Date checkedOn

	/** PASS | FAIL | HOLD */
	String checkResult = 'PASS'

	String notes

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		lot nullable: false
		checkedOn nullable: true
		checkResult nullable: false, blank: false, maxSize: 16,
				inList: ['PASS', 'FAIL', 'HOLD']
		notes nullable: true, maxSize: 4000
	}

	static mapping = {
		// PG reserved word: unquoted "check" → syntax error on list/select
		table 'quality_check'
		description type: 'text'
		notes type: 'text'
	}
}
