// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import groovy.transform.CompileStatic

/** Shape check for Spring Encryptors hex ciphertext. Does not decode or log the value. */
@CompileStatic
final class CiphertextHints {

    private CiphertextHints() {}

    static boolean looksLikeCiphertext(String value) {
        if (value == null) {
            return false
        }
        String trimmed = value.trim()
        if (trimmed.length() < 32 || (trimmed.length() % 2) != 0) {
            return false
        }
        return trimmed ==~ /[0-9a-fA-F]+/
    }
}
