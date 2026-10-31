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

package steakos.capability

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/** Portfolio master (APQC 13.2.1). */
@Slf4j
class CapPortfolioService {

    @Transactional
    Portfolio create(String npubOwner, Map fields) {
        String npub = requireNpub(npubOwner)
        String name = fields.name?.toString()?.trim()
        if (!name) throw new IllegalArgumentException('name is required')
        Portfolio p = new Portfolio(
                npubOwner      : npub,
                name           : name,
                description    : fields.description as String,
                ownerRef       : fields.ownerRef as String,
                portfolioStatus: (fields.portfolioStatus ?: 'ACTIVE') as String,
                status         : (fields.portfolioStatus ?: 'ACTIVE') as String
        )
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional
    Portfolio update(Long id, String npubOwner, Map fields) {
        Portfolio p = load(id, npubOwner)
        if (fields.name) p.name = fields.name as String
        if (fields.containsKey('description')) p.description = fields.description as String
        if (fields.containsKey('ownerRef')) p.ownerRef = fields.ownerRef as String
        if (fields.portfolioStatus) {
            String st = fields.portfolioStatus as String
            if (!(st in ['ACTIVE', 'CLOSED'])) throw new IllegalArgumentException("invalid portfolioStatus: ${st}")
            p.portfolioStatus = st
            p.status = st
        }
        p.save(failOnError: true, flush: true)
        return p
    }

    @Transactional
    Portfolio close(Long id, String npubOwner) {
        update(id, npubOwner, [portfolioStatus: 'CLOSED'])
    }

    @Transactional(readOnly = true)
    Portfolio get(Long id, String npubOwner) { load(id, npubOwner) }

    @Transactional(readOnly = true)
    List<Portfolio> list(String npubOwner, String portfolioStatus = null) {
        listPage(npubOwner, portfolioStatus, 500, 0).items as List<Portfolio>
    }

    static final List PORTFOLIO_SORT_FIELDS = [
            'id', 'name', 'portfolioStatus', 'status', 'ownerRef'
    ].asImmutable()

    @Transactional(readOnly = true)
    Map listPage(String npubOwner, String portfolioStatus = null, int max = 25, int offset = 0,
                 Map listOpts = [:]) {
        String npub = requireNpub(npubOwner)
        Map so = steakos.api.ListPaging.requireSortParams(
                listOpts ?: [:], 'id', 'desc', PORTFOLIO_SORT_FIELDS)
        List items = Portfolio.createCriteria().list(max: max, offset: offset) {
            eq('npubOwner', npub)
            if (portfolioStatus) eq('portfolioStatus', portfolioStatus)
            steakos.api.ListPaging.applyOrders(delegate, so)
        } as List
        Number total = Portfolio.createCriteria().count {
            eq('npubOwner', npub)
            if (portfolioStatus) eq('portfolioStatus', portfolioStatus)
        } as Number
        [items: items, total: total?.intValue() ?: 0, sort: so.sort, order: so.order]
    }

    Map toMap(Portfolio p) {
        if (!p) return null
        [
                id             : p.id,
                name           : p.name,
                description    : p.description,
                ownerRef       : p.ownerRef,
                portfolioStatus: p.portfolioStatus,
                npubOwner      : p.npubOwner
        ]
    }

    private Portfolio load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        Portfolio p = Portfolio.get(id)
        if (!p || p.npubOwner != npub) throw new IllegalArgumentException("portfolio not found: ${id}")
        p
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
