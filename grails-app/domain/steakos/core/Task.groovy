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

package steakos.core

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.DomainTraitConstraints

/**
 * Single work-item domain (Task unification OI-WK01).
 * Optional human and/or agent assignee, schedule window, Eisenhower urgency × importance.
 * Lifecycle via Classifiable.status: OPEN | IN_PROGRESS | DONE | CANCELLED.
 * Content companion attaches via Object Companion (core / Task / id).
 * See docs/domain classes/steakos app/TASK-UNIFICATION-PLAN.md
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name', includeNames = true, includePackage = false)
class Task implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** Human assignee (npub or user:username convention). */
	String assigneeHumanNpub

	/** Optional agent id (loose Long → agents.Agent). */
	Long assigneeAgentId

	Date startAt
	Date endAt
	/** Due / hard deadline (Eisenhower “when”). */
	Date deadlineAt

	/** 1 = low · 2 = medium · 3 = high */
	Integer urgency = 2
	/** 1 = low · 2 = medium · 3 = high */
	Integer importance = 2

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		assigneeHumanNpub nullable: true, maxSize: 128
		assigneeAgentId nullable: true
		startAt nullable: true
		endAt nullable: true
		deadlineAt nullable: true
		urgency nullable: false, min: 1, max: 3
		importance nullable: false, min: 1, max: 3
	}

	static mapping = {
		description type: 'text'
		deadlineAt index: 'task_deadline_idx'
		assigneeHumanNpub index: 'task_assignee_human_idx'
		assigneeAgentId index: 'task_assignee_agent_idx'
	}

	def beforeValidate() {
		if (urgency == null) {
			urgency = 2
		}
		if (importance == null) {
			importance = 2
		}
		if (status == null || status.isEmpty()) {
			status = 'OPEN'
		}
	}
}
