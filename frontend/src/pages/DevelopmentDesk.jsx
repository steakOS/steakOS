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
import { FlaskConical } from 'lucide-react'

/**
 * DevelopmentBasics thin hub (G4) — NPD / revision projects (not capability.Project).
 */
export default function DevelopmentDesk() {
  const { t } = useTranslation(['desks', 'common'])
  return (
    <div className="page">
      <header className="page-header">
        <h1>{t('development.title', { defaultValue: 'Development' })}</h1>
        <p className="page-sub">
          {t('development.subtitle', {
            defaultValue:
              'Entry Level/Light Process — product development projects. Separate from Capability projects.',
          })}
        </p>
      </header>
      <Link
        to="/domains/development/DevelopmentProject"
        className="card"
        style={{ textDecoration: 'none', color: 'inherit', display: 'block', maxWidth: '24rem' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
          <FlaskConical size={18} />
          <strong>
            {t('development.projects', { defaultValue: 'Development projects' })}
          </strong>
        </div>
        <p className="muted" style={{ margin: 0 }}>
          {t('development.projectsHint', {
            defaultValue: 'NPD, revision, and other development initiatives (light).',
          })}
        </p>
      </Link>
    </div>
  )
}
