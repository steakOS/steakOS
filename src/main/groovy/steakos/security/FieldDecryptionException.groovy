// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import groovy.transform.CompileStatic

/** Decrypt failed. The message must not include the stored value or the plaintext. */
@CompileStatic
class FieldDecryptionException extends IllegalStateException {

    FieldDecryptionException(String message) {
        super(message ?: 'Encrypted field could not be read')
    }
}
