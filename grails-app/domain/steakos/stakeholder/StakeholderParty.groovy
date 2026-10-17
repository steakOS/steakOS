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

package steakos.stakeholder

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
 * StakeholderBasics thin: external-relations / ethics party register (G7).
 * May reference customer/vendor ids as free-text externalRef — does not own Sales/SCM masters.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,partyKind,status', includeNames = true, includePackage = false)
class StakeholderParty implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description
	/** REGULATOR | COMMUNITY | MEDIA | PARTNER | NGO | INVESTOR | OTHER */
	String partyKind = 'OTHER'
	String contactName
	String contactEmail
	/** Light ethics / PR note (not a full policy document) */
	String ethicsNote
	/** Optional external catalogue / customer / vendor id (string ref only) */
	String externalRef
	/** ACTIVE | INACTIVE | WATCH */
	String status = 'ACTIVE'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		partyKind nullable: false, blank: false, maxSize: 32,
				inList: ['REGULATOR', 'COMMUNITY', 'MEDIA', 'PARTNER', 'NGO', 'INVESTOR', 'OTHER']
		contactName nullable: true, maxSize: 255
		contactEmail nullable: true, maxSize: 255
		ethicsNote nullable: true, maxSize: 4000
		externalRef nullable: true, maxSize: 128
		status nullable: false, blank: false, maxSize: 32,
				inList: ['ACTIVE', 'INACTIVE', 'WATCH']
	}

	static mapping = {
		description type: 'text'
		ethicsNote type: 'text'
		status index: 'stakeholder_party_status_idx'
		partyKind index: 'stakeholder_party_kind_idx'
	}
}
