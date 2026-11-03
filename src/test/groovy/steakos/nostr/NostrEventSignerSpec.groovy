// SPDX-License-Identifier: Apache-2.0
package steakos.nostr

import fr.acinq.secp256k1.Secp256k1
import spock.lang.Specification
import steakos.security.NostrAuthService

/**
 * PL-TR4 — nsec decode + Schnorr event signing (NIP-19 / NIP-01).
 */
class NostrEventSignerSpec extends Specification {

    NostrEventSigner signer = new NostrEventSigner()
    Secp256k1 secp = Secp256k1.get()

    def "NIP-19 nsec/npub test vectors decode"() {
        when:
        byte[] sk = NostrBech32.decodeSecretKey('nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5')
        byte[] pk = NostrBech32.decodePubkey('npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg')

        then:
        NostrAuthService.bytesToHex(sk) == '67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa'
        NostrAuthService.bytesToHex(pk) == '7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e'
        signer.pubkeyHex(sk) == NostrAuthService.bytesToHex(pk)
    }

    def "64-hex secret accepted"() {
        given:
        String hex = '67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa'

        expect:
        NostrAuthService.bytesToHex(NostrBech32.decodeSecretKey(hex)) == hex
    }

    def "signed event verifies with NostrAuthService id + schnorr"() {
        given:
        byte[] sk = NostrBech32.decodeSecretKey('nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5')

        when:
        Map ev = signer.signEvent(sk, 1, 'steakOS audit', [['client', 'steakOS']], 1_700_000_000L)

        then:
        ev.id?.length() == 64
        ev.sig?.length() == 128
        ev.pubkey == '7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e'
        NostrAuthService.computeEventId(ev) == ev.id
        secp.verifySchnorr(
                NostrAuthService.hexToBytes(ev.sig as String),
                NostrAuthService.hexToBytes(ev.id as String),
                NostrAuthService.hexToBytes(ev.pubkey as String)
        )
    }

    def "NIP-42 AUTH event has kind 22242 and challenge tag"() {
        given:
        byte[] sk = NostrBech32.decodeSecretKey('nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5')

        when:
        Map auth = signer.signAuthEvent(sk, 'challenge-abc', 'ws://127.0.0.1:7777')

        then:
        auth.kind == 22242
        auth.tags.find { it[0] == 'challenge' && it[1] == 'challenge-abc' }
        auth.tags.find { it[0] == 'relay' && it[1] == 'ws://127.0.0.1:7777' }
        secp.verifySchnorr(
                NostrAuthService.hexToBytes(auth.sig as String),
                NostrAuthService.hexToBytes(auth.id as String),
                NostrAuthService.hexToBytes(auth.pubkey as String)
        )
    }
}
