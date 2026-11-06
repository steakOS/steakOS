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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  Briefcase,
  RefreshCw,
  Plus,
  Check,
  Play,
  Calendar,
  Eye,
  Pencil,
  Trash2,
  Users,
  Package,
  FileText,
  User,
} from 'lucide-react'
import { deliveryApi, catalogueApi, salesApi, domainsApi, meApi } from '../api/client'
import DocumentLinesEditor, {
  emptyDocLine,
  linesToApiPayload,
} from '../components/DocumentLinesEditor'
import MasterEntityFields from '../forms/MasterEntityFields'
import { getMasterFormSpec } from '../forms/masterFormSpecs'

const serviceMasterSpec = getMasterFormSpec('delivery', 'Service')
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import ResponsiveDataList from '../components/ResponsiveDataList'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'
import CustomerSelect from '../components/CustomerSelect'
import ResourceSelect from '../components/ResourceSelect'
import DeskNewFab from '../components/DeskNewFab'
import DeskShell from '../components/desk/DeskShell'
import DeskProcessSlot from '../components/desk/DeskProcessSlot'
import ConfirmDialog from '../components/ConfirmDialog'
import RowActionSlots from '../components/RowActionSlots'
import useDeskSelection from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'

const TAB_DEFS = [
  { id: 'customers', labelKey: 'delivery.tabs.customers', icon: Users },
  { id: 'services', labelKey: 'delivery.tabs.services', icon: Package },
  { id: 'resources', labelKey: 'delivery.tabs.resources', icon: User },
  { id: 'orders', labelKey: 'delivery.tabs.orders', icon: Briefcase },
]

/**
 * Delivery desk (D-S0–D-S3) — Buyers UI 2.0 (ADR-050): single-column + modal registers.
 */
