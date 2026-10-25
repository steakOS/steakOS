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
 * Shared feature / hub nav entries for AppShell sidebar and Ctrl+K command palette.
 * Keep paths + labels here so the jump menu cannot drift behind the sidebar.
 */
import {
  BookOpen,
  FileText,
  Wallet,
  Receipt,
  PieChart,
  Landmark,
  Users,
  Shield,
  ShoppingCart,
  Package,
  Factory,
  Briefcase,
  Headphones,
  Server,
  FolderKanban,
  GitBranch,
  BadgeCheck,
  RefreshCw,
  Layers,
  Bot,
  Network,
  ShieldCheck,
  Brain,
  Settings,
  Bitcoin,
  Upload,
  Target,
  FlaskConical,
  Warehouse,
  Wrench,
  Building2,
  Ticket,
  LifeBuoy,
  KeyRound,
  Plug,
  ShieldAlert,
  Handshake,
  Tags,
  ContactRound,
  Scale,
  GitCompareArrows,
} from 'lucide-react'

/** AUTH03 — System nav by role. Help / tour live under helpNavLinks. */
export function systemLinks(t, { isAdmin, isCeo } = {}) {
  const links = [
    { name: t('nav.settings'), path: '/settings', icon: Settings },
    {
      name: t('nav.integrations', { defaultValue: 'Integrations' }),
      path: '/system/integrations',
      icon: Plug,
      keywords: 'integrations hub nostr bitcoin btcpay hermes',
    },
    {
      name: t('nav.security', { defaultValue: 'Security' }),
      path: '/security',
      icon: ShieldCheck,
      keywords: 'security totp 2fa password',
    },
    { name: t('nav.company'), path: '/company', icon: Building2 },
  ]
  if (isAdmin || isCeo) {
    links.push(
      {
        name: t('nav.users', { defaultValue: 'Users' }),
        path: '/users',
        icon: Users,
      },
      {
        name: t('nav.roles', { defaultValue: 'Roles' }),
        path: '/roles',
        icon: KeyRound,
      },
      { name: t('nav.import'), path: '/system/import', icon: Upload },
      { name: t('nav.catalogs'), path: '/system/catalogs', icon: Wrench },
    )
  }
  return links
}

/** Help hub — tour, help center, IT support. Visible to all authenticated roles. */
export function helpNavLinks(t) {
  return [
    {
      name: t('nav.helpCenter', { defaultValue: t('nav.help') }),
      path: '/help',
      icon: LifeBuoy,
    },
    {
      name: t('nav.itSupport', { defaultValue: 'IT Support' }),
      path: '/help/it-support',
      icon: Ticket,
      keywords: 'it support ticket',
    },
    {
      name: t('nav.tour'),
      path: '/tour/index.html',
      icon: BookOpen,
      external: true,
    },
  ]
}

export function financeFeatureLinks(t, { showAdvanced = false } = {}) {
  const links = [
    { name: t('features.generalLedger'), path: '/finance/transactions', icon: BookOpen, keywords: 'gl ledger journal' },
    { name: t('features.invoices'), path: '/finance/invoices', icon: FileText },
    {
      name: t('features.payments', { defaultValue: 'Payments' }),
      path: '/finance/payments',
      icon: Bitcoin,
      keywords: 'payments btcpay bitcoin lightning',
    },
    { name: t('features.treasury'), path: '/finance/treasury', icon: Wallet },
    { name: t('features.payables'), path: '/finance/payables', icon: Receipt },
    { name: t('features.budgets'), path: '/finance/budgets', icon: PieChart },
    { name: t('features.tax'), path: '/finance/tax', icon: Landmark, keywords: 'vat tax ust' },
    { name: t('features.payroll'), path: '/finance/payroll', icon: Users },
    { name: t('features.control'), path: '/finance/control', icon: Shield },
    {
      name: t('features.postingRules', { defaultValue: 'Posting rules' }),
      path: '/finance/posting-rules',
      icon: BookOpen,
      keywords: 'posting rules je auto finance',
    },
  ]
  if (showAdvanced) {
    links.push(
      {
        name: t('features.collections', { defaultValue: 'Collections' }),
        path: '/finance/collections',
        icon: Scale,
        keywords: 'collections ar dunning',
      },
      {
        name: t('features.threeWayMatch', { defaultValue: 'Three-way match' }),
        path: '/finance/match',
        icon: GitCompareArrows,
        keywords: 'three-way match po invoice receipt',
      },
    )
  }
  return links
}

export function salesFeatureLinks(t, { showAdvanced = false } = {}) {
  const links = [
    {
      name: t('desk.title', { ns: 'sales' }),
      path: '/sales',
      icon: ShoppingCart,
      keywords: 'sales quotes orders',
    },
  ]
  if (showAdvanced) {
    links.push(
      {
        name: t('features.pricing', { defaultValue: 'Price lists' }),
        path: '/sales/pricing',
        icon: Tags,
        keywords: 'pricing price list',
      },
      {
        name: t('features.crm', { defaultValue: 'CRM' }),
        path: '/sales/crm',
        icon: ContactRound,
        keywords: 'crm customer relationship pipeline leads contacts',
      },
    )
  }
  return links
}

