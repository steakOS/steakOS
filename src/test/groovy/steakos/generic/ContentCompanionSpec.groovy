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

import grails.testing.gorm.DataTest
import spock.lang.Specification

/**
 * Object Companion kernel (CT0–CT8).
 */
class ContentCompanionSpec extends Specification implements DataTest {

    ContentProfileService profileService
    ContentBlobService blobService
    ObjectContentService contentService

    Class[] getDomainClassesToMock() {
        [ObjectContent, ContentBlob, ObjectContentProfile, ContentType] as Class[]
    }

    def setup() {
        profileService = new ContentProfileService()
        blobService = new ContentBlobService(blobRoot: System.getProperty('java.io.tmpdir') + '/steakos-test-blobs')
        contentService = new ObjectContentService(
                contentProfileService: profileService,
                contentBlobService: blobService
        )
        ContentTypeCodes.ALL.each { String code ->
            if (!ContentType.findByCode(code)) {
                new ContentType(code: code, name: code).save(failOnError: true)
            }
        }
        profileService.ensureSystemProfilesSeeded()
    }

    def "quote profile includes note and risk"() {
        when:
        def types = profileService.allowedTypes('sales', 'CustomerQuote')

        then:
        types[0] == 'Note'
        types.contains('Risk')
        types.contains('Assessment')
        !types.contains('Attachment')
    }

    def "fallback host is note only"() {
        expect:
        profileService.allowedTypes('unknown', 'Thing') == ['Note']
    }

    def "add link body and reject disallowed type"() {
        when:
        def link = contentService.addLink('npub-c', 'sales', 'CustomerQuote', 1L, [
                url: 'https://example.com/brief', title: 'Brief'
        ])
        def note = contentService.addBody('npub-c', 'sales', 'CustomerQuote', 1L, [
                contentTypeCode: 'Note', title: 'Call note', bodyText: '## Hello\n- a\n- b'
        ])

        then:
        link.channel == 'LINK'
        note.bodyFormat == 'MARKDOWN'
        contentService.listByHost('npub-c', 'sales', 'CustomerQuote', 1L).size() == 2

        when:
        contentService.addBody('npub-c', 'sales', 'CustomerQuote', 1L, [
                contentTypeCode: 'Strategy', title: 'No', bodyText: 'x'
        ])

        then:
        thrown(IllegalArgumentException)
    }

    def "file hybrid inline store and download"() {
        given:
        byte[] data = 'hello content'.bytes

        when:
        def file = contentService.addFile('npub-f', 'sales', 'CustomerQuote', 9L, data, 'hello.txt', 'text/plain')
        byte[] read = blobService.readBytes(file.blobId, 'npub-f')

        then:
        file.channel == 'FILE'
        file.byteSize == data.length
        read == data
        ContentBlob.get(file.blobId).storage == 'INLINE'
    }

    def "attachment not in content type codes"() {
        expect:
        !ContentTypeCodes.isValid('Attachment')
        ContentTypeCodes.isValid('Note')
        ContentTypeCodes.REMOVED.contains('Attachment')
    }
}
