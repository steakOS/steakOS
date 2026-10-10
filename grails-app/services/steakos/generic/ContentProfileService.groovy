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

/** Resolve BODY type allowlists (CT4). */
@Slf4j
class ContentProfileService {

    @Transactional
    void ensureSystemProfilesSeeded() {
        ContentProfileDefaults.PROFILES.each { String key, List<String> codes ->
            String[] parts = key.split('\\|', 2)
            String hostPackage = parts[0]
            String hostResource = parts[1]
            ObjectContentProfile existing = ObjectContentProfile.findByHostPackageAndHostResourceAndSystemDefault(
                    hostPackage, hostResource, Boolean.TRUE)
            if (!existing) {
                new ObjectContentProfile(
                        name             : "${hostPackage}.${hostResource}",
                        description      : "System default content types for ${hostPackage}.${hostResource}",
                        hostPackage      : hostPackage,
                        hostResource     : hostResource,
                        allowedTypeCodes : codes.join(','),
                        systemDefault    : Boolean.TRUE,
                        status           : 'ACTIVE'
                ).save(failOnError: true)
            }
        }
    }

    @Transactional(readOnly = true)
    List<String> allowedTypes(String hostPackage, String hostResource) {
        ObjectContentProfile p = ObjectContentProfile.findByHostPackageAndHostResource(
                hostPackage, hostResource)
        List<String> codes
        if (p) {
            codes = p.typeCodeList()
        } else {
            codes = ContentProfileDefaults.forHost(hostPackage, hostResource)
        }
        // Note always first
        if (!codes.contains(ContentTypeCodes.NOTE)) {
            codes = [ContentTypeCodes.NOTE] + codes
        } else if (codes[0] != ContentTypeCodes.NOTE) {
            codes = [ContentTypeCodes.NOTE] + codes.findAll { it != ContentTypeCodes.NOTE }
        }
        return codes
    }

    @Transactional(readOnly = true)
    boolean isTypeAllowed(String hostPackage, String hostResource, String contentTypeCode) {
        if (!contentTypeCode) return false
        if (contentTypeCode == ContentTypeCodes.NOTE) return true
        return allowedTypes(hostPackage, hostResource).contains(contentTypeCode)
    }

    Map toMap(String hostPackage, String hostResource) {
        [
                hostPackage     : hostPackage,
                hostResource    : hostResource,
                allowedTypeCodes: allowedTypes(hostPackage, hostResource)
        ]
    }
}
