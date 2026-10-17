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
import { ContactRound } from 'lucide-react'
import { salesApi } from '../api/client'

/** SalesAdvanced light CRM shell (OI-S08 / PR-9) — /api/sales/crm */
export default function SalesCrmPage() {
  const { t } = useTranslation(['desks', 'common', 'sales'])
  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await salesApi.crm.listOpportunities({ max: 25, offset: 0, open: true })
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
          <ContactRound size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('crm.title', { defaultValue: 'CRM' })}
        </h1>
        <p className="page-sub">
          {t('crm.subtitle', {
            defaultValue: 'SalesAdvanced light opportunities and activities. Silver pack / Iron eval.',
          })}
        </p>
        <p>
          <Link to="/sales">{t('crm.backSales', { defaultValue: '← Sales desk' })}</Link>
        </p>
      </header>

      {error && (
        <div className="card" style={{ borderColor: 'var(--danger, #b91c1c)', marginBottom: '1rem' }}>
          <strong>{t('crm.loadError', { defaultValue: 'Could not load CRM opportunities' })}</strong>
          <p className="muted" style={{ margin: '0.5rem 0 0' }}>
            {error}
          </p>
        </div>
      )}

      <section className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 className="section-label" style={{ margin: 0 }}>
            {t('crm.openOpps', { defaultValue: 'Open opportunities' })}
          </h2>
          <button type="button" className="btn btn-ghost" onClick={load} disabled={loading}>
            {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
          </button>
        </div>
        {loading ? (
          <p className="muted">{t('crm.loading', { defaultValue: 'Loading…' })}</p>
        ) : items.length === 0 ? (
          <p className="muted">
            {t('crm.empty', {
              defaultValue: 'No open opportunities. Pipeline rows come from /api/sales/crm/opportunities.',
            })}
          </p>
        ) : (
          <>
            <p className="muted">
              {t('crm.count', { defaultValue: '{{total}} opportunity(ies)', total })}
            </p>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>{t('crm.colName', { defaultValue: 'Name' })}</th>
                    <th>{t('crm.colStage', { defaultValue: 'Stage' })}</th>
                    <th>{t('crm.colCustomer', { defaultValue: 'Customer' })}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((row) => (
                    <tr key={row.id}>
                      <td>{row.name || `#${row.id}`}</td>
                      <td>{row.stage || '—'}</td>
                      <td>{row.customerName || row.prospectName || row.customerId || '—'}</td>
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
