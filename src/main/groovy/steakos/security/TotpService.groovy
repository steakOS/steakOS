// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import groovy.transform.CompileStatic

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * RFC 6238 TOTP (AUTH01) — Google Authenticator compatible (SHA1, 30s, 6 digits).
 */
@CompileStatic
class TotpService {

    private static final String BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
    private final SecureRandom random = new SecureRandom()

    String generateSecret(int bytes = 20) {
        byte[] buf = new byte[bytes]
        random.nextBytes(buf)
        return base32Encode(buf)
    }

    List<String> generateRecoveryCodes(int count = 8) {
        (1..count).collect {
            String.format('%04d-%04d', random.nextInt(10000), random.nextInt(10000))
        }
    }

    String otpAuthUri(String secret, String accountName, String issuer = 'steakOS') {
        String label = URLEncoder.encode("${issuer}:${accountName}", 'UTF-8')
        String iss = URLEncoder.encode(issuer, 'UTF-8')
        "otpauth://totp/${label}?secret=${secret}&issuer=${iss}&algorithm=SHA1&digits=6&period=30"
    }

    /** PNG data URL for QR enrolment. */
    String qrDataUrl(String otpAuthUri, int size = 200) {
        BitMatrix matrix = new QRCodeWriter().encode(otpAuthUri, BarcodeFormat.QR_CODE, size, size)
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(matrix, 'PNG', baos)
        'data:image/png;base64,' + Base64.encoder.encodeToString(baos.toByteArray())
    }

    boolean verifyCode(String base32Secret, String code, int window = 1) {
        if (!base32Secret || !code) return false
        String digits = code.replaceAll('\\D', '')
        if (digits.length() != 6) return false
        long timestep = (long) (System.currentTimeMillis() / 30000L)
        byte[] key = base32Decode(base32Secret)
        for (int w = -window; w <= window; w++) {
            if (generateCode(key, timestep + w) == digits) {
                return true
            }
        }
        return false
    }

    private static String generateCode(byte[] key, long timestep) {
        byte[] data = ByteBuffer.allocate(8).putLong(timestep).array()
        Mac mac = Mac.getInstance('HmacSHA1')
        mac.init(new SecretKeySpec(key, 'HmacSHA1'))
        byte[] hash = mac.doFinal(data)
        int offset = hash[hash.length - 1] & 0x0f
        int binary = ((hash[offset] & 0x7f) << 24) |
                ((hash[offset + 1] & 0xff) << 16) |
                ((hash[offset + 2] & 0xff) << 8) |
                (hash[offset + 3] & 0xff)
        int otp = binary % 1_000_000
        String.format('%06d', otp)
    }

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder()
        int buffer = 0
        int bitsLeft = 0
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                sb.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 31))
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 31))
        }
        sb.toString()
    }

    static byte[] base32Decode(String encoded) {
        String s = encoded.toUpperCase().replaceAll('[^A-Z2-7]', '')
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        int buffer = 0
        int bitsLeft = 0
        for (int i = 0; i < s.length(); i++) {
            int val = BASE32.indexOf((int) s.charAt(i))
            if (val < 0) continue
            buffer = (buffer << 5) | val
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff)
                bitsLeft -= 8
            }
        }
        out.toByteArray()
    }
}
