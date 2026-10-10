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

/** @type {Record<string, { entityClass: string, createMode: string, deskPath?: string, deskLabel?: string, parentResource?: string, reason?: string }>} */
export const CREATE_META = {
  // —— core ——
  'core/Task': { entityClass: 'A', createMode: 'domain' },

  // —— sales ——
  'sales/Customer': { entityClass: 'A', createMode: 'domain' },
  'sales/CustomerQuote': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/sales',
    deskLabel: 'Sales desk',
  },
  'sales/QuoteLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    parentResource: 'CustomerQuote',
    parentAssoc: 'quote',
    deskPath: '/sales',
  },
  'sales/SalesOrder': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/sales',
    deskLabel: 'Sales desk',
  },
  'sales/OrderLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    parentResource: 'SalesOrder',
    parentAssoc: 'salesOrder',
    deskPath: '/sales',
  },
  'sales/GoToMarket': { entityClass: 'E', createMode: 'domain' },
  'sales/Market': { entityClass: 'E', createMode: 'domain' },
  'sales/SalesPlan': { entityClass: 'E', createMode: 'domain' },

  // —— procurement / SCM ——
  'procurement/Vendor': { entityClass: 'A', createMode: 'domain' },
  'procurement/PurchaseOrder': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/scm/buyers',
    deskLabel: 'Buyers Desk',
  },
  'procurement/PurchaseOrderLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    parentResource: 'PurchaseOrder',
    parentAssoc: 'purchaseOrder',
    deskPath: '/scm/buyers',
  },
  'procurement/Receipt': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    deskPath: '/scm/buyers',
    deskLabel: 'Buyers Desk',
  },
  'procurement/ReceiptLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    parentResource: 'Receipt',
    parentAssoc: 'receipt',
    deskPath: '/scm/buyers',
  },
  'procurement/Contract': { entityClass: 'E', createMode: 'domain' },
  'procurement/SourcingCategory': { entityClass: 'E', createMode: 'domain' },
  'procurement/SourcingPlan': { entityClass: 'E', createMode: 'domain' },

  // —— production ——
  'catalogue/CatalogueItem': {
    entityClass: 'A',
    createMode: 'deskRedirect',
    deskPath: '/scm/buyers',
    deskLabel: 'Buyers Desk (goods/services) / Delivery (fulfilled services)',
  },
  'production/Component': { entityClass: 'A', createMode: 'domain' },
  'production/BillOfMaterials': { entityClass: 'A', createMode: 'domain' },
  'production/BomItem': {
    entityClass: 'C',
    createMode: 'domain', // specialized DomainForm (parent BOM + component)
    parentResource: 'BillOfMaterials',
    parentAssoc: 'bom',
  },
  'production/ProductionOrder': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    deskPath: '/scm/shopfloor',
    deskLabel: 'Shop Floor Control',
  },
  'production/Lot': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    deskPath: '/scm/shopfloor',
    deskLabel: 'Shop Floor Control',
  },
  'production/Check': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    deskPath: '/scm/shopfloor',
    deskLabel: 'Shop Floor Control',
  },
  'production/StockMove': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    deskPath: '/scm/shopfloor',
    deskLabel: 'Shop Floor Control',
  },

  // —— delivery ——
  'delivery/Resource': { entityClass: 'A', createMode: 'domain' },
  'delivery/ServiceOrder': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/delivery',
    deskLabel: 'Delivery desk',
  },
  'delivery/ServiceOrderLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    parentResource: 'ServiceOrder',
    parentAssoc: 'serviceOrder',
    deskPath: '/delivery',
  },

  // —— aftersales (service package) ——
  'service/Claims': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    deskPath: '/aftersales',
    deskLabel: 'AfterSales desk',
    reason: 'Requires warranty association and process status',
  },
  'service/Inquiry': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/aftersales',
    deskLabel: 'AfterSales desk',
  },
  'service/Rma': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    deskPath: '/aftersales',
    deskLabel: 'AfterSales desk',
  },
  'service/Satisfaction': { entityClass: 'E', createMode: 'domain' },
  'service/ServiceLevel': { entityClass: 'A', createMode: 'domain' },
  'service/Warranty': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/aftersales',
    deskLabel: 'AfterSales desk',
  },

  // —— strategy / development / asset (G4 thin) ——
  'strategy/Competitor': { entityClass: 'A', createMode: 'domain' },
  'strategy/StrategyItem': { entityClass: 'A', createMode: 'domain' },
  'development/DevelopmentProject': { entityClass: 'A', createMode: 'domain' },
  'asset/PhysicalAsset': { entityClass: 'A', createMode: 'domain' },

  // —— human ——
  'human/Candidate': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/Goal': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/HRFile': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/hr',
    deskLabel: 'HR desk',
    reason: 'Creates/links User — must go through HR process API',
  },
  'human/HeadcountPlan': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/PartyAccess': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/Payroll': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/Policy': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },
  'human/TimeTrack': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/hr',
    deskLabel: 'HR desk',
  },

  // —— digital ——
  'digital/ConfigItem': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },
  'digital/Data': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },
  'digital/ITChange': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },
  'digital/ItTicket': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },
  'digital/Solution': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },
  'digital/System': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/digital',
    deskLabel: 'Digital desk',
  },

  // —— finance ——
  'finance/Account': { entityClass: 'A', createMode: 'domain' },
  'finance/Budget': {
    entityClass: 'A',
    createMode: 'domain',
    deskPath: '/finance/budgets',
    deskLabel: 'Finance · Budgets',
  },
  'finance/Invoice': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/finance/invoices',
    deskLabel: 'Finance · Invoices',
  },
  'finance/JournalEntry': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/finance/transactions',
    deskLabel: 'Finance · Transactions',
  },
  'finance/JournalLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    parentResource: 'JournalEntry',
    parentAssoc: 'journalEntry',
    deskPath: '/finance/transactions',
  },
  'finance/Payable': {
    entityClass: 'A',
    createMode: 'domain',
    deskPath: '/finance/payables',
    deskLabel: 'Finance · Payables',
  },
  'finance/Payment': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    deskPath: '/finance/invoices',
    deskLabel: 'Finance · Invoices',
    reason: 'Requires invoice association',
  },
  'finance/PaymentOption': { entityClass: 'E', createMode: 'domain' },
  'finance/PayrollItem': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/finance/payroll',
    deskLabel: 'Finance · Payroll',
  },
  'finance/Tax': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/finance/tax',
    deskLabel: 'Finance · Tax',
  },
  'finance/Treasury': { entityClass: 'A', createMode: 'domain' },

  // —— capability ——
  'capability/Change': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    deskPath: '/capability/changes',
    deskLabel: 'Capability hub',
  },
  'capability/ChangeItem': {
    entityClass: 'C',
    createMode: 'parentOnly',
    parentResource: 'Change',
    deskPath: '/capability/changes',
  },
  'capability/KnowledgeArea': { entityClass: 'E', createMode: 'domain' },
  'capability/Portfolio': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/capability/portfolios',
    deskLabel: 'Capability hub',
  },
  'capability/Procedure': { entityClass: 'E', createMode: 'domain' },
  'capability/Process': {
    entityClass: 'E',
    createMode: 'deskRedirect',
    deskPath: '/capability/processes',
    deskLabel: 'Capability hub',
  },
  'capability/ProcessStep': {
    entityClass: 'C',
    createMode: 'parentOnly',
    parentResource: 'Process',
    deskPath: '/capability/processes',
  },
  'capability/Program': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/capability/programs',
    deskLabel: 'Capability hub',
  },
  'capability/Project': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/capability/projects',
    deskLabel: 'Capability hub',
  },
  'capability/ProjectItem': {
    entityClass: 'C',
    createMode: 'parentOnly',
    parentResource: 'Project',
    deskPath: '/capability/projects',
  },
  'capability/Quality': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/capability/quality',
    deskLabel: 'Capability hub',
  },
  'capability/QualityItem': {
    entityClass: 'C',
    createMode: 'parentOnly',
    parentResource: 'Quality',
    deskPath: '/capability/quality',
  },
  'capability/Standard': { entityClass: 'E', createMode: 'domain' },

  // —— agents ——
  'agents/Agent': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/agents/agents',
    deskLabel: 'Agents hub',
  },
  'agents/AgentHumanLink': {
    entityClass: 'C',
    createMode: 'parentOnly',
    parentResource: 'Agent',
    deskPath: '/agents/agents',
  },
  'agents/Approval': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    deskPath: '/agents/approvals',
    deskLabel: 'Agents hub',
  },
  'agents/Hive': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    deskPath: '/agents/hives',
    deskLabel: 'Agents hub',
  },
  'agents/Memory': {
    entityClass: 'E',
    createMode: 'deskRedirect',
    deskPath: '/agents/memory',
    deskLabel: 'Agents hub',
  },

  // —— content / config / helper ——
  'generic/Content': { entityClass: 'E', createMode: 'domain' },
  'generic/ContentType': { entityClass: 'E', createMode: 'domain' },
  'generic/ContentTypeConfig': { entityClass: 'E', createMode: 'domain' },
  'configuration/GlobalConfig': { entityClass: 'E', createMode: 'domain' },
  'configuration/UserConfig': { entityClass: 'E', createMode: 'domain' },
  'helper/Address': { entityClass: 'A', createMode: 'domain' },
  'helper/Classification': { entityClass: 'E', createMode: 'domain' },
  'helper/Countries': { entityClass: 'E', createMode: 'domain' },
  'helper/Currencies': { entityClass: 'E', createMode: 'domain' },
  'helper/SimpleClass': { entityClass: 'E', createMode: 'domain' },
  'helper/SimpleStatus': { entityClass: 'E', createMode: 'domain' },
  'helper/SimpleType': { entityClass: 'E', createMode: 'domain' },
  'helper/SimpleUnit': { entityClass: 'E', createMode: 'domain' },
  'helper/SupportedLocale': { entityClass: 'E', createMode: 'domain' },
  'helper/Tag': { entityClass: 'E', createMode: 'domain' },
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
