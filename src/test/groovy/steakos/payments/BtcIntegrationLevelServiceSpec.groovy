// SPDX-License-Identifier: Apache-2.0
package steakos.payments

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.configuration.CompanyConfigService
import steakos.configuration.CompanySetting
import steakos.configuration.LegalEntity

/**
 * OI-BTC01 — migrate + Admin/CEO set rules for Bitcoin integration level.
 */
class BtcIntegrationLevelServiceSpec extends Specification implements DataTest {

    BtcIntegrationLevelService service
    CompanyConfigService configs
    PaymentSettingsService paymentSettings

    Class[] getDomainClassesToMock() {
        [LegalEntity, CompanySetting] as Class[]
    }

    def setup() {
        configs = new CompanyConfigService()
        paymentSettings = new PaymentSettingsService(companyConfigService: configs)
        service = new BtcIntegrationLevelService(
                companyConfigService: configs,
                paymentSettingsService: paymentSettings
        )
        paymentSettings.btcIntegrationLevelService = service
        paymentSettings.fieldEncryptionService = Stub(steakos.FieldEncryptionService) {
            decrypt(_) >> { args -> args[0] }
            encrypt(_) >> { args -> args[0] }
        }
    }

    private LegalEntity live(String npub) {
        new LegalEntity(npubOwner: npub, name: 'Live Co', kind: LegalEntity.KIND_LIVE, code: 'primary-' + npub)
                .save(failOnError: true, flush: true)
    }

    private LegalEntity demo(String npub) {
        new LegalEntity(npubOwner: npub, name: 'Demo Co', kind: LegalEntity.KIND_DEMO, code: 'demo-' + npub)
                .save(failOnError: true, flush: true)
    }

    def "migrate: unset level becomes DEFERRED when BTCPay not live"() {
        given:
        live('npub-mig-defer')

        when:
        String level = service.ensureMigrated('npub-mig-defer')

        then:
        level == BtcIntegrationLevelService.LEVEL_DEFERRED
        service.publicLevel('npub-mig-defer').uiLevel == 1
        service.publicLevel('npub-mig-defer').trainingNudge == BtcIntegrationLevelService.NUDGE_BASIC
    }

    def "migrate: live BTCPay config becomes BTCPAY"() {
        given:
        String npub = 'npub-mig-btcpay'
        live(npub)
        configs.put(npub, PaymentSettingsService.KEY_PROVIDER, PaymentSettingsService.PROVIDER_BTCPAY, 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_LIVE, 'true', 'payments', 'boolean')
        configs.put(npub, PaymentSettingsService.KEY_BASE_URL, 'https://pay.example.com', 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_STORE_ID, 'store1', 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_API_KEY, 'api-key-plain', 'payments', 'string')

        when:
        String level = service.ensureMigrated(npub)

        then:
        level == BtcIntegrationLevelService.LEVEL_BTCPAY
        service.publicLevel(npub).uiLevel == 3
        service.publicLevel(npub).trainingNudge == BtcIntegrationLevelService.NUDGE_NONE
    }

    def "migrate is idempotent"() {
        given:
        live('npub-mig-once')
        service.ensureMigrated('npub-mig-once')
        configs.put('npub-mig-once', BtcIntegrationLevelService.KEY_LEVEL,
                BtcIntegrationLevelService.LEVEL_SINGLE_ADDRESS, 'payments', 'string')

        expect:
        service.ensureMigrated('npub-mig-once') == BtcIntegrationLevelService.LEVEL_SINGLE_ADDRESS
    }

    def "Admin upgrade to Level 2 requires CEO training ack and address"() {
        given:
        live('npub-up2')
        service.ensureMigrated('npub-up2')

        when:
        service.setLevel('npub-up2', [
                level             : 'SINGLE_ADDRESS',
                btcReceiveAddress : 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh'
        ], 'admin', true, false)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(BtcIntegrationLevelService.ERR_CEO_ACK_REQUIRED)

        when:
        Map pub = service.setLevel('npub-up2', [
                level             : 'SINGLE_ADDRESS',
                btcReceiveAddress : 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh',
                ceoTrainingAck    : true
        ], 'admin', true, false)

        then:
        pub.level == BtcIntegrationLevelService.LEVEL_SINGLE_ADDRESS
        pub.uiLevel == 2
        pub.receiveAddress?.startsWith('bc1')
        pub.trainingNudge == BtcIntegrationLevelService.NUDGE_ADVANCED
        pub.level2Warning == true
    }

