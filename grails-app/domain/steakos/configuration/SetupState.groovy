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
import steakos.traits.DomainTraitConstraints

/**
 * One-shot setup gate per tenant (ADR-026 / OI-W01).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'templateTier')
@ToString(includes = 'setupComplete,templateTier', includeNames = true, includePackage = false)
class SetupState implements NpubOwned, Serializable {

	private static final long serialVersionUID = 1L

	Boolean setupComplete = Boolean.FALSE
	/** IRON | COPPER | BRONZE | SILVER | GOLD */
	String templateTier = 'BRONZE'
	Integer draftStep = 0
	String draftJson
	Integer wizardVersion = 1
	Date completedAt
	String completedBy
	String notes

	Date dateCreated
	Date lastUpdated

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		setupComplete nullable: false
		templateTier nullable: false, blank: false, maxSize: 16
		draftStep nullable: false
		draftJson nullable: true, maxSize: 500000
		wizardVersion nullable: false
		completedAt nullable: true
		completedBy nullable: true, maxSize: 255
		notes nullable: true, maxSize: 2000
	}

	static mapping = {
		draftJson type: 'text'
		notes type: 'text'
		table 'setup_state'
	}
}
