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

package steakos.e2e.support

import groovy.sql.Sql

import javax.sql.DataSource

/**
 * D5: truncate business tables + leave security seed for re-bootstrap.
 * Call from integration specs against steakos_test only.
 */
class E2eSeedSupport {

    private final DataSource dataSource

    E2eSeedSupport(DataSource dataSource) {
        this.dataSource = dataSource
    }

    /**
     * Wipe operational data so tests do not depend on steakos_dev.
     * Security users/roles recreated by BootStrap on cold start; after truncate
     * we only clear business tables and keep users if possible.
     */
    void truncateBusinessData() {
        Sql sql = new Sql(dataSource)
        try {
            // CASCADE drops dependent rows; order not critical
            String[] tables = [
                    'stock_move', 'receipt_line', 'receipt', 'purchase_order_line', 'purchase_order',
                    'order_line', 'sales_order', 'sales_order_dynamic', 'quote_line', 'customer_quote',
                    'customer_quote_dynamic', 'customer', 'vendor',
                    'service_order_line', 'service_order', 'service_order_dynamic', 'resource',
                    'bom_item', 'bill_of_materials', 'production_order', 'lot', 'component', 'quality_check',
                    'inquiry', 'claims', 'warranty', 'rma', 'service_level', 'satisfaction',
                    'invoice', 'invoice_dynamic', 'payment', 'payment_dynamic', 'payment_option',
                    'payable', 'payable_dynamic', 'journal_line', 'journal_entry', 'budget', 'payroll_item',
                    'payroll', 'tax', 'treasury', 'treasury_dynamic', 'account',
                    'candidate', 'hrfile', 'time_track', 'goal', 'headcount_plan', 'party_access', 'policy',
                    'it_ticket', 'itchange', 'solution', 'solution_dynamic', 'config_item', 'data',
                    'platform_system', 'system_dynamic',
                    'project_item', 'project', 'program', 'portfolio', 'process_step', 'business_process',
                    'quality', 'quality_item', 'change', 'change_item', 'change_request',
                    'knowledge_area', 'standard', 'procedure',
                    'approval', 'agent_memory', 'agent_human_link', 'agent', 'hive', 'memory',
                    'agent_api_token',
                    'object_content', 'content_blob', 'content', 'import_job',
                    'catalogue_item',
                    'task'
            ]
            tables.each { t ->
                try {
                    sql.execute("TRUNCATE TABLE ${t} RESTART IDENTITY CASCADE" as String)
                } catch (Exception ignored) {
                    // table may not exist yet on first schema create
                }
            }
        } finally {
            sql.close()
        }
    }

    int countTable(String table) {
        Sql sql = new Sql(dataSource)
        try {
            return (sql.firstRow("SELECT count(*) AS c FROM ${table}" as String)?.c ?: 0) as int
        } catch (Exception e) {
            return -1
        } finally {
            sql.close()
        }
    }

    boolean rowExists(String table, String whereSql, List params = []) {
        Sql sql = new Sql(dataSource)
        try {
            def row = sql.firstRow("SELECT 1 AS x FROM ${table} WHERE ${whereSql} LIMIT 1" as String, params)
            return row != null
        } catch (Exception e) {
            return false
        } finally {
            sql.close()
        }
    }
}
