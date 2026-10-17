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

package steakos.kpi

import grails.plugin.springsecurity.SpringSecurityService
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification
import steakos.configuration.CompanyConfigService
import steakos.configuration.TemplateTiers

class KpiServiceSpec extends Specification {

    KpiService service
    CompanyConfigService companyConfigService = Mock()
    SpringSecurityService springSecurityService = Mock()

    def setup() {
        service = new KpiService(
                companyConfigService : companyConfigService,
                springSecurityService: springSecurityService,
                roleProfileService   : null
        )
    }

    private static UsernamePasswordAuthenticationToken auth(String... roles) {
        List authorities = roles.collect { new SimpleGrantedAuthority(it) }
        new UsernamePasswordAuthenticationToken('tester', 'x', authorities)
    }

    def "Bronze dashboard-hero is counts-only with empty rich tiles"() {
        given:
        companyConfigService.getString('npub1', 'template.tier', _) >> TemplateTiers.BRONZE
        springSecurityService.authentication >> auth('ROLE_ADMIN', 'ROLE_CEO')

        when:
        Map hero = service.dashboardHero('npub1')

        then:
        hero.mode == 'counts-only'
        hero.silverRich == false
        hero.tiles instanceof List && hero.tiles.isEmpty()
        hero.countsPulse == '/api/dashboard/ceo'
    }

    def "Silver dashboard-hero returns rich tile stubs"() {
        given:
        companyConfigService.getString('npub1', 'template.tier', _) >> TemplateTiers.SILVER
        springSecurityService.authentication >> auth('ROLE_ADMIN', 'ROLE_CEO')

        when:
        Map hero = service.dashboardHero('npub1')

        then:
        hero.mode == 'rich'
        hero.silverRich == true
        hero.tiles.size() == KpiCatalogue.HERO_IDS.size()
        hero.tiles.every { it.value?.status in ['planned', 'deferred'] }
    }

    def "show unknown id throws"() {
        given:
        companyConfigService.getString(_, 'template.tier', _) >> TemplateTiers.BRONZE
        springSecurityService.authentication >> auth('ROLE_USER')

        when:
        service.show('npub1', 'KPI-ZZZ')

        then:
        thrown(IllegalArgumentException)
    }

    def "AI-only cannot see HR KPIs"() {
        given:
        companyConfigService.getString(_, 'template.tier', _) >> TemplateTiers.SILVER
        springSecurityService.authentication >> auth('ROLE_AI', 'ROLE_USER')

        when:
        service.show('npub1', 'KPI-H01')

        then:
        thrown(SecurityException)
    }

    def "catalogue meta reports ship and defer counts"() {
        given:
        companyConfigService.getString(_, 'template.tier', _) >> TemplateTiers.BRONZE
        springSecurityService.authentication >> auth('ROLE_ADMIN')

        when:
        Map cat = service.catalogue('npub1')

        then:
        cat.meta.shipCount == 16
        cat.meta.deferCount == 3
        cat.items.size() >= 16
    }
}
