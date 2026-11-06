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
  Headphones,
  RefreshCw,
  Plus,
  Check,
  X,
  Play,
  Eye,
  Pencil,
  Trash2,
  Shield,
  Package,
  MessageSquare,
  FileWarning,
  Users,
  FileText,
} from 'lucide-react'
import { aftersalesApi, salesApi, catalogueApi, domainsApi, meApi } from '../api/client'
import CatalogueItemSelect from '../components/CatalogueItemSelect'
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
import DeskNewFab from '../components/DeskNewFab'
import DeskShell from '../components/desk/DeskShell'
import ConfirmDialog from '../components/ConfirmDialog'
import RowActionSlots from '../components/RowActionSlots'
import useDeskSelection from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'

const TAB_DEFS = [
  { id: 'inquiries', labelKey: 'aftersales.tabs.inquiries', icon: MessageSquare },
  { id: 'warranties', labelKey: 'aftersales.tabs.warranties', icon: Shield },
  { id: 'claims', labelKey: 'aftersales.tabs.claims', icon: FileWarning },
  { id: 'rmas', labelKey: 'aftersales.tabs.rmas', icon: Package },
  { id: 'levels', labelKey: 'aftersales.tabs.levels', icon: Headphones },
]

/**
 * AfterSales desk (AS-S0–AS-S3) — Buyers UI 2.0 (ADR-050): single-column + modal registers.
 */
