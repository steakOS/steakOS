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
import steakos.traits.Auditable
import steakos.traits.DomainTraitConstraints

/**
 * SalesAdvanced CRM activity against an {@link Opportunity} (OI-S08 / G5).
 * Company-scoped via {@code npubOwner}; opportunity id is loose FK for monorepo staging.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'kind,opportunityId,activityAt', includeNames = true, includePackage = false)
class CrmActivity implements NpubOwned, AccessControl, Classifiable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	static final List<String> KINDS = ['NOTE', 'CALL', 'EMAIL', 'MEETING', 'TASK', 'OTHER'].asImmutable()
	static final List<String> CHANNELS = ['PHONE', 'EMAIL', 'IN_PERSON', 'OTHER', 'SYSTEM'].asImmutable()

	String name
	String description

	Opportunity opportunity

	/** NOTE | CALL | EMAIL | MEETING | TASK | OTHER */
	String kind = 'NOTE'

	/** PHONE | EMAIL | IN_PERSON | OTHER | SYSTEM */
	String channel

	Date activityAt

	/** Optional due date for TASK */
	Date dueDate

	static belongsTo = [opportunity: Opportunity]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		opportunity nullable: false
		kind nullable: false, blank: false, maxSize: 32, inList: KINDS
		channel nullable: true, maxSize: 32, inList: CHANNELS
		activityAt nullable: false
		dueDate nullable: true
	}

	static mapping = {
		table 'sales_crm_activity'
		description type: 'text'
		opportunity index: 'sales_crm_activity_opp_idx'
		kind index: 'sales_crm_activity_kind_idx'
		activityAt index: 'sales_crm_activity_at_idx'
	}

	def beforeValidate() {
		if (!activityAt) {
			activityAt = new Date()
		}
		if (!name) {
			Long oid = opportunity?.id
			name = "${kind ?: 'NOTE'} #${oid ?: '?'}"
		}
		if (!status) {
			status = 'OPEN'
		}
	}
}
