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

package steakos.configuration

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Auditable
import steakos.traits.Versioned
import steakos.traits.DomainTraitConstraints

/**
 * Company / legal entity (ADR-029 Path B).
 * PDR-026 — product UI name is “Company”; kind DEMO|LIVE for instance picker.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,code,kind', includeNames = true, includePackage = false)
class LegalEntity implements NpubOwned, AccessControl, Auditable, Versioned, Serializable {

	private static final long serialVersionUID = 1L

	static final String KIND_LIVE = 'LIVE'
	static final String KIND_DEMO = 'DEMO'
	static final String CODE_PRIMARY = 'primary'
	static final String CODE_ORANGE_STEAK = 'orange-steak-house'
	static final String CODE_HEARD_COOK = 'heard-cook-architects'

	String name
	String description

	/** Stable code e.g. primary | orange-steak-house | heard-cook-architects */
	String code
	/** LIVE (operator company) or DEMO (showcase instances) */
	String kind = KIND_LIVE

	/** e.g. SOLE, LLC, GMBH, SA, OTHER */
	String legalForm
	Date incorporationDate
	String taxId
	String countryCode
	/** ISO functional currency e.g. EUR */
	String functionalCurrency = 'EUR'
	/** Prefer dual EUR+sats display where product supports it */
	Boolean dualSats = Boolean.TRUE
	String registeredAddressText

	boolean isDemo() {
		KIND_DEMO == kind
	}

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		code nullable: true, blank: true, maxSize: 64, unique: true
		kind nullable: false, blank: false, maxSize: 16, inList: [KIND_LIVE, KIND_DEMO]
		legalForm nullable: true, maxSize: 64
		incorporationDate nullable: true
		taxId nullable: true, maxSize: 64
		countryCode nullable: true, maxSize: 8
		functionalCurrency nullable: false, blank: false, maxSize: 8
		dualSats nullable: false
		registeredAddressText nullable: true, maxSize: 2000
	}

	static mapping = {
		description type: 'text'
		registeredAddressText type: 'text'
		table 'legal_entity'
		code index: 'legal_entity_code_idx'
		kind index: 'legal_entity_kind_idx'
	}
}
