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
} from 'lucide-react'
import { contentApi, salesApi, catalogueApi, meApi } from '../api/client'
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
import ProcessHowItWorks from '../components/ProcessHowItWorks'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import ResponsiveDataList from '../components/ResponsiveDataList'
import DeskNewFab from '../components/DeskNewFab'
import ListPager from '../components/ListPager'
import CustomerSelect from '../components/CustomerSelect'
import DeskShell from '../components/desk/DeskShell'
import ConfirmDialog from '../components/ConfirmDialog'
import useDeskSelection, { scrollSelectedIntoView } from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useNarrowViewport from '../hooks/useNarrowViewport'
import { activeStepIndex } from '../config/processStatusMaps'

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
 * DeskShell layout (ADR-047 / Nielsen WP-0, WP-2, WP-6).
 */
export default function SalesDesk() {
  const { t } = useTranslation(['sales', 'common', 'content'])
  const { viewModal, ViewButton } = useRecordView()
  const narrow = useNarrowViewport()
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
  const [quoteFilter, setQuoteFilter] = useState('')
  const [orderFilter, setOrderFilter] = useState('')
  const [showAdvanced, setShowAdvanced] = useState(false)

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
  const [quoteContentMap, setQuoteContentMap] = useState({})
  const [orderContentMap, setOrderContentMap] = useState({})
  const [customerContentMap, setCustomerContentMap] = useState({})

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

  const loadAll = useCallback(async () => {
    const [c, cPick, q, o, p, s] = await Promise.all([
      salesApi.customers.list({ max: custPage.max, offset: custPage.offset }),
      salesApi.customers.list({ max: 100, offset: 0 }),
      salesApi.quotes.list({
        status: quoteFilter || undefined,
        max: quotePage.max,
        offset: quotePage.offset,
      }),
      salesApi.orders.list({
        status: orderFilter || undefined,
        max: orderPage.max,
        offset: orderPage.offset,
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
    const [qMap, oMap, cMap] = await Promise.all([
      fetchContentMap('sales', 'CustomerQuote', qs),
      fetchContentMap('sales', 'SalesOrder', os),
      fetchContentMap('sales', 'Customer', cust),
    ])
    setQuoteContentMap(qMap)
    setOrderContentMap(oMap)
    setCustomerContentMap(cMap)
    return { customers: cust, quotes: qs, orders: os }
  }, [
    quoteFilter,
    orderFilter,
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

  const selectQuote = useCallback(async (id) => {
    if (!id) {
      setSelectedQuote(null)
      setQuoteWarnings([])
      return
    }
    const full = await salesApi.quotes.get(id)
    setSelectedQuote(full)
    setQuoteWarnings(Array.isArray(full?.warnings) ? full.warnings : [])
  }, [])

  const selectOrder = useCallback(async (id) => {
    if (!id) {
      setSelectedOrder(null)
      setOrderWarnings([])
      return
    }
    const full = await salesApi.orders.get(id)
    setSelectedOrder(full)
    setOrderWarnings(Array.isArray(full?.warnings) ? full.warnings : [])
  }, [])

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      try {
        if (tab === 'quotes') {
          if (selectedId) {
            await selectQuote(selectedId)
          } else if (!cancelled) {
            setSelectedQuote(null)
            setQuoteWarnings([])
          }
          if (!cancelled) setSelectedOrder(null)
        } else if (tab === 'orders') {
          if (selectedId) {
            await selectOrder(selectedId)
          } else if (!cancelled) {
            setSelectedOrder(null)
            setOrderWarnings([])
          }
          if (!cancelled) setSelectedQuote(null)
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
  }, [tab, selectedId, selectQuote, selectOrder])

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
    setProductForm(productMasterSpec.empty())
    setSvcForm(serviceMasterSpec.empty())
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
      return Boolean((customerForm.name || '').trim() || customerForm.email || customerForm.phone)
        || customerForm.customerKind !== empty.customerKind
        || customerForm.currencyCode !== empty.currencyCode
    }
    if (tab === 'products') {
      return Boolean((productForm.name || '').trim() || (productForm.sku || '').trim())
    }
    if (tab === 'services') {
      return Boolean((svcForm.name || '').trim() || (svcForm.code || '').trim())
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
    productForm,
    svcForm,
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
      setSelectedId(String(q.id))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onSendQuote = async () => {
    if (!selectedQuote?.id) return
    setBusy('send')
    setError(null)
    setInfo(null)
    try {
      const q = await salesApi.quotes.send(selectedQuote.id)
      setInfo(t('toast.sent', { ns: 'common', number: q.quoteNumber }))
      await loadAll()
      await selectQuote(q.id)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onAcceptQuote = async () => {
    if (!selectedQuote?.id) return
    setBusy('accept')
    setError(null)
    setInfo(null)
    try {
      const result = await salesApi.quotes.accept(selectedQuote.id)
      const order = result.order
      setInfo(
        `Accepted ${result.quote?.quoteNumber}` +
          (order ? ` → order ${order.orderNumber}` : '') +
          (result.alreadyAccepted ? ' (already)' : ''),
      )
      await loadAll()
      if (order?.id) {
        setTab('orders')
        setSelectedId(String(order.id))
      } else {
        await selectQuote(selectedQuote.id)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onRejectQuote = async () => {
    if (!selectedQuote?.id) return
    setBusy('reject')
    setError(null)
    setInfo(null)
    try {
      const q = await salesApi.quotes.reject(selectedQuote.id)
      setInfo(t('toast.rejected', { ns: 'common', name: q.quoteNumber }))
      await loadAll()
      await selectQuote(q.id)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

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
      setSelectedId(String(o.id))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onConfirmOrder = async () => {
    if (!selectedOrder?.id) return
    setBusy('confirm')
    setError(null)
    setInfo(null)
    try {
      const result = await salesApi.orders.confirm(selectedOrder.id)
      const invId = result.financeInvoiceId
      setInfo(
        `Confirmed ${result.order?.orderNumber}` +
          (invId ? ` → Finance DRAFT invoice #${invId}` : '') +
          (result.alreadyConfirmed ? ' (already)' : ''),
      )
      await loadAll()
      await selectOrder(selectedOrder.id)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCancelOrder = async () => {
    if (!selectedOrder?.id) return
    if (!window.confirm(`${t('order.cancel')} ${selectedOrder.orderNumber}?`)) return
    setBusy('cancel')
    setError(null)
    setInfo(null)
    try {
      const o = await salesApi.orders.cancel(selectedOrder.id)
      setInfo(t('toast.cancelled', { ns: 'common', number: o.orderNumber }))
      await loadAll()
      await selectOrder(o.id)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

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

  const hasSelection = useMemo(() => {
    if (tab === 'quotes') return Boolean(selectedQuote?.id)
    if (tab === 'orders') return Boolean(selectedOrder?.id)
    return false
  }, [tab, selectedQuote?.id, selectedOrder?.id])

  const helpContext = useMemo(() => {
    const base = { desk: 'sales', tab }
    if (tab === 'quotes' && selectedQuote) {
      return {
        ...base,
        resource: 'CustomerQuote',
        objectId: selectedQuote.id,
        status: selectedQuote.quoteStatus,
        processCode: 'SALES_QUOTE_TO_ORDER',
      }
    }
    if (tab === 'orders' && selectedOrder) {
      return {
        ...base,
        resource: 'SalesOrder',
        objectId: selectedOrder.id,
        status: selectedOrder.orderStatus,
        processCode: 'SALES_QUOTE_TO_ORDER',
      }
    }
    return base
  }, [tab, selectedQuote, selectedOrder])

  const processStatus =
    tab === 'quotes'
      ? selectedQuote?.quoteStatus
      : tab === 'orders'
        ? selectedOrder?.orderStatus
        : null
  const processStep =
    processStatus != null ? activeStepIndex('SALES_QUOTE_TO_ORDER', processStatus) : -1

  const processSlot =
    tab === 'quotes' || tab === 'orders' ? (
      <div className="toolbar" style={{ gap: 8, flexWrap: 'wrap' }}>
        <ProcessHowItWorks processCode="SALES_QUOTE_TO_ORDER" />
        {processStep >= 0 && (
          <span className="muted" style={{ fontSize: '0.85rem' }}>
            {t('desk.processStep', {
              ns: 'common',
              defaultValue: 'Step {{n}}',
              n: processStep + 1,
            })}{' '}
            · {processStatus}
          </span>
        )}
      </div>
    ) : null

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

  // —— toolbars ——
  const quoteToolbar = (
    <select
      value={quoteFilter}
      onChange={(e) => {
        setQuoteFilter(e.target.value)
        setQuotePage((pg) => ({ ...pg, offset: 0 }))
      }}
      className="filter-select"
    >
      <option value="">{t('quote.filterAll')}</option>
      {['DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED'].map((s) => (
        <option key={s} value={s}>
          {t(`status.${s}`, { defaultValue: s })}
        </option>
      ))}
    </select>
  )

  const orderToolbar = (
    <select
      value={orderFilter}
      onChange={(e) => {
        setOrderFilter(e.target.value)
        setOrderPage((pg) => ({ ...pg, offset: 0 }))
      }}
      className="filter-select"
    >
      <option value="">{t('quote.filterAll')}</option>
      {['DRAFT', 'CONFIRMED', 'CLOSED', 'CANCELLED'].map((s) => (
        <option key={s} value={s}>
          {t(`status.${s}`, { defaultValue: s })}
        </option>
      ))}
    </select>
  )

  // —— lists ——
  const quotesList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('quote.list')} ({quotes.length})
      </h2>
      {loading && <p className="muted">{t('actions.loading', { ns: 'common' })}</p>}
      <ResponsiveDataList
        items={quotes}
        empty={t('quote.empty')}
        loading={loading}
        selectedKey={selectedQuote?.id}
        onSelect={(q) => setSelectedId(String(q.id))}
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
        actions={(q) => <ViewButton pkg="sales" resource="CustomerQuote" record={q} />}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('quote.table.number')}</th>
                <th>{t('quote.table.customer')}</th>
                <th>EUR</th>
                <th className="col-priority-low">{t('quote.table.content')}</th>
                <th>{t('quote.table.status')}</th>
                <th />
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
                  data-selected={selectedQuote?.id === q.id ? 'true' : undefined}
                  className={selectedQuote?.id === q.id ? 'row-selected' : 'row-click'}
                  onClick={() => setSelectedId(String(q.id))}
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
                    <ViewButton pkg="sales" resource="CustomerQuote" record={q} />
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
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('order.list')} ({orders.length})
      </h2>
      {loading && <p className="muted">{t('actions.loading', { ns: 'common' })}</p>}
      <ResponsiveDataList
        items={orders}
        empty={t('order.empty')}
        loading={loading}
        selectedKey={selectedOrder?.id}
        onSelect={(o) => setSelectedId(String(o.id))}
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
        actions={(o) => <ViewButton pkg="sales" resource="SalesOrder" record={o} />}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('order.table.number')}</th>
                <th>{t('order.table.customer')}</th>
                <th>EUR</th>
                <th>{t('order.table.status')}</th>
                <th />
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
                  data-selected={selectedOrder?.id === o.id ? 'true' : undefined}
                  className={selectedOrder?.id === o.id ? 'row-selected' : 'row-click'}
                  onClick={() => setSelectedId(String(o.id))}
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
                    <ViewButton pkg="sales" resource="SalesOrder" record={o} />
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
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('customer.list')} ({customers.length})
      </h2>
      {loading && <p className="muted">{t('actions.loading', { ns: 'common' })}</p>}
      <ResponsiveDataList
        items={customers}
        empty={t('customer.empty')}
        loading={loading}
        fields={[
          { id: 'name', primary: true, render: (c) => c.name },
          { id: 'email', secondary: true, render: (c) => c.email || '—' },
          { id: 'kind', priority: 'low', render: (c) => c.customerKind },
          { id: 'ccy', priority: 'low', render: (c) => c.currencyCode },
        ]}
        actions={(c) => <ViewButton pkg="sales" resource="Customer" record={c} />}
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
                <th />
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
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td className="col-priority-low">{c.customerKind}</td>
                  <td className="muted">{c.email || '—'}</td>
                  <td className="col-priority-low">
                    <ContentChannelIcons summary={customerContentMap[c.id]} />
                  </td>
                  <td className="mono col-priority-low">{c.currencyCode}</td>
                  <td>
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
    </>
  )

  const productsList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('product.list')} ({prodPage.total || products.length})
      </h2>
      <ResponsiveDataList
        items={products}
        empty={t('product.empty')}
        fields={[
          { id: 'name', primary: true, render: (p) => p.name },
          {
            id: 'sku',
            secondary: true,
            render: (p) => <span className="mono">{p.code || p.sku || '—'}</span>,
          },
          { id: 'type', meta: true, render: (p) => p.nature || 'GOODS' },
        ]}
        actions={(p) => <ViewButton pkg="catalogue" resource="CatalogueItem" record={p} />}
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
                <th />
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  <td className="mono">{p.code || p.sku || '—'}</td>
                  <td className="muted col-priority-low">{p.nature || 'GOODS'}</td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    <ViewButton pkg="catalogue" resource="CatalogueItem" record={p} />
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
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('service.list')} ({svcPage.total || services.length})
      </h2>
      <ResponsiveDataList
        items={services}
        empty={t('service.empty')}
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
        actions={(s) => <ViewButton pkg="catalogue" resource="CatalogueItem" record={s} />}
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

  const toolbar =
    tab === 'quotes' ? quoteToolbar : tab === 'orders' ? orderToolbar : null

  // —— detail + footer ——
  let detail = null
  let detailFooter = null

  if (tab === 'quotes' && selectedQuote) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <div className="inv-detail-hero">
          <div>
            <div className="about-display" style={{ fontSize: '1.35rem' }}>
              {selectedQuote.quoteNumber}
            </div>
            <div className="muted">
              {selectedQuote.customerName || t('quote.noCustomer')} ·{' '}
              <span className={statusClass(selectedQuote.quoteStatus)}>
                {t(`status.${selectedQuote.quoteStatus}`, {
                  defaultValue: selectedQuote.quoteStatus,
                })}
              </span>
              {selectedQuote.currencyCode ? ` · ${selectedQuote.currencyCode}` : ''}
            </div>
          </div>
          <div className="inv-amount">
            {selectedQuote.netAmountEur != null || selectedQuote.taxAmountEur != null ? (
              <div style={{ textAlign: 'right', fontSize: '0.85rem' }}>
                <div className="muted">
                  {t('quote.net', { defaultValue: 'Net' })}{' '}
                  {Number(selectedQuote.netAmountEur ?? 0).toFixed(2)}
                </div>
                <div className="muted">
                  {t('quote.vat', { defaultValue: 'VAT' })}{' '}
                  {Number(selectedQuote.taxAmountEur ?? 0).toFixed(2)}
                </div>
                <div>
                  <strong>
                    {t('quote.gross', { defaultValue: 'Gross' })}{' '}
                    {Number(selectedQuote.amountEur || 0).toFixed(2)}{' '}
                    {selectedQuote.currencyCode || 'EUR'}
                  </strong>
                </div>
              </div>
            ) : (
              <>
                {Number(selectedQuote.amountEur || 0).toFixed(2)}{' '}
                {selectedQuote.currencyCode || 'EUR'}
              </>
            )}
            {selectedQuote.amountSats != null && (
              <div className="muted mono" style={{ fontSize: '0.8rem' }}>
                {selectedQuote.amountSats} sats
              </div>
            )}
          </div>
        </div>
        {(selectedQuote.warnings || quoteWarnings)?.length > 0 && (
          <div className="banner-info" role="status" style={{ marginBottom: 8 }}>
            {(selectedQuote.warnings || quoteWarnings).map((w) => (
              <div key={w.code + w.message}>{w.message}</div>
            ))}
          </div>
        )}

        <div className="toolbar">
          {['DRAFT', 'SENT'].includes(selectedQuote.quoteStatus) && (
            <button type="button" className="btn" onClick={onRejectQuote} disabled={!!busy}>
              <X size={16} /> {t('quote.reject')}
            </button>
          )}
          {selectedQuote.resultingOrderId && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setTab('orders')
                setSelectedId(String(selectedQuote.resultingOrderId))
              }}
            >
              {t('quote.openOrder', { id: selectedQuote.resultingOrderId })}
            </button>
          )}
          <a
            className="btn"
            href={salesApi.quotes.pdfUrl(selectedQuote.id)}
            target="_blank"
            rel="noreferrer"
          >
            <FileText size={16} /> Download quote PDF
          </a>
        </div>

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
              {(selectedQuote.lines || []).length === 0 && (
                <tr>
                  <td colSpan={7} className="muted">
                    {t('quote.noLines')}
                  </td>
                </tr>
              )}
              {(selectedQuote.lines || []).map((l) => (
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

        {collapsedContentRail('sales', 'CustomerQuote', selectedQuote.id, async () =>
          setQuoteContentMap(await fetchContentMap('sales', 'CustomerQuote', quotes)),
        )}
      </div>
    )

    if (['DRAFT', 'SENT'].includes(selectedQuote.quoteStatus)) {
      const primaryIsSend = selectedQuote.quoteStatus === 'DRAFT'
      detailFooter = (
        <>
          {primaryIsSend ? (
            <button
              type="button"
              className="btn btn-primary"
              onClick={onSendQuote}
              disabled={!!busy}
            >
              <Send size={16} /> {busy === 'send' ? '…' : t('quote.send')}
            </button>
          ) : (
            <button
              type="button"
              className="btn btn-primary"
              onClick={onAcceptQuote}
              disabled={!!busy}
            >
              <Check size={16} /> {busy === 'accept' ? '…' : t('quote.acceptToOrder')}
            </button>
          )}
          {primaryIsSend && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={onAcceptQuote}
              disabled={!!busy}
            >
              <Check size={16} /> {t('quote.acceptToOrder')}
            </button>
          )}
        </>
      )
    }
  }

  if (tab === 'orders' && selectedOrder) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <div className="inv-detail-hero">
          <div>
            <div className="about-display" style={{ fontSize: '1.35rem' }}>
              {selectedOrder.orderNumber}
            </div>
            <div className="muted">
              {selectedOrder.customerName || t('quote.noCustomer')} ·{' '}
              <span className={statusClass(selectedOrder.orderStatus)}>
                {selectedOrder.orderStatus}
              </span>
              {selectedOrder.currencyCode ? ` · ${selectedOrder.currencyCode}` : ''}
            </div>
            {selectedOrder.sourceQuoteId && (
              <div className="muted" style={{ fontSize: '0.85rem' }}>
                From quote #{selectedOrder.sourceQuoteId}
              </div>
            )}
          </div>
          <div className="inv-amount">
            {selectedOrder.netAmountEur != null || selectedOrder.taxAmountEur != null ? (
              <div style={{ textAlign: 'right', fontSize: '0.85rem' }}>
                <div className="muted">
                  {t('quote.net', { defaultValue: 'Net' })}{' '}
                  {Number(selectedOrder.netAmountEur ?? 0).toFixed(2)}
                </div>
                <div className="muted">
                  {t('quote.vat', { defaultValue: 'VAT' })}{' '}
                  {Number(selectedOrder.taxAmountEur ?? 0).toFixed(2)}
                </div>
                <div>
                  <strong>
                    {t('quote.gross', { defaultValue: 'Gross' })}{' '}
                    {Number(selectedOrder.amountEur || 0).toFixed(2)}{' '}
                    {selectedOrder.currencyCode || 'EUR'}
                  </strong>
                </div>
              </div>
            ) : (
              <>
                {Number(selectedOrder.amountEur || 0).toFixed(2)}{' '}
                {selectedOrder.currencyCode || 'EUR'}
              </>
            )}
            {selectedOrder.amountSats != null && (
              <div className="muted mono" style={{ fontSize: '0.8rem' }}>
                {selectedOrder.amountSats} sats
              </div>
            )}
          </div>
        </div>
        {(selectedOrder.warnings || orderWarnings)?.length > 0 && (
          <div className="banner-info" role="status" style={{ marginBottom: 8 }}>
            {(selectedOrder.warnings || orderWarnings).map((w) => (
              <div key={w.code + w.message}>{w.message}</div>
            ))}
          </div>
        )}

        <div className="toolbar">
          {selectedOrder.orderStatus === 'DRAFT' && (
            <button type="button" className="btn" onClick={onCancelOrder} disabled={!!busy}>
              <Ban size={16} /> Cancel
            </button>
          )}
          {selectedOrder.financeInvoiceId && (
            <Link
              className="btn btn-secondary"
              to="/finance/invoices"
              title={`Invoice id ${selectedOrder.financeInvoiceId}`}
            >
              Finance invoice #{selectedOrder.financeInvoiceId} →
            </Link>
          )}
          <a
            className="btn"
            href={salesApi.orders.pdfUrl(selectedOrder.id)}
            target="_blank"
            rel="noreferrer"
          >
            <FileText size={16} /> Download sales order PDF
          </a>
        </div>

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
              {(selectedOrder.lines || []).length === 0 && (
                <tr>
                  <td colSpan={7} className="muted">
                    {t('quote.noLines')}
                  </td>
                </tr>
              )}
              {(selectedOrder.lines || []).map((l) => (
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

        {collapsedContentRail('sales', 'SalesOrder', selectedOrder.id, async () =>
          setOrderContentMap(await fetchContentMap('sales', 'SalesOrder', orders)),
        )}
      </div>
    )

    if (selectedOrder.orderStatus === 'DRAFT') {
      detailFooter = (
        <button
          type="button"
          className="btn btn-primary"
          onClick={onConfirmOrder}
          disabled={!!busy}
        >
          <Check size={16} /> {busy === 'confirm' ? '…' : t('order.confirmFinance')}
        </button>
      )
    }
  }

  const emptyTitle =
    tab === 'quotes'
      ? t('quote.selectHint')
      : tab === 'orders'
        ? t('order.selectHint')
        : t('desk.emptyTitle', { ns: 'common' })

  const emptyBody =
    tab === 'customers' || tab === 'products' || tab === 'services'
      ? t('desk.emptyBodyMasters', {
          ns: 'common',
          defaultValue: 'Use + New to create, or View on a row for the full record.',
        })
      : t('desk.emptyBody', { ns: 'common' })

  // silence unused picker list (kept for future typeahead)
  void customerOptions

  return (
    <div className="page sales-desk-page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}

      <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
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
            <Link className="btn btn-secondary" to="/sales/crm">
              {t('features.crm', { ns: 'common', defaultValue: 'CRM' })}
            </Link>
          </>
        )}
      </div>

      <DeskShell
        title={
          <>
            <ShoppingCart size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('desk.title')}
          </>
        }
        subtitle={t('desk.subtitle')}
        tabs={tabs}
        activeTab={tab}
        onTabChange={requestTabChange}
        newLabel={newLabel}
        newExpanded={newExpanded}
        onToggleNew={requestToggleNew}
        newPanel={newPanel}
        toolbar={toolbar}
        list={list}
        hasSelection={hasSelection}
        emptyTitle={emptyTitle}
        emptyBody={emptyBody}
        detail={detail}
        detailFooter={detailFooter}
        processSlot={processSlot}
        helpContext={helpContext}
        narrowSheetOpen={narrow && hasSelection}
        onCloseSheet={() => setSelectedId(null)}
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
