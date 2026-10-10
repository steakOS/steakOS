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

package steakos.human

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

/**
 * Payroll period header (APQC 7.5.4). Lines → Finance PayrollItem (HR3 B). HR7 dual amounts.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'period')
@ToString(includes = 'period,payrollStatus', includeNames = true, includePackage = false)
class Payroll implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Versioned, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** YYYY-MM */
	String period
	Date periodStart
	Date periodEnd

	BigDecimal totalEur = BigDecimal.ZERO
	Long totalSats
	String currencyCode = 'EUR'

	/** OPEN | READY | SUBMITTED | CLOSED */
	String payrollStatus = 'OPEN'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		DomainTraitConstraints.applyVersioned(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		period nullable: false, blank: false, maxSize: 7
		periodStart nullable: true
		periodEnd nullable: true
		totalEur nullable: false, scale: 8
		totalSats nullable: true
		currencyCode nullable: false, blank: false, maxSize: 8
		payrollStatus nullable: false, blank: false, maxSize: 32,
				inList: ['OPEN', 'READY', 'SUBMITTED', 'CLOSED']
	}

	static mapping = {
		description type: 'text'
		totalEur scale: 8
		period index: 'hr_payroll_period_idx'
	}
}