export default function AfterSalesDesk() {
  const { t } = useTranslation(['desks', 'common', 'content'])
  const loadAllRef = useRef(async () => {})
  const deepLinkOpened = useRef(null)
  const getBelowFieldsRef = useRef(null)
  const getFooterExtraRef = useRef(null)
  const runInquiryRef = useRef(async () => {})
  const runClaimRef = useRef(async () => {})
  const runRmaRef = useRef(async () => {})
  const selectedInqRef = useRef(null)
  const selectedClaimRef = useRef(null)
  const selectedRmaRef = useRef(null)
  const inquiriesRef = useRef([])
  const claimsRef = useRef([])
  const rmasRef = useRef([])
  const busyRef = useRef(null)
  const tabs = useMemo(
    () => TAB_DEFS.map((d) => ({ id: d.id, label: t(d.labelKey), icon: d.icon })),
    [t],
  )
  const [tab, setTab] = useState(() => {
    if (typeof window === 'undefined') return 'inquiries'
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    if (urlTab) return urlTab
    try {
      const raw = window.localStorage.getItem('steakos.desk.default.aftersales')
      const parsed = raw ? JSON.parse(raw) : null
      if (parsed?.tab) return String(parsed.tab)
    } catch {
      /* ignore */
    }
    return 'inquiries'
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

  const { loadMemory, saveMemory } = useDeskMemory({ deskKey: 'aftersales', companyId })

  useDeskSelection({
    deskKey: 'aftersales',
    defaultTab: 'inquiries',
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
    description: '',
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
    description: '',
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
  const [inqSort, setInqSort] = useState('id')
  const [inqOrder, setInqOrder] = useState('desc')
  const [warSort, setWarSort] = useState('id')
  const [warOrder, setWarOrder] = useState('desc')
  const [claimSort, setClaimSort] = useState('id')
  const [claimOrder, setClaimOrder] = useState('desc')
  const [rmaSort, setRmaSort] = useState('id')
  const [rmaOrder, setRmaOrder] = useState('desc')
  /** Dropdown options (not paged) — max 100 until typeahead. */
  const [customerOptions, setCustomerOptions] = useState([])
  const [levelOptions, setLevelOptions] = useState([])
  const [warrantyOptions, setWarrantyOptions] = useState([])

  const INQ_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'inquiryNumber', label: 'Number' },
      { value: 'name', label: 'Subject' },
      { value: 'customerName', label: 'Customer' },
      { value: 'kind', label: 'Kind' },
      { value: 'caseStatus', label: 'Status' },
    ],
    [],
  )
  const WAR_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'warrantyNumber', label: 'Number' },
      { value: 'customerName', label: 'Customer' },
      { value: 'productName', label: 'Product' },
      { value: 'coverStatus', label: 'Status' },
    ],
    [],
  )
  const CLAIM_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'claimNumber', label: 'Number' },
      { value: 'claimAmountEur', label: 'EUR' },
      { value: 'claimStatus', label: 'Status' },
    ],
    [],
  )
  const RMA_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'rmaNumber', label: 'Number' },
      { value: 'customerName', label: 'Customer' },
      { value: 'productName', label: 'Product' },
      { value: 'rmaStatus', label: 'Status' },
    ],
    [],
  )

  const onInqSortChange = useCallback(({ sort: s, order: o }) => {
    setInqSort(s || 'id')
    setInqOrder(o === 'asc' ? 'asc' : 'desc')
    setInqPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onWarSortChange = useCallback(({ sort: s, order: o }) => {
    setWarSort(s || 'id')
    setWarOrder(o === 'asc' ? 'asc' : 'desc')
    setWarPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onClaimSortChange = useCallback(({ sort: s, order: o }) => {
    setClaimSort(s || 'id')
    setClaimOrder(o === 'asc' ? 'asc' : 'desc')
    setClaimPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onRmaSortChange = useCallback(({ sort: s, order: o }) => {
    setRmaSort(s || 'id')
    setRmaOrder(o === 'asc' ? 'asc' : 'desc')
    setRmaPage((pg) => ({ ...pg, offset: 0 }))
  }, [])

  const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'

  const {
    viewModal,
    openView,
    openEdit,
    updateViewingRecord,
  } = useRecordView({
    modalEdit: true,
    canModalEdit: () => true,
    onSaved: (updated) => {
      loadAllRef.current().catch(() => {})
      if (!updated?.id) return
      const sync = (setter) =>
        setter((prev) =>
          prev && String(prev.id) === String(updated.id) ? { ...prev, ...updated } : prev,
        )
      sync(setSelectedInq)
      sync(setSelectedClaim)
      sync(setSelectedRma)
    },
    getBelowFields: (ctx) => getBelowFieldsRef.current?.(ctx) ?? null,
    getFooterExtra: (ctx) => getFooterExtraRef.current?.(ctx) ?? null,
  })

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
      aftersalesApi.inquiries.list({
        max: inqPage.max,
        offset: inqPage.offset,
        sort: inqSort,
        order: inqOrder,
      }),
      aftersalesApi.warranties.list({
        max: warPage.max,
        offset: warPage.offset,
        sort: warSort,
        order: warOrder,
      }),
      aftersalesApi.warranties.list({ max: 100, offset: 0 }),
      aftersalesApi.claims.list({
        max: claimPage.max,
        offset: claimPage.offset,
        sort: claimSort,
        order: claimOrder,
      }),
      aftersalesApi.rmas.list({
        max: rmaPage.max,
        offset: rmaPage.offset,
        sort: rmaSort,
        order: rmaOrder,
      }),
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
    inqSort,
    inqOrder,
    warPage.max,
    warPage.offset,
    warSort,
    warOrder,
    claimPage.max,
    claimPage.offset,
    claimSort,
    claimOrder,
    rmaPage.max,
    rmaPage.offset,
    rmaSort,
    rmaOrder,
  ])

  loadAllRef.current = loadAll
  selectedInqRef.current = selectedInq
  selectedClaimRef.current = selectedClaim
  selectedRmaRef.current = selectedRma
  inquiriesRef.current = inquiries
  claimsRef.current = claims
  rmasRef.current = rmas
  busyRef.current = busy

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

  const openInqView = useCallback(
    async (rec) => {
      if (!rec?.id && rec?.id !== 0) return
      const full = await aftersalesApi.inquiries.get(rec.id)
      setSelectedInq(full)
      openView('service', 'Inquiry', full)
    },
    [openView],
  )
  const openClaimView = useCallback(
    async (rec) => {
      if (!rec?.id && rec?.id !== 0) return
      const full = await aftersalesApi.claims.get(rec.id)
      setSelectedClaim(full)
      openView('service', 'Claims', full)
    },
    [openView],
  )
  const openRmaView = useCallback(
    async (rec) => {
      if (!rec?.id && rec?.id !== 0) return
      const full = await aftersalesApi.rmas.get(rec.id)
      setSelectedRma(full)
      openView('service', 'Rma', full)
    },
    [openView],
  )

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      if (!selectedId) return
      if (deepLinkOpened.current === String(selectedId)) return
      try {
        let pkg = 'service'
        let resource = 'Inquiry'
        let record = null
        if (tab === 'inquiries') {
          record = await aftersalesApi.inquiries.get(selectedId)
          if (!cancelled && record) setSelectedInq(record)
          resource = 'Inquiry'
        } else if (tab === 'claims') {
          record = await aftersalesApi.claims.get(selectedId)
          if (!cancelled && record) setSelectedClaim(record)
          resource = 'Claims'
        } else if (tab === 'rmas') {
          record = await aftersalesApi.rmas.get(selectedId)
          if (!cancelled && record) setSelectedRma(record)
          resource = 'Rma'
        } else if (tab === 'warranties') {
          record = await aftersalesApi.warranties.get(selectedId)
          resource = 'Warranty'
        } else if (tab === 'levels') {
          record = await aftersalesApi.serviceLevels.get(selectedId)
          resource = 'ServiceLevel'
        }
        if (!cancelled && record) {
          openView(pkg, resource, record)
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
      if (tab === 'inquiries' && selectedId) await selectInquiry(selectedId)
      if (tab === 'claims' && selectedId) await selectClaim(selectedId)
      if (tab === 'rmas' && selectedId) await selectRma(selectedId)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [loadAll, selectInquiry, selectClaim, selectRma, selectedId, tab])

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
      setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.customer', { ns: 'common' }), name: c.name }))
      setCustomerForm({ name: '', email: '', contactName: '', customerKind: 'ORGANIZATION' })
      await loadAll()
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
        description: levelForm.description?.trim() || null,
        responseHours: levelForm.responseHours || null,
        resolveHours: levelForm.resolveHours || null,
      })
      setInfo(t('toast.levelCreated', { ns: 'common', name: s.name }))
      setLevelForm({
        name: '',
        description: '',
        code: '',
        responseHours: '24',
        resolveHours: '72',
        channel: 'ANY',
      })
      setLevelPage((pg) => ({ ...pg, offset: 0 }))
      await loadAll()
      setNewExpanded(false)
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
      setNewExpanded(false)
      await loadAll()
      setTab('inquiries')
      deepLinkOpened.current = String(i.id)
      setSelectedId(String(i.id))
      await openInqView(i)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runInquiry = async (action) => {
    const live = selectedInqRef.current
    if (!live?.id) return
    setBusy(action)
    setError(null)
    setInfo(null)
    try {
      let result
      if (action === 'start') result = await aftersalesApi.inquiries.start(live.id)
      if (action === 'resolve') result = await aftersalesApi.inquiries.resolve(live.id)
      if (action === 'close') result = await aftersalesApi.inquiries.close(live.id)
      if (action === 'cancel') result = await aftersalesApi.inquiries.cancel(live.id)
      setSelectedInq(result)
      updateViewingRecord(result)
      setInfo(t('toast.numberStatus', { ns: 'common', what: t('labels.inquiry', { ns: 'common' }), number: result.inquiryNumber, status: result.caseStatus }))
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }
  runInquiryRef.current = runInquiry

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
        description: warForm.description?.trim() || null,
        customerId: warForm.customerId || undefined,
        customerName: c?.name || undefined,
        productName: warForm.productName || item?.name || undefined,
        catalogueItemId: warForm.catalogueItemId || undefined,
        coverStatus: 'ACTIVE',
      })
      setInfo(t('toast.warrantyReg', { ns: 'common', number: w.warrantyNumber }))
      setWarForm({ name: '', description: '', customerId: '', productName: '', catalogueItemId: '' })
      setWarPage((pg) => ({ ...pg, offset: 0 }))
      setNewExpanded(false)
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
      setNewExpanded(false)
      await loadAll()
      setTab('claims')
      deepLinkOpened.current = String(c.id)
      setSelectedId(String(c.id))
      await openClaimView(c)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runClaim = async (action) => {
    const live = selectedClaimRef.current
    if (!live?.id) return
    setBusy(action)
    setError(null)
    setInfo(null)
    try {
      let updated = null
      if (action === 'validate') {
        updated = await aftersalesApi.claims.validate(live.id)
        setInfo(t('toast.validated', { ns: 'common', number: updated.claimNumber }))
      } else if (action === 'approve') {
        const result = await aftersalesApi.claims.approve(live.id)
        updated = result.claim || result
        if (result.financeCreditInvoiceId) {
          setInfo('Claim approved → Finance credit')
        } else {
          setInfo(
            `Claim approved${result.finance?.error ? ` (credit: ${result.finance.error})` : ''}`,
          )
        }
      } else if (action === 'reject') {
        updated = await aftersalesApi.claims.reject(live.id, {
          resolution: 'Rejected from desk',
        })
        setInfo(t('toast.claimRejected', { ns: 'common', number: updated.claimNumber }))
      } else if (action === 'close') {
        updated = await aftersalesApi.claims.close(live.id)
        setInfo(t('toast.claimClosed', { ns: 'common', number: updated.claimNumber }))
      }
      if (updated) {
        setSelectedClaim(updated)
        updateViewingRecord(updated)
      }
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }
  runClaimRef.current = runClaim

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
      setNewExpanded(false)
      await loadAll()
      setTab('rmas')
      deepLinkOpened.current = String(r.id)
      setSelectedId(String(r.id))
      await openRmaView(r)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runRma = async (action) => {
    const live = selectedRmaRef.current
    if (!live?.id) return
    setBusy(action)
    setError(null)
    setInfo(null)
    try {
      let updated = null
      if (action === 'confirm') {
        const result = await aftersalesApi.rmas.confirm(live.id)
        updated = result.rma || result
        setInfo(
          result.stockMoveId
            ? 'RMA confirmed → stock move'
            : 'RMA confirmed (no stock — set product id)',
        )
      } else if (action === 'close') {
        updated = await aftersalesApi.rmas.close(live.id)
        setInfo(t('toast.rmaClosed', { ns: 'common', number: updated.rmaNumber }))
      } else if (action === 'cancel') {
        updated = await aftersalesApi.rmas.cancel(live.id)
        setInfo(t('toast.rmaCancelled', { ns: 'common', number: updated.rmaNumber }))
      }
      if (updated) {
        setSelectedRma(updated)
        updateViewingRecord(updated)
      }
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }
  runRmaRef.current = runRma

  const onDelete = useCallback(
    async (pkg, resource, record) => {
      const label =
        record?.name ||
        record?.inquiryNumber ||
        record?.claimNumber ||
        record?.rmaNumber ||
        record?.warrantyNumber ||
        t('view.untitled', { ns: 'common', defaultValue: 'this record' })
      if (!window.confirm(t('actions.confirmDelete', { ns: 'common', label }))) return
      setError(null)
      try {
        if (resource === 'Inquiry') await aftersalesApi.inquiries.cancel(record.id)
        else if (resource === 'Rma') await aftersalesApi.rmas.cancel(record.id)
        else await domainsApi.remove(pkg, resource, record.id)
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
          }
          delete={
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
          }
        />
      )
    },
    [onDelete, openEdit, openView, t],
  )

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


  const resetCreateForms = useCallback(() => {
    setLevelForm({
      name: '',
      description: '',
      code: '',
      responseHours: '24',
      resolveHours: '72',
      channel: 'ANY',
    })
    setInqForm({
      name: '',
      kind: 'REQUEST',
      channel: 'EMAIL',
      priority: 'NORMAL',
      customerId: '',
      serviceLevelId: '',
      description: '',
    })
    setWarForm({ name: '', description: '', customerId: '', productName: '', catalogueItemId: '' })
    setClaimForm({ warrantyId: '', claimAmountEur: '0', claimAmountSats: '', description: '' })
    setRmaForm({
      customerId: '',
      catalogueItemId: '',
      productName: '',
      quantity: '1',
      reason: '',
      expectedRefundEur: '',
    })
    setPendingInqContent([])
    setPendingClaimContent([])
    setPendingRmaContent([])
  }, [])

  const createDirty = useMemo(() => {
    if (tab === 'levels') {
      return Boolean(
        (levelForm.name || '').trim() ||
          (levelForm.code || '').trim() ||
          (levelForm.description || '').trim(),
      )
    }
    if (tab === 'inquiries') {
      return Boolean(
        (inqForm.name || '').trim() ||
          inqForm.customerId ||
          inqForm.serviceLevelId ||
          (inqForm.description || '').trim() ||
          pendingInqContent.length ||
          inqForm.kind !== 'REQUEST' ||
          inqForm.channel !== 'EMAIL' ||
          inqForm.priority !== 'NORMAL',
      )
    }
    if (tab === 'warranties') {
      return Boolean(
        (warForm.name || '').trim() ||
          (warForm.description || '').trim() ||
          warForm.customerId ||
          warForm.productName ||
          warForm.catalogueItemId,
      )
    }
    if (tab === 'claims') {
      return Boolean(
        claimForm.warrantyId ||
          claimForm.description ||
          claimForm.claimAmountSats ||
          claimForm.claimAmountEur !== '0' ||
          pendingClaimContent.length,
      )
    }
    if (tab === 'rmas') {
      return Boolean(
        rmaForm.customerId ||
          rmaForm.catalogueItemId ||
          rmaForm.productName ||
          rmaForm.reason ||
          rmaForm.expectedRefundEur ||
          rmaForm.quantity !== '1' ||
          pendingRmaContent.length,
      )
    }
    return false
  }, [tab, levelForm, inqForm, warForm, claimForm, rmaForm, pendingInqContent, pendingClaimContent, pendingRmaContent])

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

  const keyboardIds = useMemo(() => {
    if (tab === 'inquiries') return inquiries.map((i) => i.id)
    if (tab === 'claims') return claims.map((c) => c.id)
    if (tab === 'rmas') return rmas.map((r) => r.id)
    return []
  }, [tab, inquiries, claims, rmas])

  useDeskKeyboard({
    enabled:
      !newExpanded &&
      (tab === 'inquiries' || tab === 'claims' || tab === 'rmas'),
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
    if (tab === 'inquiries') return t('aftersales.newInquiry', { defaultValue: 'New inquiry' })
    if (tab === 'warranties') return t('aftersales.newWarranty', { defaultValue: 'New warranty' })
    if (tab === 'claims') return t('aftersales.newClaim', { defaultValue: 'New claim' })
    if (tab === 'rmas') return t('aftersales.newRma', { defaultValue: 'New RMA' })
    if (tab === 'levels') return t('aftersales.newLevel', { defaultValue: 'New service level' })
    return t('desk.new', { ns: 'common', defaultValue: 'New' })
  }, [tab, t])

  const hasSelection = false

  const helpContext = useMemo(() => {
    const base = { desk: 'aftersales', tab, processCode: 'AS_INQUIRY' }
    if (tab === 'inquiries' && selectedInq) {
      return {
        ...base,
        resource: 'Inquiry',
        objectId: selectedInq.id,
        status: selectedInq.caseStatus,
      }
    }
    if (tab === 'claims' && selectedClaim) {
      return {
        ...base,
        resource: 'Claims',
        objectId: selectedClaim.id,
        status: selectedClaim.claimStatus,
      }
    }
    if (tab === 'rmas' && selectedRma) {
      return {
        ...base,
        resource: 'Rma',
        objectId: selectedRma.id,
        status: selectedRma.rmaStatus,
      }
    }
    return base
  }, [tab, selectedInq, selectedClaim, selectedRma])

  const collapsedContentRail = (pkg, resource, id, onChanged) => (
    <details className="content-rail-collapse" style={{ marginTop: '1.25rem' }}>
      <summary className="btn btn-ghost" style={{ cursor: 'pointer', listStyle: 'inside' }}>
        {t('desk.attachmentsNotes', { ns: 'common', defaultValue: 'Attachments & notes' })}
      </summary>
      <ContentRail package={pkg} resource={resource} id={id} onChanged={onChanged} />
    </details>
  )


  const levelCreatePanel = (
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
              <label>
                Description
                <textarea
                  rows={2}
                  value={levelForm.description}
                  onChange={(e) => setLevelForm((f) => ({ ...f, description: e.target.value }))}
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
  )

  const inqCreatePanel = (
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
  )

  const warCreatePanel = (
<form className="form-grid" onSubmit={onCreateWar}>
              <label>
                Name
                <input
                  value={warForm.name}
                  onChange={(e) => setWarForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder={t('aftersales.coverLabel')}
                />
              </label>
              <label>
                Description
                <textarea
                  rows={2}
                  value={warForm.description}
                  onChange={(e) => setWarForm((f) => ({ ...f, description: e.target.value }))}
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
  )

  const claimCreatePanel = (
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
                <Plus size={16} />{' '}
                {t('aftersales.receiveClaim', { defaultValue: 'Receive' })}
              </button>
            </form>
  )

  const rmaCreatePanel = (
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
  )

  const newPanel =
    tab === 'levels'
      ? levelCreatePanel
      : tab === 'inquiries'
        ? inqCreatePanel
        : tab === 'warranties'
          ? warCreatePanel
          : tab === 'claims'
            ? claimCreatePanel
            : tab === 'rmas'
              ? rmaCreatePanel
              : null

  const levelsList = (
    <>
<ResponsiveDataList
                    items={levels}
                    empty="—"
                    onSelect={(sl) => openView('service', 'ServiceLevel', sl)}
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
                    actions={(sl) => renderRowActions('service', 'ServiceLevel', sl)}
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
                            <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {levels.map((sl) => (
                            <tr
                              key={sl.id}
                              className="row-click"
                              onClick={() => openView('service', 'ServiceLevel', sl)}
                            >
                              <td>{sl.name}</td>
                              <td className="mono">{sl.code || '—'}</td>
                              <td className="col-priority-low">
                                {sl.responseHours != null ? `${sl.responseHours}h` : '—'}
                              </td>
                              <td className="col-priority-low">
                                {sl.resolveHours != null ? `${sl.resolveHours}h` : '—'}
                              </td>
                              <td
                                style={{ whiteSpace: 'nowrap' }}
                                onClick={(e) => e.stopPropagation()}
                              >
                                {renderRowActions('service', 'ServiceLevel', sl)}
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
    </>
  )

  const warrantiesList = (
    <>
<ResponsiveDataList
                    items={warranties}
                    empty="—"
                    onSelect={(w) => openView('service', 'Warranty', w)}
                    sortFields={WAR_SORT_MENU}
                    sort={warSort}
                    order={warOrder}
                    onSortChange={onWarSortChange}
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
                        render: (w) => w.productName || '—',
                      },
                    ]}
                    actions={(w) => renderRowActions('service', 'Warranty', w)}
                  >
                    <div className="table-wrap">
                      <table className="data">
                        <thead>
                          <tr>
                            <ListSortHeader
                              field="warrantyNumber"
                              label={t('wb.number', { ns: 'common' })}
                              sort={warSort}
                              order={warOrder}
                              onSortChange={onWarSortChange}
                            />
                            <ListSortHeader
                              field="customerName"
                              label={t('wb.customer', { ns: 'common' })}
                              sort={warSort}
                              order={warOrder}
                              onSortChange={onWarSortChange}
                            />
                            <ListSortHeader
                              field="productName"
                              label={t('wb.product', { ns: 'common' })}
                              sort={warSort}
                              order={warOrder}
                              onSortChange={onWarSortChange}
                              className="col-priority-low"
                            />
                            <ListSortHeader
                              field="coverStatus"
                              label={t('wb.status', { ns: 'common' })}
                              sort={warSort}
                              order={warOrder}
                              onSortChange={onWarSortChange}
                            />
                            <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {warranties.map((w) => (
                            <tr
                              key={w.id}
                              className="row-click"
                              onClick={() => openView('service', 'Warranty', w)}
                            >
                              <td className="mono">{w.warrantyNumber}</td>
                              <td>{w.customerName || customerNameById(w.customerId) || '—'}</td>
                              <td className="col-priority-low">
                                {w.productName || '—'}
                              </td>
                              <td>
                                <span className={statusClass(w.coverStatus)}>
                                  {w.coverStatus}
                                  {w.inForce ? ' · in force' : ''}
                                </span>
                              </td>
                              <td
                                style={{ whiteSpace: 'nowrap' }}
                                onClick={(e) => e.stopPropagation()}
                              >
                                {renderRowActions('service', 'Warranty', w)}
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
    </>
  )

  const inquiriesList = (
    <>
<ResponsiveDataList
                    items={inquiries}
                    empty="—"
                                        onSelect={(i) => openInqView(i)}
                    sortFields={INQ_SORT_MENU}
                    sort={inqSort}
                    order={inqOrder}
                    onSortChange={onInqSortChange}
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
                    actions={(i) =>
                      renderRowActions('service', 'Inquiry', i, { onView: () => openInqView(i) })
                    }
                  >
                    <div className="table-wrap">
                      <table className="data">
                        <thead>
                          <tr>
                            <ListSortHeader
                              field="inquiryNumber"
                              label={t('wb.number', { ns: 'common' })}
                              sort={inqSort}
                              order={inqOrder}
                              onSortChange={onInqSortChange}
                            />
                            <ListSortHeader
                              field="name"
                              label={t('wb.subject', { ns: 'common' })}
                              sort={inqSort}
                              order={inqOrder}
                              onSortChange={onInqSortChange}
                            />
                            <ListSortHeader
                              field="customerName"
                              label={t('wb.customer', { ns: 'common' })}
                              sort={inqSort}
                              order={inqOrder}
                              onSortChange={onInqSortChange}
                            />
                            <ListSortHeader
                              field="kind"
                              label={t('wb.kind', { ns: 'common' })}
                              sort={inqSort}
                              order={inqOrder}
                              onSortChange={onInqSortChange}
                              className="col-priority-low"
                            />
                            <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                            <ListSortHeader
                              field="caseStatus"
                              label={t('wb.status', { ns: 'common' })}
                              sort={inqSort}
                              order={inqOrder}
                              onSortChange={onInqSortChange}
                            />
                            <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {inquiries.map((i) => (
                            <tr
                              key={i.id}
                              className="row-click"
                              onClick={() => openInqView(i)}
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
                                {renderRowActions('service', 'Inquiry', i, { onView: () => openInqView(i) })}
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
    </>
  )

  const claimsList = (
    <>
<ResponsiveDataList
                    items={claims}
                    empty="—"
                                        onSelect={(c) => openClaimView(c)}
                    sortFields={CLAIM_SORT_MENU}
                    sort={claimSort}
                    order={claimOrder}
                    onSortChange={onClaimSortChange}
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
                        render: (c) => c.warrantyNumber || '—',
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
                    actions={(c) =>
                      renderRowActions('service', 'Claims', c, { onView: () => openClaimView(c) })
                    }
                  >
                    <div className="table-wrap">
                      <table className="data">
                        <thead>
                          <tr>
                            <ListSortHeader
                              field="claimNumber"
                              label={t('wb.number', { ns: 'common' })}
                              sort={claimSort}
                              order={claimOrder}
                              onSortChange={onClaimSortChange}
                            />
                            <th>{t('aftersales.warrantiesList')}</th>
                            <ListSortHeader
                              field="claimAmountEur"
                              label="EUR"
                              sort={claimSort}
                              order={claimOrder}
                              onSortChange={onClaimSortChange}
                            />
                            <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                            <ListSortHeader
                              field="claimStatus"
                              label={t('wb.status', { ns: 'common' })}
                              sort={claimSort}
                              order={claimOrder}
                              onSortChange={onClaimSortChange}
                            />
                            <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {claims.map((c) => (
                            <tr
                              key={c.id}
                              className="row-click"
                              onClick={() => openClaimView(c)}
                            >
                              <td className="mono">{c.claimNumber}</td>
                              <td>{c.warrantyNumber || '—'}</td>
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
                                {renderRowActions('service', 'Claims', c, { onView: () => openClaimView(c) })}
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
    </>
  )

  const rmasList = (
    <>
<ResponsiveDataList
                    items={rmas}
                    empty="—"
                                        onSelect={(r) => openRmaView(r)}
                    sortFields={RMA_SORT_MENU}
                    sort={rmaSort}
                    order={rmaOrder}
                    onSortChange={onRmaSortChange}
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
                        render: (r) => r.productName || '—',
                      },
                    ]}
                    actions={(r) =>
                      renderRowActions('service', 'Rma', r, { onView: () => openRmaView(r) })
                    }
                  >
                    <div className="table-wrap">
                      <table className="data">
                        <thead>
                          <tr>
                            <ListSortHeader
                              field="rmaNumber"
                              label={t('wb.number', { ns: 'common' })}
                              sort={rmaSort}
                              order={rmaOrder}
                              onSortChange={onRmaSortChange}
                            />
                            <ListSortHeader
                              field="customerName"
                              label={t('wb.customer', { ns: 'common' })}
                              sort={rmaSort}
                              order={rmaOrder}
                              onSortChange={onRmaSortChange}
                            />
                            <ListSortHeader
                              field="productName"
                              label={t('wb.product', { ns: 'common' })}
                              sort={rmaSort}
                              order={rmaOrder}
                              onSortChange={onRmaSortChange}
                              className="col-priority-low"
                            />
                            <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                            <ListSortHeader
                              field="rmaStatus"
                              label={t('wb.status', { ns: 'common' })}
                              sort={rmaSort}
                              order={rmaOrder}
                              onSortChange={onRmaSortChange}
                            />
                            <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {rmas.map((r) => (
                            <tr
                              key={r.id}
                              className="row-click"
                              onClick={() => openRmaView(r)}
                            >
                              <td className="mono">{r.rmaNumber}</td>
                              <td>{r.customerName || customerNameById(r.customerId) || '—'}</td>
                              <td className="col-priority-low">
                                {r.productName || '—'}
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
                                {renderRowActions('service', 'Rma', r, { onView: () => openRmaView(r) })}
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
    </>
  )

  const list =
    tab === 'levels'
      ? levelsList
      : tab === 'warranties'
        ? warrantiesList
        : tab === 'inquiries'
          ? inquiriesList
          : tab === 'claims'
            ? claimsList
            : tab === 'rmas'
              ? rmasList
              : null

  getBelowFieldsRef.current = ({ resource, record }) => {
    if (!record) return null
    if (resource === 'Inquiry') {
      const live = selectedInqRef.current
      const inq = live?.id === record.id ? live : record
      return (
        <>
          {inq.description && (
            <p style={{ whiteSpace: 'pre-wrap' }}>{inq.description}</p>
          )}
          {collapsedContentRail('service', 'Inquiry', inq.id, async () =>
            setInqContentMap(await fetchContentMap('service', 'Inquiry', inquiriesRef.current)),
          )}
        </>
      )
    }
    if (resource === 'Claims') {
      const live = selectedClaimRef.current
      const claim = live?.id === record.id ? live : record
      return (
        <>
          {collapsedContentRail('service', 'Claims', claim.id, async () =>
            setClaimContentMap(await fetchContentMap('service', 'Claims', claimsRef.current)),
          )}
        </>
      )
    }
    if (resource === 'Rma') {
      const live = selectedRmaRef.current
      const rma = live?.id === record.id ? live : record
      return (
        <>
          {collapsedContentRail('service', 'Rma', rma.id, async () =>
            setRmaContentMap(await fetchContentMap('service', 'Rma', rmasRef.current)),
          )}
        </>
      )
    }
    return null
  }

  getFooterExtraRef.current = ({ resource, record }) => {
    if (!record) return null
    const isBusy = !!busyRef.current
    if (resource === 'Inquiry') {
      const live = selectedInqRef.current
      const inq = live?.id === record.id ? live : record
      return (
        <>
          {['OPEN'].includes(inq.caseStatus) && (
            <button
              type="button"
              className="btn"
              onClick={() => runInquiryRef.current('start')}
              disabled={isBusy}
            >
              <Play size={16} /> {t('aftersales.start')}
            </button>
          )}
          {['OPEN', 'IN_PROGRESS'].includes(inq.caseStatus) && (
            <button
              type="button"
              className="btn"
              onClick={() => runInquiryRef.current('resolve')}
              disabled={isBusy}
            >
              <Check size={16} /> {t('aftersales.resolve')}
            </button>
          )}
          {inq.caseStatus !== 'CLOSED' && inq.caseStatus !== 'CANCELLED' && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => runInquiryRef.current('close')}
              disabled={isBusy}
            >
              {t('actions.close', { ns: 'common' })}
            </button>
          )}
          {inq.caseStatus !== 'CLOSED' && inq.caseStatus !== 'CANCELLED' && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => runInquiryRef.current('cancel')}
              disabled={isBusy}
            >
              <X size={16} /> {t('actions.cancel', { ns: 'common' })}
            </button>
          )}
        </>
      )
    }
    if (resource === 'Claims') {
      const live = selectedClaimRef.current
      const claim = live?.id === record.id ? live : record
      return (
        <>
          {['RECEIVED'].includes(claim.claimStatus) && (
            <button
              type="button"
              className="btn"
              onClick={() => runClaimRef.current('validate')}
              disabled={isBusy}
            >
              {t('aftersales.validate')}
            </button>
          )}
          {['RECEIVED', 'VALIDATED'].includes(claim.claimStatus) && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => runClaimRef.current('approve')}
              disabled={isBusy}
              title={t('aftersales.approveFinanceTitle', {
                defaultValue: 'Approve → Finance',
              })}
            >
              <Check size={16} /> {t('aftersales.approveFinance')}
            </button>
          )}
          {['RECEIVED', 'VALIDATED'].includes(claim.claimStatus) && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => runClaimRef.current('reject')}
              disabled={isBusy}
            >
              <X size={16} /> {t('aftersales.reject')}
            </button>
          )}
          {['APPROVED', 'REJECTED'].includes(claim.claimStatus) && (
            <button
              type="button"
              className="btn"
              onClick={() => runClaimRef.current('close')}
              disabled={isBusy}
            >
              {t('actions.close', { ns: 'common' })}
            </button>
          )}
        </>
      )
    }
    if (resource === 'Rma') {
      const live = selectedRmaRef.current
      const rma = live?.id === record.id ? live : record
      return (
        <>
          {rma.rmaStatus === 'REQUESTED' && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => runRmaRef.current('confirm')}
              disabled={isBusy}
              title={t('aftersales.confirmScmStockTitle', {
                defaultValue: 'Confirm → SCM stock',
              })}
            >
              <Check size={16} /> {t('aftersales.confirmScmStock')}
            </button>
          )}
          {rma.rmaStatus !== 'CLOSED' && rma.rmaStatus !== 'CANCELLED' && (
            <button
              type="button"
              className="btn"
              onClick={() => runRmaRef.current('close')}
              disabled={isBusy}
            >
              {t('actions.close', { ns: 'common' })}
            </button>
          )}
          {rma.rmaStatus === 'REQUESTED' && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => runRmaRef.current('cancel')}
              disabled={isBusy}
            >
              <X size={16} /> {t('actions.cancel', { ns: 'common' })}
            </button>
          )}
          <a
            className="btn"
            href={aftersalesApi.rmas.pdfUrl(rma.id)}
            target="_blank"
            rel="noreferrer"
            title={t('aftersales.downloadRmaPdfTitle', {
              defaultValue: 'Download RMA PDF',
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

  return (
    <div className="page aftersales-desk-page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}

      {showNewCustomer && (
        <section className="card" style={{ marginBottom: '1rem' }}>
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
            <button type="submit" className="btn btn-primary" disabled={!!busy}>
              <Plus size={16} /> {busy === 'cust' ? '…' : t('aftersales.createCustomer')}
            </button>
          </form>
        </section>
      )}

      <div className="toolbar" style={{ marginBottom: '1.5rem' }}>
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
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

      <DeskShell
        layout="single"
        title={
          <>
            <Headphones size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('aftersales.title')}
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
