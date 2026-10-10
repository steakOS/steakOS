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
 * Append-only learning signals (AR4 A). Not a vector store.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,memoryKind', includeNames = true, includePackage = false)
class Memory implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	Long agentId
	/** FEEDBACK | EVAL | LESSON | MARKETPLACE_REF */
	String memoryKind = 'FEEDBACK'
	String payloadJson
	BigDecimal score
	String sourceRef
	/** ACTIVE | ARCHIVED */
	String memoryStatus = 'ACTIVE'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		agentId nullable: true
		memoryKind nullable: false, blank: false, maxSize: 32,
				inList: ['FEEDBACK', 'EVAL', 'LESSON', 'MARKETPLACE_REF']
		payloadJson nullable: true, maxSize: 16000
		score nullable: true, scale: 4
		sourceRef nullable: true, maxSize: 255
		memoryStatus nullable: false, blank: false, maxSize: 32,
				inList: ['ACTIVE', 'ARCHIVED']
	}

	static mapping = {
		table 'agent_memory'
		description type: 'text'
		payloadJson type: 'text'
		agentId index: 'agent_memory_agent_idx'
		memoryKind index: 'agent_memory_kind_idx'
	}
}
