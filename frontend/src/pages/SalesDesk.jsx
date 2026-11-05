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
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import {
  ShoppingCart,
  RefreshCw,
  Plus,
  Send,
  Check,
  X,
  Ban,
  FileText,
  Users,
  Package,
  Boxes,
  Briefcase,
  Eye,
  Pencil,
  Trash2,
} from 'lucide-react'
import { contentApi, salesApi, catalogueApi, domainsApi, meApi } from '../api/client'
import { allowsSilverSurfaces } from '../utils/silverSurfaces'
import CurrencySelect from '../components/CurrencySelect'
import DocumentLinesEditor, {
  emptyDocLine,
  linesToApiPayload,
} from '../components/DocumentLinesEditor'
import MasterEntityFields from '../forms/MasterEntityFields'
import { getMasterFormSpec } from '../forms/masterFormSpecs'

const customerMasterSpec = getMasterFormSpec('sales', 'Customer')
const productMasterSpec = getMasterFormSpec('production', 'Product')
const serviceMasterSpec = getMasterFormSpec('delivery', 'Service')
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import useRecordView from '../hooks/useRecordView'
import { labelFromList } from '../utils/entityDisplayLabel'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import ResponsiveDataList from '../components/ResponsiveDataList'
import DeskNewFab from '../components/DeskNewFab'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'

import CustomerSelect from '../components/CustomerSelect'
import DeskShell from '../components/desk/DeskShell'
import ConfirmDialog from '../components/ConfirmDialog'
import RowActionSlots from '../components/RowActionSlots'
import useDeskSelection, { scrollSelectedIntoView } from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'

const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'

const DEFAULT_QUOTE_LINES = () => [
  emptyDocLine({ name: 'Consulting day', quantity: '1', unitPriceEur: '500' }),
]
const DEFAULT_ORDER_LINES = () => [
  emptyDocLine({ name: 'Product', quantity: '1', unitPriceEur: '100' }),
]

async function fetchContentMap(pkg, resource, rows) {
  if (!rows?.length) return {}
  try {
    const res = await contentApi.summary(
      pkg,
      resource,
      rows.map((r) => r.id),
    )
    const map = {}
    for (const s of res.items || []) {
      map[s.hostId] = s
    }
    return map
  } catch {
    return {}
  }
}

function linesDirty(lines, defaultsFactory) {
  const payload = linesToApiPayload(lines)
  const defPayload = linesToApiPayload(defaultsFactory())
  if (payload.length !== defPayload.length) return true
  for (let i = 0; i < payload.length; i += 1) {
    const a = payload[i]
    const b = defPayload[i]
    if (
      a.name !== b.name ||
      String(a.quantity) !== String(b.quantity) ||
      String(a.unitPriceEur) !== String(b.unitPriceEur) ||
      (a.catalogueItemId || null) !== (b.catalogueItemId || null)
    ) {
      return true
    }
  }
  return false
}

/**
 * Sales commercial desk (S-S1–S-S3): Customer → Quote(+lines) → Order → Finance DRAFT invoice.
 * Buyers UI 2.0 (ADR-050): single-column + modal registers.
 */
