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

/**
 * In-process domain event envelope (ADR-025 skeleton — no outbox/Kafka yet).
 */
class DomainEvent {

    /** Unique id for idempotency when outbox lands. */
    String eventId

    /** Registered name from {@link DomainEventNames}. */
    String name

    /** Event schema version (semver-ish major as int for now). */
    int version = 1

    /** Tenant npub when applicable. */
    String npubOwner

    /** Publishing plugin / package label (e.g. sales, finance). */
    String source

    Date occurredAt = new Date()

    /** Opaque payload map. */
    Map payload = [:]

    Map toMap() {
        [
                eventId   : eventId,
                name      : name,
                version   : version,
                npubOwner : npubOwner,
                source    : source,
                occurredAt: occurredAt?.time,
                payload   : payload ?: [:]
        ]
    }

    static DomainEvent of(String name, String source, String npubOwner = null, Map payload = [:], int version = 1) {
        new DomainEvent(
                eventId   : UUID.randomUUID().toString(),
                name      : name,
                version   : version,
                npubOwner : npubOwner,
                source    : source,
                occurredAt: new Date(),
                payload   : payload ?: [:]
        )
    }
}
