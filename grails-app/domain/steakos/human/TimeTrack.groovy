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
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints

/**
 * Time / attendance (APQC 7.7.6). HR4: hrFile required.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,hours,timeKind', includeNames = true, includePackage = false)
class TimeTrack implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** HR4: required employment file */
	HRFile hrFile

	Date startedAt
	Date endedAt
	BigDecimal hours = BigDecimal.ZERO
	/** WORK | LEAVE | OTHER */
	String timeKind = 'WORK'
	/** DRAFT | POSTED */
	String timeStatus = 'POSTED'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		hrFile nullable: false
		startedAt nullable: true
		endedAt nullable: true
		hours nullable: false, scale: 8
		timeKind nullable: false, blank: false, maxSize: 16,
				inList: ['WORK', 'LEAVE', 'OTHER']
		timeStatus nullable: false, blank: false, maxSize: 16,
				inList: ['DRAFT', 'POSTED']
	}

	static mapping = {
		description type: 'text'
		hours scale: 8
	}
}
