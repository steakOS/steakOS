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

/**
 * Shared GORM constraint fragments for domains implementing steakOS traits.
 * Source of truth for composition: {@code docs/traits/TRAITS.md}.
 */
class DomainTraitConstraints {

    private DomainTraitConstraints() {}

    static final Closure npubOwnedFragment = {
        npubOwner nullable: false, blank: false, maxSize: 200
        deputyNpub nullable: true, maxSize: 200
    }

    static final Closure auditableFragment = {
        createdByNpub nullable: true, maxSize: 200
        lastUpdatedByNpub nullable: true, maxSize: 200
    }

    static final Closure nostrPublishableFragment = {
        nostrEventId nullable: true, maxSize: 128
        nostrMessageRef nullable: true, maxSize: 256
        publicNostr nullable: true
    }

    static final Closure roleRestrictedFragment = {
        apqcAreaCode nullable: true, maxSize: 64
        minWriteRole nullable: true, maxSize: 16
    }

    static final Closure accessControlFragment = {
        protection nullable: true, maxSize: 32
        access nullable: true, maxSize: 4000
    }

    static final Closure aiTaggedFragment = {
        aiGenerated nullable: true
        proposalId nullable: true, maxSize: 128
    }

    static final Closure dynamicExtensibleFragment = {
        extSource nullable: true, maxSize: 128
        extId nullable: true, maxSize: 256
        dynamic nullable: true
    }

    static final Closure classifiableFragment = {
        status nullable: true, maxSize: 64
        classification nullable: true, maxSize: 128
        type nullable: true, maxSize: 64
        tags nullable: true, maxSize: 2000
    }

    static final Closure versionedFragment = {
        revision nullable: true
        revisionNote nullable: true, maxSize: 2000
    }

    static final Closure networkedFragment = {
        parentId nullable: true
        predecessorId nullable: true
        successorId nullable: true
    }

    static final Closure attachableFragment = {
        attachmentMeta nullable: true, maxSize: 8000
        attachmentCount nullable: true
    }

    /** Optional Task-style assignee (human XOR agent). */
    static final Closure assignableFragment = {
        assigneeHumanNpub nullable: true, maxSize: 128
        assigneeAgentId nullable: true
    }

    static final Closure reputationFragment = {
        rating nullable: true, scale: 4
        points nullable: true
        karma nullable: true
    }

    /** Foundational five + common MVP extras (not all 12). */
    static final Closure corePlusMvpFragment = {
        npubOwner nullable: false, blank: false, maxSize: 200
        deputyNpub nullable: true, maxSize: 200
        createdByNpub nullable: true, maxSize: 200
        lastUpdatedByNpub nullable: true, maxSize: 200
        nostrEventId nullable: true, maxSize: 128
        nostrMessageRef nullable: true, maxSize: 256
        publicNostr nullable: true
        apqcAreaCode nullable: true, maxSize: 64
        minWriteRole nullable: true, maxSize: 16
        aiGenerated nullable: true
        proposalId nullable: true, maxSize: 128
        status nullable: true, maxSize: 64
        classification nullable: true, maxSize: 128
        type nullable: true, maxSize: 64
        tags nullable: true, maxSize: 2000
        revision nullable: true
        revisionNote nullable: true, maxSize: 2000
        extSource nullable: true, maxSize: 128
        extId nullable: true, maxSize: 256
        dynamic nullable: true
    }

    static void apply(Object constraintsDelegate, Closure fragment) {
        Closure c = (Closure) fragment.clone()
        c.delegate = constraintsDelegate
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
    }

    static void applyAll(Object constraintsDelegate) {
        apply(constraintsDelegate, corePlusMvpFragment)
    }

    static void applyNpubOwned(Object d) { apply(d, npubOwnedFragment) }
    static void applyAuditable(Object d) { apply(d, auditableFragment) }
    static void applyNostrPublishable(Object d) { apply(d, nostrPublishableFragment) }
    static void applyRoleRestricted(Object d) { apply(d, roleRestrictedFragment) }
    static void applyAccessControl(Object d) { apply(d, accessControlFragment) }
    static void applyAiTagged(Object d) { apply(d, aiTaggedFragment) }
    static void applyDynamicExtensible(Object d) { apply(d, dynamicExtensibleFragment) }
    static void applyClassifiable(Object d) { apply(d, classifiableFragment) }
    static void applyVersioned(Object d) { apply(d, versionedFragment) }
    static void applyNetworked(Object d) { apply(d, networkedFragment) }
    static void applyAttachable(Object d) { apply(d, attachableFragment) }
    static void applyReputation(Object d) { apply(d, reputationFragment) }
    static void applyAssignable(Object d) { apply(d, assignableFragment) }
}
