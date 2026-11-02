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
    KpiFeedService kpiFeedService = Mock()

    def setup() {
        service = new KpiService(
                companyConfigService : companyConfigService,
                springSecurityService: springSecurityService,
                roleProfileService   : null,
                kpiFeedService       : kpiFeedService
        )
    }

    private static UsernamePasswordAuthenticationToken auth(String... roles) {
        List authorities = roles.collect { new SimpleGrantedAuthority(it) }
        new UsernamePasswordAuthenticationToken('tester', 'x', authorities)
    }

    private static Map readyValue(String unit = 'count') {
        [amount: null, count: 0, ratio: null, unit: unit, status: 'ready', message: 'ok']
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
        0 * kpiFeedService._
    }

    def "Silver dashboard-hero returns rich tiles from feeds"() {
        given:
        companyConfigService.getString('npub1', 'template.tier', _) >> TemplateTiers.SILVER
        springSecurityService.authentication >> auth('ROLE_ADMIN', 'ROLE_CEO')
        kpiFeedService.compute(_, 'npub1', null, null) >> { Map defn, String npub, Date f, Date t ->
            readyValue(defn.unit as String)
        }

        when:
        Map hero = service.dashboardHero('npub1')

        then:
        hero.mode == 'rich'
        hero.silverRich == true
        hero.tiles.size() == KpiCatalogue.HERO_IDS.size()
        hero.tiles.every { it.value?.status == 'ready' }
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
        0 * kpiFeedService._
    }

    def "AI-only cannot see money KPIs"() {
        given:
        companyConfigService.getString(_, 'template.tier', _) >> TemplateTiers.SILVER
        springSecurityService.authentication >> auth('ROLE_AI', 'ROLE_USER')

        when:
        service.show('npub1', 'KPI-F02')

        then:
        thrown(SecurityException)
    }

    def "AI-only cannot call ar-aging"() {
        given:
        companyConfigService.getString(_, 'template.tier', _) >> TemplateTiers.SILVER
        springSecurityService.authentication >> auth('ROLE_AI', 'ROLE_USER')

        when:
        service.arAging('npub1')

        then:
        thrown(SecurityException)
    }

    def "show delegates to feed for ship KPI"() {
        given:
        companyConfigService.getString(_, 'template.tier', _) >> TemplateTiers.SILVER
        springSecurityService.authentication >> auth('ROLE_ADMIN')
        kpiFeedService.compute({ it.id == 'KPI-W01' }, 'npub1', null, null) >>
                [amount: null, count: 3, ratio: null, unit: 'count', status: 'ready', message: '3 overdue']

        when:
        Map body = service.show('npub1', 'KPI-W01')

        then:
        body.id == 'KPI-W01'
        body.value.count == 3
        body.value.status == 'ready'
    }

    def "catalogue lists ship KPIs only and hides deferred or planned"() {
        given:
        companyConfigService.getString(_, 'template.tier', _) >> TemplateTiers.BRONZE
        springSecurityService.authentication >> auth('ROLE_ADMIN')

        when:
        Map cat = service.catalogue('npub1')

        then:
        cat.meta.shipCount == 16
        cat.meta.deferCount == 3
        cat.meta.visibleCount == cat.items.size()
        cat.items.size() == 16
        cat.items.every { it.ship && !it.deferred }
        cat.items.find { it.id == 'KPI-S01' }.status == 'ready'
        cat.items.find { it.id == 'KPI-S04' } == null
        cat.items.find { it.id == 'KPI-I02' } == null
        cat.items.find { it.id == 'KPI-F06' } == null
    }

    def "show rejects deferred and planned stretch KPIs"() {
        given:
        companyConfigService.getString(_, 'template.tier', _) >> TemplateTiers.SILVER
        springSecurityService.authentication >> auth('ROLE_ADMIN')

        when:
        service.show('npub1', 'KPI-S04')

        then:
        thrown(IllegalArgumentException)

        when:
        service.show('npub1', 'KPI-F06')

        then:
        thrown(IllegalArgumentException)
    }
}
