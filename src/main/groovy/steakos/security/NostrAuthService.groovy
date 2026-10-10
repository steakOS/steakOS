// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.security

import fr.acinq.secp256k1.Secp256k1
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * NIP-07 login: challenge + signed Nostr event verify (AUTH concept A3–A5).
 */
@Slf4j
@CompileStatic
class NostrAuthService {

    private static final long TTL_MS = 5 * 60 * 1000L
    private final ConcurrentHashMap<String, Long> challenges = new ConcurrentHashMap<>()
    private final SecureRandom random = new SecureRandom()
    private final Secp256k1 secp = Secp256k1.get()

    String issueChallenge() {
        byte[] buf = new byte[32]
        random.nextBytes(buf)
        String challenge = bytesToHex(buf)
        challenges.put(challenge, System.currentTimeMillis() + TTL_MS)
        challenge
    }

    boolean consumeChallenge(String challenge) {
        if (!challenge) return false
        Long exp = challenges.remove(challenge)
        return exp != null && exp >= System.currentTimeMillis()
    }

    /**
     * Verify a signed kind-22242 (or any) event that includes the challenge tag.
     * @param event Map with id, pubkey, created_at, kind, tags, content, sig (hex strings)
     * @return hex pubkey if valid
     */
    String verifyLoginEvent(Map event, String expectedChallenge) {
        if (!event || !expectedChallenge) {
            throw new IllegalArgumentException('event and challenge required')
        }
        if (!consumeChallenge(expectedChallenge)) {
            throw new IllegalArgumentException('challenge expired or unknown')
        }
        String pubkey = (event.pubkey as String)?.toLowerCase()
        String sig = (event.sig as String)?.toLowerCase()
        String id = (event.id as String)?.toLowerCase()
        if (!pubkey || pubkey.length() != 64 || !sig || sig.length() != 128 || !id || id.length() != 64) {
            throw new IllegalArgumentException('invalid event fields')
        }
        // challenge must appear in tags
        boolean found = false
        List tags = event.tags as List ?: []
        tags.each { Object t ->
            if (t instanceof List && (t as List).size() >= 2) {
                if ((t as List)[0]?.toString() == 'challenge' && (t as List)[1]?.toString() == expectedChallenge) {
                    found = true
                }
            }
        }
        if (!found && (event.content as String) != expectedChallenge) {
            throw new IllegalArgumentException('challenge not present in event')
        }

        String computed = computeEventId(event)
        if (computed != id) {
            throw new IllegalArgumentException('event id mismatch')
        }
        byte[] msg = hexToBytes(id)
        byte[] signature = hexToBytes(sig)
        byte[] pub = hexToBytes(pubkey)
        if (!secp.verifySchnorr(signature, msg, pub)) {
            throw new IllegalArgumentException('invalid signature')
        }
        pubkey
    }

    /** Convert hex pubkey to npub-ish storage form (hex ok; bech32 optional later). */
    static String normalizePubkey(String pubkeyOrNpub) {
        String p = pubkeyOrNpub?.trim()
        if (!p) return null
        if (p.startsWith('npub1')) {
            // keep as-is for display storage; findByNpub must match what we store
            return p
        }
        return p.toLowerCase()
    }

    static String computeEventId(Map event) {
        // NIP-01: [0, pubkey, created_at, kind, tags, content]
        List payload = [
                0,
                event.pubkey,
                (event.created_at instanceof Number) ? (event.created_at as Number).longValue() : Long.parseLong(event.created_at.toString()),
                (event.kind instanceof Number) ? (event.kind as Number).intValue() : Integer.parseInt(event.kind.toString()),
                event.tags ?: [],
                event.content ?: ''
        ]
        String json = JsonOutput.toJson(payload)
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8))
        bytesToHex(hash)
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2)
        for (byte b : bytes) {
            sb.append(String.format('%02x', b))
        }
        sb.toString()
    }

    static byte[] hexToBytes(String hex) {
        int len = hex.length()
        byte[] data = new byte[len >> 1]
        for (int i = 0; i < len; i += 2) {
            data[i >> 1] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16))
        }
        data
    }
}
