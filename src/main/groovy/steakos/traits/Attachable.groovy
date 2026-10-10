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
 * Attachment metadata on a domain (PDF: AttachableTrait).
 * Links and file bytes live on Object Companion ({@code ObjectContent} LINK/FILE), not on this trait.
 * Decision D1 (updated): metadata + count only — no trait-level URL field.
 */
@CompileStatic
trait Attachable {

    /**
     * Optional JSON metadata: filename, mime, size, checksum, storage key, etc.
     * Binary bytes and external links live outside the trait (Object Companion / asset pipeline).
     */
    String attachmentMeta

    /** Number of associated attachments if known. */
    Integer attachmentCount = 0

    void markAttachmentAdded() {
        attachmentCount = (attachmentCount ?: 0) + 1
    }
}
