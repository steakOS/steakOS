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
import ResponsiveDataList from '../components/ResponsiveDataList'
import { financeOpsApi } from '../api/client'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'

function fmtMoney(v) {
  if (v == null || v === '') return '—'
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : String(v)
}

function isSilverDenied(e) {
  return e?.status === 403 || e?.code === 'ADVANCED_REQUIRES_SILVER'
}

function canAct(status) {
  const s = String(status || '').toUpperCase()
  return s === 'PROPOSED'
}

/** FinanceAdvanced three-way match desk — matches + propose from candidates. */
export default function FinanceMatchPage() {
  const { t } = useTranslation(['desks', 'common'])
  const [tab, setTab] = useState('matches')
  const [items, setItems] = useState([])
  const [candidates, setCandidates] = useState([])
  const [gaps, setGaps] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [busy, setBusy] = useState(false)
  const [selected, setSelected] = useState(null)
  const [rejectNote, setRejectNote] = useState('')

  const friendlyError = useCallback(
    (e) => {
      if (isSilverDenied(e)) {
        return t('match.silverRequired', {
          defaultValue:
            'Three-way match needs the Silver pack (or Iron eval). Upgrade the company template in Settings to use Finance Advanced.',
        })
      }
      return e?.message || String(e)
    },
    [t],
  )

  const loadMatches = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await financeOpsApi.matchList()
      setItems(data?.items || [])
      setTotal(data?.total ?? (data?.items || []).length)
      if (Array.isArray(data?.gaps)) setGaps(data.gaps)
    } catch (e) {
      setItems([])
      setTotal(0)
      setError(friendlyError(e))
    } finally {
      setLoading(false)
    }
  }, [friendlyError])

  const loadCandidates = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await financeOpsApi.matchCandidates()
      setCandidates(data?.items || [])
      setTotal(data?.total ?? (data?.items || []).length)
      if (Array.isArray(data?.gaps)) setGaps(data.gaps)
    } catch (e) {
      setCandidates([])
      setTotal(0)
      setError(friendlyError(e))
    } finally {
      setLoading(false)
    }
  }, [friendlyError])

  const load = useCallback(() => {
    if (tab === 'candidates') return loadCandidates()
    return loadMatches()
  }, [tab, loadCandidates, loadMatches])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    setSelected(null)
    setRejectNote('')
    setInfo(null)
  }, [tab])

  const selectMatch = async (row) => {
    setInfo(null)
    setError(null)
    try {
      const full = await financeOpsApi.matchGet(row.id)
      setSelected(full || row)
      if (Array.isArray(full?.gaps)) setGaps(full.gaps)
    } catch (e) {
      setSelected(row)
      setError(friendlyError(e))
    }
  }

  const runAction = async (action) => {
    if (!selected?.id) return
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      let result
      if (action === 'confirm') {
        result = await financeOpsApi.matchConfirm(selected.id)
        setInfo(t('match.confirmed', { defaultValue: 'Match confirmed.' }))
      } else if (action === 'reject') {
        result = await financeOpsApi.matchReject(selected.id, {
          description: rejectNote.trim() || undefined,
        })
        setInfo(t('match.rejected', { defaultValue: 'Match rejected.' }))
        setRejectNote('')
      } else if (action === 'cancel') {
        result = await financeOpsApi.matchCancel(selected.id)
        setInfo(t('match.cancelled', { defaultValue: 'Match cancelled.' }))
      }
      setSelected(result || selected)
      await loadMatches()
    } catch (e) {
      setError(friendlyError(e))
    } finally {
      setBusy(false)
    }
  }

  const proposeCandidate = async (cand) => {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      const receiptId = cand.receipts?.[0]?.receiptId
      const body = {
        purchaseOrderId: cand.purchaseOrderId,
      }
      if (cand.payableId != null) body.payableId = cand.payableId
      if (receiptId != null) body.receiptId = receiptId
      const created = await financeOpsApi.matchPropose(body)
      setInfo(
        t('match.proposed', {
          defaultValue: 'Match proposed for PO {{po}}.',
          po: entityDisplayLabel(cand, {
            keys: ['poNumber', 'purchaseOrderNumber'],
            fallback: '—',
          }),
        }),
      )
      setTab('matches')
      setSelected(created)
      await loadMatches()
    } catch (e) {
      setError(friendlyError(e))
    } finally {
      setBusy(false)
    }
  }

  const statusOf = (row) => row?.matchStatus || row?.status || '—'

  return (
    <div className="page">
      <header className="page-header">
        <h1>
          <GitCompareArrows size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('match.title', { defaultValue: 'Three-way match (Experimental)' })}
        </h1>
        <p className="page-sub">
          {t('match.subtitle', {
            defaultValue:
              'Match purchase orders, receipts, and payables before AP pay. Silver pack / Iron eval.',
          })}
        </p>
        <p>
          <Link to="/finance/payables">
            {t('match.backPayables', { defaultValue: '← Payables' })}
          </Link>
        </p>
      </header>

      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <button
          type="button"
          className={`btn ${tab === 'matches' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTab('matches')}
        >
          {t('match.tabMatches', { defaultValue: 'Matches' })}
        </button>
        <button
          type="button"
          className={`btn ${tab === 'candidates' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTab('candidates')}
        >
          {t('match.tabCandidates', { defaultValue: 'Propose from candidates' })}
        </button>
        <button type="button" className="btn btn-ghost" onClick={load} disabled={loading}>
          {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
      </div>

      {error && (
        <div className="card" style={{ borderColor: 'var(--danger, #b91c1c)', marginBottom: '1rem' }}>
          <strong>{t('match.loadError', { defaultValue: 'Could not load matches' })}</strong>
          <p className="muted" style={{ margin: '0.5rem 0 0' }}>
            {error}
          </p>
        </div>
      )}
      {info && <div className="banner-info">{info}</div>}

      {gaps?.length > 0 && (
        <p className="muted" style={{ fontSize: '0.85rem', marginBottom: '0.75rem' }}>
          {t('match.gapsTip', {
            defaultValue: 'Honesty notes: {{notes}}',
            notes: gaps.slice(0, 3).join(' · '),
          })}
        </p>
      )}

      {tab === 'matches' && (
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
              {t('match.list', { defaultValue: 'Matches' })}
            </h2>
            {loading ? (
              <p className="muted">{t('match.loading', { defaultValue: 'Loading…' })}</p>
            ) : items.length === 0 ? (
              <p className="muted">
                {t('match.empty', {
                  defaultValue:
                    'No three-way matches yet. Use Propose from candidates when PO, receipt, and payable are ready.',
                })}
              </p>
            ) : (
              <>
                <p className="muted" style={{ marginTop: 0 }}>
                  {t('match.count', { defaultValue: '{{total}} match(es)', total })}
                </p>
                <ResponsiveDataList
                  items={items}
                  empty={t('match.empty', {
                    defaultValue:
                      'No three-way matches yet. Use Propose from candidates when PO, receipt, and payable are ready.',
                  })}
                  selectedKey={selected?.id}
                  onSelect={(row) => selectMatch(row)}
                  fields={[
                    {
                      id: 'status',
                      primary: true,
                      render: (row) => statusOf(row),
                    },
                    {
                      id: 'po',
                      secondary: true,
                      render: (row) =>
                        entityDisplayLabel(row, {
                          keys: ['poNumber', 'purchaseOrderNumber'],
                          fallback: '—',
                        }),
                    },
                    {
                      id: 'variance',
                      meta: true,
                      render: (row) => fmtMoney(row.varianceEur),
                    },
                  ]}
                >
                  <div className="table-wrap">
                    <table className="data">
                      <thead>
                        <tr>
                          <th>{t('match.colStatus', { defaultValue: 'Status' })}</th>
                          <th>{t('match.colPo', { defaultValue: 'PO' })}</th>
                          <th>{t('match.colPayable', { defaultValue: 'Payable' })}</th>
                          <th>{t('match.colVariance', { defaultValue: 'Variance' })}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {items.map((row) => (
                          <tr
                            key={row.id}
                            data-selected={selected?.id === row.id ? 'true' : undefined}
                            className={selected?.id === row.id ? 'row-selected' : 'row-click'}
                            onClick={() => selectMatch(row)}
                          >
                            <td>{statusOf(row)}</td>
                            <td>
                              {entityDisplayLabel(row, {
                                keys: ['poNumber', 'purchaseOrderNumber'],
                                fallback: '—',
                              })}
                            </td>
                            <td>
                              {entityDisplayLabel(row, {
                                keys: ['payableNumber'],
                                fallback: '—',
                              })}
                            </td>
                            <td>{fmtMoney(row.varianceEur)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </ResponsiveDataList>
              </>
            )}
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('match.detail', { defaultValue: 'Match detail' })}
            </h2>
            {!selected && (
              <p className="muted">
                {t('match.selectHint', {
                  defaultValue: 'Select a match to confirm, reject, or cancel.',
                })}
              </p>
            )}
            {selected && (
              <>
                <div className="inv-detail-hero" style={{ marginBottom: '0.75rem' }}>
                  <div>
                    <div className="about-display" style={{ fontSize: '1.2rem' }}>
                      {statusOf(selected)}
                    </div>
                    <div className="muted">
                      {t('match.colPo', { defaultValue: 'PO' })}{' '}
                      {entityDisplayLabel(selected, {
                        keys: ['poNumber', 'purchaseOrderNumber'],
                        fallback: '—',
                      })}
                      {' · '}
                      {t('match.colPayable', { defaultValue: 'Payable' })}{' '}
                      {entityDisplayLabel(selected, {
                        keys: ['payableNumber'],
                        fallback: '—',
                      })}
                      {selected.receiptId != null
                        ? ` · ${t('match.receipt', { defaultValue: 'Receipt' })}`
                        : ''}
                    </div>
                    {selected.description && (
                      <div style={{ fontSize: '0.9rem', marginTop: 4 }}>{selected.description}</div>
                    )}
                  </div>
                  <div className="inv-amount">
                    <div className="muted" style={{ fontSize: '0.8rem' }}>
                      {t('match.colVariance', { defaultValue: 'Variance' })}
                    </div>
                    {fmtMoney(selected.varianceEur)} EUR
                  </div>
                </div>
                <p className="muted" style={{ fontSize: '0.85rem' }}>
                  PO {fmtMoney(selected.amountPoEur)} · Payable{' '}
                  {fmtMoney(selected.amountPayableEur)}
                  {selected.amountOk != null ? ` · amountOk=${String(selected.amountOk)}` : ''}
                  {selected.qtyOk != null ? ` · qtyOk=${String(selected.qtyOk)}` : ''}
                </p>

                {canAct(statusOf(selected)) ? (
                  <>
                    <div className="toolbar" style={{ marginBottom: 8 }}>
                      <button
                        type="button"
                        className="btn btn-primary"
                        disabled={busy}
                        onClick={() => runAction('confirm')}
                      >
                        {t('match.confirm', { defaultValue: 'Confirm' })}
                      </button>
                      <button
                        type="button"
                        className="btn"
                        disabled={busy}
                        onClick={() => runAction('reject')}
                      >
                        {t('match.reject', { defaultValue: 'Reject' })}
                      </button>
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={busy}
                        onClick={() => runAction('cancel')}
                      >
                        {t('match.cancel', { defaultValue: 'Cancel' })}
                      </button>
                    </div>
                    <label>
                      {t('match.rejectNote', {
                        defaultValue: 'Reject note (optional)',
                      })}
                      <textarea
                        rows={2}
                        value={rejectNote}
                        onChange={(ev) => setRejectNote(ev.target.value)}
                        style={{ width: '100%' }}
                      />
                    </label>
                  </>
                ) : (
                  <p className="muted">
                    {t('match.noActions', {
                      defaultValue: 'No actions for status {{status}}.',
                      status: statusOf(selected),
                    })}
                  </p>
                )}
              </>
            )}
          </section>
        </div>
      )}

      {tab === 'candidates' && (
        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('match.candidates', { defaultValue: 'Candidates' })}
          </h2>
          {loading ? (
            <p className="muted">{t('match.loading', { defaultValue: 'Loading…' })}</p>
          ) : candidates.length === 0 ? (
            <p className="muted">
              {t('match.emptyCandidates', {
                defaultValue:
                  'No matchable candidates. Need a PO with receipt and linked payable that is not already confirmed.',
              })}
            </p>
          ) : (
            <>
              <p className="muted" style={{ marginTop: 0 }}>
                {t('match.candidateCount', {
                  defaultValue: '{{total}} candidate(s)',
                  total,
                })}
              </p>
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('match.colPo', { defaultValue: 'PO' })}</th>
                      <th>{t('match.colVendor', { defaultValue: 'Vendor' })}</th>
                      <th>{t('match.colPayable', { defaultValue: 'Payable' })}</th>
                      <th>{t('match.colPoAmt', { defaultValue: 'PO amt' })}</th>
                      <th>{t('match.colPayAmt', { defaultValue: 'Payable amt' })}</th>
                      <th>{t('match.colVariance', { defaultValue: 'Variance' })}</th>
                      <th>{t('match.colReceipts', { defaultValue: 'Receipts' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {candidates.map((cand) => (
                      <tr key={`${cand.purchaseOrderId}-${cand.payableId}`}>
                        <td>
                          {entityDisplayLabel(cand, {
                            keys: ['poNumber', 'purchaseOrderNumber'],
                            fallback: '—',
                          })}
                        </td>
                        <td>{cand.vendorName || '—'}</td>
                        <td>
                          {entityDisplayLabel(cand, {
                            keys: ['payableNumber'],
                            fallback: '—',
                          })}
                        </td>
                        <td>{fmtMoney(cand.amountPoEur)}</td>
                        <td>{fmtMoney(cand.amountPayableEur)}</td>
                        <td>{fmtMoney(cand.varianceEur)}</td>
                        <td>{(cand.receipts || []).length}</td>
                        <td>
                          <button
                            type="button"
                            className="btn btn-primary btn-sm"
                            disabled={busy}
                            onClick={() => proposeCandidate(cand)}
                          >
                            {t('match.propose', { defaultValue: 'Propose' })}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </section>
      )}
    </div>
  )
}
