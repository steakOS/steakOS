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
import { Handshake } from 'lucide-react'
import { stakeholderApi } from '../api/client'

/**
 * StakeholderBasics thin desk (G7 / PR-9) — parties + comms via /api/stakeholder.
 */
export default function StakeholderDesk() {
  const { t } = useTranslation(['desks', 'common'])
  const [tab, setTab] = useState('parties')
  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data =
        tab === 'parties'
          ? await stakeholderApi.parties.list({ max: 25, offset: 0 })
          : await stakeholderApi.comms.list({ max: 25, offset: 0 })
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
          <Handshake size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('stakeholder.title', { defaultValue: 'Stakeholders' })}
        </h1>
        <p className="page-sub">
          {t('stakeholder.subtitle', {
            defaultValue:
              'External parties, ethics/comms log, and light contract register. Silver pack / Iron eval.',
          })}
        </p>
      </header>

      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
        <button
          type="button"
          className={`btn ${tab === 'parties' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTab('parties')}
        >
          {t('stakeholder.parties', { defaultValue: 'Parties' })}
        </button>
        <button
          type="button"
          className={`btn ${tab === 'comms' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTab('comms')}
        >
          {t('stakeholder.comms', { defaultValue: 'Communications' })}
        </button>
        <button type="button" className="btn btn-ghost" onClick={load} disabled={loading}>
          {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
      </div>

      {error && (
        <div className="card" style={{ borderColor: 'var(--danger, #b91c1c)', marginBottom: '1rem' }}>
          <strong>
            {t('stakeholder.loadError', { defaultValue: 'Could not load stakeholder data' })}
          </strong>
          <p className="muted" style={{ margin: '0.5rem 0 0' }}>
            {error}
          </p>
        </div>
      )}

      <section className="card">
        {loading ? (
          <p className="muted">{t('stakeholder.loading', { defaultValue: 'Loading…' })}</p>
        ) : items.length === 0 ? (
          <p className="muted">
            {tab === 'parties'
              ? t('stakeholder.emptyParties', {
                  defaultValue: 'No parties yet. Register stakeholders via /api/stakeholder/parties.',
                })
              : t('stakeholder.emptyComms', {
                  defaultValue: 'No communications logged yet.',
                })}
          </p>
        ) : (
          <>
            <p className="muted" style={{ marginTop: 0 }}>
              {t('stakeholder.count', { defaultValue: '{{total}} item(s)', total })}
            </p>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>{t('stakeholder.colName', { defaultValue: 'Name / subject' })}</th>
                    <th>{t('stakeholder.colStatus', { defaultValue: 'Status' })}</th>
                    <th>
                      {t('stakeholder.colExtra', {
                        defaultValue: tab === 'parties' ? 'Kind' : 'Channel',
                      })}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((row) => (
                    <tr key={row.id}>
                      <td>{row.name || row.subject || `#${row.id}`}</td>
                      <td>{row.status || '—'}</td>
                      <td>{row.partyKind || row.channel || row.commsKind || '—'}</td>
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
