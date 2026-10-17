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

package steakos.events

/**
 * Stable domain event names (ADR-025 / OI-A02 skeleton).
 * Silver contract set from plan-plugins-boundary §4.3 — same strings when outbox lands.
 */
final class DomainEventNames {

    private DomainEventNames() {}

    // Sales (Basics / Advanced)
    static final String SALES_ORDER_CONFIRMED = 'sales.order.confirmed'
    static final String SALES_OPPORTUNITY_WON = 'sales.opportunity.won'
    static final String SALES_PRICELIST_UPDATED = 'sales.pricelist.updated'

    // Finance (Basics / Advanced)
    static final String FINANCE_INVOICE_ISSUED = 'finance.invoice.issued'
    static final String FINANCE_INVOICE_SETTLED = 'finance.invoice.settled'
    static final String FINANCE_INVOICE_OVERDUE = 'finance.invoice.overdue'
    static final String FINANCE_MATCH_COMPLETED = 'finance.match.completed'

    // SCM
    static final String SCM_RECEIPT_POSTED = 'scm.receipt.posted'

    // GRRC / Stakeholder (Silver families)
    static final String GRRC_RISK_OPENED = 'grrc.risk.opened'
    static final String STAKEHOLDER_COMMUNICATION_LOGGED = 'stakeholder.communication.logged'

    // Agents
    static final String AGENT_PROPOSAL_SUBMITTED = 'agent.proposal.submitted'
    static final String AGENT_PROPOSAL_APPROVED = 'agent.proposal.approved'

    // Messaging (core — email/Telegram later)
    static final String MESSAGING_OUTBOUND_SENT = 'messaging.outbound.sent'

    /** All registered names (immutable). */
    static final List<String> ALL = [
            SALES_ORDER_CONFIRMED,
            SALES_OPPORTUNITY_WON,
            SALES_PRICELIST_UPDATED,
            FINANCE_INVOICE_ISSUED,
            FINANCE_INVOICE_SETTLED,
            FINANCE_INVOICE_OVERDUE,
            FINANCE_MATCH_COMPLETED,
            SCM_RECEIPT_POSTED,
            GRRC_RISK_OPENED,
            STAKEHOLDER_COMMUNICATION_LOGGED,
            AGENT_PROPOSAL_SUBMITTED,
            AGENT_PROPOSAL_APPROVED,
            MESSAGING_OUTBOUND_SENT
    ].asImmutable()

    /**
     * Intended consumers (documentation for Wave 2+ / messaging). Not enforced at runtime.
     * Keys = event name; values = family / core labels.
     */
    static final Map<String, List<String>> INTENDED_CONSUMERS = [
            (SALES_ORDER_CONFIRMED)             : ['finance', 'delivery', 'scm'].asImmutable(),
            (SALES_OPPORTUNITY_WON)             : ['sales', 'audit'].asImmutable(),
            (SALES_PRICELIST_UPDATED)           : ['sales', 'audit'].asImmutable(),
            (FINANCE_INVOICE_ISSUED)            : ['sales', 'messaging'].asImmutable(),
            (FINANCE_INVOICE_SETTLED)           : ['sales', 'messaging'].asImmutable(),
            (FINANCE_INVOICE_OVERDUE)           : ['sales', 'messaging', 'collections'].asImmutable(),
            (FINANCE_MATCH_COMPLETED)           : ['finance'].asImmutable(),
            (SCM_RECEIPT_POSTED)                : ['finance'].asImmutable(),
            (GRRC_RISK_OPENED)                  : ['capability', 'agents'].asImmutable(),
            (STAKEHOLDER_COMMUNICATION_LOGGED)  : ['messaging'].asImmutable(),
            (AGENT_PROPOSAL_SUBMITTED)          : ['messaging', 'audit'].asImmutable(),
            (AGENT_PROPOSAL_APPROVED)           : ['messaging', 'audit'].asImmutable(),
            (MESSAGING_OUTBOUND_SENT)           : ['audit'].asImmutable()
    ].asImmutable()

    static boolean isKnown(String name) {
        name != null && ALL.contains(name)
    }
}
