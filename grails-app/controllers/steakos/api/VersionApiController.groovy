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
import steakos.version.ProductVersionService
import steakos.version.SystemStatsService

/**
 * Product version surface (VERSIONS.md V7).
 * GET /api/version → line, R, V, buildId, fullStamp, displayLabel, dockerTag
 * GET /api/version/stats → system vitals (About page)
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class VersionApiController {

    static allowedMethods = [index: 'GET', stats: 'GET']

    ProductVersionService productVersionService
    SystemStatsService systemStatsService

    def index() {
        render(productVersionService.current().toMap() as JSON)
    }

    /** Live + inventory system stats for About. */
    def stats() {
        try {
            render(systemStatsService.snapshot() as JSON)
        } catch (Exception e) {
            render(status: 500, text: ([error: e.message] as JSON))
        }
    }
}
