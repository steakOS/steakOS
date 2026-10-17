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
import steakos.configuration.TenantNpubService
import steakos.kpi.KpiService

/**
 * KPI read API — GET /api/kpi/* (Silver WP-K1).
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class KpiApiController {

    static allowedMethods = [
            catalogue    : 'GET',
            show         : 'GET',
            dashboardHero: 'GET'
    ]

    KpiService kpiService
    TenantNpubService tenantNpubService

    def catalogue() {
        wrap { String npub ->
            kpiService.catalogue(npub)
        }
    }

    def show() {
        wrap { String npub ->
            Date from = parseDate(params.from)
            Date to = parseDate(params.to)
            kpiService.show(npub, params.id as String, from, to)
        }
    }

    def dashboardHero() {
        wrap { String npub ->
            kpiService.dashboardHero(npub)
        }
    }

    private void wrap(Closure c) {
        try {
            String npub = tenantNpubService.resolve(session, params)
            render(c.call(npub) as JSON)
        } catch (SecurityException e) {
            render(status: 403, text: ([code: 'KPI_FORBIDDEN', error: e.message] as JSON))
        } catch (IllegalArgumentException e) {
            render(status: 404, text: ([code: 'KPI_NOT_FOUND', error: e.message] as JSON))
        } catch (Exception e) {
            log.error('KPI API error', e)
            render(status: 500, text: ([code: 'KPI_ERROR', error: e.message] as JSON))
        }
    }

    private static Date parseDate(Object raw) {
        if (raw == null) return null
        String s = raw.toString().trim()
        if (!s) return null
        try {
            if (s.isLong()) {
                return new Date(s.toLong())
            }
            // ISO-8601 date (yyyy-MM-dd) — enough for period filters
            return Date.parse('yyyy-MM-dd', s.take(10))
        } catch (Exception ignored) {
            return null
        }
    }
}
