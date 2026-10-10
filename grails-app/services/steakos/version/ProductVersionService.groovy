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

import grails.core.GrailsApplication
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value

import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Resolves product version (VERSIONS.md V6/V7 / OI-V01).
 *
 * Priority avoids "process version lag" where a long-lived process served a stale
 * {@code info.app.version} from outdated processResources while {@code build.gradle}
 * had already been bumped:
 * <ol>
 *   <li>{@code STEAKOS_SOFTWARE_VERSION} env (ops override)</li>
 *   <li>Running jar {@code Implementation-Version} (authoritative for bootJar)</li>
 *   <li>Spring {@code META-INF/build-info.properties} if present</li>
 *   <li>Filtered {@code info.app.version} / Grails metadata / grails.build.info</li>
 *   <li>Dev: parse {@code version = "..."} from {@code build.gradle} under user.dir</li>
 * </ol>
 */
@Slf4j
class ProductVersionService {

    private static final Pattern GRADLE_VERSION =
            Pattern.compile(/^\s*version\s*=\s*["']([^"']+)["']\s*$/, Pattern.MULTILINE)

    GrailsApplication grailsApplication

    @Value('${steakos.version.productLine:SBM}')
    String productLine

    @Value('${steakos.version.releaseMajor:1}')
    int releaseMajor

    @Value('${steakos.version.releaseMinor:0}')
    int releaseMinor

    @Value('${steakos.version.buildId:}')
    String configuredBuildId

    private String runtimeBuildId
    private String cachedSoftware
    private String cachedSource
    private boolean loggedOnce

    ProductVersionInfo current() {
        Map resolved = resolveSoftwareVersionDetailed()
        if (!loggedOnce) {
            loggedOnce = true
            log.info('Product version {} (source={})', resolved.version, resolved.source)
        }
        new ProductVersionInfo(
                productLine    : productLine ?: 'SBM',
                releaseMajor   : releaseMajor,
                releaseMinor   : releaseMinor,
                softwareVersion: resolved.version as String,
                buildId        : resolveBuildId(),
                versionSource  : resolved.source as String
        )
    }

    /**
     * @return [version: String, source: String]
     */
    Map resolveSoftwareVersionDetailed() {
        if (cachedSoftware) {
            return [version: cachedSoftware, source: cachedSource]
        }
        List candidates = [
                [System.getenv('STEAKOS_SOFTWARE_VERSION'), 'env:STEAKOS_SOFTWARE_VERSION'],
                [readManifestImplementationVersion(), 'jar:Implementation-Version'],
                [readSpringBuildInfoVersion(), 'META-INF/build-info.properties'],
                [grailsApplication?.config?.getProperty('info.app.version', String), 'config:info.app.version'],
                [readGrailsMetadataVersion(), 'grails.metadata'],
                [System.getProperty('info.app.version'), 'systemProperty:info.app.version'],
                [readBuildInfoVersion(), 'META-INF/grails.build.info'],
                [readBuildGradleVersion(), 'dev:build.gradle'],
        ]
        for (List pair : candidates) {
            String v = pair[0] as String
            String src = pair[1] as String
            if (isUsableVersion(v)) {
                cachedSoftware = v.trim()
                cachedSource = src
                return [version: cachedSoftware, source: cachedSource]
            }
        }
        cachedSoftware = '0.1.0'
        cachedSource = 'fallback'
        return [version: cachedSoftware, source: cachedSource]
    }

    private String resolveSoftwareVersion() {
        resolveSoftwareVersionDetailed().version as String
    }

    private static boolean isUsableVersion(String v) {
        v != null && v.trim() && !v.contains('@') && !(v.trim() ==~ /(?i)unspecified|null/)
    }

    private String readGrailsMetadataVersion() {
        def meta = grailsApplication?.metadata
        if (!meta) return null
        meta.getApplicationVersion() ?: (meta.get('info.app.version') as String)
    }

