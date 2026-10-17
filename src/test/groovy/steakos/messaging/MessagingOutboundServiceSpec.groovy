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
import steakos.configuration.CompanyConfigService
import steakos.events.DomainEvent
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames

/**
 * PR-8 — flags default off (no-op); enabled+configured sends via stub transports.
 */
class MessagingOutboundServiceSpec extends Specification {

    Map<String, String> stored = [:]
    CompanyConfigService configs
    RecordingEmailTransport emailTransport
    RecordingTelegramTransport telegramTransport
    DomainEventBusService bus
    MessagingOutboundService service
    List<DomainEvent> audited = []

    def setup() {
        stored.clear()
        audited.clear()
        configs = Stub(CompanyConfigService) {
            getString(_, _, _) >> { String npub, String key, String defVal ->
                stored.containsKey("${npub}|${key}".toString()) ? stored.get("${npub}|${key}".toString()) : defVal
            }
            getBoolean(_, _, _) >> { String npub, String key, Boolean defVal ->
                String v = stored.get("${npub}|${key}".toString())
                if (v == null) return defVal
                return v.equalsIgnoreCase('true') || v == '1'
            }
        }
        emailTransport = new RecordingEmailTransport()
        telegramTransport = new RecordingTelegramTransport()
        bus = new DomainEventBusService()
        bus.subscribe(DomainEventNames.MESSAGING_OUTBOUND_SENT) { DomainEvent e ->
            audited << e
        }

        EmailSmtpSender email = new EmailSmtpSender(
                companyConfigService: configs,
                emailTransport: emailTransport
        )
        TelegramBotSender telegram = new TelegramBotSender(
                companyConfigService: configs,
                telegramTransport: telegramTransport
        )
        service = new MessagingOutboundService(
                companyConfigService: configs,
                domainEventBusService: bus,
                emailSmtpSender: email,
                telegramBotSender: telegram
        )
    }

    def cleanup() {
        bus.clearSubscribers()
    }

    def "keys match PR-3 SetupService constants"() {
        expect:
        MessagingKeys.EMAIL_ENABLED == 'messaging.emailEnabled'
        MessagingKeys.TELEGRAM_ENABLED == 'messaging.telegramEnabled'
        MessagingKeys.EMAIL_SMTP_HOST == 'messaging.email.smtpHost'
        MessagingKeys.TELEGRAM_BOT_TOKEN == 'messaging.telegram.botToken'
    }

    def "flags default false — email and telegram no-op"() {
        given:
        String npub = 'company:demo'

        when:
        MessagingSendResult email = service.send(npub, 'email', 'finance.invoice.overdue',
                [to: 'ops@example.com', text: 'Overdue'])
        MessagingSendResult tg = service.send(npub, 'telegram', 'finance.invoice.overdue',
                [text: 'Overdue'])

        then:
        email.isSkipped()
        email.reason == 'disabled'
        tg.isSkipped()
        tg.reason == 'disabled'
        emailTransport.delivered.isEmpty()
        telegramTransport.sent.isEmpty()
        audited.isEmpty()
    }

    def "email enabled but not configured skips"() {
        given:
        String npub = 'company:demo'
        stored["${npub}|${MessagingKeys.EMAIL_ENABLED}"] = 'true'

        when:
        MessagingSendResult r = service.send(npub, 'email', 't1', [to: 'a@b.c', text: 'hi'])

        then:
        r.isSkipped()
        r.reason == 'not_configured'
        emailTransport.delivered.isEmpty()
    }

    def "email enabled and configured sends via stub and audits"() {
        given:
        String npub = 'company:demo'
        stored["${npub}|${MessagingKeys.EMAIL_ENABLED}"] = 'true'
        stored["${npub}|${MessagingKeys.EMAIL_SMTP_HOST}"] = 'smtp.example.com'
        stored["${npub}|${MessagingKeys.EMAIL_FROM}"] = 'noreply@steakos.local'

        when:
        MessagingSendResult r = service.send(npub, 'email', 'finance.invoice.overdue',
                [to: 'ops@example.com', subject: 'Overdue', text: 'Invoice 9'])

        then:
        r.isSent()
        r.channel == 'email'
        emailTransport.delivered.size() == 1
        emailTransport.delivered[0].to == 'ops@example.com'
        emailTransport.delivered[0].subject == 'Overdue'
        emailTransport.delivered[0].host == 'smtp.example.com'
        !emailTransport.delivered[0].containsKey('password')
        audited.size() == 1
        audited[0].name == DomainEventNames.MESSAGING_OUTBOUND_SENT
        audited[0].payload.channel == 'email'
    }

