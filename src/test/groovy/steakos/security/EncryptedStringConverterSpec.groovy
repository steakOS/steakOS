// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import org.springframework.security.crypto.encrypt.Encryptors
import spock.lang.Specification

class EncryptedStringConverterSpec extends Specification {

    def "converter does not return plaintext when encryption is unavailable"() {
        when:
        new EncryptedStringConverter().convertToDatabaseColumn('top-secret-value')

        then:
        thrown(IllegalStateException)
    }

    def "converter does not return a stored value when encryption is unavailable"() {
        when:
        new EncryptedStringConverter().convertToEntityAttribute('00112233445566778899aabbccddeeff')

        then:
        thrown(IllegalStateException)
    }

    def "tampered ciphertext does not come back as the stored value"() {
        given:
        def encryptor = Encryptors.delux('0123456789abcdef0123456789abcdef', '0123456789abcdef0123456789abcdef')
        String plain = 'correct horse battery'
        String cipher = encryptor.encrypt(plain)
        String tampered = cipher.substring(0, cipher.length() - 1) + (cipher.endsWith('a') ? 'b' : 'a')

        when:
        encryptor.decrypt(tampered)

        then:
        def ex = thrown(Exception)
        !ex.message?.contains(plain)
        !ex.message?.contains(cipher)
    }

    def "legacy plaintext is not treated as ciphertext"() {
        expect:
        !CiphertextHints.looksLikeCiphertext('plain-token')
        !CiphertextHints.looksLikeCiphertext('short')
        CiphertextHints.looksLikeCiphertext('00112233445566778899aabbccddeeff')
    }

    def "round trip keeps the plaintext"() {
        given:
        def encryptor = Encryptors.delux('0123456789abcdef0123456789abcdef', '0123456789abcdef0123456789abcdef')

        expect:
        encryptor.decrypt(encryptor.encrypt('nsec-sample')) == 'nsec-sample'
    }
}
