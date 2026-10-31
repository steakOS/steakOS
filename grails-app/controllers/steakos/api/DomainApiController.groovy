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
import grails.plugin.springsecurity.annotation.Secured
import grails.util.Environment
import groovy.util.logging.Slf4j
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import steakos.api.ListPaging
import steakos.configuration.TenantNpubService

/**
 * Dynamic domain CRUD API (FE-2 / F3).
 * Routes: /api/domains/$package/$resource and /api/domains/$package/$resource/$id
 * Errors return JSON: { code, error, detail?, stackTrace? } for SPA dialogs.
 * B4: tenant domains scoped to active company npub (session via TenantNpubService).
 */
@Slf4j
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class DomainApiController {

    static allowedMethods = [
            index : ['GET'],
            show  : ['GET'],
            save  : ['POST'],
            update: ['PUT', 'POST'],
            delete: ['DELETE', 'POST']
    ]

    DomainRegistryService domainRegistryService
    TenantNpubService tenantNpubService

    /** GET /api/domains — registry listing */
    def index() {
        if (params.package && params.resource) {
            return listResource()
        }
        render(domainRegistryService.listRegistry() as JSON)
    }

    /** GET /api/domains/$package/$resource */
    def listResource() {
        Class domainClass = resolveOr404()
        if (!domainClass) {
            return
        }
        try {
            Map page = ListPaging.pageParams(params, ListPaging.DEFAULT_MAX, ListPaging.HARD_MAX)
            int max = page.max as int
            int offset = page.offset as int
            String npub = activeNpub()
            Map filters = [
                    q        : params.q,
                    status   : params.status,
                    view     : params.view,
                    sort     : params.sort,
                    order    : params.order,
                    npubOwner: npub,
            ]
            // L3 — parent FK for nested line lists (quoteId, salesOrderId, …)
            params.each { k, v ->
                String key = k?.toString()
                if (key?.endsWith('Id') && key != 'id' && v != null && v.toString().trim()) {
                    filters[key] = v
                }
            }
            Map result = domainRegistryService.listPage(domainClass, max, offset, filters)
            List rows = result.items as List
            int total = result.total as int
            if (offset > 0 && total > 0 && rows.isEmpty()) {
                // clamp past-end page
                offset = Math.max(0, ((int) Math.floor((total - 1) / (double) max)) * max)
                result = domainRegistryService.listPage(domainClass, max, offset, filters)
                rows = result.items as List
                total = result.total as int
            }
            String appliedSort = (result.sort ?: 'id') as String
            String appliedOrder = (result.order ?: 'desc') as String
            render(ListPaging.envelope(
                    rows.collect { domainRegistryService.toMap(it) },
                    total,
                    max,
                    offset,
                    [package: params.package, resource: params.resource,
                     sort: appliedSort, order: appliedOrder,
                     q: params.q, status: params.status, view: params.view]
            ) as JSON)
        } catch (IllegalArgumentException e) {
            renderApiError(400, 'INVALID_SORT', e)
        } catch (Exception e) {
            renderApiError(500, 'DOMAIN_LIST_FAILED', e)
        }
    }

    /** GET /api/domains/$package/$resource/$id */
    def show() {
        Class domainClass = resolveOr404()
        if (!domainClass) {
            return
        }
        try {
            Long id = params.long('id')
            Object row = domainRegistryService.get(domainClass, id, activeNpub())
            if (!row) {
                render(status: 404, text: ([code: 'NOT_FOUND', error: 'Not found'] as JSON))
                return
            }
            render(domainRegistryService.toMap(row) as JSON)
        } catch (Exception e) {
            renderApiError(500, 'DOMAIN_SHOW_FAILED', e)
        }
    }

    /** GET /api/domains/$package/$resource/schema — field metadata for schema-driven forms (P2) */
    def schema() {
        Class domainClass = resolveOr404()
        if (!domainClass) {
            return
        }
        try {
            render(domainRegistryService.schemaFor(domainClass, params.package as String, params.resource as String) as JSON)
        } catch (Exception e) {
            renderApiError(500, 'DOMAIN_SCHEMA_FAILED', e)
        }
    }

    /** POST /api/domains/$package/$resource */
    def save() {
        Class domainClass = resolveOr404()
        if (!domainClass) {
            return
        }
        try {
            Map body = request.JSON as Map ?: [:]
            Object row = domainRegistryService.create(domainClass, body, activeNpub())
            render(status: 201, text: (domainRegistryService.toMap(row) as JSON))
        } catch (IllegalArgumentException e) {
            renderApiError(422, 'VALIDATION_ERROR', e)
        } catch (Exception e) {
            renderApiError(400, 'DOMAIN_CREATE_FAILED', e)
        }
    }

    /** PUT /api/domains/$package/$resource/$id */
    def update() {
        Class domainClass = resolveOr404()
        if (!domainClass) {
            return
        }
        try {
            Map body = request.JSON as Map ?: [:]
            Object row = domainRegistryService.update(domainClass, params.long('id'), body, activeNpub())
            if (!row) {
                render(status: 404, text: ([code: 'NOT_FOUND', error: 'Not found'] as JSON))
                return
            }
            render(domainRegistryService.toMap(row) as JSON)
        } catch (steakos.document.DocumentChainLockedException e) {
            renderApiError(409, steakos.document.DocumentChainLockedException.CODE, e, e.reasons)
        } catch (IllegalArgumentException e) {
            renderApiError(422, 'VALIDATION_ERROR', e)
        } catch (IllegalStateException e) {
            renderApiError(409, 'CONSTRAINT_VIOLATION', e)
        } catch (Exception e) {
            renderApiError(400, 'DOMAIN_UPDATE_FAILED', e)
        }
    }

    /** DELETE /api/domains/$package/$resource/$id */
    def delete() {
        Class domainClass = resolveOr404()
        if (!domainClass) {
            return
        }
        try {
            boolean ok = domainRegistryService.delete(domainClass, params.long('id'), activeNpub())
            if (!ok) {
                render(status: 404, text: ([code: 'NOT_FOUND', error: 'Not found'] as JSON))
                return
            }
            render(status: 204, text: '')
        } catch (steakos.document.DocumentChainLockedException e) {
            renderApiError(409, steakos.document.DocumentChainLockedException.CODE, e, e.reasons)
        } catch (IllegalStateException | IllegalArgumentException e) {
            renderApiError(409, 'CONSTRAINT_VIOLATION', e)
        } catch (DataIntegrityViolationException | ConstraintViolationException e) {
            renderApiError(409, 'CONSTRAINT_VIOLATION', e)
        } catch (Exception e) {
            // unwrap Spring nested cause (FK etc.)
            Throwable root = rootCause(e)
            if (root instanceof DataIntegrityViolationException || root instanceof ConstraintViolationException
                    || (root?.message && root.message.toLowerCase().contains('foreign key'))) {
                renderApiError(409, 'CONSTRAINT_VIOLATION', e)
            } else {
                renderApiError(500, 'DOMAIN_DELETE_FAILED', e)
            }
        }
    }

    /** Active company npub from session — never honor body owner for scoping (B4). */
    private String activeNpub() {
        tenantNpubService.resolve(session)
    }

    private Class resolveOr404() {
        Class domainClass = domainRegistryService.resolve(params.package as String, params.resource as String)
        if (!domainClass || !domainRegistryService.isAllowed(domainClass)) {
            render(status: 404, text: ([
                    code : 'UNKNOWN_DOMAIN',
                    error: "Unknown domain ${params.package}/${params.resource}"
            ] as JSON))
            return null
        }
        return domainClass
    }

    private void renderApiError(int status, String code, Throwable e, List reasons = null) {
        Throwable root = rootCause(e)
        String message = friendlyMessage(code, root)
        String detail = root?.message ?: e?.message
        log.warn('Domain API {} {}: {}', code, status, detail)
        Map body = [
                code  : code,
                error : message,
                detail: detail
        ]
        if (reasons) {
            body.reasons = reasons
            body.unlockHint = reasons.collect { it.unlockHint ?: it.message }.join(' · ')
        }
        if (Environment.current == Environment.DEVELOPMENT) {
            body.stackTrace = stackString(e)
        }
        response.status = status
        render(contentType: 'application/json', text: (body as JSON).toString())
    }

    private static String friendlyMessage(String code, Throwable root) {
        String raw = root?.message ?: ''
        if (code == steakos.document.DocumentChainLockedException.CODE) {
            return raw ?: 'This record is locked by a downstream document. Roll back that document first.'
        }
        if (code == 'CONSTRAINT_VIOLATION' || raw.toLowerCase().contains('foreign key')
                || raw.toLowerCase().contains('still referenced')) {
            String table = null
            def m = (raw =~ /table "([^"]+)"/)
            if (m.find()) {
                // last table in message is often the referencing table
                def all = (raw =~ /table "([^"]+)"/)
                String last = null
                while (all.find()) {
                    last = all.group(1)
                }
                table = last
            }
            if (table) {
                return "Cannot delete: still referenced by ${table.replace('_', ' ')}. Remove those records first."
            }
            return 'Cannot delete: this record is still referenced by other data.'
        }
        if (code == 'VALIDATION_ERROR') {
            return root?.message ?: 'Validation failed'
        }
        return root?.message ?: 'Request failed'
    }

    private static Throwable rootCause(Throwable e) {
        Throwable t = e
        while (t?.cause != null && t.cause != t) {
            t = t.cause
        }
        return t ?: e
    }

    private static String stackString(Throwable e) {
        if (!e) return null
        StringWriter sw = new StringWriter()
        e.printStackTrace(new PrintWriter(sw))
        String s = sw.toString()
        return s.length() > 12000 ? s.substring(0, 12000) + '\n…' : s
    }
}
