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

import spock.lang.Specification

/**
 * PR-3 pluginDepth helpers + PR-17 Silver open for metal selection.
 */
class TemplateTiersPluginDepthSpec extends Specification {

    def "OPEN_FOR_METAL_SELECTION includes Bronze and Silver (PR-17)"() {
        expect:
        TemplateTiers.OPEN_FOR_METAL_SELECTION == [TemplateTiers.BRONZE, TemplateTiers.SILVER]
        TemplateTiers.isOpenForMetalSelection(TemplateTiers.SILVER)
        TemplateTiers.isOpenForMetalSelection(TemplateTiers.BRONZE)
        !TemplateTiers.isOpenForMetalSelection(TemplateTiers.GOLD)
        TemplateTiers.normalizeMetalSelection('SILVER') == TemplateTiers.SILVER
        TemplateTiers.normalizeMetalSelection('GOLD') == TemplateTiers.BRONZE
    }

    def "Bronze defaultPluginDepth is Basics for sales and finance"() {
        when:
        def d = TemplateTiers.defaultPluginDepth(TemplateTiers.BRONZE)

        then:
        d.sales == TemplateTiers.DEPTH_BASICS
        d.finance == TemplateTiers.DEPTH_BASICS
        TemplateTiers.advancedFamilies(TemplateTiers.BRONZE).isEmpty()
    }

    def "Silver defaultPluginDepth is Advanced (G5 pack-mandatory)"() {
        when:
        def d = TemplateTiers.defaultPluginDepth(TemplateTiers.SILVER)

        then:
        d.sales == TemplateTiers.DEPTH_ADVANCED
        d.finance == TemplateTiers.DEPTH_ADVANCED
        TemplateTiers.advancedFamilies(TemplateTiers.SILVER) == ['sales', 'finance']
    }

    def "Gold defaultPluginDepth is Advanced for sales and finance"() {
        expect:
        TemplateTiers.defaultPluginDepth(TemplateTiers.GOLD).sales == TemplateTiers.DEPTH_ADVANCED
        TemplateTiers.defaultPluginDepth(TemplateTiers.GOLD).finance == TemplateTiers.DEPTH_ADVANCED
    }

    def "sanitizePluginDepth drops unknown families and normalizes depth codes"() {
        when:
        def d = TemplateTiers.sanitizePluginDepth(TemplateTiers.BRONZE, [
                sales: 'advanced',
                finance: 'nope',
                grrc: 'ADVANCED'
        ])

        then:
        d.sales == TemplateTiers.DEPTH_ADVANCED
        d.finance == TemplateTiers.DEPTH_BASICS
        !d.containsKey('grrc')
    }

    def "sanitizePluginDepth forces Advanced on Silver when draft asks Basics"() {
        when:
        def d = TemplateTiers.sanitizePluginDepth(TemplateTiers.SILVER, [
                sales: 'BASICS',
                finance: 'BASICS'
        ])

        then:
        d.sales == TemplateTiers.DEPTH_ADVANCED
        d.finance == TemplateTiers.DEPTH_ADVANCED
    }

    def "sanitizePluginDepth allows Pro on Silver"() {
        when:
        def d = TemplateTiers.sanitizePluginDepth(TemplateTiers.SILVER, [sales: 'PRO', finance: 'ADVANCED'])

        then:
        d.sales == TemplateTiers.DEPTH_PRO
        d.finance == TemplateTiers.DEPTH_ADVANCED
    }

    def "catalog marks Silver open and Gold closed"() {
        when:
        def silver = TemplateTiers.catalog().find { it.code == TemplateTiers.SILVER }
        def gold = TemplateTiers.catalog().find { it.code == TemplateTiers.GOLD }

        then:
        silver.openForMetalSelection == true
        !(silver.blurb as String).toLowerCase().contains('coming soon')
        (silver.blurb as String).toLowerCase().contains('advanced')
        gold.openForMetalSelection == false
        (gold.blurb as String).toLowerCase().contains('coming soon')
    }
}
