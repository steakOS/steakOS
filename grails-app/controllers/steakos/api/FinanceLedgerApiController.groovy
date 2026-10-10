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
import steakos.User
import steakos.finance.Account
import steakos.finance.FinanceLedgerService
import steakos.finance.JournalEntry
import steakos.finance.PostJournalCommand
import steakos.finance.PostingRulesetService

/**
 * Finance ledger API (F-S1 / APQC 9.3).
 * All money posts go through {@link FinanceLedgerService} (FS6).
 */
@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class FinanceLedgerApiController {

    static allowedMethods = [
            seed              : 'POST',
            accounts          : 'GET',
            createAccount     : 'POST',
            postEntry         : 'POST',
            reverse           : 'POST',
            balance           : 'GET',
            trialBalance      : 'GET',
            closePeriod       : 'POST',
            periodStatus      : 'GET',
            postingRulesGet   : 'GET',
            postingRulesSeed  : 'POST',
            postingRulesSave  : 'PUT',
            postingRulesReseed: 'POST',
    ]

    FinanceLedgerService financeLedgerService
    PostingRulesetService postingRulesetService
    SpringSecurityService springSecurityService
    steakos.configuration.TenantNpubService tenantNpubService

    /** POST /api/finance/ledger/seed */
    def seed() {
        try {
            String npub = resolveNpub(request.JSON as Map)
            List<Account> accounts = financeLedgerService.ensureChartSeeded(npub)
            Map rulesSeed = null
            try {
                rulesSeed = postingRulesetService?.ensureSeeded(npub)
            } catch (Exception ignored) {
            }
            render([
                    npubOwner   : npub,
                    count       : accounts.size(),
                    accounts    : accounts.collect { financeLedgerService.accountToMap(it) },
                    postingRules: rulesSeed?.ruleset,
                    accountingMode: rulesSeed?.mode,
            ] as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** GET /api/finance/ledger/accounts */
    def accounts() {
        try {
            String npub = resolveNpub(params)
            boolean activeOnly = params.activeOnly != 'false'
            Map page = steakos.api.ListPaging.pageParams(params, steakos.api.ListPaging.DEFAULT_MAX, steakos.api.ListPaging.HARD_MAX)
            Map result = financeLedgerService.listAccountsPage(npub, activeOnly, page.max as int, page.offset as int)
            render(steakos.api.ListPaging.envelope(
                    (result.items as List).collect { financeLedgerService.accountToMap(it) },
                    result.total as int,
                    page.max as int,
                    page.offset as int,
                    [npubOwner: npub, sort: 'code', order: 'asc']
            ) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/finance/ledger/accounts */
    def createAccount() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            Account a = financeLedgerService.createAccount(npub, body)
            render(status: 201, text: (financeLedgerService.accountToMap(a) as JSON))
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/finance/ledger/entries */
    def postEntry() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            if (!body.npubOwner) {
                body.npubOwner = resolveNpub(body)
            }
            PostJournalCommand cmd = PostJournalCommand.fromMap(body)
            JournalEntry entry = financeLedgerService.postEntry(cmd)
            render(status: 201, text: (financeLedgerService.entryToMap(entry) as JSON))
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/finance/ledger/entries/$id/reverse */
    def reverse() {
        try {
            Long id = params.long('id')
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            String reason = body.reason as String
            JournalEntry rev = financeLedgerService.reverseEntry(id, npub, reason)
            render(financeLedgerService.entryToMap(rev) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** GET /api/finance/ledger/accounts/$id/balance */
    def balance() {
        try {
            String npub = resolveNpub(params)
            Long id = params.long('id')
            Date asOf = null
            if (params.asOf) {
                asOf = Date.parse('yyyy-MM-dd', params.asOf.toString().substring(0, 10))
            }
            render(financeLedgerService.getAccountBalance(npub, id, asOf) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** GET /api/finance/ledger/trial-balance?period=yyyy-MM */
    def trialBalance() {
        try {
            String npub = resolveNpub(params)
            String period = params.period ?: new java.text.SimpleDateFormat('yyyy-MM').format(new Date())
            render(financeLedgerService.trialBalance(npub, period) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** POST /api/finance/ledger/periods/$period/close */
    def closePeriod() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            String period = params.period
            render(financeLedgerService.closePeriod(npub, period) as JSON)
        } catch (IllegalArgumentException | IllegalStateException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    /** GET /api/finance/ledger/periods/$period */
    def periodStatus() {
        try {
            String npub = resolveNpub(params)
            String period = params.period
            render([
                    period: period,
                    closed: financeLedgerService.isPeriodClosed(npub, period),
                    npubOwner: npub
            ] as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message] as JSON))
        }
    }

    // ----- PDR-027 PR1 posting rules -----

    /** GET /api/finance/posting-rules */
    def postingRulesGet() {
        try {
            String npub = resolveNpub(params)
            postingRulesetService.ensureSeeded(npub)
            render([
                    npubOwner      : npub,
                    accountingMode : postingRulesetService.getAccountingMode(npub),
                    ruleset        : postingRulesetService.getRuleset(npub),
            ] as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'POSTING_RULES_INVALID'] as JSON))
        }
    }

    /** POST /api/finance/posting-rules/seed — idempotent ensure */
    def postingRulesSeed() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            render(postingRulesetService.ensureSeeded(npub) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'POSTING_RULES_INVALID'] as JSON))
        }
    }

    /** PUT /api/finance/posting-rules — Admin only (audit ticket in later PR) */
    @Secured(['ROLE_ADMIN'])
    def postingRulesSave() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            render(postingRulesetService.saveRuleset(npub, body) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'POSTING_RULES_INVALID'] as JSON))
        }
    }

    /** POST /api/finance/posting-rules/reseed — Admin reset to SBM defaults */
    @Secured(['ROLE_ADMIN'])
    def postingRulesReseed() {
        try {
            Map body = (request.JSON as Map) ?: [:]
            String npub = resolveNpub(body)
            render(postingRulesetService.reseedDefaults(npub) as JSON)
        } catch (IllegalArgumentException e) {
            render(status: 400, text: ([error: e.message, code: 'POSTING_RULES_INVALID'] as JSON))
        }
    }

    private String resolveNpub(Object source) {
        tenantNpubService.resolve(session, source)
    }
}
