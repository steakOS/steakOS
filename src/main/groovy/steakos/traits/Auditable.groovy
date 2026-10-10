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
 * Mutation audit fields for domain entities (ADR-001, ADR-005, ADR-011).
 * PDF: AuditableTrait — who/when only.
 * <p>
 * GORM auto-timestamps populate {@code dateCreated} / {@code lastUpdated} when present.
 * Actor fields store the acting npub (not passwords or nsec).
 * Business {@code revision} lives on {@link Versioned} only (Decision A1).
 * </p>
 */
@CompileStatic
trait Auditable {

    Date dateCreated
    Date lastUpdated

    /** npub of the principal that created the row */
    String createdByNpub

    /** npub of the principal that last updated the row */
    String lastUpdatedByNpub

    /**
     * Stamp create actor; safe to call from services before insert.
     */
    void markCreatedBy(String npub) {
        createdByNpub = npub
        if (lastUpdatedByNpub == null) {
            lastUpdatedByNpub = npub
        }
    }

    /**
     * Stamp update actor; safe to call from services before update.
     */
    void markUpdatedBy(String npub) {
        lastUpdatedByNpub = npub
    }
}
