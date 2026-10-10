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

package steakos.development

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
 * DevelopmentBasics thin: NPD / revision project (separate from capability.Project) (G4).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,name,projectKind,projectStatus', includeNames = true, includePackage = false)
class DevelopmentProject implements NpubOwned, AccessControl, Classifiable, Attachable, Auditable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description
	/** NPD | REVISION | OTHER */
	String projectKind = 'NPD'
	/** DRAFT | ACTIVE | DONE | CANCELLED */
	String projectStatus = 'DRAFT'
	/** Optional link to commercial catalogue master */
	Long catalogueItemId
	Date startDate
	Date endDate

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		DomainTraitConstraints.applyAttachable(delegate)
		DomainTraitConstraints.applyAuditable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		projectKind nullable: false, blank: false, maxSize: 32, inList: ['NPD', 'REVISION', 'OTHER']
		projectStatus nullable: false, blank: false, maxSize: 32,
				inList: ['DRAFT', 'ACTIVE', 'DONE', 'CANCELLED']
		catalogueItemId nullable: true
		startDate nullable: true
		endDate nullable: true
	}

	static mapping = {
		description type: 'text'
		projectKind index: 'dev_project_kind_idx'
		projectStatus index: 'dev_project_status_idx'
	}
}
