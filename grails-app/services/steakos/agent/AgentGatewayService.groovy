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

import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityService
import steakos.User
import steakos.agents.AgApprovalService
import steakos.agents.Approval
import steakos.capability.CapProcessService
import steakos.configuration.SetupService
import steakos.core.Task
import steakos.finance.Account
import steakos.finance.Invoice
import steakos.finance.Payable

/**
 * ADR-037 P1–P3 — L0 summary + L1 propose for Hermes agents.
 */
@Transactional
class AgentGatewayService {

    SpringSecurityService springSecurityService
    SetupService setupService
    AgApprovalService agApprovalService
    CapProcessService capProcessService
    AgentCapabilityService agentCapabilityService

    /** Default lanes for new agents (ADR-037 D3). */
    static final List<String> DEFAULT_LANES = ['L0', 'L1'].asImmutable()

    Map me(String username, String linkedTenantKey) {
        User u = username ? User.findByUsername(username) : (springSecurityService?.currentUser as User)
        [
                username        : u?.username ?: username,
                userId          : u?.id,
                npub            : u?.npub,
                linkedTenantKey : linkedTenantKey ?: u?.npub ?: 'user:admin',
                roles           : authoritiesOf(u),
                lanes           : DEFAULT_LANES,
                writeDefault    : 'L1_PROPOSE',
                note            : 'L2 delegated execute is not granted by default (ADR-037)'
        ]
    }

    @Transactional(readOnly = true)
    Map l0Summary(String npubOwner) {
        String npub = npubOwner?.trim() ?: 'user:admin'
        Map profile = null
        Map setup = null
        try {
            profile = setupService?.profile(npub, null)
            setup = setupService?.statusMap(npub)
        } catch (Exception ignored) {
        }

        int openTasks = 0
        try {
            openTasks = Task.countByNpubOwnerAndStatus(npub, 'OPEN') as int
            openTasks += (Task.countByNpubOwnerAndStatus(npub, 'IN_PROGRESS') as int)
        } catch (Exception ignored) {
        }

        int processCount = 0
        try {
            if (capProcessService) {
                Map page = capProcessService.listProcessesPage(npub, null, null, 1, 0)
                processCount = (page.total as Integer) ?: 0
            }
        } catch (Exception ignored) {
            try {
                processCount = steakos.capability.Process.countByNpubOwner(npub) as int
            } catch (Exception ignore2) {
            }
        }

        int invoiceOpen = 0
        int invoiceTotal = 0
        int accountCount = 0
        int payableOpen = 0
        try {
            invoiceTotal = Invoice.countByNpubOwner(npub) as int
            invoiceOpen = Invoice.countByNpubOwnerAndInvoiceStatus(npub, 'ISSUED') as int
            invoiceOpen += (Invoice.countByNpubOwnerAndInvoiceStatus(npub, 'PART_PAID') as int)
        } catch (Exception ignored) {
        }
        try {
            accountCount = Account.countByNpubOwner(npub) as int
        } catch (Exception ignored) {
        }
        try {
            payableOpen = Payable.countByNpubOwnerAndPayableStatus(npub, 'OPEN') as int
        } catch (Exception ignored) {
        }

        int pendingProposals = 0
        try {
            pendingProposals = Approval.countByNpubOwnerAndDecision(npub, 'PENDING') as int
        } catch (Exception ignored) {
        }

        [
                npubOwner : npub,
                lanes     : DEFAULT_LANES,
                company   : profile?.legalEntity ? [
                        name              : profile.legalEntity.name,
                        functionalCurrency: profile.legalEntity.functionalCurrency,
                        countryCode       : profile.legalEntity.countryCode
                ] : null,
                setup     : setup ? [
                        setupComplete: setup.setupComplete,
                        templateTier : setup.templateTier
                ] : null,
                tasks     : [openOrInProgress: openTasks],
                processes : [count: processCount],
                finance   : [
                        accounts     : accountCount,
                        invoicesTotal: invoiceTotal,
                        invoicesOpen : invoiceOpen,
                        payablesOpen : payableOpen
                ],
                proposals : [pending: pendingProposals],
                links     : [
                        profile      : '/api/setup/profile',
                        tasks        : '/api/domains/core/Task?view=open&max=25',
                        processes    : '/api/capability/processes?max=25',
                        invoices     : '/api/finance/revenue/invoices?max=25',
                        capabilities : '/api/agent/capabilities',
                        proposals    : '/api/agent/proposals?decision=PENDING'
                ]
        ]
    }

    /**
     * L1: create HITL Approval as agent proposal.
     */
    Approval propose(String npubOwner, Map fields, String agentUsername) {
        String npub = requireNpub(npubOwner)
        Map body = new LinkedHashMap(fields ?: [:])
        if (!body.name) {
            body.name = "Agent proposal from ${agentUsername ?: 'agent'}"
        }
        // Stamp actor into description for audit if not already
        String stamp = "proposedBy=${agentUsername ?: 'agent'}"
        String desc = body.description?.toString() ?: ''
        if (!desc.contains('proposedBy=')) {
            body.description = desc ? "${desc}\n${stamp}" : stamp
        }
        // Embed actor in JSON envelope
        String json = body.proposalJson?.toString()
        if (!json?.trim()) {
            body.proposalJson = groovy.json.JsonOutput.toJson([
                    proposedBy: agentUsername,
                    action    : body.action ?: body.operationId ?: 'unspecified',
                    payload   : body.payload ?: [:]
            ])
        }
        if (!body.riskLevel) body.riskLevel = 'LOW'
        return agApprovalService.submit(npub, body)
    }

    @Transactional(readOnly = true)
    Map listProposals(String npubOwner, String decision, int max, int offset) {
        agApprovalService.listPage(npubOwner, decision, max, offset)
    }

    @Transactional(readOnly = true)
    Approval getProposal(Long id, String npubOwner) {
        agApprovalService.get(id, npubOwner)
    }

    private static List authoritiesOf(User u) {
        if (!u) return []
        try {
            return (steakos.UserRole.findAllByUser(u)*.role*.authority).findAll { it }
        } catch (Exception ignored) {
            return []
        }
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
