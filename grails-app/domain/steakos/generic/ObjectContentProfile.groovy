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

package steakos.generic

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.traits.Classifiable
import steakos.traits.DomainTraitConstraints

/**
 * Allowlisted BODY content types per host resource (CT4).
 * systemDefault rows are global; allowedTypeCodes is comma-separated ordered list.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,hostPackage,hostResource', includeNames = true, includePackage = false)
class ObjectContentProfile implements Classifiable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String hostPackage
	String hostResource
	/** Comma-separated ordered codes, e.g. Note,Target,Risk */
	String allowedTypeCodes
	Boolean systemDefault = Boolean.TRUE

	static constraints = {
		DomainTraitConstraints.applyClassifiable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		hostPackage nullable: false, blank: false, maxSize: 64
		hostResource nullable: false, blank: false, maxSize: 128
		allowedTypeCodes nullable: false, blank: false, maxSize: 2000
		systemDefault nullable: false
	}

	static mapping = {
		table 'object_content_profile'
		description type: 'text'
		hostPackage index: 'obj_content_profile_host_idx'
		hostResource index: 'obj_content_profile_host_idx'
	}

	List<String> typeCodeList() {
		if (!allowedTypeCodes) return []
		allowedTypeCodes.split(',')*.trim().findAll { it } as List<String>
	}
}