export function grrcFeatureLinks(t) {
  return [
    {
      name: t('features.grrcDesk', { defaultValue: 'Governance desk' }),
      path: '/grrc',
      icon: ShieldAlert,
      keywords: 'governance grrc risk compliance control',
    },
  ]
}

export function stakeholderFeatureLinks(t) {
  return [
    {
      name: t('features.stakeholderDesk', { defaultValue: 'Stakeholder desk' }),
      path: '/stakeholder',
      icon: Handshake,
      keywords: 'stakeholder engagement partners',
    },
  ]
}

export function procurementFeatureLinks(t) {
  return [
    {
      name: t('features.buyersDesk'),
      path: '/scm/buyers',
      icon: Package,
      keywords: 'buyers procurement vendor purchase po',
    },
  ]
}

export function productionFeatureLinks(t) {
  return [
    {
      name: t('features.shopFloor'),
      path: '/scm/shopfloor',
      icon: Factory,
      keywords: 'shop floor production inventory quality lot',
    },
  ]
}

export function deliveryFeatureLinks(t) {
  return [{ name: t('features.deliveryDesk'), path: '/delivery', icon: Briefcase }]
}

export function afterSalesFeatureLinks(t) {
  return [{ name: t('features.afterSalesDesk'), path: '/aftersales', icon: Headphones }]
}

export function hrFeatureLinks(t) {
  return [{ name: t('features.hrDesk'), path: '/hr', icon: Users, keywords: 'hr human capital people' }]
}

export function digitalFeatureLinks(t) {
  return [{ name: t('features.digitalDesk'), path: '/digital', icon: Server, keywords: 'digital platform it' }]
}

export function strategyFeatureLinks(t) {
  return [
    {
      name: t('features.strategyDesk', { defaultValue: 'Strategy desk' }),
      path: '/strategy',
      icon: Target,
      keywords: 'strategy planning',
    },
  ]
}

export function developmentFeatureLinks(t) {
  return [
    {
      name: t('features.developmentDesk', { defaultValue: 'Development desk' }),
      path: '/development',
      icon: FlaskConical,
      keywords: 'development rd product',
    },
  ]
}

export function assetFeatureLinks(t) {
  return [
    {
      name: t('features.assetDesk', { defaultValue: 'Assets desk' }),
      path: '/assets',
      icon: Warehouse,
      keywords: 'assets fixed asset inventory',
    },
  ]
}

export function capabilityFeatureLinks(t) {
  return [
    { name: t('features.portfolios'), path: '/capability/portfolios', icon: Layers },
    { name: t('features.programs'), path: '/capability/programs', icon: Briefcase },
    { name: t('features.projects'), path: '/capability/projects', icon: FolderKanban },
    { name: t('features.processes'), path: '/capability/processes', icon: GitBranch },
    { name: t('features.quality'), path: '/capability/quality', icon: BadgeCheck },
    { name: t('features.changes'), path: '/capability/changes', icon: RefreshCw },
    { name: t('features.knowledge'), path: '/capability/knowledge', icon: BookOpen },
  ]
}

export function agentsFeatureLinks(t) {
  return [
    { name: t('features.agents'), path: '/agents/agents', icon: Bot, keywords: 'ai agents' },
    { name: t('features.hives'), path: '/agents/hives', icon: Network },
    { name: t('features.hitl'), path: '/agents/hitl', icon: ShieldCheck, keywords: 'hitl human in the loop' },
    { name: t('features.memory'), path: '/agents/memory', icon: Brain },
  ]
}

/**
 * Map package → feature link builder (same switch as AppShell NavGroup).
 */
export function featureLinksForPackage(pkg, t, { showAdvanced = false } = {}) {
  switch (pkg) {
    case 'finance':
      return financeFeatureLinks(t, { showAdvanced })
    case 'sales':
      return salesFeatureLinks(t, { showAdvanced })
    case 'procurement':
      return procurementFeatureLinks(t)
    case 'production':
      return productionFeatureLinks(t)
    case 'delivery':
      return deliveryFeatureLinks(t)
    case 'service':
      return afterSalesFeatureLinks(t)
    case 'human':
      return hrFeatureLinks(t)
    case 'digital':
      return digitalFeatureLinks(t)
    case 'capability':
      return capabilityFeatureLinks(t)
    case 'agents':
      return agentsFeatureLinks(t)
    case 'strategy':
      return strategyFeatureLinks(t)
    case 'development':
      return developmentFeatureLinks(t)
    case 'asset':
      return assetFeatureLinks(t)
    case 'grrc':
      return grrcFeatureLinks(t)
    case 'stakeholder':
      return stakeholderFeatureLinks(t)
    default:
      return []
  }
}

/** Palette group bucket for a domain package. */
export function paletteGroupForPackage(pkg, t) {
  if (pkg === 'finance') return t('pages.commandPalette.groupFinance')
  if (pkg === 'capability' || pkg === 'agents') {
    return t('pages.commandPalette.groupIntelligence')
  }
  return t('pages.commandPalette.groupWork')
}
