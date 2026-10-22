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

/** @type {Record<string, { entityClass: string, createMode: string, interaction?: 'deskShell'|'domainFullPage'|'capability', deskPath?: string, defaultTab?: string, deskLabel?: string, parentResource?: string, reason?: string }>} */
export const CREATE_META = {
  // —— core ——
  'core/Task': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },

  // —— sales ——
  'sales/Customer': {
    entityClass: 'A',
    createMode: 'domain',
    interaction: 'deskShell',
    deskPath: '/sales',
    defaultTab: 'customers',
    deskLabel: 'Sales desk',
  },
  'sales/CustomerQuote': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/sales',
    defaultTab: 'quotes',
    deskLabel: 'Sales desk',
  },
  'sales/QuoteLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'CustomerQuote',
    parentAssoc: 'quote',
    deskPath: '/sales',
    defaultTab: 'quotes',
  },
  'sales/SalesOrder': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/sales',
    defaultTab: 'orders',
    deskLabel: 'Sales desk',
  },
  'sales/OrderLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'SalesOrder',
    parentAssoc: 'salesOrder',
    deskPath: '/sales',
    defaultTab: 'orders',
  },
  'sales/GoToMarket': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'sales/Market': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'sales/SalesPlan': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },

  // —— procurement / SCM ——
  'procurement/Vendor': {
    entityClass: 'A',
    createMode: 'domain',
    interaction: 'deskShell',
    deskPath: '/scm/buyers',
    defaultTab: 'vendors',
    deskLabel: 'Buyers Desk',
  },
  'procurement/PurchaseOrder': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/buyers',
    defaultTab: 'purchase',
    deskLabel: 'Buyers Desk',
  },
  'procurement/PurchaseOrderLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'PurchaseOrder',
    parentAssoc: 'purchaseOrder',
    deskPath: '/scm/buyers',
    defaultTab: 'purchase',
  },
  'procurement/Receipt': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/buyers',
    defaultTab: 'purchase',
    deskLabel: 'Buyers Desk',
  },
  'procurement/ReceiptLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'Receipt',
    parentAssoc: 'receipt',
    deskPath: '/scm/buyers',
    defaultTab: 'purchase',
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
    defaultTab: 'products',
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
    defaultTab: 'production',
    deskLabel: 'Shop Floor Control',
  },
  'production/Lot': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/shopfloor',
    defaultTab: 'inventory',
    deskLabel: 'Shop Floor Control',
  },
  'production/Check': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/shopfloor',
    defaultTab: 'quality',
    deskLabel: 'Shop Floor Control',
  },
  'production/StockMove': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/scm/shopfloor',
    defaultTab: 'inventory',
    deskLabel: 'Shop Floor Control',
  },

  // —— delivery ——
  'delivery/Resource': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },
  'delivery/ServiceOrder': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/delivery',
    defaultTab: 'orders',
    deskLabel: 'Delivery desk',
  },
  'delivery/ServiceOrderLine': {
    entityClass: 'C',
    createMode: 'parentOnly',
    interaction: 'deskShell',
    parentResource: 'ServiceOrder',
    parentAssoc: 'serviceOrder',
    deskPath: '/delivery',
    defaultTab: 'orders',
  },

  // —— aftersales (service package) ——
  'service/Claims': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/aftersales',
    defaultTab: 'claims',
    deskLabel: 'AfterSales desk',
    reason: 'Requires warranty association and process status',
  },
  'service/Inquiry': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/aftersales',
    defaultTab: 'inquiries',
    deskLabel: 'AfterSales desk',
  },
  'service/Rma': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/aftersales',
    defaultTab: 'rmas',
    deskLabel: 'AfterSales desk',
  },
  'service/Satisfaction': { entityClass: 'E', createMode: 'domain', interaction: 'domainFullPage' },
  'service/ServiceLevel': { entityClass: 'A', createMode: 'domain', interaction: 'domainFullPage' },
  'service/Warranty': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/aftersales',
    defaultTab: 'warranties',
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
    defaultTab: 'candidates',
    deskLabel: 'HR desk',
  },
  'human/Goal': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    defaultTab: 'goals',
    deskLabel: 'HR desk',
  },
  'human/HRFile': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    defaultTab: 'files',
    deskLabel: 'HR desk',
    reason: 'Creates/links User — must go through HR process API',
  },
  'human/HeadcountPlan': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    defaultTab: 'plans',
    deskLabel: 'HR desk',
  },
  'human/PartyAccess': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    defaultTab: 'parties',
    deskLabel: 'HR desk',
  },
  'human/Payroll': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    defaultTab: 'payroll',
    deskLabel: 'HR desk',
  },
  'human/Policy': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    defaultTab: 'policies',
    deskLabel: 'HR desk',
  },
  'human/TimeTrack': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/hr',
    defaultTab: 'time',
    deskLabel: 'HR desk',
  },

  // —— digital ——
  'digital/ConfigItem': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    defaultTab: 'cmdb',
    deskLabel: 'Digital desk',
  },
  'digital/Data': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    defaultTab: 'data',
    deskLabel: 'Digital desk',
  },
  'digital/ITChange': {
    entityClass: 'D',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    defaultTab: 'changes',
    deskLabel: 'Digital desk',
  },
  'digital/ItTicket': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    defaultTab: 'support',
    deskLabel: 'Digital desk',
  },
  'digital/Solution': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    defaultTab: 'solutions',
    deskLabel: 'Digital desk',
  },
  'digital/System': {
    entityClass: 'B',
    createMode: 'deskRedirect',
    interaction: 'deskShell',
    deskPath: '/digital',
    defaultTab: 'systems',
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
    defaultTab: 'invoices',
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
    defaultTab: 'invoices',
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
    deskPath: '/agents/hitl',
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
      interaction: explicit.interaction || null,
      deskPath: explicit.deskPath || null,
      defaultTab: explicit.defaultTab || null,
      deskLabel: explicit.deskLabel || null,
      parentResource: explicit.parentResource || null,
      parentAssoc: explicit.parentAssoc || null,
      reason: explicit.reason || null,
    }
  }
  return {
    ...inferMeta(pkg, resourceName),
    interaction: null,
    deskPath: null,
    defaultTab: null,
    deskLabel: null,
    parentResource: null,
    parentAssoc: null,
    reason: null,
  }
}

