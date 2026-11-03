// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.configuration

import groovy.sql.Sql
import groovy.util.logging.Slf4j
import grails.gorm.transactions.Transactional

import javax.sql.DataSource

/**
 * Destructive wipe of application data after a successful DB backup.
 * Keeps login/security seed so the admin session can reach /setup again.
 * System catalog tables (currencies, locales, help, …) are also wiped; BootStrap
 * re-seeds them on the next API restart — wizard still runs with empty catalogs
 * until then for most paths that do not require them mid-flight.
 */
@Slf4j
class DatabaseWipeService {

    DataSource dataSource

    /** Tables required so the current admin can stay authenticated. */
    static final Set<String> KEEP_TABLES = [
            'user',
            'role',
            'user_role',
    ] as Set

    /**
     * Truncate every public table except KEEP_TABLES.
     * @return map: ok, truncated (list), kept (list), skipped (list)
     */
    @Transactional
    Map wipeAllData() {
        Sql sql = new Sql(dataSource)
        List truncated = []
        List skipped = []
        List kept = KEEP_TABLES.toList().sort()
        try {
            List tables = sql.rows(
                    "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
            ).collect { it.tablename as String }

            // Disable FK checks via CASCADE; truncate each table individually so a
            // missing/locked table does not abort the whole wipe.
            tables.each { String table ->
                if (KEEP_TABLES.contains(table)) {
                    return
                }
                try {
                    sql.execute("TRUNCATE TABLE \"${table}\" RESTART IDENTITY CASCADE" as String)
                    truncated << table
                } catch (Exception e) {
                    log.warn('wipe-all: skip table {}: {}', table, e.message)
                    skipped << [table: table, error: e.message]
                }
            }

            // Clear first-level Hibernate session so GORM does not serve stale entities
            try {
                SetupState.withSession { session ->
                    session.clear()
                }
            } catch (ignored) {
            }

            log.warn('wipe-all complete: truncated={} kept={} skipped={}',
                    truncated.size(), kept.size(), skipped.size())

            [
                    ok        : true,
                    truncated : truncated,
                    kept      : kept,
                    skipped   : skipped,
                    tableCount: tables.size(),
            ]
        } finally {
            try { sql.close() } catch (ignored) {}
        }
    }
}
