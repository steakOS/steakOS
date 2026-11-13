// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import groovy.transform.CompileStatic
import org.springframework.core.env.Environment

import java.security.MessageDigest

/**
 * Production secret checks. Compares SHA-256 hashes of known dev values.
 * The values themselves are not stored here.
 */
@CompileStatic
final class ProductionSecrets {

    static final String KNOWN_ENCRYPTION_PASSWORD =
            '62b2d6d03a6df1609aceee67dc1a9ce456c17c83d2d4c5961271886647fe36d4'
    static final String KNOWN_ENCRYPTION_SALT =
            'b82efc46c073d9d8f6dfd0ad738a7074df7789847dd8f041c62e96000323260c'
    static final String KNOWN_DB_PASSWORD =
            'f8078ea87fb022c6724aecefd65423c221e629ee6f1a69de6e27c2208049aeb9'
    static final String KNOWN_REMEMBER_ME =
            '177e00b4f084d9f0f680928f7b0f3cc882dccaaf41f95f54c1f7f678272d45b8'

    static final int MIN_SECRET_LENGTH = 32
    static final int MIN_SALT_HEX_CHARS = 32

    private ProductionSecrets() {}

    static boolean productionFlag() {
        String g = System.getProperty('grails.env') ?: System.getenv('GRAILS_ENV') ?: ''
        return g.equalsIgnoreCase('production') || g.equalsIgnoreCase('prod')
    }

    static boolean isProduction(Environment environment) {
        if (productionFlag()) {
            return true
        }
        String[] profiles = environment?.activeProfiles
        if (profiles == null) {
            return false
        }
        for (String profile : profiles) {
            if (profile != null && (profile.equalsIgnoreCase('production') || profile.equalsIgnoreCase('prod'))) {
                return true
            }
        }
        return false
    }

    static List<String> problems(String encryptionPassword, String salt, String dbPassword, String rememberMeKey) {
        List<String> found = []
        if (!acceptableSecret(encryptionPassword, KNOWN_ENCRYPTION_PASSWORD)) {
            found.add('Set STEAKOS_FIELD_ENCRYPTION_PASSWORD to a new value of at least 32 characters.')
        }
        if (!acceptableSalt(salt)) {
            found.add('Set STEAKOS_FIELD_ENCRYPTION_SALT to at least 32 hex characters.')
        }
        if (!acceptableSecret(dbPassword, KNOWN_DB_PASSWORD)) {
            found.add('Set STEAKOS_DB_PASSWORD to a new value of at least 32 characters.')
        }
        if (!acceptableSecret(rememberMeKey, KNOWN_REMEMBER_ME)) {
            found.add('Set STEAKOS_REMEMBER_ME_KEY to a new value of at least 32 characters.')
        }
        return found
    }

    static boolean acceptableSecret(String value, String knownHash) {
        if (value == null || value.length() < MIN_SECRET_LENGTH) {
            return false
        }
        return sha256(value) != knownHash
    }

    static boolean acceptableSalt(String value) {
        if (value == null || !(value ==~ /[0-9a-fA-F]{32,}/)) {
            return false
        }
        return sha256(value) != KNOWN_ENCRYPTION_SALT
    }

    static String sha256(String value) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        byte[] hash = digest.digest((value ?: '').getBytes('UTF-8'))
        StringBuilder out = new StringBuilder(hash.length * 2)
        for (byte b : hash) {
            out.append(String.format('%02x', b & 0xff))
        }
        return out.toString()
    }
}
