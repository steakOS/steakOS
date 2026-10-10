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
import grails.core.GrailsClass
import grails.gorm.transactions.Transactional
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.core.artefact.ServiceArtefactHandler

import javax.sql.DataSource
import java.net.URL
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * System vitals for About page — live DB/GORM counts + engineering inventory snapshot.
 * Inventory numbers are refreshed when code/docs change (see refresh note in inventory map).
 */
@Slf4j
class SystemStatsService {

    GrailsApplication grailsApplication
    GrailsPluginManager pluginManager
    DataSource dataSource

    /** Skip heavy / generated trees when summing repo bytes. */
    private static final Set<String> DISK_SKIP_DIR_NAMES = [
            '.git', '.gradle', 'node_modules', 'build', 'out', 'target',
            'dist', '.idea', '.vscode', 'glowroot', '__pycache__'
    ].asImmutable() as Set<String>

    private static final long DISK_CACHE_TTL_MS = 5L * 60L * 1000L
    private volatile Map cachedDisk
    private volatile long cachedDiskAtMs = 0L

    /**
     * Engineering inventory (repo scan; productVersion stamp tracks current cut).
     * Not live LOCs — update when cutting a version or after large doc/code waves.
     */
    private static final Map INVENTORY = [
            asOf                    : '2026-08-22',
            productVersion          : '0.1.6',
            architecture            : 'modular monorepo (not microservices)',
            docsMarkdownFiles       : 47,
            docsLines               : 12680,
            docsWords               : 90669,
            docsChars               : 640682,
            docsSentencesApprox     : 6360,
            docsPagesApprox         : 250,
            conceptDocs             : 23,
            adrCount                : 37,
            adrRange                : 'ADR-001 … ADR-037',
            userStoriesPractice     : false,
            acceptanceCriteriaBulletsApprox: 16,
            successCriteriaBulletsApprox   : 25,
            decisionTableRowsApprox : 884,
            apiPathPatterns         : 260,
            apiMethodEndpointsApprox: 340,
            frontendApiClients      : 22,
            unitSpecFiles           : 23,
            unitTestMethodsApprox   : 112,
            httpE2eSuites           : 3, // SmokeApiSpec, FamilyHappyPathSpec, AgentBearerSmokeSpec
            codeLocApprox           : 55000,
            codePlusDocsI18nLocApprox: 80000,
            processAreaFamilies     : 11,
            apqcL1Target            : 14,
            importPacksLive         : 4,
            spaPages                : 22,
            note                    : 'Live rows: domains, services, controllers, DB tables, processes, help, demo companies, disk. Inventory: docs/LOC/ADRs/tests from monorepo scan.'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map snapshot() {
        Map live = liveInventory()
        Map disk = diskInventoryCached()
        [
                // Instant.toString is ISO-8601 UTC — avoid Groovy Date.format(pattern, TimeZone) (broken on Groovy 4 / JDK 21)
                generatedAt : java.time.Instant.now().toString(),
                live        : live,
                inventory   : INVENTORY,
                disk        : disk,
                // Flattened “vitals” for simple About grid
                vitals      : [
                        domains            : live.domainClasses,
                        services           : live.serviceBeans,
                        controllers        : live.controllers,
                        dbTables           : live.dbTables,
                        processesSystem    : live.processesSystem,
                        processSteps       : live.processSteps,
                        helpTopics         : live.helpTopics,
                        catalogueItems     : live.catalogueItems,
                        openTasks          : live.openTasks,
                        demoCompanies      : live.demoCompanies,
                        demoCompanyNames   : live.demoCompanyNames,
                        liveCompanies      : live.liveCompanies,
                        docsFiles          : INVENTORY.docsMarkdownFiles,
                        docsPagesApprox    : INVENTORY.docsPagesApprox,
                        docsChars          : INVENTORY.docsChars,
                        adrs               : INVENTORY.adrCount,
                        apiEndpointsApprox : INVENTORY.apiMethodEndpointsApprox,
                        unitTestsApprox    : INVENTORY.unitTestMethodsApprox,
                        httpE2e            : INVENTORY.httpE2eSuites,
                        codeLocApprox      : INVENTORY.codeLocApprox,
                        processAreas       : INVENTORY.processAreaFamilies,
                        pluginsInstalled   : live.pluginsInstalled,
                        pluginNames        : live.pluginNames,
                        userStories        : 0,
                        architecture       : INVENTORY.architecture,
                        diskBytesInclDocsDemos : disk.bytesInclDocsDemos,
                        diskBytesExclDocsDemos : disk.bytesExclDocsDemos,
                        diskHumanInclDocsDemos : disk.humanInclDocsDemos,
                        diskHumanExclDocsDemos : disk.humanExclDocsDemos,
                        docsBytes          : disk.docsBytes,
                        demosBytes         : disk.demosBytes,
                        docsHuman          : disk.docsHuman,
                        demosHuman         : disk.demosHuman
                ]
        ]
    }

    private Map liveInventory() {
        int domains = countArtefacts(DomainClassArtefactHandler.TYPE)
        int services = countArtefacts(ServiceArtefactHandler.TYPE)
        int controllers = countArtefacts(ControllerArtefactHandler.TYPE)

        int tables = 0
        int procSystem = 0
        int procAll = 0
        int steps = 0
        int help = 0
        int catalogue = 0
        int openTasks = 0
        int demoCompanies = 0
        int liveCompanies = 0
        List<String> demoNames = []
        Map pluginStats = countInstalledPlugins()

        Sql sql = null
        try {
            sql = new Sql(dataSource)
            tables = (sql.firstRow('SELECT count(*) AS c FROM information_schema.tables WHERE table_schema = ? AND table_type = ?',
                    ['public', 'BASE TABLE'])?.c ?: 0) as int
            // Process domain maps to business_process
            def pr = sql.firstRow('''
                SELECT
                  count(*) FILTER (WHERE process_kind = 'SYSTEM') AS system_n,
                  count(*) AS all_n
                FROM business_process
            ''')
            procSystem = (pr?.system_n ?: 0) as int
            procAll = (pr?.all_n ?: 0) as int
            steps = (sql.firstRow('SELECT count(*) AS c FROM process_step')?.c ?: 0) as int
            help = (sql.firstRow('SELECT count(*) AS c FROM help_topic')?.c ?: 0) as int
            catalogue = (sql.firstRow('SELECT count(*) AS c FROM catalogue_item')?.c ?: 0) as int
            openTasks = (sql.firstRow("SELECT count(*) AS c FROM task WHERE status IS NULL OR status NOT IN ('DONE','CANCELLED','Completed')")?.c ?: 0) as int

            def co = sql.firstRow('''
                SELECT
                  count(*) FILTER (WHERE kind = 'DEMO') AS demo_n,
                  count(*) FILTER (WHERE kind = 'LIVE') AS live_n
                FROM legal_entity
            ''')
            demoCompanies = (co?.demo_n ?: 0) as int
            liveCompanies = (co?.live_n ?: 0) as int
            sql.eachRow("SELECT name FROM legal_entity WHERE kind = 'DEMO' ORDER BY name") { row ->
                if (row.name) demoNames << (row.name as String)
            }
        } catch (Exception e) {
            log.warn("system stats DB probe: ${e.message}")
        } finally {
            try { sql?.close() } catch (ignored) { }
        }

        [
                domainClasses : domains,
                serviceBeans  : services,
                controllers   : controllers,
                dbTables      : tables,
                processesSystem: procSystem,
                processesAll  : procAll,
                processSteps  : steps,
                helpTopics    : help,
                catalogueItems: catalogue,
                openTasks     : openTasks,
                demoCompanies : demoCompanies,
                liveCompanies : liveCompanies,
                demoCompanyNames: demoNames,
                pluginsInstalled: pluginStats.count as int,
                pluginNames     : pluginStats.names as List
        ]
    }

    /**
     * Grails plugins currently loaded and enabled in this JVM (running install).
     */
    private Map countInstalledPlugins() {
        List<String> names = []
        try {
            GrailsPluginManager mgr = pluginManager
            if (mgr == null && grailsApplication?.mainContext != null) {
                try {
                    mgr = grailsApplication.mainContext.getBean(GrailsPluginManager.BEAN_NAME) as GrailsPluginManager
                } catch (Exception ignored) {
                    mgr = grailsApplication.mainContext.getBean(GrailsPluginManager) as GrailsPluginManager
                }
            }
            if (mgr == null) {
                return [count: 0, names: []]
            }
            GrailsPlugin[] all = mgr.allPlugins ?: new GrailsPlugin[0]
            for (GrailsPlugin p : all) {
                if (p == null) continue
                if (!p.enabled) continue
                String n = p.name ?: p.fileSystemShortName ?: p.toString()
                if (n) names << n
            }
            names = names.unique().sort(false)
            return [count: names.size(), names: names]
        } catch (Exception e) {
            log.warn("system stats plugin count: ${e.message}")
            return [count: 0, names: []]
        }
    }

    private Map diskInventoryCached() {
        long now = System.currentTimeMillis()
        Map hit = cachedDisk
        if (hit != null && (now - cachedDiskAtMs) < DISK_CACHE_TTL_MS) {
            return hit
        }
        Map fresh = diskInventory()
        cachedDisk = fresh
        cachedDiskAtMs = now
        return fresh
    }

    /**
     * Repo file sizes under project root.
     * Always skips build/node_modules/.git/etc.
     * Incl = with docs/ + demos/; Excl = without those two trees.
     */
    private Map diskInventory() {
        Path root = resolveProjectRoot()
        if (root == null || !Files.isDirectory(root)) {
            return [
                    root                 : null,
                    bytesInclDocsDemos   : 0L,
                    bytesExclDocsDemos   : 0L,
                    docsBytes            : 0L,
                    demosBytes           : 0L,
                    humanInclDocsDemos   : '—',
                    humanExclDocsDemos   : '—',
                    docsHuman            : '—',
                    demosHuman           : '—',
                    note                 : 'Project root not found for disk scan'
            ]
        }

        AtomicLong incl = new AtomicLong(0L)
        AtomicLong excl = new AtomicLong(0L)
        AtomicLong docs = new AtomicLong(0L)
        AtomicLong demos = new AtomicLong(0L)

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.fileName?.toString()
                    if (name && DISK_SKIP_DIR_NAMES.contains(name) && dir != root) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE
                    long size = attrs.size()
                    incl.addAndGet(size)
                    Path rel = root.relativize(file)
                    String top = rel.nameCount > 0 ? rel.getName(0).toString() : ''
                    if (top == 'docs') {
                        docs.addAndGet(size)
                    } else if (top == 'demos') {
                        demos.addAndGet(size)
                    } else {
                        excl.addAndGet(size)
                    }
                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (Exception e) {
            log.warn("system stats disk scan: ${e.message}")
        }

        long inclB = incl.get()
        long exclB = excl.get()
        long docsB = docs.get()
        long demosB = demos.get()
        [
                root               : root.toAbsolutePath().toString(),
                bytesInclDocsDemos : inclB,
                bytesExclDocsDemos : exclB,
                docsBytes          : docsB,
                demosBytes         : demosB,
                humanInclDocsDemos : humanBytes(inclB),
                humanExclDocsDemos : humanBytes(exclB),
                docsHuman          : humanBytes(docsB),
                demosHuman         : humanBytes(demosB),
                skipDirs           : DISK_SKIP_DIR_NAMES as List,
                note               : 'Excludes .git, node_modules, build, dist, .gradle, glowroot, etc. Incl = whole tree; Excl = without docs/ and demos/.'
        ]
    }

    private Path resolveProjectRoot() {
        List<Path> candidates = []
        String userDir = System.getProperty('user.dir')
        if (userDir) candidates << Paths.get(userDir)
        try {
            URL loc = SystemStatsService.class.protectionDomain?.codeSource?.location
            if (loc != null) {
                Path p = Paths.get(loc.toURI()).toAbsolutePath()
                // …/build/classes/groovy/main or similar → walk up
                for (int i = 0; i < 8 && p != null; i++) {
                    candidates << p
                    p = p.parent
                }
            }
        } catch (ignored) { }

        for (Path c : candidates) {
            if (c == null) continue
            Path abs = c.toAbsolutePath().normalize()
            if (looksLikeRepoRoot(abs)) return abs
            // bootRun sometimes uses subdirectory
            Path parent = abs.parent
            if (parent != null && looksLikeRepoRoot(parent)) return parent
        }
        return null
    }

    private static boolean looksLikeRepoRoot(Path p) {
        Files.isDirectory(p.resolve('grails-app')) &&
                (Files.isDirectory(p.resolve('demos')) || Files.isDirectory(p.resolve('docs'))) &&
                Files.isDirectory(p.resolve('frontend'))
    }

    private static String humanBytes(long bytes) {
        if (bytes < 0L) return '—'
        if (bytes < 1024L) return "${bytes} B"
        double kb = bytes / 1024d
        if (kb < 1024d) return String.format(Locale.US, '%.1f KB', kb)
        double mb = kb / 1024d
        if (mb < 1024d) return String.format(Locale.US, '%.1f MB', mb)
        double gb = mb / 1024d
        return String.format(Locale.US, '%.2f GB', gb)
    }

    private int countArtefacts(String type) {
        try {
            GrailsClass[] arr = grailsApplication.getArtefacts(type)
            return arr?.length ?: 0
        } catch (Exception e) {
            log.debug("artefact count ${type}: ${e.message}")
            return 0
        }
    }
}
