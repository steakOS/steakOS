// SPDX-License-Identifier: Apache-2.0
package steakos.payments

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.configuration.CompanyConfigService
import steakos.configuration.CompanySetting
import steakos.configuration.LegalEntity

/**
 * A2/D3 — DEMO companies force MOCK; LIVE can enable BTCPay when configured.
 */
class PaymentSettingsServiceSpec extends Specification implements DataTest {

    PaymentSettingsService service
    CompanyConfigService configs

    Class[] getDomainClassesToMock() {
        [LegalEntity, CompanySetting] as Class[]
    }

    def setup() {
        configs = new CompanyConfigService()
        service = new PaymentSettingsService(companyConfigService: configs)
    }

    def "DEMO company always resolves MOCK and surfaces demoForcedMock"() {
        given:
        String npub = 'company:orange-steak-house'
        new LegalEntity(npubOwner: npub, name: 'Orange Steak House', kind: LegalEntity.KIND_DEMO, code: 'orange-steak-house')
                .save(failOnError: true, flush: true)
        configs.put(npub, PaymentSettingsService.KEY_PROVIDER, PaymentSettingsService.PROVIDER_BTCPAY, 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_LIVE, 'true', 'payments', 'boolean')
        configs.put(npub, PaymentSettingsService.KEY_BASE_URL, 'https://pay.example.com', 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_STORE_ID, 'store1', 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_API_KEY, 'secret', 'payments', 'string')

        when:
        Map pub = service.publicSettings(npub)

        then:
        service.isDemoCompany(npub)
        service.resolvedProviderCode(npub) == PaymentSettingsService.PROVIDER_MOCK
        !service.isBtcpayLiveReady(npub)
        pub.demoForcedMock == true
        pub.activeProvider == PaymentSettingsService.PROVIDER_MOCK
        pub.note.contains('Demo company')
    }

    def "DEMO company cannot enable BTCPAY live"() {
        given:
        String npub = 'company:heard-cook-architects'
        new LegalEntity(npubOwner: npub, name: 'Heard & Cook', kind: LegalEntity.KIND_DEMO, code: 'heard-cook')
                .save(failOnError: true, flush: true)

        when:
        service.updateSettings(npub, [provider: 'BTCPAY', liveEnabled: true])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(PaymentSettingsService.ERR_DEMO_MOCK_ONLY)
    }

    def "LIVE company can resolve BTCPAY when live and configured"() {
        given:
        String npub = 'npub-live-test'
        new LegalEntity(npubOwner: npub, name: 'Live Co', kind: LegalEntity.KIND_LIVE, code: 'primary-test')
                .save(failOnError: true, flush: true)
        // OI-BTC01: Level 3 required for live path
        configs.put(npub, BtcIntegrationLevelService.KEY_LEVEL, BtcIntegrationLevelService.LEVEL_BTCPAY, 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_PROVIDER, PaymentSettingsService.PROVIDER_BTCPAY, 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_LIVE, 'true', 'payments', 'boolean')
        configs.put(npub, PaymentSettingsService.KEY_BASE_URL, 'https://pay.example.com', 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_STORE_ID, 'store1', 'payments', 'string')
        // apiKey stored plain for unit path (decryptOptional falls back)
        configs.put(npub, PaymentSettingsService.KEY_API_KEY, 'api-key-plain', 'payments', 'string')
        service.fieldEncryptionService = Stub(steakos.FieldEncryptionService) {
            decrypt(_) >> { args -> args[0] }
            encrypt(_) >> { args -> args[0] }
        }

        expect:
        !service.isDemoCompany(npub)
        service.isBtcpayLiveReady(npub)
        service.resolvedProviderCode(npub) == PaymentSettingsService.PROVIDER_BTCPAY
        service.publicSettings(npub).demoForcedMock == false
    }

    def "LIVE company cannot enable BTCPAY when level is DEFERRED"() {
        given:
        String npub = 'npub-live-l1'
        new LegalEntity(npubOwner: npub, name: 'Live L1', kind: LegalEntity.KIND_LIVE, code: 'primary-l1')
                .save(failOnError: true, flush: true)
        configs.put(npub, BtcIntegrationLevelService.KEY_LEVEL, BtcIntegrationLevelService.LEVEL_DEFERRED, 'payments', 'string')
        def levels = new BtcIntegrationLevelService(companyConfigService: configs, paymentSettingsService: service)
        service.btcIntegrationLevelService = levels

        when:
        service.updateSettings(npub, [provider: 'BTCPAY', liveEnabled: true])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(PaymentSettingsService.ERR_LEVEL_NOT_BTCPAY)
    }
}
