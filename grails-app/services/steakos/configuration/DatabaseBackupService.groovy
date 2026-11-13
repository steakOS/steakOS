// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.configuration

import groovy.util.logging.Slf4j
import grails.gorm.transactions.Transactional

import javax.sql.DataSource
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Operator DB backup before destructive admin reset (wipe-all).
 * Writes pg_dump -Fc into {@code var/backups/db/} under the app root.
 */
@Slf4j
@Transactional(readOnly = true)
class DatabaseBackupService {

    DataSource dataSource
    def grailsApplication

    static final String DEFAULT_REL_DIR = 'var/backups/db'

    /**
     * @return map: ok, path, fileName, bytes, database, createdAt
     */
    Map backupBeforeWipe(String reason = 'admin-wipe-all') {
        File dir = backupDir()
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create backup directory: ${dir.absolutePath}")
        }
        if (!dir.canWrite()) {
            throw new IllegalStateException("Backup directory not writable: ${dir.absolutePath}")
        }

        Map jdbc = jdbcParts()
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now())
        String fileName = "steakos-${jdbc.database}-${stamp}.dump"
        File out = new File(dir, fileName)

        String pgDump = resolvePgDump()
        List<String> cmd = [
                pgDump,
                '-h', jdbc.host as String,
                '-p', String.valueOf(jdbc.port),
                '-U', jdbc.user as String,
                '-Fc',
                '-f', out.absolutePath,
                jdbc.database as String,
        ]

        ProcessBuilder pb = new ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        pb.environment().put('PGPASSWORD', jdbc.password as String)
        Process proc = pb.start()
        String output = proc.inputStream.getText('UTF-8')
        int code = proc.waitFor()
        if (code != 0 || !out.isFile() || out.length() < 1) {
            out.delete()
            throw new IllegalStateException(
                    "pg_dump failed (exit ${code}) before wipe — aborting. ${output?.take(500)}",
            )
        }

        log.warn('DB backup written before wipe: {} ({} bytes, reason={})',
                out.absolutePath, out.length(), reason)

        [
                ok        : true,
                path      : out.absolutePath,
                fileName  : fileName,
                bytes     : out.length(),
                database  : jdbc.database,
                createdAt : Instant.now().toString(),
                reason    : reason,
        ]
    }

    File backupDir() {
        String configured = configString('steakos.backup.dir', '')
        if (configured?.trim()) {
            return new File(configured.trim()).canonicalFile
        }
        new File(repoRoot(), DEFAULT_REL_DIR).canonicalFile
    }

    private File repoRoot() {
        String env = System.getenv('STEAKOS_ROOT')
        if (env?.trim()) return new File(env.trim()).canonicalFile
        new File(System.getProperty('user.dir') ?: '.').canonicalFile
    }

    private String resolvePgDump() {
        String configured = configString('steakos.backup.pgDump', '')
        if (configured?.trim()) {
            File f = new File(configured.trim())
            if (f.isFile() && f.canExecute()) return f.absolutePath
        }
        String which = 'pg_dump'
        // Prefer PATH resolution
        try {
            Process p = new ProcessBuilder('which', 'pg_dump').start()
            String path = p.inputStream.getText('UTF-8')?.trim()
            if (p.waitFor() == 0 && path) return path
        } catch (ignored) {
        }
        which
    }

    private Map jdbcParts() {
        String url = ''
        String user = System.getenv('STEAKOS_DB_USER') ?: 'steakos'
        String password = System.getenv('STEAKOS_DB_PASSWORD') ?: ''
        String host = System.getenv('STEAKOS_DB_HOST') ?: 'localhost'
        int port = (System.getenv('STEAKOS_DB_PORT') ?: '5432') as int
        String envName = System.getProperty('grails.env') ?: System.getenv('GRAILS_ENV') ?: 'development'
        String database = System.getenv('STEAKOS_DB_NAME') ?:
                (envName in ['production', 'prod'] ? 'steakos_prod' : 'steakos_dev')

        try {
            def cfg = grailsApplication?.config
            def ds = cfg?.getProperty('dataSource') ?: cfg?.dataSource
            if (ds != null) {
                url = (ds.url ?: ds.getAt?.('url') ?: url) as String
                if (ds.username) user = ds.username as String
                if (ds.password != null) password = ds.password as String
            }
        } catch (ignored) {
        }

        if (!url && dataSource) {
            def conn = null
            try {
                conn = dataSource.connection
                url = conn?.metaData?.URL ?: ''
                if (conn?.metaData?.userName) user = conn.metaData.userName
            } catch (ignored) {
            } finally {
                try { conn?.close() } catch (ignored2) {}
            }
        }

        def m = (url =~ /jdbc:postgresql:\/\/([^:\/]+)(?::(\d+))?\/([^?\s]+)/)
        if (m.find()) {
            host = m.group(1) ?: host
            if (m.group(2)) port = m.group(2) as int
            if (m.group(3)) database = m.group(3)
        }

        [host: host, port: port, database: database, user: user, password: password, url: url]
    }

    private String configString(String path, String fallback) {
        try {
            def cfg = grailsApplication?.config
            def v = cfg?.getProperty?.(path)
            if (v == null) {
                v = cfg
                path.split(/\./).each { part -> v = v?."$part" }
            }
            return (v != null && v.toString().trim()) ? v.toString().trim() : fallback
        } catch (ignored) {
            return fallback
        }
    }
}
