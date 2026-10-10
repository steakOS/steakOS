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
 * CSV/XLSX import job (Wave B / OI-I01).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,packType,status', includeNames = true, includePackage = false)
class ImportJob implements NpubOwned, Serializable {

	private static final long serialVersionUID = 1L

	/** CUSTOMERS | VENDORS | COA | OPENING_BALANCES */
	String packType
	/** PENDING | VALIDATING | READY | RUNNING | COMPLETED | FAILED */
	String status = 'PENDING'
	String fileName
	String contentType
	String rawText
	String mappingJson
	String reportJson
	Integer rowCount = 0
	Integer createdCount = 0
	Integer updatedCount = 0
	Integer skippedCount = 0
	Integer errorCount = 0
	String errorSummary

	Date dateCreated
	Date lastUpdated

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		packType nullable: false, blank: false, maxSize: 32
		status nullable: false, blank: false, maxSize: 16
		fileName nullable: true, maxSize: 512
		contentType nullable: true, maxSize: 128
		rawText nullable: true, maxSize: 5_000_000
		mappingJson nullable: true, maxSize: 100_000
		reportJson nullable: true, maxSize: 2_000_000
		rowCount nullable: false
		createdCount nullable: false
		updatedCount nullable: false
		skippedCount nullable: false
		errorCount nullable: false
		errorSummary nullable: true, maxSize: 4000
	}

	static mapping = {
		rawText type: 'text'
		mappingJson type: 'text'
		reportJson type: 'text'
		errorSummary type: 'text'
		table 'import_job'
	}
}
