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

package steakos.messaging

import spock.lang.Specification

/**
 * OI-I18N02 PR4a / PL-TR3 — EN/DE messaging packs for partner email.
 */
class MessagingMessageBundleSpec extends Specification {

    def "EN pack has overdue and generic event keys"() {
        when:
        MessagingMessageBundle en = MessagingMessageBundle.forLanguage('en')

        then:
        en.language == 'en'
        en.get('msg.invoice.overdue.subject').contains('overdue')
        en.get('msg.invoice.overdue.body').contains('Open amount')
        en.get('msg.event.subject').contains('{0}')
        en.format('msg.invoice.overdue.subject', 'INV-1') == 'Invoice INV-1 is overdue'
        en.format('msg.event.subject', 'finance.invoice.issued').contains('finance.invoice.issued')
    }

    def "DE pack used for de_DE locale"() {
        when:
        MessagingMessageBundle de = MessagingMessageBundle.forLocale('de_DE')
        MessagingMessageBundle en = MessagingMessageBundle.forLocale('en_US')

        then:
        de.language == 'de'
        de.get('msg.invoice.overdue.subject').contains('überfällig')
        de.format('msg.invoice.overdue.subject', 'R-9') == 'Rechnung R-9 ist überfällig'
        de.get('msg.event.body').contains('Benachrichtigung')
        en.get('msg.invoice.overdue.subject').contains('overdue')
        !en.get('msg.invoice.overdue.subject').contains('überfällig')
    }

    def "unknown locale falls back to EN pack language"() {
        expect:
        MessagingMessageBundle.forLocale('fr_FR').language == 'en'
        MessagingMessageBundle.forLocale(null).language == 'en'
        MessagingMessageBundle.forLanguage('es').language == 'en'
    }
}
