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
import grails.plugin.springsecurity.annotation.Secured
import groovy.util.logging.Slf4j
import steakos.configuration.RequiresAdvanced
import steakos.configuration.TenantNpubService
import steakos.finance.FinanceMatchService
import steakos.finance.ThreeWayMatch

/**
 * FinanceAdvanced light three-way match API (OI-F21 / PR-5b).
 * Path prefix {@code /api/finance/match} is gated SILVER+ via AdvancedRouteCatalog.
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
@RequiresAdvanced(family = 'finance')
class FinanceMatchApiController {

    static allowedMethods = [
            list      : 'GET',
            candidates: 'GET',
            show      : 'GET',
            propose   : 'POST',
            confirm   : 'POST',
            reject    : 'POST',
            cancel    : 'POST'
    ]

    FinanceMatchService financeMatchService
    TenantNpubService tenantNpubService

    /** GET /api/finance/match — list matches (?status=PROPOSED|CONFIRMED|…) */
    def list() {
        wrap { String npub ->
            financeMatchService.listMatches(npub, params.status?.toString())
        }
    }

    /** GET /api/finance/match/candidates — matchable PO/receipt/payable triples */
    def candidates() {
        wrap { String npub ->
            financeMatchService.listCandidates(npub)
        }
    }

    /** GET /api/finance/match/$id */
    def show() {
        wrap { String npub ->
            financeMatchService.show(params.long('id'), npub)
        }
    }

    /** POST /api/finance/match — propose {purchaseOrderId, receiptId?, payableId?} */
    def propose() {
        wrapMutate(201) { String npub, Map body ->
            ThreeWayMatch m = financeMatchService.propose(npub, body)
            financeMatchService.toMap(m)
        }
    }

    /** POST /api/finance/match/$id/confirm — optional {force:true} */
    def confirm() {
        wrapMutate(200) { String npub, Map body ->
            ThreeWayMatch m = financeMatchService.confirm(npub, params.long('id'), body)
            financeMatchService.toMap(m)
        }
    }

    /** POST /api/finance/match/$id/reject */
    def reject() {
        wrapMutate(200) { String npub, Map body ->
            ThreeWayMatch m = financeMatchService.reject(npub, params.long('id'), body)
            financeMatchService.toMap(m)
        }
    }

    /** POST /api/finance/match/$id/cancel */
    def cancel() {
        wrapMutate(200) { String npub, Map body ->
            ThreeWayMatch m = financeMatchService.cancel(npub, params.long('id'))
            financeMatchService.toMap(m)
        }
    }

    private void wrap(Closure c) {
        try {
            String npub = tenantNpubService.resolve(session, params)
            render(c.call(npub) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'MATCH_BAD_REQUEST'] as JSON))
        } catch (IllegalStateException e) {
            render(status: 409, text: ([error: e.message, code: 'MATCH_CONFLICT'] as JSON))
        } catch (Exception e) {
            log.error('match API error', e)
            render(status: 500, text: ([error: e.message, code: 'MATCH_ERROR'] as JSON))
        }
    }

    private void wrapMutate(int okStatus, Closure c) {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = tenantNpubService.resolve(session, body ?: params)
            Object result = c.call(npub, body)
            render(status: okStatus, text: (result as JSON))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'MATCH_BAD_REQUEST'] as JSON))
        } catch (IllegalStateException e) {
            render(status: 409, text: ([error: e.message, code: 'MATCH_CONFLICT'] as JSON))
        } catch (Exception e) {
            log.error('match API mutate error', e)
            render(status: 500, text: ([error: e.message, code: 'MATCH_ERROR'] as JSON))
        }
    }
}
