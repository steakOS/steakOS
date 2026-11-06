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

/**
 * processCode → status continuum for desk process strips (Nielsen WP-1/5).
 * Primary keys match SYSTEM processCode values in system-processes.json.
 * Kebab aliases kept for HelpContext / docs that used early names.
 */

/** @type {Record<string, { titleKey: string, processCode: string, steps: { id: string, statusValues: string[] }[] }>} */
export const PROCESS_STATUS_MAPS = {
  SALES_QUOTE_TO_ORDER: {
    titleKey: 'labels.SALES_QUOTE_TO_ORDER',
    processCode: 'SALES_QUOTE_TO_ORDER',
    steps: [
      { id: 'quote', statusValues: ['DRAFT', 'SENT'] },
      { id: 'accept', statusValues: ['ACCEPTED'] },
      { id: 'order', statusValues: ['CONFIRMED'] },
      { id: 'invoice', statusValues: ['CLOSED'] },
    ],
  },
  // Early plan alias (HelpContext examples)
  'sales.quote-to-cash': {
    titleKey: 'labels.SALES_QUOTE_TO_ORDER',
    processCode: 'SALES_QUOTE_TO_ORDER',
    steps: [
      { id: 'quote', statusValues: ['DRAFT', 'SENT'] },
      { id: 'accept', statusValues: ['ACCEPTED'] },
      { id: 'order', statusValues: ['CONFIRMED'] },
      { id: 'invoice', statusValues: ['CLOSED'] },
    ],
  },
  SCM_PO_FULFILL: {
    titleKey: 'labels.SCM_PO_FULFILL',
    processCode: 'SCM_PO_FULFILL',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'sent', statusValues: ['SENT'] },
      { id: 'received', statusValues: ['RECEIVED'] },
      { id: 'payable', statusValues: ['PAYABLE'] },
      { id: 'paid', statusValues: ['PAID', 'CLOSED'] },
    ],
  },
  SCM_MAKE_TO_STOCK: {
    titleKey: 'labels.SCM_MAKE_TO_STOCK',
    processCode: 'SCM_MAKE_TO_STOCK',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'released', statusValues: ['RELEASED'] },
      { id: 'in_progress', statusValues: ['IN_PROGRESS', 'STARTED'] },
      { id: 'completed', statusValues: ['COMPLETED'] },
      { id: 'qa', statusValues: ['QA', 'PASS', 'FAIL', 'HOLD'] },
    ],
  },
  'procurement.po': {
    titleKey: 'labels.SCM_PO_FULFILL',
    processCode: 'SCM_PO_FULFILL',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'sent', statusValues: ['SENT'] },
      { id: 'received', statusValues: ['RECEIVED'] },
      { id: 'payable', statusValues: ['PAYABLE'] },
      { id: 'paid', statusValues: ['PAID', 'CLOSED'] },
    ],
  },
  FIN_INVOICE_SETTLE: {
    titleKey: 'labels.FIN_INVOICE_SETTLE',
    processCode: 'FIN_INVOICE_SETTLE',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'issued', statusValues: ['ISSUED'] },
      { id: 'settled', statusValues: ['SETTLED'] },
    ],
  },
  'finance.invoice-settle': {
    titleKey: 'labels.FIN_INVOICE_SETTLE',
    processCode: 'FIN_INVOICE_SETTLE',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'issued', statusValues: ['ISSUED'] },
      { id: 'settled', statusValues: ['SETTLED'] },
    ],
  },
  DEL_SERVICE_ORDER: {
    titleKey: 'labels.DEL_SERVICE_ORDER',
    processCode: 'DEL_SERVICE_ORDER',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'scheduled', statusValues: ['SCHEDULED'] },
      { id: 'in_progress', statusValues: ['IN_PROGRESS'] },
      { id: 'completed', statusValues: ['COMPLETED'] },
    ],
  },
  'delivery.so': {
    titleKey: 'labels.DEL_SERVICE_ORDER',
    processCode: 'DEL_SERVICE_ORDER',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'scheduled', statusValues: ['SCHEDULED'] },
      { id: 'in_progress', statusValues: ['IN_PROGRESS'] },
      { id: 'completed', statusValues: ['COMPLETED'] },
    ],
  },
  AS_INQUIRY: {
    titleKey: 'labels.AS_INQUIRY',
    processCode: 'AS_INQUIRY',
    steps: [
      { id: 'open', statusValues: ['OPEN'] },
      { id: 'in_progress', statusValues: ['IN_PROGRESS'] },
      { id: 'resolved', statusValues: ['RESOLVED'] },
      { id: 'closed', statusValues: ['CLOSED'] },
    ],
  },
  HR_CANDIDATE_HIRE: {
    titleKey: 'labels.HR_CANDIDATE_HIRE',
    processCode: 'HR_CANDIDATE_HIRE',
    steps: [
      { id: 'applied', statusValues: ['APPLIED'] },
      { id: 'interview', statusValues: ['INTERVIEW'] },
      { id: 'hired', statusValues: ['HIRED'] },
      { id: 'active', statusValues: ['ACTIVE'] },
    ],
  },
  FIN_LEDGER: {
    titleKey: 'labels.FIN_LEDGER',
    processCode: 'FIN_LEDGER',
    steps: [
      { id: 'ready', statusValues: ['READY'] },
      { id: 'posted', statusValues: ['POSTED'] },
      { id: 'balanced', statusValues: ['BALANCED'] },
      { id: 'closed', statusValues: ['CLOSED'] },
    ],
  },
  AS_RMA: {
    titleKey: 'labels.AS_RMA',
    processCode: 'AS_RMA',
    steps: [
      { id: 'requested', statusValues: ['REQUESTED'] },
      { id: 'confirmed', statusValues: ['CONFIRMED'] },
      { id: 'closed', statusValues: ['CLOSED'] },
    ],
  },
  DIG_IT_TICKET: {
    titleKey: 'labels.DIG_IT_TICKET',
    processCode: 'DIG_IT_TICKET',
    steps: [
      { id: 'open', statusValues: ['OPEN'] },
      { id: 'in_progress', statusValues: ['IN_PROGRESS'] },
      { id: 'resolved', statusValues: ['RESOLVED'] },
      { id: 'closed', statusValues: ['CLOSED'] },
    ],
  },
  DIG_IT_CHANGE: {
    titleKey: 'labels.DIG_IT_CHANGE',
    processCode: 'DIG_IT_CHANGE',
    steps: [
      { id: 'planned', statusValues: ['PLANNED'] },
      { id: 'in_progress', statusValues: ['IN_PROGRESS'] },
      { id: 'done', statusValues: ['DONE'] },
      { id: 'cancelled', statusValues: ['CANCELLED'] },
    ],
  },
  CAP_PROJECT: {
    titleKey: 'labels.CAP_PROJECT',
    processCode: 'CAP_PROJECT',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'active', statusValues: ['ACTIVE'] },
      { id: 'completed', statusValues: ['COMPLETED'] },
      { id: 'cancelled', statusValues: ['CANCELLED'] },
    ],
  },
  AR_HITL_APPROVAL: {
    titleKey: 'labels.AR_HITL_APPROVAL',
    processCode: 'AR_HITL_APPROVAL',
    steps: [
      { id: 'pending', statusValues: ['PENDING'] },
      { id: 'approved', statusValues: ['APPROVED'] },
      { id: 'rejected', statusValues: ['REJECTED'] },
    ],
  },
  AST_ASSET_LIFECYCLE: {
    titleKey: 'labels.AST_ASSET_LIFECYCLE',
    processCode: 'AST_ASSET_LIFECYCLE',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'in_service', statusValues: ['IN_SERVICE'] },
      { id: 'maintenance', statusValues: ['MAINTENANCE'] },
      { id: 'retired', statusValues: ['RETIRED'] },
    ],
  },
  DEV_PROJECT_DELIVER: {
    titleKey: 'labels.DEV_PROJECT_DELIVER',
    processCode: 'DEV_PROJECT_DELIVER',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'active', statusValues: ['ACTIVE'] },
      { id: 'done', statusValues: ['DONE'] },
      { id: 'cancelled', statusValues: ['CANCELLED'] },
    ],
  },
  STR_STRATEGY_ITEM: {
    titleKey: 'labels.STR_STRATEGY_ITEM',
    processCode: 'STR_STRATEGY_ITEM',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'active', statusValues: ['ACTIVE'] },
      { id: 'archived', statusValues: ['ARCHIVED'] },
    ],
  },
  STR_COMPETITOR_WATCH: {
    titleKey: 'labels.STR_COMPETITOR_WATCH',
    processCode: 'STR_COMPETITOR_WATCH',
    steps: [
      { id: 'active', statusValues: ['ACTIVE'] },
      { id: 'watch', statusValues: ['WATCH'] },
      { id: 'inactive', statusValues: ['INACTIVE'] },
    ],
  },
  GRC_RISK_TREAT: {
    titleKey: 'labels.GRC_RISK_TREAT',
    processCode: 'GRC_RISK_TREAT',
    steps: [
      { id: 'open', statusValues: ['OPEN'] },
      { id: 'mitigating', statusValues: ['MITIGATING'] },
      { id: 'closed', statusValues: ['CLOSED'] },
      { id: 'accepted', statusValues: ['ACCEPTED'] },
    ],
  },
  GRC_COMPLIANCE_ITEM: {
    titleKey: 'labels.GRC_COMPLIANCE_ITEM',
    processCode: 'GRC_COMPLIANCE_ITEM',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'active', statusValues: ['ACTIVE'] },
      { id: 'retired', statusValues: ['RETIRED'] },
    ],
  },
  STK_PARTY_ENGAGE: {
    titleKey: 'labels.STK_PARTY_ENGAGE',
    processCode: 'STK_PARTY_ENGAGE',
    steps: [
      { id: 'active', statusValues: ['ACTIVE'] },
      { id: 'watch', statusValues: ['WATCH'] },
      { id: 'inactive', statusValues: ['INACTIVE'] },
    ],
  },
  FIN_PAYABLE: {
    titleKey: 'labels.FIN_PAYABLE',
    processCode: 'FIN_PAYABLE',
    steps: [
      { id: 'draft', statusValues: ['DRAFT'] },
      { id: 'open', statusValues: ['OPEN'] },
      { id: 'paid', statusValues: ['PAID'] },
      { id: 'void', statusValues: ['VOID'] },
    ],
  },
  SALES_OPPORTUNITY: {
    titleKey: 'labels.SALES_OPPORTUNITY',
    processCode: 'SALES_OPPORTUNITY',
    steps: [
      { id: 'lead', statusValues: ['LEAD'] },
      { id: 'qualified', statusValues: ['QUALIFIED'] },
      { id: 'proposal', statusValues: ['PROPOSAL'] },
      { id: 'negotiation', statusValues: ['NEGOTIATION'] },
      { id: 'won', statusValues: ['WON'] },
      { id: 'lost', statusValues: ['LOST'] },
    ],
  },
  SALES_PRICE_LIST: {
    titleKey: 'labels.SALES_PRICE_LIST',
    processCode: 'SALES_PRICE_LIST',
    steps: [
      { id: 'active', statusValues: ['ACTIVE'] },
      { id: 'inactive', statusValues: ['INACTIVE'] },
    ],
  },
}

/**
 * @param {string} processCode
 * @param {string} status
 * @returns {number} step index or -1 if unknown
 */
export function activeStepIndex(processCode, status) {
  if (!processCode || status == null || status === '') return -1
  const map = PROCESS_STATUS_MAPS[processCode]
  if (!map?.steps?.length) return -1
  const needle = String(status).toUpperCase()
  for (let i = 0; i < map.steps.length; i += 1) {
    const values = map.steps[i].statusValues || []
    if (values.some((v) => String(v).toUpperCase() === needle)) return i
  }
  return -1
}

/** Canonical SYSTEM processCode for a map key or alias. */
export function resolveProcessCode(processCode) {
  const map = PROCESS_STATUS_MAPS[processCode]
  return map?.processCode || processCode || null
}
