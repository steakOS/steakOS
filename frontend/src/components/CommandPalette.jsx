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

import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { domainGroups } from '../config/domainRegistry'
import { humanizeLabel } from '../utils/humanize'
import {
  systemLinks,
  helpNavLinks,
  featureLinksForPackage,
  paletteGroupForPackage,
} from '../nav/featureLinks'

const RESULT_LIMIT = 32

/** Desk / hub packages whose feature links appear in the jump menu (same as sidebar). */
const FEATURE_PACKAGES = [
  'sales',
  'procurement',
  'production',
  'delivery',
  'service',
  'human',
  'digital',
  'strategy',
  'development',
  'asset',
  'finance',
  'capability',
  'agents',
  'grrc',
  'stakeholder',
]

/**
 * Ctrl+K / ⌘K jump palette (Wave B).
 * Feature entries share builders with AppShell so desks (Opportunities, Governance, …) stay in sync.
 * Help entries navigate to the page and open Help with ?help=<key>.
 */
export default function CommandPalette({ open, onClose, showAdvanced = false, elevated = false }) {
  const navigate = useNavigate()
  const { t } = useTranslation(['common', 'domain', 'desks', 'sales'])
  const [q, setQ] = useState('')

  const items = useMemo(() => {
    const appGroup = t('pages.commandPalette.groupApp')
    const sysGroup = t('pages.commandPalette.groupSystem')
    const helpGroup = t('pages.commandPalette.groupHelp', { defaultValue: 'Help' })
    const intelGroup = t('pages.commandPalette.groupIntelligence')

    const toItem = (link, group) => ({
      label: link.name,
      path: link.path,
      group,
      keywords: link.keywords || '',
      external: !!link.external,
    })

    const staticItems = [
      { label: t('nav.dashboard'), path: '/dashboard', group: appGroup },
      {
        label: t('pages.work.nav', { defaultValue: 'My work' }),
        path: '/work',
        group: appGroup,
        keywords: 'my work tasks activity',
      },
      { label: t('nav.about'), path: '/about', group: appGroup },
    ]

    if (showAdvanced) {
      staticItems.push({
        label: t('nav.analytics', { defaultValue: 'Analytics' }),
        path: '/analytics',
        group: intelGroup,
        keywords: 'analytics kpi dashboard metrics',
      })
    }

    // System + Help — same builders as AppShell
    systemLinks(t, { isAdmin: elevated, isCeo: elevated }).forEach((link) => {
      staticItems.push(toItem(link, sysGroup))
    })
    helpNavLinks(t).forEach((link) => {
      staticItems.push(toItem(link, helpGroup))
    })
    staticItems.push({
      label: t('pages.help.wizard'),
      path: '/setup',
      group: sysGroup,
      keywords: 'setup wizard onboarding',
    })

    // Desks / hubs — include Silver surfaces when showAdvanced (Opportunities, GRRC, …)
    FEATURE_PACKAGES.forEach((pkg) => {
      if ((pkg === 'grrc' || pkg === 'stakeholder') && !showAdvanced) return
      const group = paletteGroupForPackage(pkg, t)
      featureLinksForPackage(pkg, t, { showAdvanced }).forEach((link) => {
        staticItems.push(toItem(link, group))
      })
    })

    // —— Help topics (searchable by Bitcoin, BTCPay, Nostr, AI, Hermes) ——
    staticItems.push(
      {
        label: 'Integrations hub',
        path: '/system/integrations',
        helpKey: 'system.integrations',
        keywords: 'integrations hub nostr bitcoin btcpay hermes ai',
        group: helpGroup,
      },
      {
        label: 'Bitcoin',
        path: '/system/integrations',
        helpKey: 'integrations.bitcoin',
        keywords: 'bitcoin btc receive level single address deferred',
        group: helpGroup,
      },
      {
        label: 'Bitcoin literacy (training)',
        path: '/system/integrations',
        helpKey: 'training.btc.basic',
        keywords: 'bitcoin training literacy education',
        group: helpGroup,
      },
      {
        label: 'Bitcoin security (training)',
        path: '/system/integrations',
        helpKey: 'training.btc.security-advanced',
        keywords: 'bitcoin security seed malware address',
        group: helpGroup,
      },
      {
        label: 'BTCPay Server',
        path: '/finance/payments',
        helpKey: 'finance.payments.live',
        keywords: 'btcpay btc pay server lightning live payments webhook',
        group: helpGroup,
      },
      {
        label: 'Nostr',
        path: '/system/integrations',
        helpKey: 'integrations.nostr',
        keywords: 'nostr relay nip-07 npub local relay',
        group: helpGroup,
      },
      {
        label: 'Hermes Agent',
        path: '/system/integrations',
        helpKey: 'agents.activate',
        keywords: 'hermes agent token api sos activate',
        group: helpGroup,
      },
      {
        label: 'AI / Agents',
        path: '/agents',
        helpKey: 'agents.hub',
        keywords: 'ai artificial intelligence agentic hive hitl memory',
        group: helpGroup,
      },
    )

    // Deduplicate by path+helpKey+label (system/help may overlap wizard etc.)
    const seen = new Set()
    const deduped = []
    for (const item of staticItems) {
      const key = `${item.path}|${item.helpKey || ''}|${item.label}`
      if (seen.has(key)) continue
      seen.add(key)
      deduped.push(item)
    }

    const domainItems = []
    domainGroups.forEach((g) => {
      const groupLabel = t(`groups.${g.package}`, {
        defaultValue: humanizeLabel(g.label || g.package),
      })
      ;(g.resources || []).forEach((r) => {
        const resLabel = t(`${g.package}.${r.name}`, {
          ns: 'domain',
          defaultValue: humanizeLabel(r.name),
        })
        domainItems.push({
          label: `${groupLabel} · ${resLabel}`,
          path: r.path,
          group: t('pages.commandPalette.groupMasterData'),
        })
      })
    })
    return [...deduped, ...domainItems]
  }, [t, showAdvanced, elevated])

  const filtered = useMemo(() => {
    const s = q.trim().toLowerCase()
    if (!s) return items.slice(0, RESULT_LIMIT)
    return items
      .filter((i) => {
        const hay = `${i.label} ${i.path} ${i.keywords || ''} ${i.helpKey || ''}`.toLowerCase()
        return hay.includes(s)
      })
      .slice(0, RESULT_LIMIT)
  }, [items, q])

  useEffect(() => {
    if (open) setQ('')
  }, [open])

  // Hooks must run unconditionally (Rules of Hooks). Early-return only after all hooks.
  const recentGroup = t('pages.commandPalette.groupRecent', { defaultValue: 'Recent' })
  const recentItems = useMemo(() => {
    if (!open) return []
    try {
      const raw = JSON.parse(localStorage.getItem('steakos.cmd.recent') || '[]')
      if (!Array.isArray(raw)) return []
      return raw.slice(0, 8).map((r) => ({
        label: r.label || r.path,
        path: r.path,
        group: recentGroup,
        keywords: 'recent',
      }))
    } catch {
      return []
    }
  }, [open, recentGroup])

  const filteredWithRecent = useMemo(() => {
    if (q.trim()) return filtered
    const paths = new Set(filtered.map((i) => i.path))
    const extras = recentItems.filter((r) => r.path && !paths.has(r.path))
    return [...extras, ...filtered].slice(0, RESULT_LIMIT)
  }, [filtered, recentItems, q])

  // WP-9: quick actions always available
  const quickActions = useMemo(
    () => [
      {
        label: t('pages.commandPalette.newQuote', { defaultValue: 'New quote' }),
        path: '/sales?tab=quotes&new=1',
        group: t('pages.commandPalette.groupWork'),
        keywords: 'new quote create sales',
      },
      {
        label: t('pages.commandPalette.openSales', { defaultValue: 'Open Sales desk' }),
        path: '/sales',
        group: t('pages.commandPalette.groupWork'),
        keywords: 'sales quotes orders',
      },
    ],
    [t],
  )

  const shown = useMemo(() => {
    if (q.trim()) {
      const s = q.trim().toLowerCase()
      const qa = quickActions.filter((i) =>
        `${i.label} ${i.path} ${i.keywords || ''}`.toLowerCase().includes(s),
      )
      return [...qa, ...filteredWithRecent].slice(0, RESULT_LIMIT)
    }
    return [...quickActions, ...filteredWithRecent].slice(0, RESULT_LIMIT)
  }, [q, quickActions, filteredWithRecent])

  const go = (item) => {
    onClose()
    if (item.external || (item.path.startsWith('/tour') && !item.helpKey)) {
      window.open(item.path, '_blank', 'noopener,noreferrer')
      return
    }
    const path = item.helpKey
      ? `${item.path}${item.path.includes('?') ? '&' : '?'}help=${encodeURIComponent(item.helpKey)}`
      : item.path
    try {
      const prev = JSON.parse(localStorage.getItem('steakos.cmd.recent') || '[]')
      const next = [
        { label: item.label, path },
        ...(Array.isArray(prev) ? prev : []).filter((r) => r.path !== path),
      ].slice(0, 12)
      localStorage.setItem('steakos.cmd.recent', JSON.stringify(next))
    } catch {
      /* ignore */
    }
    navigate(path)
  }

  if (!open) return null

  return (
    <div className="cmd-backdrop" onClick={onClose} role="presentation">
      <div className="cmd-panel" onClick={(e) => e.stopPropagation()} role="dialog">
        <input
          className="cmd-input"
          autoFocus
          placeholder={t('pages.commandPalette.placeholder')}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape') onClose()
            if (e.key === 'Enter' && shown[0]) go(shown[0])
          }}
        />
        <ul className="cmd-list">
          {shown.map((item) => (
            <li key={`${item.path}:${item.helpKey || ''}:${item.label}`}>
              <button type="button" className="cmd-item" onClick={() => go(item)}>
                <span>{item.label}</span>
                <span className="cmd-meta">{item.group}</span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
