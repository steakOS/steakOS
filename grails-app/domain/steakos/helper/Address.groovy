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

package steakos.helper

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
 * International postal address + GPS.
 * Decisions A1–A8: see ADDRESS-CONCEPT.md
 * Inspiration: legacy noERP Address (street lines, locality, country, type).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'name')
@ToString(includes = 'name,city', includeNames = true, includePackage = false)
class Address implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** e.g. COMMERCIAL, RESIDENTIAL, DELIVERY, PERSONAL, POBOX, GOVERNMENTAL (A1) */
	String addressType = 'COMMERCIAL'

	String street
	String streetNo
	String street2
	String street3
	String street4
	String street5
	String street6

	String lot
	String building
	String room
	String locality

	String postalCode
	String city
	String region
	String county

	Countries country

	BigDecimal latitude
	BigDecimal longitude

	Boolean active = Boolean.TRUE

	String formatLine() {
		String streetPart = [street, streetNo].findAll { it }?.join(' ')
		String cityPart = [postalCode, city].findAll { it }?.join(' ')
		String ctry = country?.code
		[streetPart, cityPart, ctry].findAll { it }?.join(', ')
	}

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		addressType nullable: false, blank: false, maxSize: 32

		street nullable: false, blank: false, maxSize: 255
		streetNo nullable: true, maxSize: 32
		street2 nullable: true, maxSize: 255
		street3 nullable: true, maxSize: 255
		street4 nullable: true, maxSize: 255
		street5 nullable: true, maxSize: 255
		street6 nullable: true, maxSize: 255

		lot nullable: true, maxSize: 64
		building nullable: true, maxSize: 128
		room nullable: true, maxSize: 64
		locality nullable: true, maxSize: 128

		postalCode nullable: true, maxSize: 32
		city nullable: false, blank: false, maxSize: 128
		region nullable: true, maxSize: 128
		county nullable: true, maxSize: 128

		country nullable: false

		latitude nullable: true, scale: 8
		longitude nullable: true, scale: 8
		active nullable: true
	}

	static mapping = {
		description type: 'text'
		latitude scale: 8
		longitude scale: 8
	}
}
