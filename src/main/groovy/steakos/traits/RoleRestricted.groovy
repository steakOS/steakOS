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
import steakos.security.RoleLevel

/**
 * Plugin / area security metadata for domain entities (ADR-007, ADR-014).
 * PDF: RoleRestrictedTrait — APQC area + min write role; AI cannot direct-write.
 * <p>
 * Distinct from {@link AccessControl} (instance protection mode + access list).
 * Complements Spring Security {@code @Secured} and Postgres RLS.
 * </p>
 */
@CompileStatic
trait RoleRestricted {

    /**
     * APQC process category / area code this entity belongs to
     * (e.g. Finance process family). Used for D/M area checks.
     */
    String apqcAreaCode

    /**
     * Minimum role level required to <em>write</em> this entity (default Associate).
     * Stored as enum name: C, D, M, A, AI, P.
     */
    String minWriteRole = RoleLevel.A.name()

    RoleLevel getMinWriteRoleLevel() {
        RoleLevel level = RoleLevel.fromAuthority(minWriteRole)
        return level ?: RoleLevel.A
    }

    /**
     * Whether the given Spring Security authorities may write this instance
     * (rank-based; area-specific D/M rules can be refined by services).
     */
    boolean allowsWriteFor(Collection<String> authorities) {
        RoleLevel highest = RoleLevel.highestOf(authorities)
        if (highest == null) {
            return false
        }
        // AI never writes directly — Proposal path only (ADR-012)
        if (highest == RoleLevel.AI) {
            return false
        }
        return highest.atLeast(minWriteRoleLevel)
    }

    /**
     * Read is allowed for any authenticated steakOS role level, including AI.
     * Partner (P) scoping is enforced in services via ownership filters.
     */
    boolean allowsReadFor(Collection<String> authorities) {
        RoleLevel.highestOf(authorities) != null
    }
}
