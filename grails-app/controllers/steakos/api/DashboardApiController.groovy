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

package steakos.api

import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import steakos.configuration.TenantNpubService

/**
 * CEO dashboard aggregate API (FE-3 / F4–F5). Counts-only v1.
 * B4: tenant counts / tasks scoped to active company npub.
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class DashboardApiController {

    static allowedMethods = [ceo: 'GET']

    DomainRegistryService domainRegistryService
    SpringSecurityService springSecurityService
    TenantNpubService tenantNpubService

    def ceo() {
        String username = springSecurityService?.principal?.username ?: 'user'
        Collection authorities = springSecurityService?.authentication?.authorities*.authority ?: []
        boolean isCeo = authorities?.contains('ROLE_CEO')
        boolean isAdmin = authorities?.contains('ROLE_ADMIN')
        // Prefer CEO label only when CEO; otherwise Admin / first desk role
        String displayRole
        if (isCeo) {
            displayRole = 'CEO'
        } else if (isAdmin) {
            displayRole = 'Admin'
        } else if (authorities) {
            String raw = (authorities.find { it != 'ROLE_USER' && it != 'ROLE_AI' } ?: authorities[0]) as String
            displayRole = raw?.replaceFirst(/^ROLE_/, '')?.toLowerCase()?.capitalize() ?: 'User'
        } else {
            displayRole = 'User'
        }

        Map myWork = buildMyWork()
        List apqcGroups = buildApqcGroups()
        List actionQueue = buildActionQueue(myWork, apqcGroups)

        int contentTypes = safeCount('generic', 'ContentType')
        int locales = safeCount('helper', 'SupportedLocale')

        render([
                user       : [
                        username   : username,
                        displayRole: displayRole,
                        isAdmin    : isAdmin,
                        isCeo      : isCeo,
                        authorities: authorities as List
                ],
                myWork     : myWork,
                apqcGroups : apqcGroups,
                actionQueue: actionQueue,
                health     : [
                        contentTypes: contentTypes,
                        locales     : locales,
                        api         : 'ok'
                ]
        ] as JSON)
    }

    private Map buildMyWork() {
        Class taskClass = domainRegistryService.resolve('core', 'Task')

        List taskItems = []
        int taskOpen = 0
        int taskOverdue = 0
        // 3×3 matrix counts: urgency 1–3 × importance 1–3 (open tasks only)
        Map<String, Integer> matrixCounts = [:]
        (1..3).each { int u ->
            (1..3).each { int i ->
                matrixCounts["${u}-${i}"] = 0
            }
        }
        if (taskClass) {
            try {
                // Own transaction so a schema lag (e.g. new Task columns) cannot 500 the whole CEO dashboard.
                taskClass.withNewTransaction(readOnly: true) {
                    String npub = tenantNpubService.resolve(session)
                    List all = domainRegistryService.listAll(taskClass, 200, 0, [npubOwner: npub])
                    Date now = new Date()
                    List openRows = all.findAll { Object t ->
                        String st = t.hasProperty('status') ? (t['status'] as String) : null
                        !(st in ['DONE', 'CANCELLED'])
                    }
                    taskOpen = openRows.size()
                    taskOverdue = openRows.count { Object t ->
                        Date dl = t.hasProperty('deadlineAt') ? (t['deadlineAt'] as Date) : null
                        dl != null && dl.before(now)
                    } as int
                    openRows.each { Object t ->
                        int u = clampEisenhower(t.hasProperty('urgency') ? t['urgency'] : null)
                        int i = clampEisenhower(t.hasProperty('importance') ? t['importance'] : null)
                        String key = "${u}-${i}"
                        matrixCounts[key] = (matrixCounts[key] ?: 0) + 1
                    }
                    taskItems = openRows.take(5).collect { domainRegistryService.toMap(it) }
                }
            } catch (Exception e) {
                log.warn("Dashboard myWork tasks failed: {}", e.message)
            }
        }

        List matrix = []
        // Rows urgency 3→1 (top = most urgent); cols importance 1→3
        [3, 2, 1].each { int u ->
            [1, 2, 3].each { int i ->
                matrix << [
                        urgency   : u,
                        importance: i,
                        count     : matrixCounts["${u}-${i}"] ?: 0
                ]
            }
        }

        return [
                tasks     : [open: taskOpen, overdue: taskOverdue, items: taskItems],
                eisenhower: [cells: matrix]
        ]
    }

    private static int clampEisenhower(Object raw) {
        try {
            int v = raw != null ? raw as int : 2
            if (v < 1) return 1
            if (v > 3) return 3
            return v
        } catch (ignored) {
            return 2
        }
    }

    private List buildApqcGroups() {
        List defs = [
                [id: 'sales', label: 'Sales & Marketing', package: 'sales',
                 metrics: ['Customer', 'CustomerQuote', 'SalesOrder'],
                 actions: [[label: 'New Quote', route: '/domains/sales/CustomerQuote/new'],
                           [label: 'Customers', route: '/domains/sales/Customer']]],
                [id: 'procurement', label: 'Procurement', package: 'procurement',
                 metrics: ['Vendor', 'PurchaseOrder', 'Receipt'],
                 actions: [[label: 'New PO', route: '/domains/procurement/PurchaseOrder/new'],
                           [label: 'Vendors', route: '/domains/procurement/Vendor']]],
                [id: 'production', label: 'Production', package: 'production',
                 metrics: ['ProductionOrder', 'BillOfMaterials', 'Check'],
                 actions: [[label: 'New Production Order', route: '/domains/production/ProductionOrder/new']]],
                [id: 'delivery', label: 'Service Delivery', package: 'delivery',
                 metrics: ['ServiceOrder', 'Resource'],
                 actions: [[label: 'New Service Order', route: '/domains/delivery/ServiceOrder/new']]],
                [id: 'service', label: 'Customer Service', package: 'service',
                 metrics: ['Inquiry', 'Claims', 'Warranty'],
                 actions: [[label: 'New Inquiry', route: '/domains/service/Inquiry/new']]],
                [id: 'human', label: 'Human Capital', package: 'human',
                 metrics: ['Candidate', 'Goal', 'Payroll'],
                 actions: [[label: 'Candidates', route: '/domains/human/Candidate']]],
                [id: 'digital', label: 'Platform & IT', package: 'digital',
                 metrics: ['Solution', 'ITChange', 'System'],
                 actions: [[label: 'IT Changes', route: '/domains/digital/ITChange']]],
                [id: 'finance', label: 'Financial Resources', package: 'finance',
                 metrics: ['Invoice', 'Payable', 'Treasury', 'Account'],
                 actions: [[label: 'New Invoice', route: '/domains/finance/Invoice/new'],
                           [label: 'Payables', route: '/domains/finance/Payable']]],
                [id: 'capability', label: 'Business Capabilities', package: 'capability',
                 metrics: ['Project', 'Process', 'Change'],
                 actions: [[label: 'Projects', route: '/domains/capability/Project']]],
                [id: 'agents', label: 'Agentic Workforce', package: 'agents',
                 metrics: ['Agent', 'Hive', 'Approval'],
                 actions: [[label: 'Approvals', route: '/domains/agents/Approval'],
                           [label: 'Agents', route: '/domains/agents/Agent']]],
        ]

        defs.collect { Map g ->
            String pkg = g.package as String
            List metrics = (g.metrics as List).collect { String res ->
                [
                        key  : res,
                        label: res,
                        value: safeCount(pkg, res)
                ]
            }
            int total = metrics.sum { (it as Map).value as int } as int
            [
                    id     : g.id,
                    label  : g.label,
                    package: pkg,
                    total  : total,
                    metrics: metrics,
                    actions: g.actions
            ]
        }
    }

    private List buildActionQueue(Map myWork, List apqcGroups) {
        List queue = []
        int tasks = (myWork.tasks as Map)?.open as int ?: 0
        if (tasks > 0) {
            queue << [
                    id      : 'tasks-open',
                    label   : "${tasks} open task(s)",
                    domain  : 'core',
                    priority: 1,
                    route   : '/work'
            ]
        }
        apqcGroups.each { Map g ->
            int total = g.total as int
            if (total > 0) {
                List actions = g.actions as List
                Map first = actions ? actions[0] as Map : null
                queue << [
                        id      : "group-${g.id}",
                        label   : "${g.label}: ${total} record(s)",
                        domain  : g.package,
                        priority: 5,
                        route   : first?.route ?: "/domains/${g.package}"
                ]
            }
        }
        if (!queue) {
            queue << [
                    id      : 'bootstrap',
                    label   : 'Create your first Customer or Revenue Item',
                    domain  : 'finance',
                    priority: 9,
                    route   : '/domains/finance/Invoice/new'
            ]
        }
        return queue.take(7)
    }

    /**
     * Each count runs in its own transaction so one bad table name
     * (e.g. reserved SQL word) cannot abort the rest of the dashboard.
     */
    private int safeCount(String pkg, String resource) {
        try {
            Class clazz = domainRegistryService.resolve(pkg, resource)
            if (!clazz) {
                log.warn("Dashboard count: unknown domain {}/{}", pkg, resource)
                return 0
            }
            Integer n = clazz.withNewTransaction(readOnly: true) {
                domainRegistryService.count(clazz, tenantNpubService.resolve(session))
            } as Integer
            return n ?: 0
        } catch (Exception e) {
            log.warn("Dashboard count failed for {}/{}: {}", pkg, resource, e.message)
            return 0
        }
    }
}
