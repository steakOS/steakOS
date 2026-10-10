// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import spock.lang.Specification

class TotpServiceSpec extends Specification {

    TotpService service = new TotpService()

    def "generateSecret is base32-ish and long enough"() {
        when:
        String secret = service.generateSecret()

        then:
        secret
        secret.length() >= 16
        secret ==~ /[A-Z2-7]+/
    }

    def "verifyCode rejects garbage and accepts live code"() {
        given:
        String secret = service.generateSecret()
        String live = liveTotp(secret)

        expect:
        !service.verifyCode(secret, '000000')
        !service.verifyCode(secret, 'abc')
        !service.verifyCode(null, '123456')
        service.verifyCode(secret, live)
    }

    /** Mirror TotpService HMAC-SHA1 TOTP for the current 30s step. */
    private static String liveTotp(String base32Secret) {
        byte[] key = TotpService.base32Decode(base32Secret)
        long timestep = System.currentTimeMillis() / 30000L
        byte[] data = java.nio.ByteBuffer.allocate(8).putLong(timestep).array()
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance('HmacSHA1')
        mac.init(new javax.crypto.spec.SecretKeySpec(key, 'HmacSHA1'))
        byte[] hash = mac.doFinal(data)
        int offset = hash[hash.length - 1] & 0x0f
        int binary = ((hash[offset] & 0x7f) << 24) |
                ((hash[offset + 1] & 0xff) << 16) |
                ((hash[offset + 2] & 0xff) << 8) |
                (hash[offset + 3] & 0xff)
        String.format('%06d', binary % 1_000_000)
    }

    def "otpAuthUri contains issuer and secret"() {
        when:
        String uri = service.otpAuthUri('ABCD2345', 'admin', 'steakOS')

        then:
        uri.startsWith('otpauth://totp/')
        uri.contains('secret=ABCD2345')
        uri.contains('issuer=steakOS')
    }

    def "qrDataUrl returns png data url"() {
        when:
        String uri = service.otpAuthUri('ABCD2345EFGH6789', 'admin')
        String data = service.qrDataUrl(uri, 120)

        then:
        data.startsWith('data:image/png;base64,')
        data.length() > 100
    }

    def "recovery codes format"() {
        when:
        List codes = service.generateRecoveryCodes(4)

        then:
        codes.size() == 4
        codes.every { it ==~ /\d{4}-\d{4}/ }
    }

    def "base32 round-trip"() {
        given:
        byte[] raw = ([1, 2, 3, 4, 5, 6, 7, 8, 9, 10] as byte[])

        when:
        String enc = TotpService.base32Encode(raw)
        byte[] dec = TotpService.base32Decode(enc)

        then:
        enc
        dec == raw
    }
}
