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
import steakos.traits.Versioned
import steakos.traits.DomainTraitConstraints
import steakos.traits.Assignable

/**
 * Product / order warranty cover registration (APQC 6.3.1, AS-S0).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'warrantyNumber')
@ToString(includes = 'warrantyNumber,coverStatus', includeNames = true, includePackage = false)
class Warranty implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Versioned, Assignable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String warrantyNumber

	Long customerId
	String customerName
	Long catalogueItemId
	String productName
	Long salesOrderId

	Date validFrom
	Date validTo

	/** DRAFT | ACTIVE | EXPIRED | VOID */
	String coverStatus = 'DRAFT'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAssignable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		warrantyNumber nullable: true, maxSize: 64
		customerId nullable: true
		customerName nullable: true, maxSize: 255
		catalogueItemId nullable: true
		productName nullable: true, maxSize: 255
		salesOrderId nullable: true
		validFrom nullable: true
		validTo nullable: true
		coverStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'ACTIVE', 'EXPIRED', 'VOID']
	}

	static mapping = {
		description type: 'text'
		warrantyNumber index: 'warranty_number_idx'
		coverStatus index: 'warranty_status_idx'
	}
}
