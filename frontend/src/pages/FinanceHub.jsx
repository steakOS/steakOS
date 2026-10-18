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
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  Wallet,
  Receipt,
  PieChart,
  Landmark,
  Users,
  Shield,
  RefreshCw,
} from 'lucide-react'
import { financeOpsApi, meApi } from '../api/client'
import { allowsSilverSurfaces } from '../utils/silverSurfaces'
import useRecordView from '../hooks/useRecordView'
import ResponsiveDataList from '../components/ResponsiveDataList'
import DeskNewFab from '../components/DeskNewFab'
import ListPager from '../components/ListPager'
import StubBadge from '../components/StubBadge'

function periodNow() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const TAB_DEFS = [
  { id: 'treasury', labelKey: 'finance.tabs.treasury', icon: Wallet },
  { id: 'payables', labelKey: 'finance.tabs.payables', icon: Receipt },
  { id: 'budgets', labelKey: 'finance.tabs.budgets', icon: PieChart },
  { id: 'tax', labelKey: 'finance.tabs.tax', icon: Landmark },
  { id: 'payroll', labelKey: 'finance.tabs.payroll', icon: Users },
  { id: 'control', labelKey: 'finance.tabs.control', icon: Shield },
]

/**
 * F-S3–F-S6 combined simple UI (FinanceBasics ops).
 */
