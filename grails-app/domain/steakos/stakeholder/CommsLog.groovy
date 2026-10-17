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
 * StakeholderBasics thin: communications log entry (G7).
 * Linked to {@link StakeholderParty} by id within the same tenant.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,subject,channel,status', includeNames = true, includePackage = false)
class CommsLog implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	Long stakeholderPartyId
	String subject
	String summary
	/** EMAIL | MEETING | CALL | PRESS | OTHER */
	String channel = 'OTHER'
	/** IN | OUT | INTERNAL */
	String direction = 'OUT'
	Date occurredAt
	/** LOGGED | DRAFT */
	String status = 'LOGGED'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		stakeholderPartyId nullable: false
		subject nullable: false, blank: false, maxSize: 255
		summary nullable: true, maxSize: 8000
		channel nullable: false, blank: false, maxSize: 32,
				inList: ['EMAIL', 'MEETING', 'CALL', 'PRESS', 'OTHER']
		direction nullable: false, blank: false, maxSize: 16,
				inList: ['IN', 'OUT', 'INTERNAL']
		occurredAt nullable: true
		status nullable: false, blank: false, maxSize: 32,
				inList: ['LOGGED', 'DRAFT']
	}

	static mapping = {
		summary type: 'text'
		status index: 'stakeholder_comms_status_idx'
		stakeholderPartyId index: 'stakeholder_comms_party_idx'
		channel index: 'stakeholder_comms_channel_idx'
	}
}
