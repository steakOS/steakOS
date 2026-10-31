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
  Package,
  RefreshCw,
  Plus,
  Check,
  Send,
  Truck,
  Factory,
  Boxes,
  ClipboardCheck,
  FileText,
  Wrench,
} from 'lucide-react'
import { scmApi, catalogueApi, meApi } from '../api/client'
import CurrencySelect from '../components/CurrencySelect'
import DocumentLinesEditor, {
  emptyDocLine,
  linesToApiPayload,
} from '../components/DocumentLinesEditor'
import MasterEntityFields from '../forms/MasterEntityFields'
import { getMasterFormSpec } from '../forms/masterFormSpecs'

const vendorMasterSpec = getMasterFormSpec('procurement', 'Vendor')
const productMasterSpec = getMasterFormSpec('production', 'Product')
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import DeskProcessSlot from '../components/desk/DeskProcessSlot'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import ResponsiveDataList from '../components/ResponsiveDataList'
import DeskNewFab from '../components/DeskNewFab'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'
import ListFilterBar from '../components/ListFilterBar'
import VendorSelect from '../components/VendorSelect'
import CatalogueItemSelect from '../components/CatalogueItemSelect'
import DeskShell from '../components/desk/DeskShell'
import ConfirmDialog from '../components/ConfirmDialog'
import useDeskSelection, { scrollSelectedIntoView } from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'
import useNarrowViewport from '../hooks/useNarrowViewport'
import { labelFromList } from '../utils/entityDisplayLabel'

const BUYERS_TABS = [
  { id: 'vendors', labelKey: 'scm.tabs.vendors', icon: Package },
  { id: 'products', labelKey: 'scm.tabs.products', icon: Boxes },
  { id: 'services', labelKey: 'scm.tabs.services', icon: Wrench },
  { id: 'purchase', labelKey: 'scm.tabs.purchase', icon: Truck },
]

const SHOP_FLOOR_TABS = [
  { id: 'production', labelKey: 'scm.tabs.production', icon: Factory },
  { id: 'quality', labelKey: 'scm.tabs.quality', icon: ClipboardCheck },
  { id: 'inventory', labelKey: 'scm.tabs.inventory', icon: Boxes },
]

/**
 * Buyers Desk (/scm/buyers) or Shop Floor Control (/scm/shopfloor).
 * @param {'buyers'|'shopfloor'} desk
 */
