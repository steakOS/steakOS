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
 * Agent identity & lifecycle (APQC 14.x). Hermes default; multi-runtime AR1 C.
 * taskContext ≈ AGENTS.md; soul ≈ SOUL.md (AR2).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,agentKey,agentStatus', includeNames = true, includePackage = false)
class Agent implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String agentKey
	/** HERMES | CUSTOM | EXTERNAL — AR1 C */
	String runtimeKind = 'HERMES'
	String endpointRef
	String toolsJson
	/** DRAFT | ACTIVE | PAUSED | RETIRED */
	String agentStatus = 'DRAFT'
	String versionLabel
	String agentNpub
	/** Optional primary hive (AR2 A) */
	Long hiveId
	/** AGENTS.md-style operating context */
	String taskContext
	/** SOUL.md-style character / values */
	String soul

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		agentKey nullable: false, blank: false, maxSize: 64
		runtimeKind nullable: false, blank: false, maxSize: 32,
				inList: ['HERMES', 'CUSTOM', 'EXTERNAL']
		endpointRef nullable: true, maxSize: 512
		toolsJson nullable: true, maxSize: 8000
		agentStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'ACTIVE', 'PAUSED', 'RETIRED']
		versionLabel nullable: true, maxSize: 64
		agentNpub nullable: true, maxSize: 128
		hiveId nullable: true
		taskContext nullable: true, maxSize: 16000
		soul nullable: true, maxSize: 16000
	}

	static mapping = {
		table 'agent_resource'
		description type: 'text'
		toolsJson type: 'text'
		taskContext type: 'text'
		soul type: 'text'
		agentKey index: 'agent_resource_key_idx'
		agentStatus index: 'agent_resource_status_idx'
		hiveId index: 'agent_resource_hive_idx'
	}
}
