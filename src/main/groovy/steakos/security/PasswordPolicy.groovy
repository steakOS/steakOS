// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import groovy.transform.CompileStatic

/**
 * Human-account password rules (AUTH).
 * Min 12 chars; at least one lower, one upper, one digit, one special (non-alphanumeric).
 */
@CompileStatic
final class PasswordPolicy {

    static final int MIN_LENGTH = 12
    /** Dev/test seed for admin + ceo (meets policy). */
    static final String DEV_DEFAULT_PASSWORD = 'Schn1tz3l3ck3$'

    static final String RULES_HINT =
            "at least ${MIN_LENGTH} characters, with 1 lowercase, 1 uppercase, 1 number, and 1 special character"

    private PasswordPolicy() {}

    /**
     * @return null if ok, otherwise a short error message
     */
    static String validate(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "password is required (${RULES_HINT})"
        }
        if (raw.length() < MIN_LENGTH) {
            return "password must be at least ${MIN_LENGTH} characters (${RULES_HINT})"
        }
        if (!(raw =~ /[a-z]/)) {
            return "password must include a lowercase letter (${RULES_HINT})"
        }
        if (!(raw =~ /[A-Z]/)) {
            return "password must include an uppercase letter (${RULES_HINT})"
        }
        if (!(raw =~ /[0-9]/)) {
            return "password must include a number (${RULES_HINT})"
        }
        if (!(raw =~ /[^A-Za-z0-9]/)) {
            return "password must include a special character (${RULES_HINT})"
        }
        null
    }

    static void requireValid(String raw) {
        String err = validate(raw)
        if (err) {
            throw new IllegalArgumentException(err)
        }
    }

    static boolean isValid(String raw) {
        validate(raw) == null
    }
}
