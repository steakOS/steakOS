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

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { BookOpen, RefreshCw, Plus, RotateCcw, Lock } from 'lucide-react'
import { financeLedgerApi } from '../api/client'
import useRecordView from '../hooks/useRecordView'
import ResponsiveDataList from '../components/ResponsiveDataList'
import ListPager from '../components/ListPager'

function currentPeriod() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return `${d.getFullYear()}-${m}`
}

function newEventId() {
  return `ui-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

const emptyLine = () => ({
  accountCode: '',
  debitEur: '',
  creditEur: '',
  memo: '',
})

/**
 * F-S1 ledger workbench: seed COA, post journals, trial balance, reverse, soft-close.
 */
export default function FinanceTransactions() {
  const { t } = useTranslation(['desks', 'common'])
  const { viewModal, ViewButton } = useRecordView()
  const [period, setPeriod] = useState(currentPeriod)
  const [accounts, setAccounts] = useState([])
  const [formAccounts, setFormAccounts] = useState([])
  const [coaPage, setCoaPage] = useState({ max: 25, offset: 0, total: 0 })
  const [tb, setTb] = useState(null)
  const [lastEntry, setLastEntry] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(null)

  const [entryName, setEntryName] = useState('')  /* set default after t available */
  const [lines, setLines] = useState([
    { accountCode: '1000', debitEur: '100', creditEur: '', memo: 'Cash' },
    { accountCode: '3000', debitEur: '', creditEur: '100', memo: 'Equity' },
  ])

  const loadAccounts = useCallback(async () => {
    const [listData, formData] = await Promise.all([
      financeLedgerApi.accounts({ max: coaPage.max, offset: coaPage.offset }),
      financeLedgerApi.accounts({ max: 100, offset: 0 }),
    ])
    setAccounts(listData.items || [])
    setCoaPage((pg) => ({
      ...pg,
      max: listData.max ?? pg.max,
      offset: listData.offset ?? pg.offset,
      total: listData.total ?? (listData.items || []).length,
    }))
    setFormAccounts(formData.items || [])
    return listData.items || []
  }, [coaPage.max, coaPage.offset])

  const loadTb = useCallback(async () => {
    const data = await financeLedgerApi.trialBalance(period)
    setTb(data)
    return data
  }, [period])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      await loadAccounts()
      await loadTb()
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [loadAccounts, loadTb])

  useEffect(() => {
    refresh()
  }, [refresh])

  const totals = useMemo(() => {
    let d = 0
    let c = 0
    lines.forEach((l) => {
      d += Number(l.debitEur) || 0
      c += Number(l.creditEur) || 0
    })
    return { debit: d, credit: c, balanced: Math.abs(d - c) < 1e-9 && d > 0 }
  }, [lines])

  const onSeed = async () => {
    setBusy('seed')
    setError(null)
    setInfo(null)
    try {
      const data = await financeLedgerApi.seed()
      setInfo(t('toast.coaSeeded', { ns: 'common', count: data.count, owner: data.npubOwner }))
      await refresh()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(null)
    }
  }

  const onPost = async (e) => {
    e.preventDefault()
    setBusy('post')
    setError(null)
    setInfo(null)
    try {
      if (!totals.balanced) {
        throw new Error(t('finance.balanceError'))
      }
      const body = {
        name: entryName || t('finance.manualJournal'),
        period,
        eventId: newEventId(),
        sourceType: 'MANUAL',
        lines: lines.map((l) => ({
          accountCode: l.accountCode,
          debitEur: Number(l.debitEur) || 0,
          creditEur: Number(l.creditEur) || 0,
          memo: l.memo || null,
        })),
      }
      const entry = await financeLedgerApi.postEntry(body)
      setLastEntry(entry)
      setInfo(t('toast.postedJe', { ns: 'common', id: entry.id, period: entry.period, eventId: entry.eventId }))
      await loadTb()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onReverse = async () => {
    if (!lastEntry?.id) return
    setBusy('reverse')
    setError(null)
    setInfo(null)
    try {
      const rev = await financeLedgerApi.reverse(lastEntry.id, { reason: 'UI reverse' })
      setLastEntry(rev)
      setInfo(t('toast.reversalJe', { ns: 'common', id: rev.id, of: rev.reversesEntryId }))
      await loadTb()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onClosePeriod = async () => {
    if (!window.confirm(t('toast.closePeriodConfirm', { ns: 'common', period }))) {
      return
    }
    setBusy('close')
    setError(null)
    setInfo(null)
    try {
      const r = await financeLedgerApi.closePeriod(period)
      setInfo(
        r.alreadyClosed
          ? `Period ${period} was already closed`
          : `Period ${period} closed (marker #${r.markerEntryId})`,
      )
      await loadTb()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const updateLine = (idx, field, value) => {
    setLines((prev) => {
      const next = [...prev]
      next[idx] = { ...next[idx], [field]: value }
      return next
    })
  }

  return (
    <div className="page finance-tx-page">
      <header className="page-header" style={{ marginBottom: '1.25rem' }}>
        <h1 className="page-title">
          <BookOpen size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('finance.ledgerTitle')}
        </h1>
        <p className="page-sub">
          {t('finance.ledgerSubtitle')}{' '}
          <input
            className="inline-period"
            value={period}
            onChange={(e) => setPeriod(e.target.value)}
            pattern="\d{4}-\d{2}"
            title="yyyy-MM"
            style={{ width: '7rem', marginLeft: 4 }}
          />
        </p>
      </header>

      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      {viewModal}

      <div className="toolbar">
        <button type="button" className="btn btn-primary" onClick={onSeed} disabled={!!busy}>
          {busy === 'seed' ? t('actions.seeding', { ns: 'common' }) : t('finance.seedCoa')}
        </button>
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <button type="button" className="btn" onClick={onClosePeriod} disabled={!!busy}>
          <Lock size={16} /> {t('finance.closePeriod')}
        </button>
        <Link className="btn btn-secondary" to="/domains/finance/Account">
          {t('finance.chartOfAccountsLink')}
        </Link>
        <Link className="btn btn-secondary" to="/domains/finance/JournalEntry">
          {t('finance.journalEntriesLink')}
        </Link>
      </div>

      <div className="two-col finance-ledger-layout">
        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('finance.postJournal')}
          </h2>
          <form className="form-grid" onSubmit={onPost}>
            <label>
              {t('finance.entryName')}
              <input value={entryName} onChange={(e) => setEntryName(e.target.value)} />
            </label>

            <div className="tx-lines finance-tx-grid">
              <div className="tx-line-head">
                <span>{t('finance.account')}</span>
                <span>{t('finance.debitEur')}</span>
                <span>{t('finance.creditEur')}</span>
                <span>{t('finance.memo')}</span>
              </div>
              {lines.map((line, idx) => (
                <div className="tx-line-row" key={idx}>
                  <select
                    value={line.accountCode}
                    onChange={(e) => updateLine(idx, 'accountCode', e.target.value)}
                    required
                  >
                    <option value="">{t('finance.selectCode')}</option>
                    {formAccounts.map((a) => (
                      <option key={a.id} value={a.code}>
                        {a.code} · {a.name}
                      </option>
                    ))}
                    {/* allow typed codes if COA empty */}
                    {!formAccounts.find((a) => a.code === line.accountCode) && line.accountCode && (
                      <option value={line.accountCode}>{line.accountCode}</option>
                    )}
                  </select>
                  <input
                    inputMode="decimal"
                    placeholder="0"
                    value={line.debitEur}
                    onChange={(e) => updateLine(idx, 'debitEur', e.target.value)}
                  />
                  <input
                    inputMode="decimal"
                    placeholder="0"
                    value={line.creditEur}
                    onChange={(e) => updateLine(idx, 'creditEur', e.target.value)}
                  />
                  <input
                    placeholder={t('ph.memo', { ns: 'common' })}
                    value={line.memo}
                    onChange={(e) => updateLine(idx, 'memo', e.target.value)}
                  />
                </div>
              ))}
            </div>

            <div className="toolbar" style={{ marginBottom: 0 }}>
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => setLines((p) => [...p, emptyLine()])}
              >
                <Plus size={16} /> {t('finance.addLine')}
              </button>
              <span className={`tx-totals ${totals.balanced ? 'ok' : 'bad'}`}>
                {t('finance.drCr', {
                  debit: totals.debit.toFixed(2),
                  credit: totals.credit.toFixed(2),
                })}
                {totals.balanced ? t('finance.balanced') : t('finance.notBalanced')}
              </span>
            </div>

            <div className="toolbar" style={{ marginBottom: 0 }}>
              <button type="submit" className="btn btn-primary" disabled={!!busy || !totals.balanced}>
                {busy === 'post' ? t('actions.posting', { ns: 'common' }) : t('finance.postEntry')}
              </button>
              {lastEntry?.id && !lastEntry.reversesEntryId && (
                <button type="button" className="btn" onClick={onReverse} disabled={!!busy}>
                  <RotateCcw size={16} /> {t('finance.reverseLast', { id: lastEntry.id })}
                </button>
              )}
            </div>
          </form>

          {lastEntry && (
            <div className="tx-last mono" style={{ marginTop: '1rem' }}>
              {t('finance.lastEntry', {
                id: lastEntry.id,
                name: lastEntry.name,
                period: lastEntry.period,
                eventId: lastEntry.eventId,
              })}
              {lastEntry.reversesEntryId
                ? t('finance.revOf', { id: lastEntry.reversesEntryId })
                : ''}
            </div>
          )}
        </section>

        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('finance.trialBalance', { period })}
            {tb?.closed ? t('finance.trialBalanceClosed') : ''}
          </h2>
          {loading && !tb && (
            <p className="muted">{t('actions.loading', { ns: 'common' })}</p>
          )}
          {tb && (
            <>
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.code', { ns: 'common' })}</th>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th>{t('finance.debit')}</th>
                      <th>{t('finance.credit')}</th>
                      <th>{t('finance.net')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(tb.lines || []).length === 0 && (
                      <tr>
                        <td colSpan={5} className="muted">
                          {t('finance.emptyPeriod')}
                        </td>
                      </tr>
                    )}
                    {(tb.lines || []).map((row) => (
                      <tr key={row.accountId}>
                        <td className="mono">{row.code}</td>
                        <td>{row.name}</td>
                        <td>{Number(row.debitsEur).toFixed(2)}</td>
                        <td>{Number(row.creditsEur).toFixed(2)}</td>
                        <td>{Number(row.balanceEur).toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td colSpan={2}>
                        <strong>{t('wb.total', { ns: 'common' })}</strong>
                      </td>
                      <td>
                        <strong>{Number(tb.totalDebits).toFixed(2)}</strong>
                      </td>
                      <td>
                        <strong>{Number(tb.totalCredits).toFixed(2)}</strong>
                      </td>
                      <td className={tb.balanced ? 'ok-text' : 'bad-text'}>
                        {tb.balanced ? t('finance.balanceOk') : t('finance.balanceOut')}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>
              <p className="muted" style={{ marginTop: '0.75rem', fontSize: '0.85rem' }}>
                {t('finance.functionalNote')}{' '}
                <Link to="/domains/finance/Account">{t('finance.account')}</Link>,{' '}
                <Link to="/domains/finance/JournalEntry">JournalEntry</Link>,{' '}
                <Link to="/domains/finance/JournalLine">JournalLine</Link>.
              </p>
            </>
          )}

          <h2 className="section-label">
            {t('finance.coaCount', { count: coaPage.total || accounts.length })}
          </h2>
          <ResponsiveDataList
            items={accounts}
            empty={t('finance.emptyCoa')}
            fields={[
              {
                id: 'code',
                primary: true,
                render: (a) => (
                  <>
                    <span className="mono">{a.code}</span> {a.name}
                  </>
                ),
              },
              {
                id: 'type',
                secondary: true,
                render: (a) => a.accountType,
              },
            ]}
            actions={(a) => (
              <ViewButton pkg="finance" resource="Account" record={a} />
            )}
          >
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('wb.code', { ns: 'common' })}</th>
                    <th>{t('wb.name', { ns: 'common' })}</th>
                    <th className="col-priority-low">{t('wb.type', { ns: 'common' })}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {accounts.length === 0 && (
                    <tr>
                      <td colSpan={4} className="muted">
                        {t('finance.emptyCoa')}
                      </td>
                    </tr>
                  )}
                  {accounts.map((a) => (
                    <tr key={a.id}>
                      <td className="mono">{a.code}</td>
                      <td>{a.name}</td>
                      <td className="col-priority-low">{a.accountType}</td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        <ViewButton pkg="finance" resource="Account" record={a} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </ResponsiveDataList>
          <ListPager
            total={coaPage.total}
            max={coaPage.max}
            offset={coaPage.offset}
            disabled={loading || !!busy}
            onOffset={(o) => setCoaPage((pg) => ({ ...pg, offset: o }))}
            onMax={(m) => setCoaPage((pg) => ({ ...pg, max: m, offset: 0 }))}
          />
        </section>
      </div>
    </div>
  )
}
