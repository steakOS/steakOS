// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.version

import spock.lang.Specification

class ProductVersionServiceSpec extends Specification {

    def "readBuildGradleVersion parses project version from build.gradle"() {
        given:
        def root = new File('.').canonicalFile
        // Spec runs from project root in Gradle; if not, skip
        def gradle = new File(root, 'build.gradle')
        if (!gradle.isFile()) {
            gradle = new File(root.parentFile ?: root, 'build.gradle')
        }

        expect:
        gradle.isFile()
        def v = ProductVersionService.readBuildGradleVersion()
        v ==~ /^\d+\.\d+(\.\d+)?/
        !v.contains('@')
    }

    def "toMap includes versionSource for OI-V01 diagnostics"() {
        given:
        def info = new ProductVersionInfo(
                productLine    : 'SBM',
                releaseMajor   : 1,
                releaseMinor   : 0,
                softwareVersion: '0.1.7',
                buildId        : '20260917T1200',
                versionSource  : 'jar:Implementation-Version'
        )

        expect:
        info.toMap().versionSource == 'jar:Implementation-Version'
        info.toMap().softwareVersion == '0.1.7'
    }
}