    def "telegram enabled and configured sends via stub and audits"() {
        given:
        String npub = 'company:demo'
        stored["${npub}|${MessagingKeys.TELEGRAM_ENABLED}"] = 'true'
        stored["${npub}|${MessagingKeys.TELEGRAM_BOT_TOKEN}"] = '123:ABC'
        stored["${npub}|${MessagingKeys.TELEGRAM_CHAT_ID}"] = '-1001'

        when:
        MessagingSendResult r = service.send(npub, 'telegram', 'agent.proposal.submitted',
                [text: 'Approve proposal?'])

        then:
        r.isSent()
        r.channel == 'telegram'
        telegramTransport.sent.size() == 1
        telegramTransport.sent[0].chatId == '-1001'
        telegramTransport.sent[0].text == 'Approve proposal?'
        telegramTransport.sent[0].hasToken == true
        audited.size() == 1
        audited[0].payload.channel == 'telegram'
    }

    def "notifyAll with both channels on delivers to both"() {
        given:
        String npub = 'company:demo'
        stored["${npub}|${MessagingKeys.EMAIL_ENABLED}"] = 'true'
        stored["${npub}|${MessagingKeys.EMAIL_SMTP_HOST}"] = 'smtp.example.com'
        stored["${npub}|${MessagingKeys.EMAIL_FROM}"] = 'noreply@steakos.local'
        stored["${npub}|${MessagingKeys.TELEGRAM_ENABLED}"] = 'true'
        stored["${npub}|${MessagingKeys.TELEGRAM_BOT_TOKEN}"] = '123:ABC'
        stored["${npub}|${MessagingKeys.TELEGRAM_CHAT_ID}"] = '-1001'

        when:
        List<MessagingSendResult> results = service.notifyAll(npub, 'finance.invoice.issued',
                [to: 'ops@example.com', text: 'Issued'])

        then:
        results.size() == 2
        results.every { it.isSent() }
        emailTransport.delivered.size() == 1
        telegramTransport.sent.size() == 1
        audited.size() == 2
    }

    def "domain event trigger no-ops when flags off"() {
        given:
        String npub = 'company:demo'
        DomainEvent ev = DomainEvent.of(
                DomainEventNames.FINANCE_INVOICE_OVERDUE, 'finance', npub,
                [invoiceId: 42, to: 'ops@example.com'])

        when:
        service.onDomainEvent(ev)

        then:
        emailTransport.delivered.isEmpty()
        telegramTransport.sent.isEmpty()
        audited.isEmpty()
    }

    def "domain event trigger sends when email enabled+configured"() {
        given:
        String npub = 'company:demo'
        stored["${npub}|${MessagingKeys.EMAIL_ENABLED}"] = 'true'
        stored["${npub}|${MessagingKeys.EMAIL_SMTP_HOST}"] = 'smtp.example.com'
        stored["${npub}|${MessagingKeys.EMAIL_FROM}"] = 'noreply@steakos.local'
        DomainEvent ev = DomainEvent.of(
                DomainEventNames.FINANCE_INVOICE_OVERDUE, 'finance', npub,
                [invoiceId: 42, to: 'ops@example.com'])

        when:
        service.onDomainEvent(ev)

        then:
        emailTransport.delivered.size() == 1
        emailTransport.delivered[0].to == 'ops@example.com'
        audited.size() == 1
        audited[0].payload.templateId == DomainEventNames.FINANCE_INVOICE_OVERDUE
    }

    def "health reports enabled/configured flags"() {
        given:
        String npub = 'company:demo'
        stored["${npub}|${MessagingKeys.EMAIL_ENABLED}"] = 'true'
        stored["${npub}|${MessagingKeys.EMAIL_SMTP_HOST}"] = 'smtp.example.com'
        stored["${npub}|${MessagingKeys.EMAIL_FROM}"] = 'noreply@steakos.local'

        when:
        Map h = service.health(npub)

        then:
        h.email.enabled == true
        h.email.configured == true
        h.email.ready == true
        h.telegram.enabled == false
        h.telegram.ready == false
    }
}
