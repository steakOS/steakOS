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
 * In-process event name registry (OI-A02). Validates publish/subscribe names.
 * Future: plugin-events.yml manifests, version compatibility, publicNostr flags.
 */
final class DomainEventRegistry {

    private DomainEventRegistry() {}

    static List<String> knownNames() {
        DomainEventNames.ALL
    }

    static void requireKnown(String name) {
        if (!DomainEventNames.isKnown(name)) {
            throw new IllegalArgumentException("Unknown domain event name: ${name}")
        }
    }

    static boolean isRegistered(String name) {
        DomainEventNames.isKnown(name)
    }

    static List<String> intendedConsumers(String name) {
        requireKnown(name)
        DomainEventNames.INTENDED_CONSUMERS[name] ?: []
    }

    /** Snapshot for diagnostics / smoke. */
    static Map catalog() {
        DomainEventNames.ALL.collectEntries { String n ->
            [(n): [version: 1, consumers: DomainEventNames.INTENDED_CONSUMERS[n] ?: []]]
        }
    }
}
