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

package steakos.agents

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.DomainTraitConstraints

/**
 * HITL gate with folded proposal payload (AR3 A).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,decision', includeNames = true, includePackage = false)
class Approval implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	Long agentId
	String proposalRef
	String proposalJson
	String targetFamily
	/** PENDING | APPROVED | REJECTED | CANCELLED */
	String decision = 'PENDING'
	Date decidedAt
	String decidedByNpub
	/** LOW | MED | HIGH */
	String riskLevel = 'LOW'
	String nostrEventId

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		agentId nullable: true
		proposalRef nullable: true, maxSize: 255
		proposalJson nullable: true, maxSize: 16000
		targetFamily nullable: true, maxSize: 64
		decision nullable: false, blank: false, maxSize: 32,
				inList: ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED']
		decidedAt nullable: true
		decidedByNpub nullable: true, maxSize: 128
		riskLevel nullable: false, blank: false, maxSize: 16,
				inList: ['LOW', 'MED', 'HIGH']
		nostrEventId nullable: true, maxSize: 128
	}

	static mapping = {
		table 'agent_approval'
		description type: 'text'
		proposalJson type: 'text'
		decision index: 'agent_approval_decision_idx'
	}
}
