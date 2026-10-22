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

import registry from './domainRegistry.json'

/**
 * P0 master-data ↔ desk parity (MASTER-DATA-DESK-PARITY-CONCEPT).
 *
 * entityClass:
 *   A = reference master · B = process document · C = line/child ·
 *   D = workflow · E = config/helper
 *
 * createMode:
 *   domain       — DomainForm create OK
 *   deskRedirect — New → deskPath (process UI)
 *   parentOnly   — no standalone New (create under parent)
 *   blocked      — no create until specialized form exists
 */

/** @type {Record<string, { entityClass: string, createMode: string, interaction?: 'deskShell'|'domainFullPage'|'capability', deskPath?: string, deskLabel?: string, parentResource?: string, reason?: string }>} */
export const CREATE_META = {
  // —— core ——
  'core/Task': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },

  // —— sales ——
  'sales/Customer': { entityClass: 'A', createMode: 'domain', interaction: 'deskShell' },
  'sales/CustomerQuote': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/sales',
    deskLabel: 'Sales desk',
  },
  'sales/QuoteLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'CustomerQuote',
    parentAssoc: 'quote',
    deskPath: '/sales',
  },
  'sales/SalesOrder': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/sales',
    deskLabel: 'Sales desk',
  },
  'sales/OrderLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'SalesOrder',
    parentAssoc: 'salesOrder',
    deskPath: '/sales',
  },
  'sales/GoToMarket': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'sales/Market': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'sales/SalesPlan': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },

  // —— procurement / SCM ——
  'procurement/Vendor': { entityClass: 'A', createMode: 'domain', interaction: 'deskShell' },
  'procurement/PurchaseOrder': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/buyers',
    deskLabel: 'Buyers Desk',
  },
  'procurement/PurchaseOrderLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'PurchaseOrder',
    parentAssoc: 'purchaseOrder',
    deskPath: '/scm/buyers',
  },
  'procurement/Receipt': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/buyers',
    deskLabel: 'Buyers Desk',
  },
  'procurement/ReceiptLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'Receipt',
    parentAssoc: 'receipt',
    deskPath: '/scm/buyers',
  },
  'procurement/Contract': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'procurement/SourcingCategory': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'procurement/SourcingPlan': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },

  // —— production ——
  'catalogue/CatalogueItem': {
    entityClass: 'A',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/buyers',
    deskLabel: 'Buyers Desk (goods/services) / Delivery (fulfilled services)',
  },
  'production/Component': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },
  'production/BillOfMaterials': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },
  'production/BomItem': {
    entityClass: 'C',
    createMode: 'domain',
    interaction: 'domainFullPage', // specialized DomainForm (parent BOM + component)
    parentResource: 'BillOfMaterials',
    parentAssoc: 'bom',
  },
  'production/ProductionOrder': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/shopfloor',
    deskLabel: 'Shop Floor Control',
  },
  'production/Lot': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/shopfloor',
    deskLabel: 'Shop Floor Control',
  },
  'production/Check': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/shopfloor',
    deskLabel: 'Shop Floor Control',
  },
  'production/StockMove': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/shopfloor',
    deskLabel: 'Shop Floor Control',
  },

  // —— delivery ——
  'delivery/Resource': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },
  'delivery/ServiceOrder': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/delivery',
    deskLabel: 'Delivery desk',
  },
  'delivery/ServiceOrderLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'ServiceOrder',
    parentAssoc: 'serviceOrder',
    deskPath: '/delivery',
  },

  // —— aftersales (service package) ——
  'service/Claims': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/aftersales',
    deskLabel: 'AfterSales desk',
    reason: 'Requires warranty association and process status',
  },
  'service/Inquiry': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/aftersales',
    deskLabel: 'AfterSales desk',
  },
  'service/Rma': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/aftersales',
    deskLabel: 'AfterSales desk',
  },
  'service/Satisfaction': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'service/ServiceLevel': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },
  'service/Warranty': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/aftersales',
    deskLabel: 'AfterSales desk',
  },

  // —— strategy / development / asset (G4 thin) ——
  'strategy/Competitor': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },
  'strategy/StrategyItem': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },
  'development/DevelopmentProject': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },
  'asset/PhysicalAsset': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },

  // —— human ——
  'human/Candidate': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/Goal': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/HRFile': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    deskLabel: 'HR desk',
    reason: 'Creates/links User — must go through HR process API',
  },
  'human/HeadcountPlan': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/PartyAccess': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/Payroll': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/Policy': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/TimeTrack': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },

  // —— digital ——
  'digital/ConfigItem': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },
  'digital/Data': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },
  'digital/ITChange': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },
  'digital/ItTicket': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },
  'digital/Solution': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },
  'digital/System': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },

  // —— finance ——
  'finance/Account': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },
  'finance/Budget': {
    entityClass: 'A',
    createMode: 'domain',
    interaction: 'domainFullPage',
    deskPath: '/finance/budgets',
    deskLabel: 'Finance · Budgets',
  },
  'finance/Invoice': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/finance/invoices',
    deskLabel: 'Finance · Invoices',
  },
  'finance/JournalEntry': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/finance/transactions',
    deskLabel: 'Finance · Transactions',
  },
  'finance/JournalLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'JournalEntry',
    parentAssoc: 'journalEntry',
    deskPath: '/finance/transactions',
  },
  'finance/Payable': {
    entityClass: 'A',
    createMode: 'domain',
    interaction: 'domainFullPage',
    deskPath: '/finance/payables',
    deskLabel: 'Finance · Payables',
  },
  'finance/Payment': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/finance/invoices',
    deskLabel: 'Finance · Invoices',
    reason: 'Requires invoice association',
  },
  'finance/PaymentOption': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'finance/PayrollItem': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/finance/payroll',
    deskLabel: 'Finance · Payroll',
  },
  'finance/Tax': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/finance/tax',
    deskLabel: 'Finance · Tax',
  },
  'finance/Treasury': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },

  // —— capability ——
  'capability/Change': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'capability',
    deskPath: '/capability/changes',
    deskLabel: 'Capability hub',
  },
  'capability/ChangeItem': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'capability',
    parentResource: 'Change',
    deskPath: '/capability/changes',
  },
  'capability/KnowledgeArea': { entityClass: 'E', createMode: 'domain', interaction: 'capability' },
  'capability/Portfolio': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'capability',
    deskPath: '/capability/portfolios',
    deskLabel: 'Capability hub',
  },
  'capability/Procedure': { entityClass: 'E', createMode: 'domain', interaction: 'capability' },
  'capability/Process': {
    entityClass: 'E',
    createMode: 'deskRedirect',
    interaction: 'capability',
    deskPath: '/capability/processes',
    deskLabel: 'Capability hub',
  },
  'capability/ProcessStep': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'capability',
    parentResource: 'Process',
    deskPath: '/capability/processes',
  },
  'capability/Program': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'capability',
    deskPath: '/capability/programs',
    deskLabel: 'Capability hub',
  },
  'capability/Project': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'capability',
    deskPath: '/capability/projects',
    deskLabel: 'Capability hub',
  },
  'capability/ProjectItem': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'capability',
    parentResource: 'Project',
    deskPath: '/capability/projects',
  },
  'capability/Quality': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'capability',
    deskPath: '/capability/quality',
    deskLabel: 'Capability hub',
  },
  'capability/QualityItem': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'capability',
    parentResource: 'Quality',
    deskPath: '/capability/quality',
  },
  'capability/Standard': { entityClass: 'E', createMode: 'domain', interaction: 'capability' },

  // —— agents ——
  'agents/Agent': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/agents/agents',
    deskLabel: 'Agents hub',
  },
  'agents/AgentHumanLink': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'Agent',
    deskPath: '/agents/agents',
  },
  'agents/Approval': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/agents/approvals',
    deskLabel: 'Agents hub',
  },
  'agents/Hive': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/agents/hives',
    deskLabel: 'Agents hub',
  },
  'agents/Memory': {
    entityClass: 'E',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/agents/memory',
    deskLabel: 'Agents hub',
  },

  // —— content / config / helper ——
  'generic/Content': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'generic/ContentType': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'generic/ContentTypeConfig': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'configuration/GlobalConfig': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'configuration/UserConfig': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'helper/Address': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },
  'helper/Classification': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'helper/Countries': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'helper/Currencies': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'helper/SimpleClass': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'helper/SimpleStatus': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'helper/SimpleType': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'helper/SimpleUnit': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'helper/SupportedLocale': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'helper/Tag': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
}

