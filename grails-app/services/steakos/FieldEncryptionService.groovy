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

package steakos

import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import org.springframework.core.env.Environment
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.security.crypto.encrypt.TextEncryptor

import jakarta.annotation.PostConstruct

/**
 * Application-level field encryption for sensitive User attributes (nsec, npub, bunker).
 * Key material from config / env — never commit production secrets.
 *
 * @see ADR-014 Security Controls, Encryption at Rest
 */
@CompileStatic
@Transactional(readOnly = true)
class FieldEncryptionService {

    Environment environment

    private TextEncryptor encryptor

    @PostConstruct
    void init() {
        String password = environment.getProperty('steakos.encryption.password')
        String saltHex = environment.getProperty('steakos.encryption.salt')
        if (!password?.trim() || !saltHex?.trim()) {
            throw new IllegalStateException(
                    'Field encryption is not configured. Set STEAKOS_FIELD_ENCRYPTION_PASSWORD and STEAKOS_FIELD_ENCRYPTION_SALT.'
            )
        }
        encryptor = Encryptors.delux(password, saltHex)
    }

    String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText
        }
        return encryptor.encrypt(plainText)
    }

    String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText
        }
        return encryptor.decrypt(cipherText)
    }
}
