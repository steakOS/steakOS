// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.ops

import grails.gorm.transactions.Transactional
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import steakos.configuration.TemplateTiers
import steakos.version.ProductVersionService

import javax.sql.DataSource
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * OBS-01 — liveness / readiness checks for Uptime Kuma + Ops tile (concept §8.1).
 * Public liveness must stay free of secrets and PII.
 */
@Slf4j
@Transactional(readOnly = true)
class OpsHealthService {

    DataSource dataSource
    ProductVersionService productVersionService
    def grailsApplication

    /** Liveness: process is up. No dependency waits. */
    Map liveness() {
        [
            status : 'UP',
            version: versionStamp(),
            tier   : configuredTier(),
            checks : [
                process: [status: 'UP', uptimeMs: ManagementFactoryUptime.uptimeMs()],
            ],
        ]
    }

    /**
     * Readiness: Postgres + disk; optional stubs for BTCPay / Nostr / backup.
     * Overall DOWN if postgres or disk critical.
     */
    Map readiness() {
        Map checks = [:]
        checks.postgres = checkPostgres()
        checks.disk = checkDisk()
        checks.migrations = [status: 'UP', note: 'Flyway/GORM assumed applied at boot']
        checks.btcpay = stubDep('btcpay', 'steakos.observability.health.btcpay')
        checks.nostrRelay = stubDep('nostrRelay', 'steakos.observability.health.nostrRelay')
        checks.backup = stubBackup()

        boolean down = ['postgres', 'disk'].any { k -> checks[k]?.status == 'DOWN' }
        boolean degraded = !down && checks.values().any { it?.status in ['DOWN', 'DEGRADED', 'UNKNOWN'] && it?.critical }

        [
            status : down ? 'DOWN' : (degraded ? 'DEGRADED' : 'UP'),
            version: versionStamp(),
            tier   : configuredTier(),
            checks : checks,
        ]
    }

    private Map checkPostgres() {
        long t0 = System.nanoTime()
        Sql sql = null
        try {
            sql = new Sql(dataSource)
            sql.firstRow('SELECT 1 AS ok')
            long ms = (System.nanoTime() - t0) / 1_000_000L
            return [status: 'UP', latencyMs: ms]
        } catch (Exception e) {
            log.warn('health postgres DOWN: {}', e.message)
            return [status: 'DOWN', error: 'connection failed', latencyMs: (System.nanoTime() - t0) / 1_000_000L]
        } finally {
            try { sql?.close() } catch (ignored) {}
        }
    }

    private Map checkDisk() {
        try {
            Path path = Paths.get(System.getProperty('user.dir', '/'))
            FileStore store = Files.getFileStore(path)
            long free = store.getUsableSpace()
            long total = store.getTotalSpace()
            // Critical under 512 MiB free
            boolean down = free < 512L * 1024L * 1024L
            boolean degraded = free < 2L * 1024L * 1024L * 1024L
            [
                status   : down ? 'DOWN' : (degraded ? 'DEGRADED' : 'UP'),
                freeBytes: free,
                totalBytes: total,
                path     : path.toAbsolutePath().toString(),
            ]
        } catch (Exception e) {
            [status: 'UNKNOWN', error: e.message]
        }
    }

    private Map stubDep(String name, String configKey) {
        String url = configString(configKey, '')
        if (!url) {
            return [status: 'UNKNOWN', note: "not configured (${name})"]
        }
        // Phase 1: do not probe outbound by default (avoid hanging readiness). Phase 3 wires real probes.
        return [status: 'UNKNOWN', note: 'configured; probe deferred to Phase 3', urlHost: hostOnly(url)]
    }

    private Map stubBackup() {
        String marker = configString('steakos.observability.health.backupMarker', '')
        if (!marker) {
            return [status: 'UNKNOWN', note: 'backup marker path not configured']
        }
        try {
            Path p = Paths.get(marker)
            if (!Files.exists(p)) {
                return [status: 'DOWN', error: 'marker missing', path: marker]
            }
            long mtime = Files.getLastModifiedTime(p).toMillis()
            return [
                status        : 'UP',
                lastSuccessAt : java.time.Instant.ofEpochMilli(mtime).toString(),
            ]
        } catch (Exception e) {
            return [status: 'UNKNOWN', error: e.message]
        }
    }

    private String versionStamp() {
        try {
            def info = productVersionService?.current()
            return info?.semVerLabel ?: info?.softwareVersion ?: 'unknown'
        } catch (Exception e) {
            return 'unknown'
        }
    }

    private String configuredTier() {
        try {
            String t = configString('steakos.template.tier', '')
            if (t) return TemplateTiers.normalize(t)
        } catch (ignored) {}
        return TemplateTiers.DEFAULT_OPERATOR_TIER ?: 'BRONZE'
    }

    private String configString(String key, String defVal) {
        try {
            def cfg = grailsApplication?.config
            if (!cfg) return defVal
            // Flattened key via config.getProperty when available
            if (cfg.respondsTo('getProperty')) {
                String v = cfg.getProperty(key, String, null)
                if (v != null && v.trim()) return v.trim()
            }
        } catch (ignored) {}
        return defVal
    }

    private static String hostOnly(String url) {
        try {
            URI u = URI.create(url)
            return u.host ?: 'configured'
        } catch (ignored) {
            return 'configured'
        }
    }
}

/** Tiny helper so OpsHealthService does not import java.lang.management at class init issues. */
class ManagementFactoryUptime {
    static long uptimeMs() {
        try {
            return java.lang.management.ManagementFactory.runtimeMXBean.uptime
        } catch (ignored) {
            return -1L
        }
    }
}
