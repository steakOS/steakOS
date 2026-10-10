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
import {
  Headphones,
  RefreshCw,
  Plus,
  Check,
  X,
  Play,
  Shield,
  Package,
  MessageSquare,
  FileWarning,
  Users,
  FileText,
} from 'lucide-react'
import { aftersalesApi, salesApi, catalogueApi } from '../api/client'
import CatalogueItemSelect from '../components/CatalogueItemSelect'
import ProcessHowItWorks from '../components/ProcessHowItWorks'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import ResponsiveDataList from '../components/ResponsiveDataList'
import ListPager from '../components/ListPager'
import CustomerSelect from '../components/CustomerSelect'

const TAB_DEFS = [
  { id: 'inquiries', labelKey: 'aftersales.tabs.inquiries', icon: MessageSquare },
  { id: 'warranties', labelKey: 'aftersales.tabs.warranties', icon: Shield },
  { id: 'claims', labelKey: 'aftersales.tabs.claims', icon: FileWarning },
  { id: 'rmas', labelKey: 'aftersales.tabs.rmas', icon: Package },
  { id: 'levels', labelKey: 'aftersales.tabs.levels', icon: Headphones },
]

/**
 * AfterSales desk (AS-S0–AS-S3): same UI shell as Delivery desk.
 * Customers from sales.Customer; create customer for distributor / walk-in cases.
 */