export default function DeliveryDesk() {
  const { t } = useTranslation(['desks', 'common', 'content', 'sales'])
  const loadAllRef = useRef(async () => {})
  const deepLinkOpened = useRef(null)
  const getBelowFieldsRef = useRef(null)
  const getFooterExtraRef = useRef(null)
  const runOrderRef = useRef(async () => {})
  const selectedRef = useRef(null)
  const ordersRef = useRef([])
  const resourceOptionsRef = useRef([])
  const busyRef = useRef(null)
  const tabs = useMemo(
    () => TAB_DEFS.map((d) => ({ id: d.id, label: t(d.labelKey), icon: d.icon })),
    [t],
  )
  const [tab, setTab] = useState(() => {
    if (typeof window === 'undefined') return 'orders'
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    if (urlTab) return urlTab
    try {
      const raw = window.localStorage.getItem('steakos.desk.default.delivery')
      const parsed = raw ? JSON.parse(raw) : null
      if (parsed?.tab) return String(parsed.tab)
    } catch {
      /* ignore */
    }
    return 'orders'
  })
  const [selectedId, setSelectedId] = useState(() => {
    if (typeof window === 'undefined') return null
    const id = new URLSearchParams(window.location.search).get('id')
    return id || null
  })
  const [newExpanded, setNewExpanded] = useState(false)
  const [companyId, setCompanyId] = useState('default')
  const [discardOpen, setDiscardOpen] = useState(false)
  const [pendingNav, setPendingNav] = useState(null)

  const { loadMemory, saveMemory } = useDeskMemory({ deskKey: 'delivery', companyId })

  useDeskSelection({
    deskKey: 'delivery',
    defaultTab: 'orders',
    tab,
    setTab,
    selectedId,
    setSelectedId,
  })

  useEffect(() => {
    saveMemory({ tab })
  }, [tab, saveMemory])

  useEffect(() => {
    if (companyId === 'default') return
    const mem = loadMemory()
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    if (!urlTab && mem?.tab && mem.tab !== tab) {
      setTab(mem.tab)
    }
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
  const [orderSort, setOrderSort] = useState('id')
  const [orderOrder, setOrderOrder] = useState('desc')
  const [resSort, setResSort] = useState('name')
  const [resOrder, setResOrder] = useState('asc')
  /** Dropdown options (not paged) — max 100 until typeahead. */
  const [customerOptions, setCustomerOptions] = useState([])
  const [resourceOptions, setResourceOptions] = useState([])

  const ORDER_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'orderNumber', label: 'Number' },
      { value: 'customerName', label: 'Customer' },
      { value: 'amountEur', label: 'EUR' },
      { value: 'fulfillStatus', label: 'Status' },
    ],
    [],
  )
  const RESOURCE_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'resourceKind', label: 'Kind' },
      { value: 'status', label: 'Status' },
    ],
    [],
  )

  const onOrderSortChange = useCallback(({ sort: s, order: o }) => {
    setOrderSort(s || 'id')
    setOrderOrder(o === 'asc' ? 'asc' : 'desc')
    setOrderPage((pg) => ({ ...pg, offset: 0 }))
  }, [])

  const onResSortChange = useCallback(({ sort: s, order: o }) => {
    setResSort(s || 'name')
    setResOrder(o === 'asc' ? 'asc' : 'desc')
    setResPage((pg) => ({ ...pg, offset: 0 }))
  }, [])

  const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'

  const {
    viewModal,
    openView,
    openEdit,
    updateViewingRecord,
  } = useRecordView({
    modalEdit: true,
    canModalEdit: ({ resource, record }) => {
      if (resource === 'Customer' || resource === 'CatalogueItem' || resource === 'Resource') return true
      if (resource === 'ServiceOrder') {
        return (record?.fulfillStatus || record?.status) === 'DRAFT'
      }
      return false
    },
    onSaved: (updated) => {
      loadAllRef.current().catch(() => {})
      if (updated?.id != null && (updated.fulfillStatus != null || Array.isArray(updated.lines))) {
        setSelected((prev) =>
          prev && String(prev.id) === String(updated.id) ? { ...prev, ...updated } : prev,
        )
      }
    },
    getBelowFields: (ctx) => getBelowFieldsRef.current?.(ctx) ?? null,
    getFooterExtra: (ctx) => getFooterExtraRef.current?.(ctx) ?? null,
  })

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
      deliveryApi.resources.list({
        max: resPage.max,
        offset: resPage.offset,
        sort: resSort,
        order: resOrder,
      }),
      // Assignee pickers: active employed humans from HR (not arbitrary delivery resources)
      deliveryApi.resources.list({ max: 200, offset: 0, assignees: true }),
      deliveryApi.orders.list({
        max: orderPage.max,
        offset: orderPage.offset,
        sort: orderSort,
        order: orderOrder,
      }),
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
    resSort,
    resOrder,
    orderPage.max,
    orderPage.offset,
    orderSort,
    orderOrder,
  ])

  loadAllRef.current = loadAll
  selectedRef.current = selected
  ordersRef.current = orders
  resourceOptionsRef.current = resourceOptions
  busyRef.current = busy

  const selectOrder = useCallback(async (id) => {
    if (!id) {
      setSelected(null)
      return
    }
    setSelected(await deliveryApi.orders.get(id))
  }, [])

  const openOrderView = useCallback(
    async (order) => {
      if (!order?.id && order?.id !== 0) return
      const full = await deliveryApi.orders.get(order.id)
      setSelected(full)
      openView('delivery', 'ServiceOrder', full)
    },
    [openView],
  )

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      if (!selectedId) return
      if (deepLinkOpened.current === String(selectedId)) return
      try {
        const resource =
          tab === 'customers'
            ? ['sales', 'Customer']
            : tab === 'services'
              ? ['catalogue', 'CatalogueItem']
              : tab === 'resources'
                ? ['delivery', 'Resource']
                : ['delivery', 'ServiceOrder']
        const [pkg, res] = resource
        let record = null
        if (res === 'ServiceOrder') {
          record = await deliveryApi.orders.get(selectedId)
          if (!cancelled && record) setSelected(record)
        } else if (res === 'Customer') {
          record = await domainsApi.get(pkg, res, selectedId)
        } else if (res === 'CatalogueItem') {
          record = await domainsApi.get(pkg, res, selectedId)
        } else {
          record = await deliveryApi.resources.get(selectedId)
        }
        if (!cancelled && record) {
          openView(pkg, res, record)
          deepLinkOpened.current = String(selectedId)
        }
      } catch (e) {
        if (!cancelled) setError(e.message || String(e))
      }
    }
    run()
    return () => {
      cancelled = true
    }
  }, [selectedId, tab, openView])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      await loadAll()
      if (tab === 'orders' && selectedId) await selectOrder(selectedId)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [loadAll, selectOrder, selectedId, tab])

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
      setNewExpanded(false)
      setCustPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
      setOrderForm((f) => ({ ...f, customerId: String(c.id) }))
      setSelectedId(null)
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
      setNewExpanded(false)
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
      setNewExpanded(false)
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
      setPendingOrderContent([])
      setNewExpanded(false)
      setInfo(t('toast.numberAmount', { ns: 'common', what: t('labels.order', { ns: 'common' }), number: o.orderNumber, amount: Number(o.amountEur).toFixed(2) }))
      setOrderPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
      setTab('orders')
      deepLinkOpened.current = String(o.id)
      setSelectedId(String(o.id))
      await openOrderView(o)
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
        `Created ${items.length} delivery order(s) from sales order (service lines only)`,
      )
      setOrderPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
      setTab('orders')
      if (items[0]?.id) {
        deepLinkOpened.current = String(items[0].id)
        setSelectedId(String(items[0].id))
        await openOrderView(items[0])
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runOrder = async (action, orderId) => {
    const id = orderId ?? selectedRef.current?.id
    if (!id) return
    const live = selectedRef.current?.id === id ? selectedRef.current : { id }
    setBusy(action)
    setError(null)
    setInfo(null)
    try {
      let updated = null
      if (action === 'schedule') {
        updated = await deliveryApi.orders.schedule(id, {
          resourceId: live.resourceId || undefined,
        })
        setInfo(t('toast.scheduled', { ns: 'common', number: updated.orderNumber }))
      } else if (action === 'start') {
        updated = await deliveryApi.orders.start(id)
        setInfo(t('toast.started', { ns: 'common', name: updated.orderNumber }))
      } else if (action === 'complete') {
        const result = await deliveryApi.orders.complete(id)
        updated = result.order
        setInfo(
          `Completed ${result.order?.orderNumber} · no Finance invoice (Sales owns billing)`,
        )
      } else if (action === 'cancel') {
        updated = await deliveryApi.orders.cancel(id)
        setInfo(t('toast.cancelled', { ns: 'common', number: updated.orderNumber }))
      }
      if (updated) {
        setSelected(updated)
        updateViewingRecord(updated)
      }
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }
  runOrderRef.current = runOrder

  const onAssign = async (resourceId) => {
    const live = selectedRef.current
    if (!live?.id) return
    setBusy('assign')
    setError(null)
    try {
      const o = await deliveryApi.orders.assign(live.id, resourceId || null)
      setSelected(o)
      updateViewingRecord(o)
      setInfo(resourceId ? t('toast.assigned', { ns: 'common' }) : t('delivery.unassigned'))
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const canEditRecord = useCallback((resource, record) => {
    if (resource === 'Customer' || resource === 'CatalogueItem' || resource === 'Resource') return true
    if (resource === 'ServiceOrder') return (record?.fulfillStatus || record?.status) === 'DRAFT'
    return false
  }, [])

  const canDeleteRecord = useCallback((resource, record) => {
    if (resource === 'Customer' || resource === 'CatalogueItem' || resource === 'Resource') return true
    if (resource === 'ServiceOrder') return (record?.fulfillStatus || record?.status) === 'DRAFT'
    return false
  }, [])

  const onDelete = useCallback(
    async (pkg, resource, record) => {
      const label =
        record?.name ||
        record?.orderNumber ||
        t('view.untitled', { ns: 'common', defaultValue: 'this record' })
      if (!window.confirm(t('actions.confirmDelete', { ns: 'common', label }))) return
      setError(null)
      try {
        if (resource === 'ServiceOrder') {
          await deliveryApi.orders.cancel(record.id)
        } else {
          await domainsApi.remove(pkg, resource, record.id)
        }
        setInfo(
          t('toast.namedDeleted', {
            ns: 'common',
            name: label,
            defaultValue: `Deleted ${label}`,
          }),
        )
        await loadAllRef.current()
      } catch (err) {
        setError(err.message || String(err))
      }
    },
    [t],
  )

  const renderRowActions = useCallback(
    (pkg, resource, record, { onView } = {}) => {
      if (!record?.id && record?.id !== 0) return null
      const canEdit = canEditRecord(resource, record)
      const canDelete = canDeleteRecord(resource, record)
      return (
        <RowActionSlots
          view={
            <button
              type="button"
              className={ROW_ACTION_CLASS}
              title={t('view.open', { ns: 'common' })}
              aria-label={t('view.open', { ns: 'common' })}
              onClick={(e) => {
                e.stopPropagation()
                if (typeof onView === 'function') onView(record)
                else openView(pkg, resource, record)
              }}
            >
              <Eye size={14} />
            </button>
          }
          edit={
            canEdit ? (
              <button
                type="button"
                className={ROW_ACTION_CLASS}
                title={t('actions.edit', { ns: 'common' })}
                aria-label={t('actions.edit', { ns: 'common' })}
                onClick={(e) => {
                  e.stopPropagation()
                  if (resource === 'ServiceOrder') {
                    selectOrder(record.id).finally(() => openEdit(pkg, resource, record))
                  } else {
                    openEdit(pkg, resource, record)
                  }
                }}
              >
                <Pencil size={14} />
              </button>
            ) : null
          }
          delete={
            canDelete ? (
              <button
                type="button"
                className={ROW_ACTION_CLASS}
                title={t('actions.delete', { ns: 'common' })}
                aria-label={t('actions.delete', { ns: 'common' })}
                onClick={(e) => {
                  e.stopPropagation()
                  onDelete(pkg, resource, record)
                }}
              >
                <Trash2 size={14} />
              </button>
            ) : null
          }
        />
      )
    },
    [canDeleteRecord, canEditRecord, onDelete, openEdit, openView, selectOrder, t],
  )


  const DEFAULT_ORDER_LINES = () => [
    emptyDocLine({ name: '', quantity: '1', unitPriceEur: '100' }),
  ]

  const resetCreateForms = useCallback(() => {
    setCustomerForm({ name: '', email: '', contactName: '', customerKind: 'ORGANIZATION' })
    setSvcForm(serviceMasterSpec.empty())
    setResForm({ name: '', resourceKind: 'HUMAN', capacityHours: '40', email: '' })
    setOrderForm({ resourceId: '', customerId: '' })
    setOrderLines(DEFAULT_ORDER_LINES())
    setPendingOrderContent([])
    setSalesOrderId('')
  }, [])

  const createDirty = useMemo(() => {
    if (tab === 'customers') {
      return Boolean(
        (customerForm.name || '').trim() ||
          customerForm.email ||
          customerForm.contactName ||
          customerForm.customerKind !== 'ORGANIZATION',
      )
    }
    if (tab === 'services') {
      return Boolean((svcForm.name || '').trim() || (svcForm.code || '').trim())
    }
    if (tab === 'resources') {
      return Boolean(
        (resForm.name || '').trim() ||
          resForm.email ||
          resForm.resourceKind !== 'HUMAN' ||
          String(resForm.capacityHours) !== '40',
      )
    }
    if (tab === 'orders') {
      const lines = linesToApiPayload(orderLines, { includeServiceRefs: true })
      const linesTouched =
        lines.length !== 1 ||
        Boolean(orderLines[0]?.name) ||
        String(orderLines[0]?.quantity) !== '1' ||
        String(orderLines[0]?.unitPriceEur) !== '100'
      return Boolean(
        orderForm.customerId ||
          orderForm.resourceId ||
          pendingOrderContent.length ||
          salesOrderId ||
          linesTouched,
      )
    }
    return false
  }, [
    tab,
    customerForm,
    svcForm,
    resForm,
    orderForm,
    orderLines,
    pendingOrderContent,
    salesOrderId,
  ])

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
    if (newExpanded) {
      applyCollapseCreate()
    } else {
      setNewExpanded(true)
    }
  }, [newExpanded, createDirty, applyCollapseCreate])

  const keyboardIds = useMemo(() => {
    if (tab === 'orders') return orders.map((o) => o.id)
    return []
  }, [tab, orders])

  useDeskKeyboard({
    enabled: false,
    itemIds: keyboardIds,
    selectedId,
    setSelectedId,
    onToggleNew: requestToggleNew,
  })

  const requestTabChange = useCallback(
    (nextTab) => {
      if (nextTab === tab) return
      if (newExpanded && createDirty) {
        setPendingNav({ type: 'tab', tab: nextTab })
        setDiscardOpen(true)
        return
      }
      setTab(nextTab)
      setSelectedId(null)
      setNewExpanded(false)
      resetCreateForms()
    },
    [tab, newExpanded, createDirty, resetCreateForms],
  )

  const confirmDiscard = useCallback(() => {
    const nav = pendingNav
    setDiscardOpen(false)
    setPendingNav(null)
    applyCollapseCreate()
    if (nav?.type === 'tab' && nav.tab) {
      setTab(nav.tab)
      setSelectedId(null)
    }
  }, [pendingNav, applyCollapseCreate])

  const cancelDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
  }, [])

  const newLabel = useMemo(() => {
    if (tab === 'customers') return t('customer.new', { ns: 'sales' })
    if (tab === 'services') return t('delivery.newService')
    if (tab === 'resources') return t('delivery.newResource')
    if (tab === 'orders') return t('delivery.newService') + ' order'
    return t('desk.new', { ns: 'common', defaultValue: 'New' })
  }, [tab, t])

  const helpContext = useMemo(() => {
    const base = { desk: 'delivery', tab, processCode: 'DEL_SERVICE_ORDER' }
    if (tab === 'orders' && selected) {
      return {
        ...base,
        resource: 'ServiceOrder',
        objectId: selected.id,
        status: selected.fulfillStatus,
      }
    }
    return base
  }, [tab, selected])

  const processStatus = tab === 'orders' ? selected?.fulfillStatus : null

  const processSlot = (
    <DeskProcessSlot processCode="DEL_SERVICE_ORDER" status={processStatus} />
  )

  const collapsedContentRail = (pkg, resource, id, onChanged) => (
    <details className="content-rail-collapse" style={{ marginTop: '1.25rem' }}>
      <summary className="btn btn-ghost" style={{ cursor: 'pointer', listStyle: 'inside' }}>
        {t('desk.attachmentsNotes', { ns: 'common', defaultValue: 'Attachments & notes' })}
      </summary>
      <ContentRail package={pkg} resource={resource} id={id} onChanged={onChanged} />
    </details>
  )

  // —— create panels ——
  const customerCreatePanel = (
    <>
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
    </>
  )

  const serviceCreatePanel = (
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
  )

  const resourceCreatePanel = (
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
  )

  const orderCreatePanel = (
    <>
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
            onClick={() => requestTabChange('customers')}
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
          {t('delivery.fromSalesOrder', { defaultValue: 'Sales order' })}
          <input
            value={salesOrderId}
            onChange={(e) => setSalesOrderId(e.target.value)}
            placeholder={t('ph.serviceLines', { ns: 'common' })}
            inputMode="numeric"
            aria-label={t('delivery.fromSalesOrder', { defaultValue: 'Sales order' })}
          />
        </label>
        <button type="submit" className="btn" disabled={!!busy || !salesOrderId}>
          Create from sales
        </button>
      </form>
    </>
  )

  const newPanel =
    tab === 'customers'
      ? customerCreatePanel
      : tab === 'services'
        ? serviceCreatePanel
        : tab === 'resources'
          ? resourceCreatePanel
          : tab === 'orders'
            ? orderCreatePanel
            : null

  // —— lists ——
  const customersList = (
    <>
      {loading && <p className="muted">{t('actions.loading', { ns: 'common' })}</p>}
      <ResponsiveDataList
        items={customers}
        empty={t('customer.empty', { ns: 'sales' })}
        loading={loading}
        onSelect={(c) => openView('sales', 'Customer', c)}
        fields={[
          { id: 'name', primary: true, render: (c) => c.name },
          { id: 'email', secondary: true, render: (c) => c.email || '—' },
          { id: 'kind', priority: 'low', render: (c) => c.customerKind },
          { id: 'ccy', priority: 'low', render: (c) => c.currencyCode },
        ]}
        actions={(c) => renderRowActions('sales', 'Customer', c)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('customer.table.name', { ns: 'sales' })}</th>
                <th className="col-priority-low">{t('customer.table.kind', { ns: 'sales' })}</th>
                <th>{t('customer.table.email', { ns: 'sales' })}</th>
                <th className="col-priority-low">{t('customer.table.content', { ns: 'sales' })}</th>
                <th className="col-priority-low">{t('wb.ccy', { ns: 'common' })}</th>
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
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
                <tr
                  key={c.id}
                  className="row-click"
                  onClick={() => openView('sales', 'Customer', c)}
                >
                  <td>{c.name}</td>
                  <td className="col-priority-low">{c.customerKind}</td>
                  <td className="muted">{c.email || '—'}</td>
                  <td className="col-priority-low">
                    <ContentChannelIcons summary={customerContentMap[c.id]} />
                  </td>
                  <td className="mono col-priority-low">{c.currencyCode}</td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('sales', 'Customer', c)}
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
    </>
  )

  const servicesList = (
    <>
      <ResponsiveDataList
        items={services}
        empty="—"
        onSelect={(s) => openView('catalogue', 'CatalogueItem', s)}
        fields={[
          { id: 'name', primary: true, render: (s) => s.name },
          { id: 'unit', secondary: true, render: (s) => s.unit || '—' },
          {
            id: 'eur',
            meta: true,
            render: (s) =>
              (s.listPrice ?? s.listPriceEur) != null
                ? `${Number(s.listPrice ?? s.listPriceEur).toFixed(2)} EUR`
                : '—',
          },
        ]}
        actions={(s) => renderRowActions('catalogue', 'CatalogueItem', s)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('wb.name', { ns: 'common' })}</th>
                <th>{t('wb.unit', { ns: 'common' })}</th>
                <th>EUR</th>
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {services.map((s) => (
                <tr
                  key={s.id}
                  className="row-click"
                  onClick={() => openView('catalogue', 'CatalogueItem', s)}
                >
                  <td>{s.name}</td>
                  <td>{s.unit}</td>
                  <td>
                    {(s.listPrice ?? s.listPriceEur) != null
                      ? Number(s.listPrice ?? s.listPriceEur).toFixed(2)
                      : '—'}
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('catalogue', 'CatalogueItem', s)}
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
    </>
  )

  const resourcesList = (
    <>
      <ResponsiveDataList
        items={resources}
        empty="—"
        onSelect={(r) => openView('delivery', 'Resource', r)}
        sortFields={RESOURCE_SORT_MENU}
        sort={resSort}
        order={resOrder}
        onSortChange={onResSortChange}
        fields={[
          { id: 'name', primary: true, render: (r) => r.name },
          { id: 'kind', secondary: true, render: (r) => r.resourceKind || '—' },
          {
            id: 'hours',
            meta: true,
            render: (r) => (r.capacityHours != null ? `${r.capacityHours}h` : '—'),
          },
        ]}
        actions={(r) => renderRowActions('delivery', 'Resource', r)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={resSort}
                  order={resOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onResSortChange}
                />
                <ListSortHeader
                  field="resourceKind"
                  label={t('wb.kind', { ns: 'common' })}
                  sort={resSort}
                  order={resOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onResSortChange}
                />
                <th className="col-priority-low">{t('wb.hours', { ns: 'common' })}</th>
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {resources.map((r) => (
                <tr
                  key={r.id}
                  className="row-click"
                  onClick={() => openView('delivery', 'Resource', r)}
                >
                  <td>{r.name}</td>
                  <td>{r.resourceKind}</td>
                  <td className="col-priority-low">{r.capacityHours ?? '—'}</td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('delivery', 'Resource', r)}
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
    </>
  )

  const ordersList = (
    <>
      <ResponsiveDataList
        items={orders}
        empty="—"
        onSelect={(o) => openOrderView(o)}
        sortFields={ORDER_SORT_MENU}
        sort={orderSort}
        order={orderOrder}
        onSortChange={onOrderSortChange}
        fields={[
          {
            id: 'number',
            primary: true,
            render: (o) => (
              <>
                <span className="mono">{o.orderNumber}</span>{' '}
                <span className={statusClass(o.fulfillStatus)}>{o.fulfillStatus}</span>
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
              o.amountEur != null ? `${Number(o.amountEur).toFixed(2)} EUR` : '—',
          },
          {
            id: 'content',
            priority: 'low',
            render: (o) => <ContentChannelIcons summary={orderContentMap[o.id]} />,
          },
        ]}
        actions={(o) =>
          renderRowActions('delivery', 'ServiceOrder', o, { onView: () => openOrderView(o) })
        }
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="orderNumber"
                  label={t('wb.number', { ns: 'common' })}
                  sort={orderSort}
                  order={orderOrder}
                  onSortChange={onOrderSortChange}
                />
                <ListSortHeader
                  field="customerName"
                  label={t('wb.customer', { ns: 'common' })}
                  sort={orderSort}
                  order={orderOrder}
                  onSortChange={onOrderSortChange}
                />
                <ListSortHeader
                  field="amountEur"
                  label="EUR"
                  sort={orderSort}
                  order={orderOrder}
                  onSortChange={onOrderSortChange}
                />
                <ListSortHeader
                  field="fulfillStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={orderSort}
                  order={orderOrder}
                  onSortChange={onOrderSortChange}
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common', defaultValue: 'Content' })}</th>
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr
                  key={o.id}
                  className="row-click"
                  onClick={() => openOrderView(o)}
                >
                  <td className="mono">{o.orderNumber}</td>
                  <td>{o.customerName || '—'}</td>
                  <td>{o.amountEur != null ? Number(o.amountEur).toFixed(2) : '—'}</td>
                  <td>
                    <span className={statusClass(o.fulfillStatus)}>{o.fulfillStatus}</span>
                  </td>
                  <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                    <ContentChannelIcons summary={orderContentMap[o.id]} />
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('delivery', 'ServiceOrder', o, {
                      onView: () => openOrderView(o),
                    })}
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
    </>
  )

  const list =
    tab === 'customers'
      ? customersList
      : tab === 'services'
        ? servicesList
        : tab === 'resources'
          ? resourcesList
          : tab === 'orders'
            ? ordersList
            : null

  getBelowFieldsRef.current = ({ resource, record }) => {
    if (resource !== 'ServiceOrder' || !record) return null
    const live = selectedRef.current
    const order = live?.id === record.id ? live : record
    const opts = resourceOptionsRef.current || []
    return (
      <>
        <label>
          {t('delivery.assignEmployee', { defaultValue: 'Assign employee' })}
          <select
            value={order.resourceId || ''}
            onChange={(e) => onAssign(e.target.value)}
            disabled={!!busyRef.current || order.fulfillStatus === 'COMPLETED'}
          >
            <option value="">
              {t('delivery.noAssignee', { defaultValue: '— none —' })}
            </option>
            {opts.length === 0 && (
              <option value="" disabled>
                {t('delivery.emptyAssignees', {
                  defaultValue: 'No active employees — add people in HR first.',
                })}
              </option>
            )}
            {opts.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
                {r.email ? ` · ${r.email}` : ''}
              </option>
            ))}
          </select>
        </label>
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
              {(order.lines || []).map((l) => (
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
        {collapsedContentRail('delivery', 'ServiceOrder', order.id, async () =>
          setOrderContentMap(await fetchContentMap('delivery', 'ServiceOrder', ordersRef.current)),
        )}
      </>
    )
  }

  getFooterExtraRef.current = ({ resource, record }) => {
    if (resource !== 'ServiceOrder' || !record) return null
    const live = selectedRef.current
    const order = live?.id === record.id ? live : record
    const isBusy = !!busyRef.current
    return (
      <>
        {['DRAFT'].includes(order.fulfillStatus) && (
          <button
            type="button"
            className="btn"
            onClick={() => runOrderRef.current('schedule', order.id)}
            disabled={isBusy}
          >
            <Calendar size={16} /> Schedule
          </button>
        )}
        {['DRAFT', 'SCHEDULED'].includes(order.fulfillStatus) && (
          <button
            type="button"
            className="btn"
            onClick={() => runOrderRef.current('start', order.id)}
            disabled={isBusy}
          >
            <Play size={16} /> Start
          </button>
        )}
        {order.fulfillStatus !== 'COMPLETED' && order.fulfillStatus !== 'CANCELLED' && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => runOrderRef.current('complete', order.id)}
            disabled={isBusy}
          >
            <Check size={16} /> Complete
          </button>
        )}
        <a
          className="btn"
          href={deliveryApi.orders.pdfUrl(order.id)}
          target="_blank"
          rel="noreferrer"
          title={t('delivery.downloadPdfTitle', {
            defaultValue: 'Download delivery PDF',
          })}
        >
          <FileText size={16} /> {t('actions.pdf', { ns: 'common', defaultValue: 'PDF' })}
        </a>
      </>
    )
  }

  const emptyTitle = t('desk.emptyTitle', { ns: 'common' })
  const emptyBody = t('desk.emptyBodyMasters', {
    ns: 'common',
    defaultValue: 'Use + New to create, or View on a row for the full record.',
  })


  return (
    <div className="page delivery-desk-page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}

      <div className="toolbar" style={{ marginBottom: '1.5rem' }}>
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <Link className="btn btn-secondary" to="/sales">
          {t('delivery.salesDesk')}
        </Link>
        <Link className="btn btn-secondary" to="/scm/buyers">
          {t('delivery.scmDesk')}
        </Link>
      </div>

      <DeskShell
        layout="single"
        title={
          <>
            <Briefcase size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('delivery.title')}
          </>
        }
        subtitle={null}
        tabs={tabs}
        activeTab={tab}
        onTabChange={requestTabChange}
        newLabel={newLabel}
        newExpanded={newExpanded}
        onToggleNew={requestToggleNew}
        newPanel={newPanel}
        list={list}
        hasSelection={false}
        emptyTitle={emptyTitle}
        emptyBody={emptyBody}
        detail={null}
        detailFooter={null}
        processSlot={processSlot}
        helpContext={helpContext}
        narrowSheetOpen={false}
      />

      <DeskNewFab label={newLabel} onClick={() => !newExpanded && requestToggleNew()} />

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