/**
 * Desk deep-link: `/sales?tab=customers&id=123` or `&new=1`.
 * @param {{ deskPath: string, defaultTab?: string|null, id?: string|number|null, new?: boolean, returnQ?: string|null }} opts
 */
export function deskHref({ deskPath, defaultTab, id, new: isNew = false, returnQ } = {}) {
  if (!deskPath) return '/'
  const qIdx = deskPath.indexOf('?')
  const path = qIdx >= 0 ? deskPath.slice(0, qIdx) : deskPath
  const params = new URLSearchParams(qIdx >= 0 ? deskPath.slice(qIdx + 1) : '')
  if (defaultTab) params.set('tab', defaultTab)
  if (isNew) {
    params.set('new', '1')
    params.delete('id')
  } else if (id != null && id !== '') {
    params.set('id', String(id))
  }
  if (returnQ) params.set('returnQ', returnQ)
  const qs = params.toString()
  return qs ? `${path}?${qs}` : path
}

/** sessionStorage key for DomainList → DomainForm return query */
export function domainListReturnKey(pkg, resource) {
  return `steakos.domainListReturn.${pkg}.${resource}`
}

/**
 * Domain master-data list URL with optional search/pager restore.
 * @param {string} pkg
 * @param {string} resource
 * @param {{ q?: string, offset?: number|string, max?: number|string }} [opts]
 */
export function listReturnHref(pkg, resource, { q, offset, max } = {}) {
  const params = new URLSearchParams()
  if (q != null && String(q).trim() !== '') params.set('q', String(q).trim())
  if (offset != null && offset !== '' && Number(offset) > 0) {
    params.set('offset', String(offset))
  }
  if (max != null && max !== '' && Number(max) > 0) params.set('max', String(max))
  const qs = params.toString()
  return `/domains/${pkg}/${resource}${qs ? `?${qs}` : ''}`
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
    interaction: res.interaction || meta.interaction,
    deskPath: res.deskPath || meta.deskPath,
    defaultTab: res.defaultTab || meta.defaultTab,
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
