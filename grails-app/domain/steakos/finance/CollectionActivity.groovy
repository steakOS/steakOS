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

package steakos.finance

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.NpubOwned
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints

/**
 * FinanceAdvanced collections contact / promise-to-pay against an AR invoice (APQC 9.2.4).
 * Company-scoped via {@code npubOwner}; invoice id is loose FK for monorepo staging.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'kind,invoiceId,activityAt', includeNames = true, includePackage = false)
class CollectionActivity implements NpubOwned, AccessControl, Classifiable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	/** Invoice.id (same npubOwner) */
	Long invoiceId

	/** PROMISE | CONTACT | NOTE | REMINDER | CREDIT_HOLD */
	String kind = 'NOTE'

	/** PHONE | EMAIL | IN_PERSON | OTHER | SYSTEM */
	String channel

	Date activityAt

	/** Promise-to-pay amount (functional / primary rail) */
	BigDecimal promisedAmountEur

	/** Promise-to-pay date */
	Date promisedPayBy

	/** OPEN | KEPT | BROKEN | CANCELLED — meaningful for PROMISE; else OPEN */
	String promiseStatus = 'OPEN'

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		invoiceId nullable: false
		kind nullable: false, blank: false, maxSize: 32,
				inList: ['PROMISE', 'CONTACT', 'NOTE', 'REMINDER', 'CREDIT_HOLD']
		channel nullable: true, maxSize: 32,
				inList: ['PHONE', 'EMAIL', 'IN_PERSON', 'OTHER', 'SYSTEM']
		activityAt nullable: false
		promisedAmountEur nullable: true, scale: 8, min: 0.0G
		promisedPayBy nullable: true
		promiseStatus nullable: false, blank: false, maxSize: 32,
				inList: ['OPEN', 'KEPT', 'BROKEN', 'CANCELLED']
	}

	static mapping = {
		description type: 'text'
		invoiceId index: 'collection_activity_invoice_idx'
		kind index: 'collection_activity_kind_idx'
		activityAt index: 'collection_activity_at_idx'
		promisedAmountEur scale: 8
	}

	def beforeValidate() {
		if (!activityAt) {
			activityAt = new Date()
		}
		if (!name) {
			name = "${kind ?: 'NOTE'} #${invoiceId ?: '?'}"
		}
		if (!promiseStatus) {
			promiseStatus = 'OPEN'
		}
		if (!status) {
			status = promiseStatus
		}
	}
}
