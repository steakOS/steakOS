// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package steakos.events

import groovy.util.logging.Slf4j

/**
 * In-process publisher/subscriber façade (ADR-025 skeleton).
 * Synchronous fan-out; no Kafka/outbox — replace internals later without renaming events.
 */
@Slf4j
class DomainEventBusService {

    private final Object lock = new Object()
    private final Map<String, List<DomainEventSubscriber>> subscribers = [:].withDefault { [] }

    /**
     * Subscribe to a registered event name. Returns unsubscribe closure.
     */
    Closure subscribe(String eventName, DomainEventSubscriber subscriber) {
        DomainEventRegistry.requireKnown(eventName)
        if (subscriber == null) {
            throw new IllegalArgumentException('subscriber is required')
        }
        synchronized (lock) {
            subscribers[eventName] << subscriber
        }
        return {
            synchronized (lock) {
                subscribers[eventName]?.remove(subscriber)
            }
        }
    }

    /** Publish a fully built event (name must be registered). */
    void publish(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException('event is required')
        }
        DomainEventRegistry.requireKnown(event.name)
        if (!event.eventId) {
            event.eventId = UUID.randomUUID().toString()
        }
        if (!event.occurredAt) {
            event.occurredAt = new Date()
        }
        List<DomainEventSubscriber> handlers
        synchronized (lock) {
            handlers = new ArrayList<>(subscribers[event.name] ?: [])
        }
        handlers.each { DomainEventSubscriber h ->
            try {
                h.onEvent(event)
            } catch (Exception e) {
                log.warn("domain event handler failed name=${event.name} id=${event.eventId}: ${e.message}")
            }
        }
    }

    /** Convenience: build + publish. */
    DomainEvent publish(String name, String source, String npubOwner = null, Map payload = [:], int version = 1) {
        DomainEvent ev = DomainEvent.of(name, source, npubOwner, payload, version)
        publish(ev)
        ev
    }

    /** Test/diag: clear all subscribers. */
    void clearSubscribers() {
        synchronized (lock) {
            subscribers.clear()
        }
    }

    int subscriberCount(String eventName) {
        synchronized (lock) {
            return (subscribers[eventName] ?: []).size()
        }
    }
}
