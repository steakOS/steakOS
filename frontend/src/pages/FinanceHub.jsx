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
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  Wallet,
  Receipt,
  PieChart,
  Landmark,
  Users,
  Shield,
  RefreshCw,
  Eye,
  Pencil,
} from 'lucide-react'
import { financeOpsApi, meApi } from '../api/client'
import { allowsSilverSurfaces } from '../utils/silverSurfaces'
import { hasMasterFormSpec } from '../forms/masterFormSpecs'
import useRecordView from '../hooks/useRecordView'
import ResponsiveDataList from '../components/ResponsiveDataList'
import RowActionSlots from '../components/RowActionSlots'
import DeskNewFab from '../components/DeskNewFab'
import DeskShell from '../components/desk/DeskShell'
import ConfirmDialog from '../components/ConfirmDialog'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'
import StubBadge from '../components/StubBadge'
import useDeskSelection, { scrollSelectedIntoView } from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'
import useNarrowViewport from '../hooks/useNarrowViewport'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'

const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'
const ACTION_TH = (t) => t('wb.action', { ns: 'common', defaultValue: 'Action' })

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

const CREATE_TABS = new Set(['payables', 'budgets', 'payroll'])

/**
 * F-S3–F-S6 combined simple UI (FinanceBasics ops).
 * DeskShell layout (ADR-047 / Nielsen WP-0, WP-2) — plan §4.3.
 */
