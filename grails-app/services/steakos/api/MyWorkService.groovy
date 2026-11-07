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

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.User
import steakos.core.Task
import steakos.traits.Assignable

/**
 * My Work surfaces (ADR-056):
 * <ul>
 *   <li><b>Assignments</b> — any Assignable (or Task) row where the actor is the human
 *       assignee and status is not DONE / VOID / CANCELLED.</li>
 *   <li><b>Own Tasks</b> — Task rows where the actor is the human assignee, or there is
 *       no assignee and the actor is the npub owner; status not DONE / VOID / CANCELLED.
 *       Agentic mode lists AI:* Task statuses instead.</li>
 * </ul>
 */
@Slf4j
@Transactional(readOnly = true)
class MyWorkService {

    static final Set<String> TERMINAL_STATUSES =
            (['DONE', 'VOID', 'CANCELLED'] as Set).asImmutable()

    DomainRegistryService domainRegistryService

    /**
     * Human identity tokens used on assigneeHumanNpub / npubOwner
     * ({@code user:username} and optional raw npub).
     */
    static List<String> humanIdentities(User user) {
        if (!user) {
            return Collections.emptyList()
        }
        List<String> out = []
        String username = user.username?.trim()
        if (username) {
            out << "user:${username}"
        }
        String npub = user.npub?.trim()
        if (npub) {
            out << npub
        }
        return out.unique(false) as List<String>
    }

    static boolean isTerminalStatus(String status) {
        status != null && TERMINAL_STATUSES.contains(status.trim().toUpperCase())
    }

    /**
     * Cross-domain Assignments for the current user.
     * @param tenantNpub optional company scope (NpubOwned rows)
     */
    Map listAssignments(User user, String tenantNpub, int max = 50) {
        List<String> identities = humanIdentities(user)
        if (!identities) {
            return [items: [], total: 0, max: max]
        }
        int limit = Math.min(Math.max(max, 1), 200)
        List<Map> items = []
        List<Map> registry = domainRegistryService.listRegistry() as List<Map>
        for (Map entry : registry) {
            if (items.size() >= limit) {
                break
            }
            Class clazz = domainRegistryService.resolve(
                    entry.package as String, entry.resource as String)
            if (!clazz || !isAssignmentDomain(clazz)) {
                continue
            }
            int remaining = limit - items.size()
            try {
                List rows = queryAssignedOpen(clazz, identities, tenantNpub, remaining)
                String pkg = entry.package as String
                String res = entry.resource as String
                for (Object row : rows) {
                    items << assignmentRow(pkg, res, row)
                }
            } catch (Exception e) {
                log.debug("Assignments skip {}/{}: {}", entry.package, entry.resource, e.message)
            }
        }
        // Task may not appear as Assignable in the registry scan if trait not implemented
        if (items.size() < limit) {
            boolean hasTask = items.any { (it.package == 'core' && it.resource == 'Task') }
            if (!hasTask) {
                try {
                    int remaining = limit - items.size()
                    List rows = queryAssignedOpen(Task, identities, tenantNpub, remaining)
                    for (Object row : rows) {
                        String path = "/domains/core/Task/${row['id']}"
                        if (!items.any { it.path == path }) {
                            items << assignmentRow('core', 'Task', row)
                        }
                    }
                } catch (Exception e) {
                    log.debug("Assignments Task scan: {}", e.message)
                }
            }
        }
        items.sort { Map a, Map b ->
            String an = (a.name ?: '') as String
            String bn = (b.name ?: '') as String
            an.toLowerCase() <=> bn.toLowerCase()
        }
        [items: items, total: items.size(), max: limit]
    }

    /**
     * Own Tasks board rows (human) or agentic AI:* queue.
     * @param mode {@code human} (default) or {@code agentic}
     */
    Map listOwnTasks(User user, String tenantNpub, String mode, int max = 500, int offset = 0) {
        String m = (mode ?: 'human').trim().toLowerCase()
        int limit = Math.min(Math.max(max, 1), 500)
        int off = Math.max(offset, 0)
        List<String> identities = humanIdentities(user)

        List items = Task.createCriteria().list(max: limit, offset: off) {
            applyOwnTaskFilters(delegate, identities, tenantNpub, m)
            order('deadlineAt', 'asc')
            order('id', 'desc')
        } as List
        Number total = Task.createCriteria().count {
            applyOwnTaskFilters(delegate, identities, tenantNpub, m)
        } as Number

        [
                items : items.collect { domainRegistryService.toMap(it) },
                total : total?.intValue() ?: 0,
                max   : limit,
                offset: off,
                mode  : m
        ]
    }

    private static boolean isAssignmentDomain(Class clazz) {
        if (!clazz) {
            return false
        }
        if (Assignable.isAssignableFrom(clazz)) {
            return true
        }
        if (clazz == Task) {
            return true
        }
        try {
            return clazz.metaClass.getMetaProperty('assigneeHumanNpub') != null
        } catch (ignored) {
            return false
        }
    }

    private static boolean hasProperty(Class clazz, String name) {
        try {
            return clazz.metaClass.getMetaProperty(name) != null
        } catch (ignored) {
            return false
        }
    }

    private List queryAssignedOpen(Class clazz, List<String> identities,
                                   String tenantNpub, int max) {
        boolean scoped = domainRegistryService.isTenantScoped(clazz) && tenantNpub?.trim()
        boolean hasStatus = hasProperty(clazz, 'status')
        clazz.createCriteria().list(max: max, offset: 0) {
            'in'('assigneeHumanNpub', identities)
            if (hasStatus) {
                or {
                    isNull('status')
                    eq('status', '')
                    not {
                        'in'('status', TERMINAL_STATUSES as List)
                    }
                }
            }
            if (scoped) {
                eq('npubOwner', tenantNpub.trim())
            }
            order('id', 'desc')
        } as List
    }

    private static void applyOwnTaskFilters(Object c, List<String> identities,
                                            String tenantNpub, String mode) {
        if (tenantNpub?.trim()) {
            c.eq('npubOwner', tenantNpub.trim())
        }
        if (mode == 'agentic') {
            c.in('status', steakos.configuration.DevelopmentModeService.AI_TASK_STATUSES)
            return
        }
        // human Own Tasks
        c.or {
            isNull('status')
            eq('status', '')
            not {
                'in'('status', TERMINAL_STATUSES as List)
            }
        }
        if (!identities) {
            // no identity → empty result
            c.eq('id', -1L)
            return
        }
        c.or {
            'in'('assigneeHumanNpub', identities)
            and {
                or {
                    isNull('assigneeHumanNpub')
                    eq('assigneeHumanNpub', '')
                }
                isNull('assigneeAgentId')
                'in'('npubOwner', identities)
            }
        }
    }

    private Map assignmentRow(String pkg, String res, Object row) {
        Long id = row.hasProperty('id') ? (row['id'] as Long) : null
        String name = null
        if (row.hasProperty('name') && row['name']) {
            name = row['name'] as String
        } else if (row.hasProperty('title') && row['title']) {
            name = row['title'] as String
        } else {
            name = "${res} #${id}"
        }
        String status = row.hasProperty('status') ? (row['status'] as String) : null
        [
                package           : pkg,
                resource          : res,
                id                : id,
                name              : name,
                status            : status,
                assigneeHumanNpub : row.hasProperty('assigneeHumanNpub') ? row['assigneeHumanNpub'] : null,
                assigneeAgentId   : row.hasProperty('assigneeAgentId') ? row['assigneeAgentId'] : null,
                path              : "/domains/${pkg}/${res}/${id}",
                class             : row.getClass().name
        ]
    }
}
