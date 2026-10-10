// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
package steakos.configuration

import spock.lang.Specification

class CompanyContextServiceSpec extends Specification {

    void 'session and demo constants are stable'() {
        expect:
        CompanyContextService.SESSION_ACTIVE_COMPANY_ID == 'STEAKOS_ACTIVE_COMPANY_ID'
        CompanyContextService.DEMO_NAME == 'Orange Steak House'
        LegalEntity.KIND_DEMO == 'DEMO'
        LegalEntity.KIND_LIVE == 'LIVE'
        LegalEntity.CODE_ORANGE_STEAK == 'orange-steak-house'
        LegalEntity.CODE_PRIMARY == 'primary'
    }
}
