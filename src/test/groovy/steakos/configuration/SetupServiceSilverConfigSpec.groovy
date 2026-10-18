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

package steakos.configuration

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.finance.Account

/**
 * PR-3 pluginDepth + messaging flags; PR-17 Silver open for metal selection.
 */
class SetupServiceSilverConfigSpec extends Specification implements DataTest {

    SetupService setupService
    CompanyConfigService configService
    Map<String, String> stored = [:]

    Class[] getDomainClassesToMock() {
        [CompanySetting, SetupState, LegalEntity, Site, BankAccount, Account] as Class[]
    }

    def setup() {
        stored.clear()
        configService = Stub(CompanyConfigService) {
            getString(_, _, _) >> { String npub, String key, String defVal ->
                stored.get("${npub}|${key}".toString()) ?: defVal
            }
            getBoolean(_, _, _) >> { String npub, String key, Boolean defVal ->
                String v = stored.get("${npub}|${key}".toString())
                if (v == null) return defVal
                return v.equalsIgnoreCase('true') || v == '1'
            }
            getJson(_, _, _) >> { String npub, String key, Object defVal ->
                String v = stored.get("${npub}|${key}".toString())
                if (!v) return defVal
                return new groovy.json.JsonSlurper().parseText(v)
            }
            put(_, _, _, _, _) >> { String npub, String key, String value, String cat, String dt ->
                stored.put("${npub}|${key}".toString(), value)
                Stub(CompanySetting)
            }
            putJson(_, _, _, _) >> { String npub, String key, Object value, String cat ->
                stored.put("${npub}|${key}".toString(), groovy.json.JsonOutput.toJson(value))
                Stub(CompanySetting)
            }
            enabledFamilies(_) >> { String npub ->
                Object raw = stored.get("${npub}|template.enabledFamilies".toString())
                if (raw) {
                    return new groovy.json.JsonSlurper().parseText(raw as String) as List
                }
                TemplateTiers.defaultFamilies(TemplateTiers.BRONZE)
            }
        }
        setupService = new SetupService(companyConfigService: configService)
    }

    def "WIZARD_VERSION is 2"() {
        expect:
        SetupService.WIZARD_VERSION == 2
    }

    def "statusMap exposes pluginDepth Basics and messaging off for Bronze tenant"() {
        given:
        String npub = 'user:admin'
        stored.put("${npub}|template.tier".toString(), TemplateTiers.BRONZE)

        when:
        Map status = setupService.statusMap(npub)

        then:
        status.wizardVersion == 2 || status.wizardVersion == SetupService.WIZARD_VERSION
        status.pluginDepth.sales == TemplateTiers.DEPTH_BASICS
        status.pluginDepth.finance == TemplateTiers.DEPTH_BASICS
        status.messaging.emailEnabled == false
        status.messaging.telegramEnabled == false
        status.tiers.find { it.code == 'SILVER' }.openForMetalSelection == true
        status.tiers.find { it.code == 'GOLD' }.openForMetalSelection == false
    }

    def "readPluginDepth returns stored Advanced map"() {
        given:
        String npub = 'user:admin'
        stored.put("${npub}|${SetupService.KEY_PLUGIN_DEPTH}".toString(),
                '{"sales":"ADVANCED","finance":"ADVANCED"}')

        when:
        def d = setupService.readPluginDepth(npub, TemplateTiers.SILVER)

        then:
        d.sales == 'ADVANCED'
        d.finance == 'ADVANCED'
    }

    def "setTemplateTier SILVER succeeds when open (PR-17)"() {
        given:
        String npub = 'user:admin'
        stored.put("${npub}|privacy.hrAcknowledged".toString(), 'true')

        when:
        Map status = setupService.setTemplateTier(npub, 'SILVER')

        then:
        stored.get("${npub}|template.tier".toString()) == 'SILVER'
        status.templateTier == 'SILVER'
        status.pluginDepth.sales == 'ADVANCED'
        status.pluginDepth.finance == 'ADVANCED'
        (status.enabledFamilies as List).containsAll(['grrc', 'stakeholder'])
        status.messaging.emailEnabled == false
        status.messaging.telegramEnabled == false
    }

    def "setTemplateTier GOLD while closed throws METAL_NOT_OPEN"() {
        when:
        setupService.setTemplateTier('user:admin', 'GOLD')

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains('not open for selection')
    }

    def "setTemplateTier BRONZE writes Basics depth and messaging defaults when missing"() {
        given:
        String npub = 'user:admin'
        stored.put("${npub}|privacy.hrAcknowledged".toString(), 'true')

        when:
        Map status = setupService.setTemplateTier(npub, 'BRONZE')

        then:
        stored.get("${npub}|template.tier".toString()) == 'BRONZE'
        stored.get("${npub}|${SetupService.KEY_PLUGIN_DEPTH}".toString()).contains('BASICS')
        stored.get("${npub}|${SetupService.KEY_MESSAGING_EMAIL}".toString()) == 'false'
        stored.get("${npub}|${SetupService.KEY_MESSAGING_TELEGRAM}".toString()) == 'false'
        status.pluginDepth.sales == 'BASICS'
        status.messaging.emailEnabled == false
    }

    def "setTemplateTier accepts messaging override on Bronze"() {
        given:
        String npub = 'user:admin'
        stored.put("${npub}|privacy.hrAcknowledged".toString(), 'true')

        when:
        Map status = setupService.setTemplateTier(npub, 'BRONZE', null, [
                messaging: [emailEnabled: true, telegramEnabled: false]
        ])

        then:
        status.messaging.emailEnabled == true
        status.messaging.telegramEnabled == false
        stored.get("${npub}|${SetupService.KEY_MESSAGING_EMAIL}".toString()) == 'true'
    }

    def "setting key constants match plan"() {
        expect:
        SetupService.KEY_PLUGIN_DEPTH == 'template.pluginDepth'
        SetupService.KEY_MESSAGING_EMAIL == 'messaging.emailEnabled'
        SetupService.KEY_MESSAGING_TELEGRAM == 'messaging.telegramEnabled'
    }

    def "setPackOptions updates messaging without metal-open and defaults off"() {
        given:
        String npub = 'user:admin'
        stored.put("${npub}|template.tier".toString(), TemplateTiers.BRONZE)

        when:
        Map status = setupService.setPackOptions(npub, [
                messaging: [emailEnabled: true, telegramEnabled: false]
        ])

        then:
        status.messaging.emailEnabled == true
        status.messaging.telegramEnabled == false
        stored.get("${npub}|${SetupService.KEY_MESSAGING_EMAIL}".toString()) == 'true'
        stored.get("${npub}|${SetupService.KEY_MESSAGING_TELEGRAM}".toString()) == 'false'
        stored.get("${npub}|template.tier".toString()) == 'BRONZE'
    }

    def "setPackOptions on Silver forces Advanced depth even if Basics sent"() {
        given:
        String npub = 'user:admin'
        stored.put("${npub}|template.tier".toString(), TemplateTiers.SILVER)

        when:
        Map status = setupService.setPackOptions(npub, [
                messaging  : [emailEnabled: false, telegramEnabled: true],
                pluginDepth: [sales: 'BASICS', finance: 'BASICS']
        ])

        then:
        status.messaging.emailEnabled == false
        status.messaging.telegramEnabled == true
        status.pluginDepth.sales == 'ADVANCED'
        status.pluginDepth.finance == 'ADVANCED'
    }
}
