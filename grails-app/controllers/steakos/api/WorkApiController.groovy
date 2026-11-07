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
import steakos.User
import steakos.configuration.TenantNpubService

/**
 * My Work API (ADR-056) — Assignments + Own Tasks.
 * Routes: GET /api/work/assignments · GET /api/work/own-tasks
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class WorkApiController {

    static allowedMethods = [
            assignments: 'GET',
            ownTasks   : 'GET',
    ]

    MyWorkService myWorkService
    TenantNpubService tenantNpubService
    SpringSecurityService springSecurityService

    /** GET /api/work/assignments?max=50 */
    def assignments() {
        User u = springSecurityService.currentUser as User
        String npub = tenantNpubService.resolve(session)
        int max = params.max != null ? params.int('max') : 50
        Map result = myWorkService.listAssignments(u, npub, max)
        render(result as JSON)
    }

    /** GET /api/work/own-tasks?mode=human|agentic&max=500&offset=0 */
    def ownTasks() {
        User u = springSecurityService.currentUser as User
        String npub = tenantNpubService.resolve(session)
        String mode = (params.mode ?: 'human').toString()
        int max = params.max != null ? params.int('max') : 500
        int offset = params.offset != null ? params.int('offset') : 0
        Map result = myWorkService.listOwnTasks(u, npub, mode, max, offset)
        render(result as JSON)
    }
}
