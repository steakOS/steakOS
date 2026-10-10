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

/**
 * User-level preferences (ADR-029 Path B).
 * Complements skeleton UserConfig; wizard writes here.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = ['username', 'prefKey'])
@ToString(includes = 'prefKey', includeNames = true, includePackage = false)
class UserPreference implements Serializable {

	private static final long serialVersionUID = 1L

	String username
	String prefKey
	String prefValue
	/** profile, defaults, ui, notifications, forms */
	String category = 'ui'
	String lastUpdatedBy

	Date dateCreated
	Date lastUpdated

	static constraints = {
		username nullable: false, blank: false, maxSize: 255
		prefKey nullable: false, blank: false, maxSize: 255
		prefValue nullable: true, maxSize: 100000
		category nullable: false, blank: false, maxSize: 64
		lastUpdatedBy nullable: true, maxSize: 255
	}

	static mapping = {
		prefValue type: 'text'
		table 'user_preference'
		username index: 'user_pref_user_idx'
		prefKey index: 'user_pref_key_idx'
	}
}
