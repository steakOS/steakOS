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

package steakos.sales

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Attachable
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints
import steakos.traits.Assignable

/**
 * Individual or organization customer (S-S0).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name', includeNames = true, includePackage = false)
class Customer implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Assignable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** PERSON | ORGANIZATION */
	String customerKind = 'ORGANIZATION'

	String email
	String phone
	String contactName

	/** Optional Nostr npub of the customer */
	String customerNpub

	String taxId
	String currencyCode = 'EUR'

	/** Partner communication locale (OI-I18N02); blank → en_US outbound fallback */
	String communicationLocale

	Boolean active = Boolean.TRUE

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAssignable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		customerKind nullable: false, blank: false, maxSize: 32,
				inList: ['PERSON', 'ORGANIZATION']
		email nullable: true, maxSize: 255
		phone nullable: true, maxSize: 64
		contactName nullable: true, maxSize: 255
		customerNpub nullable: true, maxSize: 200
		taxId nullable: true, maxSize: 64
		currencyCode nullable: false, blank: false, maxSize: 8
		communicationLocale nullable: true, maxSize: 16
		active nullable: true
	}

	static mapping = {
		description type: 'text'
	}
}
