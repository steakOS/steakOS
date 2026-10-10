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

package steakos.agent

/**
 * ADR-037 P2 — capability catalog for Hermes discovery (anti-scrape).
 * Static curated list of MVP L0/L1 operations.
 */
class AgentCapabilityService {

    static final String RISK_READ = 'read'
    static final String RISK_PROPOSE = 'propose'
    static final String RISK_EXECUTE = 'execute'
    static final String RISK_ADMIN = 'admin'

    /**
     * @return list of capability maps: id, method, path, risk, roles, description
     */
    List<Map> catalog() {
        [
                cap('agent.me', 'GET', '/api/agent/me', RISK_READ,
                        'Current agent principal, lane, linked tenant'),
                cap('agent.l0.summary', 'GET', '/api/agent/l0/summary', RISK_READ,
                        'L0 pack: profile, tasks, processes, finance read snapshot'),
                cap('agent.capabilities', 'GET', '/api/agent/capabilities', RISK_READ,
                        'This capability catalog'),
                cap('setup.profile', 'GET', '/api/setup/profile', RISK_READ,
                        'Tenant company profile (ADR-029)'),
                cap('setup.status', 'GET', '/api/setup/status', RISK_READ,
                        'Setup complete flag and template tier'),
                cap('version', 'GET', '/api/version', RISK_READ,
                        'Product version stamp'),
                cap('version.stats', 'GET', '/api/version/stats', RISK_READ,
                        'System vitals counts'),
                cap('domains.task.list', 'GET', '/api/domains/core/Task', RISK_READ,
                        'List tasks (max/offset/q/view)'),
                cap('domains.task.show', 'GET', '/api/domains/core/Task/{id}', RISK_READ,
                        'Show one task'),
                cap('capability.processes.list', 'GET', '/api/capability/processes', RISK_READ,
                        'Process catalogue list'),
                cap('finance.invoices.list', 'GET', '/api/finance/revenue/invoices', RISK_READ,
                        'List invoices (paged)'),
                cap('finance.accounts.list', 'GET', '/api/finance/ledger/accounts', RISK_READ,
                        'Chart of accounts (paged)'),
                cap('finance.payables.list', 'GET', '/api/finance/payables', RISK_READ,
                        'List payables (paged)'),
                cap('catalogue.items.list', 'GET', '/api/catalogue/items', RISK_READ,
                        'Catalogue search (q, nature, max)'),
                cap('agent.proposals.list', 'GET', '/api/agent/proposals', RISK_READ,
                        'List AI / HITL proposals for linked tenant'),
                cap('agent.proposals.show', 'GET', '/api/agent/proposals/{id}', RISK_READ,
                        'Show one proposal'),
                cap('agent.proposals.create', 'POST', '/api/agent/proposals', RISK_PROPOSE,
                        'L1: submit proposal for human approval (HITL Approval)'),
                // Human-only (documented for Hermes so it does not attempt)
                cap('agent.tokens.mint', 'POST', '/api/agent/tokens', RISK_ADMIN,
                        'Mint bearer token — human admin only'),
                cap('setup.reset', 'POST', '/api/setup/reset', RISK_ADMIN,
                        'Reset setup — denied for agents'),
        ]
    }

    private static Map cap(String id, String method, String path, String risk, String description) {
        [
                id         : id,
                method     : method,
                path       : path,
                risk       : risk,
                roles      : risk == RISK_ADMIN ? ['ROLE_ADMIN'] : ['ROLE_AI', 'ROLE_USER', 'ROLE_ADMIN'],
                lane       : risk == RISK_PROPOSE ? 'L1' : (risk == RISK_EXECUTE ? 'L2' : (risk == RISK_ADMIN ? 'admin' : 'L0')),
                description: description
        ]
    }
}
