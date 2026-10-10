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
 * System help topic index (Wave B / OI-H01). Bodies live as ObjectContent.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'helpKey')
@ToString(includes = 'helpKey,title', includeNames = true, includePackage = false)
class HelpTopic implements Serializable {

	private static final long serialVersionUID = 1L

	String helpKey
	String title
	/** Glob/prefix for route match e.g. /sales, /finance/invoices */
	String routePattern
	String family
	Integer sortOrder = 0
	Boolean systemDefault = Boolean.TRUE

	Date dateCreated
	Date lastUpdated

	static constraints = {
		helpKey nullable: false, blank: false, maxSize: 128, unique: true
		title nullable: false, blank: false, maxSize: 255
		routePattern nullable: true, maxSize: 255
		family nullable: true, maxSize: 64
		sortOrder nullable: false
		systemDefault nullable: false
	}

	static mapping = {
		table 'help_topic'
		helpKey index: 'help_topic_key_idx'
	}
}
