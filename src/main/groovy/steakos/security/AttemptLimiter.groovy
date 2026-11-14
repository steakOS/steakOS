// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap

/**
 * Fixed-window counter. The attempt that reaches the limit is rejected,
 * including when that attempt would otherwise have been correct.
 */
@CompileStatic
class AttemptLimiter {

    static final int LOGIN_PER_USER_IP = 5
    static final long LOGIN_USER_WINDOW_MS = 60_000L
    static final int LOGIN_PER_IP = 30
    static final long LOGIN_IP_WINDOW_MS = 60_000L
    static final int TOTP_LIMIT = 5
    static final long TOTP_WINDOW_MS = 5 * 60_000L
    static final int NOSTR_CHALLENGE_PER_IP = 10
    static final long NOSTR_CHALLENGE_WINDOW_MS = 60_000L

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>()

    boolean allow(String key, int limit, long windowMs) {
        return allow(key, limit, windowMs, System.currentTimeMillis())
    }

    boolean allow(String key, int limit, long windowMs, long now) {
        if (!key) return false
        prune(key, windowMs, now)
        Deque<Long> deque = hits.computeIfAbsent(key) { new ArrayDeque<Long>() }
        synchronized (deque) {
            pruneLocked(deque, windowMs, now)
            return deque.size() < limit
        }
    }

    void record(String key, long windowMs) {
        record(key, windowMs, System.currentTimeMillis())
    }

    void record(String key, long windowMs, long now) {
        if (!key) return
        Deque<Long> deque = hits.computeIfAbsent(key) { new ArrayDeque<Long>() }
        synchronized (deque) {
            pruneLocked(deque, windowMs, now)
            deque.addLast(now)
        }
    }

    long retryAfterSeconds(String key, long windowMs) {
        return retryAfterSeconds(key, windowMs, System.currentTimeMillis())
    }

    long retryAfterSeconds(String key, long windowMs, long now) {
        Deque<Long> deque = hits.get(key)
        if (deque == null) return 1L
        synchronized (deque) {
            pruneLocked(deque, windowMs, now)
            if (deque.isEmpty()) return 1L
            long waitMs = windowMs - (now - deque.peekFirst())
            return Math.max(1L, (long) Math.ceil(waitMs / 1000.0d))
        }
    }

    private void prune(String key, long windowMs, long now) {
        Deque<Long> deque = hits.get(key)
        if (deque == null) return
        synchronized (deque) {
            pruneLocked(deque, windowMs, now)
        }
    }

    private static void pruneLocked(Deque<Long> deque, long windowMs, long now) {
        while (!deque.isEmpty() && now - deque.peekFirst() >= windowMs) {
            deque.removeFirst()
        }
    }
}
