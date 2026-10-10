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

package steakos.helper

import spock.lang.Specification

class CurrencyCatalogCodesSpec extends Specification {

    def "catalog has common trade fiats and no bitcoin codes"() {
        expect:
        CurrencyCatalogCodes.ALL.size() >= 25
        CurrencyCatalogCodes.ALL.size() <= 40
        CurrencyCatalogCodes.isValid('EUR')
        CurrencyCatalogCodes.isValid('USD')
        CurrencyCatalogCodes.isValid('JPY')
        !CurrencyCatalogCodes.isValid('BTC')
        !CurrencyCatalogCodes.isValid('XBT')
        !CurrencyCatalogCodes.isValid('SATS')
        CurrencyCatalogCodes.ALL.every { !(it.code in ['BTC', 'XBT', 'SATS']) }
        CurrencyCatalogCodes.rowFor('eur').name == 'Euro'
        CurrencyCatalogCodes.rowFor('JPY').decimals == 0
    }
}
