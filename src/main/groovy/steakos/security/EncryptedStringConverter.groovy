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

package steakos.security

import grails.util.Holders
import groovy.transform.CompileStatic
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import steakos.FieldEncryptionService

/**
 * JPA/Hibernate converter: plaintext in the domain, ciphertext in the database.
 */
@CompileStatic
@Converter
class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute
        }
        FieldEncryptionService svc = encryptionService
        if (svc == null) {
            throw new IllegalStateException('Field encryption is not available')
        }
        return svc.encrypt(attribute)
    }

    @Override
    String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData
        }
        FieldEncryptionService svc = encryptionService
        if (svc == null) {
            throw new IllegalStateException('Field encryption is not available')
        }
        try {
            return svc.decrypt(dbData)
        } catch (FieldDecryptionException e) {
            throw e
        } catch (Exception ignored) {
            if (allowLegacyPlaintext() && !CiphertextHints.looksLikeCiphertext(dbData)) {
                return dbData
            }
            throw new FieldDecryptionException('Encrypted field could not be read')
        }
    }

    private static boolean allowLegacyPlaintext() {
        String raw = System.getenv('STEAKOS_ENCRYPTION_ALLOW_LEGACY_PLAINTEXT')
        return raw == 'true' || raw == '1'
    }

    private static FieldEncryptionService getEncryptionService() {
        try {
            def ctx = Holders.applicationContext
            if (ctx == null) {
                return null
            }
            return (FieldEncryptionService) ctx.getBean('fieldEncryptionService')
        } catch (Exception ignored) {
            return null
        }
    }
}
