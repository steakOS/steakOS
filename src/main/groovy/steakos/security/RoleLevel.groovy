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

package steakos.security

import groovy.transform.CompileStatic

/**
 * steakOS role levels (ADR-007).
 * Spring Security authorities use the {@link #authority} form (e.g. ROLE_C).
 */
@CompileStatic
enum RoleLevel {

    /** Full access — Startup / solo mode */
    C('ROLE_C', 50, 'C-Level'),
    /** Director — full access in APQC area, read-all elsewhere */
    D('ROLE_D', 40, 'D-Level'),
    /** Manager — access within APQC sub-area */
    M('ROLE_M', 30, 'M-Level'),
    /** Associate — individual assigned transactions */
    A('ROLE_A', 20, 'A-Level'),
    /** AI agents — read-all; writes only via Proposal + human approval (ADR-012) */
    AI('ROLE_AI', 10, 'AI-Level'),
    /** External partners — scoped to partner-owned data */
    P('ROLE_P', 5, 'P-Level')

    final String authority
    final int rank
    final String label

    private RoleLevel(String authority, int rank, String label) {
        this.authority = authority
        this.rank = rank
        this.label = label
    }

    static RoleLevel fromAuthority(String authority) {
        if (!authority) {
            return null
        }
        String normalized = authority.startsWith('ROLE_') ? authority : "ROLE_${authority}"
        values().find { it.authority == normalized || it.name() == authority }
    }

    /**
     * Highest rank among a set of Spring Security authority strings.
     */
    static RoleLevel highestOf(Collection<String> authorities) {
        RoleLevel best = null
        authorities?.each { String auth ->
            RoleLevel level = fromAuthority(auth)
            if (level && (best == null || level.rank > best.rank)) {
                best = level
            }
        }
        return best
    }

    boolean atLeast(RoleLevel minimum) {
        minimum != null && this.rank >= minimum.rank
    }
}
