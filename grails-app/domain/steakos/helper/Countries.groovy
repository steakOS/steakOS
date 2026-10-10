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

/**
 * ISO 3166-1 country reference (system-wide, D2).
 * Seeded from SupportedLocale country codes; expand independently later.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'code')
@ToString(includes = 'code,name', includeNames = true, includePackage = false)
class Countries implements Serializable {

	private static final long serialVersionUID = 1L

	/** ISO 3166-1 alpha-2, e.g. DE */
	String code

	String name
	String description

	Boolean enabled = Boolean.TRUE

	static constraints = {
		code nullable: false, blank: false, maxSize: 2, unique: true, matches: /[A-Z]{2}/
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		enabled nullable: true
	}

	static mapping = {
		description type: 'text'
	}
}
