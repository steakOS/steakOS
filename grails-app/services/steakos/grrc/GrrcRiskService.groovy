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

package steakos.grrc

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import steakos.events.DomainEventBusService
import steakos.events.DomainEventNames

/**
 * GRRCBasics risk register CRUD (G7). Company-scoped via npubOwner.
 */
@Slf4j
class GrrcRiskService {

    DomainEventBusService domainEventBusService

    @Transactional
    RiskItem create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        RiskItem r = new RiskItem(
                npubOwner    : npub,
                name         : name,
                description  : fields.description as String,
                status       : (fields.status ?: 'OPEN') as String,
                severity     : (fields.severity ?: 'MEDIUM') as String,
                likelihood   : (fields.likelihood ?: 'MEDIUM') as String,
                ownerName    : fields.ownerName as String,
                treatmentNote: fields.treatmentNote as String
        )
        r.save(failOnError: true, flush: true)
        publishOpened(r)
        return r
    }

    @Transactional
    RiskItem update(Long id, String npubOwner, Map fields) {
        RiskItem r = load(id, npubOwner)
        if (fields.name) r.name = fields.name as String
        if (fields.containsKey('description')) r.description = fields.description as String
        if (fields.status) r.status = fields.status as String
        if (fields.severity) r.severity = fields.severity as String
        if (fields.likelihood) r.likelihood = fields.likelihood as String
        if (fields.containsKey('ownerName')) r.ownerName = fields.ownerName as String
        if (fields.containsKey('treatmentNote')) r.treatmentNote = fields.treatmentNote as String
        r.save(failOnError: true, flush: true)
        return r
    }

    @Transactional
    RiskItem close(Long id, String npubOwner, Map fields = [:]) {
        RiskItem r = load(id, npubOwner)
        if (fields.containsKey('treatmentNote')) {
            r.treatmentNote = fields.treatmentNote as String
        }
        r.status = 'CLOSED'
        r.save(failOnError: true, flush: true)
        return r
    }

    @Transactional
    void delete(Long id, String npubOwner) {
        RiskItem r = load(id, npubOwner)
        r.delete(flush: true)
    }

    @Transactional(readOnly = true)
    RiskItem get(Long id, String npubOwner) { load(id, npubOwner) }

    static final List RISK_SORT_FIELDS = [
            'id', 'name', 'status', 'severity', 'likelihood', 'ownerName'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String status = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', RISK_SORT_FIELDS)
        String st = status?.trim() ? status.trim().toUpperCase() : null
        List items = RiskItem.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (st) eq('status', st)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = RiskItem.createCriteria().count {
            eq('npubOwner', npub)
            if (st) eq('status', st)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    Map toMap(RiskItem r) {
        if (!r) return null
        [
                id           : r.id,
                name         : r.name,
                description  : r.description,
                status       : r.status,
                severity     : r.severity,
                likelihood   : r.likelihood,
                ownerName    : r.ownerName,
                treatmentNote: r.treatmentNote,
                npubOwner    : r.npubOwner
        ]
    }

    private void publishOpened(RiskItem r) {
        if (!domainEventBusService || !r?.id) return
        try {
            domainEventBusService.publish(
                    DomainEventNames.GRRC_RISK_OPENED,
                    'grrc.risk',
                    r.npubOwner,
                    [riskId: r.id, name: r.name, severity: r.severity, status: r.status]
            )
        } catch (Exception e) {
            log.debug("grrc.risk.opened publish skipped: ${e.message}")
        }
    }

    private RiskItem load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        RiskItem r = RiskItem.get(id)
        if (!r || r.npubOwner != npub) throw new IllegalArgumentException("risk not found: ${id}")
        r
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
