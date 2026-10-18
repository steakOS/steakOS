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
import spock.lang.Unroll

/**
 * Domain CRUD date binding must accept HTML date inputs (yyyy-MM-dd)
 * without Groovy Date.parse (broken when groovy-dateutil is absent).
 */
class DateParseSpec extends Specification {

    def "blank and null → null"() {
        expect:
        DateParse.toDate(null) == null
        DateParse.toDate('') == null
        DateParse.toDate('  ') == null
    }

    def "yyyy-MM-dd from date input"() {
        when:
        Date d = DateParse.toDate('2026-10-01')

        then:
        d != null
        d.toInstant().toString().startsWith('2026-10-01')
    }

    def "ISO instant with Z"() {
        when:
        Date d = DateParse.toDate('2026-10-01T15:30:00Z')

        then:
        d != null
        d.time > 0
    }

    def "passes through Date and epoch millis"() {
        given:
        Date now = new Date(1_700_000_000_000L)

        expect:
        DateParse.toDate(now).is(now)
        DateParse.toDate(1_700_000_000_000L).time == 1_700_000_000_000L
        DateParse.toDate('1700000000000').time == 1_700_000_000_000L
    }

    @Unroll
    def "unparseable #raw → null"() {
        expect:
        DateParse.toDate(raw) == null

        where:
        raw << ['nope', '2026/10/01', 'October 1']
    }
}