/** ADR-045 — resolve interaction mode for a package/resource key. */
export function getInteraction(packageName, resourceName) {
  const key = `${packageName}/${resourceName}`
  const meta = CREATE_META[key]
  if (meta?.interaction) return meta.interaction
  if (meta?.createMode === 'deskRedirect' || meta?.createMode === 'parentOnly') return 'deskShell'
  if (String(packageName) === 'capability') return 'capability'
  return 'domainFullPage'
}

const DEFAULT_META = {
  entityClass: 'E',
  createMode: 'domain',
}

/** Infer create meta when not explicitly listed */
function inferMeta(pkg, name) {
  if (/Line$/.test(name) || /Item$/.test(name)) {
    // BomItem has specialized form — listed explicitly as domain
    return {
      entityClass: 'C',
      createMode: 'parentOnly',
      reason: 'Child line — create under parent document',
    }
  }
  return { ...DEFAULT_META }
}

/**
 * Resolved create policy for a package/resource.
 */
export function getCreateMeta(pkg, resourceName) {
  const key = `${pkg}/${resourceName}`
  const explicit = CREATE_META[key]
  if (explicit) {
    return {
      entityClass: explicit.entityClass || 'E',
      createMode: explicit.createMode || 'domain',
      deskPath: explicit.deskPath || null,
      deskLabel: explicit.deskLabel || null,
      parentResource: explicit.parentResource || null,
      parentAssoc: explicit.parentAssoc || null,
      reason: explicit.reason || null,
    }
  }
  return {
    ...inferMeta(pkg, resourceName),
    deskPath: null,
    deskLabel: null,
    parentResource: null,
    parentAssoc: null,
    reason: null,
  }
}

