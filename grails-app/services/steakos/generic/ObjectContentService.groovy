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

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/** Companion entries CRUD (CT0/CT2). */
@Slf4j
class ObjectContentService {

    ContentProfileService contentProfileService
    ContentBlobService contentBlobService

    @Transactional
    ObjectContent addLink(String npubOwner, String hostPackage, String hostResource, Long hostId, Map fields) {
        String npub = requireNpub(npubOwner)
        String url = fields.url?.toString()?.trim()
        if (!url) throw new IllegalArgumentException('url is required')
        String title = (fields.title ?: fields.name ?: url)?.toString()?.trim()
        ObjectContent c = base(npub, hostPackage, hostResource, hostId, 'LINK', title)
        c.url = url
        c.contentTypeCode = null
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    ObjectContent addBody(String npubOwner, String hostPackage, String hostResource, Long hostId, Map fields) {
        String npub = requireNpub(npubOwner)
        String typeCode = (fields.contentTypeCode ?: ContentTypeCodes.NOTE).toString().trim()
        if (!contentProfileService.isTypeAllowed(hostPackage, hostResource, typeCode)) {
            throw new IllegalArgumentException("content type not allowed for host: ${typeCode}")
        }
        if (!ContentTypeCodes.isValid(typeCode)) {
            throw new IllegalArgumentException("unknown content type: ${typeCode}")
        }
        String title = (fields.title ?: fields.name ?: typeCode)?.toString()?.trim()
        ObjectContent c = base(npub, hostPackage, hostResource, hostId, 'BODY', title)
        c.contentTypeCode = typeCode
        c.bodyFormat = 'MARKDOWN'
        c.bodyText = fields.bodyText as String
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    ObjectContent addFile(String npubOwner, String hostPackage, String hostResource, Long hostId,
                          byte[] data, String fileName, String mimeType) {
        String npub = requireNpub(npubOwner)
        ContentBlob blob = contentBlobService.store(npub, data, mimeType, fileName)
        String title = (fileName ?: 'file').toString()
        ObjectContent c = base(npub, hostPackage, hostResource, hostId, 'FILE', title)
        c.fileName = fileName
        c.mimeType = mimeType ?: blob.mimeType
        c.byteSize = blob.byteSize
        c.checksumSha256 = blob.checksumSha256
        c.blobId = blob.id
        c.contentTypeCode = null
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    ObjectContent update(Long id, String npubOwner, Map fields) {
        ObjectContent c = load(id, npubOwner)
        if (fields.containsKey('title')) {
            String title = (fields.title ?: '').toString().trim()
            c.title = title ?: null
            // name is required — keep a stable fallback for empty titles
            c.name = title ?: (c.contentTypeCode ?: c.channel ?: 'content')
        }
        if (fields.containsKey('bodyText') && c.channel == 'BODY') {
            c.bodyText = fields.bodyText as String
        }
        if (fields.containsKey('url') && c.channel == 'LINK') {
            c.url = fields.url as String
        }
        if (fields.containsKey('pinned')) {
            c.pinned = fields.pinned as Boolean
        }
        if (fields.containsKey('description')) {
            c.description = fields.description as String
        }
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    ObjectContent pin(Long id, String npubOwner, boolean pinned = true) {
        ObjectContent c = load(id, npubOwner)
        if (pinned) {
            // unpin others on same host
            ObjectContent.findAllByNpubOwnerAndHostPackageAndHostResourceAndHostIdAndPinned(
                    c.npubOwner, c.hostPackage, c.hostResource, c.hostId, Boolean.TRUE
            ).each { ObjectContent o ->
                if (o.id != c.id) {
                    o.pinned = Boolean.FALSE
                    o.save(failOnError: true)
                }
            }
        }
        c.pinned = pinned
        c.save(failOnError: true, flush: true)
        return c
    }

    @Transactional
    void delete(Long id, String npubOwner) {
        ObjectContent c = load(id, npubOwner)
        Long blobId = c.blobId
        c.delete(flush: true)
        if (blobId) {
            try {
                contentBlobService.deleteBlob(blobId, npubOwner)
            } catch (Exception e) {
                log.warn('blob delete after content: {}', e.message)
            }
        }
    }

    @Transactional(readOnly = true)
    ObjectContent get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<ObjectContent> listByHost(String npubOwner, String hostPackage, String hostResource, Long hostId,
                                   String channel = null) {
        String npub = requireNpub(npubOwner)
        if (channel) {
            return ObjectContent.findAllByNpubOwnerAndHostPackageAndHostResourceAndHostIdAndChannel(
                    npub, hostPackage, hostResource, hostId, channel,
                    [sort: 'id', order: 'desc', max: 200])
        }
        return ObjectContent.findAllByNpubOwnerAndHostPackageAndHostResourceAndHostId(
                npub, hostPackage, hostResource, hostId,
                [sort: 'id', order: 'desc', max: 200])
    }

    /**
     * Batch channel counts for list UIs (icons on desk tables).
     * @param hostIds up to 500 ids
     */
    @Transactional(readOnly = true)
    List<Map> summarizeHosts(String npubOwner, String hostPackage, String hostResource, List hostIds) {
        String npub = requireNpub(npubOwner)
        if (!hostPackage?.trim() || !hostResource?.trim()) {
            throw new IllegalArgumentException('hostPackage and hostResource required')
        }
        List<Long> ids = (hostIds ?: []).findAll { it != null && it != '' }.collect { it as Long }.unique()
        if (!ids) return []
        if (ids.size() > 500) ids = ids.take(500)

        Map<Long, Map> byId = [:]
        ids.each { Long hid ->
            byId[hid] = [
                    hostId   : hid,
                    linkCount: 0,
                    fileCount: 0,
                    bodyCount: 0,
                    hasLink  : false,
                    hasFile  : false,
                    hasBody  : false,
                    total    : 0
            ]
        }

        List<ObjectContent> rows = ObjectContent.findAllByNpubOwnerAndHostPackageAndHostResourceAndHostIdInList(
                npub, hostPackage.trim(), hostResource.trim(), ids)

        rows.each { ObjectContent c ->
            Map m = byId[c.hostId]
            if (!m) return
            m.total = (m.total as int) + 1
            if (c.channel == 'LINK') {
                m.linkCount = (m.linkCount as int) + 1
                m.hasLink = true
            } else if (c.channel == 'FILE') {
                m.fileCount = (m.fileCount as int) + 1
                m.hasFile = true
            } else if (c.channel == 'BODY') {
                m.bodyCount = (m.bodyCount as int) + 1
                m.hasBody = true
            }
        }
        return ids.collect { byId[it] }
    }

    Map toMap(ObjectContent c) {
        if (!c) return null
        [
                id              : c.id,
                name            : c.name,
                title           : c.title ?: c.name,
                description     : c.description,
                hostPackage     : c.hostPackage,
                hostResource    : c.hostResource,
                hostId          : c.hostId,
                channel         : c.channel,
                contentTypeCode : c.contentTypeCode,
                bodyFormat      : c.bodyFormat,
                bodyText        : c.bodyText,
                url             : c.url,
                pinned          : c.pinned,
                fileName        : c.fileName,
                mimeType        : c.mimeType,
                byteSize        : c.byteSize,
                checksumSha256  : c.checksumSha256,
                blobId          : c.blobId,
                npubOwner       : c.npubOwner
        ]
    }

    private ObjectContent base(String npub, String hostPackage, String hostResource, Long hostId,
                               String channel, String title) {
        if (!hostPackage?.trim() || !hostResource?.trim() || !hostId) {
            throw new IllegalArgumentException('hostPackage, hostResource, hostId required')
        }
        new ObjectContent(
                npubOwner   : npub,
                name        : title ?: channel,
                title       : title,
                hostPackage : hostPackage.trim(),
                hostResource: hostResource.trim(),
                hostId      : hostId,
                channel     : channel,
                status      : 'ACTIVE'
        )
    }

    private ObjectContent load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        ObjectContent c = ObjectContent.get(id)
        if (!c || c.npubOwner != npub) throw new IllegalArgumentException("content not found: ${id}")
        c
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
