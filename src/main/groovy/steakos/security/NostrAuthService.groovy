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

    static final long TTL_MS = 5 * 60 * 1000L
    static final int MAX_CHALLENGES = 10_000
    static final long MAX_SKEW_SEC = 120L
    private final ConcurrentHashMap<String, Long> challenges = new ConcurrentHashMap<>()
    private final SecureRandom random = new SecureRandom()
    private final Secp256k1 secp = Secp256k1.get()

    String issueChallenge() {
        byte[] buf = new byte[32]
        random.nextBytes(buf)
        String challenge = bytesToHex(buf)
        evictExpired()
        while (challenges.size() >= MAX_CHALLENGES) {
            String oldest = null
            long oldestExp = Long.MAX_VALUE
            for (Map.Entry<String, Long> entry : challenges.entrySet()) {
                if (entry.value < oldestExp) {
                    oldestExp = entry.value
                    oldest = entry.key
                }
            }
            if (oldest == null) break
            challenges.remove(oldest)
        }
        challenges.put(challenge, System.currentTimeMillis() + TTL_MS)
        challenge
    }

    private void evictExpired() {
        long now = System.currentTimeMillis()
        challenges.entrySet().removeIf { Map.Entry<String, Long> e -> e.value < now }
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
        verifyLoginEvent(event, expectedChallenge, null)
    }

    String verifyLoginEvent(Map event, String expectedChallenge, String expectedOrigin) {
        if (!event || !expectedChallenge) {
            throw new IllegalArgumentException('event and challenge required')
        }
        if (!consumeChallenge(expectedChallenge)) {
            throw new IllegalArgumentException('challenge expired or unknown')
        }
        int kind = (event.kind instanceof Number) ? (event.kind as Number).intValue() : Integer.parseInt(event.kind?.toString() ?: '0')
        if (kind != 22242) {
            throw new IllegalArgumentException('event kind must be 22242')
        }
        long created = (event.created_at instanceof Number) ? (event.created_at as Number).longValue() : Long.parseLong(event.created_at?.toString() ?: '0')
        long nowSec = (long) (System.currentTimeMillis() / 1000L)
        if (Math.abs(nowSec - created) > MAX_SKEW_SEC) {
            throw new IllegalArgumentException('event timestamp is outside the allowed window')
        }
        String pubkey = (event.pubkey as String)?.toLowerCase()
        String sig = (event.sig as String)?.toLowerCase()
        String id = (event.id as String)?.toLowerCase()
        if (!pubkey || pubkey.length() != 64 || !sig || sig.length() != 128 || !id || id.length() != 64) {
            throw new IllegalArgumentException('invalid event fields')
        }
        boolean foundChallenge = false
        boolean foundOrigin = false
        List tags = event.tags as List ?: []
        tags.each { Object t ->
            if (!(t instanceof List) || (t as List).size() < 2) return
            List row = t as List
            String tag = row[0]?.toString()
            String value = row[1]?.toString()
            if (tag == 'challenge' && value == expectedChallenge) foundChallenge = true
            if ((tag == 'relay' || tag == 'origin') && originMatches(value, expectedOrigin)) foundOrigin = true
        }
        if (!foundChallenge) {
            throw new IllegalArgumentException('challenge tag required')
        }
        if (expectedOrigin && !foundOrigin) {
            throw new IllegalArgumentException('origin tag required')
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
    static boolean originMatches(String tagValue, String expectedOrigin) {
        if (!expectedOrigin) return true
        if (!tagValue) return false
        String tag = tagValue.trim().toLowerCase()
        String origin = expectedOrigin.trim().toLowerCase()
        if (tag == origin) return true
        try {
            URI originUri = new URI(origin)
            URI tagUri = tag.contains('://') ? new URI(tag) : null
            String originHost = originUri.host
            if (tagUri?.host && originHost && tagUri.host.equalsIgnoreCase(originHost)) return true
            if (originHost && tag == originHost) return true
        } catch (ignored) {
        }
        return false
    }

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
