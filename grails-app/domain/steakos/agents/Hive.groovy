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
 * Multi-agent team by APQC area. One Hive Master coordinates members (AR2).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,hiveStatus', includeNames = true, includePackage = false)
class Hive implements NpubOwned, AccessControl, Classifiable, Attachable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String apqcAreaCode
	/** ACTIVE | CLOSED | DRAFT */
	String hiveStatus = 'DRAFT'
	/** Hive Master agent id — required to activate */
	Long hiveMasterAgentId
	Integer maxAgents

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		apqcAreaCode nullable: true, maxSize: 32
		hiveStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'ACTIVE', 'CLOSED']
		hiveMasterAgentId nullable: true
		maxAgents nullable: true, min: 1
	}

	static mapping = {
		// Keep physical name "hive" (existing DBs / running jars). Index name is agent_* for consistency.
		description type: 'text'
		hiveStatus index: 'agent_hive_status_idx'
	}
}
