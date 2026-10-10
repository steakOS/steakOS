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

package steakos.traits

import groovy.transform.CompileStatic

/**
 * Classification, local status/type, and tags for search, AI, and ootW-UI (PDF: ClassifiableTrait).
 * Fields: status (local), classification (global), type (local), tags.
 */
@CompileStatic
trait Classifiable {

    /** Local lifecycle status (e.g. DRAFT, OPEN, CLOSED). */
    String status

    /** Global classification code or label (tenant- or system-wide taxonomy). */
    String classification

    /** Local type discriminator within a domain (e.g. invoice subtype). */
    String type

    /**
     * Free-form or controlled tags. Skeleton uses comma-separated string;
     * services may split/join. Future: dedicated tag table if needed.
     */
    String tags

    List<String> tagList() {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList()
        }
        tags.split(',')*.trim().findAll { it } as List<String>
    }

    void addTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return
        }
        List<String> list = new ArrayList<>(tagList())
        if (!list.contains(tag.trim())) {
            list.add(tag.trim())
            tags = list.join(',')
        }
    }

    boolean hasStatus(String expected) {
        expected != null && expected == status
    }
}
