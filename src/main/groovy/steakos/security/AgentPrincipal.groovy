// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

/**
 * Detects agent-only principals (ROLE_AI without admin/C) — money/setup hard-denies.
 */
final class AgentPrincipal {

    private AgentPrincipal() {}

    static boolean isAgentOnly(Object authentication) {
        if (!authentication?.authenticated) return false
        Collection authorities = authentication.authorities*.authority
        boolean hasAi = authorities?.contains('ROLE_AI')
        boolean hasAdmin = authorities?.contains('ROLE_ADMIN') || authorities?.contains('ROLE_C')
        return hasAi && !hasAdmin
    }
}
