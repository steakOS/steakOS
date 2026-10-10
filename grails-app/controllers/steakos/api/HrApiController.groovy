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

package steakos.api

import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import groovy.util.logging.Slf4j
import steakos.User
import steakos.api.ListPaging
import steakos.human.*

/**
 * HR API (HR-S0–HR-S3): /api/hr/* (HR6). Services in steakos.human (HR8).
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class HrApiController {

    HrFileService hrFileService
    HrRecruitService hrRecruitService
    HrTimeService hrTimeService
    HrPayrollService hrPayrollService
    HrPlanService hrPlanService
    HrGoalService hrGoalService
    HrPolicyService hrPolicyService
    HrPartyService hrPartyService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    // ----- Files -----
    def fileList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = hrFileService.listPage(npub, params.status as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { hrFileService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def fileCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: hrFileService.toMap(hrFileService.create(npub, body))]
        }
    }

    def fileShow() {
        wrap { String npub -> hrFileService.toMap(hrFileService.get(params.long('id'), npub)) }
    }

    def fileUpdate() {
        wrapBody { String npub, Map body ->
            hrFileService.toMap(hrFileService.update(params.long('id'), npub, body))
        }
    }

    def fileExit() {
        wrapBody { String npub, Map body ->
            hrFileService.toMap(hrFileService.separate(params.long('id'), npub, body))
        }
    }

    // ----- Candidates -----
    def candidateList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = hrRecruitService.listPage(npub, params.stage as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { hrRecruitService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def candidateCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: hrRecruitService.toMap(hrRecruitService.createCandidate(npub, body))]
        }
    }

    def candidateShow() {
        wrap { String npub -> hrRecruitService.toMap(hrRecruitService.get(params.long('id'), npub)) }
    }

    def candidateAdvance() {
        wrapBody { String npub, Map body ->
            String stage = body.stage?.toString() ?: params.stage
            hrRecruitService.toMap(hrRecruitService.advanceStage(params.long('id'), npub, stage))
        }
    }

    def candidateHire() {
        wrapBody { String npub, Map body ->
            hrRecruitService.hire(params.long('id'), npub, body)
        }
    }

    def candidateReject() {
        wrapBody { String npub, Map body ->
            hrRecruitService.toMap(hrRecruitService.reject(params.long('id'), npub))
        }
    }

    // ----- Time -----
    def timeList() {
        wrap { String npub ->
            Long fid = params.hrFileId ? params.long('hrFileId') : null
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = hrTimeService.listPage(npub, fid, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { hrTimeService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def timeCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: hrTimeService.toMap(hrTimeService.createEntry(npub, body))]
        }
    }

    // ----- Payroll -----
    def payrollList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = hrPayrollService.listPage(npub, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { hrPayrollService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def payrollCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: hrPayrollService.toMap(hrPayrollService.openPeriod(npub, body))]
        }
    }

    def payrollShow() {
        wrap { String npub -> hrPayrollService.toMap(hrPayrollService.get(params.long('id'), npub)) }
    }

    def payrollLines() {
        wrapBody { String npub, Map body ->
            List lines = (body.lines instanceof List) ? body.lines : []
            hrPayrollService.setLines(params.long('id'), npub, lines)
        }
    }

    def payrollReady() {
        wrapBody { String npub, Map body ->
            hrPayrollService.toMap(hrPayrollService.markReady(params.long('id'), npub))
        }
    }

    def payrollSubmit() {
        wrapBody { String npub, Map body ->
            hrPayrollService.submitToFinance(params.long('id'), npub)
        }
    }

    def payrollClose() {
        wrapBody { String npub, Map body ->
            hrPayrollService.toMap(hrPayrollService.close(params.long('id'), npub))
        }
    }

    // ----- Plan -----
    def planList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = hrPlanService.listPage(npub, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { hrPlanService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def planCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: hrPlanService.toMap(hrPlanService.create(npub, body))]
        }
    }

    // ----- Goals -----
    def goalList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = hrGoalService.listPage(npub, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { hrGoalService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def goalCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: hrGoalService.toMap(hrGoalService.create(npub, body))]
        }
    }

    def goalProgress() {
        wrapBody { String npub, Map body ->
            Integer prog = body.progress != null ? body.progress as Integer : null
            hrGoalService.toMap(hrGoalService.updateProgress(params.long('id'), npub, prog))
        }
    }

    def goalComplete() {
        wrapBody { String npub, Map body ->
            hrGoalService.toMap(hrGoalService.complete(params.long('id'), npub))
        }
    }

    // ----- Policy -----
    def policyList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            boolean activeOnly = params.activeOnly != 'false'
            Map result = hrPolicyService.listPage(npub, activeOnly, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { hrPolicyService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'name', order: 'asc']
            )
        }
    }

    def policyCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: hrPolicyService.toMap(hrPolicyService.create(npub, body))]
        }
    }

    // ----- Party (no HRFile) -----
    def partyList() {
        wrap { String npub ->
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            Map result = hrPartyService.listPage(npub, params.partyKind as String, page.max as int, page.offset as int)
            ListPaging.envelope(
                    (result.items as List).collect { hrPartyService.toMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'id', order: 'desc']
            )
        }
    }

    def partyCreate() {
        wrapBody { String npub, Map body ->
            [status: 201, body: hrPartyService.toMap(hrPartyService.provision(npub, body))]
        }
    }

    private void wrap(Closure c) {
        try {
            render(c.call(resolveNpub(params)) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('HR API error', e)
            render(status: 500, text: ([code: 'HR_ERROR', error: e.message] as JSON))
        }
    }

    private void wrapBody(Closure c) {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            def result = c.call(npub, body)
            if (result instanceof Map && result.status && result.body != null) {
                render(status: result.status as int, text: (result.body as JSON))
            } else {
                render(result as JSON)
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([code: 'BAD_REQUEST', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('HR API error', e)
            render(status: 500, text: ([code: 'HR_ERROR', error: e.message] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