export default function AfterSalesDesk() {
  const { t } = useTranslation(['desks', 'common', 'content'])
  const { viewModal, ViewButton } = useRecordView()
  const TABS = useMemo(
    () => TAB_DEFS.map((d) => ({ ...d, label: t(d.labelKey) })),
    [t],
  )
  const [tab, setTab] = useState('inquiries')
  const [customers, setCustomers] = useState([])
  const [levels, setLevels] = useState([])
  const [inquiries, setInquiries] = useState([])
  const [warranties, setWarranties] = useState([])
  const [claims, setClaims] = useState([])
  const [rmas, setRmas] = useState([])
  const [selectedInq, setSelectedInq] = useState(null)
  const [selectedClaim, setSelectedClaim] = useState(null)
  const [selectedRma, setSelectedRma] = useState(null)
  const [inqContentMap, setInqContentMap] = useState({})
  const [claimContentMap, setClaimContentMap] = useState({})
  const [rmaContentMap, setRmaContentMap] = useState({})
  const [pendingInqContent, setPendingInqContent] = useState([])
  const [pendingClaimContent, setPendingClaimContent] = useState([])
  const [pendingRmaContent, setPendingRmaContent] = useState([])
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [showNewCustomer, setShowNewCustomer] = useState(false)

  const [customerForm, setCustomerForm] = useState({
    name: '',
    email: '',
    contactName: '',
    customerKind: 'ORGANIZATION',
  })

  const [levelForm, setLevelForm] = useState({
    name: '',
    code: '',
    responseHours: '24',
    resolveHours: '72',
    channel: 'ANY',
  })
  const [inqForm, setInqForm] = useState({
    name: '',
    kind: 'REQUEST',
    channel: 'EMAIL',
    priority: 'NORMAL',
    customerId: '',
    serviceLevelId: '',
    description: '',
  })
  const [warForm, setWarForm] = useState({
    name: '',
    customerId: '',
    productName: '',
    catalogueItemId: '',
  })
  const [claimForm, setClaimForm] = useState({
    warrantyId: '',
    claimAmountEur: '0',
    claimAmountSats: '',
    description: '',
  })
  const [rmaForm, setRmaForm] = useState({
    customerId: '',
    catalogueItemId: '',
    productName: '',
    quantity: '1',
    reason: '',
    expectedRefundEur: '',
  })
  const [goodsItems, setGoodsItems] = useState([])

  const [levelPage, setLevelPage] = useState({ max: 25, offset: 0, total: 0 })
  const [inqPage, setInqPage] = useState({ max: 25, offset: 0, total: 0 })
  const [warPage, setWarPage] = useState({ max: 25, offset: 0, total: 0 })
  const [claimPage, setClaimPage] = useState({ max: 25, offset: 0, total: 0 })
  const [rmaPage, setRmaPage] = useState({ max: 25, offset: 0, total: 0 })
  /** Dropdown options (not paged) — max 100 until typeahead. */
  const [customerOptions, setCustomerOptions] = useState([])
  const [levelOptions, setLevelOptions] = useState([])
  const [warrantyOptions, setWarrantyOptions] = useState([])

  const customerLabel = (c) => {
    if (!c) return '—'
    const kind = c.customerKind ? ` (${c.customerKind})` : ''
    return `${c.name}${kind}`
  }

  const customerNameById = (id) => {
    if (id == null || id === '') return null
    const c =
      customerOptions.find((x) => String(x.id) === String(id)) ||
      customers.find((x) => String(x.id) === String(id))
    return c?.name || null
  }

  const applyCustomerTo = (setter, customerId) => {
    const c =
      customerOptions.find((x) => String(x.id) === String(customerId)) ||
      customers.find((x) => String(x.id) === String(customerId))
    setter((f) => ({
      ...f,
      customerId: customerId || '',
    }))
    return c
  }

  const loadAll = useCallback(async () => {
    const [cust, l, lPick, i, w, wPick, c, r, goods] = await Promise.all([
      salesApi.customers.list({ max: 100, offset: 0 }),
      aftersalesApi.serviceLevels.list({ max: levelPage.max, offset: levelPage.offset }),
      aftersalesApi.serviceLevels.list({ max: 100, offset: 0 }),
      aftersalesApi.inquiries.list({ max: inqPage.max, offset: inqPage.offset }),
      aftersalesApi.warranties.list({ max: warPage.max, offset: warPage.offset }),
      aftersalesApi.warranties.list({ max: 100, offset: 0 }),
      aftersalesApi.claims.list({ max: claimPage.max, offset: claimPage.offset }),
      aftersalesApi.rmas.list({ max: rmaPage.max, offset: rmaPage.offset }),
      catalogueApi.list({ nature: 'GOODS', activeOnly: true, max: 100, offset: 0 }),
    ])
    const custItems = cust.items || []
    const inqs = i.items || []
    const cls = c.items || []
    const rms = r.items || []
    const lvls = l.items || []
    const wars = w.items || []
    setCustomers(custItems)
    setCustomerOptions(custItems)
    setLevels(lvls)
    setLevelOptions(lPick.items || [])
    setInquiries(inqs)
    setWarranties(wars)
    setWarrantyOptions(wPick.items || [])
    setClaims(cls)
    setRmas(rms)
    setGoodsItems(goods.items || [])
    setLevelPage((pg) => ({
      ...pg,
      max: l.max ?? pg.max,
      offset: l.offset ?? pg.offset,
      total: l.total ?? lvls.length,
    }))
    setInqPage((pg) => ({
      ...pg,
      max: i.max ?? pg.max,
      offset: i.offset ?? pg.offset,
      total: i.total ?? inqs.length,
    }))
    setWarPage((pg) => ({
      ...pg,
      max: w.max ?? pg.max,
      offset: w.offset ?? pg.offset,
      total: w.total ?? wars.length,
    }))
    setClaimPage((pg) => ({
      ...pg,
      max: c.max ?? pg.max,
      offset: c.offset ?? pg.offset,
      total: c.total ?? cls.length,
    }))
    setRmaPage((pg) => ({
      ...pg,
      max: r.max ?? pg.max,
      offset: r.offset ?? pg.offset,
      total: r.total ?? rms.length,
    }))
    const [im, cm, rm] = await Promise.all([
      fetchContentMap('service', 'Inquiry', inqs),
      fetchContentMap('service', 'Claims', cls),
      fetchContentMap('service', 'Rma', rms),
    ])
    setInqContentMap(im)
    setClaimContentMap(cm)
    setRmaContentMap(rm)
  }, [
    levelPage.max,
    levelPage.offset,
    inqPage.max,
    inqPage.offset,
    warPage.max,
    warPage.offset,
    claimPage.max,
    claimPage.offset,
    rmaPage.max,
    rmaPage.offset,
  ])

  const selectInquiry = useCallback(async (id) => {
    if (!id) {
      setSelectedInq(null)
      return
    }
    setSelectedInq(await aftersalesApi.inquiries.get(id))
  }, [])

  const selectClaim = useCallback(async (id) => {
    if (!id) {
      setSelectedClaim(null)
      return
    }
    setSelectedClaim(await aftersalesApi.claims.get(id))
  }, [])

  const selectRma = useCallback(async (id) => {
    if (!id) {
      setSelectedRma(null)
      return
    }
    setSelectedRma(await aftersalesApi.rmas.get(id))
  }, [])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      await loadAll()
      if (selectedInq?.id) await selectInquiry(selectedInq.id)
      if (selectedClaim?.id) await selectClaim(selectedClaim.id)
      if (selectedRma?.id) await selectRma(selectedRma.id)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [
    loadAll,
    selectInquiry,
    selectClaim,
    selectRma,
    selectedInq?.id,
    selectedClaim?.id,
    selectedRma?.id,
  ])

  useEffect(() => {
    setLoading(true)
    loadAll()
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [loadAll])

  const statusClass = (s) => {
    if (['CLOSED', 'APPROVED', 'ACTIVE', 'CONFIRMED', 'RESOLVED', 'COMPLETED'].includes(s)) {
      return 'badge badge-ok'
    }
    if (['CANCELLED', 'REJECTED', 'VOID', 'EXPIRED'].includes(s)) return 'badge badge-muted'
    if (['IN_PROGRESS', 'VALIDATED', 'REQUESTED', 'SCHEDULED'].includes(s)) {
      return 'badge badge-warn'
    }
    return 'badge badge-draft'
  }

  const onCreateCustomer = async (e) => {
    e.preventDefault()
    setBusy('cust')
    setError(null)
    setInfo(null)
    try {
      const c = await salesApi.customers.create({
        name: customerForm.name,
        email: customerForm.email || null,
        contactName: customerForm.contactName || null,
        customerKind: customerForm.customerKind,
      })
      setInfo(t('toast.namedCreatedId', { ns: 'common', what: t('labels.customer', { ns: 'common' }), name: c.name, id: c.id }))
      setCustomerForm({ name: '', email: '', contactName: '', customerKind: 'ORGANIZATION' })
      await loadAll()
      // pre-select on open forms
      setInqForm((f) => ({ ...f, customerId: String(c.id) }))
      setWarForm((f) => ({ ...f, customerId: String(c.id) }))
      setRmaForm((f) => ({ ...f, customerId: String(c.id) }))
      setShowNewCustomer(false)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateLevel = async (e) => {
    e.preventDefault()
    setBusy('level')
    setError(null)
    setInfo(null)
    try {
      const s = await aftersalesApi.serviceLevels.create({
        ...levelForm,
        responseHours: levelForm.responseHours || null,
        resolveHours: levelForm.resolveHours || null,
      })
      setInfo(t('toast.levelCreated', { ns: 'common', name: s.name }))
      setLevelForm({ name: '', code: '', responseHours: '24', resolveHours: '72', channel: 'ANY' })
      setLevelPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateInq = async (e) => {
    e.preventDefault()
    setBusy('inq')
    setError(null)
    setInfo(null)
    try {
      const c = customerOptions.find((x) => String(x.id) === String(inqForm.customerId))
      const i = await aftersalesApi.inquiries.create({
        name: inqForm.name,
        kind: inqForm.kind,
        channel: inqForm.channel,
        priority: inqForm.priority,
        customerId: inqForm.customerId || undefined,
        customerName: c?.name || undefined,
        serviceLevelId: inqForm.serviceLevelId || undefined,
        description: inqForm.description || undefined,
      })
      if (pendingInqContent.length) {
        await attachPendingContent('service', 'Inquiry', i.id, pendingInqContent)
        setPendingInqContent([])
      }
      setInfo(t('toast.numberOpened', { ns: 'common', what: t('labels.inquiry', { ns: 'common' }), number: i.inquiryNumber }))
      setInqForm({
        name: '',
        kind: 'REQUEST',
        channel: 'EMAIL',
        priority: 'NORMAL',
        customerId: '',
        serviceLevelId: '',
        description: '',
      })
      setInqPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
      await selectInquiry(i.id)
      setTab('inquiries')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runInquiry = async (action) => {
    if (!selectedInq?.id) return
    setBusy(action)
    setError(null)
    setInfo(null)
    try {
      let result
      if (action === 'start') result = await aftersalesApi.inquiries.start(selectedInq.id)
      if (action === 'resolve') result = await aftersalesApi.inquiries.resolve(selectedInq.id)
      if (action === 'close') result = await aftersalesApi.inquiries.close(selectedInq.id)
      if (action === 'cancel') result = await aftersalesApi.inquiries.cancel(selectedInq.id)
      setSelectedInq(result)
      setInfo(t('toast.numberStatus', { ns: 'common', what: t('labels.inquiry', { ns: 'common' }), number: result.inquiryNumber, status: result.caseStatus }))
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateWar = async (e) => {
    e.preventDefault()
    setBusy('war')
    setError(null)
    setInfo(null)
    try {
      const c = customerOptions.find((x) => String(x.id) === String(warForm.customerId))
      const item = goodsItems.find((x) => String(x.id) === String(warForm.catalogueItemId))
      const w = await aftersalesApi.warranties.create({
        name: warForm.name || undefined,
        customerId: warForm.customerId || undefined,
        customerName: c?.name || undefined,
        productName: warForm.productName || item?.name || undefined,
        catalogueItemId: warForm.catalogueItemId || undefined,
        coverStatus: 'ACTIVE',
      })
      setInfo(t('toast.warrantyReg', { ns: 'common', number: w.warrantyNumber }))
      setWarForm({ name: '', customerId: '', productName: '', catalogueItemId: '' })
      setWarPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
      setTab('warranties')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateClaim = async (e) => {
    e.preventDefault()
    setBusy('claim')
    setError(null)
    setInfo(null)
    try {
      const c = await aftersalesApi.claims.create({
        warrantyId: claimForm.warrantyId,
        claimAmountEur: claimForm.claimAmountEur || '0',
        claimAmountSats: claimForm.claimAmountSats || undefined,
        description: claimForm.description || undefined,
      })
      if (pendingClaimContent.length) {
        await attachPendingContent('service', 'Claims', c.id, pendingClaimContent)
        setPendingClaimContent([])
      }
      setInfo(t('toast.numberOpened', { ns: 'common', what: t('labels.claim', { ns: 'common' }), number: c.claimNumber }))
      setClaimForm({ warrantyId: '', claimAmountEur: '0', claimAmountSats: '', description: '' })
      setClaimPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
      await selectClaim(c.id)
      setTab('claims')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runClaim = async (action) => {
    if (!selectedClaim?.id) return
    setBusy(action)
    setError(null)
    setInfo(null)
    try {
      if (action === 'validate') {
        const result = await aftersalesApi.claims.validate(selectedClaim.id)
        setSelectedClaim(result)
        setInfo(t('toast.validated', { ns: 'common', number: result.claimNumber }))
      } else if (action === 'approve') {
        const result = await aftersalesApi.claims.approve(selectedClaim.id)
        setSelectedClaim(result.claim || result)
        if (result.financeCreditInvoiceId) {
          setInfo(
            `Claim approved → Finance credit #${result.financeCreditInvoiceId}`,
          )
        } else {
          setInfo(
            `Claim approved${result.finance?.error ? ` (credit: ${result.finance.error})` : ''}`,
          )
        }
      } else if (action === 'reject') {
        const result = await aftersalesApi.claims.reject(selectedClaim.id, {
          resolution: 'Rejected from desk',
        })
        setSelectedClaim(result)
        setInfo(t('toast.claimRejected', { ns: 'common', number: result.claimNumber }))
      } else if (action === 'close') {
        const result = await aftersalesApi.claims.close(selectedClaim.id)
        setSelectedClaim(result)
        setInfo(t('toast.claimClosed', { ns: 'common', number: result.claimNumber }))
      }
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateRma = async (e) => {
    e.preventDefault()
    setBusy('rma')
    setError(null)
    setInfo(null)
    try {
      const c = customerOptions.find((x) => String(x.id) === String(rmaForm.customerId))
      const item = goodsItems.find((x) => String(x.id) === String(rmaForm.catalogueItemId))
      const r = await aftersalesApi.rmas.create({
        customerId: rmaForm.customerId || undefined,
        customerName: c?.name || undefined,
        catalogueItemId: rmaForm.catalogueItemId || undefined,
        productName: rmaForm.productName || item?.name || undefined,
        quantity: rmaForm.quantity,
        reason: rmaForm.reason || undefined,
        expectedRefundEur: rmaForm.expectedRefundEur || undefined,
      })
      if (pendingRmaContent.length) {
        await attachPendingContent('service', 'Rma', r.id, pendingRmaContent)
        setPendingRmaContent([])
      }
      setInfo(t('toast.numberOpened', { ns: 'common', what: t('labels.rma', { ns: 'common' }), number: r.rmaNumber }))
      setRmaForm({
        customerId: '',
        catalogueItemId: '',
        productName: '',
        quantity: '1',
        reason: '',
        expectedRefundEur: '',
      })
      setRmaPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
      await selectRma(r.id)
      setTab('rmas')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runRma = async (action) => {
    if (!selectedRma?.id) return
    setBusy(action)
    setError(null)
    setInfo(null)
    try {
      if (action === 'confirm') {
        const result = await aftersalesApi.rmas.confirm(selectedRma.id)
        setSelectedRma(result.rma || result)
        setInfo(
          result.stockMoveId
            ? `RMA confirmed → stock move #${result.stockMoveId}`
            : 'RMA confirmed (no stock — set product id)',
        )
      } else if (action === 'close') {
        const result = await aftersalesApi.rmas.close(selectedRma.id)
        setSelectedRma(result)
        setInfo(t('toast.rmaClosed', { ns: 'common', number: result.rmaNumber }))
      } else if (action === 'cancel') {
        const result = await aftersalesApi.rmas.cancel(selectedRma.id)
        setSelectedRma(result)
        setInfo(t('toast.rmaCancelled', { ns: 'common', number: result.rmaNumber }))
      }
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  /** Shared customer typeahead + create-customer affordance */
  const CustomerField = ({ value, onChange, required = false, label = 'Customer' }) => (
    <div>
      <label>
        {label}
        {required ? ' *' : ''}
        <CustomerSelect
          value={value}
          onChange={(id) => onChange(id || '')}
          disabled={!!busy}
        />
      </label>
      {required && !value && (
        <input
          tabIndex={-1}
          autoComplete="off"
          style={{ opacity: 0, height: 0, position: 'absolute' }}
          value=""
          onChange={() => {}}
          required
        />
      )}
      <div className="toolbar" style={{ marginTop: 6, marginBottom: 0 }}>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => setShowNewCustomer((v) => !v)}
        >
          <Users size={14} /> {showNewCustomer ? t('aftersales.hideNewCustomer') : t('aftersales.newCustomer')}
        </button>
        <Link className="btn btn-secondary" to="/sales">
          {t('aftersales.salesDesk')}
        </Link>
      </div>
    </div>
  )

  return (
    <div className="page delivery-desk-page">
      <header className="page-header" style={{ marginBottom: '1.25rem' }}>
        <h1 className="page-title">
          <Headphones size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('aftersales.title')}
        </h1>
        <p className="page-sub">{t('aftersales.subtitle')}</p>
      </header>

      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      {viewModal}

      <div className="toolbar">
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <ProcessHowItWorks processCode="AS_INQUIRY" />
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          {TABS.map((tabDef) => {
            const Icon = tabDef.icon
            return (
              <button
                key={tabDef.id}
                type="button"
                className={`btn ${tab === tabDef.id ? 'btn-primary' : 'btn-ghost'}`}
                onClick={() => setTab(tabDef.id)}
              >
                <Icon size={14} /> {tabDef.label}
              </button>
            )
          })}
        </div>
        <button
          type="button"
          className={`btn ${showNewCustomer ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setShowNewCustomer((v) => !v)}
        >
          <Users size={14} /> {t('aftersales.newCustomer')}
        </button>
        <Link className="btn btn-secondary" to="/sales">
          {t('aftersales.salesDesk')}
        </Link>
        <Link className="btn btn-secondary" to="/delivery">
          {t('aftersales.deliveryDesk')}
        </Link>
      </div>

      {showNewCustomer && (
        <div className="two-col finance-tx-grid list-first-narrow" style={{ marginBottom: '1rem' }}>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('aftersales.newCustomer')}
            </h2>
            <p className="muted" style={{ marginTop: 0, fontSize: '0.9rem' }}>
              For end customers reached via a distributor (or any case not already on the sales
              books). Uses the Sales Customer domain.
            </p>
            <form className="form-grid" onSubmit={onCreateCustomer}>
              <label>
                Name *
                <input
                  required
                  value={customerForm.name}
                  onChange={(e) => setCustomerForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder={t('aftersales.customerPlaceholder')}
                />
              </label>
              <div className="form-row-2">
                <label>
                  Kind
                  <select
                    value={customerForm.customerKind}
                    onChange={(e) =>
                      setCustomerForm((f) => ({ ...f, customerKind: e.target.value }))
                    }
                  >
                    {['ORGANIZATION', 'PERSON'].map((k) => (
                      <option key={k} value={k}>
                        {k}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Contact
                  <input
                    value={customerForm.contactName}
                    onChange={(e) =>
                      setCustomerForm((f) => ({ ...f, contactName: e.target.value }))
                    }
                  />
                </label>
              </div>
              <label>
                Email
                <input
                  type="email"
                  value={customerForm.email}
                  onChange={(e) => setCustomerForm((f) => ({ ...f, email: e.target.value }))}
                />
              </label>
              <div className="toolbar" style={{ marginBottom: 0 }}>
                <button type="submit" className="btn btn-primary" disabled={!!busy}>
                  <Plus size={16} /> {t('aftersales.createCustomer')}
                </button>
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => setShowNewCustomer(false)}
                >
                  Cancel
                </button>
              </div>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Customers ({customers.length})
            </h2>
            <ResponsiveDataList
              items={customers}
              empty={t('aftersales.emptyCustomers')}
              fields={[
                { id: 'name', primary: true, render: (c) => c.name },
                {
                  id: 'email',
                  secondary: true,
                  render: (c) => c.email || '—',
                },
                {
                  id: 'kind',
                  priority: 'low',
                  render: (c) => c.customerKind || '—',
                },
              ]}
              actions={(c) => (
                <ViewButton pkg="sales" resource="Customer" record={c} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.kind', { ns: 'common' })}</th>
                      <th>{t('wb.email', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {customers.map((c) => (
                      <tr key={c.id}>
                        <td>{c.name}</td>
                        <td className="col-priority-low">{c.customerKind || '—'}</td>
                        <td className="muted">{c.email || '—'}</td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="sales" resource="Customer" record={c} />
                        </td>
                      </tr>
                    ))}
                    {!customers.length && (
                      <tr>
                        <td colSpan={4} className="muted">
                          {t('aftersales.emptyCustomers')}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
          </section>
        </div>
      )}

      {tab === 'levels' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('aftersales.newLevel')}
            </h2>
            <form className="form-grid" onSubmit={onCreateLevel}>
              <label>
                Name *
                <input
                  required
                  value={levelForm.name}
                  onChange={(e) => setLevelForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder={t('ph.gold', { ns: 'common' })}
                />
              </label>
              <div className="form-row-2">
                <label>
                  Code
                  <input
                    value={levelForm.code}
                    onChange={(e) => setLevelForm((f) => ({ ...f, code: e.target.value }))}
                  />
                </label>
                <label>
                  Channel
                  <select
                    value={levelForm.channel}
                    onChange={(e) => setLevelForm((f) => ({ ...f, channel: e.target.value }))}
                  >
                    {['ANY', 'EMAIL', 'CHAT', 'PHONE', 'OTHER'].map((ch) => (
                      <option key={ch} value={ch}>
                        {ch}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <div className="form-row-2">
                <label>
                  Response hrs
                  <input
                    value={levelForm.responseHours}
                    onChange={(e) =>
                      setLevelForm((f) => ({ ...f, responseHours: e.target.value }))
                    }
                  />
                </label>
                <label>
                  Resolve hrs
                  <input
                    value={levelForm.resolveHours}
                    onChange={(e) =>
                      setLevelForm((f) => ({ ...f, resolveHours: e.target.value }))
                    }
                  />
                </label>
              </div>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('aftersales.createLevel')}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Service levels ({levelPage.total})
            </h2>
            <ResponsiveDataList
              items={levels}
              empty="—"
              fields={[
                { id: 'name', primary: true, render: (sl) => sl.name },
                {
                  id: 'code',
                  secondary: true,
                  render: (sl) => (
                    <span className="mono">{sl.code || '—'}</span>
                  ),
                },
                {
                  id: 'hours',
                  meta: true,
                  render: (sl) =>
                    [
                      sl.responseHours != null ? `${sl.responseHours}h resp` : null,
                      sl.resolveHours != null ? `${sl.resolveHours}h resolve` : null,
                    ]
                      .filter(Boolean)
                      .join(' · ') || '—',
                },
              ]}
              actions={(sl) => (
                <ViewButton pkg="service" resource="ServiceLevel" record={sl} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th>{t('wb.code', { ns: 'common' })}</th>
                      <th className="col-priority-low">
                        {t('wb.period', { ns: 'common' }) /* Response hours */}
                      </th>
                      <th className="col-priority-low">{t('aftersales.resolve')}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {levels.map((sl) => (
                      <tr key={sl.id}>
                        <td>{sl.name}</td>
                        <td className="mono">{sl.code || '—'}</td>
                        <td className="col-priority-low">
                          {sl.responseHours != null ? `${sl.responseHours}h` : '—'}
                        </td>
                        <td className="col-priority-low">
                          {sl.resolveHours != null ? `${sl.resolveHours}h` : '—'}
                        </td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="service" resource="ServiceLevel" record={sl} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={levelPage.total}
              max={levelPage.max}
              offset={levelPage.offset}
              disabled={loading}
              onOffset={(o) => setLevelPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setLevelPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'inquiries' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Open inquiry
            </h2>
            <form className="form-grid" onSubmit={onCreateInq}>
              <label>
                Subject *
                <input
                  required
                  value={inqForm.name}
                  onChange={(e) => setInqForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder={t('aftersales.subjectPlaceholder')}
                />
              </label>
              <CustomerField
                value={inqForm.customerId}
                onChange={(id) => applyCustomerTo(setInqForm, id)}
              />
              <div className="form-row-2">
                <label>
                  Kind
                  <select
                    value={inqForm.kind}
                    onChange={(e) => setInqForm((f) => ({ ...f, kind: e.target.value }))}
                  >
                    {['REQUEST', 'PROBLEM', 'COMPLAINT', 'OTHER'].map((k) => (
                      <option key={k} value={k}>
                        {k}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Priority
                  <select
                    value={inqForm.priority}
                    onChange={(e) => setInqForm((f) => ({ ...f, priority: e.target.value }))}
                  >
                    {['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((p) => (
                      <option key={p} value={p}>
                        {p}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <div className="form-row-2">
                <label>
                  Channel
                  <select
                    value={inqForm.channel}
                    onChange={(e) => setInqForm((f) => ({ ...f, channel: e.target.value }))}
                  >
                    {['EMAIL', 'CHAT', 'PHONE', 'WEB', 'OTHER'].map((ch) => (
                      <option key={ch} value={ch}>
                        {ch}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Service level
                  <select
                    value={inqForm.serviceLevelId}
                    onChange={(e) =>
                      setInqForm((f) => ({ ...f, serviceLevelId: e.target.value }))
                    }
                  >
                    <option value="">— optional —</option>
                    {levelOptions.map((sl) => (
                      <option key={sl.id} value={sl.id}>
                        {sl.name}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <label>
                Description
                <textarea
                  rows={2}
                  value={inqForm.description}
                  onChange={(e) => setInqForm((f) => ({ ...f, description: e.target.value }))}
                />
              </label>
              <PendingContentComposer
                package="service"
                resource="Inquiry"
                pending={pendingInqContent}
                onChange={setPendingInqContent}
                title={t('pending.titleCase', { ns: 'content' })}
                hint={t('pending.hintCase', { ns: 'content' })}
              />
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('aftersales.openCase')}
              </button>
            </form>

            <h2 className="section-label">
              {t('aftersales.inquiriesCount', { count: inqPage.total })}
            </h2>
            <ResponsiveDataList
              items={inquiries}
              empty="—"
              selectedKey={selectedInq?.id}
              onSelect={(i) => selectInquiry(i.id)}
              fields={[
                {
                  id: 'number',
                  primary: true,
                  render: (i) => (
                    <>
                      <span className="mono">{i.inquiryNumber}</span>{' '}
                      <span className={statusClass(i.caseStatus)}>{i.caseStatus}</span>
                      {i.overdue ? ' ⚠' : ''}
                    </>
                  ),
                },
                {
                  id: 'subject',
                  secondary: true,
                  render: (i) => i.name || '—',
                },
                {
                  id: 'customer',
                  meta: true,
                  render: (i) =>
                    i.customerName || customerNameById(i.customerId) || '—',
                },
                {
                  id: 'kind',
                  priority: 'low',
                  render: (i) => i.kind,
                },
              ]}
              actions={(i) => (
                <ViewButton pkg="service" resource="Inquiry" record={i} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.number', { ns: 'common' })}</th>
                      <th>{t('wb.subject', { ns: 'common' })}</th>
                      <th>{t('wb.customer', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.kind', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {inquiries.map((i) => (
                      <tr
                        key={i.id}
                        className={selectedInq?.id === i.id ? 'row-selected' : 'row-click'}
                        onClick={() => selectInquiry(i.id)}
                      >
                        <td className="mono">{i.inquiryNumber}</td>
                        <td>
                          {i.name}
                          {i.overdue ? ' ⚠' : ''}
                        </td>
                        <td>{i.customerName || customerNameById(i.customerId) || '—'}</td>
                        <td className="col-priority-low">{i.kind}</td>
                        <td
                          className="col-priority-low"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <ContentChannelIcons summary={inqContentMap[i.id]} />
                        </td>
                        <td>
                          <span className={statusClass(i.caseStatus)}>{i.caseStatus}</span>
                        </td>
                        <td
                          style={{ whiteSpace: 'nowrap' }}
                          onClick={(e) => e.stopPropagation()}
                        >
                          <ViewButton pkg="service" resource="Inquiry" record={i} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={inqPage.total}
              max={inqPage.max}
              offset={inqPage.offset}
              disabled={loading}
              onOffset={(o) => setInqPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setInqPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('aftersales.inquiryDetail')}
            </h2>
            {!selectedInq && <p className="muted">{t('aftersales.selectInquiry')}</p>}
            {selectedInq && (
              <>
                <div className="inv-detail-hero">
                  <div>
                    <div className="about-display" style={{ fontSize: '1.35rem' }}>
                      {selectedInq.inquiryNumber}
                    </div>
                    <div className="muted">
                      {selectedInq.customerName ||
                        customerNameById(selectedInq.customerId) ||
                        t('wb.noCustomer', { ns: 'common' })}{' '}
                      ·{' '}
                      <span className={statusClass(selectedInq.caseStatus)}>
                        {selectedInq.caseStatus}
                      </span>
                    </div>
                    <div className="muted" style={{ fontSize: '0.85rem' }}>
                      {selectedInq.kind} · {selectedInq.priority} · {selectedInq.channel}
                      {selectedInq.overdue
                        ? ` · ${t('pages.dashboard.overdue', { ns: 'common' })}`
                        : ''}
                    </div>
                  </div>
                </div>
                {selectedInq.description && (
                  <p style={{ whiteSpace: 'pre-wrap' }}>{selectedInq.description}</p>
                )}
                <div className="toolbar">
                  {['OPEN'].includes(selectedInq.caseStatus) && (
                    <button
                      type="button"
                      className="btn"
                      onClick={() => runInquiry('start')}
                      disabled={!!busy}
                    >
                      <Play size={16} /> {t('aftersales.start')}
                    </button>
                  )}
                  {['OPEN', 'IN_PROGRESS'].includes(selectedInq.caseStatus) && (
                    <button
                      type="button"
                      className="btn"
                      onClick={() => runInquiry('resolve')}
                      disabled={!!busy}
                    >
                      <Check size={16} /> {t('aftersales.resolve')}
                    </button>
                  )}
                  {selectedInq.caseStatus !== 'CLOSED' &&
                    selectedInq.caseStatus !== 'CANCELLED' && (
                      <button
                        type="button"
                        className="btn btn-primary"
                        onClick={() => runInquiry('close')}
                        disabled={!!busy}
                      >
                        {t('actions.close', { ns: 'common' })}
                      </button>
                    )}
                  {selectedInq.caseStatus !== 'CLOSED' &&
                    selectedInq.caseStatus !== 'CANCELLED' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        onClick={() => runInquiry('cancel')}
                        disabled={!!busy}
                      >
                        <X size={16} /> {t('actions.cancel', { ns: 'common' })}
                      </button>
                    )}
                </div>
                <ContentRail
                  package="service"
                  resource="Inquiry"
                  id={selectedInq.id}
                  onChanged={async () =>
                    setInqContentMap(await fetchContentMap('service', 'Inquiry', inquiries))
                  }
                />
              </>
            )}
          </section>
        </div>
      )}

      {tab === 'warranties' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Register warranty
            </h2>
            <form className="form-grid" onSubmit={onCreateWar}>
              <label>
                Name
                <input
                  value={warForm.name}
                  onChange={(e) => setWarForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder={t('aftersales.coverLabel')}
                />
              </label>
              <CustomerField
                value={warForm.customerId}
                onChange={(id) => applyCustomerTo(setWarForm, id)}
              />
              <label>
                Catalogue item (GOODS)
                <CatalogueItemSelect
                  forChannel="sale"
                  nature="GOODS"
                  value={warForm.catalogueItemId}
                  onChange={(id, item) =>
                    setWarForm((f) => ({
                      ...f,
                      catalogueItemId: id,
                      productName: item?.name || f.productName,
                    }))
                  }
                  disabled={!!busy}
                />
              </label>
              <label>
                Cover label / product name (optional)
                <input
                  value={warForm.productName}
                  onChange={(e) => setWarForm((f) => ({ ...f, productName: e.target.value }))}
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> Register (ACTIVE)
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Warranties ({warPage.total})
            </h2>
            <ResponsiveDataList
              items={warranties}
              empty="—"
              fields={[
                {
                  id: 'number',
                  primary: true,
                  render: (w) => (
                    <>
                      <span className="mono">{w.warrantyNumber}</span>{' '}
                      <span className={statusClass(w.coverStatus)}>
                        {w.coverStatus}
                        {w.inForce ? ' · in force' : ''}
                      </span>
                    </>
                  ),
                },
                {
                  id: 'customer',
                  secondary: true,
                  render: (w) =>
                    w.customerName || customerNameById(w.customerId) || '—',
                },
                {
                  id: 'product',
                  meta: true,
                  render: (w) => w.productName || w.productId || '—',
                },
              ]}
              actions={(w) => (
                <ViewButton pkg="service" resource="Warranty" record={w} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.number', { ns: 'common' })}</th>
                      <th>{t('wb.customer', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.product', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {warranties.map((w) => (
                      <tr key={w.id}>
                        <td className="mono">{w.warrantyNumber}</td>
                        <td>{w.customerName || customerNameById(w.customerId) || '—'}</td>
                        <td className="col-priority-low">
                          {w.productName || w.productId || '—'}
                        </td>
                        <td>
                          <span className={statusClass(w.coverStatus)}>
                            {w.coverStatus}
                            {w.inForce ? ' · in force' : ''}
                          </span>
                        </td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="service" resource="Warranty" record={w} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={warPage.total}
              max={warPage.max}
              offset={warPage.offset}
              disabled={loading}
              onOffset={(o) => setWarPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setWarPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'claims' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Receive claim
            </h2>
            <form className="form-grid" onSubmit={onCreateClaim}>
              <label>
                {t('aftersales.warrantyField')} *
                <select
                  required
                  value={claimForm.warrantyId}
                  onChange={(e) => setClaimForm((f) => ({ ...f, warrantyId: e.target.value }))}
                >
                  <option value="">— select —</option>
                  {warrantyOptions
                    .filter((w) => w.coverStatus === 'ACTIVE' || w.inForce)
                    .map((w) => (
                      <option key={w.id} value={w.id}>
                        {w.warrantyNumber} —{' '}
                        {w.customerName || customerNameById(w.customerId) || w.name}
                      </option>
                    ))}
                </select>
              </label>
              <div className="form-row-2">
                <label>
                  Amount EUR *
                  <input
                    required
                    value={claimForm.claimAmountEur}
                    onChange={(e) =>
                      setClaimForm((f) => ({ ...f, claimAmountEur: e.target.value }))
                    }
                  />
                </label>
                <label>
                  Amount sats
                  <input
                    value={claimForm.claimAmountSats}
                    onChange={(e) =>
                      setClaimForm((f) => ({ ...f, claimAmountSats: e.target.value }))
                    }
                  />
                </label>
              </div>
              <label>
                Notes
                <textarea
                  rows={2}
                  value={claimForm.description}
                  onChange={(e) => setClaimForm((f) => ({ ...f, description: e.target.value }))}
                />
              </label>
              <PendingContentComposer
                package="service"
                resource="Claims"
                pending={pendingClaimContent}
                onChange={setPendingClaimContent}
              />
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> Receive claim
              </button>
            </form>

            <h2 className="section-label">Claims ({claimPage.total})</h2>
            <ResponsiveDataList
              items={claims}
              empty="—"
              selectedKey={selectedClaim?.id}
              onSelect={(c) => selectClaim(c.id)}
              fields={[
                {
                  id: 'number',
                  primary: true,
                  render: (c) => (
                    <>
                      <span className="mono">{c.claimNumber}</span>{' '}
                      <span className={statusClass(c.claimStatus)}>{c.claimStatus}</span>
                    </>
                  ),
                },
                {
                  id: 'warranty',
                  secondary: true,
                  render: (c) => c.warrantyNumber || c.warrantyId || '—',
                },
                {
                  id: 'eur',
                  meta: true,
                  render: (c) =>
                    c.claimAmountEur != null
                      ? `${Number(c.claimAmountEur).toFixed(2)} EUR`
                      : '—',
                },
              ]}
              actions={(c) => (
                <ViewButton pkg="service" resource="Claims" record={c} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.number', { ns: 'common' })}</th>
                      <th>{t('aftersales.warrantiesList')}</th>
                      <th>EUR</th>
                      <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {claims.map((c) => (
                      <tr
                        key={c.id}
                        className={selectedClaim?.id === c.id ? 'row-selected' : 'row-click'}
                        onClick={() => selectClaim(c.id)}
                      >
                        <td className="mono">{c.claimNumber}</td>
                        <td>{c.warrantyNumber || c.warrantyId}</td>
                        <td>
                          {c.claimAmountEur != null
                            ? Number(c.claimAmountEur).toFixed(2)
                            : '—'}
                        </td>
                        <td
                          className="col-priority-low"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <ContentChannelIcons summary={claimContentMap[c.id]} />
                        </td>
                        <td>
                          <span className={statusClass(c.claimStatus)}>{c.claimStatus}</span>
                        </td>
                        <td
                          style={{ whiteSpace: 'nowrap' }}
                          onClick={(e) => e.stopPropagation()}
                        >
                          <ViewButton pkg="service" resource="Claims" record={c} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={claimPage.total}
              max={claimPage.max}
              offset={claimPage.offset}
              disabled={loading}
              onOffset={(o) => setClaimPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setClaimPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('aftersales.claimDetail')}
            </h2>
            {!selectedClaim && <p className="muted">{t('aftersales.selectClaim')}</p>}
            {selectedClaim && (
              <>
                <div className="inv-detail-hero">
                  <div>
                    <div className="about-display" style={{ fontSize: '1.35rem' }}>
                      {selectedClaim.claimNumber}
                    </div>
                    <div className="muted">
                      {t('aftersales.warrantyRef', {
                        ref: selectedClaim.warrantyNumber || `#${selectedClaim.warrantyId}`,
                      })}{' '}
                      ·{' '}
                      <span className={statusClass(selectedClaim.claimStatus)}>
                        {selectedClaim.claimStatus}
                      </span>
                    </div>
                    {selectedClaim.financeCreditInvoiceId && (
                      <div className="muted" style={{ fontSize: '0.85rem' }}>
                        {t('aftersales.financeCreditInvoice', {
                          id: selectedClaim.financeCreditInvoiceId,
                        })}
                      </div>
                    )}
                  </div>
                  <div className="inv-amount">
                    {Number(selectedClaim.claimAmountEur || 0).toFixed(2)} EUR
                    {selectedClaim.claimAmountSats != null && (
                      <div className="muted mono" style={{ fontSize: '0.8rem' }}>
                        {selectedClaim.claimAmountSats} sats
                      </div>
                    )}
                  </div>
                </div>
                <div className="toolbar">
                  {['RECEIVED'].includes(selectedClaim.claimStatus) && (
                    <button
                      type="button"
                      className="btn"
                      onClick={() => runClaim('validate')}
                      disabled={!!busy}
                    >
                      {t('aftersales.validate')}
                    </button>
                  )}
                  {['RECEIVED', 'VALIDATED'].includes(selectedClaim.claimStatus) && (
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={() => runClaim('approve')}
                      disabled={!!busy}
                    >
                      <Check size={16} /> {t('aftersales.approveFinance')}
                    </button>
                  )}
                  {['RECEIVED', 'VALIDATED'].includes(selectedClaim.claimStatus) && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      onClick={() => runClaim('reject')}
                      disabled={!!busy}
                    >
                      <X size={16} /> {t('aftersales.reject')}
                    </button>
                  )}
                  {['APPROVED', 'REJECTED'].includes(selectedClaim.claimStatus) && (
                    <button
                      type="button"
                      className="btn"
                      onClick={() => runClaim('close')}
                      disabled={!!busy}
                    >
                      {t('actions.close', { ns: 'common' })}
                    </button>
                  )}
                </div>
                <ContentRail
                  package="service"
                  resource="Claims"
                  id={selectedClaim.id}
                  onChanged={async () =>
                    setClaimContentMap(await fetchContentMap('service', 'Claims', claims))
                  }
                />
              </>
            )}
          </section>
        </div>
      )}

      {tab === 'rmas' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Open return (RMA)
            </h2>
            <form className="form-grid" onSubmit={onCreateRma}>
              <CustomerField
                value={rmaForm.customerId}
                onChange={(id) => applyCustomerTo(setRmaForm, id)}
              />
              <label>
                Catalogue item (GOODS — stock on confirm)
                <CatalogueItemSelect
                  forChannel="purchase"
                  nature="GOODS"
                  value={rmaForm.catalogueItemId}
                  onChange={(id, item) =>
                    setRmaForm((f) => ({
                      ...f,
                      catalogueItemId: id,
                      productName: item?.name || f.productName,
                    }))
                  }
                  disabled={!!busy}
                />
              </label>
              <label>
                Product name (snapshot, optional)
                <input
                  value={rmaForm.productName}
                  onChange={(e) => setRmaForm((f) => ({ ...f, productName: e.target.value }))}
                />
              </label>
              <div className="form-row-2">
                <label>
                  Qty
                  <input
                    value={rmaForm.quantity}
                    onChange={(e) => setRmaForm((f) => ({ ...f, quantity: e.target.value }))}
                  />
                </label>
                <label>
                  Expected refund EUR
                  <input
                    value={rmaForm.expectedRefundEur}
                    onChange={(e) =>
                      setRmaForm((f) => ({ ...f, expectedRefundEur: e.target.value }))
                    }
                  />
                </label>
              </div>
              <label>
                Reason
                <textarea
                  rows={2}
                  value={rmaForm.reason}
                  onChange={(e) => setRmaForm((f) => ({ ...f, reason: e.target.value }))}
                />
              </label>
              <PendingContentComposer
                package="service"
                resource="Rma"
                pending={pendingRmaContent}
                onChange={setPendingRmaContent}
              />
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('aftersales.openRma')}
              </button>
            </form>

            <h2 className="section-label">
              {t('aftersales.returnsCount', { count: rmaPage.total })}
            </h2>
            <ResponsiveDataList
              items={rmas}
              empty="—"
              selectedKey={selectedRma?.id}
              onSelect={(r) => selectRma(r.id)}
              fields={[
                {
                  id: 'number',
                  primary: true,
                  render: (r) => (
                    <>
                      <span className="mono">{r.rmaNumber}</span>{' '}
                      <span className={statusClass(r.rmaStatus)}>{r.rmaStatus}</span>
                    </>
                  ),
                },
                {
                  id: 'customer',
                  secondary: true,
                  render: (r) =>
                    r.customerName || customerNameById(r.customerId) || '—',
                },
                {
                  id: 'product',
                  meta: true,
                  render: (r) => r.productName || r.productId || '—',
                },
              ]}
              actions={(r) => (
                <ViewButton pkg="service" resource="Rma" record={r} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.number', { ns: 'common' })}</th>
                      <th>{t('wb.customer', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.product', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {rmas.map((r) => (
                      <tr
                        key={r.id}
                        className={selectedRma?.id === r.id ? 'row-selected' : 'row-click'}
                        onClick={() => selectRma(r.id)}
                      >
                        <td className="mono">{r.rmaNumber}</td>
                        <td>{r.customerName || customerNameById(r.customerId) || '—'}</td>
                        <td className="col-priority-low">
                          {r.productName || r.productId || '—'}
                        </td>
                        <td
                          className="col-priority-low"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <ContentChannelIcons summary={rmaContentMap[r.id]} />
                        </td>
                        <td>
                          <span className={statusClass(r.rmaStatus)}>{r.rmaStatus}</span>
                        </td>
                        <td
                          style={{ whiteSpace: 'nowrap' }}
                          onClick={(e) => e.stopPropagation()}
                        >
                          <ViewButton pkg="service" resource="Rma" record={r} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={rmaPage.total}
              max={rmaPage.max}
              offset={rmaPage.offset}
              disabled={loading}
              onOffset={(o) => setRmaPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setRmaPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('aftersales.rmaDetail')}
            </h2>
            {!selectedRma && <p className="muted">{t('aftersales.selectRma')}</p>}
            {selectedRma && (
              <>
                <div className="inv-detail-hero">
                  <div>
                    <div className="about-display" style={{ fontSize: '1.35rem' }}>
                      {selectedRma.rmaNumber}
                    </div>
                    <div className="muted">
                      {selectedRma.customerName ||
                        customerNameById(selectedRma.customerId) ||
                        t('wb.noCustomer', { ns: 'common' })}{' '}
                      ·{' '}
                      <span className={statusClass(selectedRma.rmaStatus)}>
                        {selectedRma.rmaStatus}
                      </span>
                    </div>
                    <div className="muted" style={{ fontSize: '0.85rem' }}>
                      {t('aftersales.qtyLabel', { qty: selectedRma.quantity })}
                      {selectedRma.stockMoveId
                        ? ` · ${t('aftersales.stockRef', { id: selectedRma.stockMoveId })}`
                        : ''}
                    </div>
                  </div>
                  {selectedRma.expectedRefundEur != null && (
                    <div className="inv-amount">
                      {Number(selectedRma.expectedRefundEur).toFixed(2)} EUR
                      <div className="muted" style={{ fontSize: '0.75rem' }}>
                        {t('aftersales.expectedRefund')}
                      </div>
                    </div>
                  )}
                </div>
                <div className="toolbar">
                  {selectedRma.rmaStatus === 'REQUESTED' && (
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={() => runRma('confirm')}
                      disabled={!!busy}
                    >
                      <Check size={16} /> {t('aftersales.confirmScmStock')}
                    </button>
                  )}
                  {selectedRma.rmaStatus !== 'CLOSED' &&
                    selectedRma.rmaStatus !== 'CANCELLED' && (
                      <button
                        type="button"
                        className="btn"
                        onClick={() => runRma('close')}
                        disabled={!!busy}
                      >
                        {t('actions.close', { ns: 'common' })}
                      </button>
                    )}
                  {selectedRma.rmaStatus === 'REQUESTED' && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      onClick={() => runRma('cancel')}
                      disabled={!!busy}
                    >
                      <X size={16} /> {t('actions.cancel', { ns: 'common' })}
                    </button>
                  )}
                  <a
                    className="btn"
                    href={aftersalesApi.rmas.pdfUrl(selectedRma.id)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <FileText size={16} /> Download RMA PDF
                  </a>
                </div>
                <ContentRail
                  package="service"
                  resource="Rma"
                  id={selectedRma.id}
                  onChanged={async () =>
                    setRmaContentMap(await fetchContentMap('service', 'Rma', rmas))
                  }
                />
              </>
            )}
          </section>
        </div>
      )}
    </div>
  )
}