/** Whether master-data DomainForm may create this resource */

/**
 * Line-child resource for a parent document (DomainList nested lines).
 * Uses CREATE_META parentResource + parentAssoc on class-C rows.
 */
export function getLineChildForParent(pkg, parentResource) {
  const prefix = `${pkg}/`
  for (const [key, meta] of Object.entries(CREATE_META)) {
    if (!key.startsWith(prefix)) continue
    if (meta.parentResource !== parentResource) continue
    if (!meta.parentAssoc) continue
    const childResource = key.slice(prefix.length)
    return {
      package: pkg,
      resource: childResource,
      parentAssoc: meta.parentAssoc,
      parentFk: `${meta.parentAssoc}Id`,
      deskPath: meta.deskPath || null,
    }
  }
  return null
}

export function allowsDomainCreate(pkg, resourceName) {
  return getCreateMeta(pkg, resourceName).createMode === 'domain'
}

/** Path for “create via process” CTA, or null */
export function getDeskCreatePath(pkg, resourceName) {
  const m = getCreateMeta(pkg, resourceName)
  if (m.createMode === 'deskRedirect' || m.createMode === 'parentOnly') {
    return m.deskPath
  }
  return null
}

function enrichResource(pkg, res) {
  const meta = getCreateMeta(pkg, res.name)
  return {
    ...res,
    entityClass: res.entityClass || meta.entityClass,
    createMode: res.createMode || meta.createMode,
    deskPath: res.deskPath || meta.deskPath,
    deskLabel: res.deskLabel || meta.deskLabel,
    parentResource: res.parentResource || meta.parentResource,
    createReason: res.createReason || meta.reason,
  }
}

export const domainGroups = (registry.groups || []).map((g) => ({
  ...g,
  resources: (g.resources || []).map((r) => enrichResource(g.package, r)),
}))

export function findResource(pkg, resource) {
  const group = domainGroups.find((g) => g.package === pkg)
  if (!group) return null
  const res = group.resources.find((r) => r.name === resource)
  return res ? { group, resource: res } : null
}

export function apqcGroups() {
  return domainGroups.filter((g) => g.apqc != null && g.apqc >= 1 && g.apqc <= 10)
}

/** All classified resources (for audits / tests) */
export function listCreatePolicies() {
  const out = []
  for (const g of domainGroups) {
    for (const r of g.resources) {
      out.push({
        package: g.package,
        resource: r.name,
        entityClass: r.entityClass,
        createMode: r.createMode,
        deskPath: r.deskPath,
      })
    }
  }
  return out
}
