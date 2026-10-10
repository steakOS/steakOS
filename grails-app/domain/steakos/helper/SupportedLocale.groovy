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
 * Supported UI locale catalog (system-wide helper).
 * Codes use Java/Grails underscore form (e.g. {@code de_DE}) for {@link java.util.Locale}
 * and {@code messages_de_DE.properties}. Convert to BCP-47 with {@link #toLanguageTag()}.
 *
 * Decisions: L1-A, L2-A, L3-A, L4-A — see CORE-DOMAIN-CONCEPT.md
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'code')
@ToString(includes = 'code,name', includeNames = true, includePackage = false)
class SupportedLocale implements Serializable {

	private static final long serialVersionUID = 1L

	/** Locale id, e.g. de_DE, en_US (underscore, Grails/Java style). */
	String code

	String name
	String description

	/** ISO 639 language, e.g. de, en */
	String languageCode

	/** ISO 3166-1 alpha-2 country, e.g. DE — aligns with Countries later */
	String countryCode

	/** ISO 4217 currency, e.g. EUR — aligns with Currencies later */
	String currencyCode

	Boolean enabled = Boolean.TRUE

	/**
	 * BCP 47 language tag (hyphen), e.g. de-DE — useful for HTML lang / some APIs.
	 */
	String toLanguageTag() {
		if (!code) {
			return null
		}
		return code.replace('_', '-')
	}

	/**
	 * java.util.Locale for Spring/Grails LocaleResolver.
	 */
	java.util.Locale toJavaLocale() {
		if (!code) {
			return null
		}
		return java.util.Locale.forLanguageTag(toLanguageTag())
	}

	static constraints = {
		code nullable: false, blank: false, maxSize: 16, unique: true,
				matches: /[a-z]{2}_[A-Z]{2}/
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		languageCode nullable: false, blank: false, maxSize: 8
		countryCode nullable: false, blank: false, maxSize: 2
		currencyCode nullable: false, blank: false, maxSize: 3
		enabled nullable: true
	}

	static mapping = {
		description type: 'text'
	}
}
