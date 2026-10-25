// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import groovy.transform.CompileStatic
import steakos.User
import steakos.core.Task

/**
 * Matrix exception: desk {@code delete} is false for all presets (PDR-025),
 * but a human may always hard-delete a Task assigned to themselves.
 */
@CompileStatic
final class SelfAssignedTaskDelete {

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

    static boolean assigneeMatchesUser(String assigneeHumanNpub, User user) {
        if (!user || !assigneeHumanNpub?.trim()) {
            return false
        }
        String a = assigneeHumanNpub.trim()
        if (user.username) {
            if (a == "user:${user.username}") {
                return true
            }
        }
        String npub = user.npub?.trim()
        return npub && a == npub
    }

    /**
     * True when DELETE targets a Task whose {@code assigneeHumanNpub} is the acting user.
     * Uses {@code withNewSession} because MatrixAccessFilter runs outside OSIV.
     */
    static boolean allows(String method, String requestUri, String contextPath, User user) {
        Long id = taskIdFromDeletePath(method, requestUri, contextPath)
        if (id == null || !user) {
            return false
        }
        try {
            boolean match = false
            Task.withNewSession {
                Task task = Task.get(id)
                if (task) {
                    match = assigneeMatchesUser(task.assigneeHumanNpub, user)
                }
            }
            return match
        } catch (Exception ignored) {
            return false
        }
    }
}
