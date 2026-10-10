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

package steakos.traits

import groovy.transform.CompileStatic

/**
 * Lightweight Nostr event references on domain state (ADR-003, ADR-005, ADR-013, ADR-025).
 * PDF: NostrPublishableTrait — event id/ref + publicNostr.
 * <p>
 * Full event content lives on Nostr; the relational model keeps only ids/refs.
 * Distinct from {@link Networked} (domain graph structure).
 * </p>
 */
@CompileStatic
trait NostrPublishable {

    /**
     * Latest related Nostr event id (hex), if any.
     */
    String nostrEventId

    /**
     * Optional secondary ref (e.g. message id, replacement event, or outbox key).
     */
    String nostrMessageRef

    /**
     * When true, the corresponding domain event is eligible for public Nostr publication
     * ({@code publicNostr: true} in ADR-025). Default false — private relay / internal only.
     */
    Boolean publicNostr = Boolean.FALSE

    boolean hasNostrEvent() {
        nostrEventId != null && !nostrEventId.isEmpty()
    }

    void recordNostrEvent(String eventId, String messageRef = null) {
        nostrEventId = eventId
        if (messageRef != null) {
            nostrMessageRef = messageRef
        }
    }
}
