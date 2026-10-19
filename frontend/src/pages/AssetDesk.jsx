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
import { Warehouse } from 'lucide-react'

/**
 * AssetBasics thin hub (G4) — physical plant/vehicles/tools.
 * IT-managed assets permanently link to Digital ConfigItem (CI is master for IT fields).
 */
export default function AssetDesk() {
  const { t } = useTranslation(['desks', 'common'])
  return (
    <div className="page">
      <header className="page-header">
        <h1>{t('asset.title', { defaultValue: 'Assets' })}</h1>
        <p className="page-sub">
          {t('asset.subtitle', {
            defaultValue:
              'Entry Level — physical asset register. Flags for financial depreciation (Finance agents) and required maintenance (attach schedule; later shopfloor/maintenance activities).',
          })}
        </p>
      </header>
      <Link
        to="/domains/asset/PhysicalAsset"
        className="card"
        style={{ textDecoration: 'none', color: 'inherit', display: 'block', maxWidth: '28rem' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
          <Warehouse size={18} />
          <strong>{t('asset.register', { defaultValue: 'Physical assets' })}</strong>
        </div>
        <p className="muted" style={{ margin: 0 }}>
          {t('asset.registerHint', {
            defaultValue:
              'Name, tag, location, owner, status. Optional IT link, financial depreciation, and maintenance flags.',
          })}
        </p>
      </Link>
      <p className="muted" style={{ marginTop: '1rem' }}>
        <Link to="/digital">{t('asset.digitalLink', { defaultValue: 'Open Digital / IT desk →' })}</Link>
      </p>
    </div>
  )
}
