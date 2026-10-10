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
import steakos.configuration.LocaleService

/** Public locale catalog for login + tour pickers. */
@Secured(['permitAll'])
class LocalesApiController {

    static allowedMethods = [index: 'GET']

    LocaleService localeService

    def index() {
        render([
                items  : localeService.listLocales(),
                default: LocaleService.DEFAULT_LOCALE
        ] as JSON)
    }
}
