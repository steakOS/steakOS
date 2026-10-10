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
import steakos.traits.DomainTraitConstraints

/**
 * Internal light ITSM ticket (DIG0/DIG3). Customer cases stay AfterSales.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'ticketNumber')
@ToString(includes = 'ticketNumber,ticketStatus', includeNames = true, includePackage = false)
class ItTicket implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String ticketNumber
	/** INCIDENT | REQUEST */
	String ticketKind = 'INCIDENT'
	/** LOW | NORMAL | HIGH | URGENT */
	String priority = 'NORMAL'
	/** OPEN | IN_PROGRESS | RESOLVED | CLOSED | CANCELLED */
	String ticketStatus = 'OPEN'
	Long configItemId
	Long solutionId
	Long systemId
	Date openedAt
	Date resolvedAt
	Date closedAt
	String assignedTo

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 8000
		ticketNumber nullable: true, maxSize: 64
		ticketKind nullable: false, blank: false, maxSize: 16,
				inList: ['INCIDENT', 'REQUEST']
		priority nullable: false, blank: false, maxSize: 16,
				inList: ['LOW', 'NORMAL', 'HIGH', 'URGENT']
		ticketStatus nullable: false, blank: false, maxSize: 32,
				inList: ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED']
		configItemId nullable: true
		solutionId nullable: true
		systemId nullable: true
		openedAt nullable: true
		resolvedAt nullable: true
		closedAt nullable: true
		assignedTo nullable: true, maxSize: 255
	}

	static mapping = {
		description type: 'text'
		ticketNumber index: 'digital_ticket_number_idx'
		ticketStatus index: 'digital_ticket_status_idx'
	}
}
