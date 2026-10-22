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
import steakos.configuration.HelpService

@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class HelpApiController {

    static allowedMethods = [topics: 'GET', resolve: 'GET', show: 'GET']

    HelpService helpService

    def topics() {
        render([items: helpService.listTopics()] as JSON)
    }

    def resolve() {
        String route = params.route ?: params.path ?: '/'
        String lang = params.lang ?: request.getHeader('Accept-Language')
        // ADR-044: richer HelpContext query params with ordered fallback
        Map extra = [:]
        if (params.desk) extra.desk = params.desk as String
        if (params.tab) extra.tab = params.tab as String
        if (params.resource) extra.resource = params.resource as String
        if (params.objectId) extra.objectId = params.objectId as String
        if (params.status) extra.status = params.status as String
        if (params.processCode) extra.processCode = params.processCode as String
        if (extra) {
            render(helpService.resolveForContext(route as String, lang as String, extra) as JSON)
        } else {
            render(helpService.resolveForRoute(route as String, lang as String) as JSON)
        }
    }

    def show(String helpKey) {
        String lang = params.lang ?: request.getHeader('Accept-Language')
        def t = helpService.getTopic(helpKey, lang as String)
        if (!t) {
            render(status: 404, text: ([error: 'not found'] as JSON))
            return
        }
        render(t as JSON)
    }
}
