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

package steakos.finance

import spock.lang.Specification

class TaxCatalogCodesSpec extends Specification {

    def "DE AT packs and US stubs present"() {
        expect:
        TaxCatalogCodes.isValid('DE_USt_STD')
        TaxCatalogCodes.rowFor('DE_USt_STD').ratePercent == 19.00G
        TaxCatalogCodes.rowFor('DE_USt_RED').ratePercent == 7.00G
        TaxCatalogCodes.rowFor('DE_RC').reverseCharge == true
        TaxCatalogCodes.rowFor('AT_USt_STD').ratePercent == 20.00G
        TaxCatalogCodes.rowFor('AT_USt_RED').ratePercent == 10.00G
        TaxCatalogCodes.rowFor('AT_USt_RED2').ratePercent == 13.00G
        TaxCatalogCodes.isValid('US_EXEMPT')
        TaxCatalogCodes.isValid('US_ST_STD')
        TaxCatalogCodes.forJurisdiction('DE').size() >= 5
        TaxCatalogCodes.forJurisdiction('AT').size() >= 5
    }
}
