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
import steakos.traits.NpubOwned
import steakos.traits.DomainTraitConstraints

/**
 * Binary payload for ObjectContent FILE channel (CT5 hybrid).
 * INLINE = BYTEA; FS = path under data/content-blobs/{npub}/
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,storage,byteSize', includeNames = true, includePackage = false)
class ContentBlob implements NpubOwned, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String mimeType
	Long byteSize
	String checksumSha256
	/** INLINE | FS */
	String storage = 'INLINE'
	byte[] bytes
	String fsPath

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		name nullable: true, maxSize: 255
		description nullable: true, maxSize: 4000
		mimeType nullable: true, maxSize: 128
		byteSize nullable: false
		checksumSha256 nullable: false, blank: false, maxSize: 64
		storage nullable: false, blank: false, maxSize: 16,
				inList: ['INLINE', 'FS']
		bytes nullable: true
		fsPath nullable: true, maxSize: 1024
	}

	static mapping = {
		table 'content_blob'
		// Postgres: bytea via binary type; H2 unit tests use default binary
		bytes type: 'binary', sqlType: 'bytea'
		description type: 'text'
	}
}