export default function ScmDesk({ desk = 'buyers' }) {
  const isBuyers = desk !== 'shopfloor'
  const { t } = useTranslation(['desks', 'common', 'content'])
  const { viewModal, ViewButton } = useRecordView()
  const TAB_DEFS = isBuyers ? BUYERS_TABS : SHOP_FLOOR_TABS
  const tabs = useMemo(
    () => TAB_DEFS.map((d) => ({ id: d.id, label: t(d.labelKey), icon: d.icon })),
    [t, isBuyers],
  )
  const deskKey = isBuyers ? 'scm' : 'scm-shopfloor'
  const defaultTab = isBuyers ? 'purchase' : 'production'
  const narrow = useNarrowViewport()
  const [tab, setTab] = useState(() => {
    if (typeof window === 'undefined') return isBuyers ? 'purchase' : 'production'
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    if (urlTab) return urlTab
    try {
      const raw = window.localStorage.getItem(`steakos.desk.default.${isBuyers ? 'scm' : 'scm-shopfloor'}`)
      const parsed = raw ? JSON.parse(raw) : null
      if (parsed?.tab) return String(parsed.tab)
    } catch {
      /* ignore */
    }
    return isBuyers ? 'purchase' : 'production'
  })
  useEffect(() => {
    setTab(isBuyers ? 'purchase' : 'production')
    setSelectedId(null)
  }, [isBuyers])
  const [selectedId, setSelectedId] = useState(() => {
    if (typeof window === 'undefined') return null
    const id = new URLSearchParams(window.location.search).get('id')
    return id || null
  })
  const [newExpanded, setNewExpanded] = useState(false)
  const [companyId, setCompanyId] = useState('default')
  const [discardOpen, setDiscardOpen] = useState(false)
  const [pendingNav, setPendingNav] = useState(null)

  const { loadMemory, saveMemory } = useDeskMemory({ deskKey, companyId })

  useDeskSelection({
    deskKey,
    defaultTab,
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
  }, [companyId, deskKey])

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

  const [vendors, setVendors] = useState([])
  const [products, setProducts] = useState([])
  const [services, setServices] = useState([])
  const [pos, setPos] = useState([])
  const [prods, setProds] = useState([])
  const [lots, setLots] = useState([])
  const [onHand, setOnHand] = useState([])
  const [selectedPo, setSelectedPo] = useState(null)
  const [selectedProd, setSelectedProd] = useState(null)
  const [poContentMap, setPoContentMap] = useState({})
  const [prodContentMap, setProdContentMap] = useState({})
  const [pendingPoContent, setPendingPoContent] = useState([])
  const [pendingProdContent, setPendingProdContent] = useState([])
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)

  const [vendorForm, setVendorForm] = useState(() => vendorMasterSpec.empty())
  const [productForm, setProductForm] = useState(() => productMasterSpec.empty())
  const [serviceForm, setServiceForm] = useState(() => ({
    ...productMasterSpec.empty(),
    nature: 'SERVICE',
    canBeBought: true,
    canBeSold: true,
  }))
  const onMasterField =
    (setter) =>
    (e) => {
      const { name, value, type, checked } = e.target
      setter((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
    }
  const [poForm, setPoForm] = useState({
    vendorId: '',
    currencyCode: 'EUR',
  })
  const [poWarnings, setPoWarnings] = useState([])
  const [poLines, setPoLines] = useState(() => [
    emptyDocLine({ name: 'Raw material', quantity: '10', unitPriceEur: '25' }),
  ])
  const [prodForm, setProdForm] = useState({
    productId: '',
    quantity: '5',
    name: '',
  })
  const [lotForm, setLotForm] = useState({
    catalogueItemId: '',
    quantity: '1',
    name: '',
    notes: '',
  })
  const [salesOrderId, setSalesOrderId] = useState('')

  const [vendorPage, setVendorPage] = useState({ max: 25, offset: 0, total: 0 })
  const [poPage, setPoPage] = useState({ max: 25, offset: 0, total: 0 })
  const [prodPage, setProdPage] = useState({ max: 25, offset: 0, total: 0 })
  const [lotPage, setLotPage] = useState({ max: 25, offset: 0, total: 0 })
  const [goodsPage, setGoodsPage] = useState({ max: 25, offset: 0, total: 0 })
  const [servicesPage, setServicesPage] = useState({ max: 25, offset: 0, total: 0 })
  const [vendorOptions, setVendorOptions] = useState([])
  /** Picker options for PO/lot/production forms (not paged main list). */
  const [productOptions, setProductOptions] = useState([])
  const [poQ, setPoQ] = useState('')
  const [poSort, setPoSort] = useState('id')
  const [poOrder, setPoOrder] = useState('desc')

  const PO_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'poNumber', label: 'Number' },
      { value: 'vendorName', label: 'Vendor' },
      { value: 'amountEur', label: 'EUR' },
      { value: 'poStatus', label: 'Status' },
    ],
    [],
  )

  const onPoSortChange = useCallback(({ sort: s, order: o }) => {
    setPoSort(s || 'id')
    setPoOrder(o === 'asc' ? 'asc' : 'desc')
    setPoPage((pg) => ({ ...pg, offset: 0 }))
  }, [])

  const loadAll = useCallback(async () => {
    const [v, vPick, p, pPick, svc, po, pr, l, oh] = await Promise.all([
      scmApi.vendors.list({ max: vendorPage.max, offset: vendorPage.offset }),
      scmApi.vendors.list({ max: 100, offset: 0 }),
      catalogueApi.list({
        nature: 'GOODS',
        activeOnly: true,
        max: goodsPage.max,
        offset: goodsPage.offset,
      }),
      catalogueApi.list({ nature: 'GOODS', activeOnly: true, max: 100, offset: 0 }),
      catalogueApi.list({
        nature: 'SERVICE',
        activeOnly: true,
        max: servicesPage.max,
        offset: servicesPage.offset,
      }),
      scmApi.purchaseOrders.list({
        max: poPage.max,
        offset: poPage.offset,
        sort: poSort,
        order: poOrder,
        q: poQ.trim() || undefined,
      }),
      scmApi.production.list({ max: prodPage.max, offset: prodPage.offset }),
      scmApi.lots.list({ max: lotPage.max, offset: lotPage.offset }),
      scmApi.inventory.onHand(),
    ])
    const posItems = po.items || []
    const prItems = pr.items || []
    setVendors(v.items || [])
    setVendorOptions(vPick.items || [])
    setProducts(p.items || [])
    setProductOptions(pPick.items || [])
    setServices(svc.items || [])
    setPos(posItems)
    setProds(prItems)
    setLots(l.items || [])
    setOnHand(oh.items || [])
    setVendorPage((pg) => ({
      ...pg,
      max: v.max ?? pg.max,
      offset: v.offset ?? pg.offset,
      total: v.total ?? (v.items || []).length,
    }))
    setGoodsPage((pg) => ({
      ...pg,
      max: p.max ?? pg.max,
      offset: p.offset ?? pg.offset,
      total: p.total ?? (p.items || []).length,
    }))
    setServicesPage((pg) => ({
      ...pg,
      max: svc.max ?? pg.max,
      offset: svc.offset ?? pg.offset,
      total: svc.total ?? (svc.items || []).length,
    }))
    setPoPage((pg) => ({
      ...pg,
      max: po.max ?? pg.max,
      offset: po.offset ?? pg.offset,
      total: po.total ?? posItems.length,
    }))
    setProdPage((pg) => ({
      ...pg,
      max: pr.max ?? pg.max,
      offset: pr.offset ?? pg.offset,
      total: pr.total ?? prItems.length,
    }))
    setLotPage((pg) => ({
      ...pg,
      max: l.max ?? pg.max,
      offset: l.offset ?? pg.offset,
      total: l.total ?? (l.items || []).length,
    }))
    const [pm, prm] = await Promise.all([
      fetchContentMap('procurement', 'PurchaseOrder', posItems),
      fetchContentMap('production', 'ProductionOrder', prItems),
    ])
    setPoContentMap(pm)
    setProdContentMap(prm)
  }, [
    vendorPage.max,
    vendorPage.offset,
    goodsPage.max,
    goodsPage.offset,
    servicesPage.max,
    servicesPage.offset,
    poPage.max,
    poPage.offset,
    poQ,
    poSort,
    poOrder,
    prodPage.max,
    prodPage.offset,
    lotPage.max,
    lotPage.offset,
  ])

  const selectPo = useCallback(async (id) => {
    if (!id) {
      setSelectedPo(null)
      setPoWarnings([])
      return
    }
    const full = await scmApi.purchaseOrders.get(id)
    setSelectedPo(full)
    setPoWarnings(Array.isArray(full?.warnings) ? full.warnings : [])
  }, [])

  const selectProd = useCallback(async (id) => {
    if (!id) {
      setSelectedProd(null)
      return
    }
    setSelectedProd(await scmApi.production.get(id))
  }, [])

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      try {
        if (tab === 'purchase') {
          if (selectedId) await selectPo(selectedId)
          else if (!cancelled) {
            setSelectedPo(null)
            setPoWarnings([])
          }
          if (!cancelled) setSelectedProd(null)
        } else if (tab === 'production') {
          if (selectedId) await selectProd(selectedId)
          else if (!cancelled) setSelectedProd(null)
          if (!cancelled) setSelectedPo(null)
        } else if (!cancelled) {
          setSelectedPo(null)
          setSelectedProd(null)
        }
      } catch (e) {
        if (!cancelled) setError(e.message || String(e))
      }
    }
    run()
    return () => {
      cancelled = true
    }
  }, [tab, selectedId, selectPo, selectProd])

  useEffect(() => {
    if (!selectedId) return
    const id = window.requestAnimationFrame(() => {
      scrollSelectedIntoView('.desk-shell-list')
    })
    return () => window.cancelAnimationFrame(id)
  }, [selectedId, tab, selectedPo?.id, selectedProd?.id])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      await loadAll()
      if (tab === 'purchase' && selectedId) await selectPo(selectedId)
      if (tab === 'production' && selectedId) await selectProd(selectedId)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [loadAll, selectPo, selectProd, selectedId, tab])

  useEffect(() => {
    setLoading(true)
    loadAll()
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [loadAll])

  const statusClass = (s) => {
    if (['CONFIRMED', 'RECEIVED', 'COMPLETED', 'RELEASED', 'PASS'].includes(s)) return 'badge badge-ok'
    if (['CANCELLED', 'REJECTED', 'FAIL'].includes(s)) return 'badge badge-muted'
    if (['SENT', 'PART_RECEIVED', 'IN_PROGRESS', 'HOLD', 'QUARANTINE'].includes(s)) return 'badge badge-warn'
    return 'badge badge-draft'
  }

  const onCreateVendor = async (e) => {
    e.preventDefault()
    setBusy('v')
    setError(null)
    try {
      await scmApi.vendors.create(vendorMasterSpec.toBody(vendorForm))
      setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.vendor', { ns: 'common' }), name: vendorForm.name }))
      setVendorForm(vendorMasterSpec.empty())
      setNewExpanded(false)
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateProduct = async (e) => {
    e.preventDefault()
    setBusy('p')
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
          what: 'Product',
          name: prod.name,
          status: prod.code || prod.sku || '—',
        }),
      )
      setProductForm(productMasterSpec.empty())
      setNewExpanded(false)
      try {
        await loadAll()
      } catch (refreshErr) {
        // create already succeeded — do not surface as create failure
        setError(`Created, but refresh failed: ${refreshErr.message}`)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreatePo = async (e) => {
    e.preventDefault()
    setBusy('po')
    setError(null)
    try {
      const lines = linesToApiPayload(poLines)
      if (!lines.length) {
        setError(t('docLines.needOne', { ns: 'common', defaultValue: 'Add at least one line.' }))
        setBusy(null)
        return
      }
      const body = {
        vendorId: poForm.vendorId || undefined,
        currencyCode: poForm.currencyCode || undefined,
        lines,
      }
      const po = await scmApi.purchaseOrders.create(body)
      setPoWarnings(Array.isArray(po?.warnings) ? po.warnings : [])
      if (pendingPoContent.length) {
        await attachPendingContent('procurement', 'PurchaseOrder', po.id, pendingPoContent)
        setPendingPoContent([])
      }
      setPoLines([emptyDocLine({ name: 'Raw material', quantity: '10', unitPriceEur: '25' })])
      setPoForm({ vendorId: '', currencyCode: 'EUR' })
      setNewExpanded(false)
      setInfo(t('toast.numberAmount', { ns: 'common', what: 'PO', number: po.poNumber, amount: Number(po.amountEur).toFixed(2) }))
      await loadAll()
      setSelectedId(String(po.id))
      setTab('purchase')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runPo = async (action) => {
    if (!selectedPo?.id) return
    setBusy(action)
    setError(null)
    setInfo(null)
    try {
      let result
      if (action === 'send') result = await scmApi.purchaseOrders.send(selectedPo.id)
      if (action === 'confirm') result = await scmApi.purchaseOrders.confirm(selectedPo.id)
      if (action === 'receive') result = await scmApi.purchaseOrders.receive(selectedPo.id)
      if (action === 'cancel') result = await scmApi.purchaseOrders.cancel(selectedPo.id)

      if (action === 'confirm') {
        setInfo(
          `Confirmed ${result.purchaseOrder?.poNumber}` +
            (result.financePayableId || result.payable?.payableNumber
              ? ` → payable${result.payable?.payableNumber ? ` ${result.payable.payableNumber}` : ''}`
              : '') +
            (result.alreadyConfirmed ? ' (already)' : ''),
        )
        setSelectedPo(result.purchaseOrder)
      } else if (action === 'receive') {
        setInfo(t('toast.receivedStock', { ns: 'common', number: result.receipt?.receiptNumber }))
        setSelectedPo(result.purchaseOrder)
      } else {
        setSelectedPo(result)
        setInfo(t('toast.numberStatus', { ns: 'common', what: action, number: result.poNumber, status: result.poStatus }))
      }
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateProd = async (e) => {
    e.preventDefault()
    setBusy('mo')
    setError(null)
    try {
      const o = await scmApi.production.create({
        productId: prodForm.productId,
        quantity: prodForm.quantity,
        name: prodForm.name || undefined,
      })
      if (pendingProdContent.length) {
        await attachPendingContent('production', 'ProductionOrder', o.id, pendingProdContent)
        setPendingProdContent([])
      }
      setInfo(t('toast.namedStatus', { ns: 'common', what: t('labels.production', { ns: 'common' }), name: o.orderNumber, status: '' }))
      setNewExpanded(false)
      await loadAll()
      setSelectedId(String(o.id))
      setTab('production')
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
      const r = await scmApi.production.fromSales(salesOrderId)
      setInfo(t('toast.prodFromSo', { ns: 'common', count: (r.items || []).length }))
      await loadAll()
      setTab('production')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runProd = async (action) => {
    if (!selectedProd?.id) return
    setBusy(action)
    setError(null)
    try {
      let result
      if (action === 'release') result = await scmApi.production.release(selectedProd.id)
      if (action === 'start') result = await scmApi.production.start(selectedProd.id)
      if (action === 'complete') result = await scmApi.production.complete(selectedProd.id)
      if (action === 'cancel') result = await scmApi.production.cancel(selectedProd.id)

      if (action === 'complete') {
        const lotCode = result.lot?.lotCode
        setInfo(
          lotCode
            ? t('scm.completeAwaitingQa', {
                number: result.productionOrder?.orderNumber,
                lot: lotCode,
                defaultValue: `Completed ${result.productionOrder?.orderNumber} · lot ${lotCode} · awaiting QA (stock after PASS)`,
              })
            : t('scm.completeDone', {
                number: result.productionOrder?.orderNumber,
                defaultValue: `Completed ${result.productionOrder?.orderNumber}`,
              }),
        )
        setSelectedProd(result.productionOrder)
        if (lotCode) setTab('quality')
      } else {
        setSelectedProd(result)
        setInfo(t('toast.numberStatus', { ns: 'common', what: action, number: result.orderNumber, status: result.prodStatus }))
      }
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onLotCheck = async (lotId, result) => {
    setBusy(`check-${lotId}-${result}`)
    setError(null)
    try {
      const notes = lotForm.notes?.trim() || undefined
      const c = await scmApi.lots.check(lotId, { checkResult: result, notes })
      const stockHint =
        result === 'PASS'
          ? t('scm.qaPassStock', { defaultValue: ' · stock posted if production lot' })
          : ''
      setInfo(
        `${t('toast.checkLot', { ns: 'common', result: c.checkResult })}${stockHint}`,
      )
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateLot = async (e) => {
    e.preventDefault()
    if (!lotForm.catalogueItemId) {
      setError(t('scm.lotNeedItem', { defaultValue: 'Catalogue item is required for a lot' }))
      return
    }
    setBusy('lot')
    setError(null)
    try {
      const lot = await scmApi.lots.create({
        catalogueItemId: Number(lotForm.catalogueItemId),
        quantity: Number(lotForm.quantity) || 1,
        name: lotForm.name || undefined,
        description: lotForm.notes || undefined,
      })
      setInfo(
        t('scm.lotCreated', {
          code: lot.lotCode,
          defaultValue: `Lot ${lot.lotCode} created (OPEN) — run QA check`,
        }),
      )
      setLotForm({ catalogueItemId: '', quantity: '1', name: '', notes: '' })
      setNewExpanded(false)
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const renderLotCheckActions = (l) => {
    if (!(l.lotStatus === 'OPEN' || l.lotStatus === 'QUARANTINE')) return null
    return (
      <span style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => onLotCheck(l.id, 'PASS')}
          disabled={!!busy}
        >
          PASS
        </button>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => onLotCheck(l.id, 'HOLD')}
          disabled={!!busy}
        >
          HOLD
        </button>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => onLotCheck(l.id, 'FAIL')}
          disabled={!!busy}
        >
          FAIL
        </button>
      </span>
    )
  }

  const productName = (id) =>
    labelFromList(productOptions, id, {
      fallback: labelFromList(products, id, { fallback: '—' }),
    })

  const onCreateService = async (e) => {
    e.preventDefault()
    setBusy('svc')
    setError(null)
    setInfo(null)
    try {
      const body = {
        ...productMasterSpec.toBody(serviceForm),
        nature: 'SERVICE',
        canBeBought: true,
        code: serviceForm.sku || serviceForm.code || undefined,
      }
      const svc = await catalogueApi.create(body)
      setInfo(
        t('toast.namedStatus', {
          ns: 'common',
          what: 'Service',
          name: svc.name,
          status: svc.code || svc.sku || '—',
        }),
      )
      setServiceForm({
        ...productMasterSpec.empty(),
        nature: 'SERVICE',
        canBeBought: true,
        canBeSold: true,
      })
      setNewExpanded(false)
      setTab('services')
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }


  const deskTitle = isBuyers ? t('scm.buyersTitle') : t('scm.shopFloorTitle')
  // Buyers: no process-flow subtitle under the title (tabs + hops are enough).
  const deskSubtitle = isBuyers ? null : t('scm.shopFloorSubtitle')
  const DeskIcon = isBuyers ? Package : Factory

  const DEFAULT_PO_LINES = () => [
    emptyDocLine({ name: 'Raw material', quantity: '10', unitPriceEur: '25' }),
  ]

  const resetCreateForms = useCallback(() => {
    setVendorForm(vendorMasterSpec.empty())
    setProductForm(productMasterSpec.empty())
    setServiceForm({
      ...productMasterSpec.empty(),
      nature: 'SERVICE',
      canBeBought: true,
      canBeSold: true,
    })
    setPoForm({ vendorId: '', currencyCode: 'EUR' })
    setPoLines(DEFAULT_PO_LINES())
    setPendingPoContent([])
    setPoWarnings([])
    setProdForm({ productId: '', quantity: '5', name: '' })
    setPendingProdContent([])
    setLotForm({ catalogueItemId: '', quantity: '1', name: '', notes: '' })
    setSalesOrderId('')
  }, [])

  const createDirty = useMemo(() => {
    if (tab === 'vendors') {
      return Boolean((vendorForm.name || '').trim() || vendorForm.email)
    }
    if (tab === 'products') {
      return Boolean((productForm.name || '').trim() || (productForm.sku || '').trim())
    }
    if (tab === 'services') {
      return Boolean((serviceForm.name || '').trim() || (serviceForm.sku || '').trim())
    }
    if (tab === 'purchase') {
      const lines = linesToApiPayload(poLines)
      const def = linesToApiPayload(DEFAULT_PO_LINES())
      const linesTouched =
        lines.length !== def.length ||
        lines.some((l, i) => !def[i] || l.name !== def[i].name || String(l.quantity) !== String(def[i].quantity))
      return Boolean(
        poForm.vendorId ||
          poForm.currencyCode !== 'EUR' ||
          pendingPoContent.length ||
          linesTouched,
      )
    }
    if (tab === 'production') {
      return Boolean(prodForm.productId || prodForm.name || pendingProdContent.length || salesOrderId)
    }
    if (tab === 'quality') {
      return Boolean(lotForm.catalogueItemId || lotForm.name || lotForm.notes || lotForm.quantity !== '1')
    }
    return false
  }, [
    tab,
    vendorForm,
    productForm,
    serviceForm,
    poForm,
    poLines,
    pendingPoContent,
    prodForm,
    pendingProdContent,
    lotForm,
    salesOrderId,
  ])

  const applyCollapseCreate = useCallback(() => {
    setNewExpanded(false)
    resetCreateForms()
  }, [resetCreateForms])

  const requestToggleNew = useCallback(() => {
    if (tab === 'inventory') return
    if (newExpanded && createDirty) {
      setPendingNav({ type: 'collapse' })
      setDiscardOpen(true)
      return
    }
    if (newExpanded) applyCollapseCreate()
    else setNewExpanded(true)
  }, [tab, newExpanded, createDirty, applyCollapseCreate])

  const keyboardIds = useMemo(() => {
    if (tab === 'purchase') return pos.map((p) => p.id)
    if (tab === 'production') return prods.map((p) => p.id)
    return []
  }, [tab, pos, prods])

  useDeskKeyboard({
    enabled: !newExpanded && (tab === 'purchase' || tab === 'production'),
    itemIds: keyboardIds,
    selectedId,
    setSelectedId,
    onToggleNew: tab === 'inventory' ? undefined : requestToggleNew,
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
    if (tab === 'vendors') return t('scm.newVendor')
    if (tab === 'products') return t('scm.newProduct')
    if (tab === 'services') return t('scm.newService')
    if (tab === 'purchase') return t('scm.newPo')
    if (tab === 'production') return t('scm.newProduct') + 'ion order'
    if (tab === 'quality') return t('scm.newLot', { defaultValue: 'New lot / manual QA' })
    return t('desk.new', { ns: 'common', defaultValue: 'New' })
  }, [tab, t])

  const hasSelection = useMemo(() => {
    if (tab === 'purchase') return Boolean(selectedPo?.id)
    if (tab === 'production') return Boolean(selectedProd?.id)
    return false
  }, [tab, selectedPo?.id, selectedProd?.id])

  const processCode = isBuyers ? 'SCM_PO_FULFILL' : 'SCM_MAKE_TO_STOCK'

  const helpContext = useMemo(() => {
    const base = { desk: deskKey, tab, processCode }
    if (tab === 'purchase' && selectedPo) {
      return {
        ...base,
        resource: 'PurchaseOrder',
        objectId: selectedPo.id,
        status: selectedPo.poStatus,
        processCode: 'SCM_PO_FULFILL',
      }
    }
    if (tab === 'production' && selectedProd) {
      return {
        ...base,
        resource: 'ProductionOrder',
        objectId: selectedProd.id,
        status: selectedProd.prodStatus,
      }
    }
    return base
  }, [deskKey, tab, selectedPo, selectedProd, processCode])

  const processStatus =
    tab === 'purchase'
      ? selectedPo?.poStatus
      : tab === 'production'
        ? selectedProd?.prodStatus
        : null

  const processSlot =
    tab === 'purchase' || tab === 'production' ? (
      <DeskProcessSlot
        processCode={isBuyers ? 'SCM_PO_FULFILL' : 'SCM_MAKE_TO_STOCK'}
        status={processStatus}
      />
    ) : null

  const collapsedContentRail = (pkg, resource, id, onChanged) => (
    <details className="content-rail-collapse" style={{ marginTop: '1.25rem' }}>
      <summary className="btn btn-ghost" style={{ cursor: 'pointer', listStyle: 'inside' }}>
        {t('desk.attachmentsNotes', { ns: 'common', defaultValue: 'Attachments & notes' })}
      </summary>
      <ContentRail package={pkg} resource={resource} id={id} onChanged={onChanged} />
    </details>
  )

  const vendorCreatePanel = (
    <form className="form-grid" onSubmit={onCreateVendor}>
      <MasterEntityFields
        spec={vendorMasterSpec}
        form={vendorForm}
        onChange={onMasterField(setVendorForm)}
        disabled={!!busy}
        showDescription={false}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('scm.createVendor')}
      </button>
    </form>
  )

  const productCreatePanel = (
    <form className="form-grid" onSubmit={onCreateProduct}>
      <MasterEntityFields
        spec={productMasterSpec}
        form={productForm}
        onChange={onMasterField(setProductForm)}
        disabled={!!busy}
        showDescription={false}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('scm.createProduct')}
      </button>
    </form>
  )

  const serviceCreatePanel = (
    <form className="form-grid" onSubmit={onCreateService}>
      <MasterEntityFields
        spec={productMasterSpec}
        form={serviceForm}
        onChange={onMasterField(setServiceForm)}
        disabled={!!busy}
        showDescription={false}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('scm.createService')}
      </button>
    </form>
  )

  const poCreatePanel = (
    <form className="form-grid" onSubmit={onCreatePo}>
      <label>
        Vendor
        <VendorSelect
          value={poForm.vendorId}
          onChange={(id) => setPoForm((f) => ({ ...f, vendorId: id || '' }))}
          disabled={!!busy}
        />
      </label>
      <label>
        Document currency
        <CurrencySelect
          name="currencyCode"
          value={poForm.currencyCode}
          onChange={(e) =>
            setPoForm((f) => ({ ...f, currencyCode: e.target.value || 'EUR' }))
          }
          disabled={!!busy}
          required
        />
      </label>
      {poWarnings?.length > 0 && (
        <div className="banner-info" role="status">
          {poWarnings.map((w) => (
            <div key={w.code + w.message}>{w.message}</div>
          ))}
        </div>
      )}
      <DocumentLinesEditor
        forChannel="purchase"
        lines={poLines}
        onChange={setPoLines}
        disabled={!!busy}
      />
      <PendingContentComposer
        package="procurement"
        resource="PurchaseOrder"
        pending={pendingPoContent}
        onChange={setPendingPoContent}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('scm.createPo')}
      </button>
    </form>
  )

  const prodCreatePanel = (
    <>
      <form className="form-grid" onSubmit={onCreateProd}>
        <label>
          Product *
          <select
            required
            value={prodForm.productId}
            onChange={(e) => setProdForm((f) => ({ ...f, productId: e.target.value }))}
          >
            <option value="">— select —</option>
            {productOptions.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Qty
          <input
            value={prodForm.quantity}
            onChange={(e) => setProdForm((f) => ({ ...f, quantity: e.target.value }))}
          />
        </label>
        <PendingContentComposer
          package="production"
          resource="ProductionOrder"
          pending={pendingProdContent}
          onChange={setPendingProdContent}
        />
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> Create
        </button>
      </form>
      <h2 className="section-label">{t('scm.fromSalesOrder')}</h2>
      <form className="form-grid" onSubmit={onFromSales}>
        <label>
          Sales order id
          <input
            value={salesOrderId}
            onChange={(e) => setSalesOrderId(e.target.value)}
            placeholder={t('ph.numericId', { ns: 'common' })}
          />
        </label>
        <button type="submit" className="btn" disabled={!!busy || !salesOrderId}>
          {t('scm.createFromSales')}
        </button>
      </form>
    </>
  )

  const lotCreatePanel = (
    <>
      <p className="muted" style={{ marginTop: 0 }}>
        {t('scm.qualityHint', {
          defaultValue:
            'Production complete creates an OPEN lot automatically. Create a lot here to inspect stock already on hand (PASS does not double-post stock).',
        })}
      </p>
      <form className="stack-form" onSubmit={onCreateLot}>
        <label>
          {t('fields.catalogueItemId', { ns: 'common', defaultValue: 'Catalogue item' })} *
          <CatalogueItemSelect
            id="lotCatalogueItemId"
            forChannel="sale"
            nature="GOODS"
            value={lotForm.catalogueItemId}
            onChange={(id) =>
              setLotForm((f) => ({ ...f, catalogueItemId: id ? String(id) : '' }))
            }
          />
        </label>
        <label>
          {t('wb.qty', { ns: 'common' })}
          <input
            name="quantity"
            value={lotForm.quantity}
            onChange={(e) => setLotForm((f) => ({ ...f, quantity: e.target.value }))}
            inputMode="decimal"
            required
          />
        </label>
        <label>
          {t('fields.name', { ns: 'common' })}
          <input
            name="name"
            value={lotForm.name}
            onChange={(e) => setLotForm((f) => ({ ...f, name: e.target.value }))}
            placeholder={t('scm.lotNamePh', { defaultValue: 'Optional lot name' })}
          />
        </label>
        <label>
          {t('scm.checkNotes', { defaultValue: 'Check notes (used on next PASS/FAIL/HOLD)' })}
          <textarea
            name="notes"
            value={lotForm.notes}
            onChange={(e) => setLotForm((f) => ({ ...f, notes: e.target.value }))}
            rows={2}
          />
        </label>
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          {t('scm.createLot', { defaultValue: 'Create lot' })}
        </button>
      </form>
    </>
  )

  const newPanel =
    tab === 'vendors'
      ? vendorCreatePanel
      : tab === 'products'
        ? productCreatePanel
        : tab === 'services'
          ? serviceCreatePanel
          : tab === 'purchase'
            ? poCreatePanel
            : tab === 'production'
              ? prodCreatePanel
              : tab === 'quality'
                ? lotCreatePanel
                : null

  const vendorsList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('scm.vendorsCount', { count: vendors.length })}
      </h2>
      <ResponsiveDataList
        items={vendors}
        empty="—"
        fields={[
          { id: 'name', primary: true, render: (v) => v.name },
          { id: 'email', secondary: true, render: (v) => v.email || '—' },
        ]}
        actions={(v) => <ViewButton pkg="procurement" resource="Vendor" record={v} />}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('wb.name', { ns: 'common' })}</th>
                <th>{t('wb.email', { ns: 'common' })}</th>
                <th className="col-priority-low">CCY</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {vendors.map((v) => (
                <tr key={v.id}>
                  <td>{v.name}</td>
                  <td className="muted">{v.email || '—'}</td>
                  <td className="mono col-priority-low">{v.currencyCode}</td>
                  <td>
                    <ViewButton pkg="procurement" resource="Vendor" record={v} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={vendorPage.total}
        max={vendorPage.max}
        offset={vendorPage.offset}
        disabled={loading}
        onOffset={(o) => setVendorPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setVendorPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const productsList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('scm.productsCount', { count: goodsPage.total || products.length })}
      </h2>
      <ResponsiveDataList
        items={products}
        empty="—"
        fields={[
          { id: 'name', primary: true, render: (p) => p.name },
          { id: 'sku', secondary: true, render: (p) => p.code || p.sku || '—' },
          { id: 'nature', meta: true, render: (p) => p.nature || 'GOODS' },
        ]}
        actions={(p) => <ViewButton pkg="catalogue" resource="CatalogueItem" record={p} />}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('wb.name', { ns: 'common' })}</th>
                <th>SKU</th>
                <th className="col-priority-low">{t('wb.type', { ns: 'common' })}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  <td className="mono">{p.code || p.sku || '—'}</td>
                  <td className="muted col-priority-low">{p.nature || 'GOODS'}</td>
                  <td>
                    <ViewButton pkg="catalogue" resource="CatalogueItem" record={p} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={goodsPage.total}
        max={goodsPage.max}
        offset={goodsPage.offset}
        disabled={loading}
        onOffset={(o) => setGoodsPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setGoodsPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const servicesList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('scm.servicesCount', { count: servicesPage.total || services.length, defaultValue: `Services (${servicesPage.total || services.length})` })}
      </h2>
      <ResponsiveDataList
        items={services}
        empty="—"
        fields={[
          { id: 'name', primary: true, render: (s) => s.name },
          { id: 'sku', secondary: true, render: (s) => s.code || s.sku || '—' },
        ]}
        actions={(s) => <ViewButton pkg="catalogue" resource="CatalogueItem" record={s} />}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('wb.name', { ns: 'common' })}</th>
                <th>SKU</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {services.map((s) => (
                <tr key={s.id}>
                  <td>{s.name}</td>
                  <td className="mono">{s.code || s.sku || '—'}</td>
                  <td>
                    <ViewButton pkg="catalogue" resource="CatalogueItem" record={s} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={servicesPage.total}
        max={servicesPage.max}
        offset={servicesPage.offset}
        disabled={loading}
        onOffset={(o) => setServicesPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setServicesPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const purchaseList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        POs ({pos.length})
      </h2>
      <ListFilterBar
        q={poQ}
        onQChange={(next) => {
          setPoQ(next)
          setPoPage((pg) => ({ ...pg, offset: 0 }))
        }}
        placeholder={t('masterData.search', { ns: 'common', defaultValue: 'Search…' })}
        className="list-filter-bar--block"
      />
      <ResponsiveDataList
        items={pos}
        empty="—"
        selectedKey={selectedPo?.id}
        onSelect={(po) => setSelectedId(String(po.id))}
        sortFields={PO_SORT_MENU}
        sort={poSort}
        order={poOrder}
        onSortChange={onPoSortChange}
        fields={[
          {
            id: 'number',
            primary: true,
            render: (po) => (
              <>
                <span className="mono">{po.poNumber}</span>{' '}
                <span className={statusClass(po.poStatus)}>{po.poStatus}</span>
              </>
            ),
          },
          { id: 'vendor', secondary: true, render: (po) => po.vendorName || '—' },
          {
            id: 'eur',
            meta: true,
            render: (po) =>
              po.amountEur != null ? `${Number(po.amountEur).toFixed(2)} EUR` : '—',
          },
        ]}
        actions={(po) => (
          <ViewButton pkg="procurement" resource="PurchaseOrder" record={po} />
        )}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="poNumber"
                  label={t('wb.number', { ns: 'common' })}
                  sort={poSort}
                  order={poOrder}
                  onSortChange={onPoSortChange}
                />
                <ListSortHeader
                  field="vendorName"
                  label={t('wb.vendor', { ns: 'common' })}
                  sort={poSort}
                  order={poOrder}
                  onSortChange={onPoSortChange}
                />
                <ListSortHeader
                  field="amountEur"
                  label="EUR"
                  sort={poSort}
                  order={poOrder}
                  onSortChange={onPoSortChange}
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                <ListSortHeader
                  field="poStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={poSort}
                  order={poOrder}
                  onSortChange={onPoSortChange}
                />
                <th />
              </tr>
            </thead>
            <tbody>
              {pos.map((po) => (
                <tr
                  key={po.id}
                  data-selected={selectedPo?.id === po.id ? 'true' : undefined}
                  className={selectedPo?.id === po.id ? 'row-selected' : 'row-click'}
                  onClick={() => setSelectedId(String(po.id))}
                >
                  <td className="mono">{po.poNumber}</td>
                  <td>{po.vendorName || '—'}</td>
                  <td>{po.amountEur != null ? Number(po.amountEur).toFixed(2) : '—'}</td>
                  <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                    <ContentChannelIcons summary={poContentMap[po.id]} />
                  </td>
                  <td>
                    <span className={statusClass(po.poStatus)}>{po.poStatus}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <ViewButton pkg="procurement" resource="PurchaseOrder" record={po} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={poPage.total}
        max={poPage.max}
        offset={poPage.offset}
        disabled={loading}
        onOffset={(o) => setPoPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setPoPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const productionList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Orders ({prods.length})
      </h2>
      <ResponsiveDataList
        items={prods}
        empty="—"
        selectedKey={selectedProd?.id}
        onSelect={(o) => setSelectedId(String(o.id))}
        fields={[
          {
            id: 'number',
            primary: true,
            render: (o) => (
              <>
                <span className="mono">{o.orderNumber}</span>{' '}
                <span className={statusClass(o.prodStatus)}>{o.prodStatus}</span>
              </>
            ),
          },
          { id: 'product', secondary: true, render: (o) => o.productName },
          { id: 'qty', meta: true, render: (o) => `Qty ${o.quantity}` },
        ]}
        actions={(o) => (
          <ViewButton pkg="production" resource="ProductionOrder" record={o} />
        )}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('wb.number', { ns: 'common' })}</th>
                <th>{t('wb.product', { ns: 'common' })}</th>
                <th>{t('wb.qty', { ns: 'common' })}</th>
                <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                <th>{t('wb.status', { ns: 'common' })}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {prods.map((o) => (
                <tr
                  key={o.id}
                  data-selected={selectedProd?.id === o.id ? 'true' : undefined}
                  className={selectedProd?.id === o.id ? 'row-selected' : 'row-click'}
                  onClick={() => setSelectedId(String(o.id))}
                >
                  <td className="mono">{o.orderNumber}</td>
                  <td>{o.productName}</td>
                  <td>{o.quantity}</td>
                  <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                    <ContentChannelIcons summary={prodContentMap[o.id]} />
                  </td>
                  <td>
                    <span className={statusClass(o.prodStatus)}>{o.prodStatus}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <ViewButton pkg="production" resource="ProductionOrder" record={o} />
                  </td>
                </tr>
              ))}
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

  const qualityList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('scm.lots')} ({lotPage.total || lots.length})
      </h2>
      <ResponsiveDataList
        items={lots}
        empty={t('scm.emptyLots')}
        fields={[
          {
            id: 'code',
            primary: true,
            render: (l) => (
              <>
                <span className="mono">{l.lotCode}</span>{' '}
                <span className={statusClass(l.lotStatus)}>{l.lotStatus}</span>
              </>
            ),
          },
          { id: 'product', secondary: true, render: (l) => l.productName },
          { id: 'qty', meta: true, render: (l) => `Qty ${l.quantity}` },
        ]}
        actions={(l) => renderLotCheckActions(l)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('wb.code', { ns: 'common' })}</th>
                <th>{t('wb.product', { ns: 'common' })}</th>
                <th>{t('wb.qty', { ns: 'common' })}</th>
                <th>{t('wb.status', { ns: 'common' })}</th>
                <th>{t('scm.checks')}</th>
              </tr>
            </thead>
            <tbody>
              {lots.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">
                    {t('scm.emptyLots')}
                  </td>
                </tr>
              )}
              {lots.map((l) => (
                <tr key={l.id}>
                  <td className="mono">{l.lotCode}</td>
                  <td>{l.productName}</td>
                  <td>{l.quantity}</td>
                  <td>
                    <span className={statusClass(l.lotStatus)}>{l.lotStatus}</span>
                    {l.sourceProductionOrderId ? (
                      <span className="muted" style={{ marginLeft: 6, fontSize: '0.75rem' }}>
                        MO{' '}
                        {labelFromList(prods, l.sourceProductionOrderId, {
                          fallback: '—',
                        })}
                      </span>
                    ) : null}
                  </td>
                  <td>{renderLotCheckActions(l) || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={lotPage.total}
        max={lotPage.max}
        offset={lotPage.offset}
        disabled={loading}
        onOffset={(o) => setLotPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setLotPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const inventoryList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        On hand (StockMove ledger)
      </h2>
      <ResponsiveDataList
        items={onHand}
        getKey={(row) => row.productId}
        empty={t('scm.emptyStock')}
        fields={[
          {
            id: 'product',
            primary: true,
            render: (row) => productName(row.productId),
          },
          {
            id: 'onHand',
            secondary: true,
            render: (row) => (
              <span className="mono">{Number(row.onHand)} on hand</span>
            ),
          },
        ]}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('wb.product', { ns: 'common' })}</th>
                <th>{t('scm.onHand')}</th>
              </tr>
            </thead>
            <tbody>
              {onHand.length === 0 && (
                <tr>
                  <td colSpan={2} className="muted">
                    {t('scm.emptyStock')}
                  </td>
                </tr>
              )}
              {onHand.map((row) => (
                <tr key={row.productId}>
                  <td>{productName(row.productId)}</td>
                  <td className="mono">{Number(row.onHand)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
    </>
  )

  const list =
    tab === 'vendors'
      ? vendorsList
      : tab === 'products'
        ? productsList
        : tab === 'services'
          ? servicesList
          : tab === 'purchase'
            ? purchaseList
            : tab === 'production'
              ? productionList
              : tab === 'quality'
                ? qualityList
                : tab === 'inventory'
                  ? inventoryList
                  : null

  let detail = null
  let detailFooter = null

  if (tab === 'purchase' && selectedPo) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <div className="inv-detail-hero">
          <div>
            <div className="about-display" style={{ fontSize: '1.35rem' }}>
              {selectedPo.poNumber}
            </div>
            <div className="muted">
              {selectedPo.vendorName || t('wb.noVendor', { ns: 'common' })} ·{' '}
              <span className={statusClass(selectedPo.poStatus)}>{selectedPo.poStatus}</span>
              {selectedPo.currencyCode ? ` · ${selectedPo.currencyCode}` : ''}
            </div>
          </div>
          <div className="inv-amount">
            {Number(selectedPo.amountEur || 0).toFixed(2)}{' '}
            {selectedPo.currencyCode || 'EUR'}
            {selectedPo.amountSats != null && (
              <div className="muted mono" style={{ fontSize: '0.8rem' }}>
                {selectedPo.amountSats} sats
              </div>
            )}
          </div>
        </div>
        {(selectedPo.warnings || poWarnings)?.length > 0 && (
          <div className="banner-info" role="status" style={{ marginBottom: 8 }}>
            {(selectedPo.warnings || poWarnings).map((w) => (
              <div key={w.code + w.message}>{w.message}</div>
            ))}
          </div>
        )}
        <div className="toolbar">
          {['DRAFT'].includes(selectedPo.poStatus) && (
            <button type="button" className="btn" onClick={() => runPo('send')} disabled={!!busy}>
              <Send size={16} /> Send
            </button>
          )}
          {['CONFIRMED', 'PART_RECEIVED', 'SENT'].includes(selectedPo.poStatus) && (
            <button type="button" className="btn" onClick={() => runPo('receive')} disabled={!!busy}>
              <Truck size={16} /> Receive → stock
            </button>
          )}
          {selectedPo.financePayableId && (
            <Link className="btn btn-secondary" to="/finance/payables">
              Payable →
            </Link>
          )}
          <a
            className="btn"
            href={scmApi.purchaseOrders.pdfUrl(selectedPo.id)}
            target="_blank"
            rel="noreferrer"
          >
            <FileText size={16} /> Download purchase order PDF
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
                <th>{t('scm.rcv')}</th>
                <th>{t('wb.total', { ns: 'common' })}</th>
              </tr>
            </thead>
            <tbody>
              {(selectedPo.lines || []).map((l) => (
                <tr key={l.id || l.lineNo}>
                  <td>{l.lineNo}</td>
                  <td>{l.name}</td>
                  <td>{l.quantity}</td>
                  <td>{l.quantityReceived}</td>
                  <td>{Number(l.lineTotalEur).toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {collapsedContentRail('procurement', 'PurchaseOrder', selectedPo.id, async () =>
          setPoContentMap(await fetchContentMap('procurement', 'PurchaseOrder', pos)),
        )}
      </div>
    )
    if (['DRAFT', 'SENT'].includes(selectedPo.poStatus)) {
      detailFooter = (
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => runPo('confirm')}
          disabled={!!busy}
        >
          <Check size={16} /> Confirm → payable
        </button>
      )
    }
  }

  if (tab === 'production' && selectedProd) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <div className="inv-detail-hero">
          <div>
            <div className="about-display" style={{ fontSize: '1.35rem' }}>
              {selectedProd.orderNumber}
            </div>
            <div className="muted">
              {selectedProd.productName} · qty {selectedProd.quantity} ·{' '}
              <span className={statusClass(selectedProd.prodStatus)}>
                {selectedProd.prodStatus}
              </span>
            </div>
            {selectedProd.sourceSalesOrderId && (
              <div className="muted" style={{ fontSize: '0.85rem' }}>
                From sales order
              </div>
            )}
          </div>
        </div>
        <div className="toolbar">
          {selectedProd.prodStatus === 'DRAFT' && (
            <button type="button" className="btn" onClick={() => runProd('release')} disabled={!!busy}>
              Release
            </button>
          )}
          {['DRAFT', 'RELEASED'].includes(selectedProd.prodStatus) && (
            <button type="button" className="btn" onClick={() => runProd('start')} disabled={!!busy}>
              Start
            </button>
          )}
          <a
            className="btn"
            href={scmApi.production.pdfUrl(selectedProd.id)}
            target="_blank"
            rel="noreferrer"
          >
            <FileText size={16} /> Download production order PDF
          </a>
        </div>
        {collapsedContentRail('production', 'ProductionOrder', selectedProd.id, async () =>
          setProdContentMap(await fetchContentMap('production', 'ProductionOrder', prods)),
        )}
      </div>
    )
    if (
      selectedProd.prodStatus !== 'COMPLETED' &&
      selectedProd.prodStatus !== 'CANCELLED'
    ) {
      detailFooter = (
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => runProd('complete')}
          disabled={!!busy}
        >
          <Check size={16} /> {t('scm.completeToQa', { defaultValue: 'Complete → QA lot' })}
        </button>
      )
    }
  }

  const emptyTitle =
    tab === 'purchase'
      ? t('scm.selectPo')
      : tab === 'production'
        ? t('scm.selectOrder')
        : t('desk.emptyTitle', { ns: 'common' })
  const emptyBody =
    tab === 'purchase' || tab === 'production'
      ? t('desk.emptyBody', { ns: 'common' })
      : t('desk.emptyBodyMasters', {
          ns: 'common',
          defaultValue: 'Use + New to create, or View on a row for the full record.',
        })

  return (
    <div className={`page scm-desk-page scm-desk-${isBuyers ? 'buyers' : 'shopfloor'}`}>
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}

      <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        {isBuyers ? (
          <>
            <Link className="btn btn-secondary" to="/finance/payables">
              {t('scm.financePayables')}
            </Link>
            <Link className="btn btn-secondary" to="/sales">
              {t('scm.salesDesk')}
            </Link>
            <Link className="btn btn-secondary" to="/scm/shopfloor">
              {t('scm.shopFloorLink')}
            </Link>
          </>
        ) : (
          <>
            <Link className="btn btn-secondary" to="/scm/buyers">
              {t('scm.buyersLink')}
            </Link>
            <Link className="btn btn-secondary" to="/sales">
              {t('scm.salesDesk')}
            </Link>
          </>
        )}
      </div>

      <DeskShell
        title={
          <>
            <DeskIcon size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {deskTitle}
          </>
        }
        subtitle={deskSubtitle}
        tabs={tabs}
        activeTab={tab}
        onTabChange={requestTabChange}
        newLabel={tab === 'inventory' ? undefined : newLabel}
        newExpanded={newExpanded}
        onToggleNew={tab === 'inventory' ? undefined : requestToggleNew}
        newPanel={newPanel}
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

      {tab !== 'inventory' && (
        <DeskNewFab label={newLabel} onClick={() => !newExpanded && requestToggleNew()} />
      )}

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
