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

import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Target, Users } from 'lucide-react'

/**
 * StrategyBasics thin hub (G4) — competitors + strategy notes via domain CRUD.
 */
export default function StrategyDesk() {
  const { t } = useTranslation(['desks', 'common'])
  const cards = [
    {
      to: '/domains/strategy/Competitor',
      icon: Users,
      title: t('strategy.competitors', { defaultValue: 'Competitors' }),
      body: t('strategy.competitorsHint', {
        defaultValue: 'Register competitors you watch.',
      }),
    },
    {
      to: '/domains/strategy/StrategyItem',
      icon: Target,
      title: t('strategy.items', { defaultValue: 'Strategy items' }),
      body: t('strategy.itemsHint', {
        defaultValue: 'Vision, mission, strategies, needs, and factors (light notes).',
      }),
    },
  ]
  return (
    <div className="page">
      <header className="page-header">
        <h1>{t('strategy.title', { defaultValue: 'Strategy' })}</h1>
        <p className="page-sub">
          {t('strategy.subtitle', {
            defaultValue: 'Entry Level/Light Process — registers only; not full APQC strategy suite.',
          })}
        </p>
      </header>
      <div className="card-grid" style={{ display: 'grid', gap: '1rem', gridTemplateColumns: 'repeat(auto-fill, minmax(16rem, 1fr))' }}>
        {cards.map((c) => {
          const Icon = c.icon
          return (
            <Link key={c.to} to={c.to} className="card" style={{ textDecoration: 'none', color: 'inherit' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
                <Icon size={18} />
                <strong>{c.title}</strong>
              </div>
              <p className="muted" style={{ margin: 0 }}>
                {c.body}
              </p>
            </Link>
          )
        })}
      </div>
    </div>
  )
}