    /**
     * Implementation-Version from the running bootJar / war Manifest.
     * This matches the artifact filename (e.g. steakOS-0.1.7.jar) even when
     * classpath application.yml was filtered in an older build.
     */
    static String readManifestImplementationVersion() {
        try {
            // Prefer ProtectionDomain code source (bootJar nested jar or exploded)
            URL loc = ProductVersionService.protectionDomain?.codeSource?.location
            if (loc != null && loc.protocol == 'file') {
                File f = new File(loc.toURI())
                if (f.isFile() && f.name.toLowerCase().endsWith('.jar')) {
                    JarFile jar = new JarFile(f)
                    try {
                        String v = jar.manifest?.mainAttributes?.getValue(Attributes.Name.IMPLEMENTATION_VERSION)
                        if (isUsableVersion(v)) {
                            return v
                        }
                    } finally {
                        jar.close()
                    }
                }
            }
        } catch (Exception ignored) {
            /* fall through */
        }
        try {
            InputStream is = ProductVersionService.classLoader.getResourceAsStream('META-INF/MANIFEST.MF')
            if (!is) {
                return null
            }
            try {
                Manifest mf = new Manifest(is)
                return mf.mainAttributes?.getValue(Attributes.Name.IMPLEMENTATION_VERSION)
            } finally {
                is.close()
            }
        } catch (Exception ignored) {
            return null
        }
    }

    /** Spring Boot build-info plugin output when enabled. */
    static String readSpringBuildInfoVersion() {
        try {
            InputStream is = ProductVersionService.classLoader.getResourceAsStream('META-INF/build-info.properties')
            if (!is) return null
            Properties p = new Properties()
            is.withCloseable { stream -> p.load(stream) }
            return p.getProperty('build.version')
        } catch (Exception ignored) {
            return null
        }
    }

    /** META-INF/grails.build.info written by Gradle. */
    static String readBuildInfoVersion() {
        try {
            InputStream is = ProductVersionService.classLoader.getResourceAsStream('META-INF/grails.build.info')
            if (!is) {
                is = ProductVersionService.classLoader.getResourceAsStream('grails.build.info')
            }
            if (!is) {
                return null
            }
            Properties p = new Properties()
            is.withCloseable { stream -> p.load(stream) }
            return p.getProperty('info.app.version')
        } catch (Exception ignored) {
            return null
        }
    }

    /**
     * Development / bootRun: read {@code version = "x.y.z"} from build.gradle under user.dir
     * so a bump shows up without waiting for a stale filtered application.yml.
     */
    static String readBuildGradleVersion() {
        try {
            File root = new File(System.getProperty('user.dir') ?: '.')
            File gradle = new File(root, 'build.gradle')
            if (!gradle.isFile()) {
                gradle = new File(root, 'build.gradle.kts')
            }
            if (!gradle.isFile()) return null
            String text = gradle.getText('UTF-8')
            Matcher m = GRADLE_VERSION.matcher(text)
            if (m.find()) {
                return m.group(1)
            }
        } catch (Exception ignored) {
            return null
        }
        return null
    }

    private String resolveBuildId() {
        String fromEnv = System.getenv('STEAKOS_BUILD_ID')
        if (fromEnv?.trim()) {
            return fromEnv.trim()
        }
        if (configuredBuildId?.trim()) {
            return configuredBuildId.trim()
        }
        String fromBuildInfo = readSpringBuildInfoTime()
        if (fromBuildInfo) {
            return fromBuildInfo
        }
        if (!runtimeBuildId) {
            runtimeBuildId = ProductVersionInfo.defaultBuildId()
        }
        runtimeBuildId
    }

    private static String readSpringBuildInfoTime() {
        try {
            InputStream is = ProductVersionService.classLoader.getResourceAsStream('META-INF/build-info.properties')
            if (!is) return null
            Properties p = new Properties()
            is.withCloseable { stream -> p.load(stream) }
            String t = p.getProperty('build.time')
            if (!t?.trim()) return null
            // Spring uses Instant ISO; compress to yyyyMMdd'T'HHmm when parseable
            try {
                return ProductVersionInfo.defaultBuildId(java.time.Instant.parse(t.trim()))
            } catch (Exception ignored) {
                return t.trim().replaceAll(/[^0-9T]/, '').take(13)
            }
        } catch (Exception ignored) {
            return null
        }
    }
}
