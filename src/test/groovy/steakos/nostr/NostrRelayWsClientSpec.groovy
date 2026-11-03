// SPDX-License-Identifier: Apache-2.0
package steakos.nostr

import groovy.json.JsonSlurper
import spock.lang.Specification

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * PL-TR4 — AUTH + EVENT framing with mocked WebSocket session (no network).
 */
class NostrRelayWsClientSpec extends Specification {

    static final String NSEC = 'nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5'
    static final String RELAY = 'ws://127.0.0.1:7777'

    def "parseOkOrAuth recognizes AUTH and OK frames"() {
        expect:
        NostrRelayWsClient.parseOkOrAuth('["AUTH","chal-1"]').type == 'AUTH'
        NostrRelayWsClient.parseOkOrAuth('["AUTH","chal-1"]').challenge == 'chal-1'
        NostrRelayWsClient.parseOkOrAuth('["OK","abc",true,""]').ok == true
        NostrRelayWsClient.parseOkOrAuth('["OK","abc",false,"auth-required: login"]').ok == false
    }

    def "publishEvent AUTH-on-connect then EVENT OK"() {
        given:
        NostrRelayWsClient client = new NostrRelayWsClient(authChallengeWaitMs: 200, messageTimeoutMs: 2000)
        RecordingSession session = new RecordingSession()
        session.inbox.offer('["AUTH","chal-connect"]')
        client.sessionFactory = { String url -> session }

        // After AUTH send, provide OK for auth then OK for event — driven by send hook
        session.onSend = { String text ->
            def arr = new JsonSlurper().parseText(text) as List
            if (arr[0] == 'AUTH') {
                String id = (arr[1] as Map).id
                session.inbox.offer(groovy.json.JsonOutput.toJson(['OK', id, true, '']))
            } else if (arr[0] == 'EVENT') {
                String id = (arr[1] as Map).id
                session.inbox.offer(groovy.json.JsonOutput.toJson(['OK', id, true, '']))
            }
        }

        when:
        Map r = client.publishEvent(RELAY, NSEC, [kind: 1, content: 'audit hello', tags: []])

        then:
        r.ok == true
        r.authenticated == true
        r.eventId?.length() == 64
        session.sent.any { it.startsWith('["AUTH"') || it.startsWith("['AUTH'") || it.contains('"AUTH"') || it.contains('AUTH') }
        session.sent.count { it.contains('EVENT') } >= 1
    }

    def "publishEvent auth-required on EVENT then retry succeeds"() {
        given:
        NostrRelayWsClient client = new NostrRelayWsClient(authChallengeWaitMs: 50, messageTimeoutMs: 2000)
        RecordingSession session = new RecordingSession()
        client.sessionFactory = { String url -> session }
        int eventCount = 0
        session.onSend = { String text ->
            def arr = new JsonSlurper().parseText(text) as List
            if (arr[0] == 'EVENT') {
                eventCount++
                String id = (arr[1] as Map).id
                if (eventCount == 1) {
                    session.inbox.offer(groovy.json.JsonOutput.toJson(['OK', id, false, 'auth-required: please AUTH']))
                    session.inbox.offer('["AUTH","chal-late"]')
                } else {
                    session.inbox.offer(groovy.json.JsonOutput.toJson(['OK', id, true, '']))
                }
            } else if (arr[0] == 'AUTH') {
                String id = (arr[1] as Map).id
                session.inbox.offer(groovy.json.JsonOutput.toJson(['OK', id, true, '']))
            }
        }

        when:
        Map r = client.publishEvent(RELAY, NSEC, [kind: 1, content: 'retry', tags: []])

        then:
        r.ok == true
        r.authenticated == true
        eventCount == 2
    }

    def "frameEvent wraps EVENT verb"() {
        expect:
        NostrRelayWsClient.frameEvent([id: 'ab', kind: 1]).startsWith('["EVENT"')
    }

    /** In-memory duplex session. */
    static class RecordingSession implements NostrRelayWsClient.Session {
        final LinkedBlockingQueue<String> inbox = new LinkedBlockingQueue<>()
        final List<String> sent = []
        Closure onSend

        @Override
        void send(String text) {
            sent << text
            onSend?.call(text)
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
        void close() {}
    }
}
