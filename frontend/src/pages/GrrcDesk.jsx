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

import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ShieldAlert } from 'lucide-react'
import { grrcApi } from '../api/client'

/**
 * GRRCBasics thin desk (G7 / PR-9) — risks + compliance registers via /api/grrc.
 */
export default function GrrcDesk() {
  const { t } = useTranslation(['desks', 'common'])
  const [tab, setTab] = useState('risks')
  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data =
        tab === 'risks'
          ? await grrcApi.risks.list({ max: 25, offset: 0 })
          : await grrcApi.compliance.list({ max: 25, offset: 0 })
      setItems(data?.items || [])
      setTotal(data?.total ?? (data?.items || []).length)
    } catch (e) {
      setItems([])
      setTotal(0)
      setError(e.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [tab])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="page">
      <header className="page-header">
        <h1>
          <ShieldAlert size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('grrc.title', { defaultValue: 'GRRC' })}
        </h1>
        <p className="page-sub">
          {t('grrc.subtitle', {
            defaultValue:
              'Governance, risk, remediation, and compliance — Basics register. Silver pack / Iron eval.',
          })}
        </p>
      </header>

      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
        <button
          type="button"
          className={`btn ${tab === 'risks' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTab('risks')}
        >
          {t('grrc.risks', { defaultValue: 'Risks' })}
        </button>
        <button
          type="button"
          className={`btn ${tab === 'compliance' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTab('compliance')}
        >
          {t('grrc.compliance', { defaultValue: 'Compliance' })}
        </button>
        <button type="button" className="btn btn-ghost" onClick={load} disabled={loading}>
          {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
      </div>

      {error && (
        <div className="card" style={{ borderColor: 'var(--danger, #b91c1c)', marginBottom: '1rem' }}>
          <strong>{t('grrc.loadError', { defaultValue: 'Could not load GRRC data' })}</strong>
          <p className="muted" style={{ margin: '0.5rem 0 0' }}>
            {error}
          </p>
        </div>
      )}

      <section className="card">
        {loading ? (
          <p className="muted">{t('grrc.loading', { defaultValue: 'Loading…' })}</p>
        ) : items.length === 0 ? (
          <p className="muted">
            {tab === 'risks'
              ? t('grrc.emptyRisks', {
                  defaultValue: 'No risks yet. Open items will appear here from /api/grrc/risks.',
                })
              : t('grrc.emptyCompliance', {
                  defaultValue:
                    'No compliance items yet. Register policies and controls via /api/grrc/compliance.',
                })}
          </p>
        ) : (
          <>
            <p className="muted" style={{ marginTop: 0 }}>
              {t('grrc.count', {
                defaultValue: '{{total}} item(s)',
                total,
              })}
            </p>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>{t('grrc.colName', { defaultValue: 'Name' })}</th>
                    <th>{t('grrc.colStatus', { defaultValue: 'Status' })}</th>
                    <th>{t('grrc.colExtra', { defaultValue: tab === 'risks' ? 'Severity' : 'Kind' })}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((row) => (
                    <tr key={row.id}>
                      <td>{row.name || row.title || `#${row.id}`}</td>
                      <td>{row.status || '—'}</td>
                      <td>{row.severity || row.itemKind || row.kind || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </section>
    </div>
  )
}
