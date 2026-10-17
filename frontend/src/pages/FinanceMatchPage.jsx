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
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { GitCompareArrows } from 'lucide-react'
import { financeOpsApi } from '../api/client'

/** FinanceAdvanced three-way match shell (OI-F21 / PR-9) — /api/finance/match */
export default function FinanceMatchPage() {
  const { t } = useTranslation(['desks', 'common'])
  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await financeOpsApi.matchList()
      setItems(data?.items || [])
      setTotal(data?.total ?? (data?.items || []).length)
    } catch (e) {
      setItems([])
      setTotal(0)
      setError(e.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="page">
      <header className="page-header">
        <h1>
          <GitCompareArrows size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('match.title', { defaultValue: 'Three-way match' })}
        </h1>
        <p className="page-sub">
          {t('match.subtitle', {
            defaultValue:
              'FinanceAdvanced invoice ↔ PO ↔ receipt match. Silver pack / Iron eval.',
          })}
        </p>
        <p>
          <Link to="/finance/payables">
            {t('match.backPayables', { defaultValue: '← Payables' })}
          </Link>
        </p>
      </header>

      {error && (
        <div className="card" style={{ borderColor: 'var(--danger, #b91c1c)', marginBottom: '1rem' }}>
          <strong>{t('match.loadError', { defaultValue: 'Could not load matches' })}</strong>
          <p className="muted" style={{ margin: '0.5rem 0 0' }}>
            {error}
          </p>
        </div>
      )}

      <section className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 className="section-label" style={{ margin: 0 }}>
            {t('match.list', { defaultValue: 'Matches' })}
          </h2>
          <button type="button" className="btn btn-ghost" onClick={load} disabled={loading}>
            {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
          </button>
        </div>
        {loading ? (
          <p className="muted">{t('match.loading', { defaultValue: 'Loading…' })}</p>
        ) : items.length === 0 ? (
          <p className="muted">
            {t('match.empty', {
              defaultValue: 'No three-way matches yet. Propose matches via /api/finance/match.',
            })}
          </p>
        ) : (
          <>
            <p className="muted">{t('match.count', { defaultValue: '{{total}} match(es)', total })}</p>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>{t('match.colId', { defaultValue: 'ID' })}</th>
                    <th>{t('match.colStatus', { defaultValue: 'Status' })}</th>
                    <th>{t('match.colInvoice', { defaultValue: 'Invoice' })}</th>
                    <th>{t('match.colPo', { defaultValue: 'PO' })}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((row) => (
                    <tr key={row.id}>
                      <td>{row.id}</td>
                      <td>{row.matchStatus || row.status || '—'}</td>
                      <td>{row.invoiceId || row.invoiceNumber || '—'}</td>
                      <td>{row.purchaseOrderId || row.poId || '—'}</td>
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
