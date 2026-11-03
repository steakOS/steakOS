// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.configuration

import spock.lang.Specification

class DatabaseWipeServiceSpec extends Specification {

    def 'keep set never includes business tables'() {
        expect:
        DatabaseWipeService.KEEP_TABLES.containsAll(['user', 'role', 'user_role'])
        !DatabaseWipeService.KEEP_TABLES.contains('invoice')
        !DatabaseWipeService.KEEP_TABLES.contains('legal_entity')
        !DatabaseWipeService.KEEP_TABLES.contains('setup_state')
    }
}
