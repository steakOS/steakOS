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
import steakos.traits.Assignable

/**
 * Program under portfolio (APQC 13.2.2). Optional for SBM (CAP1).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,programStatus', includeNames = true, includePackage = false)
class Program implements NpubOwned, AccessControl, Classifiable, Attachable, Assignable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	Long portfolioId
	/** ACTIVE | CLOSED */
	String programStatus = 'ACTIVE'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAssignable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		portfolioId nullable: true
		programStatus nullable: false, blank: false, maxSize: 32,
				inList: ['ACTIVE', 'CLOSED']
	}

	static mapping = {
		description type: 'text'
		portfolioId index: 'cap_program_portfolio_idx'
	}
}
