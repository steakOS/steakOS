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

package steakos.strategy

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
 * StrategyBasics thin: vision, mission, strategy, need, or factor notes (G4).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,itemKind,status', includeNames = true, includePackage = false)
class StrategyItem implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description
	/** VISION | MISSION | STRATEGY | NEED | FACTOR */
	String itemKind = 'STRATEGY'
	/** DRAFT | ACTIVE | ARCHIVED */
	String status = 'DRAFT'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 8000
		itemKind nullable: false, blank: false, maxSize: 32,
				inList: ['VISION', 'MISSION', 'STRATEGY', 'NEED', 'FACTOR']
		status nullable: false, blank: false, maxSize: 32, inList: ['DRAFT', 'ACTIVE', 'ARCHIVED']
	}

	static mapping = {
		description type: 'text'
		itemKind index: 'strategy_item_kind_idx'
		status index: 'strategy_item_status_idx'
	}
}
