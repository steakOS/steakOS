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

package steakos.digital

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.Versioned
import steakos.traits.DynamicExtensible
import steakos.traits.DomainTraitConstraints

/**
 * Productized capability / integration (APQC 8.5, DIG1). Optional systemId (DIG4).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,lifecycleStatus', includeNames = true, includePackage = false)
class Solution implements NpubOwned, AccessControl, Classifiable, Attachable,
		Auditable, Versioned, DynamicExtensible, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** BTCPAY | NOSTR | RELAY | WEBHOOK | CUSTOM | OTHER */
	String integrationType = 'OTHER'
	/** DRAFT | ACTIVE | RETIRED | CANCELLED */
	String lifecycleStatus = 'DRAFT'
	String endpointRef
	String versionLabel
	/** Optional platform System (DIG4) — stored as id to avoid GORM name clash noise */
	Long systemId

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)
		DomainTraitConstraints.applyDynamicExtensible(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		integrationType nullable: false, blank: false, maxSize: 32,
				inList: ['BTCPAY', 'NOSTR', 'RELAY', 'WEBHOOK', 'CUSTOM', 'OTHER']
		lifecycleStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'ACTIVE', 'RETIRED', 'CANCELLED']
		endpointRef nullable: true, maxSize: 2000
		versionLabel nullable: true, maxSize: 64
		systemId nullable: true
	}

	static mapping = {
		description type: 'text'
		lifecycleStatus index: 'digital_solution_status_idx'
		integrationType index: 'digital_solution_type_idx'
	}
}
