// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.nostr

import fr.acinq.secp256k1.Secp256k1
import groovy.transform.CompileStatic
import steakos.security.NostrAuthService

/**
 * Sign NIP-01 events with fr.acinq.secp256k1 (BIP-340 Schnorr).
 * Reuses {@link NostrAuthService#computeEventId} for id hashing.
 */
@CompileStatic
class NostrEventSigner {

    static final int KIND_AUTH = 22242

    private final Secp256k1 secp = Secp256k1.get()

    /** Decode nsec1… or 64-hex → 32-byte secret. */
    static byte[] decodeSecretKey(String nsecOrHex) {
        NostrBech32.decodeSecretKey(nsecOrHex)
    }

    /** X-only hex pubkey (64 chars) from 32-byte secret. */
    String pubkeyHex(byte[] secretKey) {
        requireSk(secretKey)
        byte[] uncompressed = secp.pubkeyCreate(secretKey)
        if (uncompressed == null || uncompressed.length < 33) {
            throw new IllegalStateException('pubkeyCreate failed')
        }
        byte[] xonly = java.util.Arrays.copyOfRange(uncompressed, 1, 33)
        NostrAuthService.bytesToHex(xonly)
    }

    /**
     * Build and Schnorr-sign a NIP-01 event.
     * @return map with id, pubkey, created_at, kind, tags, content, sig
     */
    Map signEvent(byte[] secretKey, int kind, String content, List tags, Long createdAtSec = null) {
        requireSk(secretKey)
        long created = createdAtSec != null ? createdAtSec.longValue() : (long) (System.currentTimeMillis() / 1000L)
        String pubkey = pubkeyHex(secretKey)
        List safeTags = tags != null ? tags : []
        Map unsigned = [
                pubkey    : pubkey,
                created_at: created,
                kind      : kind,
                tags      : safeTags,
                content   : content != null ? content : ''
        ]
        String id = NostrAuthService.computeEventId(unsigned)
        byte[] sig = secp.signSchnorr(NostrAuthService.hexToBytes(id), secretKey, null)
        [
                id        : id,
                pubkey    : pubkey,
                created_at: created,
                kind      : kind,
                tags      : safeTags,
                content   : unsigned.content,
                sig       : NostrAuthService.bytesToHex(sig)
        ]
    }

    /** NIP-42 AUTH event (kind 22242) for a relay challenge. */
    Map signAuthEvent(byte[] secretKey, String challenge, String relayUrl) {
        if (!challenge?.trim()) throw new IllegalArgumentException('AUTH challenge required')
        String relay = relayUrl?.trim() ?: ''
        List tags = [
                ['relay', relay],
                ['challenge', challenge]
        ]
        signEvent(secretKey, KIND_AUTH, '', tags)
    }

    /** Convenience: nsec string → signed note. */
    Map signNote(String nsecOrHex, String content, int kind = 1, List tags = []) {
        signEvent(decodeSecretKey(nsecOrHex), kind, content, tags)
    }

    private static void requireSk(byte[] secretKey) {
        if (secretKey == null || secretKey.length != 32) {
            throw new IllegalArgumentException('secret key must be 32 bytes')
        }
    }
}