export default function FinanceHub() {
  const { t } = useTranslation(['desks', 'common'])
  const { viewModal, ViewButton } = useRecordView()
  const TABS = useMemo(
    () => TAB_DEFS.map((d) => ({ ...d, label: t(d.labelKey) })),
    [t],
  )
  const { section } = useParams()
  const tab = TABS.some((tabDef) => tabDef.id === section) ? section : 'treasury'

  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [busy, setBusy] = useState(false)
  const [period, setPeriod] = useState(periodNow())
  const [showAdvanced, setShowAdvanced] = useState(false)

  useEffect(() => {
    let cancelled = false
    meApi
      .get()
      .then((me) => {
        if (!cancelled) setShowAdvanced(allowsSilverSurfaces(me?.templateTier))
      })
      .catch(() => {
        if (!cancelled) setShowAdvanced(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const [positions, setPositions] = useState([])
  const [treasuryPage, setTreasuryPage] = useState({ max: 25, offset: 0, total: 0 })
  const [payables, setPayables] = useState([])
  const [payablePage, setPayablePage] = useState({ max: 25, offset: 0, total: 0 })
  const [budgets, setBudgets] = useState([])
  const [budgetPage, setBudgetPage] = useState({ max: 25, offset: 0, total: 0 })
  const [variance, setVariance] = useState(null)
  const [taxes, setTaxes] = useState([])
  const [taxPage, setTaxPage] = useState({ max: 25, offset: 0, total: 0 })
  const [payroll, setPayroll] = useState([])
  const [payrollPage, setPayrollPage] = useState({ max: 25, offset: 0, total: 0 })
  const [audit, setAudit] = useState([])
  const [auditPage, setAuditPage] = useState({ max: 25, offset: 0, total: 0 })
  const [periodClosed, setPeriodClosed] = useState(false)

  const applyPage = (setPage, d) => {
    setPage((pg) => ({
      ...pg,
      max: d.max ?? pg.max,
      offset: d.offset ?? pg.offset,
      total: d.total ?? (d.items || []).length,
    }))
  }

  const [apForm, setApForm] = useState({ vendorName: '', amountEur: '50', name: '' })
  const [budForm, setBudForm] = useState({ name: '', amountEur: '1000', period: periodNow() })
  const [payForm, setPayForm] = useState({ personRef: '', amountEur: '100', period: periodNow() })

  const run = async (fn, msg) => {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      await fn()
      if (msg) setInfo(msg)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const loadTab = useCallback(async () => {
    setError(null)
    try {
      if (tab === 'treasury') {
        const d = await financeOpsApi.treasuryList({
          max: treasuryPage.max,
          offset: treasuryPage.offset,
        })
        setPositions(d.items || [])
        applyPage(setTreasuryPage, d)
      } else if (tab === 'payables') {
        const d = await financeOpsApi.payableList({
          max: payablePage.max,
          offset: payablePage.offset,
        })
        setPayables(d.items || [])
        applyPage(setPayablePage, d)
      } else if (tab === 'budgets') {
        const d = await financeOpsApi.budgetList({
          max: budgetPage.max,
          offset: budgetPage.offset,
        })
        setBudgets(d.items || [])
        applyPage(setBudgetPage, d)
        const v = await financeOpsApi.budgetVariance(period)
        setVariance(v)
      } else if (tab === 'tax') {
        const d = await financeOpsApi.taxList({
          max: taxPage.max,
          offset: taxPage.offset,
        })
        setTaxes(d.items || [])
        applyPage(setTaxPage, d)
      } else if (tab === 'payroll') {
        const d = await financeOpsApi.payrollList({
          max: payrollPage.max,
          offset: payrollPage.offset,
        })
        setPayroll(d.items || [])
        applyPage(setPayrollPage, d)
      } else if (tab === 'control') {
        const a = await financeOpsApi.controlAudit({
          max: auditPage.max,
          offset: auditPage.offset,
        })
        setAudit(a.items || [])
        applyPage(setAuditPage, a)
        const p = await financeOpsApi.controlPeriod(period)
        setPeriodClosed(!!p.closed)
      }
    } catch (e) {
      setError(e.message)
    }
  }, [
    tab,
    period,
    treasuryPage.max,
    treasuryPage.offset,
    payablePage.max,
    payablePage.offset,
    budgetPage.max,
    budgetPage.offset,
    taxPage.max,
    taxPage.offset,
    payrollPage.max,
    payrollPage.offset,
    auditPage.max,
    auditPage.offset,
  ])

  useEffect(() => {
    loadTab()
  }, [loadTab])

  return (
    <div className="page">
      <header className="page-header" style={{ marginBottom: '1rem' }}>
        <h1 className="page-title">{t('finance.hubTitle')}</h1>
        <p className="page-sub">
          {t('finance.hubSubtitle')} ·{' '}
          <Link to="/finance/transactions">{t('finance.ledgerTitle')}</Link>
          {' · '}
          <Link to="/finance/invoices">{t('finance.invoicesTitle')}</Link>
          {' · '}
          <Link to="/finance/posting-rules">
            {t('finance.postingRules.title', { defaultValue: 'Posting rules' })}
          </Link>
          {showAdvanced && (
            <>
              {' · '}
              <Link to="/finance/collections">
                {t('features.collections', { ns: 'common', defaultValue: 'Collections' })}
              </Link>
              {' · '}
              <Link to="/finance/match">
                {t('features.threeWayMatch', { ns: 'common', defaultValue: 'Three-way match' })}
              </Link>
            </>
          )}
        </p>
      </header>

      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      {viewModal}

      <div className="finance-tabs">
        {TABS.map((tabDef) => {
          const Icon = tabDef.icon
          return (
            <Link
              key={tabDef.id}
              to={`/finance/${tabDef.id}`}
              className={`finance-tab ${tab === tabDef.id ? 'active' : ''}`}
            >
              <Icon size={16} /> {tabDef.label}
            </Link>
          )
        })}
      </div>

      <div className="toolbar">
        <button type="button" className="btn" onClick={loadTab} disabled={busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <label className="muted" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {t('wb.period', { ns: 'common' })}
          <input
            className="inline-period"
            value={period}
            onChange={(e) => setPeriod(e.target.value)}
          />
        </label>
      </div>

      {tab === 'treasury' && (
        <section className="card">
          <div className="toolbar">
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy}
              onClick={() =>
                run(async () => {
                  const d = await financeOpsApi.treasurySeed()
                  setPositions(d.items || [])
                }, t('finance.treasuryReady'))
              }
            >
              {t('finance.seedTreasury')}
            </button>
          </div>
          <ResponsiveDataList
            items={positions}
            empty={t('finance.seedPositionsEmpty')}
            fields={[
              { id: 'name', primary: true, render: (p) => p.name },
              {
                id: 'book',
                secondary: true,
                render: (p) =>
                  `${Number(p.balanceEur).toFixed(2)} ${p.currencyCode || 'EUR'}`,
              },
              {
                id: 'kind',
                priority: 'low',
                render: (p) => p.accountKind,
              },
            ]}
            actions={(p) => (
              <ViewButton pkg="finance" resource="Treasury" record={p} />
            )}
          >
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('wb.name', { ns: 'common' })}</th>
                    <th className="col-priority-low">{t('wb.kind', { ns: 'common' })}</th>
                    <th>{t('finance.bookBalance')}</th>
                    <th className="col-priority-low">{t('finance.sats')}</th>
                    <th className="col-priority-low">{t('finance.coa')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {positions.length === 0 && (
                    <tr>
                      <td colSpan={6} className="muted">
                        {t('finance.seedPositionsEmpty')}
                      </td>
                    </tr>
                  )}
                  {positions.map((p) => (
                    <tr key={p.id}>
                      <td>{p.name}</td>
                      <td className="mono col-priority-low">{p.accountKind}</td>
                      <td>
                        {Number(p.balanceEur).toFixed(2)}{' '}
                        <span className="muted">{p.currencyCode || 'EUR'}</span>
                      </td>
                      <td className="mono col-priority-low">
                        {p.accountKind === 'FIAT_BANK' ? '—' : p.balanceSats}
                      </td>
                      <td className="mono col-priority-low">{p.coaCode}</td>
                      <td>
                        <ViewButton pkg="finance" resource="Treasury" record={p} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </ResponsiveDataList>
          <ListPager
            total={treasuryPage.total}
            max={treasuryPage.max}
            offset={treasuryPage.offset}
            disabled={busy}
            onOffset={(o) => setTreasuryPage((pg) => ({ ...pg, offset: o }))}
            onMax={(m) => setTreasuryPage((pg) => ({ ...pg, max: m, offset: 0 }))}
          />
          <p className="muted" style={{ fontSize: '0.85rem' }}>
            {t('finance.treasuryDualHint')}
          </p>
        </section>
      )}

      {tab === 'payables' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <DeskNewFab label={t('finance.newPayable')} />
          <section id="desk-create" className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('finance.newPayable')}
            </h2>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault()
                run(async () => {
                  await financeOpsApi.payableCreate(apForm)
                  await loadTab()
                  setApForm({ vendorName: '', amountEur: '50', name: '' })
                }, t('finance.payableCreated'))
              }}
            >
              <label>
                {t('finance.vendor')}
                <input
                  value={apForm.vendorName}
                  onChange={(e) => setApForm((f) => ({ ...f, vendorName: e.target.value }))}
                  required
                />
              </label>
              <label>
                {t('wb.amountEur', { ns: 'common' })}
                <input
                  value={apForm.amountEur}
                  onChange={(e) => setApForm((f) => ({ ...f, amountEur: e.target.value }))}
                  required
                />
              </label>
              <label>
                {t('finance.label')}
                <input
                  value={apForm.name}
                  onChange={(e) => setApForm((f) => ({ ...f, name: e.target.value }))}
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={busy}>
                {t('finance.createPayable')}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('finance.openPaid')}
            </h2>
            <ResponsiveDataList
              items={payables}
              empty="—"
              fields={[
                {
                  id: 'vendor',
                  primary: true,
                  render: (p) => p.vendorName || p.name,
                },
                {
                  id: 'eur',
                  secondary: true,
                  render: (p) => `${Number(p.amountEur).toFixed(2)} EUR`,
                },
                {
                  id: 'status',
                  meta: true,
                  render: (p) => p.payableStatus,
                },
              ]}
              actions={(p) => (
                <>
                  {p.payableStatus === 'OPEN' && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={busy}
                      onClick={() =>
                        run(async () => {
                          await financeOpsApi.payablePay(p.id)
                          await loadTab()
                        }, t('finance.paidId', { id: p.id }))
                      }
                    >
                      {t('finance.pay')}
                    </button>
                  )}
                  <ViewButton pkg="finance" resource="Payable" record={p} />
                </>
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.vendor', { ns: 'common' })}</th>
                      <th>EUR</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {payables.map((p) => (
                      <tr key={p.id}>
                        <td>{p.vendorName || p.name}</td>
                        <td>{Number(p.amountEur).toFixed(2)}</td>
                        <td>{p.payableStatus}</td>
                        <td>
                          {p.payableStatus === 'OPEN' && (
                            <button
                              type="button"
                              className="btn btn-ghost"
                              disabled={busy}
                              onClick={() =>
                                run(async () => {
                                  await financeOpsApi.payablePay(p.id)
                                  await loadTab()
                                }, t('finance.paidId', { id: p.id }))
                              }
                            >
                              {t('finance.pay')}
                            </button>
                          )}
                        </td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="finance" resource="Payable" record={p} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={payablePage.total}
              max={payablePage.max}
              offset={payablePage.offset}
              disabled={busy}
              onOffset={(o) => setPayablePage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setPayablePage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'budgets' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <DeskNewFab label={t('finance.newBudget')} />
          <section id="desk-create" className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('finance.createBudget')}
            </h2>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault()
                run(async () => {
                  await financeOpsApi.budgetCreate(budForm)
                  await loadTab()
                }, t('finance.budgetSaved'))
              }}
            >
              <label>
                {t('finance.name')}
                <input
                  value={budForm.name}
                  onChange={(e) => setBudForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder={t('ph.opsBudget', { ns: 'common' })}
                />
              </label>
              <label>
                {t('wb.period', { ns: 'common' })}
                <input
                  value={budForm.period}
                  onChange={(e) => setBudForm((f) => ({ ...f, period: e.target.value }))}
                  required
                />
              </label>
              <label>
                {t('wb.amountEur', { ns: 'common' })}
                <input
                  value={budForm.amountEur}
                  onChange={(e) => setBudForm((f) => ({ ...f, amountEur: e.target.value }))}
                  required
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={busy}>
                {t('finance.saveBudget')}
              </button>
            </form>
            <ul className="inv-list">
              {budgets.map((b) => (
                <li
                  key={b.id}
                  style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'space-between' }}
                >
                  <span>
                    {b.period} · {b.name || t('finance.budgetFallback')} ·{' '}
                    {Number(b.amountEur).toFixed(2)} EUR
                  </span>
                  <ViewButton pkg="finance" resource="Budget" record={b} />
                </li>
              ))}
            </ul>
            <ListPager
              total={budgetPage.total}
              max={budgetPage.max}
              offset={budgetPage.offset}
              disabled={busy}
              onOffset={(o) => setBudgetPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setBudgetPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('finance.varianceTitle', { period })}
            </h2>
            {variance && (
              <dl className="about-grid">
                <div>
                  <dt>{t('finance.budgetTotal')}</dt>
                  <dd>{Number(variance.budgetTotal).toFixed(2)} EUR</dd>
                </div>
                <div>
                  <dt>{t('finance.actualExpense')}</dt>
                  <dd>{Number(variance.actualExpense).toFixed(2)} EUR</dd>
                </div>
                <div>
                  <dt>{t('finance.variance')}</dt>
                  <dd className={variance.underBudget ? 'ok-text' : 'bad-text'}>
                    {Number(variance.variance).toFixed(2)} EUR
                  </dd>
                </div>
              </dl>
            )}
          </section>
        </div>
      )}

      {tab === 'tax' && (
        <section className="card">
          <div className="toolbar">
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy}
              onClick={() =>
                run(async () => {
                  await financeOpsApi.taxEstimate({ period, rate: 0.19, jurisdiction: 'DE' })
                  await loadTab()
                }, t('finance.taxEstimateDone', { period }))
              }
            >
              {t('finance.taxEstimate', { period })}
            </button>
          </div>
          <ResponsiveDataList
            items={taxes}
            empty="—"
            fields={[
              {
                id: 'period',
                primary: true,
                render: (taxRow) => (
                  <span className="mono">{taxRow.period}</span>
                ),
              },
              {
                id: 'amount',
                secondary: true,
                render: (taxRow) => `${Number(taxRow.amountEur).toFixed(2)} EUR`,
              },
              {
                id: 'jurisdiction',
                meta: true,
                label: t('finance.jurisdiction'),
                render: (taxRow) => taxRow.jurisdiction,
              },
              {
                id: 'status',
                meta: true,
                render: (taxRow) => taxRow.taxStatus,
              },
            ]}
            actions={(taxRow) => (
              <ViewButton pkg="finance" resource="Tax" record={taxRow} />
            )}
          >
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('wb.period', { ns: 'common' })}</th>
                    <th className="col-priority-low">{t('finance.jurisdiction')}</th>
                    <th>{t('wb.amount', { ns: 'common' })}</th>
                    <th>{t('wb.status', { ns: 'common' })}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {taxes.map((taxRow) => (
                    <tr key={taxRow.id}>
                      <td className="mono">{taxRow.period}</td>
                      <td className="col-priority-low">{taxRow.jurisdiction}</td>
                      <td>{Number(taxRow.amountEur).toFixed(2)}</td>
                      <td>{taxRow.taxStatus}</td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        <ViewButton pkg="finance" resource="Tax" record={taxRow} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </ResponsiveDataList>
          <ListPager
            total={taxPage.total}
            max={taxPage.max}
            offset={taxPage.offset}
            disabled={busy}
            onOffset={(o) => setTaxPage((pg) => ({ ...pg, offset: o }))}
            onMax={(m) => setTaxPage((pg) => ({ ...pg, max: m, offset: 0 }))}
          />
        </section>
      )}

      {tab === 'payroll' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <DeskNewFab label={t('finance.newPayrollLine')} />
          <section id="desk-create" className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('finance.addPayrollLine')}
            </h2>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault()
                run(async () => {
                  await financeOpsApi.payrollCreate(payForm)
                  await loadTab()
                }, t('finance.payrollSaved'))
              }}
            >
              <label>
                {t('finance.personRef')}
                <input
                  value={payForm.personRef}
                  onChange={(e) => setPayForm((f) => ({ ...f, personRef: e.target.value }))}
                />
              </label>
              <label>
                {t('wb.period', { ns: 'common' })}
                <input
                  value={payForm.period}
                  onChange={(e) => setPayForm((f) => ({ ...f, period: e.target.value }))}
                />
              </label>
              <label>
                {t('wb.amountEur', { ns: 'common' })}
                <input
                  value={payForm.amountEur}
                  onChange={(e) => setPayForm((f) => ({ ...f, amountEur: e.target.value }))}
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={busy}>
                {t('finance.addPayrollLine')}
              </button>
            </form>
            <div className="toolbar" style={{ gap: 8, flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn"
                disabled={busy}
                onClick={() =>
                  run(async () => {
                    const r = await financeOpsApi.payrollRecognize(period)
                    setInfo(r.message || JSON.stringify(r))
                    await loadTab()
                  })
                }
              >
                {t('finance.postPayrollRun')}
              </button>
              <button
                type="button"
                className="btn btn-primary"
                disabled={busy}
                onClick={() =>
                  run(async () => {
                    const r = await financeOpsApi.payrollPay(period)
                    setInfo(r.message || JSON.stringify(r))
                    await loadTab()
                  })
                }
              >
                {t('finance.payWages')}
              </button>
            </div>
            <p className="muted" style={{ fontSize: '0.85rem' }}>
              {t('finance.payrollHint')}
            </p>
          </section>
          <section className="card">
            <ul className="inv-list">
              {payroll.map((p) => (
                <li
                  key={p.id}
                  style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'space-between' }}
                >
                  <span>
                    {p.personRef || p.name} · {p.period} · {Number(p.amountEur).toFixed(2)} ·{' '}
                    {p.payrollStatus}
                  </span>
                  <ViewButton pkg="finance" resource="PayrollItem" record={p} />
                </li>
              ))}
            </ul>
            <ListPager
              total={payrollPage.total}
              max={payrollPage.max}
              offset={payrollPage.offset}
              disabled={busy}
              onOffset={(o) => setPayrollPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setPayrollPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'control' && (
        <section className="card">
          <p>
            {t('wb.period', { ns: 'common' })} <strong className="mono">{period}</strong>:{' '}
            {periodClosed ? (
              <span className="bad-text">{t('finance.statusClosed')}</span>
            ) : (
              <span className="ok-text">{t('finance.statusOpen')}</span>
            )}
          </p>
          <div className="toolbar">
            <button
              type="button"
              className="btn"
              disabled={busy || periodClosed}
              onClick={() =>
                run(async () => {
                  await financeOpsApi.controlClose(period)
                  await loadTab()
                }, t('finance.closedPeriod', { period }))
              }
            >
              {t('finance.softClosePeriod')}{' '}
              <StubBadge hint={t('finance.softCloseHint')} />
            </button>
          </div>
          <h2 className="section-label">{t('finance.recentAudit')}</h2>
          <ResponsiveDataList
            items={audit}
            empty="—"
            fields={[
              {
                id: 'name',
                primary: true,
                render: (a) => a.name || `#${a.id}`,
              },
              {
                id: 'period',
                secondary: true,
                render: (a) => <span className="mono">{a.period}</span>,
              },
              {
                id: 'source',
                meta: true,
                label: t('finance.source'),
                render: (a) => `${a.sourceType}/${a.sourceId}`,
              },
              {
                id: 'id',
                priority: 'low',
                render: (a) => a.id,
              },
            ]}
            actions={(a) => (
              <ViewButton
                pkg="finance"
                resource="JournalEntry"
                record={{ id: a.id, name: a.name, period: a.period }}
              />
            )}
          >
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th className="col-priority-low">ID</th>
                    <th>{t('wb.name', { ns: 'common' })}</th>
                    <th>{t('wb.period', { ns: 'common' })}</th>
                    <th className="col-priority-low">{t('finance.source')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {audit.map((a) => (
                    <tr key={a.id}>
                      <td className="mono col-priority-low">{a.id}</td>
                      <td>{a.name}</td>
                      <td className="mono">{a.period}</td>
                      <td className="col-priority-low">
                        {a.sourceType}/{a.sourceId}
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        <ViewButton
                          pkg="finance"
                          resource="JournalEntry"
                          record={{ id: a.id, name: a.name, period: a.period }}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </ResponsiveDataList>
          <ListPager
            total={auditPage.total}
            max={auditPage.max}
            offset={auditPage.offset}
            disabled={busy}
            onOffset={(o) => setAuditPage((pg) => ({ ...pg, offset: o }))}
            onMax={(m) => setAuditPage((pg) => ({ ...pg, max: m, offset: 0 }))}
          />
        </section>
      )}
    </div>
  )
}
