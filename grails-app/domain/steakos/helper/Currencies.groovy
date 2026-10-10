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
 * ISO 4217 currency reference (system-wide, core D2 / OI-CUR01).
 * Seeded from {@link steakos.helper.CurrencyCatalogCodes} (fiat only — no BTC).
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'code')
@ToString(includes = 'code,name', includeNames = true, includePackage = false)
class Currencies implements Serializable {

	private static final long serialVersionUID = 1L

	/** ISO 4217, e.g. EUR */
	String code

	String name
	String description

	/** Minor units (e.g. 2 for EUR/USD; useful for money formatting later). */
	Integer decimals = 2

	Boolean enabled = Boolean.TRUE

	static constraints = {
		code nullable: false, blank: false, maxSize: 3, unique: true, matches: /[A-Z]{3}/
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		decimals nullable: true, min: 0, max: 8
		enabled nullable: true
	}

	static mapping = {
		description type: 'text'
	}
}
