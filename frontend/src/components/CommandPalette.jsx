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

/**
 * Ctrl+K / ⌘K jump palette (Wave B).
 * Help entries navigate to the page and open Help with ?help=<key>.
 */
export default function CommandPalette({ open, onClose }) {
  const navigate = useNavigate()
  const { t } = useTranslation(['common', 'domain', 'desks', 'sales'])
  const [q, setQ] = useState('')

  const items = useMemo(() => {
    const helpGroup = t('pages.commandPalette.groupHelp', {
      defaultValue: 'Help',
    })
    const staticItems = [
      { label: t('nav.dashboard'), path: '/dashboard', group: t('pages.commandPalette.groupApp') },
      { label: t('nav.about'), path: '/about', group: t('pages.commandPalette.groupApp') },
      { label: t('nav.settings'), path: '/settings', group: t('pages.commandPalette.groupSystem') },
      { label: t('nav.company'), path: '/company', group: t('pages.commandPalette.groupSystem') },
      {
        label: t('nav.integrations', { defaultValue: 'Integrations' }),
        path: '/system/integrations',
        keywords: 'integrations hub nostr bitcoin btcpay hermes',
        group: t('pages.commandPalette.groupSystem'),
      },
      { label: t('nav.import'), path: '/system/import', group: t('pages.commandPalette.groupSystem') },
      { label: t('nav.help'), path: '/help', group: t('pages.commandPalette.groupSystem') },
      {
        label: t('nav.tour'),
        path: '/tour/index.html',
        group: t('pages.commandPalette.groupSystem'),
        external: true,
      },
      { label: t('nav.catalogs'), path: '/system/catalogs', group: t('pages.commandPalette.groupSystem') },
      {
        label: t('desk.title', { ns: 'sales' }),
        path: '/sales',
        group: t('pages.commandPalette.groupWork'),
      },
      {
        label: t('features.buyersDesk', { defaultValue: 'Buyers Desk' }),
        path: '/scm/buyers',
        keywords: 'buyers procurement vendor purchase po',
        group: t('pages.commandPalette.groupWork'),
      },
      {
        label: t('features.shopFloor', { defaultValue: 'Shop Floor Control' }),
        path: '/scm/shopfloor',
        keywords: 'shop floor production inventory quality lot',
        group: t('pages.commandPalette.groupWork'),
      },
      {
        label: t('delivery.title', { ns: 'desks' }),
        path: '/delivery',
        group: t('pages.commandPalette.groupWork'),
      },
      {
        label: t('aftersales.title', { ns: 'desks' }),
        path: '/aftersales',
        group: t('pages.commandPalette.groupWork'),
      },
      {
        label: t('hr.title', { ns: 'desks' }),
        path: '/hr',
        group: t('pages.commandPalette.groupWork'),
      },
      {
        label: t('digital.title', { ns: 'desks' }),
        path: '/digital',
        group: t('pages.commandPalette.groupWork'),
      },
      {
        label: t('features.generalLedger'),
        path: '/finance/transactions',
        group: t('pages.commandPalette.groupFinance'),
      },
      {
        label: t('features.invoices'),
        path: '/finance/invoices',
        group: t('pages.commandPalette.groupFinance'),
      },
      {
        label: t('features.projects'),
        path: '/capability/projects',
        group: t('pages.commandPalette.groupIntelligence'),
      },
      {
        label: t('features.agents'),
        path: '/agents/agents',
        group: t('pages.commandPalette.groupIntelligence'),
      },
      {
        label: t('pages.help.wizard'),
        path: '/setup',
        group: t('pages.commandPalette.groupSystem'),
      },
      // —— Help topics (searchable by Bitcoin, BTCPay, Nostr, AI, Hermes) ——
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
    ]

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
    return [...staticItems, ...domainItems]
  }, [t])

  const filtered = useMemo(() => {
    const s = q.trim().toLowerCase()
    if (!s) return items.slice(0, 24)
    return items
      .filter((i) => {
        const hay = `${i.label} ${i.path} ${i.keywords || ''} ${i.helpKey || ''}`.toLowerCase()
        return hay.includes(s)
      })
      .slice(0, 24)
  }, [items, q])

  useEffect(() => {
    if (open) setQ('')
  }, [open])

  if (!open) return null

  const go = (item) => {
    onClose()
    if (item.external || (item.path.startsWith('/tour') && !item.helpKey)) {
      window.open(item.path, '_blank', 'noopener,noreferrer')
      return
    }
    const path = item.helpKey
      ? `${item.path}${item.path.includes('?') ? '&' : '?'}help=${encodeURIComponent(item.helpKey)}`
      : item.path
    navigate(path)
  }

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
            if (e.key === 'Enter' && filtered[0]) go(filtered[0])
          }}
        />
        <ul className="cmd-list">
          {filtered.map((item) => (
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
