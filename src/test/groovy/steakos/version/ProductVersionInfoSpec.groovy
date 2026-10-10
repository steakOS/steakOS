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

package steakos.version

import spock.lang.Specification
import spock.lang.Unroll

class ProductVersionInfoSpec extends Specification {

    def "full stamp and display label match VERSIONS.md shape"() {
        given:
        def info = new ProductVersionInfo(
                productLine    : 'SBM',
                releaseMajor   : 1,
                releaseMinor   : 0,
                softwareVersion: '0.1.0',
                buildId        : '20260315T1321'
        )

        expect:
        info.releaseTrain == 'R1.0'
        info.semVerLabel == 'V0.1.0'
        info.fullStamp == 'SBM-R1.0-V0.1.0+20260315T1321'
        info.displayLabel == 'SBM 1.0 (V0.1.0)'
        info.dockerTag == 'SBM-R1.0-V0.1.0-20260315T1321'
    }

    def "SMB line resets SemVer clock — stamp still unique via line"() {
        given:
        def info = new ProductVersionInfo(
                productLine    : 'SMB',
                releaseMajor   : 2,
                releaseMinor   : 0,
                softwareVersion: '1.0.0',
                buildId        : '20271223T1321'
        )

        expect:
        info.fullStamp == 'SMB-R2.0-V1.0.0+20271223T1321'
        info.displayLabel == 'SMB 2.0 (V1.0.0)'
    }

    @Unroll
    def "normalizeSemVer #raw -> #expected"() {
        expect:
        ProductVersionInfo.normalizeSemVer(raw) == expected

        where:
        raw              | expected
        '0.1'            | '0.1.0'
        '0.1.0'          | '0.1.0'
        '1.2.3'          | '1.2.3'
        '0.1.0-SNAPSHOT' | '0.1.0'
        '2.0+build.1'    | '2.0.0'
        null             | '0.0.0'
        ''               | '0.0.0'
    }

    def "toMap exposes V7 API fields"() {
        given:
        def info = new ProductVersionInfo(
                productLine    : 'SBM',
                releaseMajor   : 1,
                releaseMinor   : 0,
                softwareVersion: '0.1',
                buildId        : '20260101T0000'
        )

        when:
        Map m = info.toMap()

        then:
        m.productLine == 'SBM'
        m.R == 'R1.0'
        m.V == 'V0.1.0'
        m.softwareVersion == '0.1.0'
        m.buildId == '20260101T0000'
        m.fullStamp == 'SBM-R1.0-V0.1.0+20260101T0000'
        m.displayLabel == 'SBM 1.0 (V0.1.0)'
        m.dockerTag == 'SBM-R1.0-V0.1.0-20260101T0000'
    }

    def "defaultBuildId is compact UTC stamp"() {
        expect:
        ProductVersionInfo.defaultBuildId() ==~ /^\d{8}T\d{4}$/
        ProductVersionInfo.defaultBuildId(java.time.Instant.parse('2026-03-15T13:21:00Z')) == '20260315T1321'
    }
}
