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

import spock.lang.Specification

class KpiCatalogueSpec extends Specification {

    def "ship set is 16 and defer headline is 3"() {
        expect:
        KpiCatalogue.SHIP_IDS.size() == 16
        KpiCatalogue.DEFER_IDS.size() == 3
        KpiCatalogue.DEFER_IDS.containsAll(['KPI-S04', 'KPI-I03', 'KPI-F05'])
    }

    def "all catalogue ids are unique and resolvable"() {
        when:
        List<Map> all = KpiCatalogue.all()
        Set ids = all*.id as Set

        then:
        all.size() == ids.size()
        all.every { Map row ->
            KpiCatalogue.contains(row.id as String) &&
                    KpiCatalogue.get(row.id as String)?.name == row.name
        }
    }

    def "DEFER rows are marked deferred with blocker"() {
        expect:
        (KpiCatalogue.DEFER_IDS + KpiCatalogue.EXTRA_DEFER_IDS).every { String id ->
            Map row = KpiCatalogue.get(id)
            row.deferred && row.maturity == KpiCatalogue.MATURITY_DEFER && row.status == 'deferred'
        }
    }

    def "ship rows are ready after PR-13 feeds"() {
        expect:
        KpiCatalogue.SHIP_IDS.every { String id ->
            Map row = KpiCatalogue.get(id)
            !row.deferred && row.ship && row.status == KpiCatalogue.STATUS_READY
        }
    }

    def "silver-new stretch stays planned"() {
        expect:
        KpiCatalogue.get('KPI-I02').status == KpiCatalogue.STATUS_PLANNED
        KpiCatalogue.get('KPI-F06').status == KpiCatalogue.STATUS_PLANNED
        !KpiCatalogue.isShip('KPI-I02')
        !KpiCatalogue.isShip('KPI-F06')
    }

    def "money and HR sensitivity cover F* and H*"() {
        expect:
        ['KPI-F01', 'KPI-F02', 'KPI-F03', 'KPI-F04', 'KPI-F05', 'KPI-F06'].every {
            KpiCatalogue.get(it).sensitivity == KpiCatalogue.SENSITIVITY_MONEY
        }
        ['KPI-H01', 'KPI-H02'].every {
            KpiCatalogue.get(it).sensitivity == KpiCatalogue.SENSITIVITY_HR
        }
    }

    def "hero ids are subset of catalogue"() {
        expect:
        KpiCatalogue.HERO_IDS.every { KpiCatalogue.contains(it) }
        KpiCatalogue.HERO_IDS.containsAll(['KPI-F02', 'KPI-F01', 'KPI-S01', 'KPI-D02', 'KPI-W01'])
    }

    def "normalizeId is case-insensitive"() {
        expect:
        KpiCatalogue.get('kpi-s01')?.id == 'KPI-S01'
        KpiCatalogue.contains('kpi-w01')
    }
}
