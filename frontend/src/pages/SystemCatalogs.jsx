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

import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { domainGroups } from '../config/domainRegistry'
import { catalogLifecycle, lifecycleBadgeClass } from '../config/catalogLifecycle'
import { humanizeLabel } from '../utils/humanize'

/**
 * Admin catalogs formerly in the last three sidebar dropdowns.
 */
export default function SystemCatalogs() {
  const { t } = useTranslation(['common', 'domain'])
  const adminGroups = domainGroups.filter((g) => g.navRole === 'admin' || g.primary === false)

  const lifecycleLabel = (life) => {
    if (life === 'in_use') return t('pages.catalogs.inUse')
    if (life === 'system') return t('pages.catalogs.system')
    return t('pages.catalogs.reserve')
  }

  const lifecycleTitle = (life) => {
    if (life === 'in_use') return t('pages.catalogs.inUseHint')
    if (life === 'system') return t('pages.catalogs.systemHint')
    return t('pages.catalogs.reserveHint')
  }

  return (
    <div>
      <h1 className="page-title">{t('pages.catalogs.title')}</h1>
      <p className="page-sub">{t('pages.catalogs.subtitle')}</p>
      {adminGroups.map((g) => (
        <div key={g.package} className="card" style={{ marginTop: 12 }}>
          <h2>{t(`groups.${g.package}`, { defaultValue: g.label })}</h2>
          <ul className="setup-family-list">
            {(g.resources || []).map((r) => {
              const life = catalogLifecycle(g.package, r.name)
              const label = t(`${g.package}.${r.name}`, {
                ns: 'domain',
                defaultValue: humanizeLabel(r.name),
              })
              return (
                <li
                  key={r.name}
                  style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}
                >
                  <NavLink to={r.path}>{label}</NavLink>
                  <span className={lifecycleBadgeClass(life)} title={lifecycleTitle(life)}>
                    {lifecycleLabel(life)}
                  </span>
                </li>
              )
            })}
          </ul>
        </div>
      ))}
    </div>
  )
}
