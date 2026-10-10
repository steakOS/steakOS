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
 * Instance-level protection and access payload (ADR-007, ADR-014).
 * PDF: AccessControlTrait — protection, access.
 * <p>
 * Distinct from {@link RoleRestricted}: that trait encodes <em>role rank + APQC area</em>;
 * this trait encodes <em>how this instance is protected</em> and optional access lists.
 * </p>
 */
@CompileStatic
trait AccessControl {

    /**
     * Protection mode, e.g. {@code OPEN}, {@code OWNER_ONLY}, {@code ROLE}, {@code LIST}, {@code PARTNER}.
     */
    String protection = 'OWNER_ONLY'

    /**
     * Optional access payload: comma-separated npubs, role names, or JSON list depending on protection mode.
     * Kept as String in the skeleton for simplicity; services interpret by {@link #protection}.
     */
    String access

    boolean isOwnerOnly() {
        protection == null || protection == 'OWNER_ONLY'
    }

    boolean isOpenAccess() {
        protection == 'OPEN'
    }

    /**
     * Lightweight check: OPEN always true; OWNER_ONLY defers to caller ownership checks;
     * LIST mode treats {@code access} as comma-separated principals.
     */
    boolean accessListContains(String principal) {
        if (principal == null || access == null || access.isEmpty()) {
            return false
        }
        access.split(',')*.trim().any { it == principal }
    }
}
