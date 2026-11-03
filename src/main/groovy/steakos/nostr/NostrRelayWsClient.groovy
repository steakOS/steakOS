// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.nostr

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletionStage
import java.util.function.Function

/**
 * Thin JDK {@link WebSocket} client for NIP-01 EVENT + NIP-42 AUTH to a local relay.
 * No xyz.tcheeric:nostr-java-client (Spring clash). Injectable {@link Session} for unit tests.
 */
@Slf4j
@CompileStatic
class NostrRelayWsClient {

    long connectTimeoutMs = 5000L
    long messageTimeoutMs = 8000L
    /** How long to wait for an unsolicited AUTH challenge after connect before sending EVENT. */
    long authChallengeWaitMs = 800L

    NostrEventSigner signer = new NostrEventSigner()

    /** Override in tests to avoid a real network socket. */
    Function<String, Session> sessionFactory = new Function<String, Session>() {
        @Override
        Session apply(String url) {
            openJdkSession(url)
        }
    }

    /**
     * Sign (if needed) and publish one event. Performs NIP-42 AUTH when the relay challenges.
     *
     * @return [ok: boolean, eventId: String, detail: String, authenticated: boolean]
     */
    Map publishEvent(String relayUrl, String nsecOrHex, Map signedOrPartial) {
        if (!relayUrl?.trim()) throw new IllegalArgumentException('relayUrl required')
        if (!nsecOrHex?.trim()) throw new IllegalArgumentException('nsec required')
        byte[] sk = NostrEventSigner.decodeSecretKey(nsecOrHex)
        Map event = ensureSigned(sk, signedOrPartial)
        String eventId = event.id as String

        Session session = sessionFactory.apply(relayUrl.trim())
        boolean authenticated = false
        try {
            String early = session.poll(authChallengeWaitMs)
            if (early) {
                authenticated = handleIncoming(session, sk, relayUrl, early, authenticated)
            }

            session.send(JsonOutput.toJson(['EVENT', event]))
            String reply = session.receive(messageTimeoutMs)
            if (!reply) {
                return [ok: false, eventId: eventId, detail: 'timeout waiting for OK after EVENT', authenticated: authenticated]
            }

            Map parsed = parseOkOrAuth(reply)
            if (parsed.type == 'AUTH') {
                authenticated = doAuth(session, sk, relayUrl, parsed.challenge as String)
                session.send(JsonOutput.toJson(['EVENT', event]))
                reply = session.receive(messageTimeoutMs)
                if (!reply) {
                    return [ok: false, eventId: eventId, detail: 'timeout waiting for OK after AUTH+EVENT', authenticated: authenticated]
                }
                parsed = parseOkOrAuth(reply)
            }

            if (parsed.type == 'OK') {
                boolean ok = parsed.ok as boolean
                String msg = (parsed.message ?: '') as String
                if (!ok && msg.toLowerCase().contains('auth-required') && !authenticated) {
                    String challenge = parsed.challenge as String
                    if (!challenge) {
                        String maybeAuth = session.poll(authChallengeWaitMs)
                        if (maybeAuth) {
                            Map a = parseOkOrAuth(maybeAuth)
                            if (a.type == 'AUTH') challenge = a.challenge as String
                        }
                    }
                    if (!challenge) {
                        return [ok: false, eventId: eventId, detail: "auth-required but no challenge: ${msg}", authenticated: false]
                    }
                    authenticated = doAuth(session, sk, relayUrl, challenge)
                    session.send(JsonOutput.toJson(['EVENT', event]))
                    reply = session.receive(messageTimeoutMs)
                    if (!reply) {
                        return [ok: false, eventId: eventId, detail: 'timeout after auth-required retry', authenticated: authenticated]
                    }
                    parsed = parseOkOrAuth(reply)
                    ok = parsed.type == 'OK' && (parsed.ok as boolean)
                    msg = (parsed.message ?: '') as String
                }
                return [
                        ok           : ok,
                        eventId      : (parsed.eventId ?: eventId) as String,
                        detail       : msg,
                        authenticated: authenticated
                ]
            }

            if (parsed.type == 'AUTH') {
                authenticated = doAuth(session, sk, relayUrl, parsed.challenge as String)
                session.send(JsonOutput.toJson(['EVENT', event]))
                reply = session.receive(messageTimeoutMs)
                parsed = reply ? parseOkOrAuth(reply) : [type: 'NONE']
                if (parsed.type == 'OK') {
                    return [
                            ok           : parsed.ok as boolean,
                            eventId      : (parsed.eventId ?: eventId) as String,
                            detail       : (parsed.message ?: '') as String,
                            authenticated: authenticated
                    ]
                }
            }

            return [ok: false, eventId: eventId, detail: "unexpected relay message: ${reply}", authenticated: authenticated]
        } finally {
            try {
                session.close()
            } catch (Exception e) {
                log.debug('relay session close: {}', e.message)
            }
        }
    }

