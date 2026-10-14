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
  Briefcase,
  RefreshCw,
  Plus,
  Check,
  Play,
  Calendar,
  User,
  Users,
  Package,
  FileText,
} from 'lucide-react'
import { deliveryApi, catalogueApi, salesApi } from '../api/client'
import DocumentLinesEditor, {
  emptyDocLine,
  linesToApiPayload,
} from '../components/DocumentLinesEditor'
import MasterEntityFields from '../forms/MasterEntityFields'
import { getMasterFormSpec } from '../forms/masterFormSpecs'

const serviceMasterSpec = getMasterFormSpec('delivery', 'Service')
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
import ResourceSelect from '../components/ResourceSelect'

const TAB_DEFS = [
  { id: 'customers', labelKey: 'delivery.tabs.customers', icon: Users },
  { id: 'services', labelKey: 'delivery.tabs.services', icon: Package },
  { id: 'resources', labelKey: 'delivery.tabs.resources', icon: User },
  { id: 'orders', labelKey: 'delivery.tabs.orders', icon: Briefcase },
]

/**
 * Delivery desk (D-S0–D-S3): catalogue → resource → fulfill. No Finance invoice (D3).
 */
export default function DeliveryDesk() {
  const { t } = useTranslation(['desks', 'common', 'content', 'sales'])
  const { viewModal, ViewButton } = useRecordView()
  const TABS = useMemo(
    () => TAB_DEFS.map((d) => ({ ...d, label: t(d.labelKey) })),
    [t],
  )
  const [tab, setTab] = useState('orders')
  const [customers, setCustomers] = useState([])
  const [services, setServices] = useState([])
  const [resources, setResources] = useState([])
  const [orders, setOrders] = useState([])
  const [selected, setSelected] = useState(null)
  const [orderContentMap, setOrderContentMap] = useState({})
  const [customerContentMap, setCustomerContentMap] = useState({})
  const [pendingOrderContent, setPendingOrderContent] = useState([])
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)

  const [customerForm, setCustomerForm] = useState({
    name: '',
    email: '',
    contactName: '',
    customerKind: 'ORGANIZATION',
  })
  const [svcForm, setSvcForm] = useState(() => serviceMasterSpec.empty())
  const onMasterField =
    (setter) =>
    (e) => {
      const { name, value, type, checked } = e.target
      setter((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
    }
  const [resForm, setResForm] = useState({
    name: '',
    resourceKind: 'HUMAN',
    capacityHours: '40',
    email: '',
  })
  const [orderForm, setOrderForm] = useState({
    resourceId: '',
    customerId: '',
  })
  const [orderLines, setOrderLines] = useState(() => [
    emptyDocLine({ name: '', quantity: '1', unitPriceEur: '100' }),
  ])
  const [salesOrderId, setSalesOrderId] = useState('')

  const [custPage, setCustPage] = useState({ max: 25, offset: 0, total: 0 })
  const [svcPage, setSvcPage] = useState({ max: 25, offset: 0, total: 0 })
  const [resPage, setResPage] = useState({ max: 25, offset: 0, total: 0 })
  const [orderPage, setOrderPage] = useState({ max: 25, offset: 0, total: 0 })
  /** Dropdown options (not paged) — max 100 until typeahead. */
  const [customerOptions, setCustomerOptions] = useState([])
  const [resourceOptions, setResourceOptions] = useState([])

  const loadAll = useCallback(async () => {
    const [c, cPick, s, r, rPick, o] = await Promise.all([
      salesApi.customers.list({ max: custPage.max, offset: custPage.offset }),
      salesApi.customers.list({ max: 100, offset: 0 }),
      catalogueApi.list({
        nature: 'SERVICE',
        activeOnly: true,
        max: svcPage.max,
        offset: svcPage.offset,
      }),
      deliveryApi.resources.list({ max: resPage.max, offset: resPage.offset }),
      // Assignee pickers: active employed humans from HR (not arbitrary delivery resources)
      deliveryApi.resources.list({ max: 200, offset: 0, assignees: true }),
      deliveryApi.orders.list({ max: orderPage.max, offset: orderPage.offset }),
    ])
    const cust = c.items || []
    const os = o.items || []
    const svcs = s.items || []
    const res = r.items || []
    setCustomers(cust)
    setCustomerOptions(cPick.items || [])
    setServices(svcs)
    setResources(res)
    setResourceOptions(rPick.items || [])
    setOrders(os)
    setCustPage((pg) => ({
      ...pg,
      max: c.max ?? pg.max,
      offset: c.offset ?? pg.offset,
      total: c.total ?? cust.length,
    }))
    setSvcPage((pg) => ({
      ...pg,
      max: s.max ?? pg.max,
      offset: s.offset ?? pg.offset,
      total: s.total ?? svcs.length,
    }))
    setResPage((pg) => ({
      ...pg,
      max: r.max ?? pg.max,
      offset: r.offset ?? pg.offset,
      total: r.total ?? res.length,
    }))
    setOrderPage((pg) => ({
      ...pg,
      max: o.max ?? pg.max,
      offset: o.offset ?? pg.offset,
      total: o.total ?? os.length,
    }))
    const [oMap, cMap] = await Promise.all([
      fetchContentMap('delivery', 'ServiceOrder', os),
      fetchContentMap('sales', 'Customer', cust),
    ])
    setOrderContentMap(oMap)
    setCustomerContentMap(cMap)
  }, [
    custPage.max,
    custPage.offset,
    svcPage.max,
    svcPage.offset,
    resPage.max,
    resPage.offset,
    orderPage.max,
    orderPage.offset,
  ])

  const selectOrder = useCallback(async (id) => {
    if (!id) {
      setSelected(null)
      return
    }
    setSelected(await deliveryApi.orders.get(id))
  }, [])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      await loadAll()
      if (selected?.id) await selectOrder(selected.id)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [loadAll, selectOrder, selected?.id])

  useEffect(() => {
    setLoading(true)
    loadAll()
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [loadAll])

  const statusClass = (s) => {
    if (s === 'COMPLETED') return 'badge badge-ok'
    if (s === 'CANCELLED') return 'badge badge-muted'
    if (s === 'IN_PROGRESS' || s === 'SCHEDULED') return 'badge badge-warn'
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
      setInfo(
        t('toast.namedCreated', {
          ns: 'common',
          what: t('labels.customer', { ns: 'common' }),
          name: c.name,
        }),
      )
      setCustomerForm({ name: '', email: '', contactName: '', customerKind: 'ORGANIZATION' })
      setCustPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
      setOrderForm((f) => ({ ...f, customerId: String(c.id) }))
      setTab('orders')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateService = async (e) => {
    e.preventDefault()
    setBusy('svc')
    setError(null)
    try {
      const raw = serviceMasterSpec.toBody(svcForm)
      const s = await catalogueApi.create({
        ...raw,
        nature: 'SERVICE',
        listPrice: raw.listPriceEur ?? raw.listPrice,
        canBeSold: true,
        canBeBought: false,
      })
      setInfo(
        t('toast.namedCreated', {
          ns: 'common',
          what: t('labels.service', { ns: 'common' }),
          name: s.name,
        }),
      )
      setSvcForm(serviceMasterSpec.empty())
      setSvcPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateResource = async (e) => {
    e.preventDefault()
    setBusy('res')
    setError(null)
    try {
      const r = await deliveryApi.resources.create(resForm)
      setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.resource', { ns: 'common' }), name: r.name }))
      setResForm({ name: '', resourceKind: 'HUMAN', capacityHours: '40', email: '' })
      setResPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateOrder = async (e) => {
    e.preventDefault()
    setBusy('ord')
    setError(null)
    try {
      const lines = linesToApiPayload(orderLines, { includeServiceRefs: true })
      if (!lines.length) {
        setError(t('docLines.needOne', { ns: 'common', defaultValue: 'Add at least one line.' }))
        setBusy(null)
        return
      }
      const cust =
        customerOptions.find((c) => String(c.id) === String(orderForm.customerId)) ||
        customers.find((c) => String(c.id) === String(orderForm.customerId))
      const body = {
        resourceId: orderForm.resourceId || undefined,
        customerId: orderForm.customerId || undefined,
        customerName: cust?.name || undefined,
        lines,
      }
      const o = await deliveryApi.orders.create(body)
      if (pendingOrderContent.length) {
        await attachPendingContent('delivery', 'ServiceOrder', o.id, pendingOrderContent)
        setPendingOrderContent([])
      }
      setOrderLines([emptyDocLine({ name: '', quantity: '1', unitPriceEur: '100' })])
      setOrderForm({ resourceId: '', customerId: '' })
      setInfo(t('toast.numberAmount', { ns: 'common', what: t('labels.order', { ns: 'common' }), number: o.orderNumber, amount: Number(o.amountEur).toFixed(2) }))
      setOrderPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
      await selectOrder(o.id)
      setTab('orders')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onFromSales = async (e) => {
    e.preventDefault()
    if (!salesOrderId) return
    setBusy('fromSo')
    setError(null)
    try {
      const r = await deliveryApi.orders.fromSales(salesOrderId)
      const items = r.items || []
      setInfo(
        `Created ${items.length} delivery order(s) from sales #${salesOrderId} (service lines only)`,
      )
      setOrderPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
      if (items[0]?.id) await selectOrder(items[0].id)
      setTab('orders')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runOrder = async (action) => {
    if (!selected?.id) return
    setBusy(action)
    setError(null)
    setInfo(null)
    try {
      let result
      if (action === 'schedule') {
        result = await deliveryApi.orders.schedule(selected.id, {
          resourceId: selected.resourceId || undefined,
        })
        setSelected(result)
        setInfo(t('toast.scheduled', { ns: 'common', number: result.orderNumber }))
      } else if (action === 'start') {
        result = await deliveryApi.orders.start(selected.id)
        setSelected(result)
        setInfo(t('toast.started', { ns: 'common', name: result.orderNumber }))
      } else if (action === 'complete') {
        result = await deliveryApi.orders.complete(selected.id)
        setSelected(result.order)
        setInfo(
          `Completed ${result.order?.orderNumber} · no Finance invoice (Sales owns billing)`,
        )
      } else if (action === 'cancel') {
        result = await deliveryApi.orders.cancel(selected.id)
        setSelected(result)
        setInfo(t('toast.cancelled', { ns: 'common', number: result.orderNumber }))
      }
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onAssign = async (resourceId) => {
    if (!selected?.id) return
    setBusy('assign')
    setError(null)
    try {
      const o = await deliveryApi.orders.assign(selected.id, resourceId || null)
      setSelected(o)
      setInfo(resourceId ? t('toast.assigned', { ns: 'common' }) : t('delivery.unassigned'))
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="page delivery-desk-page">
      <header className="page-header" style={{ marginBottom: '1.25rem' }}>
        <h1 className="page-title">
          <Briefcase size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('delivery.title')}
        </h1>
        <p className="page-sub">{t('delivery.subtitle')}</p>
      </header>

      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      {viewModal}

      <div className="toolbar">
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <ProcessHowItWorks processCode="DEL_SERVICE_ORDER" />
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
        <Link className="btn btn-secondary" to="/sales">
          {t('delivery.salesDesk')}
        </Link>
        <Link className="btn btn-secondary" to="/scm/buyers">
          {t('delivery.scmDesk')}
        </Link>
      </div>

      {tab === 'customers' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('customer.new', { ns: 'sales' })}
            </h2>
            <p className="muted" style={{ marginTop: 0, fontSize: '0.88rem' }}>
              {t('delivery.customersHint', {
                defaultValue:
                  'Same customer master as Sales — create here or pick when starting a service order.',
              })}
            </p>
            <form className="form-grid" onSubmit={onCreateCustomer}>
              <label>
                {t('customer.name', { ns: 'sales' })} *
                <input
                  required
                  value={customerForm.name}
                  onChange={(e) => setCustomerForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder={t('ph.acme', { ns: 'common' })}
                />
              </label>
              <div className="form-row-2">
                <label>
                  {t('customer.kind', { ns: 'sales' })}
                  <select
                    value={customerForm.customerKind}
                    onChange={(e) =>
                      setCustomerForm((f) => ({ ...f, customerKind: e.target.value }))
                    }
                  >
                    <option value="ORGANIZATION">
                      {t('customer.organization', { ns: 'sales' })}
                    </option>
                    <option value="PERSON">{t('customer.person', { ns: 'sales' })}</option>
                  </select>
                </label>
                <label>
                  {t('customer.contact', { ns: 'sales' })}
                  <input
                    value={customerForm.contactName}
                    onChange={(e) =>
                      setCustomerForm((f) => ({ ...f, contactName: e.target.value }))
                    }
                  />
                </label>
              </div>
              <label>
                {t('customer.email', { ns: 'sales' })}
                <input
                  type="email"
                  value={customerForm.email}
                  onChange={(e) => setCustomerForm((f) => ({ ...f, email: e.target.value }))}
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {busy === 'cust' ? '…' : t('customer.create', { ns: 'sales' })}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('customer.list', { ns: 'sales' })} ({custPage.total})
            </h2>
            {loading && <p className="muted">{t('actions.loading', { ns: 'common' })}</p>}
            <ResponsiveDataList
              items={customers}
              empty={t('customer.empty', { ns: 'sales' })}
              loading={loading}
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
                  render: (c) => c.customerKind,
                },
                {
                  id: 'ccy',
                  priority: 'low',
                  render: (c) => c.currencyCode,
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
                      <th>{t('customer.table.name', { ns: 'sales' })}</th>
                      <th className="col-priority-low">
                        {t('customer.table.kind', { ns: 'sales' })}
                      </th>
                      <th>{t('customer.table.email', { ns: 'sales' })}</th>
                      <th className="col-priority-low">
                        {t('customer.table.content', { ns: 'sales' })}
                      </th>
                      <th className="col-priority-low">{t('wb.ccy', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {customers.length === 0 && (
                      <tr>
                        <td colSpan={6} className="muted">
                          {t('customer.empty', { ns: 'sales' })}
                        </td>
                      </tr>
                    )}
                    {customers.map((c) => (
                      <tr key={c.id}>
                        <td>{c.name}</td>
                        <td className="col-priority-low">{c.customerKind}</td>
                        <td className="muted">{c.email || '—'}</td>
                        <td className="col-priority-low">
                          <ContentChannelIcons summary={customerContentMap[c.id]} />
                        </td>
                        <td className="mono col-priority-low">{c.currencyCode}</td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="sales" resource="Customer" record={c} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={custPage.total}
              max={custPage.max}
              offset={custPage.offset}
              disabled={loading}
              onOffset={(o) => setCustPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setCustPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'services' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('delivery.newService')}
            </h2>
            <form className="form-grid" onSubmit={onCreateService}>
              <MasterEntityFields
                spec={serviceMasterSpec}
                form={svcForm}
                onChange={onMasterField(setSvcForm)}
                disabled={!!busy}
                showDescription={false}
              />
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('delivery.createService')}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Services ({svcPage.total})
            </h2>
            <ResponsiveDataList
              items={services}
              empty="—"
              fields={[
                { id: 'name', primary: true, render: (s) => s.name },
                {
                  id: 'unit',
                  secondary: true,
                  render: (s) => s.unit || '—',
                },
                {
                  id: 'eur',
                  meta: true,
                  render: (s) =>
                    (s.listPrice ?? s.listPriceEur) != null
                      ? `${Number(s.listPrice ?? s.listPriceEur).toFixed(2)} EUR`
                      : '—',
                },
              ]}
              actions={(s) => (
                <ViewButton pkg="catalogue" resource="CatalogueItem" record={s} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.unit', { ns: 'common' })}</th>
                      <th>EUR</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {services.map((s) => (
                      <tr key={s.id}>
                        <td>{s.name}</td>
                        <td className="col-priority-low">{s.unit}</td>
                        <td>
                          {(s.listPrice ?? s.listPriceEur) != null
                            ? Number(s.listPrice ?? s.listPriceEur).toFixed(2)
                            : '—'}
                        </td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="catalogue" resource="CatalogueItem" record={s} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={svcPage.total}
              max={svcPage.max}
              offset={svcPage.offset}
              disabled={loading}
              onOffset={(o) => setSvcPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setSvcPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'resources' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('delivery.newResource')}
            </h2>
            <form className="form-grid" onSubmit={onCreateResource}>
              <label>
                Name *
                <input
                  required
                  value={resForm.name}
                  onChange={(e) => setResForm((f) => ({ ...f, name: e.target.value }))}
                />
              </label>
              <div className="form-row-2">
                <label>
                  Kind
                  <select
                    value={resForm.resourceKind}
                    onChange={(e) => setResForm((f) => ({ ...f, resourceKind: e.target.value }))}
                  >
                    {['HUMAN', 'AGENT', 'OTHER'].map((k) => (
                      <option key={k} value={k}>
                        {k}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Capacity hours
                  <input
                    value={resForm.capacityHours}
                    onChange={(e) => setResForm((f) => ({ ...f, capacityHours: e.target.value }))}
                  />
                </label>
              </div>
              <label>
                Email
                <input
                  value={resForm.email}
                  onChange={(e) => setResForm((f) => ({ ...f, email: e.target.value }))}
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('delivery.createResource')}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Resources ({resPage.total})
            </h2>
            <ResponsiveDataList
              items={resources}
              empty="—"
              fields={[
                { id: 'name', primary: true, render: (r) => r.name },
                {
                  id: 'kind',
                  secondary: true,
                  render: (r) => r.resourceKind || '—',
                },
                {
                  id: 'hours',
                  meta: true,
                  render: (r) =>
                    r.capacityHours != null ? `${r.capacityHours}h` : '—',
                },
              ]}
              actions={(r) => (
                <ViewButton pkg="delivery" resource="Resource" record={r} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th>{t('wb.kind', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.hours', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {resources.map((r) => (
                      <tr key={r.id}>
                        <td>{r.name}</td>
                        <td>{r.resourceKind}</td>
                        <td className="col-priority-low">{r.capacityHours ?? '—'}</td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="delivery" resource="Resource" record={r} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={resPage.total}
              max={resPage.max}
              offset={resPage.offset}
              disabled={loading}
              onOffset={(o) => setResPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setResPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'orders' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('delivery.newService')} order
            </h2>
            <form className="form-grid" onSubmit={onCreateOrder}>
              <label>
                {t('customer.name', { ns: 'sales', defaultValue: 'Customer' })}
                <CustomerSelect
                  value={orderForm.customerId}
                  onChange={(id) => setOrderForm((f) => ({ ...f, customerId: id || '' }))}
                  disabled={!!busy}
                  placeholder={t('delivery.selectCustomer', {
                    defaultValue: '— search customer —',
                  })}
                />
              </label>
              <p className="muted" style={{ margin: 0, fontSize: '0.85rem' }}>
                {t('delivery.noCustomersHint', {
                  defaultValue: 'Need a new customer?',
                })}{' '}
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  onClick={() => setTab('customers')}
                >
                  {t('customer.create', { ns: 'sales' })}
                </button>
              </p>
              <label>
                Assignee
                <ResourceSelect
                  value={orderForm.resourceId}
                  onChange={(id) => setOrderForm((f) => ({ ...f, resourceId: id || '' }))}
                  disabled={!!busy}
                />
              </label>
              <DocumentLinesEditor
                showTax={false}
                forChannel="sale"
                nature="SERVICE"
                lines={orderLines}
                onChange={setOrderLines}
                disabled={!!busy}
              />
              <PendingContentComposer
                package="delivery"
                resource="ServiceOrder"
                pending={pendingOrderContent}
                onChange={setPendingOrderContent}
              />
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('delivery.createOrder')}
              </button>
            </form>

            <h2 className="section-label">{t('delivery.fromSalesOrder')}</h2>
            <form className="form-grid" onSubmit={onFromSales}>
              <label>
                Sales order id
                <input
                  value={salesOrderId}
                  onChange={(e) => setSalesOrderId(e.target.value)}
                  placeholder={t('ph.serviceLines', { ns: 'common' })}
                />
              </label>
              <button type="submit" className="btn" disabled={!!busy || !salesOrderId}>
                Create from sales
              </button>
            </form>

            <h2 className="section-label">Orders ({orderPage.total})</h2>
            <ResponsiveDataList
              items={orders}
              empty="—"
              selectedKey={selected?.id}
              onSelect={(o) => selectOrder(o.id)}
              fields={[
                {
                  id: 'number',
                  primary: true,
                  render: (o) => (
                    <>
                      <span className="mono">{o.orderNumber}</span>{' '}
                      <span className={statusClass(o.fulfillStatus)}>
                        {o.fulfillStatus}
                      </span>
                    </>
                  ),
                },
                {
                  id: 'customer',
                  secondary: true,
                  render: (o) => o.customerName || '—',
                },
                {
                  id: 'eur',
                  meta: true,
                  render: (o) =>
                    o.amountEur != null
                      ? `${Number(o.amountEur).toFixed(2)} EUR`
                      : '—',
                },
              ]}
              actions={(o) => (
                <ViewButton pkg="delivery" resource="ServiceOrder" record={o} />
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
                    {orders.map((o) => (
                      <tr
                        key={o.id}
                        className={selected?.id === o.id ? 'row-selected' : 'row-click'}
                        onClick={() => selectOrder(o.id)}
                      >
                        <td className="mono">{o.orderNumber}</td>
                        <td>{o.customerName || '—'}</td>
                        <td>
                          {o.amountEur != null ? Number(o.amountEur).toFixed(2) : '—'}
                        </td>
                        <td>
                          <span className={statusClass(o.fulfillStatus)}>
                            {o.fulfillStatus}
                          </span>
                        </td>
                        <td
                          style={{ whiteSpace: 'nowrap' }}
                          onClick={(e) => e.stopPropagation()}
                        >
                          <ViewButton pkg="delivery" resource="ServiceOrder" record={o} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={orderPage.total}
              max={orderPage.max}
              offset={orderPage.offset}
              disabled={loading}
              onOffset={(o) => setOrderPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setOrderPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Order detail
            </h2>
            {!selected && <p className="muted">{t('delivery.selectOrder')}</p>}
            {selected && (
              <>
                <div className="inv-detail-hero">
                  <div>
                    <div className="about-display" style={{ fontSize: '1.35rem' }}>
                      {selected.orderNumber}
                    </div>
                    <div className="muted">
                      {selected.customerName || t('wb.noCustomer', { ns: 'common' })} ·{' '}
                      <span className={statusClass(selected.fulfillStatus)}>
                        {selected.fulfillStatus}
                      </span>
                    </div>
                    {selected.sourceSalesOrderId && (
                      <div className="muted" style={{ fontSize: '0.85rem' }}>
                        From sales order #{selected.sourceSalesOrderId}
                      </div>
                    )}
                    {selected.resourceName && (
                      <div className="muted" style={{ fontSize: '0.85rem' }}>
                        Assignee: {selected.resourceName}
                      </div>
                    )}
                  </div>
                  <div className="inv-amount">
                    {Number(selected.amountEur || 0).toFixed(2)} EUR
                    {selected.amountSats != null && (
                      <div className="muted mono" style={{ fontSize: '0.8rem' }}>
                        {selected.amountSats} sats
                      </div>
                    )}
                  </div>
                </div>

                <label>
                  {t('delivery.assignEmployee', { defaultValue: 'Assign employee' })}
                  <select
                    value={selected.resourceId || ''}
                    onChange={(e) => onAssign(e.target.value)}
                    disabled={!!busy || selected.fulfillStatus === 'COMPLETED'}
                  >
                    <option value="">
                      {t('delivery.noAssignee', { defaultValue: '— none —' })}
                    </option>
                    {resourceOptions.length === 0 && (
                      <option value="" disabled>
                        {t('delivery.emptyAssignees', {
                          defaultValue: 'No active employees — add people in HR first.',
                        })}
                      </option>
                    )}
                    {resourceOptions.map((r) => (
                      <option key={r.id} value={r.id}>
                        {r.name}
                        {r.email ? ` · ${r.email}` : ''}
                      </option>
                    ))}
                  </select>
                </label>

                <div className="toolbar">
                  {['DRAFT'].includes(selected.fulfillStatus) && (
                    <button
                      type="button"
                      className="btn"
                      onClick={() => runOrder('schedule')}
                      disabled={!!busy}
                    >
                      <Calendar size={16} /> Schedule
                    </button>
                  )}
                  {['DRAFT', 'SCHEDULED'].includes(selected.fulfillStatus) && (
                    <button
                      type="button"
                      className="btn"
                      onClick={() => runOrder('start')}
                      disabled={!!busy}
                    >
                      <Play size={16} /> Start
                    </button>
                  )}
                  {selected.fulfillStatus !== 'COMPLETED' &&
                    selected.fulfillStatus !== 'CANCELLED' && (
                      <button
                        type="button"
                        className="btn btn-primary"
                        onClick={() => runOrder('complete')}
                        disabled={!!busy}
                      >
                        <Check size={16} /> Complete
                      </button>
                    )}
                  <a
                    className="btn"
                    href={deliveryApi.orders.pdfUrl(selected.id)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <FileText size={16} /> Download delivery PDF
                  </a>
                </div>

                <h3 className="section-label">{t('wb.lines', { ns: 'common' })}</h3>
                <div className="table-wrap">
                  <table className="data">
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>{t('wb.name', { ns: 'common' })}</th>
                        <th>{t('wb.qty', { ns: 'common' })}</th>
                        <th>{t('wb.total', { ns: 'common' })}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(selected.lines || []).map((l) => (
                        <tr key={l.id || l.lineNo}>
                          <td>{l.lineNo}</td>
                          <td>{l.name}</td>
                          <td>{l.quantity}</td>
                          <td>{Number(l.lineTotalEur).toFixed(2)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <ContentRail
                  package="delivery"
                  resource="ServiceOrder"
                  id={selected.id}
                  onChanged={async () =>
                    setOrderContentMap(await fetchContentMap('delivery', 'ServiceOrder', orders))
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
