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

package steakos.grrc

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
 * GRRCBasics thin risk register row (G7 / APQC 11.1).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,status,severity', includeNames = true, includePackage = false)
class RiskItem implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description
	/** OPEN | MITIGATING | CLOSED | ACCEPTED */
	String status = 'OPEN'
	/** LOW | MEDIUM | HIGH | CRITICAL */
	String severity = 'MEDIUM'
	/** LOW | MEDIUM | HIGH */
	String likelihood = 'MEDIUM'
	/** Person or role responsible (free text, thin) */
	String ownerName
	/** Treatment / mitigation note (light) */
	String treatmentNote

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 8000
		status nullable: false, blank: false, maxSize: 32,
				inList: ['OPEN', 'MITIGATING', 'CLOSED', 'ACCEPTED']
		severity nullable: false, blank: false, maxSize: 32,
				inList: ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
		likelihood nullable: false, blank: false, maxSize: 32,
				inList: ['LOW', 'MEDIUM', 'HIGH']
		ownerName nullable: true, maxSize: 255
		treatmentNote nullable: true, maxSize: 4000
	}

	static mapping = {
		description type: 'text'
		treatmentNote type: 'text'
		status index: 'grrc_risk_status_idx'
		severity index: 'grrc_risk_severity_idx'
	}
}
