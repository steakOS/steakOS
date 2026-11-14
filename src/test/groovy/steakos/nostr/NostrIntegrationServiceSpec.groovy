// SPDX-License-Identifier: Apache-2.0
package steakos.nostr

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.configuration.CompanyConfigService
import steakos.configuration.CompanySetting
import steakos.configuration.LegalEntity

/**
 * OI-NOSTR01 — level persistence + wizard field validation.
 */
class NostrIntegrationServiceSpec extends Specification implements DataTest {

    NostrIntegrationService service
    CompanyConfigService configs
    NostrPublishService publish

    Class[] getDomainClassesToMock() {
        [LegalEntity, CompanySetting] as Class[]
    }

    def cleanup() {
        NostrIntegrationService.botKeyOverride = null
    }

    def setup() {
        NostrIntegrationService.botKeyOverride = null
        configs = new CompanyConfigService()
        service = new NostrIntegrationService(companyConfigService: configs)
        publish = new NostrPublishService(
                nostrIntegrationService: service,
                companyConfigService: configs
        )
    }

    private LegalEntity live(String npub) {
        new LegalEntity(npubOwner: npub, name: 'Live Co', kind: LegalEntity.KIND_LIVE, code: 'primary-' + npub)
                .save(failOnError: true, flush: true)
    }

    private LegalEntity demo(String npub) {
        new LegalEntity(npubOwner: npub, name: 'Demo Co', kind: LegalEntity.KIND_DEMO, code: 'demo-' + npub)
                .save(failOnError: true, flush: true)
    }

    def "migrate: unset level becomes DEFERRED and seeds relay URL"() {
        given:
        live('npub-nostr-mig')

        when:
        String level = service.ensureMigrated('npub-nostr-mig')

        then:
        level == NostrIntegrationService.LEVEL_DEFERRED
        service.publicSettings('npub-nostr-mig').uiLevel == 0
        service.publicSettings('npub-nostr-mig').relayUrl == NostrIntegrationService.DEFAULT_RELAY_URL
    }

    def "Admin can set Level 1 and editable relay URL"() {
        given:
        live('npub-nostr-l1')
        service.ensureMigrated('npub-nostr-l1')

        when:
        Map pub = service.updateSettings('npub-nostr-l1', [
                level   : 'LOGIN_AUDIT',
                relayUrl: 'ws://127.0.0.1:7778'
        ], 'admin', true)

        then:
        pub.level == NostrIntegrationService.LEVEL_LOGIN_AUDIT
        pub.uiLevel == 1
        pub.relayUrl == 'ws://127.0.0.1:7778'
        pub.publishAllowed == true
    }

    def "non-Admin cannot set level"() {
        given:
        live('npub-nostr-noadmin')
        service.ensureMigrated('npub-nostr-noadmin')

        when:
        service.updateSettings('npub-nostr-noadmin', [level: 'LOGIN_AUDIT'], 'ceo', false)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(NostrIntegrationService.ERR_ADMIN_REQUIRED)
    }

    def "rejects nsec in wizard-like body (D8)"() {
        when:
        NostrIntegrationService.rejectNsecInBody([operatorNpub: 'nsec1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(NostrIntegrationService.ERR_NSEC_REJECTED)
    }

    def "rejects nsec masquerading as public npub"() {
        when:
        NostrIntegrationService.validatePublicNpub('nsec1forbidden')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(NostrIntegrationService.ERR_NSEC_REJECTED)
    }

    def "wizard apply seeds Level 2 and operator npub"() {
        given:
        live('npub-nostr-wiz')

        when:
        Map pub = service.applyFromWizard('npub-nostr-wiz', [
                nostrIntegrationLevel: 'LOGIN_AUDIT_NOTIFY',
                nostrOperatorNpub    : 'npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq6w0k8g',
                nostrRelayUrl        : 'ws://127.0.0.1:7777'
        ], 'admin')

        then:
        pub.level == NostrIntegrationService.LEVEL_LOGIN_AUDIT_NOTIFY
        pub.uiLevel == 2
        pub.notifyAllowed == true
        pub.operatorNpub?.startsWith('npub1')
    }

    def "DEMO never allows publish even at Level 1"() {
        given:
        demo('company:demo-nostr')
        service.applyFromWizard('company:demo-nostr', [nostrIntegrationLevel: 'LOGIN_AUDIT'], 'admin')

        expect:
        !service.allowsPublish('company:demo-nostr')
        service.publicSettings('company:demo-nostr').demoNoPublicPublish == true

        when:
        Map r = publish.publishAuditEvent('company:demo-nostr', 'test audit')

        then:
        r.status == 'skipped'
        r.reason.toLowerCase().contains('demo')
    }

    def "Level 1 LIVE publish without bot key stubs honestly"() {
        given:
        live('npub-nostr-stub')
        service.applyFromWizard('npub-nostr-stub', [nostrIntegrationLevel: '1'], 'admin')

        when:
        Map r = publish.publishAuditEvent('npub-nostr-stub', 'invoice.settle INV-1')

        then:
        r.status == 'stubbed'
        r.nostrEventId
        r.residual
        r.residual.toLowerCase().contains('companybotnsec') || r.reason.toLowerCase().contains('bot nsec')
        publish.lastIntent('npub-nostr-stub').status == 'stubbed'
    }

    def "Level 1 LIVE with bot nsec publishes via injected relay client"() {
        given:
        live('npub-nostr-real')
        service.applyFromWizard('npub-nostr-real', [nostrIntegrationLevel: '1'], 'admin')
        // Store plain hex as sealed value when FieldEncryptionService absent
        NostrIntegrationService.botKeyOverride = '67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa'
        NostrRelayWsClient mockClient = Mock(NostrRelayWsClient)
        publish.nostrRelayWsClient = mockClient

        when:
        Map r = publish.publishAuditEvent('npub-nostr-real', 'invoice.settle INV-9')

        then:
        1 * mockClient.publishEvent('ws://127.0.0.1:7777', _ as String, _ as Map) >> [
                ok: true, eventId: 'a' * 64, authenticated: true, detail: ''
        ]
        r.status == 'published'
        r.nostrEventId == 'a' * 64
        publish.lastIntent('npub-nostr-real').status == 'published'
    }

    def "publishNotifyStub remains giftwrap residual"() {
        given:
        live('npub-nostr-notify')
        service.applyFromWizard('npub-nostr-notify', [nostrIntegrationLevel: '2'], 'admin')

        when:
        Map r = publish.publishNotifyStub('npub-nostr-notify', 'ping')

        then:
        r.notifyResidual?.toLowerCase()?.contains('giftwrap')
        r.status == 'stubbed' // no bot key
    }

    def "normalizeLevel accepts UI numbers"() {
        expect:
        NostrIntegrationService.normalizeLevel('0') == NostrIntegrationService.LEVEL_DEFERRED
        NostrIntegrationService.normalizeLevel('1') == NostrIntegrationService.LEVEL_LOGIN_AUDIT
        NostrIntegrationService.normalizeLevel('2') == NostrIntegrationService.LEVEL_LOGIN_AUDIT_NOTIFY
    }

    def "relay URL must be ws or wss"() {
        when:
        NostrIntegrationService.validateRelayUrl('http://127.0.0.1:7777')

        then:
        thrown(IllegalArgumentException)
    }
}
