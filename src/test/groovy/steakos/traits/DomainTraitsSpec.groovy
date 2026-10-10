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

import steakos.security.RoleLevel
import spock.lang.Specification

/**
 * Pure unit tests for all 12 steakOS domain traits (no Spring / GORM session).
 */
class DomainTraitsSpec extends Specification {

    static class FullEntity implements
            NpubOwned, Auditable, NostrPublishable, RoleRestricted, AccessControl,
            AiTagged, DynamicExtensible, Classifiable, Versioned, Networked,
            Attachable, Reputation {
    }

    def "NpubOwned ownership and deputy"() {
        given:
        def e = new FullEntity(npubOwner: 'npub1owner', deputyNpub: 'npub1deputy')

        expect:
        e.isOwnedBy('npub1owner')
        e.isDeputy('npub1deputy')
        e.isOwnedOrDeputizedBy('npub1deputy')
        !e.isOwnedBy('npub1other')
        !e.isSystemTemplate()

        when:
        e.npubOwner = 'SYSTEM_TEMPLATE_copper'

        then:
        e.isSystemTemplate()
    }

    def "Auditable stamps actors without revision"() {
        given:
        def e = new FullEntity()

        when:
        e.markCreatedBy('npub1alice')
        e.markUpdatedBy('npub1bob')

        then:
        e.createdByNpub == 'npub1alice'
        e.lastUpdatedByNpub == 'npub1bob'
        // revision is on Versioned only (A1)
        e.revision == 1L
    }

    def "NostrPublishable records event refs"() {
        given:
        def e = new FullEntity()

        when:
        e.recordNostrEvent('abc123', 'msg-9')
        e.publicNostr = true

        then:
        e.hasNostrEvent()
        e.nostrEventId == 'abc123'
        e.nostrMessageRef == 'msg-9'
        e.publicNostr
    }

    def "RoleRestricted write rules block AI direct write"() {
        given:
        def e = new FullEntity(minWriteRole: 'A', apqcAreaCode: '4.0')

        expect:
        e.allowsWriteFor(['ROLE_C'])
        e.allowsWriteFor(['ROLE_A'])
        !e.allowsWriteFor(['ROLE_AI'])
        e.allowsReadFor(['ROLE_AI'])
        e.minWriteRoleLevel == RoleLevel.A
    }

    def "AccessControl list mode"() {
        given:
        def e = new FullEntity(protection: 'LIST', access: 'npub1a, npub1b')

        expect:
        !e.isOwnerOnly()
        e.accessListContains('npub1a')
        !e.accessListContains('npub1c')
    }

    def "AiTagged proposal marking"() {
        given:
        def e = new FullEntity()

        when:
        e.markFromProposal('prop-42')

        then:
        e.isFromAi()
        e.proposalId == 'prop-42'
    }

    def "DynamicExtensible map and external id"() {
        given:
        def e = new FullEntity(extSource: 'btcpay', extId: 'inv-1')

        when:
        e.putDynamic('currency', 'EUR')

        then:
        e.hasExternalIdentity()
        e.getDynamicValue('currency') == 'EUR'
        e.dynamic.currency == 'EUR'
    }

    def "Classifiable tags and status"() {
        given:
        def e = new FullEntity(status: 'DRAFT', type: 'STANDARD')

        when:
        e.addTag('finance')
        e.addTag('mvp')
        e.addTag('finance')

        then:
        e.hasStatus('DRAFT')
        e.tagList() == ['finance', 'mvp']
    }

    def "Versioned bumps business revision"() {
        given:
        def e = new FullEntity()

        when:
        e.bumpRevision('after-proposal')

        then:
        e.revision == 2L
        e.revisionNote == 'after-proposal'
    }

    def "Networked scalar links"() {
        given:
        def e = new FullEntity(parentId: 10L, predecessorId: 8L, successorId: 12L)

        expect:
        e.hasParent()
        !e.isRoot()
        e.successorId == 12L
    }

    def "Attachable attachment metadata"() {
        given:
        def e = new FullEntity(attachmentMeta: '{"filename":"inv.pdf","mime":"application/pdf"}')

        when:
        e.markAttachmentAdded()

        then:
        e.attachmentMeta.contains('inv.pdf')
        e.attachmentCount == 1
    }

    def "Reputation points and karma"() {
        given:
        def e = new FullEntity(rating: 4.5G)

        when:
        e.addPoints(10)
        e.addKarma(2)

        then:
        e.points == 10L
        e.karma == 2L
        e.rating == 4.5G
    }
}
