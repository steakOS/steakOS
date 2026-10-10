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

package steakos.human

import grails.testing.gorm.DataTest
import spock.lang.Specification
import steakos.User
import steakos.finance.PayrollItem
import steakos.finance.FinancePayrollService

/**
 * HR kernel: file+user, hire, time, payroll hand-off, party without file.
 */
class HrServicesSpec extends Specification implements DataTest {

    HrUserSupportService hrUserSupportService
    HrFileService fileService
    HrRecruitService recruitService
    HrTimeService timeService
    HrPayrollService payrollService
    HrPartyService partyService
    FinancePayrollService financePayrollService

    Class[] getDomainClassesToMock() {
        [
                HRFile, Candidate, TimeTrack, Payroll, HeadcountPlan, Goal, Policy, PartyAccess,
                User, PayrollItem
        ] as Class[]
    }

    def setup() {
        hrUserSupportService = new HrUserSupportService(springSecurityService: null)
        fileService = new HrFileService(hrUserSupportService: hrUserSupportService)
        recruitService = new HrRecruitService(hrFileService: fileService)
        timeService = new HrTimeService(hrFileService: fileService)
        financePayrollService = new FinancePayrollService()
        payrollService = new HrPayrollService(
                hrFileService: fileService,
                financePayrollService: financePayrollService
        )
        partyService = new HrPartyService(hrUserSupportService: hrUserSupportService)
    }

    def "create HRFile creates deactivated user"() {
        when:
        HRFile f = fileService.create('npub-hr', [name: 'Ada Lovelace', username: 'ada.hr'])

        then:
        f.userId
        f.username == 'ada.hr'
        User u = User.get(f.userId)
        u != null
        u.enabled == false
    }

    def "hire creates HRFile and time requires hrFile"() {
        given:
        Candidate c = recruitService.createCandidate('npub-h', [name: 'Bob Hire', email: 'b@x.com'])

        when:
        Map hired = recruitService.hire(c.id, 'npub-h')
        TimeTrack t = timeService.createEntry('npub-h', [
                hrFileId: hired.hrFile.id, hours: 8G, timeKind: 'WORK'
        ])

        then:
        hired.candidate.stage == 'HIRED'
        hired.hrFile.id
        t.hrFile.id == hired.hrFile.id

        when:
        timeService.createEntry('npub-h', [hours: 1G])

        then:
        thrown(IllegalArgumentException)
    }

    def "payroll submit creates finance lines"() {
        given:
        HRFile f = fileService.create('npub-p', [name: 'Pay Person', username: 'pay.p'])
        Payroll p = payrollService.openPeriod('npub-p', [period: '2026-07'])

        when:
        payrollService.setLines(p.id, 'npub-p', [
                [hrFileId: f.id, amountEur: 2500G, amountSats: 100L]
        ])
        Map sub = payrollService.submitToFinance(p.id, 'npub-p')

        then:
        sub.payroll.payrollStatus == 'SUBMITTED'
        sub.payroll.totalEur == 2500G
        sub.lines.size() == 1
        sub.lines[0].amountEur == 2500G
    }

    def "party access without HRFile"() {
        when:
        PartyAccess pa = partyService.provision('npub-x', [
                name: 'Partner Co', partyKind: 'BUSINESS_PARTNER', username: 'partner.co'
        ])

        then:
        pa.partyKind == 'BUSINESS_PARTNER'
        pa.userId
        User.get(pa.userId).enabled == false
        !HRFile.findByUserId(pa.userId)
    }
}
