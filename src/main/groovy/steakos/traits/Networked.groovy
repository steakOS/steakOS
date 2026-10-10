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
 * Structural graph links for APQC hierarchies, processes, org charts (PDF: NetworkedTrait).
 * Decision C1: scalar FK ids only in this skeleton (parent / predecessor / successor).
 * Rich {@code connections} graphs deferred to a later join-table design.
 */
@CompileStatic
trait Networked {

    /** Parent entity id in a hierarchy (same or related table — enforced by services). */
    Long parentId

    /** Previous node in a linear process / chain. */
    Long predecessorId

    /** Next node in a linear process / chain. */
    Long successorId

    boolean hasParent() {
        parentId != null
    }

    boolean isRoot() {
        parentId == null
    }
}
