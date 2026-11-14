// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import groovy.transform.CompileStatic

@CompileStatic
class RateLimitedException extends IllegalStateException {
    final long retryAfterSeconds

    RateLimitedException(long retryAfterSeconds) {
        super('Too many attempts')
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds)
    }
}
