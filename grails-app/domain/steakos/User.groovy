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

package steakos

import grails.plugin.springsecurity.SpringSecurityService
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import grails.compiler.GrailsCompileStatic
import jakarta.persistence.Convert
import steakos.security.EncryptedStringConverter

@GrailsCompileStatic
@EqualsAndHashCode(includes = 'username')
@ToString(includes = 'username', includeNames = true, includePackage = false)
class User implements Serializable {

	private static final long serialVersionUID = 1

	SpringSecurityService springSecurityService

	String username
	String password
	boolean enabled = true
	boolean accountExpired
	boolean accountLocked
	boolean passwordExpired

	/** Nostr public key (bech32 npub… or hex) — stored encrypted at rest */
	@Convert(converter = EncryptedStringConverter)
	String npub

	/** NIP-46 bunker URI (e.g. bunker://…) — stored encrypted at rest */
	@Convert(converter = EncryptedStringConverter)
	String bunker

	/** AUTH01 — TOTP secret (Base32), encrypted at rest */
	@Convert(converter = EncryptedStringConverter)
	String totpSecret

	boolean totpEnabled = false

	/** Last accepted TOTP timestep. A code at or before this step is rejected. */
	Long lastTotpStep

	/** JSON array of one-time recovery codes (hashed or plaintext encrypted blob) */
	@Convert(converter = EncryptedStringConverter)
	String totpRecoveryCodes

	/** Display name for UI (optional) */
	String displayName

	/** PDR-025 role profile id (admin|ceo|office|ops|finance|user|read_only) */
	String roleProfileId

	/** Optional per-user permission override JSON (Allow/Deny/Inherit cells) */
	String permissionOverrideJson

	Set<Role> getAuthorities() {
		(UserRole.findAllByUser(this) as List<UserRole>)*.role as Set<Role>
	}

	def beforeInsert() {
		encodePassword()
	}

	def beforeUpdate() {
		if (isDirty('password')) {
			encodePassword()
		}
	}

	protected void encodePassword() {
		password = springSecurityService?.passwordEncoder ? springSecurityService.encodePassword(password) : password
	}

	static transients = ['springSecurityService']

	static constraints = {
		password nullable: false, blank: false, password: true
		username nullable: false, blank: false, unique: true
		npub nullable: true, blank: true, maxSize: 4000
		bunker nullable: true, blank: true, maxSize: 4000, validator: { String val ->
			if (val == null || val.isEmpty()) {
				return true
			}
			try {
				URI uri = new URI(val)
				if (uri.scheme == null) {
					return ['user.bunker.invalidUri']
				}
				return true
			} catch (URISyntaxException ignored) {
				return ['user.bunker.invalidUri']
			}
		}
		totpSecret nullable: true, blank: true, maxSize: 4000, password: true
		totpEnabled nullable: false
		lastTotpStep nullable: true
		totpRecoveryCodes nullable: true, blank: true, maxSize: 8000
		displayName nullable: true, blank: true, maxSize: 255
		roleProfileId nullable: true, blank: true, maxSize: 64
		permissionOverrideJson nullable: true, blank: true, maxSize: 16000
	}

	static mapping = {
		table name: '`user`'
		password column: '`password`'
		npub type: 'text'
		bunker type: 'text'
		totpSecret type: 'text'
		totpRecoveryCodes type: 'text'
		permissionOverrideJson type: 'text'
	}
}
