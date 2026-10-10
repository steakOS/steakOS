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
import steakos.traits.AccessControl
import steakos.traits.Classifiable
import steakos.traits.DomainTraitConstraints

/**
 * Object Companion entry (CT0–CT2): LINK | FILE | BODY on any host record.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'id')
@ToString(includes = 'id,channel,title', includeNames = true, includePackage = false)
class ObjectContent implements NpubOwned, AccessControl, Classifiable, Serializable {

	private static final long serialVersionUID = 1L

	String name
	String description

	String hostPackage
	String hostResource
	Long hostId

	/** LINK | FILE | BODY */
	String channel
	/** BODY only — ContentType code (Note default) */
	String contentTypeCode

	String title
	/** MARKDOWN (CT7) */
	String bodyFormat = 'MARKDOWN'
	String bodyText
	String url

	Boolean pinned = Boolean.FALSE

	String fileName
	String mimeType
	Long byteSize
	String checksumSha256
	Long blobId

	static constraints = {
		DomainTraitConstraints.applyNpubOwned(delegate)
		DomainTraitConstraints.applyAccessControl(delegate)
		DomainTraitConstraints.applyClassifiable(delegate)
		name nullable: false, blank: false, maxSize: 255
		description nullable: true, maxSize: 4000
		hostPackage nullable: false, blank: false, maxSize: 64
		hostResource nullable: false, blank: false, maxSize: 128
		hostId nullable: false
		channel nullable: false, blank: false, maxSize: 16,
				inList: ['LINK', 'FILE', 'BODY']
		contentTypeCode nullable: true, maxSize: 64
		title nullable: true, maxSize: 255
		bodyFormat nullable: true, maxSize: 16
		bodyText nullable: true, maxSize: 100000
		url nullable: true, maxSize: 2000
		pinned nullable: false
		fileName nullable: true, maxSize: 512
		mimeType nullable: true, maxSize: 128
		byteSize nullable: true
		checksumSha256 nullable: true, maxSize: 64
		blobId nullable: true
	}

	static mapping = {
		table 'object_content'
		description type: 'text'
		bodyText type: 'text'
		hostPackage index: 'obj_content_host_idx'
		hostResource index: 'obj_content_host_idx'
		hostId index: 'obj_content_host_idx'
		channel index: 'obj_content_channel_idx'
	}
}
