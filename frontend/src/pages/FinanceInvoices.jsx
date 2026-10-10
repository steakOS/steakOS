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
import { FileText, Plus, RefreshCw, Send, Banknote, Ban } from 'lucide-react'
import { financeOpsApi, financeRevenueApi, financeLedgerApi } from '../api/client'
import ProcessHowItWorks from '../components/ProcessHowItWorks'
import ContentRail from '../components/ContentRail'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import ResponsiveDataList from '../components/ResponsiveDataList'
import ListPager from '../components/ListPager'
import MoneyHint from '../components/MoneyHint'

const RAILS = [
  { value: 'SEPA', labelKey: 'finance.rails.SEPA' },
  { value: 'BTC_ONCHAIN', labelKey: 'finance.rails.BTC_ONCHAIN' },
  { value: 'BTC_LIGHTNING', labelKey: 'finance.rails.BTC_LIGHTNING' },
  { value: 'BTC_WALLET_ADDRESS', labelKey: 'finance.rails.BTC_WALLET_ADDRESS' },
  { value: 'OTHER', labelKey: 'finance.rails.OTHER' },
]

/**
 * F-S2 invoicing workbench: create → issue (3 rails) → settle (ledger) → void.
 */
export default function FinanceInvoices() {
  const { t } = useTranslation(['desks', 'common', 'content'])
  const { viewModal, ViewButton } = useRecordView()
  const [items, setItems] = useState([])
  const [selected, setSelected] = useState(null)
  const [invoiceContentMap, setInvoiceContentMap] = useState({})
  const [pendingInvoiceContent, setPendingInvoiceContent] = useState([])
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [filter, setFilter] = useState('')

  const [form, setForm] = useState({
    customerName: '',
    amountEur: '100',
    referenceText: '',
    issue: true,
  })

  const [settle, setSettle] = useState({
    rail: 'SEPA',
    amountEur: '',
    walletAddress: '',
  })

  const [page, setPage] = useState({ max: 25, offset: 0, total: 0 })
  const [demoForcedMock, setDemoForcedMock] = useState(false)
  const [btcIntegration, setBtcIntegration] = useState(null)

  useEffect(() => {
    financeOpsApi
      .paymentsSettingsGet()
      .then((s) => {
        setDemoForcedMock(!!s?.demoForcedMock)
        setBtcIntegration(s?.btcIntegration || null)
      })
      .catch(() => {
        setDemoForcedMock(false)
        setBtcIntegration(null)
      })
  }, [])

  const loadList = useCallback(async () => {
    const data = await financeRevenueApi.list({
      status: filter || undefined,
      max: page.max,
      offset: page.offset,
    })
    const list = data.items || []
    setItems(list)
    setPage((pg) => ({
      ...pg,
      max: data.max ?? pg.max,
      offset: data.offset ?? pg.offset,
      total: data.total ?? list.length,
    }))
    setInvoiceContentMap(await fetchContentMap('finance', 'Invoice', list))
    return list
  }, [filter, page.max, page.offset])

  const selectInvoice = useCallback(async (id) => {
    if (!id) {
      setSelected(null)
      return
    }
    const full = await financeRevenueApi.get(id)
    setSelected(full)
    const lvl = full.btcIntegration || btcIntegration
    const addr = lvl?.receiveAddress || ''
    setSettle((s) => ({
      ...s,
      amountEur: full.amountEur != null ? String(full.amountEur) : '',
      walletAddress:
        lvl?.level === 'SINGLE_ADDRESS' && addr
          ? addr
          : s.walletAddress,
      rail:
        lvl?.level === 'SINGLE_ADDRESS' && addr && ['ISSUED', 'PART_PAID'].includes(full.invoiceStatus)
          ? 'BTC_WALLET_ADDRESS'
          : s.rail,
    }))
  }, [btcIntegration])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      await financeLedgerApi.seed().catch(() => null)
      const list = await loadList()
      if (selected?.id) {
        const still = list.find((i) => i.id === selected.id)
        if (still) await selectInvoice(selected.id)
        else setSelected(null)
      }
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [loadList, selectInvoice, selected?.id])

  useEffect(() => {
    setLoading(true)
    loadList()
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [loadList])

  const onCreate = async (e) => {
    e.preventDefault()
    setBusy('create')
    setError(null)
    setInfo(null)
    try {
      const body = {
        customerName: form.customerName || null,
        amountEur: form.amountEur,
        amount: form.amountEur,
        referenceText: form.referenceText || null,
        issue: !!form.issue,
      }
      const inv = await financeRevenueApi.create(body)
      if (pendingInvoiceContent.length) {
        await attachPendingContent('finance', 'Invoice', inv.id, pendingInvoiceContent)
        setPendingInvoiceContent([])
      }
      setInfo(
        `Invoice ${inv.invoiceNumber} · ${inv.invoiceStatus}` +
          (inv.paymentOptions?.length ? ` · ${inv.paymentOptions.length} rails` : ''),
      )
      setForm((f) => ({ ...f, customerName: '', referenceText: '' }))
      await loadList()
      await selectInvoice(inv.id)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onIssue = async () => {
    if (!selected?.id) return
    setBusy('issue')
    setError(null)
    setInfo(null)
    try {
      const inv = await financeRevenueApi.issue(selected.id)
      setInfo(t('toast.issued', { ns: 'common', number: inv.invoiceNumber }))
      await loadList()
      await selectInvoice(inv.id)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onSettle = async (e) => {
    e.preventDefault()
    if (!selected?.id) return
    setBusy('settle')
    setError(null)
    setInfo(null)
    try {
      const body = {
        rail: settle.rail,
        amountEur: settle.amountEur === '' ? undefined : settle.amountEur,
        eventId: `ui-pay-${selected.id}-${Date.now()}`,
      }
      if (settle.rail === 'BTC_WALLET_ADDRESS') {
        body.walletAddress = settle.walletAddress?.trim()
        if (!body.walletAddress) {
          throw new Error('BTC wallet address is required for this settle rail')
        }
      }
      const result = await financeRevenueApi.settle(selected.id, body)
      setInfo(
        `Settled via ${result.payment?.rail} · JE #${result.journalEntryId}` +
          (result.idempotent ? ' (idempotent)' : '') +
          ` · invoice ${result.invoice?.invoiceStatus}`,
      )
      await loadList()
      await selectInvoice(selected.id)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onVoid = async () => {
    if (!selected?.id) return
    if (!window.confirm(t('toast.voidConfirm', { ns: 'common', number: selected.invoiceNumber }))) {
      return
    }
    setBusy('void')
    setError(null)
    setInfo(null)
    try {
      const inv = await financeRevenueApi.voidInvoice(selected.id, { reason: 'UI void' })
      setInfo(t('toast.voided', { ns: 'common', number: inv.invoiceNumber }))
      await loadList()
      await selectInvoice(inv.id)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const statusClass = (s) => {
    if (s === 'PAID') return 'badge badge-ok'
    if (s === 'VOID') return 'badge badge-muted'
    if (s === 'PART_PAID') return 'badge badge-warn'
    if (s === 'ISSUED') return 'badge'
    return 'badge badge-draft'
  }

  return (
    <div className="page finance-inv-page">
      <header className="page-header" style={{ marginBottom: '1.25rem' }}>
        <h1 className="page-title">
          <FileText size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('finance.invoicesTitle')}
        </h1>
        <p className="page-sub">{t('finance.invoicesSubtitle')}</p>
      </header>

      {demoForcedMock && (
        <div className="banner-info" role="status">
          {t('finance.demoMockOnly')}{' '}
          <Link to="/finance/payments">{t('features.payments', { ns: 'common' })}</Link>
        </div>
      )}

      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      {viewModal}

      <div className="toolbar">
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <ProcessHowItWorks processCode="FIN_INVOICE_SETTLE" />
        <select
          value={filter}
          onChange={(e) => {
            setFilter(e.target.value)
            setPage((pg) => ({ ...pg, offset: 0 }))
          }}
          className="filter-select"
        >
          <option value="">{t('wb.filterAll', { ns: 'common' })}</option>
          {['DRAFT', 'ISSUED', 'PART_PAID', 'PAID', 'VOID'].map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <Link className="btn btn-secondary" to="/finance/transactions">
          {t('finance.generalLedgerLink')}
        </Link>
      </div>

      <div className="two-col finance-tx-grid">
        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('finance.newInvoice')}
          </h2>
          <form className="form-grid" onSubmit={onCreate}>
            <label>
              {t('finance.customerName')}
              <input
                value={form.customerName}
                onChange={(e) => setForm((f) => ({ ...f, customerName: e.target.value }))}
                placeholder={t('ph.acme', { ns: 'common' })}
              />
            </label>
            <div className="form-row-2">
              <label>
                {t('finance.amountRequired')}
                <input
                  required
                  value={form.amountEur}
                  onChange={(e) => setForm((f) => ({ ...f, amountEur: e.target.value }))}
                />
                <MoneyHint primaryRail="EUR" amount={form.amountEur} />
              </label>
            </div>
            <label>
              {t('finance.reference')}
              <input
                value={form.referenceText}
                onChange={(e) => setForm((f) => ({ ...f, referenceText: e.target.value }))}
              />
            </label>
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={!!form.issue}
                onChange={(e) => setForm((f) => ({ ...f, issue: e.target.checked }))}
              />
              {t('finance.issueImmediately')}
            </label>
            <PendingContentComposer
              package="finance"
              resource="Invoice"
              pending={pendingInvoiceContent}
              onChange={setPendingInvoiceContent}
              title={t('pending.titleInvoice', { ns: 'content' })}
              hint={t('pending.hintInvoice', { ns: 'content' })}
            />
            <button type="submit" className="btn btn-primary" disabled={!!busy}>
              <Plus size={16} /> {busy === 'create' ? t('actions.creating', { ns: 'common' }) : t('finance.createInvoice')}
            </button>
          </form>

          <h2 className="section-label">{t('finance.invoicesCount', { count: items.length })}</h2>
          {loading && <p className="muted">{t('actions.loading', { ns: 'common' })}</p>}
          <ResponsiveDataList
            items={items}
            empty={t('finance.noInvoicesYet')}
            loading={loading}
            selectedKey={selected?.id}
            onSelect={(inv) => selectInvoice(inv.id)}
            fields={[
              {
                id: 'number',
                primary: true,
                render: (inv) => (
                  <>
                    <span className="mono">{inv.invoiceNumber}</span>{' '}
                    <span className={statusClass(inv.invoiceStatus)}>{inv.invoiceStatus}</span>
                  </>
                ),
              },
              {
                id: 'customer',
                secondary: true,
                render: (inv) => inv.customerName || '—',
              },
              {
                id: 'eur',
                meta: true,
                render: (inv) =>
                  inv.amountEur != null
                    ? `${Number(inv.amountEur).toFixed(2)} EUR`
                    : '—',
              },
            ]}
            actions={(inv) => (
              <ViewButton pkg="finance" resource="Invoice" record={inv} />
            )}
          >
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('wb.number', { ns: 'common' })}</th>
                    <th>{t('wb.customer', { ns: 'common' })}</th>
                    <th>EUR</th>
                    <th>{t('wb.status', { ns: 'common' })}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {items.length === 0 && (
                    <tr>
                      <td colSpan={5} className="muted">
                        No invoices yet
                      </td>
                    </tr>
                  )}
                  {items.map((inv) => (
                    <tr
                      key={inv.id}
                      className={selected?.id === inv.id ? 'row-selected' : 'row-click'}
                      onClick={() => selectInvoice(inv.id)}
                    >
                      <td className="mono">{inv.invoiceNumber}</td>
                      <td>{inv.customerName || '—'}</td>
                      <td>
                        {inv.amountEur != null ? Number(inv.amountEur).toFixed(2) : '—'}
                      </td>
                      <td>
                        <span className={statusClass(inv.invoiceStatus)}>
                          {inv.invoiceStatus}
                        </span>
                      </td>
                      <td
                        style={{ whiteSpace: 'nowrap' }}
                        onClick={(e) => e.stopPropagation()}
                      >
                        <ViewButton pkg="finance" resource="Invoice" record={inv} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </ResponsiveDataList>
          <ListPager
            total={page.total}
            max={page.max}
            offset={page.offset}
            disabled={loading}
            onOffset={(o) => setPage((pg) => ({ ...pg, offset: o }))}
            onMax={(m) => setPage((pg) => ({ ...pg, max: m, offset: 0 }))}
          />
        </section>

        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('finance.detail')}
          </h2>
          {!selected && <p className="muted">{t('wb.selectItem', { ns: 'common' })}</p>}
          {selected && (
            <>
              <div className="inv-detail-hero">
                <div>
                  <div className="about-display" style={{ fontSize: '1.35rem' }}>
                    {selected.invoiceNumber}
                  </div>
                  <div className="muted">
                    {selected.customerName || t('wb.noCustomer', { ns: 'common' })} ·{' '}
                    <span className={statusClass(selected.invoiceStatus)}>
                      {selected.invoiceStatus}
                    </span>
                  </div>
                </div>
                <div className="inv-amount">
                  {Number(selected.amountEur).toFixed(2)} EUR
                  {selected.amountSats != null && (
                    <div className="muted mono" style={{ fontSize: '0.8rem' }}>
                      {selected.amountSats} sats
                    </div>
                  )}
                </div>
              </div>

              {selected.referenceText && (
                <p className="muted" style={{ marginTop: 0 }}>
                  {t('finance.refPrefix', { text: selected.referenceText })}
                </p>
              )}

              <div className="toolbar">
                {selected.invoiceStatus === 'DRAFT' && (
                  <button type="button" className="btn btn-primary" onClick={onIssue} disabled={!!busy}>
                    <Send size={16} /> {busy === 'issue' ? '…' : t('finance.issue')}
                  </button>
                )}
                {selected.invoiceStatus !== 'VOID' && (
                  <button type="button" className="btn" onClick={onVoid} disabled={!!busy}>
                    <Ban size={16} /> {t('finance.void')}
                  </button>
                )}
              </div>

              {((selected.btcIntegration || btcIntegration)?.level === 'SINGLE_ADDRESS' ||
                (selected.btcIntegration || btcIntegration)?.receiveAddress) &&
                ['ISSUED', 'PART_PAID', 'DRAFT'].includes(selected.invoiceStatus) && (
                  <div
                    className="card"
                    style={{
                      marginTop: '1rem',
                      padding: '0.85rem',
                      background: 'var(--surface-2, #f8fafc)',
                    }}
                  >
                    <h3 className="section-label" style={{ marginTop: 0 }}>
                      {t('finance.level2Receive')}
                    </h3>
                    {(selected.btcIntegration || btcIntegration)?.receiveAddressQrDataUrl && (
                      <img
                        src={(selected.btcIntegration || btcIntegration).receiveAddressQrDataUrl}
                        alt={t('finance.bitcoinReceiveQrAlt')}
                        width={160}
                        height={160}
                        style={{ display: 'block', marginBottom: 8 }}
                      />
                    )}
                    <p className="mono" style={{ wordBreak: 'break-all', fontSize: 13 }}>
                      {(selected.btcIntegration || btcIntegration)?.receiveAddress}
                    </p>
                    <p className="muted" style={{ fontSize: '0.82rem', margin: 0 }}>
                      {t('finance.level2ManualWarn')}
                    </p>
                  </div>
                )}

              {['ISSUED', 'PART_PAID'].includes(selected.invoiceStatus) && (
                <form className="form-grid" onSubmit={onSettle} style={{ marginTop: '1rem' }}>
                  <h3 className="section-label" style={{ marginTop: 0 }}>
                    {t('finance.recordPaymentSettle')}
                  </h3>
                  <div className="form-row-2">
                    <label>
                      {t('finance.rail')}
                      <select
                        value={settle.rail}
                        onChange={(e) => setSettle((s) => ({ ...s, rail: e.target.value }))}
                      >
                        {RAILS.map((r) => (
                          <option key={r.value} value={r.value}>
                            {t(r.labelKey)}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      {t('wb.amountEur', { ns: 'common' })}
                      <input
                        value={settle.amountEur}
                        onChange={(e) => setSettle((s) => ({ ...s, amountEur: e.target.value }))}
                        placeholder={t('ph.fullAmount', { ns: 'common' })}
                      />
                    </label>
                  </div>
                  {settle.rail === 'BTC_WALLET_ADDRESS' && (
                    <label>
                      {t('finance.btcWalletAddress')}
                      <input
                        value={settle.walletAddress}
                        onChange={(e) => setSettle((s) => ({ ...s, walletAddress: e.target.value }))}
                        placeholder="bc1… or 1… / 3…"
                        autoComplete="off"
                        spellCheck={false}
                      />
                    </label>
                  )}
                  {settle.rail === 'BTC_WALLET_ADDRESS' && (
                    <p
                      className="muted"
                      style={{ fontSize: '0.82rem', margin: 0, color: 'var(--warning, #9a6700)' }}
                    >
                      {t('finance.manualSettleSoft')}
                    </p>
                  )}
                  <p className="muted" style={{ fontSize: '0.82rem', margin: 0 }}>
                    {t('finance.settleLedgerHint')}
                  </p>
                  <button type="submit" className="btn btn-primary" disabled={!!busy}>
                    <Banknote size={16} /> {busy === 'settle' ? t('actions.settling', { ns: 'common' }) : t('finance.settlePayment')}
                  </button>
                </form>
              )}

              {selected?.id && (
                <p style={{ marginTop: '0.75rem' }}>
                  <a
                    className="btn"
                    href={financeRevenueApi.pdfUrl(selected.id)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <FileText size={16} /> {t('finance.downloadInvoicePdf')}
                  </a>
                </p>
              )}

              <h3 className="section-label">{t('finance.paymentOptions')}</h3>
              <ul className="inv-list">
                {(selected.paymentOptions || []).length === 0 && (
                  <li className="muted">{t('finance.noPaymentOptions')}</li>
                )}
                {(selected.paymentOptions || []).map((o) => (
                  <li key={o.id}>
                    <strong>{o.rail}</strong>
                    {o.estimatedSettle ? ` · ${o.estimatedSettle}` : ''}
                  </li>
                ))}
              </ul>

              <h3 className="section-label">{t('finance.payments')}</h3>
              <ul className="inv-list">
                {(selected.payments || []).length === 0 && <li className="muted">{t('finance.noPayments')}</li>}
                {(selected.payments || []).map((p) => (
                  <li key={p.id}>
                    {p.rail} · {Number(p.amountEur).toFixed(2)} EUR · {p.paymentStatus}
                    {p.journalEntryId ? ` · JE #${p.journalEntryId}` : ''}
                  </li>
                ))}
              </ul>
              {selected.paidEur != null && (
                <p className="muted">
                  {t('finance.paidTotal', { amount: Number(selected.paidEur).toFixed(2) })}
                </p>
              )}
              <ContentRail
                package="finance"
                resource="Invoice"
                id={selected.id}
                onChanged={async () =>
                  setInvoiceContentMap(await fetchContentMap('finance', 'Invoice', items))
                }
              />
            </>
          )}
        </section>
      </div>
    </div>
  )
}
