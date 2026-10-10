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

/**
 * Step within a process (CAP2 B: stepNo + optional predecessorId).
 * Metadata D5: stepCode, deskHint, outcomeStatus, actorHint.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,stepNo', includeNames = true, includePackage = false)
class ProcessStep implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	Long processId
	Integer stepNo = 1
	/** Optional light graph predecessor (not full BPMN). */
	Long predecessorId

	/** Stable code e.g. scm.po.send */
	String stepCode
	/** SPA path hint e.g. /scm */
	String deskHint
	/** Status after this step e.g. SENT */
	String outcomeStatus
	/** SYSTEM | HUMAN | AGENT */
	String actorHint

	/** A7 hover tip — plain-language what this step does */
	String tipWhat
	/** Who typically acts */
	String tipWho
	/** Where in the product (desk / document) */
	String tipWhere
	/** Common pitfall */
	String tipPitfall
	/** Honest stub / planned step (never fake L4 depth) */
	Boolean stub = Boolean.FALSE
	/** Optional Help topic key */
	String helpKey

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		processId nullable: false
		stepNo nullable: false, min: 1
		predecessorId nullable: true
		stepCode nullable: true, maxSize: 64
		deskHint nullable: true, maxSize: 255
		outcomeStatus nullable: true, maxSize: 64
		actorHint nullable: true, blank: true, maxSize: 32,
				inList: ['SYSTEM', 'HUMAN', 'AGENT']
		tipWhat nullable: true, maxSize: 2000
		tipWho nullable: true, maxSize: 128
		tipWhere nullable: true, maxSize: 255
		tipPitfall nullable: true, maxSize: 2000
		stub nullable: true
		helpKey nullable: true, maxSize: 128
	}

	static mapping = {
		description type: 'text'
		tipWhat type: 'text'
		tipPitfall type: 'text'
		processId index: 'cap_process_step_proc_idx'
		stepCode index: 'cap_process_step_code_idx'
	}
}
