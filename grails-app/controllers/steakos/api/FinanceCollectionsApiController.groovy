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
import steakos.finance.CollectionActivity
import steakos.finance.FinanceCollectionsService

/**
 * FinanceAdvanced collections API — overdue queue + promise/activity (PR-5a).
 * Path prefix {@code /api/finance/collections} is gated SILVER+ via AdvancedRouteCatalog.
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
@RequiresAdvanced(family = 'finance')
class FinanceCollectionsApiController {

    static allowedMethods = [
            overdue           : 'GET',
            openQueue         : 'GET',
            show              : 'GET',
            listActivities    : 'GET',
            createActivity    : 'POST',
            createPromise     : 'POST',
            updatePromiseStatus: 'PUT'
    ]

    FinanceCollectionsService financeCollectionsService
    TenantNpubService tenantNpubService

    /** GET /api/finance/collections — overdue AR queue */
    def overdue() {
        wrap { String npub ->
            financeCollectionsService.listOverdueQueue(npub, parseAsOf(params.asOf))
        }
    }

    /** GET /api/finance/collections/open — all open AR with aging buckets */
    def openQueue() {
        wrap { String npub ->
            financeCollectionsService.listOpenQueue(npub, parseAsOf(params.asOf))
        }
    }

    /** GET /api/finance/collections/$id */
    def show() {
        wrap { String npub ->
            financeCollectionsService.showQueueItem(params.long('id'), npub, parseAsOf(params.asOf))
        }
    }

    /** GET /api/finance/collections/$id/activities */
    def listActivities() {
        wrap { String npub ->
            financeCollectionsService.listActivities(npub, params.long('id'))
        }
    }

    /** POST /api/finance/collections/$id/activities */
    def createActivity() {
        wrapMutate(201) { String npub, Map body ->
            CollectionActivity act = financeCollectionsService.recordActivity(npub, params.long('id'), body)
            financeCollectionsService.activityToMap(act)
        }
    }

    /** POST /api/finance/collections/$id/promise */
    def createPromise() {
        wrapMutate(201) { String npub, Map body ->
            CollectionActivity act = financeCollectionsService.recordPromise(npub, params.long('id'), body)
            financeCollectionsService.activityToMap(act)
        }
    }

    /** PUT /api/finance/collections/activities/$activityId */
    def updatePromiseStatus() {
        wrapMutate(200) { String npub, Map body ->
            String st = (body.promiseStatus ?: body.status)?.toString()
            CollectionActivity act = financeCollectionsService.updatePromiseStatus(
                    npub, params.long('activityId'), st)
            financeCollectionsService.activityToMap(act)
        }
    }

    private void wrap(Closure c) {
        try {
            String npub = tenantNpubService.resolve(session, params)
            render(c.call(npub) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'COLLECTIONS_BAD_REQUEST'] as JSON))
        } catch (IllegalStateException e) {
            render(status: 409, text: ([error: e.message, code: 'COLLECTIONS_CONFLICT'] as JSON))
        } catch (Exception e) {
            log.error('collections API error', e)
            render(status: 500, text: ([error: e.message, code: 'COLLECTIONS_ERROR'] as JSON))
        }
    }

    private void wrapMutate(int okStatus, Closure c) {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = tenantNpubService.resolve(session, body ?: params)
            Object result = c.call(npub, body)
            render(status: okStatus, text: (result as JSON))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'COLLECTIONS_BAD_REQUEST'] as JSON))
        } catch (IllegalStateException e) {
            render(status: 409, text: ([error: e.message, code: 'COLLECTIONS_CONFLICT'] as JSON))
        } catch (Exception e) {
            log.error('collections API mutate error', e)
            render(status: 500, text: ([error: e.message, code: 'COLLECTIONS_ERROR'] as JSON))
        }
    }

    private static Date parseAsOf(Object raw) {
        if (raw == null || raw.toString().trim().isEmpty()) {
            return new Date()
        }
        String s = raw.toString().trim()
        try {
            if (s.isLong()) {
                return new Date(s.toLong())
            }
            return Date.parse('yyyy-MM-dd', s.take(10))
        } catch (Exception ignored) {
            return new Date()
        }
    }
}