export default function SalesDesk() {
  const { t } = useTranslation(['sales', 'common', 'content'])
  const loadAllRef = useRef(async () => {})
  const getBelowFieldsRef = useRef(null)
  const getFooterExtraRef = useRef(null)
  const deepLinkOpened = useRef(null)
  const selectedQuoteRef = useRef(null)
  const selectedOrderRef = useRef(null)
  const quoteWarningsRef = useRef([])
  const orderWarningsRef = useRef([])
  const busyRef = useRef(null)
  const quotesRef = useRef([])
  const ordersRef = useRef([])
  const onSendQuoteRef = useRef(async () => {})
  const onAcceptQuoteRef = useRef(async () => {})
  const onRejectQuoteRef = useRef(async () => {})
  const onConfirmOrderRef = useRef(async () => {})
  const onCancelOrderRef = useRef(async () => {})
  const tabs = useMemo(
    () => [
      { id: 'customers', label: t('desk.tabs.customers'), icon: Users },
      { id: 'quotes', label: t('desk.tabs.quotes'), icon: FileText },
      { id: 'orders', label: t('desk.tabs.orders'), icon: Package },
      { id: 'products', label: t('desk.tabs.products'), icon: Boxes },
      { id: 'services', label: t('desk.tabs.services'), icon: Briefcase },
    ],
    [t],
  )

  const [tab, setTab] = useState(() => {
    if (typeof window === 'undefined') return 'quotes'
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    if (urlTab) return urlTab
    try {
      const raw = window.localStorage.getItem('steakos.desk.default.sales')
      const parsed = raw ? JSON.parse(raw) : null
      if (parsed?.tab) return String(parsed.tab)
    } catch {
      /* ignore */
    }
    return 'quotes'
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

  const { loadMemory, saveMemory } = useDeskMemory({ deskKey: 'sales', companyId })

  useDeskSelection({
    deskKey: 'sales',
    defaultTab: 'quotes',
    tab,
    setTab,
    selectedId,
    setSelectedId,
  })

  // Command palette / deep-link: ?new=1 expands create once
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
    // company-scoped restore once id is known
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [companyId])

  const [customers, setCustomers] = useState([])
  const [quotes, setQuotes] = useState([])
  const [orders, setOrders] = useState([])
  const [products, setProducts] = useState([])
  const [services, setServices] = useState([])
  const [selectedQuote, setSelectedQuote] = useState(null)
  const [selectedOrder, setSelectedOrder] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)

  const [quoteSort, setQuoteSort] = useState('id')
  const [quoteOrder, setQuoteOrder] = useState('desc')
  const [orderSort, setOrderSort] = useState('id')
  const [orderOrder, setOrderOrder] = useState('desc')
  const [showAdvanced, setShowAdvanced] = useState(false)

  const QUOTE_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'quoteNumber', label: 'Number' },
      { value: 'customerName', label: 'Customer' },
      { value: 'amountEur', label: 'EUR' },
      { value: 'quoteStatus', label: 'Status' },
    ],
    [],
  )
  const ORDER_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'orderNumber', label: 'Number' },
      { value: 'customerName', label: 'Customer' },
      { value: 'amountEur', label: 'EUR' },
      { value: 'orderStatus', label: 'Status' },
    ],
    [],
  )

  useEffect(() => {
    let cancelled = false
    meApi
      .get()
      .then((me) => {
        if (cancelled) return
        setShowAdvanced(allowsSilverSurfaces(me?.templateTier))
        const cid = me?.activeCompanyId ?? me?.activeCompany?.id
        if (cid != null && cid !== '') setCompanyId(String(cid))
      })
      .catch(() => {
        if (!cancelled) setShowAdvanced(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const [customerForm, setCustomerForm] = useState(() => customerMasterSpec.empty())
  const [productForm, setProductForm] = useState(() => productMasterSpec.empty())
  const [svcForm, setSvcForm] = useState(() => serviceMasterSpec.empty())

  const onMasterField =
    (setter) =>
    (e) => {
      const { name, value, type, checked } = e.target
      setter((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
    }

  const [quoteForm, setQuoteForm] = useState({
    customerId: '',
    name: '',
    currencyCode: 'EUR',
  })
  const [quoteWarnings, setQuoteWarnings] = useState([])
  const [quoteLines, setQuoteLines] = useState(DEFAULT_QUOTE_LINES)
  const [pendingQuoteContent, setPendingQuoteContent] = useState([])
  const [pendingOrderContent, setPendingOrderContent] = useState([])
  const [pendingCustomerContent, setPendingCustomerContent] = useState([])
  const [pendingProductContent, setPendingProductContent] = useState([])
  const [pendingServiceContent, setPendingServiceContent] = useState([])
  const [quoteContentMap, setQuoteContentMap] = useState({})
  const [orderContentMap, setOrderContentMap] = useState({})
  const [customerContentMap, setCustomerContentMap] = useState({})
  const [productContentMap, setProductContentMap] = useState({})
  const [serviceContentMap, setServiceContentMap] = useState({})

  const [orderForm, setOrderForm] = useState({
    customerId: '',
    name: '',
    currencyCode: 'EUR',
  })
  const [orderWarnings, setOrderWarnings] = useState([])
  const [orderLines, setOrderLines] = useState(DEFAULT_ORDER_LINES)

  const [custPage, setCustPage] = useState({ max: 25, offset: 0, total: 0 })
  const [quotePage, setQuotePage] = useState({ max: 25, offset: 0, total: 0 })
  const [orderPage, setOrderPage] = useState({ max: 25, offset: 0, total: 0 })
  const [prodPage, setProdPage] = useState({ max: 25, offset: 0, total: 0 })
  const [svcPage, setSvcPage] = useState({ max: 25, offset: 0, total: 0 })
  /** Dropdown options (not paged) — max 100 until typeahead (concept P4). */
  const [customerOptions, setCustomerOptions] = useState([])

  selectedQuoteRef.current = selectedQuote
  selectedOrderRef.current = selectedOrder
  quoteWarningsRef.current = quoteWarnings
  orderWarningsRef.current = orderWarnings
  busyRef.current = busy
  quotesRef.current = quotes
  ordersRef.current = orders

  const {
    viewModal,
    openView,
    openEdit,
    updateViewingRecord,
  } = useRecordView({
    modalEdit: true,
    canModalEdit: ({ resource, record }) => {
      if (resource === 'Customer' || resource === 'CatalogueItem') return true
      if (resource === 'CustomerQuote') return record?.quoteStatus === 'DRAFT'
      if (resource === 'SalesOrder') return record?.orderStatus === 'DRAFT'
      return false
    },
    onSaved: (updated) => {
      loadAllRef.current().catch(() => {})
      if (updated?.id == null) return
      if (updated.quoteStatus != null || Array.isArray(updated.lines)) {
        setSelectedQuote((prev) =>
          prev && String(prev.id) === String(updated.id) ? { ...prev, ...updated } : prev,
        )
      }
      if (updated.orderStatus != null || Array.isArray(updated.lines)) {
        setSelectedOrder((prev) =>
          prev && String(prev.id) === String(updated.id) ? { ...prev, ...updated } : prev,
        )
      }
    },
    getBelowFields: (ctx) => getBelowFieldsRef.current?.(ctx) ?? null,
    getFooterExtra: (ctx) => getFooterExtraRef.current?.(ctx) ?? null,
  })

  const canEditRecord = useCallback((resource, record) => {
    if (resource === 'Customer' || resource === 'CatalogueItem') return true
    if (resource === 'CustomerQuote') return record?.quoteStatus === 'DRAFT'
    if (resource === 'SalesOrder') return record?.orderStatus === 'DRAFT'
    return false
  }, [])

  const canDeleteRecord = useCallback((resource, record) => {
    if (resource === 'Customer' || resource === 'CatalogueItem') return true
    if (resource === 'CustomerQuote') return record?.quoteStatus === 'DRAFT'
    if (resource === 'SalesOrder') return record?.orderStatus === 'DRAFT'
    return false
  }, [])

  const loadAll = useCallback(async () => {
    const [c, cPick, q, o, p, s] = await Promise.all([
      salesApi.customers.list({ max: custPage.max, offset: custPage.offset }),
      salesApi.customers.list({ max: 100, offset: 0 }),
      salesApi.quotes.list({
        max: quotePage.max,
        offset: quotePage.offset,
        sort: quoteSort,
        order: quoteOrder,
      }),
      salesApi.orders.list({
        max: orderPage.max,
        offset: orderPage.offset,
        sort: orderSort,
        order: orderOrder,
      }),
      catalogueApi.list({
        nature: 'GOODS',
        activeOnly: true,
        max: prodPage.max,
        offset: prodPage.offset,
      }),
      catalogueApi.list({
        nature: 'SERVICE',
        activeOnly: true,
        max: svcPage.max,
        offset: svcPage.offset,
      }),
    ])
    const cust = c.items || []
    const qs = q.items || []
    const os = o.items || []
    setCustomers(cust)
    setCustomerOptions(cPick.items || [])
    setQuotes(qs)
    setOrders(os)
    setCustPage((pg) => ({
      ...pg,
      max: c.max ?? pg.max,
      offset: c.offset ?? pg.offset,
      total: c.total ?? cust.length,
    }))
    setQuotePage((pg) => ({
      ...pg,
      max: q.max ?? pg.max,
      offset: q.offset ?? pg.offset,
      total: q.total ?? qs.length,
    }))
    setOrderPage((pg) => ({
      ...pg,
      max: o.max ?? pg.max,
      offset: o.offset ?? pg.offset,
      total: o.total ?? os.length,
    }))
    setProducts(p.items || [])
    setServices(s.items || [])
    setProdPage((pg) => ({
      ...pg,
      max: p.max ?? pg.max,
      offset: p.offset ?? pg.offset,
      total: p.total ?? (p.items || []).length,
    }))
    setSvcPage((pg) => ({
      ...pg,
      max: s.max ?? pg.max,
      offset: s.offset ?? pg.offset,
      total: s.total ?? (s.items || []).length,
    }))
    const prods = p.items || []
    const svcs = s.items || []
    const [qMap, oMap, cMap, pMap, sMap] = await Promise.all([
      fetchContentMap('sales', 'CustomerQuote', qs),
      fetchContentMap('sales', 'SalesOrder', os),
      fetchContentMap('sales', 'Customer', cust),
      fetchContentMap('catalogue', 'CatalogueItem', prods),
      fetchContentMap('catalogue', 'CatalogueItem', svcs),
    ])
    setQuoteContentMap(qMap)
    setOrderContentMap(oMap)
    setCustomerContentMap(cMap)
    setProductContentMap(pMap)
    setServiceContentMap(sMap)
    return { customers: cust, quotes: qs, orders: os }
  }, [
    quoteSort,
    quoteOrder,
    orderSort,
    orderOrder,
    custPage.max,
    custPage.offset,
    quotePage.max,
    quotePage.offset,
    orderPage.max,
    orderPage.offset,
    prodPage.max,
    prodPage.offset,
    svcPage.max,
    svcPage.offset,
  ])

  loadAllRef.current = loadAll

  const selectQuote = useCallback(async (id) => {
    if (!id) {
      setSelectedQuote(null)
      setQuoteWarnings([])
      return null
    }
    const full = await salesApi.quotes.get(id)
    setSelectedQuote(full)
    setQuoteWarnings(Array.isArray(full?.warnings) ? full.warnings : [])
    return full
  }, [])

  const selectOrder = useCallback(async (id) => {
    if (!id) {
      setSelectedOrder(null)
      setOrderWarnings([])
      return null
    }
    const full = await salesApi.orders.get(id)
    setSelectedOrder(full)
    setOrderWarnings(Array.isArray(full?.warnings) ? full.warnings : [])
    return full
  }, [])

  const openQuoteView = useCallback(
    async (q) => {
      if (!q?.id && q?.id !== 0) return
      const full = await salesApi.quotes.get(q.id)
      setSelectedQuote(full)
      setQuoteWarnings(Array.isArray(full?.warnings) ? full.warnings : [])
      openView('sales', 'CustomerQuote', full)
    },
    [openView],
  )

  const openOrderView = useCallback(
    async (o) => {
      if (!o?.id && o?.id !== 0) return
      const full = await salesApi.orders.get(o.id)
      setSelectedOrder(full)
      setOrderWarnings(Array.isArray(full?.warnings) ? full.warnings : [])
      openView('sales', 'SalesOrder', full)
    },
    [openView],
  )

  const onDeleteRecord = useCallback(
    async (pkg, resource, record) => {
      const label =
        record?.name ||
        record?.quoteNumber ||
        record?.orderNumber ||
        t('view.untitled', { ns: 'common', defaultValue: 'this record' })
      if (!window.confirm(t('actions.confirmDelete', { ns: 'common', label }))) return
      setError(null)
      try {
        if (resource === 'SalesOrder') {
          await salesApi.orders.cancel(record.id)
        } else if (resource === 'CustomerQuote') {
          await domainsApi.remove('sales', 'CustomerQuote', record.id)
        } else if (resource === 'Customer') {
          await domainsApi.remove('sales', 'Customer', record.id)
        } else if (resource === 'CatalogueItem') {
          await domainsApi.remove('catalogue', 'CatalogueItem', record.id)
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
                  openEdit(pkg, resource, record)
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
                  onDeleteRecord(pkg, resource, record)
                }}
              >
                <Trash2 size={14} />
              </button>
            ) : null
          }
        />
      )
    },
    [canDeleteRecord, canEditRecord, onDeleteRecord, openEdit, openView, t],
  )

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      try {
        if (tab === 'quotes') {
          if (selectedId) {
            const full = await selectQuote(selectedId)
            if (
              !cancelled &&
              full &&
              deepLinkOpened.current !== String(selectedId)
            ) {
              openView('sales', 'CustomerQuote', full)
              deepLinkOpened.current = String(selectedId)
            }
          } else if (!cancelled) {
            setSelectedQuote(null)
            setQuoteWarnings([])
          }
          if (!cancelled) setSelectedOrder(null)
        } else if (tab === 'orders') {
          if (selectedId) {
            const full = await selectOrder(selectedId)
            if (
              !cancelled &&
              full &&
              deepLinkOpened.current !== String(selectedId)
            ) {
              openView('sales', 'SalesOrder', full)
              deepLinkOpened.current = String(selectedId)
            }
          } else if (!cancelled) {
            setSelectedOrder(null)
            setOrderWarnings([])
          }
          if (!cancelled) setSelectedQuote(null)
        } else if (tab === 'customers') {
          if (!cancelled) {
            setSelectedQuote(null)
            setSelectedOrder(null)
          }
          if (selectedId && deepLinkOpened.current !== String(selectedId)) {
            try {
              const full = await salesApi.customers.get(selectedId)
              if (!cancelled && full) {
                openView('sales', 'Customer', full)
                deepLinkOpened.current = String(selectedId)
              }
            } catch (e) {
              if (!cancelled) setError(e.message || String(e))
            }
          }
        } else if (!cancelled) {
          setSelectedQuote(null)
          setSelectedOrder(null)
        }
      } catch (e) {
        if (!cancelled) setError(e.message || String(e))
      }
    }
    run()
    return () => {
      cancelled = true
    }
  }, [tab, selectedId, selectQuote, selectOrder, openView])

  useEffect(() => {
    if (!selectedId) return
    const id = window.requestAnimationFrame(() => {
      scrollSelectedIntoView('.desk-shell-list')
    })
    return () => window.cancelAnimationFrame(id)
  }, [selectedId, tab, selectedQuote?.id, selectedOrder?.id])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      await loadAll()
      if (tab === 'quotes' && selectedId) await selectQuote(selectedId)
      if (tab === 'orders' && selectedId) await selectOrder(selectedId)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [loadAll, selectQuote, selectOrder, selectedId, tab])

  useEffect(() => {
    setLoading(true)
    loadAll()
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [loadAll])

  const resetCreateForms = useCallback(() => {
    setQuoteForm({ customerId: '', name: '', currencyCode: 'EUR' })
    setQuoteLines(DEFAULT_QUOTE_LINES())
    setPendingQuoteContent([])
    setQuoteWarnings([])
    setOrderForm({ customerId: '', name: '', currencyCode: 'EUR' })
    setOrderLines(DEFAULT_ORDER_LINES())
    setPendingOrderContent([])
    setOrderWarnings([])
    setCustomerForm(customerMasterSpec.empty())
    setPendingCustomerContent([])
    setProductForm(productMasterSpec.empty())
    setPendingProductContent([])
    setSvcForm(serviceMasterSpec.empty())
    setPendingServiceContent([])
  }, [])

  const createDirty = useMemo(() => {
    if (tab === 'quotes') {
      return Boolean(
        quoteForm.customerId ||
          (quoteForm.name || '').trim() ||
          quoteForm.currencyCode !== 'EUR' ||
          pendingQuoteContent.length ||
          linesDirty(quoteLines, DEFAULT_QUOTE_LINES),
      )
    }
    if (tab === 'orders') {
      return Boolean(
        orderForm.customerId ||
          (orderForm.name || '').trim() ||
          orderForm.currencyCode !== 'EUR' ||
          pendingOrderContent.length ||
          linesDirty(orderLines, DEFAULT_ORDER_LINES),
      )
    }
    if (tab === 'customers') {
      const empty = customerMasterSpec.empty()
      return (
        Boolean((customerForm.name || '').trim() || customerForm.email || customerForm.phone) ||
        customerForm.customerKind !== empty.customerKind ||
        customerForm.currencyCode !== empty.currencyCode ||
        pendingCustomerContent.length
      )
    }
    if (tab === 'products') {
      return Boolean(
        (productForm.name || '').trim() ||
          (productForm.sku || '').trim() ||
          pendingProductContent.length,
      )
    }
    if (tab === 'services') {
      return Boolean(
        (svcForm.name || '').trim() ||
          (svcForm.code || '').trim() ||
          pendingServiceContent.length,
      )
    }
    return false
  }, [
    tab,
    quoteForm,
    quoteLines,
    pendingQuoteContent,
    orderForm,
    orderLines,
    pendingOrderContent,
    customerForm,
    pendingCustomerContent,
    productForm,
    pendingProductContent,
    svcForm,
    pendingServiceContent,
  ])

  const quoteCanSubmit =
    Boolean(quoteForm.customerId) && linesToApiPayload(quoteLines).length > 0
  const orderCanSubmit =
    Boolean(orderForm.customerId) && linesToApiPayload(orderLines).length > 0
  const customerCanSubmit = Boolean((customerForm.name || '').trim())
  const productCanSubmit = Boolean(
    (productForm.name || '').trim() && (productForm.unit || '').trim(),
  )
  const serviceCanSubmit = Boolean((svcForm.name || '').trim() && (svcForm.unit || '').trim())

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
    if (tab === 'quotes') return quotes.map((q) => q.id)
    if (tab === 'orders') return orders.map((o) => o.id)
    if (tab === 'customers') return customers.map((c) => c.id)
    if (tab === 'products') return products.map((p) => p.id)
    if (tab === 'services') return services.map((s) => s.id)
    return []
  }, [tab, quotes, orders, customers, products, services])

  useDeskKeyboard({
    enabled: !newExpanded,
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

  const onCreateProduct = async (e) => {
    e.preventDefault()
    if (!productCanSubmit) return
    setBusy('prod')
    setError(null)
    setInfo(null)
    try {
      const body = {
        ...productMasterSpec.toBody(productForm),
        nature: 'GOODS',
        code: productForm.sku || productForm.code || undefined,
      }
      const prod = await catalogueApi.create(body)
      if (pendingProductContent.length && prod?.id != null) {
        await attachPendingContent(
          'catalogue',
          'CatalogueItem',
          prod.id,
          pendingProductContent,
        )
        setPendingProductContent([])
      }
      setInfo(
        t('toast.namedStatus', {
          ns: 'common',
          what: t('desk.tabs.products'),
          name: prod.name,
          status: prod.code || prod.sku || '—',
        }),
      )
      setProductForm(productMasterSpec.empty())
      setNewExpanded(false)
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateService = async (e) => {
    e.preventDefault()
    if (!serviceCanSubmit) return
    setBusy('svc')
    setError(null)
    setInfo(null)
    try {
      const raw = serviceMasterSpec.toBody(svcForm)
      const s = await catalogueApi.create({
        ...raw,
        nature: 'SERVICE',
        listPrice: raw.listPriceEur ?? raw.listPrice,
        canBeSold: true,
        canBeBought: false,
      })
      if (pendingServiceContent.length && s?.id != null) {
        await attachPendingContent(
          'catalogue',
          'CatalogueItem',
          s.id,
          pendingServiceContent,
        )
        setPendingServiceContent([])
      }
      setInfo(
        t('toast.namedCreated', {
          ns: 'common',
          what: t('desk.tabs.services'),
          name: s.name,
        }),
      )
      setSvcForm(serviceMasterSpec.empty())
      setNewExpanded(false)
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateCustomer = async (e) => {
    e.preventDefault()
    if (!customerCanSubmit) return
    setBusy('cust')
    setError(null)
    setInfo(null)
    try {
      const c = await salesApi.customers.create(customerMasterSpec.toBody(customerForm))
      if (pendingCustomerContent.length && c?.id != null) {
        await attachPendingContent('sales', 'Customer', c.id, pendingCustomerContent)
        setPendingCustomerContent([])
      }
      setInfo(
        t('toast.namedCreated', {
          ns: 'common',
          what: t('labels.customer', { ns: 'common' }),
          name: c.name,
        }),
      )
      setCustomerForm(customerMasterSpec.empty())
      setNewExpanded(false)
      await loadAll()
      setSelectedId(null)
      setTab('quotes')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateQuote = async (e) => {
    e.preventDefault()
    if (!quoteCanSubmit) return
    setBusy('quote')
    setError(null)
    setInfo(null)
    try {
      const lines = linesToApiPayload(quoteLines)
      if (!lines.length) {
        setError(t('docLines.needOne', { ns: 'common', defaultValue: 'Add at least one line.' }))
        setBusy(null)
        return
      }
      const body = {
        name: quoteForm.name || undefined,
        customerId: quoteForm.customerId || undefined,
        currencyCode: quoteForm.currencyCode || undefined,
        lines,
      }
      const q = await salesApi.quotes.create(body)
      setQuoteWarnings(Array.isArray(q?.warnings) ? q.warnings : [])
      setQuoteLines(DEFAULT_QUOTE_LINES())
      setQuoteForm({ customerId: '', name: '', currencyCode: 'EUR' })
      let contentNote = ''
      if (pendingQuoteContent.length) {
        const att = await attachPendingContent(
          'sales',
          'CustomerQuote',
          q.id,
          pendingQuoteContent,
        )
        if (att.errors?.length) {
          contentNote = ` · content attach failed: ${att.errors[0]}`
          if (att.ok > 0) setPendingQuoteContent([])
        } else {
          contentNote = att.ok > 0 ? ` · ${att.ok} content item(s) attached` : ''
          setPendingQuoteContent([])
        }
      }
      setInfo(
        `Quote ${q.quoteNumber} · ${q.quoteStatus} · ${Number(q.amountEur).toFixed(2)} EUR${contentNote}`,
      )
      if (contentNote.includes('failed')) {
        setError(
          `Quote saved, but content was not attached: ${contentNote.replace(/^ · content attach failed: /, '')}`,
        )
      }
      setNewExpanded(false)
      await loadAll()
      setTab('quotes')
      deepLinkOpened.current = String(q.id)
      setSelectedId(String(q.id))
      await openQuoteView(q)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onSendQuote = async () => {
    const qid = selectedQuoteRef.current?.id
    if (!qid) return
    setBusy('send')
    setError(null)
    setInfo(null)
    try {
      const q = await salesApi.quotes.send(qid)
      setInfo(t('toast.sent', { ns: 'common', number: q.quoteNumber }))
      await loadAll()
      const full = await selectQuote(q.id)
      if (full) updateViewingRecord(full)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onAcceptQuote = async () => {
    const qid = selectedQuoteRef.current?.id
    if (!qid) return
    setBusy('accept')
    setError(null)
    setInfo(null)
    try {
      const result = await salesApi.quotes.accept(qid)
      const order = result.order
      setInfo(
        `Accepted ${result.quote?.quoteNumber}` +
          (order ? ` → order ${order.orderNumber}` : '') +
          (result.alreadyAccepted ? ' (already)' : ''),
      )
      await loadAll()
      if (order?.id) {
        deepLinkOpened.current = String(order.id)
        setTab('orders')
        setSelectedId(String(order.id))
        await openOrderView(order)
      } else {
        const full = await selectQuote(qid)
        if (full) updateViewingRecord(full)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onRejectQuote = async () => {
    const qid = selectedQuoteRef.current?.id
    if (!qid) return
    setBusy('reject')
    setError(null)
    setInfo(null)
    try {
      const q = await salesApi.quotes.reject(qid)
      setInfo(t('toast.rejected', { ns: 'common', name: q.quoteNumber }))
      await loadAll()
      const full = await selectQuote(q.id)
      if (full) updateViewingRecord(full)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  onSendQuoteRef.current = onSendQuote
  onAcceptQuoteRef.current = onAcceptQuote
  onRejectQuoteRef.current = onRejectQuote

  const onCreateOrder = async (e) => {
    e.preventDefault()
    if (!orderCanSubmit) return
    setBusy('order')
    setError(null)
    setInfo(null)
    try {
      const lines = linesToApiPayload(orderLines)
      if (!lines.length) {
        setError(t('docLines.needOne', { ns: 'common', defaultValue: 'Add at least one line.' }))
        setBusy(null)
        return
      }
      const body = {
        name: orderForm.name || undefined,
        customerId: orderForm.customerId || undefined,
        currencyCode: orderForm.currencyCode || undefined,
        lines,
      }
      const o = await salesApi.orders.create(body)
      setOrderWarnings(Array.isArray(o?.warnings) ? o.warnings : [])
      if (pendingOrderContent.length) {
        await attachPendingContent('sales', 'SalesOrder', o.id, pendingOrderContent)
        setPendingOrderContent([])
      }
      setOrderLines(DEFAULT_ORDER_LINES())
      setOrderForm({ customerId: '', name: '', currencyCode: 'EUR' })
      setInfo(
        t('toast.namedStatus', {
          ns: 'common',
          what: t('labels.order', { ns: 'common' }),
          name: o.orderNumber,
          status: o.orderStatus,
        }),
      )
      setNewExpanded(false)
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

  const onConfirmOrder = async () => {
    const oid = selectedOrderRef.current?.id
    if (!oid) return
    setBusy('confirm')
    setError(null)
    setInfo(null)
    try {
      const result = await salesApi.orders.confirm(oid)
      const invLabel =
        result.financeInvoiceNumber ||
        result.invoice?.invoiceNumber ||
        (result.financeInvoiceId ? 'Finance DRAFT invoice' : null)
      setInfo(
        `Confirmed ${result.order?.orderNumber}` +
          (invLabel ? ` → ${invLabel}` : '') +
          (result.alreadyConfirmed ? ' (already)' : ''),
      )
      await loadAll()
      const full = await selectOrder(oid)
      if (full) updateViewingRecord(full)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCancelOrder = async () => {
    const cur = selectedOrderRef.current
    if (!cur?.id) return
    if (!window.confirm(`${t('order.cancel')} ${cur.orderNumber}?`)) return
    setBusy('cancel')
    setError(null)
    setInfo(null)
    try {
      const o = await salesApi.orders.cancel(cur.id)
      setInfo(t('toast.cancelled', { ns: 'common', number: o.orderNumber }))
      await loadAll()
      const full = await selectOrder(o.id)
      if (full) updateViewingRecord(full)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  onConfirmOrderRef.current = onConfirmOrder
  onCancelOrderRef.current = onCancelOrder

  const statusClass = (s) => {
    if (s === 'ACCEPTED' || s === 'CONFIRMED' || s === 'CLOSED') return 'badge badge-ok'
    if (s === 'REJECTED' || s === 'CANCELLED' || s === 'EXPIRED') return 'badge badge-muted'
    if (s === 'SENT') return 'badge badge-warn'
    return 'badge badge-draft'
  }

  const newLabel = useMemo(() => {
    if (tab === 'customers') return t('customer.new')
    if (tab === 'quotes') return t('quote.new')
    if (tab === 'orders') return t('order.direct')
    if (tab === 'products') return t('product.new')
    if (tab === 'services') return t('service.new')
    return t('desk.new', { ns: 'common', defaultValue: 'New' })
  }, [tab, t])

  const helpContext = useMemo(() => ({ desk: 'sales', tab }), [tab])

  const collapsedContentRail = (pkg, resource, id, onChanged) => (
    <details className="content-rail-collapse" style={{ marginTop: '1.25rem' }}>
      <summary className="btn btn-ghost" style={{ cursor: 'pointer', listStyle: 'inside' }}>
        {t('desk.attachmentsNotes', { ns: 'common', defaultValue: 'Attachments & notes' })}
      </summary>
      <ContentRail package={pkg} resource={resource} id={id} onChanged={onChanged} />
    </details>
  )

  // —— create panels ——
  const quoteCreatePanel = (
    <form className="form-grid" onSubmit={onCreateQuote}>
      <label>
        {t('quote.customer')}
        <CustomerSelect
          value={quoteForm.customerId}
          onChange={(id) => setQuoteForm((f) => ({ ...f, customerId: id || '' }))}
          disabled={!!busy}
        />
      </label>
      <label>
        {t('quote.name')}
        <input
          value={quoteForm.name}
          onChange={(e) => setQuoteForm((f) => ({ ...f, name: e.target.value }))}
          placeholder={t('wb.autoIfEmpty', { ns: 'common' })}
        />
      </label>
      <label>
        {t('quote.currency', { defaultValue: 'Document currency' })}
        <CurrencySelect
          name="currencyCode"
          value={quoteForm.currencyCode}
          onChange={(e) =>
            setQuoteForm((f) => ({ ...f, currencyCode: e.target.value || 'EUR' }))
          }
          disabled={!!busy}
          required
        />
      </label>
      {quoteWarnings?.length > 0 && (
        <div className="banner-info" role="status">
          {quoteWarnings.map((w) => (
            <div key={w.code + w.message}>{w.message}</div>
          ))}
        </div>
      )}
      <DocumentLinesEditor
        forChannel="sale"
        lines={quoteLines}
        onChange={setQuoteLines}
        disabled={!!busy}
      />
      <PendingContentComposer
        package="sales"
        resource="CustomerQuote"
        pending={pendingQuoteContent}
        onChange={setPendingQuoteContent}
        title={t('pending.titleQuote', { ns: 'content' })}
        hint={t('pending.hintQuote', { ns: 'content' })}
      />
      <button
        type="submit"
        className="btn btn-primary"
        disabled={!!busy || !quoteCanSubmit}
      >
        <Plus size={16} /> {busy === 'quote' ? '…' : t('quote.create')}
      </button>
    </form>
  )

  const orderCreatePanel = (
    <form className="form-grid" onSubmit={onCreateOrder}>
      <label>
        {t('order.customer')}
        <CustomerSelect
          value={orderForm.customerId}
          onChange={(id) => setOrderForm((f) => ({ ...f, customerId: id || '' }))}
          disabled={!!busy}
        />
      </label>
      <label>
        {t('order.name')}
        <input
          value={orderForm.name}
          onChange={(e) => setOrderForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <label>
        {t('order.currency', { defaultValue: 'Document currency' })}
        <CurrencySelect
          name="currencyCode"
          value={orderForm.currencyCode}
          onChange={(e) =>
            setOrderForm((f) => ({ ...f, currencyCode: e.target.value || 'EUR' }))
          }
          disabled={!!busy}
          required
        />
      </label>
      {orderWarnings?.length > 0 && (
        <div className="banner-info" role="status">
          {orderWarnings.map((w) => (
            <div key={w.code + w.message}>{w.message}</div>
          ))}
        </div>
      )}
      <DocumentLinesEditor
        forChannel="sale"
        lines={orderLines}
        onChange={setOrderLines}
        disabled={!!busy}
      />
      <PendingContentComposer
        package="sales"
        resource="SalesOrder"
        pending={pendingOrderContent}
        onChange={setPendingOrderContent}
      />
      <button
        type="submit"
        className="btn btn-primary"
        disabled={!!busy || !orderCanSubmit}
      >
        <Plus size={16} /> {busy === 'order' ? '…' : t('order.create')}
      </button>
    </form>
  )

  const customerCreatePanel = (
    <form className="form-grid" onSubmit={onCreateCustomer}>
      <MasterEntityFields
        spec={customerMasterSpec}
        form={customerForm}
        onChange={onMasterField(setCustomerForm)}
        disabled={!!busy}
        showDescription={false}
      />
      <PendingContentComposer
        package="sales"
        resource="Customer"
        pending={pendingCustomerContent}
        onChange={setPendingCustomerContent}
      />
      <button
        type="submit"
        className="btn btn-primary"
        disabled={!!busy || !customerCanSubmit}
      >
        <Plus size={16} /> {busy === 'cust' ? '…' : t('customer.create')}
      </button>
    </form>
  )

  const productCreatePanel = (
    <>
      <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
        {t('product.hint')}
      </p>
      <form className="form-grid" onSubmit={onCreateProduct}>
        <MasterEntityFields
          spec={productMasterSpec}
          form={productForm}
          onChange={onMasterField(setProductForm)}
          disabled={!!busy}
          showDescription={false}
        />
        <PendingContentComposer
          package="catalogue"
          resource="CatalogueItem"
          pending={pendingProductContent}
          onChange={setPendingProductContent}
        />
        <button
          type="submit"
          className="btn btn-primary"
          disabled={!!busy || !productCanSubmit}
        >
          <Plus size={16} /> {t('product.create')}
        </button>
      </form>
    </>
  )

  const serviceCreatePanel = (
    <>
      <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
        {t('service.hint')}
      </p>
      <form className="form-grid" onSubmit={onCreateService}>
        <MasterEntityFields
          spec={serviceMasterSpec}
          form={svcForm}
          onChange={onMasterField(setSvcForm)}
          disabled={!!busy}
          showDescription={false}
        />
        <PendingContentComposer
          package="catalogue"
          resource="CatalogueItem"
          pending={pendingServiceContent}
          onChange={setPendingServiceContent}
        />
        <button
          type="submit"
          className="btn btn-primary"
          disabled={!!busy || !serviceCanSubmit}
        >
          <Plus size={16} /> {t('service.create')}
        </button>
      </form>
    </>
  )

  const newPanel =
    tab === 'quotes'
      ? quoteCreatePanel
      : tab === 'orders'
        ? orderCreatePanel
        : tab === 'customers'
          ? customerCreatePanel
          : tab === 'products'
            ? productCreatePanel
            : tab === 'services'
              ? serviceCreatePanel
              : null

  // —— lists —— (no page search / status filter toolbar — ADR-050; use top-bar FTS)
  const quotesList = (
    <>
      {loading && <p className="muted">{t('actions.loading', { ns: 'common' })}</p>}
      <ResponsiveDataList
        items={quotes}
        empty={t('quote.empty')}
        loading={loading}
        onSelect={(q) => openQuoteView(q)}
        sortFields={QUOTE_SORT_MENU}
        sort={quoteSort}
        order={quoteOrder}
        onSortChange={({ sort: s, order: o }) => {
          setQuoteSort(s || 'id')
          setQuoteOrder(o === 'asc' ? 'asc' : 'desc')
          setQuotePage((pg) => ({ ...pg, offset: 0 }))
        }}
        fields={[
          {
            id: 'number',
            primary: true,
            render: (q) => (
              <>
                <span className="mono">{q.quoteNumber}</span>{' '}
                <span className={statusClass(q.quoteStatus)}>
                  {t(`status.${q.quoteStatus}`, { defaultValue: q.quoteStatus })}
                </span>
              </>
            ),
          },
          {
            id: 'customer',
            secondary: true,
            render: (q) => q.customerName || '—',
          },
          {
            id: 'eur',
            meta: true,
            render: (q) =>
              q.amountEur != null ? `${Number(q.amountEur).toFixed(2)} EUR` : '—',
          },
        ]}
        actions={(q) =>
          renderRowActions('sales', 'CustomerQuote', q, { onView: openQuoteView })
        }
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="quoteNumber"
                  label={t('quote.table.number')}
                  sort={quoteSort}
                  order={quoteOrder}
                  onSortChange={({ sort: s, order: o }) => {
                    setQuoteSort(s || 'id')
                    setQuoteOrder(o === 'asc' ? 'asc' : 'desc')
                    setQuotePage((pg) => ({ ...pg, offset: 0 }))
                  }}
                />
                <ListSortHeader
                  field="customerName"
                  label={t('quote.table.customer')}
                  sort={quoteSort}
                  order={quoteOrder}
                  onSortChange={({ sort: s, order: o }) => {
                    setQuoteSort(s || 'id')
                    setQuoteOrder(o === 'asc' ? 'asc' : 'desc')
                    setQuotePage((pg) => ({ ...pg, offset: 0 }))
                  }}
                />
                <ListSortHeader
                  field="amountEur"
                  label="EUR"
                  sort={quoteSort}
                  order={quoteOrder}
                  onSortChange={({ sort: s, order: o }) => {
                    setQuoteSort(s || 'id')
                    setQuoteOrder(o === 'asc' ? 'asc' : 'desc')
                    setQuotePage((pg) => ({ ...pg, offset: 0 }))
                  }}
                />
                <th className="col-priority-low">{t('quote.table.content')}</th>
                <ListSortHeader
                  field="quoteStatus"
                  label={t('quote.table.status')}
                  sort={quoteSort}
                  order={quoteOrder}
                  onSortChange={({ sort: s, order: o }) => {
                    setQuoteSort(s || 'id')
                    setQuoteOrder(o === 'asc' ? 'asc' : 'desc')
                    setQuotePage((pg) => ({ ...pg, offset: 0 }))
                  }}
                />
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {quotes.length === 0 && (
                <tr>
                  <td colSpan={6} className="muted">
                    {t('quote.empty')}
                  </td>
                </tr>
              )}
              {quotes.map((q) => (
                <tr
                  key={q.id}
                  className="row-click"
                  onClick={() => openQuoteView(q)}
                >
                  <td className="mono">{q.quoteNumber}</td>
                  <td>{q.customerName || '—'}</td>
                  <td>{q.amountEur != null ? Number(q.amountEur).toFixed(2) : '—'}</td>
                  <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                    <ContentChannelIcons summary={quoteContentMap[q.id]} />
                  </td>
                  <td>
                    <span className={statusClass(q.quoteStatus)}>
                      {t(`status.${q.quoteStatus}`, { defaultValue: q.quoteStatus })}
                    </span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('sales', 'CustomerQuote', q, {
                      onView: openQuoteView,
                    })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={quotePage.total}
        max={quotePage.max}
        offset={quotePage.offset}
        disabled={loading}
        onOffset={(o) => setQuotePage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setQuotePage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const ordersList = (
    <>
      {loading && <p className="muted">{t('actions.loading', { ns: 'common' })}</p>}
      <ResponsiveDataList
        items={orders}
        empty={t('order.empty')}
        loading={loading}
        onSelect={(o) => openOrderView(o)}
        sortFields={ORDER_SORT_MENU}
        sort={orderSort}
        order={orderOrder}
        onSortChange={({ sort: s, order: o }) => {
          setOrderSort(s || 'id')
          setOrderOrder(o === 'asc' ? 'asc' : 'desc')
          setOrderPage((pg) => ({ ...pg, offset: 0 }))
        }}
        fields={[
          {
            id: 'number',
            primary: true,
            render: (o) => (
              <>
                <span className="mono">{o.orderNumber}</span>{' '}
                <span className={statusClass(o.orderStatus)}>{o.orderStatus}</span>
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
        ]}
        actions={(o) =>
          renderRowActions('sales', 'SalesOrder', o, { onView: openOrderView })
        }
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="orderNumber"
                  label={t('order.table.number')}
                  sort={orderSort}
                  order={orderOrder}
                  onSortChange={({ sort: s, order: o }) => {
                    setOrderSort(s || 'id')
                    setOrderOrder(o === 'asc' ? 'asc' : 'desc')
                    setOrderPage((pg) => ({ ...pg, offset: 0 }))
                  }}
                />
                <ListSortHeader
                  field="customerName"
                  label={t('order.table.customer')}
                  sort={orderSort}
                  order={orderOrder}
                  onSortChange={({ sort: s, order: o }) => {
                    setOrderSort(s || 'id')
                    setOrderOrder(o === 'asc' ? 'asc' : 'desc')
                    setOrderPage((pg) => ({ ...pg, offset: 0 }))
                  }}
                />
                <ListSortHeader
                  field="amountEur"
                  label="EUR"
                  sort={orderSort}
                  order={orderOrder}
                  onSortChange={({ sort: s, order: o }) => {
                    setOrderSort(s || 'id')
                    setOrderOrder(o === 'asc' ? 'asc' : 'desc')
                    setOrderPage((pg) => ({ ...pg, offset: 0 }))
                  }}
                />
                <ListSortHeader
                  field="orderStatus"
                  label={t('order.table.status')}
                  sort={orderSort}
                  order={orderOrder}
                  onSortChange={({ sort: s, order: o }) => {
                    setOrderSort(s || 'id')
                    setOrderOrder(o === 'asc' ? 'asc' : 'desc')
                    setOrderPage((pg) => ({ ...pg, offset: 0 }))
                  }}
                />
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {orders.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">
                    {t('order.empty')}
                  </td>
                </tr>
              )}
              {orders.map((o) => (
                <tr
                  key={o.id}
                  className="row-click"
                  onClick={() => openOrderView(o)}
                >
                  <td className="mono">{o.orderNumber}</td>
                  <td>{o.customerName || '—'}</td>
                  <td>
                    {o.amountEur != null ? Number(o.amountEur).toFixed(2).toString() : '—'}
                  </td>
                  <td>
                    <span className={statusClass(o.orderStatus)}>{o.orderStatus}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('sales', 'SalesOrder', o, {
                      onView: openOrderView,
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

  const customersList = (
    <>
      {loading && <p className="muted">{t('actions.loading', { ns: 'common' })}</p>}
      <ResponsiveDataList
        items={customers}
        empty={t('customer.empty')}
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
                <th>{t('customer.table.name')}</th>
                <th className="col-priority-low">{t('customer.table.kind')}</th>
                <th>{t('customer.table.email')}</th>
                <th className="col-priority-low">{t('customer.table.content')}</th>
                <th className="col-priority-low">{t('wb.ccy', { ns: 'common' })}</th>
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {customers.length === 0 && (
                <tr>
                  <td colSpan={6} className="muted">
                    {t('customer.empty')}
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
                  <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
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

  const productsList = (
    <>
      <ResponsiveDataList
        items={products}
        empty={t('product.empty')}
        onSelect={(p) => openView('catalogue', 'CatalogueItem', p)}
        fields={[
          { id: 'name', primary: true, render: (p) => p.name },
          {
            id: 'sku',
            secondary: true,
            render: (p) => <span className="mono">{p.code || p.sku || '—'}</span>,
          },
          { id: 'type', meta: true, render: (p) => p.nature || 'GOODS' },
        ]}
        actions={(p) => renderRowActions('catalogue', 'CatalogueItem', p)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('wb.name', { ns: 'common' })}</th>
                <th>{t('product.sku')}</th>
                <th className="col-priority-low">
                  {t('wb.type', { ns: 'common', defaultValue: 'Type' })}
                </th>
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr
                  key={p.id}
                  className="row-click"
                  onClick={() => openView('catalogue', 'CatalogueItem', p)}
                >
                  <td>{p.name}</td>
                  <td className="mono">{p.code || p.sku || '—'}</td>
                  <td className="muted col-priority-low">{p.nature || 'GOODS'}</td>
                  <td
                    style={{ whiteSpace: 'nowrap' }}
                    onClick={(e) => e.stopPropagation()}
                  >
                    {renderRowActions('catalogue', 'CatalogueItem', p)}
                  </td>
                </tr>
              ))}
              {!products.length && (
                <tr>
                  <td colSpan={4} className="muted">
                    {t('product.empty')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={prodPage.total}
        max={prodPage.max}
        offset={prodPage.offset}
        disabled={loading}
        onOffset={(o) => setProdPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setProdPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const servicesList = (
    <>
      <ResponsiveDataList
        items={services}
        empty={t('service.empty')}
        onSelect={(s) => openView('catalogue', 'CatalogueItem', s)}
        fields={[
          { id: 'name', primary: true, render: (s) => s.name },
          {
            id: 'eur',
            secondary: true,
            render: (s) =>
              (s.listPrice ?? s.listPriceEur) != null
                ? `${Number(s.listPrice ?? s.listPriceEur).toFixed(2)} EUR`
                : '—',
          },
          {
            id: 'unit',
            meta: true,
            label: t('wb.unit', { ns: 'common' }),
            render: (s) => s.unit || '—',
          },
        ]}
        actions={(s) => renderRowActions('catalogue', 'CatalogueItem', s)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('wb.name', { ns: 'common' })}</th>
                <th className="col-priority-low">{t('wb.unit', { ns: 'common' })}</th>
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
                  <td className="col-priority-low">{s.unit}</td>
                  <td>
                    {(s.listPrice ?? s.listPriceEur) != null
                      ? Number(s.listPrice ?? s.listPriceEur).toFixed(2)
                      : '—'}
                  </td>
                  <td
                    style={{ whiteSpace: 'nowrap' }}
                    onClick={(e) => e.stopPropagation()}
                  >
                    {renderRowActions('catalogue', 'CatalogueItem', s)}
                  </td>
                </tr>
              ))}
              {!services.length && (
                <tr>
                  <td colSpan={4} className="muted">
                    {t('service.empty')}
                  </td>
                </tr>
              )}
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

  const list =
    tab === 'quotes'
      ? quotesList
      : tab === 'orders'
        ? ordersList
        : tab === 'customers'
          ? customersList
          : tab === 'products'
            ? productsList
            : tab === 'services'
              ? servicesList
              : null

  const toolbar = null

  getBelowFieldsRef.current = ({ resource, record }) => {
    if (!record) return null
    if (resource === 'CustomerQuote') {
      const live = selectedQuoteRef.current
      const q = live?.id === record.id ? live : record
      const warnings =
        q.warnings || (live?.id === record.id ? quoteWarningsRef.current : null)
      return (
        <>
          {(warnings || []).length > 0 && (
            <div className="banner-info" role="status" style={{ marginBottom: 8 }}>
              {warnings.map((w) => (
                <div key={w.code + w.message}>{w.message}</div>
              ))}
            </div>
          )}
          <h3 className="section-label">{t('quote.lines')}</h3>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t('quote.lineNo')}</th>
                  <th>{t('quote.lineName')}</th>
                  <th>{t('quote.lineQty')}</th>
                  <th>{t('quote.lineUnit')}</th>
                  <th>{t('quote.lineTotal')}</th>
                  <th>{t('docLines.taxCode', { defaultValue: 'Tax code' })}</th>
                  <th>{t('quote.vat', { defaultValue: 'VAT' })}</th>
                </tr>
              </thead>
              <tbody>
                {(q.lines || []).length === 0 && (
                  <tr>
                    <td colSpan={7} className="muted">
                      {t('quote.noLines')}
                    </td>
                  </tr>
                )}
                {(q.lines || []).map((l) => (
                  <tr key={l.id || l.lineNo}>
                    <td>{l.lineNo}</td>
                    <td>{l.name}</td>
                    <td>{l.quantity}</td>
                    <td>{Number(l.unitPriceEur).toFixed(2)}</td>
                    <td>{Number(l.lineTotalEur).toFixed(2)}</td>
                    <td className="mono col-priority-low">{l.taxCode || '—'}</td>
                    <td>
                      {l.taxAmountEur != null ? Number(l.taxAmountEur).toFixed(2) : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {collapsedContentRail('sales', 'CustomerQuote', q.id, async () =>
            setQuoteContentMap(
              await fetchContentMap('sales', 'CustomerQuote', quotesRef.current),
            ),
          )}
        </>
      )
    }
    if (resource === 'SalesOrder') {
      const live = selectedOrderRef.current
      const o = live?.id === record.id ? live : record
      const warnings =
        o.warnings || (live?.id === record.id ? orderWarningsRef.current : null)
      return (
        <>
          {(warnings || []).length > 0 && (
            <div className="banner-info" role="status" style={{ marginBottom: 8 }}>
              {warnings.map((w) => (
                <div key={w.code + w.message}>{w.message}</div>
              ))}
            </div>
          )}
          <h3 className="section-label">{t('quote.lines')}</h3>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>#</th>
                  <th>{t('quote.lineName')}</th>
                  <th>Qty</th>
                  <th>{t('quote.lineUnit')}</th>
                  <th>{t('quote.lineTotal')}</th>
                  <th>{t('docLines.taxCode', { defaultValue: 'Tax code' })}</th>
                  <th>{t('quote.vat', { defaultValue: 'VAT' })}</th>
                </tr>
              </thead>
              <tbody>
                {(o.lines || []).length === 0 && (
                  <tr>
                    <td colSpan={7} className="muted">
                      {t('quote.noLines')}
                    </td>
                  </tr>
                )}
                {(o.lines || []).map((l) => (
                  <tr key={l.id || l.lineNo}>
                    <td>{l.lineNo}</td>
                    <td>{l.name}</td>
                    <td>{l.quantity}</td>
                    <td>{Number(l.unitPriceEur).toFixed(2)}</td>
                    <td>{Number(l.lineTotalEur).toFixed(2)}</td>
                    <td className="mono col-priority-low">{l.taxCode || '—'}</td>
                    <td>
                      {l.taxAmountEur != null ? Number(l.taxAmountEur).toFixed(2) : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {collapsedContentRail('sales', 'SalesOrder', o.id, async () =>
            setOrderContentMap(
              await fetchContentMap('sales', 'SalesOrder', ordersRef.current),
            ),
          )}
        </>
      )
    }
    if (resource === 'Customer') {
      return collapsedContentRail('sales', 'Customer', record.id, async () =>
        setCustomerContentMap(
          await fetchContentMap('sales', 'Customer', customers),
        ),
      )
    }
    if (resource === 'CatalogueItem') {
      const isService = record.nature === 'SERVICE'
      return collapsedContentRail('catalogue', 'CatalogueItem', record.id, async () => {
        if (isService) {
          setServiceContentMap(
            await fetchContentMap('catalogue', 'CatalogueItem', services),
          )
        } else {
          setProductContentMap(
            await fetchContentMap('catalogue', 'CatalogueItem', products),
          )
        }
      })
    }
    return null
  }

  getFooterExtraRef.current = ({ resource, record }) => {
    if (!record) return null
    const isBusy = !!busyRef.current
    if (resource === 'CustomerQuote') {
      const live = selectedQuoteRef.current
      const q = live?.id === record.id ? live : record
      return (
        <>
          {q.quoteStatus === 'DRAFT' && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => onSendQuoteRef.current()}
              disabled={isBusy}
            >
              <Send size={16} /> {busyRef.current === 'send' ? '…' : t('quote.send')}
            </button>
          )}
          {['DRAFT', 'SENT'].includes(q.quoteStatus) && (
            <button
              type="button"
              className={q.quoteStatus === 'SENT' ? 'btn btn-primary' : 'btn btn-ghost'}
              onClick={() => onAcceptQuoteRef.current()}
              disabled={isBusy}
              title={t('quote.acceptToOrderTitle', { defaultValue: 'Accept → order' })}
            >
              <Check size={16} />{' '}
              {busyRef.current === 'accept' ? '…' : t('quote.acceptToOrder')}
            </button>
          )}
          {['DRAFT', 'SENT'].includes(q.quoteStatus) && (
            <button
              type="button"
              className="btn"
              onClick={() => onRejectQuoteRef.current()}
              disabled={isBusy}
            >
              <X size={16} /> {t('quote.reject')}
            </button>
          )}
          {q.resultingOrderId && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                deepLinkOpened.current = String(q.resultingOrderId)
                setTab('orders')
                setSelectedId(String(q.resultingOrderId))
              }}
            >
              Open{' '}
              {labelFromList(ordersRef.current, q.resultingOrderId, {
                fallback: 'order',
              })}
            </button>
          )}
          <a
            className="btn"
            href={salesApi.quotes.pdfUrl(q.id)}
            target="_blank"
            rel="noreferrer"
            title={t('quote.downloadPdfTitle', {
              defaultValue: 'Download quote PDF',
            })}
          >
            <FileText size={16} /> {t('actions.pdf', { ns: 'common', defaultValue: 'PDF' })}
          </a>
        </>
      )
    }
    if (resource === 'SalesOrder') {
      const live = selectedOrderRef.current
      const o = live?.id === record.id ? live : record
      return (
        <>
          {o.orderStatus === 'DRAFT' && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => onConfirmOrderRef.current()}
              disabled={isBusy}
              title={t('order.confirmFinanceTitle', {
                defaultValue: 'Confirm → Finance DRAFT invoice',
              })}
            >
              <Check size={16} />{' '}
              {busyRef.current === 'confirm' ? '…' : t('order.confirmFinance')}
            </button>
          )}
          {o.orderStatus === 'DRAFT' && (
            <button
              type="button"
              className="btn"
              onClick={() => onCancelOrderRef.current()}
              disabled={isBusy}
            >
              <Ban size={16} /> Cancel
            </button>
          )}
          {o.financeInvoiceId && (
            <Link
              className="btn btn-secondary"
              to="/finance/invoices"
              title={t('order.openFinanceInvoice', {
                defaultValue: 'Open finance invoice',
              })}
            >
              {t('order.financeInvoiceShort', { defaultValue: 'Invoice →' })}
            </Link>
          )}
          {o.sourceQuoteId && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                deepLinkOpened.current = String(o.sourceQuoteId)
                setTab('quotes')
                setSelectedId(String(o.sourceQuoteId))
              }}
            >
              From quote{' '}
              {labelFromList(quotesRef.current, o.sourceQuoteId, {
                fallback: o.sourceQuoteNumber || '—',
              })}
            </button>
          )}
          <a
            className="btn"
            href={salesApi.orders.pdfUrl(o.id)}
            target="_blank"
            rel="noreferrer"
            title={t('order.downloadPdfTitle', {
              defaultValue: 'Download sales order PDF',
            })}
          >
            <FileText size={16} /> {t('actions.pdf', { ns: 'common', defaultValue: 'PDF' })}
          </a>
        </>
      )
    }
    return null
  }

  const emptyTitle = t('desk.emptyTitle', { ns: 'common' })
  const emptyBody = t('desk.emptyBodyMasters', {
    ns: 'common',
    defaultValue: 'Use + New to create, or View on a row for the full record.',
  })

  // silence unused picker list (kept for future typeahead)
  void customerOptions

  return (
    <div className="page sales-desk-page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}

      <div className="toolbar" style={{ marginBottom: '1.5rem' }}>
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <Link className="btn btn-secondary" to="/finance/invoices">
          {t('desk.financeInvoices')}
        </Link>
        {showAdvanced && (
          <>
            <Link className="btn btn-secondary" to="/sales/pricing">
              {t('features.pricing', { ns: 'common', defaultValue: 'Price lists' })}
            </Link>
            <Link className="btn btn-secondary" to="/sales/opportunities">
              {t('features.crm', { ns: 'common', defaultValue: 'Opportunities' })}
            </Link>
          </>
        )}
      </div>

      <DeskShell
        layout="single"
        title={
          <>
            <ShoppingCart size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('desk.title')}
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
        toolbar={toolbar}
        list={list}
        hasSelection={false}
        emptyTitle={emptyTitle}
        emptyBody={emptyBody}
        detail={null}
        detailFooter={null}
        processSlot={null}
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
