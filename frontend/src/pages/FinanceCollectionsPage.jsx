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
import ResponsiveDataList from '../components/ResponsiveDataList'
import { financeOpsApi } from '../api/client'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'

function fmtDate(v) {
  if (!v) return '—'
  return String(v).slice(0, 10)
}

function fmtMoney(v) {
  if (v == null || v === '') return '—'
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : String(v)
}

function isSilverDenied(e) {
  return e?.status === 403 || e?.code === 'ADVANCED_REQUIRES_SILVER'
}

/** FinanceAdvanced collections desk — overdue / open AR + notes & promises. */
export default function FinanceCollectionsPage() {
  const { t } = useTranslation(['desks', 'common'])
  const [tab, setTab] = useState('overdue')
  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [busy, setBusy] = useState(false)
  const [selectedId, setSelectedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [activities, setActivities] = useState([])
  const [actLoading, setActLoading] = useState(false)
  const [noteText, setNoteText] = useState('')
  const [promiseDate, setPromiseDate] = useState('')
  const [promiseAmount, setPromiseAmount] = useState('')
  const [promiseNote, setPromiseNote] = useState('')

  const friendlyError = useCallback(
    (e) => {
      if (isSilverDenied(e)) {
        return t('collections.silverRequired', {
          defaultValue:
            'Collections need the Silver pack (or Iron eval). Upgrade the company template in Settings to use Finance Advanced.',
        })
      }
      return e?.message || String(e)
    },
    [t],
  )

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data =
        tab === 'open'
          ? await financeOpsApi.collectionsOpen()
          : await financeOpsApi.collectionsOverdue()
      setItems(data?.items || [])
      setTotal(data?.total ?? (data?.items || []).length)
    } catch (e) {
      setItems([])
      setTotal(0)
      setError(friendlyError(e))
    } finally {
      setLoading(false)
    }
  }, [tab, friendlyError])

  const loadDetail = useCallback(
    async (invoiceId) => {
      if (!invoiceId) {
        setDetail(null)
        setActivities([])
        return
      }
      setActLoading(true)
      setError(null)
      try {
        const [item, acts] = await Promise.all([
          financeOpsApi.collectionsGet(invoiceId),
          financeOpsApi.collectionsListActivities(invoiceId),
        ])
        setDetail(item)
        setActivities(acts?.items || item?.activities || [])
        setPromiseAmount(
          item?.amountOpenEur != null ? String(item.amountOpenEur) : '',
        )
      } catch (e) {
        setDetail(null)
        setActivities([])
        setError(friendlyError(e))
      } finally {
        setActLoading(false)
      }
    },
    [friendlyError],
  )

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    setSelectedId(null)
    setDetail(null)
    setActivities([])
    setNoteText('')
    setPromiseDate('')
    setPromiseNote('')
  }, [tab])

  const selectInvoice = (row) => {
    const id = row.invoiceId || row.id
    setSelectedId(id)
    setInfo(null)
    loadDetail(id)
  }

  const refreshSelected = async () => {
    if (selectedId) await loadDetail(selectedId)
    await load()
  }

  const addNote = async (e) => {
    e.preventDefault()
    if (!selectedId || !noteText.trim()) return
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      await financeOpsApi.collectionsCreateActivity(selectedId, {
        kind: 'NOTE',
        description: noteText.trim(),
        name: noteText.trim().slice(0, 80),
      })
      setNoteText('')
      setInfo(t('collections.noteAdded', { defaultValue: 'Note recorded.' }))
      await refreshSelected()
    } catch (err) {
      setError(friendlyError(err))
    } finally {
      setBusy(false)
    }
  }

  const addPromise = async (e) => {
    e.preventDefault()
    if (!selectedId || !promiseDate) return
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      const body = { promisedPayBy: promiseDate }
      if (promiseAmount !== '' && promiseAmount != null) {
        body.promisedAmountEur = Number(promiseAmount)
      }
      if (promiseNote.trim()) body.description = promiseNote.trim()
      await financeOpsApi.collectionsCreatePromise(selectedId, body)
      setPromiseDate('')
      setPromiseNote('')
      setInfo(t('collections.promiseAdded', { defaultValue: 'Promise recorded.' }))
      await refreshSelected()
    } catch (err) {
      setError(friendlyError(err))
    } finally {
      setBusy(false)
    }
  }

  const setPromiseStatus = async (activityId, promiseStatus) => {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      await financeOpsApi.collectionsUpdatePromiseStatus(activityId, { promiseStatus })
      setInfo(
        t('collections.promiseUpdated', {
          defaultValue: 'Promise marked {{status}}.',
          status: promiseStatus,
        }),
      )
      await refreshSelected()
    } catch (err) {
      setError(friendlyError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>
          <Scale size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('collections.title', { defaultValue: 'Collections' })}
        </h1>
        <p className="page-sub">
          {t('collections.subtitle', {
            defaultValue:
              'Overdue and open receivables — notes, payment promises, and aging. Silver pack / Iron eval.',
          })}
        </p>
        <p>
          <Link to="/finance/invoices">
            {t('collections.backInvoices', { defaultValue: '← Receivables' })}
          </Link>
        </p>
      </header>

      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <button
          type="button"
          className={`btn ${tab === 'overdue' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTab('overdue')}
        >
          {t('collections.tabOverdue', { defaultValue: 'Overdue' })}
        </button>
        <button
          type="button"
          className={`btn ${tab === 'open' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTab('open')}
        >
          {t('collections.tabOpen', { defaultValue: 'All open AR' })}
        </button>
        <button type="button" className="btn btn-ghost" onClick={load} disabled={loading}>
          {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
      </div>

      {error && (
        <div className="card" style={{ borderColor: 'var(--danger, #b91c1c)', marginBottom: '1rem' }}>
          <strong>
            {t('collections.loadError', { defaultValue: 'Could not load collections' })}
          </strong>
          <p className="muted" style={{ margin: '0.5rem 0 0' }}>
            {error}
          </p>
        </div>
      )}
      {info && <div className="banner-info">{info}</div>}

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 1.2fr) minmax(280px, 1fr)',
          gap: '1rem',
          alignItems: 'start',
        }}
      >
        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {tab === 'open'
              ? t('collections.openQueue', { defaultValue: 'All open AR' })
              : t('collections.overdue', { defaultValue: 'Overdue queue' })}
          </h2>
          {loading ? (
            <p className="muted">{t('collections.loading', { defaultValue: 'Loading…' })}</p>
          ) : items.length === 0 ? (
            <p className="muted">
              {tab === 'open'
                ? t('collections.emptyOpen', {
                    defaultValue: 'No open receivables right now.',
                  })
                : t('collections.empty', {
                    defaultValue: 'No overdue invoices. Queue is empty.',
                  })}
            </p>
          ) : (
            <>
              <p className="muted" style={{ marginTop: 0 }}>
                {t('collections.count', { defaultValue: '{{total}} item(s)', total })}
              </p>
              <ResponsiveDataList
                items={items}
                getKey={(row) => row.invoiceId || row.id}
                empty={
                  tab === 'open'
                    ? t('collections.emptyOpen', {
                        defaultValue: 'No open receivables right now.',
                      })
                    : t('collections.empty', {
                        defaultValue: 'No overdue invoices. Queue is empty.',
                      })
                }
                selectedKey={selectedId}
                onSelect={(row) => selectInvoice(row)}
                fields={[
                  {
                    id: 'invoice',
                    primary: true,
                    render: (row) =>
                      entityDisplayLabel(row, { fallback: 'Invoice' }),
                  },
                  {
                    id: 'customer',
                    secondary: true,
                    render: (row) => row.customerName || row.customerId || '—',
                  },
                  {
                    id: 'due',
                    secondary: true,
                    render: (row) => fmtDate(row.dueDate),
                  },
                  {
                    id: 'open',
                    meta: true,
                    render: (row) => fmtMoney(row.amountOpenEur ?? row.amountOpen),
                  },
                ]}
              >
                <div className="table-wrap">
                  <table className="data">
                    <thead>
                      <tr>
                        <th>{t('collections.colInvoice', { defaultValue: 'Invoice' })}</th>
                        <th>{t('collections.colCustomer', { defaultValue: 'Customer' })}</th>
                        <th>{t('collections.colDue', { defaultValue: 'Due' })}</th>
                        <th>{t('collections.colDays', { defaultValue: 'Days overdue' })}</th>
                        <th>{t('collections.colOpen', { defaultValue: 'Open' })}</th>
                        <th>{t('collections.colBucket', { defaultValue: 'Aging' })}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {items.map((row) => {
                        const id = row.invoiceId || row.id
                        return (
                          <tr
                            key={id}
                            data-selected={selectedId === id ? 'true' : undefined}
                            className={selectedId === id ? 'row-selected' : 'row-click'}
                            onClick={() => selectInvoice(row)}
                          >
                            <td>{entityDisplayLabel(row, { fallback: 'Invoice' })}</td>
                            <td>{row.customerName || row.customerId || '—'}</td>
                            <td>{fmtDate(row.dueDate)}</td>
                            <td>{row.daysOverdue ?? '—'}</td>
                            <td>{fmtMoney(row.amountOpenEur ?? row.amountOpen)}</td>
                            <td>{row.agingBucket || '—'}</td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              </ResponsiveDataList>
            </>
          )}
        </section>

        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('collections.detail', { defaultValue: 'Invoice detail' })}
          </h2>
          {!selectedId && (
            <p className="muted">
              {t('collections.selectHint', {
                defaultValue: 'Select an invoice to view activities and record a note or promise.',
              })}
            </p>
          )}
          {selectedId && actLoading && (
            <p className="muted">{t('collections.loading', { defaultValue: 'Loading…' })}</p>
          )}
          {selectedId && !actLoading && detail && (
            <>
              <div className="inv-detail-hero" style={{ marginBottom: '0.75rem' }}>
                <div>
                  <div className="about-display" style={{ fontSize: '1.2rem' }}>
                    {entityDisplayLabel(detail, { fallback: 'Invoice' })}
                  </div>
                  <div className="muted">
                    {detail.customerName || detail.customerId || '—'} ·{' '}
                    {t('collections.dueLabel', { defaultValue: 'Due' })} {fmtDate(detail.dueDate)}
                    {detail.agingBucket ? ` · ${detail.agingBucket}` : ''}
                  </div>
                </div>
                <div className="inv-amount">
                  {fmtMoney(detail.amountOpenEur)} EUR
                  <div className="muted" style={{ fontSize: '0.8rem' }}>
                    {t('collections.openAmount', { defaultValue: 'Open' })}
                  </div>
                </div>
              </div>

              <h3 className="section-label">
                {t('collections.activities', { defaultValue: 'Activities' })}
              </h3>
              {activities.length === 0 ? (
                <p className="muted">
                  {t('collections.noActivities', {
                    defaultValue: 'No notes or promises yet for this invoice.',
                  })}
                </p>
              ) : (
                <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 1rem' }}>
                  {activities.map((a) => (
                    <li
                      key={a.id}
                      style={{
                        borderBottom: '1px solid var(--border, #e5e7eb)',
                        padding: '0.5rem 0',
                      }}
                    >
                      <div>
                        <strong>{a.kind}</strong>
                        {a.kind === 'PROMISE' && a.promiseStatus ? ` · ${a.promiseStatus}` : ''}
                        <span className="muted"> · {fmtDate(a.activityAt || a.dateCreated)}</span>
                      </div>
                      {a.kind === 'PROMISE' && (
                        <div className="muted" style={{ fontSize: '0.9rem' }}>
                          {t('collections.promisedBy', { defaultValue: 'Pay by' })}{' '}
                          {fmtDate(a.promisedPayBy)}
                          {a.promisedAmountEur != null
                            ? ` · ${fmtMoney(a.promisedAmountEur)} EUR`
                            : ''}
                        </div>
                      )}
                      {a.description && (
                        <div style={{ fontSize: '0.9rem' }}>{a.description}</div>
                      )}
                      {a.kind === 'PROMISE' && a.promiseStatus === 'OPEN' && (
                        <div className="toolbar" style={{ marginTop: 6 }}>
                          {['KEPT', 'BROKEN', 'CANCELLED'].map((st) => (
                            <button
                              key={st}
                              type="button"
                              className="btn btn-ghost btn-sm"
                              disabled={busy}
                              onClick={() => setPromiseStatus(a.id, st)}
                            >
                              {t(`collections.promise${st}`, { defaultValue: st })}
                            </button>
                          ))}
                        </div>
                      )}
                    </li>
                  ))}
                </ul>
              )}

              <form onSubmit={addNote} style={{ marginBottom: '1rem' }}>
                <h3 className="section-label">
                  {t('collections.addNote', { defaultValue: 'Add note' })}
                </h3>
                <textarea
                  rows={2}
                  value={noteText}
                  onChange={(ev) => setNoteText(ev.target.value)}
                  placeholder={t('collections.notePlaceholder', {
                    defaultValue: 'Call outcome, email summary…',
                  })}
                  style={{ width: '100%' }}
                />
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={busy || !noteText.trim()}
                  style={{ marginTop: 6 }}
                >
                  {t('collections.saveNote', { defaultValue: 'Save note' })}
                </button>
              </form>

              <form onSubmit={addPromise}>
                <h3 className="section-label">
                  {t('collections.addPromise', { defaultValue: 'Add promise' })}
                </h3>
                <div className="form-grid" style={{ marginBottom: 6 }}>
                  <label>
                    {t('collections.promisedPayBy', { defaultValue: 'Promised pay by' })} *
                    <input
                      type="date"
                      required
                      value={promiseDate}
                      onChange={(ev) => setPromiseDate(ev.target.value)}
                    />
                  </label>
                  <label>
                    {t('collections.promisedAmount', {
                      defaultValue: 'Amount (EUR, optional)',
                    })}
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={promiseAmount}
                      onChange={(ev) => setPromiseAmount(ev.target.value)}
                      placeholder={fmtMoney(detail.amountOpenEur)}
                    />
                  </label>
                </div>
                <textarea
                  rows={2}
                  value={promiseNote}
                  onChange={(ev) => setPromiseNote(ev.target.value)}
                  placeholder={t('collections.promisePlaceholder', {
                    defaultValue: 'Optional context…',
                  })}
                  style={{ width: '100%' }}
                />
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={busy || !promiseDate}
                  style={{ marginTop: 6 }}
                >
                  {t('collections.savePromise', { defaultValue: 'Save promise' })}
                </button>
              </form>
            </>
          )}
        </section>
      </div>
    </div>
  )
}
