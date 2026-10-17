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
import { Scale } from 'lucide-react'
import { financeOpsApi } from '../api/client'

/** FinanceAdvanced collections shell (PR-5a / PR-9) — /api/finance/collections */
export default function FinanceCollectionsPage() {
  const { t } = useTranslation(['desks', 'common'])
  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await financeOpsApi.collectionsOverdue()
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
          <Scale size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('collections.title', { defaultValue: 'Collections' })}
        </h1>
        <p className="page-sub">
          {t('collections.subtitle', {
            defaultValue: 'FinanceAdvanced overdue AR queue and promises. Silver pack / Iron eval.',
          })}
        </p>
        <p>
          <Link to="/finance/invoices">
            {t('collections.backInvoices', { defaultValue: '← Receivables' })}
          </Link>
        </p>
      </header>

      {error && (
        <div className="card" style={{ borderColor: 'var(--danger, #b91c1c)', marginBottom: '1rem' }}>
          <strong>
            {t('collections.loadError', { defaultValue: 'Could not load collections queue' })}
          </strong>
          <p className="muted" style={{ margin: '0.5rem 0 0' }}>
            {error}
          </p>
        </div>
      )}

      <section className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 className="section-label" style={{ margin: 0 }}>
            {t('collections.overdue', { defaultValue: 'Overdue queue' })}
          </h2>
          <button type="button" className="btn btn-ghost" onClick={load} disabled={loading}>
            {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
          </button>
        </div>
        {loading ? (
          <p className="muted">{t('collections.loading', { defaultValue: 'Loading…' })}</p>
        ) : items.length === 0 ? (
          <p className="muted">
            {t('collections.empty', {
              defaultValue: 'No overdue invoices. Queue is empty — honest empty state.',
            })}
          </p>
        ) : (
          <>
            <p className="muted">
              {t('collections.count', { defaultValue: '{{total}} overdue', total })}
            </p>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>{t('collections.colInvoice', { defaultValue: 'Invoice' })}</th>
                    <th>{t('collections.colCustomer', { defaultValue: 'Customer' })}</th>
                    <th>{t('collections.colDue', { defaultValue: 'Due' })}</th>
                    <th>{t('collections.colDays', { defaultValue: 'Days overdue' })}</th>
                    <th>{t('collections.colOpen', { defaultValue: 'Open' })}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((row) => (
                    <tr key={row.id || row.invoiceId}>
                      <td>{row.invoiceNumber || row.number || row.id || row.invoiceId}</td>
                      <td>{row.customerName || row.customerId || '—'}</td>
                      <td>{row.dueDate ? String(row.dueDate).slice(0, 10) : '—'}</td>
                      <td>{row.daysOverdue ?? '—'}</td>
                      <td>{row.amountOpenEur ?? row.amountOpen ?? '—'}</td>
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
