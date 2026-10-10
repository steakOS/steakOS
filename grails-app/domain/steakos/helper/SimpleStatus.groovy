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
 * Generic status control catalog (system-wide, D2).
 * Seeded values: NEW, WORKING, CHECKED, COMPLETED (label Done), CANCELLED.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'code')
@ToString(includes = 'code,name', includeNames = true, includePackage = false)
class SimpleStatus implements Serializable {

	private static final long serialVersionUID = 1L

	/** Stable code, e.g. NEW, WORKING */
	String code

	String name
	String description

	/** Display / sort order in pick-lists */
	Integer sortOrder = 0

	Boolean enabled = Boolean.TRUE

	static constraints = {
		code nullable: false, blank: false, maxSize: 32, unique: true, matches: /[A-Z][A-Z0-9_]*/
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		sortOrder nullable: true
		enabled nullable: true
	}

	static mapping = {
		description type: 'text'
	}
}
