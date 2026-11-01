// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors

package steakos.search

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification
import steakos.api.DomainRegistryService
import steakos.digital.ITChange
import steakos.finance.PayrollItem
import steakos.human.Payroll
import steakos.human.PartyAccess
import steakos.security.RoleProfileService

/**
 * Global search coverage — ITChange + exclusions for payroll / authz.
 */
class SearchServiceSpec extends Specification implements ServiceUnitTest<SearchService>, DataTest {

    def setupSpec() {
        mockDomains(ITChange, Payroll, PayrollItem, PartyAccess)
    }

    def setup() {
        service.roleProfileService = Stub(RoleProfileService) {
            canSeeDesk(_, _, _, _) >> true
        }
        DomainRegistryService registry = Stub(DomainRegistryService) {
            listRegistry() >> [
                    [package: 'digital', resource: 'ITChange', className: ITChange.name, simpleName: 'ITChange'],
                    [package: 'human', resource: 'Payroll', className: Payroll.name, simpleName: 'Payroll'],
                    [package: 'finance', resource: 'PayrollItem', className: PayrollItem.name, simpleName: 'PayrollItem'],
                    [package: 'human', resource: 'PartyAccess', className: PartyAccess.name, simpleName: 'PartyAccess'],
            ]
            resolve('digital', 'ITChange') >> ITChange
            resolve('human', 'Payroll') >> Payroll
            resolve('finance', 'PayrollItem') >> PayrollItem
            resolve('human', 'PartyAccess') >> PartyAccess
            isTenantScoped(_) >> true
        }
        service.domainRegistryService = registry
    }

    def "ITChange is searchable by name tokens"() {
        given:
        new ITChange(
                name: 'install win upgrade',
                description: 'Windows upgrade window',
                npubOwner: 'npub-demo',
                changeKind: 'CHANGE',
                changeStatus: 'PLANNED',
                riskLevel: 'LOW',
        ).save(flush: true, failOnError: true)

        when:
        Map result = service.search('install win upgrade', null, 5, 'npub-demo', null, ['digital'], 'bronze', true)

        then:
        result.items.any { it.type == 'iTChange' && (it.title as String)?.toLowerCase()?.contains('install') }
        result.items.find { it.type == 'iTChange' }.href ==~ /\/domains\/digital\/ITChange\/\d+/
    }

    def "payroll and party access stay out of the registry fan-out"() {
        expect:
        List types = service.buildRegistry()*.type
        !('payroll' in types)
        !('payrollItem' in types)
        !('partyAccess' in types)
        'iTChange' in types
    }
}
