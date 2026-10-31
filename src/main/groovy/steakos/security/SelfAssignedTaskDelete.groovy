// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import groovy.transform.CompileStatic
import steakos.User
import steakos.core.Task

/**
 * Matrix exception for {@code DELETE /api/domains/core/Task/{id}}.
 * Desk {@code delete} stays false for all presets (PDR-025), but Task hard-delete
 * is allowed when the actor is the human assignee, the npub owner, or Admin/CEO.
 */
@CompileStatic
final class SelfAssignedTaskDelete {

    private static final Set<String> ELEVATED_PROFILES =
            (['admin', 'ceo'] as Set).asImmutable()

    private SelfAssignedTaskDelete() {}

    /** {@code /api/domains/core/Task/{id}} with optional trailing slash; strips context path. */
    static Long taskIdFromDeletePath(String method, String requestUri, String contextPath) {
        if (!'DELETE'.equalsIgnoreCase(method ?: '')) {
            return null
        }
        String path = requestUri ?: ''
        String ctx = contextPath ?: ''
        if (ctx && path.startsWith(ctx)) {
            path = path.substring(ctx.length())
        }
        def m = path =~ /^\/api\/domains\/core\/Task\/(\d+)\/?$/
        if (!m.matches()) {
            return null
        }
        return m.group(1) as Long
    }

    /** Match {@code user:{username}} or raw npub against the acting user. */
    static boolean identityMatchesUser(String identity, User user) {
        if (!user || !identity?.trim()) {
            return false
        }
        String a = identity.trim()
        if (user.username) {
            if (a == "user:${user.username}") {
                return true
            }
        }
        String npub = user.npub?.trim()
        return npub && a == npub
    }

    /** @deprecated use {@link #identityMatchesUser} — kept for existing call sites/tests. */
    static boolean assigneeMatchesUser(String assigneeHumanNpub, User user) {
        identityMatchesUser(assigneeHumanNpub, user)
    }

    /** Admin/CEO via roleProfileId (no DB) or Spring ROLE_ADMIN / ROLE_CEO. */
    static boolean isElevatedActor(User user) {
        if (!user) {
            return false
        }
        String pid = user.roleProfileId?.trim()?.toLowerCase()
        if (pid && ELEVATED_PROFILES.contains(pid)) {
            return true
        }
        try {
            return !!user.authorities?.any { it?.authority == 'ROLE_ADMIN' || it?.authority == 'ROLE_CEO' }
        } catch (Exception ignored) {
            return false
        }
    }

    /**
     * True when DELETE targets a Task the actor may remove:
     * Admin/CEO, human assignee, or npub owner.
     * Uses {@code withNewSession} because MatrixAccessFilter runs outside OSIV.
     */
    static boolean allows(String method, String requestUri, String contextPath, User user) {
        Long id = taskIdFromDeletePath(method, requestUri, contextPath)
        if (id == null || !user) {
            return false
        }
        // roleProfileId is on the User row already loaded by the filter
        String pid = user.roleProfileId?.trim()?.toLowerCase()
        if (pid && ELEVATED_PROFILES.contains(pid)) {
            return true
        }
        try {
            boolean ok = false
            Task.withNewSession {
                if (isElevatedActor(user)) {
                    ok = true
                    return
                }
                Task task = Task.get(id)
                if (task) {
                    ok = identityMatchesUser(task.assigneeHumanNpub, user) ||
                            identityMatchesUser(task.npubOwner, user)
                }
            }
            return ok
        } catch (Exception ignored) {
            return false
        }
    }
}
