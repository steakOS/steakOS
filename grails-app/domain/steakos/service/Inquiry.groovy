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

package steakos.service

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.NostrPublishable
import steakos.traits.DomainTraitConstraints
import steakos.traits.Assignable

/**
 * Customer service case spine (APQC 6.2.2 / 6.2.3, AS1: kind discriminates complaint).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'inquiryNumber')
@ToString(includes = 'inquiryNumber,kind,caseStatus', includeNames = true, includePackage = false)
class Inquiry implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, NostrPublishable, Assignable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String inquiryNumber

	Long customerId
	String customerName

	/** EMAIL | CHAT | PHONE | WEB | OTHER */
	String channel = 'EMAIL'
	/** REQUEST | PROBLEM | COMPLAINT | OTHER — AS1 */
	String kind = 'REQUEST'
	/** LOW | NORMAL | HIGH | URGENT */
	String priority = 'NORMAL'

	ServiceLevel serviceLevel
	Long salesOrderId
	Long serviceOrderId

	Date openedAt
	Date responseDueAt
	Date resolveDueAt
	Date resolvedAt
	Date closedAt

	String assignedTo
	/** OPEN | IN_PROGRESS | RESOLVED | CLOSED | CANCELLED */
	String caseStatus = 'OPEN'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAssignable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyNostrPublishable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		inquiryNumber nullable: true, maxSize: 64
		customerId nullable: true
		customerName nullable: true, maxSize: 255
		channel nullable: false, blank: false, maxSize: 16,
				inList: ['EMAIL', 'CHAT', 'PHONE', 'WEB', 'OTHER']
		kind nullable: false, blank: false, maxSize: 16,
				inList: ['REQUEST', 'PROBLEM', 'COMPLAINT', 'OTHER']
		priority nullable: false, blank: false, maxSize: 16,
				inList: ['LOW', 'NORMAL', 'HIGH', 'URGENT']
		serviceLevel nullable: true
		salesOrderId nullable: true
		serviceOrderId nullable: true
		openedAt nullable: true
		responseDueAt nullable: true
		resolveDueAt nullable: true
		resolvedAt nullable: true
		closedAt nullable: true
		assignedTo nullable: true, maxSize: 255
		caseStatus nullable: false, blank: false, maxSize: 32,
				inList: ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED']
	}

	static mapping = {
		description type: 'text'
		inquiryNumber index: 'inquiry_number_idx'
		caseStatus index: 'inquiry_status_idx'
	}
}
