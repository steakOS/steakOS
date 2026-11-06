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
import { financeLedgerApi, meApi } from '../api/client'
import useRecordView from '../hooks/useRecordView'
import ResponsiveDataList from '../components/ResponsiveDataList'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'
import DeskShell from '../components/desk/DeskShell'
import DeskProcessSlot from '../components/desk/DeskProcessSlot'
import DeskNewFab from '../components/DeskNewFab'
import ConfirmDialog from '../components/ConfirmDialog'
import useDeskSelection, { scrollSelectedIntoView } from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'
import useNarrowViewport from '../hooks/useNarrowViewport'

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

const DEFAULT_LINES = () => [
  { accountCode: '1000', debitEur: '100', creditEur: '', memo: 'Cash' },
  { accountCode: '3000', debitEur: '', creditEur: '100', memo: 'Equity' },
]

/**
 * F-S1 ledger workbench: seed COA, post journals, trial balance, reverse, soft-close.
 * DeskShell layout (ADR-047 / Nielsen WP-0, WP-2). finance-tx-grid kept for JE line editor (plan §4.3).
 */
export default function FinanceTransactions() {
  const { t } = useTranslation(['desks', 'common'])
  const { viewModal, ViewButton } = useRecordView()
  const narrow = useNarrowViewport()

  const [tab] = useState('accounts')
  const [selectedId, setSelectedId] = useState(() => {
    if (typeof window === 'undefined') return null
    const id = new URLSearchParams(window.location.search).get('id')
    return id || null
  })
  const [newExpanded, setNewExpanded] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [pendingNav, setPendingNav] = useState(null)
  const [companyId, setCompanyId] = useState('default')

  const { loadMemory, saveMemory } = useDeskMemory({
    deskKey: 'finance-transactions',
    companyId,
  })

  useDeskSelection({
    deskKey: 'finance-transactions',
    defaultTab: 'accounts',
    tab,
    setTab: () => {},
    selectedId,
    setSelectedId,
  })

  useEffect(() => {
    saveMemory({ tab })
  }, [tab, saveMemory])

  useEffect(() => {
    if (companyId === 'default') return
    loadMemory()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [companyId])

  useEffect(() => {
    let cancelled = false
    meApi
      .get()
      .then((me) => {
        if (cancelled) return
        const cid = me?.activeCompanyId ?? me?.activeCompany?.id
        if (cid != null && cid !== '') setCompanyId(String(cid))
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.get('new') === '1') {
      setNewExpanded(true)
      params.delete('new')
      const qs = params.toString()
      window.history.replaceState(
        null,
        '',
        `${window.location.pathname}${qs ? `?${qs}` : ''}`,
      )
    }
  }, [])

  const [period, setPeriod] = useState(currentPeriod)
  const [accounts, setAccounts] = useState([])
  const [formAccounts, setFormAccounts] = useState([])
  const [coaPage, setCoaPage] = useState({ max: 25, offset: 0, total: 0 })
  const [coaSort, setCoaSort] = useState('code')
  const [coaOrder, setCoaOrder] = useState('asc')
  const [tb, setTb] = useState(null)
  const [lastEntry, setLastEntry] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(null)

  const [entryName, setEntryName] = useState('')
  const [lines, setLines] = useState(DEFAULT_LINES)

  const COA_SORT_MENU = useMemo(
    () => [
      { value: 'code', label: t('wb.code', { ns: 'common' }) },
      { value: 'name', label: t('wb.name', { ns: 'common' }) },
      { value: 'accountType', label: t('wb.type', { ns: 'common' }) },
    ],
    [t],
  )

  const onCoaSortChange = useCallback(({ sort: s, order: o }) => {
    setCoaSort(s || 'code')
    setCoaOrder(o === 'desc' ? 'desc' : 'asc')
    setCoaPage((pg) => ({ ...pg, offset: 0 }))
  }, [])

  const loadAccounts = useCallback(async () => {
    const [listData, formData] = await Promise.all([
      financeLedgerApi.accounts({
        max: coaPage.max,
        offset: coaPage.offset,
        sort: coaSort,
        order: coaOrder,
      }),
      financeLedgerApi.accounts({ max: 100, offset: 0, sort: 'code', order: 'asc' }),
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
  }, [coaPage.max, coaPage.offset, coaSort, coaOrder])

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

  useEffect(() => {
    if (!selectedId) return
    const still = accounts.some((a) => String(a.id) === String(selectedId))
    if (accounts.length && !still) setSelectedId(null)
  }, [accounts, selectedId])

  useEffect(() => {
    if (!selectedId) return
    const id = window.requestAnimationFrame(() => {
      scrollSelectedIntoView('.desk-shell-list')
    })
    return () => window.cancelAnimationFrame(id)
  }, [selectedId])

  const selected = useMemo(
    () => accounts.find((a) => String(a.id) === String(selectedId)) || null,
    [accounts, selectedId],
  )

  const selectedTbRow = useMemo(() => {
    if (!selected || !tb?.lines) return null
    return (
      tb.lines.find(
        (row) =>
          String(row.accountId) === String(selected.id) || row.code === selected.code,
      ) || null
    )
  }, [selected, tb])

  const totals = useMemo(() => {
    let d = 0
    let c = 0
    lines.forEach((l) => {
      d += Number(l.debitEur) || 0
      c += Number(l.creditEur) || 0
    })
    return { debit: d, credit: c, balanced: Math.abs(d - c) < 1e-9 && d > 0 }
  }, [lines])

  const resetCreateForms = useCallback(() => {
    setEntryName('')
    setLines(DEFAULT_LINES())
  }, [])

  const createDirty = useMemo(() => {
    const defaults = DEFAULT_LINES()
    const linesChanged =
      lines.length !== defaults.length ||
      lines.some((l, i) => {
        const d = defaults[i]
        if (!d) return true
        return (
          l.accountCode !== d.accountCode ||
          l.debitEur !== d.debitEur ||
          l.creditEur !== d.creditEur ||
          l.memo !== d.memo
        )
      })
    return Boolean((entryName || '').trim() || linesChanged)
  }, [entryName, lines])

  const applyCollapseCreate = useCallback(() => {
    setNewExpanded(false)
    resetCreateForms()
  }, [resetCreateForms])

  const requestToggleNew = useCallback(() => {
    if (newExpanded && createDirty) {
      setPendingNav({ type: 'collapse' })
      setDiscardOpen(true)
      return
    }
    if (newExpanded) applyCollapseCreate()
    else setNewExpanded(true)
  }, [newExpanded, createDirty, applyCollapseCreate])

  const confirmDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
    applyCollapseCreate()
    void pendingNav
  }, [pendingNav, applyCollapseCreate])

  const cancelDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
  }, [])

  const keyboardIds = useMemo(() => accounts.map((a) => a.id), [accounts])

  useDeskKeyboard({
    enabled: !newExpanded,
    itemIds: keyboardIds,
    selectedId,
    setSelectedId,
    onToggleNew: requestToggleNew,
  })

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
      setInfo(
        t('toast.postedJe', {
          ns: 'common',
          id: entry.id,
          period: entry.period,
          eventId: entry.eventId,
        }),
      )
      resetCreateForms()
      setNewExpanded(false)
      await loadTb()
      await loadAccounts()
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
          : `Period ${period} closed`,
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

  const helpContext = useMemo(() => {
    const base = {
      desk: 'finance-transactions',
      tab: 'accounts',
      resource: 'Account',
      processCode: 'FIN_LEDGER',
    }
    if (selected) {
      return { ...base, objectId: selected.id, status: selected.accountType }
    }
    return base
  }, [selected])

  const processSlot = (
    <DeskProcessSlot processCode="FIN_LEDGER" status={selected?.accountType ?? null} />
  )

  const newPanel = (
    <form className="form-grid" onSubmit={onPost}>
      <label>
        {t('finance.entryName')}
        <input value={entryName} onChange={(e) => setEntryName(e.target.value)} />
      </label>

      {/* Line editor — keep finance-tx-grid (plan §4.3), not page layout */}
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

      {lastEntry && (
        <div className="tx-last mono">
          {t('finance.lastEntry', {
            id: lastEntry.id,
            name: lastEntry.name,
            period: lastEntry.period,
            eventId: lastEntry.eventId,
          })}
          {lastEntry.reversesEntryId ? t('finance.revOf', { id: lastEntry.reversesEntryId }) : ''}
        </div>
      )}
    </form>
  )

  const list = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('finance.coaCount', { count: coaPage.total || accounts.length })}
      </h2>
      <ResponsiveDataList
        items={accounts}
        empty={t('finance.emptyCoa')}
        selectedKey={selected?.id}
        onSelect={(a) => setSelectedId(String(a.id))}
        sortFields={COA_SORT_MENU}
        sort={coaSort}
        order={coaOrder}
        defaultSort="code"
        defaultOrder="asc"
        onSortChange={onCoaSortChange}
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
        actions={(a) => <ViewButton pkg="finance" resource="Account" record={a} />}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="code"
                  label={t('wb.code', { ns: 'common' })}
                  sort={coaSort}
                  order={coaOrder}
                  defaultSort="code"
                  defaultOrder="asc"
                  onSortChange={onCoaSortChange}
                />
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={coaSort}
                  order={coaOrder}
                  defaultSort="code"
                  defaultOrder="asc"
                  onSortChange={onCoaSortChange}
                />
                <ListSortHeader
                  field="accountType"
                  label={t('wb.type', { ns: 'common' })}
                  sort={coaSort}
                  order={coaOrder}
                  defaultSort="code"
                  defaultOrder="asc"
                  onSortChange={onCoaSortChange}
                  className="col-priority-low"
                />
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
                <tr
                  key={a.id}
                  data-selected={selected?.id === a.id ? 'true' : undefined}
                  className={selected?.id === a.id ? 'row-selected' : 'row-click'}
                  onClick={() => setSelectedId(String(a.id))}
                >
                  <td className="mono">{a.code}</td>
                  <td>{a.name}</td>
                  <td className="col-priority-low">{a.accountType}</td>
                  <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
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
    </>
  )

  // Detail: selected account, else period trial-balance overview
  let detail = (
    <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('finance.trialBalance', { period })}
        {tb?.closed ? t('finance.trialBalanceClosed') : ''}
      </h2>
      {loading && !tb && <p className="muted">{t('actions.loading', { ns: 'common' })}</p>}
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
    </div>
  )

  if (selected) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          <span className="mono">{selected.code}</span> {selected.name}
        </h2>
        <dl className="about-grid">
          <div>
            <dt>{t('wb.type', { ns: 'common' })}</dt>
            <dd>{selected.accountType}</dd>
          </div>
          {selectedTbRow && (
            <>
              <div>
                <dt>{t('finance.debit')}</dt>
                <dd>{Number(selectedTbRow.debitsEur).toFixed(2)}</dd>
              </div>
              <div>
                <dt>{t('finance.credit')}</dt>
                <dd>{Number(selectedTbRow.creditsEur).toFixed(2)}</dd>
              </div>
              <div>
                <dt>{t('finance.net')}</dt>
                <dd>{Number(selectedTbRow.balanceEur).toFixed(2)}</dd>
              </div>
            </>
          )}
        </dl>
        <ViewButton pkg="finance" resource="Account" record={selected} />
        <p className="muted" style={{ marginTop: '1rem', fontSize: '0.85rem' }}>
          {t('finance.trialBalance', { period })}
          {tb?.closed ? t('finance.trialBalanceClosed') : ''}
          {tb
            ? ` · ${tb.balanced ? t('finance.balanceOk') : t('finance.balanceOut')}`
            : ''}
        </p>
      </div>
    )
  }

  return (
    <div className="page finance-tx-page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}

      <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
        <button type="button" className="btn btn-primary" onClick={onSeed} disabled={!!busy}>
          {busy === 'seed' ? t('actions.seeding', { ns: 'common' }) : t('finance.seedCoa')}
        </button>
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <button type="button" className="btn" onClick={onClosePeriod} disabled={!!busy}>
          <Lock size={16} /> {t('finance.closePeriod')}
        </button>
        <label className="muted" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {t('wb.period', { ns: 'common' })}
          <input
            className="inline-period"
            value={period}
            onChange={(e) => setPeriod(e.target.value)}
            pattern="\d{4}-\d{2}"
            title="yyyy-MM"
            style={{ width: '7rem' }}
          />
        </label>
        <Link className="btn btn-secondary" to="/domains/finance/Account">
          {t('finance.chartOfAccountsLink')}
        </Link>
        <Link className="btn btn-secondary" to="/domains/finance/JournalEntry">
          {t('finance.journalEntriesLink')}
        </Link>
        <Link className="btn btn-secondary" to="/finance/invoices">
          {t('finance.invoicesTitle')}
        </Link>
      </div>

      <DeskShell
        // ADR-050 Wave C: keep split — period trial-balance (or selected account) is the always-on workspace.
        layout="split"
        title={
          <>
            <BookOpen size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('finance.ledgerTitle')}
          </>
        }
        subtitle={t('finance.ledgerSubtitle')}
        newLabel={t('finance.postJournal')}
        newExpanded={newExpanded}
        onToggleNew={requestToggleNew}
        newPanel={newPanel}
        list={list}
        hasSelection
        emptyTitle={t('wb.selectItem', { ns: 'common' })}
        emptyBody={t('desk.emptyBody', { ns: 'common' })}
        detail={detail}
        processSlot={processSlot}
        helpContext={helpContext}
        narrowSheetOpen={narrow && Boolean(selected)}
        onCloseSheet={() => setSelectedId(null)}
      />

      <DeskNewFab
        label={t('finance.postJournal')}
        onClick={() => !newExpanded && requestToggleNew()}
      />

      <ConfirmDialog
        open={discardOpen}
        title={t('desk.discardCreateTitle', {
          ns: 'common',
          defaultValue: 'Discard create form?',
        })}
        body={t('desk.discardCreateBody', {
          ns: 'common',
          defaultValue: 'You have unsaved changes in the create form. Discard them?',
        })}
        confirmLabel={t('desk.discard', { ns: 'common', defaultValue: 'Discard' })}
        tone="danger"
        onConfirm={confirmDiscard}
        onCancel={cancelDiscard}
      />
    </div>
  )
}