    def "non-Admin cannot set level"() {
        given:
        live('npub-noadmin')
        service.ensureMigrated('npub-noadmin')

        when:
        service.setLevel('npub-noadmin', [level: 'DEFERRED'], 'ceo', false, true)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(BtcIntegrationLevelService.ERR_ADMIN_REQUIRED)
    }

    def "downgrade from Level 3 requires confirm and disables live"() {
        given:
        String npub = 'npub-down3'
        live(npub)
        configs.put(npub, PaymentSettingsService.KEY_PROVIDER, PaymentSettingsService.PROVIDER_BTCPAY, 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_LIVE, 'true', 'payments', 'boolean')
        configs.put(npub, PaymentSettingsService.KEY_BASE_URL, 'https://pay.example.com', 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_STORE_ID, 'store1', 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_API_KEY, 'api-key-plain', 'payments', 'string')
        service.ensureMigrated(npub)
        assert service.currentLevel(npub) == BtcIntegrationLevelService.LEVEL_BTCPAY

        when:
        service.setLevel(npub, [level: 'DEFERRED'], 'admin', true, false)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(BtcIntegrationLevelService.ERR_DOWNGRADE_CONFIRM)

        when:
        Map pub = service.setLevel(npub, [level: 'DEFERRED', confirmDowngrade: true], 'admin', true, false)

        then:
        pub.level == BtcIntegrationLevelService.LEVEL_DEFERRED
        configs.getString(npub, PaymentSettingsService.KEY_LIVE, 'true') == 'false'
        paymentSettings.resolvedProviderCode(npub) == PaymentSettingsService.PROVIDER_MOCK
    }

    def "DEMO cannot set Level 2 or 3"() {
        given:
        demo('company:demo-btc')

        when:
        service.setLevel('company:demo-btc', [
                level             : 'SINGLE_ADDRESS',
                btcReceiveAddress : 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh',
                ceoTrainingAck    : true
        ], 'admin', true, false)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(BtcIntegrationLevelService.ERR_DEMO_LIVE)
    }

    def "rejects seed-like receive address"() {
        when:
        BtcIntegrationLevelService.validateReceiveAddress('abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(BtcIntegrationLevelService.ERR_ADDRESS_INVALID)
    }

    def "Level 1 forces MOCK provider even if leftover BTCPAY flags"() {
        given:
        String npub = 'npub-l1-mock'
        live(npub)
        configs.put(npub, BtcIntegrationLevelService.KEY_LEVEL, BtcIntegrationLevelService.LEVEL_DEFERRED, 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_PROVIDER, PaymentSettingsService.PROVIDER_BTCPAY, 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_LIVE, 'true', 'payments', 'boolean')
        configs.put(npub, PaymentSettingsService.KEY_BASE_URL, 'https://pay.example.com', 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_STORE_ID, 'store1', 'payments', 'string')
        configs.put(npub, PaymentSettingsService.KEY_API_KEY, 'api-key-plain', 'payments', 'string')

        expect:
        paymentSettings.resolvedProviderCode(npub) == PaymentSettingsService.PROVIDER_MOCK
        !paymentSettings.isBtcpayLiveReady(npub)
    }

    def "wizard apply seeds Level 2 address"() {
        given:
        live('npub-wiz2')

        when:
        Map pub = service.applyFromWizard('npub-wiz2', [
                btcIntegrationLevel: 'SINGLE_ADDRESS',
                btcReceiveAddress  : 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh',
                btcReceiveAddressLabel: 'desk'
        ], 'admin')

        then:
        pub.level == BtcIntegrationLevelService.LEVEL_SINGLE_ADDRESS
        pub.receiveAddressLabel == 'desk'
    }
}