    private boolean handleIncoming(Session session, byte[] sk, String relayUrl, String raw, boolean alreadyAuthed) {
        Map parsed = parseOkOrAuth(raw)
        if (parsed.type == 'AUTH' && !alreadyAuthed) {
            return doAuth(session, sk, relayUrl, parsed.challenge as String)
        }
        alreadyAuthed
    }

    private boolean doAuth(Session session, byte[] sk, String relayUrl, String challenge) {
        Map authEvent = signer.signAuthEvent(sk, challenge, relayUrl)
        session.send(JsonOutput.toJson(['AUTH', authEvent]))
        String reply = session.receive(messageTimeoutMs)
        if (!reply) throw new IOException('timeout waiting for OK after AUTH')
        Map parsed = parseOkOrAuth(reply)
        if (parsed.type != 'OK' || !(parsed.ok as boolean)) {
            throw new IOException("NIP-42 AUTH rejected: ${parsed.message ?: reply}")
        }
        true
    }

    private Map ensureSigned(byte[] sk, Map signedOrPartial) {
        if (signedOrPartial?.id && signedOrPartial?.sig && signedOrPartial?.pubkey) {
            return signedOrPartial
        }
        int kind = signedOrPartial?.kind != null ? (signedOrPartial.kind as Number).intValue() : 1
        String content = signedOrPartial?.content != null ? signedOrPartial.content.toString() : ''
        List tags = signedOrPartial?.tags instanceof List ? (signedOrPartial.tags as List) : []
        Long created = null
        if (signedOrPartial?.created_at != null) {
            created = (signedOrPartial.created_at as Number).longValue()
        }
        signer.signEvent(sk, kind, content, tags, created)
    }

    /** Parse relay frames: AUTH challenge, OK, or NOTICE. */
    static Map parseOkOrAuth(String raw) {
        Object parsed
        try {
            parsed = new JsonSlurper().parseText(raw)
        } catch (Exception e) {
            return [type: 'UNKNOWN', raw: raw, message: e.message]
        }
        if (!(parsed instanceof List) || (parsed as List).isEmpty()) {
            return [type: 'UNKNOWN', raw: raw]
        }
        List list = parsed as List
        String verb = list[0]?.toString()
        if (verb == 'AUTH' && list.size() >= 2) {
            Object second = list[1]
            if (second instanceof Map) {
                return [type: 'UNKNOWN', raw: raw]
            }
            return [type: 'AUTH', challenge: second?.toString()]
        }
        if (verb == 'OK' && list.size() >= 3) {
            String id = list[1]?.toString()
            boolean ok = Boolean.TRUE == list[2] || list[2]?.toString() == 'true'
            String msg = list.size() >= 4 ? list[3]?.toString() : ''
            return [type: 'OK', eventId: id, ok: ok, message: msg, challenge: null]
        }
        if (verb == 'NOTICE') {
            return [type: 'NOTICE', message: list.size() >= 2 ? list[1]?.toString() : '']
        }
        return [type: 'UNKNOWN', raw: raw]
    }

    static String frameEvent(Map event) {
        JsonOutput.toJson(['EVENT', event])
    }

    static String frameAuth(Map authEvent) {
        JsonOutput.toJson(['AUTH', authEvent])
    }

    private Session openJdkSession(String relayUrl) {
        LinkedBlockingQueue<String> inbox = new LinkedBlockingQueue<>()
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build()
        CompletableFuture<WebSocket> future = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .buildAsync(URI.create(relayUrl), new WebSocket.Listener() {
                    @Override
                    void onOpen(WebSocket webSocket) {
                        webSocket.request(1)
                    }

                    @Override
                    CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        inbox.offer(data.toString())
                        webSocket.request(1)
                        return null
                    }

                    @Override
                    void onError(WebSocket webSocket, Throwable error) {
                        log.warn('Nostr relay WS error: {}', error?.message)
                    }
                })
        WebSocket ws = future.get(connectTimeoutMs, TimeUnit.MILLISECONDS)
        new Session() {
            @Override
            void send(String text) {
                ws.sendText(text, true).get(messageTimeoutMs, TimeUnit.MILLISECONDS)
            }

            @Override
            String receive(long timeoutMs) {
                inbox.poll(timeoutMs, TimeUnit.MILLISECONDS)
            }

            @Override
            String poll(long timeoutMs) {
                inbox.poll(timeoutMs, TimeUnit.MILLISECONDS)
            }

            @Override
            void close() {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, 'bye').get(2, TimeUnit.SECONDS)
                } catch (ignored) {
                }
            }
        }
    }

    /** Minimal duplex session used by {@link #publishEvent}. */
    interface Session {
        void send(String text)

        String receive(long timeoutMs)

        String poll(long timeoutMs)

        void close()
    }
}
