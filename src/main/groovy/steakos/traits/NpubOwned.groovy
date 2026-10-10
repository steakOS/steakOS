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
 * Multi-tenant ownership by Nostr public key (ADR-003, ADR-011, ADR-014).
 * PDF: NpubOwnedTrait — legacy owner, deputy.
 * <p>
 * Domain classes implement this trait so every row is scoped by {@code npubOwner}.
 * Optional {@code deputyNpub} supports delegated access without transferring ownership.
 * </p>
 */
@CompileStatic
trait NpubOwned {

    /**
     * Tenant / owner npub (bech32 {@code npub1…} or hex). Required for tenant-owned rows.
     * System templates may use synthetic owners (e.g. {@code SYSTEM_TEMPLATE_copper}, ADR-029).
     */
    String npubOwner

    /**
     * Optional deputy npub with delegated operational rights (does not replace owner).
     */
    String deputyNpub

    boolean isOwnedBy(String npub) {
        npub != null && !npub.isEmpty() && npub == npubOwner
    }

    boolean isDeputy(String npub) {
        npub != null && !npub.isEmpty() && npub == deputyNpub
    }

    /** Owner or deputy may act on the row for common operational checks. */
    boolean isOwnedOrDeputizedBy(String npub) {
        isOwnedBy(npub) || isDeputy(npub)
    }

    boolean isSystemTemplate() {
        npubOwner != null && npubOwner.startsWith('SYSTEM_TEMPLATE_')
    }
}
