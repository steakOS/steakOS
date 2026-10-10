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
import steakos.finance.BtcFxService

/**
 * BTC FX for UI hints (PDR-023).
 * GET /api/fx/btc?quote=EUR|USD
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class FxApiController {

    static allowedMethods = [btc: 'GET']

    BtcFxService btcFxService

    def btc() {
        try {
            String quote = (params.quote ?: 'EUR') as String
            render(btcFxService.rate(quote) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        } catch (IllegalStateException e) {
            render(status: 503, text: ([error: e.message] as JSON))
        } catch (Exception e) {
            log.error('FX btc failed', e)
            render(status: 500, text: ([error: e.message ?: 'fx failed'] as JSON))
        }
    }
}
