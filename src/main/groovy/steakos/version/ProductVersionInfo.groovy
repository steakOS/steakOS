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

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Product distribution version stamp per docs/versions/VERSIONS.md (V1–V7).
 *
 * Full stamp: {@code {line}-R{major}.{minor}-V{semver}+{buildId}}
 * UI short:   {@code SBM 1.0 (V0.1.0)}
 * Docker tag: same as full stamp with {@code +} → {@code -} (OCI tag-safe)
 */
class ProductVersionInfo {

    private static final DateTimeFormatter BUILD_ID_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm").withZone(ZoneOffset.UTC)

    /** Product line: SBM | SMB | LSB | MNC */
    String productLine

    /** Release train major (R{major}.{minor}) — SBM is R1.x */
    int releaseMajor

    /** Release train minor */
    int releaseMinor

    /** Software SemVer without V prefix (resets per product line; 0.x pre-GA) */
    String softwareVersion

    /** Immutable build id (e.g. 20260315T1321) */
    String buildId

    /**
     * Where {@link #softwareVersion} was resolved from (OI-V01 diagnostics).
     * Examples: {@code jar:Implementation-Version}, {@code config:info.app.version}, {@code dev:build.gradle}.
     */
    String versionSource

    String getReleaseTrain() {
        "R${releaseMajor}.${releaseMinor}"
    }

    /** Normalized three-part SemVer with V prefix */
    String getSemVerLabel() {
        "V${normalizeSemVer(softwareVersion)}"
    }

    /**
     * Full stamp: SBM-R1.0-V0.1.0+20260315T1321
     */
    String getFullStamp() {
        "${productLine}-${releaseTrain}-${semVerLabel}+${buildId ?: 'unknown'}"
    }

    /**
     * Human UI label: SBM 1.0 (V0.1.0)
     */
    String getDisplayLabel() {
        "${productLine} ${releaseMajor}.${releaseMinor} (${semVerLabel})"
    }

    /**
     * Docker/OCI-safe image tag (no {@code +}).
     * Example: SBM-R1.0-V0.1.0-20260315T1321
     */
    String getDockerTag() {
        fullStamp.replace('+', '-')
    }

    Map toMap() {
        [
                productLine    : productLine,
                releaseMajor   : releaseMajor,
                releaseMinor   : releaseMinor,
                R              : releaseTrain,
                V              : semVerLabel,
                softwareVersion: normalizeSemVer(softwareVersion),
                buildId        : buildId ?: 'unknown',
                fullStamp      : fullStamp,
                displayLabel   : displayLabel,
                dockerTag      : dockerTag,
                versionSource  : versionSource ?: 'unknown',
        ]
    }

    /**
     * Coerce Gradle-style versions to SemVer major.minor.patch.
     * 0.1 → 0.1.0 ; 0.1.0-SNAPSHOT → 0.1.0 (label only; pre-release suffix stripped for stamp core)
     */
    static String normalizeSemVer(String raw) {
        if (!raw?.trim()) {
            return '0.0.0'
        }
        String v = raw.trim()
        // drop +build or -SNAPSHOT / -rc for core SemVer digits
        int plus = v.indexOf('+')
        if (plus >= 0) {
            v = v.substring(0, plus)
        }
        int dash = v.indexOf('-')
        if (dash >= 0) {
            v = v.substring(0, dash)
        }
        String[] parts = v.tokenize('.')
        String major = parts.length > 0 && parts[0].isInteger() ? parts[0] : '0'
        String minor = parts.length > 1 && parts[1].isInteger() ? parts[1] : '0'
        String patch = parts.length > 2 && parts[2].isInteger() ? parts[2] : '0'
        return "${major}.${minor}.${patch}"
    }

    /**
     * Compact UTC build id: yyyyMMdd'T'HHmm
     */
    static String defaultBuildId(Instant at = Instant.now()) {
        BUILD_ID_FMT.format(at)
    }
}