export default function FinanceHub() {
  const { t } = useTranslation(['desks', 'common'])
  const navigate = useNavigate()
  const narrow = useNarrowViewport()
  const loadTabRef = useRef(null)
  const { viewModal, ViewButton, openView, openEdit } = useRecordView({
    modalEdit: true,
    canModalEdit: ({ pkg, resource }) => hasMasterFormSpec(pkg, resource),
    onSaved: () => {
      loadTabRef.current?.().catch(() => {})
    },
  })
  const renderRowActions = useCallback(
    (pkg, resource, record) => {
      if (!record?.id && record?.id !== 0) return null
      const canEdit = hasMasterFormSpec(pkg, resource)
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
                openView(pkg, resource, record)
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
          delete={null}
        />
      )
    },
    [openEdit, openView, t],
  )
  const TABS = useMemo(
    () => TAB_DEFS.map((d) => ({ ...d, label: t(d.labelKey) })),
    [t],
  )
  const tabs = useMemo(
    () => TABS.map((s) => ({ id: s.id, label: s.label, icon: s.icon })),
    [TABS],
  )
  const { section } = useParams()
  const tab = TABS.some((tabDef) => tabDef.id === section) ? section : 'treasury'

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
    deskKey: 'finance-hub',
    companyId,
  })

  const setTab = useCallback(
    (next) => {
      if (!next || next === tab) return
      navigate(`/finance/${next}`)
    },
    [navigate, tab],
  )

  useDeskSelection({
    deskKey: 'finance-hub',
    defaultTab: 'treasury',
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

  // Command palette / deep-link: ?new=1 expands create once
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.get('new') === '1' && CREATE_TABS.has(tab)) {
      setNewExpanded(true)
      params.delete('new')
      const qs = params.toString()
      window.history.replaceState(
        null,
        '',
        `${window.location.pathname}${qs ? `?${qs}` : ''}`,
      )
    }
  }, [tab])

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
  const [treasurySort, setTreasurySort] = useState('accountKind')
  const [treasuryOrder, setTreasuryOrder] = useState('asc')
  const [payables, setPayables] = useState([])
  const [payablePage, setPayablePage] = useState({ max: 25, offset: 0, total: 0 })
  const [payableSort, setPayableSort] = useState('id')
  const [payableOrder, setPayableOrder] = useState('desc')
  const [budgets, setBudgets] = useState([])
  const [budgetPage, setBudgetPage] = useState({ max: 25, offset: 0, total: 0 })
  const [budgetSort, setBudgetSort] = useState('period')
  const [budgetOrder, setBudgetOrder] = useState('desc')
  const [variance, setVariance] = useState(null)
  const [taxes, setTaxes] = useState([])
  const [taxPage, setTaxPage] = useState({ max: 25, offset: 0, total: 0 })
  const [taxSort, setTaxSort] = useState('period')
  const [taxOrder, setTaxOrder] = useState('desc')
  const [payroll, setPayroll] = useState([])
  const [payrollPage, setPayrollPage] = useState({ max: 25, offset: 0, total: 0 })
  const [payrollSort, setPayrollSort] = useState('id')
  const [payrollOrder, setPayrollOrder] = useState('desc')
  const [audit, setAudit] = useState([])
  const [auditPage, setAuditPage] = useState({ max: 25, offset: 0, total: 0 })
  const [auditSort, setAuditSort] = useState('id')
  const [auditOrder, setAuditOrder] = useState('desc')
  const [periodClosed, setPeriodClosed] = useState(false)

  const TREASURY_SORT_MENU = useMemo(
    () => [
      { value: 'accountKind', label: t('wb.kind', { ns: 'common' }) },
      { value: 'name', label: t('wb.name', { ns: 'common' }) },
      { value: 'balanceEur', label: t('finance.bookBalance') },
    ],
    [t],
  )
  const PAYABLE_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'vendorName', label: t('wb.vendor', { ns: 'common' }) },
      { value: 'amountEur', label: 'EUR' },
      { value: 'payableStatus', label: t('wb.status', { ns: 'common' }) },
    ],
    [t],
  )
  const BUDGET_SORT_MENU = useMemo(
    () => [
      { value: 'period', label: t('wb.period', { ns: 'common' }) },
      { value: 'name', label: t('wb.name', { ns: 'common' }) },
      { value: 'amountEur', label: 'EUR' },
    ],
    [t],
  )
  const TAX_SORT_MENU = useMemo(
    () => [
      { value: 'period', label: t('wb.period', { ns: 'common' }) },
      { value: 'jurisdiction', label: t('finance.jurisdiction') },
      { value: 'amountEur', label: t('wb.amount', { ns: 'common' }) },
      { value: 'taxStatus', label: t('wb.status', { ns: 'common' }) },
    ],
    [t],
  )
  const PAYROLL_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'personRef', label: t('finance.personRef') },
      { value: 'period', label: t('wb.period', { ns: 'common' }) },
      { value: 'amountEur', label: 'EUR' },
      { value: 'payrollStatus', label: t('wb.status', { ns: 'common' }) },
    ],
    [t],
  )
  const AUDIT_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: t('wb.name', { ns: 'common' }) },
      { value: 'period', label: t('wb.period', { ns: 'common' }) },
      { value: 'sourceType', label: t('finance.source') },
    ],
    [t],
  )

  const onTreasurySortChange = useCallback(({ sort: s, order: o }) => {
    setTreasurySort(s || 'accountKind')
    setTreasuryOrder(o === 'desc' ? 'desc' : 'asc')
    setTreasuryPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onPayableSortChange = useCallback(({ sort: s, order: o }) => {
    setPayableSort(s || 'id')
    setPayableOrder(o === 'asc' ? 'asc' : 'desc')
    setPayablePage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onBudgetSortChange = useCallback(({ sort: s, order: o }) => {
    setBudgetSort(s || 'period')
    setBudgetOrder(o === 'asc' ? 'asc' : 'desc')
    setBudgetPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onTaxSortChange = useCallback(({ sort: s, order: o }) => {
    setTaxSort(s || 'period')
    setTaxOrder(o === 'asc' ? 'asc' : 'desc')
    setTaxPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onPayrollSortChange = useCallback(({ sort: s, order: o }) => {
    setPayrollSort(s || 'id')
    setPayrollOrder(o === 'asc' ? 'asc' : 'desc')
    setPayrollPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onAuditSortChange = useCallback(({ sort: s, order: o }) => {
    setAuditSort(s || 'id')
    setAuditOrder(o === 'asc' ? 'asc' : 'desc')
    setAuditPage((pg) => ({ ...pg, offset: 0 }))
  }, [])

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
          sort: treasurySort,
          order: treasuryOrder,
        })
        setPositions(d.items || [])
        applyPage(setTreasuryPage, d)
      } else if (tab === 'payables') {
        const d = await financeOpsApi.payableList({
          max: payablePage.max,
          offset: payablePage.offset,
          sort: payableSort,
          order: payableOrder,
        })
        setPayables(d.items || [])
        applyPage(setPayablePage, d)
      } else if (tab === 'budgets') {
        const d = await financeOpsApi.budgetList({
          max: budgetPage.max,
          offset: budgetPage.offset,
          sort: budgetSort,
          order: budgetOrder,
        })
        setBudgets(d.items || [])
        applyPage(setBudgetPage, d)
        const v = await financeOpsApi.budgetVariance(period)
        setVariance(v)
      } else if (tab === 'tax') {
        const d = await financeOpsApi.taxList({
          max: taxPage.max,
          offset: taxPage.offset,
          sort: taxSort,
          order: taxOrder,
        })
        setTaxes(d.items || [])
        applyPage(setTaxPage, d)
      } else if (tab === 'payroll') {
        const d = await financeOpsApi.payrollList({
          max: payrollPage.max,
          offset: payrollPage.offset,
          sort: payrollSort,
          order: payrollOrder,
        })
        setPayroll(d.items || [])
        applyPage(setPayrollPage, d)
      } else if (tab === 'control') {
        const a = await financeOpsApi.controlAudit({
          max: auditPage.max,
          offset: auditPage.offset,
          sort: auditSort,
          order: auditOrder,
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
    treasurySort,
    treasuryOrder,
    payablePage.max,
    payablePage.offset,
    payableSort,
    payableOrder,
    budgetPage.max,
    budgetPage.offset,
    budgetSort,
    budgetOrder,
    taxPage.max,
    taxPage.offset,
    taxSort,
    taxOrder,
    payrollPage.max,
    payrollPage.offset,
    payrollSort,
    payrollOrder,
    auditPage.max,
    auditPage.offset,
    auditSort,
    auditOrder,
  ])

  loadTabRef.current = loadTab

  useEffect(() => {
    loadTab()
  }, [loadTab])

  // Clear selection when it no longer exists in the active list
  useEffect(() => {
    if (!selectedId) return
    const list =
      tab === 'treasury'
        ? positions
        : tab === 'payables'
          ? payables
          : tab === 'budgets'
            ? budgets
            : tab === 'tax'
              ? taxes
              : tab === 'payroll'
                ? payroll
                : audit
    if (!list.length) return
    const still = list.some((row) => String(row.id) === String(selectedId))
    if (!still) setSelectedId(null)
  }, [tab, selectedId, positions, payables, budgets, taxes, payroll, audit])

  useEffect(() => {
    if (!selectedId) return
    const id = window.requestAnimationFrame(() => {
      scrollSelectedIntoView('.desk-shell-list')
    })
    return () => window.cancelAnimationFrame(id)
  }, [selectedId, tab])

  const selected = useMemo(() => {
    const list =
      tab === 'treasury'
        ? positions
        : tab === 'payables'
          ? payables
          : tab === 'budgets'
            ? budgets
            : tab === 'tax'
              ? taxes
              : tab === 'payroll'
                ? payroll
                : audit
    return list.find((row) => String(row.id) === String(selectedId)) || null
  }, [tab, selectedId, positions, payables, budgets, taxes, payroll, audit])

  const resetCreateForms = useCallback(() => {
    setApForm({ vendorName: '', amountEur: '50', name: '' })
    setBudForm({ name: '', amountEur: '1000', period: periodNow() })
    setPayForm({ personRef: '', amountEur: '100', period: periodNow() })
  }, [])

  const createDirty = useMemo(() => {
    if (tab === 'payables') {
      return Boolean(
        (apForm.vendorName || '').trim() ||
          (apForm.name || '').trim() ||
          apForm.amountEur !== '50',
      )
    }
    if (tab === 'budgets') {
      return Boolean(
        (budForm.name || '').trim() ||
          budForm.amountEur !== '1000' ||
          budForm.period !== periodNow(),
      )
    }
    if (tab === 'payroll') {
      return Boolean(
        (payForm.personRef || '').trim() ||
          payForm.amountEur !== '100' ||
          payForm.period !== periodNow(),
      )
    }
    return false
  }, [tab, apForm, budForm, payForm])

  const applyCollapseCreate = useCallback(() => {
    setNewExpanded(false)
    resetCreateForms()
  }, [resetCreateForms])

  const requestToggleNew = useCallback(() => {
    if (!CREATE_TABS.has(tab)) return
    if (newExpanded && createDirty) {
      setPendingNav({ type: 'collapse' })
      setDiscardOpen(true)
      return
    }
    if (newExpanded) applyCollapseCreate()
    else setNewExpanded(true)
  }, [tab, newExpanded, createDirty, applyCollapseCreate])

  const clearSelection = useCallback(() => setSelectedId(null), [])

  const requestTabChange = useCallback(
    (nextTab) => {
      if (nextTab === tab) return
      if (newExpanded && createDirty) {
        setPendingNav({ type: 'tab', tab: nextTab })
        setDiscardOpen(true)
        return
      }
      setNewExpanded(false)
      resetCreateForms()
      clearSelection()
      navigate(`/finance/${nextTab}`)
    },
    [tab, newExpanded, createDirty, resetCreateForms, clearSelection, navigate],
  )

  const confirmDiscard = useCallback(() => {
    const nav = pendingNav
    setDiscardOpen(false)
    setPendingNav(null)
    applyCollapseCreate()
    if (nav?.type === 'tab' && nav.tab) {
      clearSelection()
      navigate(`/finance/${nav.tab}`)
    }
  }, [pendingNav, applyCollapseCreate, clearSelection, navigate])

  const cancelDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
  }, [])

  const keyboardIds = useMemo(() => {
    if (tab === 'treasury') return positions.map((p) => p.id)
    if (tab === 'payables') return payables.map((p) => p.id)
    if (tab === 'budgets') return budgets.map((b) => b.id)
    if (tab === 'tax') return taxes.map((x) => x.id)
    if (tab === 'payroll') return payroll.map((p) => p.id)
    if (tab === 'control') return audit.map((a) => a.id)
    return []
  }, [tab, positions, payables, budgets, taxes, payroll, audit])

  useDeskKeyboard({
    enabled: !newExpanded,
    itemIds: keyboardIds,
    selectedId,
    setSelectedId,
    onToggleNew: CREATE_TABS.has(tab) ? requestToggleNew : undefined,
  })

  const sectionMeta = TABS.find((s) => s.id === tab)
  const SectionIcon = sectionMeta?.icon || Wallet

  const newLabel = useMemo(() => {
    if (tab === 'payables') return t('finance.newPayable')
    if (tab === 'budgets') return t('finance.newBudget')
    if (tab === 'payroll') return t('finance.newPayrollLine')
    return t('desk.new', { ns: 'common', defaultValue: 'New' })
  }, [tab, t])

  const hasSelection = Boolean(selected?.id)

  const helpContext = useMemo(() => {
    const base = { desk: 'finance-hub', tab, path: `/finance/${tab}` }
    if (!selected) return base
    const resource =
      tab === 'treasury'
        ? 'Treasury'
        : tab === 'payables'
          ? 'Payable'
          : tab === 'budgets'
            ? 'Budget'
            : tab === 'tax'
              ? 'Tax'
              : tab === 'payroll'
                ? 'PayrollItem'
                : 'JournalEntry'
    const status =
      selected.payableStatus ||
      selected.taxStatus ||
      selected.payrollStatus ||
      selected.accountKind ||
      undefined
    return {
      ...base,
      resource,
      objectId: selected.id,
      status,
    }
  }, [tab, selected])

  // —— create panels ——
  let newPanel = null
  if (tab === 'payables') {
    newPanel = (
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run(async () => {
            const created = await financeOpsApi.payableCreate(apForm)
            await loadTab()
            setApForm({ vendorName: '', amountEur: '50', name: '' })
            setNewExpanded(false)
            if (created?.id != null) setSelectedId(String(created.id))
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
    )
  } else if (tab === 'budgets') {
    newPanel = (
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run(async () => {
            const created = await financeOpsApi.budgetCreate(budForm)
            await loadTab()
            setBudForm({ name: '', amountEur: '1000', period: periodNow() })
            setNewExpanded(false)
            if (created?.id != null) setSelectedId(String(created.id))
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
    )
  } else if (tab === 'payroll') {
    newPanel = (
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run(async () => {
            const created = await financeOpsApi.payrollCreate(payForm)
            await loadTab()
            setPayForm({ personRef: '', amountEur: '100', period: periodNow() })
            setNewExpanded(false)
            if (created?.id != null) setSelectedId(String(created.id))
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
        <p className="muted" style={{ fontSize: '0.85rem', margin: 0 }}>
          {t('finance.payrollHint')}
        </p>
      </form>
    )
  }

  // —— lists ——
  let list = null
  let listPager = null

  if (tab === 'treasury') {
    listPager = (
      <ListPager
        total={treasuryPage.total}
        max={treasuryPage.max}
        offset={treasuryPage.offset}
        disabled={busy}
        onOffset={(o) => setTreasuryPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setTreasuryPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    )
    list = (
      <>
        <ResponsiveDataList
          items={positions}
          empty={t('finance.seedPositionsEmpty')}
          selectedKey={selected?.id}
          onSelect={(p) => setSelectedId(String(p.id))}
          sortFields={TREASURY_SORT_MENU}
          sort={treasurySort}
          order={treasuryOrder}
          defaultSort="accountKind"
          defaultOrder="asc"
          onSortChange={onTreasurySortChange}
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
          actions={(p) => renderRowActions('finance', 'Treasury', p)}
        >
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <ListSortHeader
                    field="name"
                    label={t('wb.name', { ns: 'common' })}
                    sort={treasurySort}
                    order={treasuryOrder}
                    defaultSort="accountKind"
                    defaultOrder="asc"
                    onSortChange={onTreasurySortChange}
                  />
                  <ListSortHeader
                    field="accountKind"
                    label={t('wb.kind', { ns: 'common' })}
                    sort={treasurySort}
                    order={treasuryOrder}
                    defaultSort="accountKind"
                    defaultOrder="asc"
                    onSortChange={onTreasurySortChange}
                    className="col-priority-low"
                  />
                  <ListSortHeader
                    field="balanceEur"
                    label={t('finance.bookBalance')}
                    sort={treasurySort}
                    order={treasuryOrder}
                    defaultSort="accountKind"
                    defaultOrder="asc"
                    onSortChange={onTreasurySortChange}
                  />
                  <th className="col-priority-low">{t('finance.sats')}</th>
                  <th className="col-priority-low">{t('finance.coa')}</th>
                  <th>{ACTION_TH(t)}</th>
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
                  <tr
                    key={p.id}
                    data-selected={selected?.id === p.id ? 'true' : undefined}
                    className={selected?.id === p.id ? 'row-selected' : 'row-click'}
                    onClick={() => setSelectedId(String(p.id))}
                  >
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
                    <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                      {renderRowActions('finance', 'Treasury', p)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </ResponsiveDataList>
        {listPager}
        <p className="muted" style={{ fontSize: '0.85rem' }}>
          {t('finance.treasuryDualHint')}
        </p>
      </>
    )
  } else if (tab === 'payables') {
    list = (
      <>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {t('finance.openPaid')}
        </h2>
        <ResponsiveDataList
          items={payables}
          empty="—"
          selectedKey={selected?.id}
          onSelect={(p) => setSelectedId(String(p.id))}
          sortFields={PAYABLE_SORT_MENU}
          sort={payableSort}
          order={payableOrder}
          onSortChange={onPayableSortChange}
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
          actions={(p) => renderRowActions('finance', 'Payable', p)}
        >
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <ListSortHeader
                    field="vendorName"
                    label={t('wb.vendor', { ns: 'common' })}
                    sort={payableSort}
                    order={payableOrder}
                    onSortChange={onPayableSortChange}
                  />
                  <ListSortHeader
                    field="amountEur"
                    label="EUR"
                    sort={payableSort}
                    order={payableOrder}
                    onSortChange={onPayableSortChange}
                  />
                  <ListSortHeader
                    field="payableStatus"
                    label={t('wb.status', { ns: 'common' })}
                    sort={payableSort}
                    order={payableOrder}
                    onSortChange={onPayableSortChange}
                  />
                  <th>{ACTION_TH(t)}</th>
                </tr>
              </thead>
              <tbody>
                {payables.map((p) => (
                  <tr
                    key={p.id}
                    data-selected={selected?.id === p.id ? 'true' : undefined}
                    className={selected?.id === p.id ? 'row-selected' : 'row-click'}
                    onClick={() => setSelectedId(String(p.id))}
                  >
                    <td>{p.vendorName || p.name}</td>
                    <td>{Number(p.amountEur).toFixed(2)}</td>
                    <td>{p.payableStatus}</td>
                    <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                      {renderRowActions('finance', 'Payable', p)}
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
      </>
    )
  } else if (tab === 'budgets') {
    list = (
      <>
        <ResponsiveDataList
          items={budgets}
          empty="—"
          selectedKey={selected?.id}
          onSelect={(b) => setSelectedId(String(b.id))}
          sortFields={BUDGET_SORT_MENU}
          sort={budgetSort}
          order={budgetOrder}
          defaultSort="period"
          defaultOrder="desc"
          onSortChange={onBudgetSortChange}
          fields={[
            {
              id: 'name',
              primary: true,
              render: (b) => b.name || t('finance.budgetFallback'),
            },
            {
              id: 'period',
              secondary: true,
              render: (b) => <span className="mono">{b.period}</span>,
            },
            {
              id: 'eur',
              meta: true,
              render: (b) => `${Number(b.amountEur).toFixed(2)} EUR`,
            },
          ]}
          actions={(b) => renderRowActions('finance', 'Budget', b)}
        >
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <ListSortHeader
                    field="period"
                    label={t('wb.period', { ns: 'common' })}
                    sort={budgetSort}
                    order={budgetOrder}
                    defaultSort="period"
                    defaultOrder="desc"
                    onSortChange={onBudgetSortChange}
                  />
                  <ListSortHeader
                    field="name"
                    label={t('wb.name', { ns: 'common' })}
                    sort={budgetSort}
                    order={budgetOrder}
                    defaultSort="period"
                    defaultOrder="desc"
                    onSortChange={onBudgetSortChange}
                  />
                  <ListSortHeader
                    field="amountEur"
                    label="EUR"
                    sort={budgetSort}
                    order={budgetOrder}
                    defaultSort="period"
                    defaultOrder="desc"
                    onSortChange={onBudgetSortChange}
                  />
                  <th>{ACTION_TH(t)}</th>
                </tr>
              </thead>
              <tbody>
                {budgets.map((b) => (
                  <tr
                    key={b.id}
                    data-selected={selected?.id === b.id ? 'true' : undefined}
                    className={selected?.id === b.id ? 'row-selected' : 'row-click'}
                    onClick={() => setSelectedId(String(b.id))}
                  >
                    <td className="mono">{b.period}</td>
                    <td>{b.name || t('finance.budgetFallback')}</td>
                    <td>{Number(b.amountEur).toFixed(2)}</td>
                    <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                      {renderRowActions('finance', 'Budget', b)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </ResponsiveDataList>
        <ListPager
          total={budgetPage.total}
          max={budgetPage.max}
          offset={budgetPage.offset}
          disabled={busy}
          onOffset={(o) => setBudgetPage((pg) => ({ ...pg, offset: o }))}
          onMax={(m) => setBudgetPage((pg) => ({ ...pg, max: m, offset: 0 }))}
        />
      </>
    )
  } else if (tab === 'tax') {
    list = (
      <>
        <ResponsiveDataList
          items={taxes}
          empty="—"
          selectedKey={selected?.id}
          onSelect={(taxRow) => setSelectedId(String(taxRow.id))}
          sortFields={TAX_SORT_MENU}
          sort={taxSort}
          order={taxOrder}
          defaultSort="period"
          defaultOrder="desc"
          onSortChange={onTaxSortChange}
          fields={[
            {
              id: 'period',
              primary: true,
              render: (taxRow) => <span className="mono">{taxRow.period}</span>,
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
          actions={(taxRow) => renderRowActions('finance', 'Tax', taxRow)}
        >
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <ListSortHeader
                    field="period"
                    label={t('wb.period', { ns: 'common' })}
                    sort={taxSort}
                    order={taxOrder}
                    defaultSort="period"
                    defaultOrder="desc"
                    onSortChange={onTaxSortChange}
                  />
                  <ListSortHeader
                    field="jurisdiction"
                    label={t('finance.jurisdiction')}
                    sort={taxSort}
                    order={taxOrder}
                    defaultSort="period"
                    defaultOrder="desc"
                    onSortChange={onTaxSortChange}
                    className="col-priority-low"
                  />
                  <ListSortHeader
                    field="amountEur"
                    label={t('wb.amount', { ns: 'common' })}
                    sort={taxSort}
                    order={taxOrder}
                    defaultSort="period"
                    defaultOrder="desc"
                    onSortChange={onTaxSortChange}
                  />
                  <ListSortHeader
                    field="taxStatus"
                    label={t('wb.status', { ns: 'common' })}
                    sort={taxSort}
                    order={taxOrder}
                    defaultSort="period"
                    defaultOrder="desc"
                    onSortChange={onTaxSortChange}
                  />
                  <th>{ACTION_TH(t)}</th>
                </tr>
              </thead>
              <tbody>
                {taxes.map((taxRow) => (
                  <tr
                    key={taxRow.id}
                    data-selected={selected?.id === taxRow.id ? 'true' : undefined}
                    className={selected?.id === taxRow.id ? 'row-selected' : 'row-click'}
                    onClick={() => setSelectedId(String(taxRow.id))}
                  >
                    <td className="mono">{taxRow.period}</td>
                    <td className="col-priority-low">{taxRow.jurisdiction}</td>
                    <td>{Number(taxRow.amountEur).toFixed(2)}</td>
                    <td>{taxRow.taxStatus}</td>
                    <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                      {renderRowActions('finance', 'Tax', taxRow)}
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
      </>
    )
  } else if (tab === 'payroll') {
    list = (
      <>
        <ResponsiveDataList
          items={payroll}
          empty="—"
          selectedKey={selected?.id}
          onSelect={(p) => setSelectedId(String(p.id))}
          sortFields={PAYROLL_SORT_MENU}
          sort={payrollSort}
          order={payrollOrder}
          onSortChange={onPayrollSortChange}
          fields={[
            {
              id: 'person',
              primary: true,
              render: (p) => p.personRef || p.name,
            },
            {
              id: 'period',
              secondary: true,
              render: (p) => <span className="mono">{p.period}</span>,
            },
            {
              id: 'eur',
              meta: true,
              render: (p) => `${Number(p.amountEur).toFixed(2)} · ${p.payrollStatus}`,
            },
          ]}
          actions={(p) => renderRowActions('finance', 'PayrollItem', p)}
        >
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <ListSortHeader
                    field="personRef"
                    label={t('finance.personRef')}
                    sort={payrollSort}
                    order={payrollOrder}
                    onSortChange={onPayrollSortChange}
                  />
                  <ListSortHeader
                    field="period"
                    label={t('wb.period', { ns: 'common' })}
                    sort={payrollSort}
                    order={payrollOrder}
                    onSortChange={onPayrollSortChange}
                  />
                  <ListSortHeader
                    field="amountEur"
                    label="EUR"
                    sort={payrollSort}
                    order={payrollOrder}
                    onSortChange={onPayrollSortChange}
                  />
                  <ListSortHeader
                    field="payrollStatus"
                    label={t('wb.status', { ns: 'common' })}
                    sort={payrollSort}
                    order={payrollOrder}
                    onSortChange={onPayrollSortChange}
                  />
                  <th>{ACTION_TH(t)}</th>
                </tr>
              </thead>
              <tbody>
                {payroll.map((p) => (
                  <tr
                    key={p.id}
                    data-selected={selected?.id === p.id ? 'true' : undefined}
                    className={selected?.id === p.id ? 'row-selected' : 'row-click'}
                    onClick={() => setSelectedId(String(p.id))}
                  >
                    <td>{p.personRef || p.name}</td>
                    <td className="mono">{p.period}</td>
                    <td>{Number(p.amountEur).toFixed(2)}</td>
                    <td>{p.payrollStatus}</td>
                    <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                      {renderRowActions('finance', 'PayrollItem', p)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </ResponsiveDataList>
        <ListPager
          total={payrollPage.total}
          max={payrollPage.max}
          offset={payrollPage.offset}
          disabled={busy}
          onOffset={(o) => setPayrollPage((pg) => ({ ...pg, offset: o }))}
          onMax={(m) => setPayrollPage((pg) => ({ ...pg, max: m, offset: 0 }))}
        />
      </>
    )
  } else if (tab === 'control') {
    list = (
      <>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {t('finance.recentAudit')}
        </h2>
        <ResponsiveDataList
          items={audit}
          empty="—"
          selectedKey={selected?.id}
          onSelect={(a) => setSelectedId(String(a.id))}
          sortFields={AUDIT_SORT_MENU}
          sort={auditSort}
          order={auditOrder}
          onSortChange={onAuditSortChange}
          fields={[
            {
              id: 'name',
              primary: true,
              render: (a) => entityDisplayLabel(a),
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
              render: (a) => a.sourceType || '—',
            },
          ]}
          actions={(a) =>
            renderRowActions('finance', 'JournalEntry', {
              id: a.id,
              name: a.name,
              period: a.period,
            })
          }
        >
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <ListSortHeader
                    field="name"
                    label={t('wb.name', { ns: 'common' })}
                    sort={auditSort}
                    order={auditOrder}
                    onSortChange={onAuditSortChange}
                  />
                  <ListSortHeader
                    field="period"
                    label={t('wb.period', { ns: 'common' })}
                    sort={auditSort}
                    order={auditOrder}
                    onSortChange={onAuditSortChange}
                  />
                  <ListSortHeader
                    field="sourceType"
                    label={t('finance.source')}
                    sort={auditSort}
                    order={auditOrder}
                    onSortChange={onAuditSortChange}
                    className="col-priority-low"
                  />
                  <th>{ACTION_TH(t)}</th>
                </tr>
              </thead>
              <tbody>
                {audit.map((a) => (
                  <tr
                    key={a.id}
                    data-selected={selected?.id === a.id ? 'true' : undefined}
                    className={selected?.id === a.id ? 'row-selected' : 'row-click'}
                    onClick={() => setSelectedId(String(a.id))}
                  >
                    <td>{entityDisplayLabel(a)}</td>
                    <td className="mono">{a.period}</td>
                    <td className="col-priority-low">
                      {a.sourceType || '—'}
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                      {renderRowActions('finance', 'JournalEntry', {
                        id: a.id,
                        name: a.name,
                        period: a.period,
                      })}
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
      </>
    )
  }

  // —— detail ——
  let detail = null
  let detailFooter = null

  if (tab === 'treasury' && selected) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {selected.name}
        </h2>
        <dl className="about-grid">
          <div>
            <dt>{t('wb.kind', { ns: 'common' })}</dt>
            <dd className="mono">{selected.accountKind}</dd>
          </div>
          <div>
            <dt>{t('finance.bookBalance')}</dt>
            <dd>
              {Number(selected.balanceEur).toFixed(2)} {selected.currencyCode || 'EUR'}
            </dd>
          </div>
          <div>
            <dt>{t('finance.sats')}</dt>
            <dd className="mono">
              {selected.accountKind === 'FIAT_BANK' ? '—' : selected.balanceSats}
            </dd>
          </div>
          <div>
            <dt>{t('finance.coa')}</dt>
            <dd className="mono">{selected.coaCode}</dd>
          </div>
        </dl>
        <ViewButton pkg="finance" resource="Treasury" record={selected} />
      </div>
    )
  } else if (tab === 'payables' && selected) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {selected.vendorName || selected.name}
        </h2>
        <dl className="about-grid">
          <div>
            <dt>{t('wb.amount', { ns: 'common' })}</dt>
            <dd>{Number(selected.amountEur).toFixed(2)} EUR</dd>
          </div>
          <div>
            <dt>{t('wb.status', { ns: 'common' })}</dt>
            <dd>{selected.payableStatus}</dd>
          </div>
        </dl>
        <ViewButton pkg="finance" resource="Payable" record={selected} />
      </div>
    )
    if (selected.payableStatus === 'OPEN') {
      detailFooter = (
        <button
          type="button"
          className="btn btn-primary"
          disabled={busy}
          onClick={() =>
            run(async () => {
              await financeOpsApi.payablePay(selected.id)
              await loadTab()
            }, t('finance.paidId'))
          }
        >
          {t('finance.pay')}
        </button>
      )
    }
  } else if (tab === 'budgets') {
    // Always show variance in detail; overlay selected budget when present
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        {selected && (
          <>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {selected.name || t('finance.budgetFallback')}
            </h2>
            <dl className="about-grid" style={{ marginBottom: '1rem' }}>
              <div>
                <dt>{t('wb.period', { ns: 'common' })}</dt>
                <dd className="mono">{selected.period}</dd>
              </div>
              <div>
                <dt>{t('wb.amount', { ns: 'common' })}</dt>
                <dd>{Number(selected.amountEur).toFixed(2)} EUR</dd>
              </div>
            </dl>
            <ViewButton pkg="finance" resource="Budget" record={selected} />
          </>
        )}
        <h2 className="section-label" style={{ marginTop: selected ? '1.25rem' : 0 }}>
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
      </div>
    )
  } else if (tab === 'tax' && selected) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          <span className="mono">{selected.period}</span> · {selected.jurisdiction}
        </h2>
        <dl className="about-grid">
          <div>
            <dt>{t('wb.amount', { ns: 'common' })}</dt>
            <dd>{Number(selected.amountEur).toFixed(2)} EUR</dd>
          </div>
          <div>
            <dt>{t('wb.status', { ns: 'common' })}</dt>
            <dd>{selected.taxStatus}</dd>
          </div>
        </dl>
        <ViewButton pkg="finance" resource="Tax" record={selected} />
      </div>
    )
  } else if (tab === 'payroll' && selected) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {selected.personRef || selected.name}
        </h2>
        <dl className="about-grid">
          <div>
            <dt>{t('wb.period', { ns: 'common' })}</dt>
            <dd className="mono">{selected.period}</dd>
          </div>
          <div>
            <dt>{t('wb.amount', { ns: 'common' })}</dt>
            <dd>{Number(selected.amountEur).toFixed(2)} EUR</dd>
          </div>
          <div>
            <dt>{t('wb.status', { ns: 'common' })}</dt>
            <dd>{selected.payrollStatus}</dd>
          </div>
        </dl>
        <ViewButton pkg="finance" resource="PayrollItem" record={selected} />
      </div>
    )
    detailFooter = (
      <div className="toolbar" style={{ gap: 8, flexWrap: 'wrap', margin: 0 }}>
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
    )
  } else if (tab === 'control' && selected) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {entityDisplayLabel(selected)}
        </h2>
        <dl className="about-grid">
          <div>
            <dt>{t('wb.period', { ns: 'common' })}</dt>
            <dd className="mono">{selected.period}</dd>
          </div>
          <div>
            <dt>{t('finance.source')}</dt>
            <dd className="mono">
              {selected.sourceType || '—'}
            </dd>
          </div>
        </dl>
        <ViewButton
          pkg="finance"
          resource="JournalEntry"
          record={{ id: selected.id, name: selected.name, period: selected.period }}
        />
      </div>
    )
  } else if (tab === 'control' && !selected) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <p>
          {t('wb.period', { ns: 'common' })} <strong className="mono">{period}</strong>:{' '}
          {periodClosed ? (
            <span className="bad-text">{t('finance.statusClosed')}</span>
          ) : (
            <span className="ok-text">{t('finance.statusOpen')}</span>
          )}
        </p>
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
    )
  }

  // Budgets / control overview always occupy detail col
  const showDetail =
    hasSelection || tab === 'budgets' || (tab === 'control' && !hasSelection)

  const emptyTitle = t('desk.emptyTitle', { ns: 'common', defaultValue: 'Select an item' })
  const emptyBody = t('desk.emptyBody', {
    ns: 'common',
    defaultValue: 'Choose a row from the list to see details.',
  })

  const toolbar = (
    <>
      {tab === 'treasury' && (
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
      )}
      {tab === 'tax' && (
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
      )}
      {tab === 'payroll' && !hasSelection && (
        <>
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
        </>
      )}
    </>
  )

  return (
    <div className="page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}

      <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
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
        <Link className="btn btn-secondary" to="/finance/transactions">
          {t('finance.ledgerTitle')}
        </Link>
        <Link className="btn btn-secondary" to="/finance/invoices">
          {t('finance.invoicesTitle')}
        </Link>
        <Link className="btn btn-secondary" to="/finance/posting-rules">
          {t('finance.postingRules.title', { defaultValue: 'Posting rules' })}
        </Link>
        {showAdvanced && (
          <>
            <Link className="btn btn-secondary" to="/finance/collections">
              {t('features.collections', { ns: 'common', defaultValue: 'Collections' })}
            </Link>
            <Link className="btn btn-secondary" to="/finance/match">
              {t('features.threeWayMatch', { ns: 'common', defaultValue: 'Three-way match' })}
            </Link>
          </>
        )}
      </div>

      <DeskShell
        // ADR-050 Wave D: budgets/control overview + selection detail; never empty col2 (showDetail).
        layout="split"
        title={
          <>
            <SectionIcon size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('finance.hubTitle')}
          </>
        }
        subtitle={null}
        tabs={tabs}
        activeTab={tab}
        onTabChange={requestTabChange}
        newLabel={CREATE_TABS.has(tab) ? newLabel : undefined}
        newExpanded={newExpanded}
        onToggleNew={CREATE_TABS.has(tab) ? requestToggleNew : undefined}
        newPanel={newPanel}
        toolbar={toolbar}
        list={list}
        hasSelection={showDetail}
        emptyTitle={emptyTitle}
        emptyBody={emptyBody}
        detail={detail}
        detailFooter={detailFooter}
        helpContext={helpContext}
        narrowSheetOpen={narrow && hasSelection}
        onCloseSheet={clearSelection}
      />

      {CREATE_TABS.has(tab) && (
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
