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

/**
 * SalesAdvanced light CRM opportunity (OI-S08 / G5). Pipeline stages; optional Customer link.
 * Company-scoped via {@code npubOwner}. Not full Lead CRM / SalesPro.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'name,stage,customerId', includeNames = true, includePackage = false)
class Opportunity implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	static final String STAGE_LEAD = 'LEAD'
	static final String STAGE_QUALIFIED = 'QUALIFIED'
	static final String STAGE_PROPOSAL = 'PROPOSAL'
	static final String STAGE_NEGOTIATION = 'NEGOTIATION'
	static final String STAGE_WON = 'WON'
	static final String STAGE_LOST = 'LOST'

	static final List<String> STAGES = [
			STAGE_LEAD, STAGE_QUALIFIED, STAGE_PROPOSAL, STAGE_NEGOTIATION, STAGE_WON, STAGE_LOST
	].asImmutable()

	static final List<String> OPEN_STAGES = [
			STAGE_LEAD, STAGE_QUALIFIED, STAGE_PROPOSAL, STAGE_NEGOTIATION
	].asImmutable()

	String name
	String description

	/** LEAD | QUALIFIED | PROPOSAL | NEGOTIATION | WON | LOST */
	String stage = STAGE_LEAD

	/** Optional Customer.id (same npubOwner) */
	Long customerId

	/** Snapshot / prospect name when no Customer yet */
	String contactName
	String contactEmail

	BigDecimal expectedAmountEur
	String currencyCode = 'EUR'
	Date expectedCloseDate

	/** Owner / assignee free text */
	String ownerName

	/** WEB | REFERRAL | COLD | PARTNER | OTHER */
	String source

	/** Set when converted to quote */
	Long resultingQuoteId

	Boolean active = Boolean.TRUE

	static hasMany = [activities: CrmActivity]

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)

		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		stage nullable: false, blank: false, maxSize: 32, inList: STAGES
		customerId nullable: true
		contactName nullable: true, maxSize: 255
		contactEmail nullable: true, maxSize: 255
		expectedAmountEur nullable: true, scale: 8, min: 0.0G
		currencyCode nullable: false, blank: false, maxSize: 8
		expectedCloseDate nullable: true
		ownerName nullable: true, maxSize: 255
		source nullable: true, maxSize: 32,
				inList: ['WEB', 'REFERRAL', 'COLD', 'PARTNER', 'OTHER']
		resultingQuoteId nullable: true
		active nullable: false
	}

	static mapping = {
		table 'sales_opportunity'
		description type: 'text'
		expectedAmountEur scale: 8
		stage index: 'sales_opportunity_stage_idx'
		customerId index: 'sales_opportunity_customer_idx'
		active index: 'sales_opportunity_active_idx'
	}

	def beforeValidate() {
		if (!status) {
			status = stage
		}
	}
}
