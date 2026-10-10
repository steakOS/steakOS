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

package steakos.configuration

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints

/**
 * Physical or logical site (ADR-029 Path B).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name', includeNames = true, includePackage = false)
class Site implements NpubOwned, AccessControl, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	LegalEntity legalEntity
	/** HQ, WAREHOUSE, SALES, SERVICE, OTHER */
	String siteType = 'HQ'
	String addressText
	Boolean active = Boolean.TRUE

	static belongsTo = [legalEntity: LegalEntity]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		legalEntity nullable: false
		siteType nullable: false, blank: false, maxSize: 32
		addressText nullable: true, maxSize: 2000
		active nullable: false
	}

	static mapping = {
		description type: 'text'
		addressText type: 'text'
		table 'site'
	}
}
