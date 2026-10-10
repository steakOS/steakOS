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
import steakos.traits.DomainTraitConstraints

/**
 * Flexible company settings key/value (ADR-029 Path B).
 * Templates use synthetic npubOwner e.g. SYSTEM_TEMPLATE_IRON.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'settingKey')
@ToString(includes = 'settingKey', includeNames = true, includePackage = false)
class CompanySetting implements NpubOwned, Serializable {

	private static final long serialVersionUID = 1L

	/** e.g. template.tier, finance.default_tax_rate */
	String settingKey
	String settingValue
	/** general, finance, ui, nostr, plugin, template, setup */
	String category = 'general'
	/** string, boolean, number, json */
	String dataType = 'string'

	Date dateCreated
	Date lastUpdated

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		settingKey nullable: false, blank: false, maxSize: 255
		settingValue nullable: true, maxSize: 100000
		category nullable: false, blank: false, maxSize: 64
		dataType nullable: false, blank: false, maxSize: 16
	}

	static mapping = {
		settingValue type: 'text'
		table 'company_setting'
		settingKey index: 'company_setting_key_idx'
		npubOwner index: 'company_setting_npub_idx'
	}
}
