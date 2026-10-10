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

/**
 * Facade for Object Companion / Content Rail (CT6).
 */
@Slf4j
class ContentCompanionService {

    ObjectContentService objectContentService
    ContentProfileService contentProfileService
    ContentBlobService contentBlobService

    @Transactional(readOnly = true)
    Map rail(String npub, String hostPackage, String hostResource, Long hostId, String channel = null) {
        [
                hostPackage     : hostPackage,
                hostResource    : hostResource,
                hostId          : hostId,
                allowedTypeCodes: contentProfileService.allowedTypes(hostPackage, hostResource),
                items           : objectContentService.listByHost(npub, hostPackage, hostResource, hostId, channel)
                        .collect { objectContentService.toMap(it) }
        ]
    }

    @Transactional(readOnly = true)
    Map profile(String hostPackage, String hostResource) {
        contentProfileService.toMap(hostPackage, hostResource)
    }

    @Transactional(readOnly = true)
    List<Map> listTypes() {
        ContentType.list([sort: 'code', order: 'asc']).collect { ContentType t ->
            [id: t.id, code: t.code, name: t.name, description: t.description]
        }
    }

    ObjectContent addLink(String npub, String pkg, String res, Long id, Map fields) {
        objectContentService.addLink(npub, pkg, res, id, fields)
    }

    ObjectContent addBody(String npub, String pkg, String res, Long id, Map fields) {
        objectContentService.addBody(npub, pkg, res, id, fields)
    }

    ObjectContent addFile(String npub, String pkg, String res, Long id, byte[] data, String fileName, String mime) {
        objectContentService.addFile(npub, pkg, res, id, data, fileName, mime)
    }

    ObjectContent update(Long contentId, String npub, Map fields) {
        objectContentService.update(contentId, npub, fields)
    }

    ObjectContent pin(Long contentId, String npub, boolean pinned) {
        objectContentService.pin(contentId, npub, pinned)
    }

    void delete(Long contentId, String npub) {
        objectContentService.delete(contentId, npub)
    }

    byte[] download(Long contentId, String npub) {
        ObjectContent c = objectContentService.get(contentId, npub)
        if (c.channel != 'FILE' || !c.blobId) throw new IllegalArgumentException('not a file entry')
        contentBlobService.readBytes(c.blobId, npub)
    }

    ObjectContent get(Long contentId, String npub) {
        objectContentService.get(contentId, npub)
    }

    Map toMap(ObjectContent c) {
        objectContentService.toMap(c)
    }

    @Transactional(readOnly = true)
    List<Map> summarize(String npub, String hostPackage, String hostResource, List hostIds) {
        objectContentService.summarizeHosts(npub, hostPackage, hostResource, hostIds)
    }
}
