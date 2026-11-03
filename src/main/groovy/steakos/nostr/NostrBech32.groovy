// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.nostr

import groovy.transform.CompileStatic
import steakos.security.NostrAuthService

/**
 * Minimal BIP-0173 bech32 decode for NIP-19 bare keys ({@code nsec1…} / {@code npub1…}).
 * Not bech32m — NIP-19 bare keys use classic bech32 checksum.
 */
@CompileStatic
final class NostrBech32 {

    private static final String CHARSET = 'qpzry9x8gf2tvdw0s3jn54khce6mua7l'
    private static final int[] GENERATOR = [
            0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3
    ] as int[]

    private NostrBech32() {}

    /** Decode {@code nsec1…} or 64-hex secret to 32 raw bytes. */
    static byte[] decodeSecretKey(String nsecOrHex) {
        String raw = nsecOrHex?.trim()
        if (!raw) throw new IllegalArgumentException('nsec/hex secret required')
        if (raw ==~ /^[0-9a-fA-F]{64}$/) {
            return NostrAuthService.hexToBytes(raw.toLowerCase())
        }
        String lower = raw.toLowerCase()
        if (!lower.startsWith('nsec1')) {
            throw new IllegalArgumentException('secret must be nsec1… or 64-char hex')
        }
        return decodeToBytes(lower, 'nsec')
    }

    /** Decode {@code npub1…} or 64-hex pubkey to 32-byte x-only pubkey. */
    static byte[] decodePubkey(String npubOrHex) {
        String raw = npubOrHex?.trim()
        if (!raw) throw new IllegalArgumentException('npub/hex pubkey required')
        if (raw ==~ /^[0-9a-fA-F]{64}$/) {
            return NostrAuthService.hexToBytes(raw.toLowerCase())
        }
        String lower = raw.toLowerCase()
        if (!lower.startsWith('npub1')) {
            throw new IllegalArgumentException('pubkey must be npub1… or 64-char hex')
        }
        return decodeToBytes(lower, 'npub')
    }

    static byte[] decodeToBytes(String bech, String expectedHrp) {
        String s = bech?.trim()?.toLowerCase()
        if (!s) throw new IllegalArgumentException('bech32 string required')
        int pos = s.lastIndexOf('1')
        if (pos < 1 || pos + 7 > s.length()) {
            throw new IllegalArgumentException('invalid bech32: missing separator')
        }
        String hrp = s.substring(0, pos)
        if (expectedHrp && hrp != expectedHrp) {
            throw new IllegalArgumentException("expected hrp ${expectedHrp}, got ${hrp}")
        }
        String dataPart = s.substring(pos + 1)
        int[] values = new int[dataPart.length()]
        for (int i = 0; i < dataPart.length(); i++) {
            int c = CHARSET.indexOf((int) dataPart.charAt(i))
            if (c < 0) throw new IllegalArgumentException("invalid bech32 character at ${i}")
            values[i] = c
        }
        if (!verifyChecksum(hrp, values)) {
            throw new IllegalArgumentException('invalid bech32 checksum')
        }
        int[] payload = java.util.Arrays.copyOf(values, values.length - 6)
        return convertBits(payload, 5, 8, false)
    }

    private static boolean verifyChecksum(String hrp, int[] data) {
        int[] exp = hrpExpand(hrp)
        int[] values = new int[exp.length + data.length]
        System.arraycopy(exp, 0, values, 0, exp.length)
        System.arraycopy(data, 0, values, exp.length, data.length)
        return polymod(values) == 1
    }

    private static int[] hrpExpand(String hrp) {
        int[] ret = new int[hrp.length() * 2 + 1]
        for (int i = 0; i < hrp.length(); i++) {
            ret[i] = ((int) hrp.charAt(i)) >> 5
            ret[i + hrp.length() + 1] = ((int) hrp.charAt(i)) & 31
        }
        ret[hrp.length()] = 0
        return ret
    }

    private static int polymod(int[] values) {
        int chk = 1
        for (int v : values) {
            int b = chk >> 25
            chk = ((chk & 0x1ffffff) << 5) ^ v
            for (int i = 0; i < 5; i++) {
                if (((b >> i) & 1) != 0) chk ^= GENERATOR[i]
            }
        }
        return chk
    }

    private static byte[] convertBits(int[] data, int fromBits, int toBits, boolean pad) {
        int acc = 0
        int bits = 0
        int maxv = (1 << toBits) - 1
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>()
        for (int value : data) {
            if (value < 0 || (value >> fromBits) != 0) {
                throw new IllegalArgumentException('invalid convertBits value')
            }
            acc = (acc << fromBits) | value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                out.add(Integer.valueOf((acc >> bits) & maxv))
            }
        }
        if (pad) {
            if (bits > 0) out.add(Integer.valueOf((acc << (toBits - bits)) & maxv))
        } else {
            if (bits >= fromBits) {
                throw new IllegalArgumentException('invalid convertBits leftover')
            }
            if (bits > 0 && ((acc << (toBits - bits)) & maxv) != 0) {
                throw new IllegalArgumentException('invalid convertBits padding')
            }
        }
        byte[] bytes = new byte[out.size()]
        for (int i = 0; i < out.size(); i++) {
            bytes[i] = (byte) out.get(i).intValue()
        }
        if (bytes.length != 32) {
            throw new IllegalArgumentException("expected 32-byte key, got ${bytes.length}")
        }
        return bytes
    }
}
