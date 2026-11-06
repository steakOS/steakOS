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
  Server,
  RefreshCw,
  Plus,
  Check,
  Database,
  Boxes,
  GitBranch,
  Ticket,
  HardDrive,
  Play,
} from 'lucide-react'
import { digitalApi, meApi } from '../api/client'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import ResponsiveDataList from '../components/ResponsiveDataList'
import DeskNewFab from '../components/DeskNewFab'
import DeskShell from '../components/desk/DeskShell'
import DeskProcessSlot from '../components/desk/DeskProcessSlot'
import ConfirmDialog from '../components/ConfirmDialog'
import useDeskSelection, { scrollSelectedIntoView } from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'
import useNarrowViewport from '../hooks/useNarrowViewport'

const TAB_DEFS = [
  { id: 'systems', labelKey: 'digital.tabs.systems', icon: Server },
  { id: 'data', labelKey: 'digital.tabs.data', icon: Database },
  { id: 'solutions', labelKey: 'digital.tabs.solutions', icon: Boxes },
  { id: 'changes', labelKey: 'digital.tabs.changes', icon: GitBranch },
  { id: 'cmdb', labelKey: 'digital.tabs.cmdb', icon: HardDrive },
  { id: 'support', labelKey: 'digital.tabs.support', icon: Ticket },
]

/**
 * Digital Technology desk (DIG0–DIG8) — DeskShell master–detail (ADR-047 / Nielsen).
 */
export default function DigitalDesk() {
  const { t } = useTranslation(['desks', 'common', 'content'])
  const getBelowFieldsRef = useRef(null)
  const { viewModal, ViewButton } = useRecordView({
    getBelowFields: (ctx) => getBelowFieldsRef.current?.(ctx) ?? null,
  })
  const narrow = useNarrowViewport()
  const tabs = useMemo(
    () => TAB_DEFS.map((d) => ({ id: d.id, label: t(d.labelKey), icon: d.icon })),
    [t],
  )

  const [tab, setTab] = useState(() => {
    if (typeof window === 'undefined') return 'solutions'
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    if (urlTab) return urlTab
    try {
      const raw = window.localStorage.getItem('steakos.desk.default.digital')
      const parsed = raw ? JSON.parse(raw) : null
      if (parsed?.tab) return String(parsed.tab)
    } catch {
      /* ignore */
    }
    return 'solutions'
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

  const { loadMemory, saveMemory } = useDeskMemory({ deskKey: 'digital', companyId })

  useDeskSelection({
    deskKey: 'digital',
    defaultTab: 'solutions',
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

  const [systems, setSystems] = useState([])
  const [dataItems, setDataItems] = useState([])
  const [solutions, setSolutions] = useState([])
  const [changes, setChanges] = useState([])
  const [cis, setCis] = useState([])
  const [tickets, setTickets] = useState([])
  /** Dropdown options (not paged) — max 100 until typeahead. */
  const [systemOptions, setSystemOptions] = useState([])
  const [solutionOptions, setSolutionOptions] = useState([])
  const [ciOptions, setCiOptions] = useState([])
  const [selectedTicket, setSelectedTicket] = useState(null)
  const [selectedChange, setSelectedChange] = useState(null)
  const [systemContentMap, setSystemContentMap] = useState({})
  const [dataContentMap, setDataContentMap] = useState({})
  const [solutionContentMap, setSolutionContentMap] = useState({})
  const [ciContentMap, setCiContentMap] = useState({})
  const [changeContentMap, setChangeContentMap] = useState({})
  const [ticketContentMap, setTicketContentMap] = useState({})
  const [pendingSystemContent, setPendingSystemContent] = useState([])
  const [pendingDataContent, setPendingDataContent] = useState([])
  const [pendingSolutionContent, setPendingSolutionContent] = useState([])
  const [pendingCiContent, setPendingCiContent] = useState([])
  const [pendingChangeContent, setPendingChangeContent] = useState([])
  const [pendingTicketContent, setPendingTicketContent] = useState([])
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)

  const [sysPage, setSysPage] = useState({ max: 25, offset: 0, total: 0 })
  const [dataPage, setDataPage] = useState({ max: 25, offset: 0, total: 0 })
  const [solPage, setSolPage] = useState({ max: 25, offset: 0, total: 0 })
  const [chgPage, setChgPage] = useState({ max: 25, offset: 0, total: 0 })
  const [ciPage, setCiPage] = useState({ max: 25, offset: 0, total: 0 })
  const [tixPage, setTixPage] = useState({ max: 25, offset: 0, total: 0 })
  const [sysSort, setSysSort] = useState('name')
  const [sysOrder, setSysOrder] = useState('asc')
  const [dataSort, setDataSort] = useState('name')
  const [dataOrder, setDataOrder] = useState('asc')
  const [solSort, setSolSort] = useState('id')
  const [solOrder, setSolOrder] = useState('desc')
  const [chgSort, setChgSort] = useState('id')
  const [chgOrder, setChgOrder] = useState('desc')
  const [ciSort, setCiSort] = useState('name')
  const [ciOrder, setCiOrder] = useState('asc')
  const [tixSort, setTixSort] = useState('id')
  const [tixOrder, setTixOrder] = useState('desc')

  const SYS_SORT_MENU = useMemo(
    () => [
      { value: 'name', label: t('wb.name', { ns: 'common' }) },
      { value: 'systemKey', label: t('digital.key') },
      { value: 'environment', label: t('digital.env') },
      { value: 'id', label: 'Id' },
    ],
    [t],
  )
  const DATA_SORT_MENU = useMemo(
    () => [
      { value: 'name', label: t('wb.name', { ns: 'common' }) },
      { value: 'dataClass', label: t('wb.class', { ns: 'common', defaultValue: 'Class' }) },
      { value: 'retentionDays', label: 'Retention' },
      { value: 'id', label: 'Id' },
    ],
    [t],
  )
  const SOL_SORT_MENU = useMemo(
    () => [
      { value: 'name', label: t('wb.name', { ns: 'common' }) },
      { value: 'integrationType', label: t('wb.type', { ns: 'common' }) },
      { value: 'lifecycleStatus', label: t('wb.status', { ns: 'common' }) },
      { value: 'id', label: 'Id' },
    ],
    [t],
  )
  const CHG_SORT_MENU = useMemo(
    () => [
      { value: 'name', label: t('wb.name', { ns: 'common' }) },
      { value: 'changeKind', label: t('wb.kind', { ns: 'common' }) },
      { value: 'changeStatus', label: t('wb.status', { ns: 'common' }) },
      { value: 'id', label: 'Id' },
    ],
    [t],
  )
  const CI_SORT_MENU = useMemo(
    () => [
      { value: 'name', label: t('wb.name', { ns: 'common' }) },
      { value: 'ciKind', label: t('wb.kind', { ns: 'common' }) },
      { value: 'hostname', label: t('digital.host') },
      { value: 'ciStatus', label: t('wb.status', { ns: 'common' }) },
      { value: 'id', label: 'Id' },
    ],
    [t],
  )
  const TIX_SORT_MENU = useMemo(
    () => [
      { value: 'ticketNumber', label: t('wb.number', { ns: 'common' }) },
      { value: 'name', label: t('wb.subject', { ns: 'common' }) },
      { value: 'ticketStatus', label: t('wb.status', { ns: 'common' }) },
      { value: 'id', label: 'Id' },
    ],
    [t],
  )

  const onSysSortChange = useCallback(({ sort: s, order: o }) => {
    setSysSort(s || 'name')
    setSysOrder(o === 'asc' ? 'asc' : 'desc')
    setSysPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onDataSortChange = useCallback(({ sort: s, order: o }) => {
    setDataSort(s || 'name')
    setDataOrder(o === 'asc' ? 'asc' : 'desc')
    setDataPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onSolSortChange = useCallback(({ sort: s, order: o }) => {
    setSolSort(s || 'id')
    setSolOrder(o === 'asc' ? 'asc' : 'desc')
    setSolPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onChgSortChange = useCallback(({ sort: s, order: o }) => {
    setChgSort(s || 'id')
    setChgOrder(o === 'asc' ? 'asc' : 'desc')
    setChgPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onCiSortChange = useCallback(({ sort: s, order: o }) => {
    setCiSort(s || 'name')
    setCiOrder(o === 'asc' ? 'asc' : 'desc')
    setCiPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onTixSortChange = useCallback(({ sort: s, order: o }) => {
    setTixSort(s || 'id')
    setTixOrder(o === 'asc' ? 'asc' : 'desc')
    setTixPage((pg) => ({ ...pg, offset: 0 }))
  }, [])

  const [sysForm, setSysForm] = useState({
    name: '',
    description: '',
    systemKey: '',
    environment: 'PROD',
  })
  const [dataForm, setDataForm] = useState({
    name: '',
    description: '',
    dataClass: 'INTERNAL',
    retentionDays: '365',
  })
  const [solForm, setSolForm] = useState({
    name: '',
    description: '',
    integrationType: 'OTHER',
    systemId: '',
    versionLabel: '',
  })
  const [chgForm, setChgForm] = useState({
    name: '',
    description: '',
    changeKind: 'CHANGE',
    riskLevel: 'LOW',
    cabNote: '',
  })
  const [ciForm, setCiForm] = useState({
    name: '',
    description: '',
    ciKind: 'HOST',
    hostname: '',
    systemId: '',
    solutionId: '',
  })
  const [tixForm, setTixForm] = useState({
    name: '',
    ticketKind: 'INCIDENT',
    priority: 'NORMAL',
    configItemId: '',
    solutionId: '',
  })

  const loadAll = useCallback(async () => {
    const results = await Promise.allSettled([
      digitalApi.systems.list({ max: sysPage.max, offset: sysPage.offset, sort: sysSort, order: sysOrder }),
      digitalApi.data.list({ max: dataPage.max, offset: dataPage.offset, sort: dataSort, order: dataOrder }),
      digitalApi.solutions.list({ max: solPage.max, offset: solPage.offset, sort: solSort, order: solOrder }),
      digitalApi.changes.list({ max: chgPage.max, offset: chgPage.offset, sort: chgSort, order: chgOrder }),
      digitalApi.cis.list({ max: ciPage.max, offset: ciPage.offset, sort: ciSort, order: ciOrder }),
      digitalApi.tickets.list({ max: tixPage.max, offset: tixPage.offset, sort: tixSort, order: tixOrder }),
      digitalApi.systems.list({ max: 100, offset: 0 }),
      digitalApi.solutions.list({ max: 100, offset: 0 }),
      digitalApi.cis.list({ max: 100, offset: 0 }),
    ])
    const labels = [
      'systems',
      'data',
      'solutions',
      'changes',
      'cis',
      'tickets',
      'systemOptions',
      'solutionOptions',
      'ciOptions',
    ]
    const failed = []
    results.forEach((r, i) => {
      if (r.status === 'rejected') {
        failed.push(`${labels[i]}: ${r.reason?.message || r.reason || 'failed'}`)
      }
    })
    const val = (i) => (results[i].status === 'fulfilled' ? results[i].value : null)
    const sys = val(0)
    const dat = val(1)
    const sol = val(2)
    const chgRes = val(3)
    const ciRes = val(4)
    const tixRes = val(5)
    const chg = chgRes?.items || []
    const tix = tixRes?.items || []
    const sysItems = sys?.items || []
    const datItems = dat?.items || []
    const solItems = sol?.items || []
    const ciItems = ciRes?.items || []
    setSystems(sysItems)
    setDataItems(datItems)
    setSolutions(solItems)
    setChanges(chg)
    setCis(ciItems)
    setTickets(tix)
    setSystemOptions(val(6)?.items || [])
    setSolutionOptions(val(7)?.items || [])
    setCiOptions(val(8)?.items || [])
    setSysPage((pg) => ({
      ...pg,
      max: sys?.max ?? pg.max,
      offset: sys?.offset ?? pg.offset,
      total: sys?.total ?? (sys?.items || []).length,
    }))
    setDataPage((pg) => ({
      ...pg,
      max: dat?.max ?? pg.max,
      offset: dat?.offset ?? pg.offset,
      total: dat?.total ?? (dat?.items || []).length,
    }))
    setSolPage((pg) => ({
      ...pg,
      max: sol?.max ?? pg.max,
      offset: sol?.offset ?? pg.offset,
      total: sol?.total ?? (sol?.items || []).length,
    }))
    setChgPage((pg) => ({
      ...pg,
      max: chgRes?.max ?? pg.max,
      offset: chgRes?.offset ?? pg.offset,
      total: chgRes?.total ?? chg.length,
    }))
    setCiPage((pg) => ({
      ...pg,
      max: ciRes?.max ?? pg.max,
      offset: ciRes?.offset ?? pg.offset,
      total: ciRes?.total ?? ciItems.length,
    }))
    setTixPage((pg) => ({
      ...pg,
      max: tixRes?.max ?? pg.max,
      offset: tixRes?.offset ?? pg.offset,
      total: tixRes?.total ?? tix.length,
    }))
    const [sysMap, datMap, solMap, ciMap, cm, tm] = await Promise.all([
      fetchContentMap('digital', 'System', sysItems),
      fetchContentMap('digital', 'Data', datItems),
      fetchContentMap('digital', 'Solution', solItems),
      fetchContentMap('digital', 'ConfigItem', ciItems),
      fetchContentMap('digital', 'ITChange', chg),
      fetchContentMap('digital', 'ItTicket', tix),
    ])
    setSystemContentMap(sysMap)
    setDataContentMap(datMap)
    setSolutionContentMap(solMap)
    setCiContentMap(ciMap)
    setChangeContentMap(cm)
    setTicketContentMap(tm)
    if (failed.length) {
      setError(
        `Hold on — some Digital data could not load (${failed.join('; ')}). The application server may still be starting on port 9001.`,
      )
    } else {
      setError(null)
    }
  }, [
    sysPage.max,
    sysPage.offset,
    sysSort,
    sysOrder,
    dataPage.max,
    dataPage.offset,
    dataSort,
    dataOrder,
    solPage.max,
    solPage.offset,
    solSort,
    solOrder,
    chgPage.max,
    chgPage.offset,
    chgSort,
    chgOrder,
    ciPage.max,
    ciPage.offset,
    ciSort,
    ciOrder,
    tixPage.max,
    tixPage.offset,
    tixSort,
    tixOrder,
  ])

  // Resolve selection from loaded lists (digital changes/tickets have no GET-by-id).
  useEffect(() => {
    if (tab === 'changes') {
      if (selectedId) {
        const row = changes.find((c) => String(c.id) === String(selectedId))
        if (row) setSelectedChange(row)
      } else {
        setSelectedChange(null)
      }
      setSelectedTicket(null)
    } else if (tab === 'support') {
      if (selectedId) {
        const row = tickets.find((x) => String(x.id) === String(selectedId))
        if (row) setSelectedTicket(row)
      } else {
        setSelectedTicket(null)
      }
      setSelectedChange(null)
    } else {
      setSelectedChange(null)
      setSelectedTicket(null)
    }
  }, [tab, selectedId, changes, tickets])

  useEffect(() => {
    if (!selectedId) return
    const rid = window.requestAnimationFrame(() => {
      scrollSelectedIntoView('.desk-shell-list')
    })
    return () => window.cancelAnimationFrame(rid)
  }, [selectedId, tab, selectedChange?.id, selectedTicket?.id])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      await loadAll()
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [loadAll])

  useEffect(() => {
    setLoading(true)
    loadAll()
      .catch((e) => setError(e.message || String(e)))
      .finally(() => setLoading(false))
  }, [loadAll])

  const statusClass = (s) => {
    if (['ACTIVE', 'DONE', 'RESOLVED', 'CLOSED'].includes(s)) return 'badge badge-ok'
    if (['BLOCKED', 'ERROR', 'FAILED', 'REJECTED'].includes(s)) return 'badge badge-danger'
    if (['RETIRED', 'CANCELLED', 'INACTIVE', 'ARCHIVED'].includes(s)) return 'badge badge-muted'
    if (['IN_PROGRESS', 'PLANNED', 'OPEN'].includes(s)) return 'badge badge-warn'
    return 'badge badge-draft'
  }

  const run = async (label, fn) => {
    setBusy(label)
    setError(null)
    setInfo(null)
    try {
      await fn()
      await loadAll()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(null)
    }
  }
  const resetCreateForms = useCallback(() => {
    setSysForm({ name: '', description: '', systemKey: '', environment: 'PROD' })
    setDataForm({ name: '', description: '', dataClass: 'INTERNAL', retentionDays: '365' })
    setSolForm({
      name: '',
      description: '',
      integrationType: 'OTHER',
      systemId: '',
      versionLabel: '',
    })
    setChgForm({
      name: '',
      description: '',
      changeKind: 'CHANGE',
      riskLevel: 'LOW',
      cabNote: '',
    })
    setCiForm({
      name: '',
      description: '',
      ciKind: 'HOST',
      hostname: '',
      systemId: '',
      solutionId: '',
    })
    setTixForm({
      name: '',
      ticketKind: 'INCIDENT',
      priority: 'NORMAL',
      configItemId: '',
      solutionId: '',
    })
    setPendingSystemContent([])
    setPendingDataContent([])
    setPendingSolutionContent([])
    setPendingCiContent([])
    setPendingChangeContent([])
    setPendingTicketContent([])
  }, [])

  const createDirty = useMemo(() => {
    if (tab === 'systems') {
      return Boolean(
        (sysForm.name || '').trim() ||
          (sysForm.description || '').trim() ||
          (sysForm.systemKey || '').trim() ||
          sysForm.environment !== 'PROD' ||
          pendingSystemContent.length,
      )
    }
    if (tab === 'data') {
      return Boolean(
        (dataForm.name || '').trim() ||
          (dataForm.description || '').trim() ||
          dataForm.dataClass !== 'INTERNAL' ||
          dataForm.retentionDays !== '365' ||
          pendingDataContent.length,
      )
    }
    if (tab === 'solutions') {
      return Boolean(
        (solForm.name || '').trim() ||
          (solForm.description || '').trim() ||
          solForm.systemId ||
          (solForm.versionLabel || '').trim() ||
          solForm.integrationType !== 'OTHER' ||
          pendingSolutionContent.length,
      )
    }
    if (tab === 'changes') {
      return Boolean(
        (chgForm.name || '').trim() ||
          (chgForm.description || '').trim() ||
          (chgForm.cabNote || '').trim() ||
          chgForm.changeKind !== 'CHANGE' ||
          chgForm.riskLevel !== 'LOW' ||
          pendingChangeContent.length,
      )
    }
    if (tab === 'cmdb') {
      return Boolean(
        (ciForm.name || '').trim() ||
          (ciForm.description || '').trim() ||
          (ciForm.hostname || '').trim() ||
          ciForm.systemId ||
          ciForm.solutionId ||
          ciForm.ciKind !== 'HOST' ||
          pendingCiContent.length,
      )
    }
    if (tab === 'support') {
      return Boolean(
        (tixForm.name || '').trim() ||
          tixForm.configItemId ||
          tixForm.solutionId ||
          tixForm.ticketKind !== 'INCIDENT' ||
          tixForm.priority !== 'NORMAL' ||
          pendingTicketContent.length,
      )
    }
    return false
  }, [
    tab,
    sysForm,
    dataForm,
    solForm,
    chgForm,
    ciForm,
    tixForm,
    pendingSystemContent,
    pendingDataContent,
    pendingSolutionContent,
    pendingCiContent,
    pendingChangeContent,
    pendingTicketContent,
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
    if (newExpanded) applyCollapseCreate()
    else setNewExpanded(true)
  }, [newExpanded, createDirty, applyCollapseCreate])

  const keyboardIds = useMemo(() => {
    if (tab === 'changes') return changes.map((c) => c.id)
    if (tab === 'support') return tickets.map((x) => x.id)
    return []
  }, [tab, changes, tickets])

  useDeskKeyboard({
    enabled: !newExpanded && (tab === 'changes' || tab === 'support'),
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
    if (tab === 'systems') return t('digital.newSystem')
    if (tab === 'data') return t('digital.newData')
    if (tab === 'solutions') return t('digital.newSolution')
    if (tab === 'changes') return t('digital.newChange')
    if (tab === 'cmdb') return t('digital.newCi')
    if (tab === 'support') return t('digital.openTicket', { defaultValue: 'Open ticket' })
    return t('desk.new', { ns: 'common', defaultValue: 'New' })
  }, [tab, t])

  const hasSelection = useMemo(() => {
    if (tab === 'changes') return Boolean(selectedChange?.id)
    if (tab === 'support') return Boolean(selectedTicket?.id)
    return false
  }, [tab, selectedChange?.id, selectedTicket?.id])

  const helpContext = useMemo(() => {
    const processCode = tab === 'changes' ? 'DIG_IT_CHANGE' : 'DIG_IT_TICKET'
    const base = { desk: 'digital', tab, processCode }
    if (tab === 'changes' && selectedChange) {
      return {
        ...base,
        resource: 'ITChange',
        objectId: selectedChange.id,
        status: selectedChange.changeStatus,
      }
    }
    if (tab === 'support' && selectedTicket) {
      return {
        ...base,
        resource: 'ItTicket',
        objectId: selectedTicket.id,
        status: selectedTicket.ticketStatus,
      }
    }
    return base
  }, [tab, selectedChange, selectedTicket])

  const processCode = tab === 'changes' ? 'DIG_IT_CHANGE' : 'DIG_IT_TICKET'
  const processStatus =
    tab === 'support'
      ? selectedTicket?.ticketStatus
      : tab === 'changes'
        ? selectedChange?.changeStatus
        : null

  const processSlot = <DeskProcessSlot processCode={processCode} status={processStatus} />

  const collapsedContentRail = (pkg, resource, id, onChanged) => (
    <details className="content-rail-collapse" style={{ marginTop: '1.25rem' }}>
      <summary className="btn btn-ghost" style={{ cursor: 'pointer', listStyle: 'inside' }}>
        {t('desk.attachmentsNotes', { ns: 'common', defaultValue: 'Attachments & notes' })}
      </summary>
      <ContentRail package={pkg} resource={resource} id={id} onChanged={onChanged} />
    </details>
  )

  getBelowFieldsRef.current = ({ resource, record }) => {
    if (!record || (record.id == null && record.id !== 0)) return null
    if (resource === 'System') {
      return collapsedContentRail('digital', 'System', record.id, async () =>
        setSystemContentMap(await fetchContentMap('digital', 'System', systems)),
      )
    }
    if (resource === 'Data') {
      return collapsedContentRail('digital', 'Data', record.id, async () =>
        setDataContentMap(await fetchContentMap('digital', 'Data', dataItems)),
      )
    }
    if (resource === 'Solution') {
      return collapsedContentRail('digital', 'Solution', record.id, async () =>
        setSolutionContentMap(await fetchContentMap('digital', 'Solution', solutions)),
      )
    }
    if (resource === 'ConfigItem') {
      return collapsedContentRail('digital', 'ConfigItem', record.id, async () =>
        setCiContentMap(await fetchContentMap('digital', 'ConfigItem', cis)),
      )
    }
    if (resource === 'ITChange') {
      return collapsedContentRail('digital', 'ITChange', record.id, async () =>
        setChangeContentMap(await fetchContentMap('digital', 'ITChange', changes)),
      )
    }
    if (resource === 'ItTicket') {
      return collapsedContentRail('digital', 'ItTicket', record.id, async () =>
        setTicketContentMap(await fetchContentMap('digital', 'ItTicket', tickets)),
      )
    }
    return null
  }

  const sysCreatePanel = (
    <form
      className="form-grid"
      onSubmit={(e) => {
        e.preventDefault()
        run('sys', async () => {
          const s = await digitalApi.systems.create(sysForm)
          if (pendingSystemContent.length && s?.id != null) {
            await attachPendingContent('digital', 'System', s.id, pendingSystemContent)
            setPendingSystemContent([])
          }
          setInfo(
            t('toast.namedCreated', {
              ns: 'common',
              what: t('labels.system', { ns: 'common' }),
              name: s.name,
            }),
          )
          setSysForm({ name: '', description: '', systemKey: '', environment: 'PROD' })
          setNewExpanded(false)
        })
      }}
    >
      <label>
        Name *
        <input
          required
          value={sysForm.name}
          onChange={(e) => setSysForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <label>
        Description
        <textarea
          rows={2}
          value={sysForm.description}
          onChange={(e) => setSysForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          Key
          <input
            value={sysForm.systemKey}
            onChange={(e) => setSysForm((f) => ({ ...f, systemKey: e.target.value }))}
          />
        </label>
        <label>
          Environment
          <select
            value={sysForm.environment}
            onChange={(e) => setSysForm((f) => ({ ...f, environment: e.target.value }))}
          >
            {['DEV', 'STAGE', 'PROD', 'OTHER'].map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
      </div>
      <PendingContentComposer
        package="digital"
        resource="System"
        pending={pendingSystemContent}
        onChange={setPendingSystemContent}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('digital.createSystem')}
      </button>
    </form>
  )

  const dataCreatePanel = (
    <form
      className="form-grid"
      onSubmit={(e) => {
        e.preventDefault()
        run('data', async () => {
          const d = await digitalApi.data.create({
            ...dataForm,
            retentionDays: dataForm.retentionDays || null,
          })
          if (pendingDataContent.length && d?.id != null) {
            await attachPendingContent('digital', 'Data', d.id, pendingDataContent)
            setPendingDataContent([])
          }
          setInfo(
            t('toast.namedStatus', {
              ns: 'common',
              what: t('labels.data', { ns: 'common' }),
              name: d.name,
              status: d.dataClass,
            }),
          )
          setDataForm({ name: '', description: '', dataClass: 'INTERNAL', retentionDays: '365' })
          setNewExpanded(false)
        })
      }}
    >
      <label>
        Name *
        <input
          required
          value={dataForm.name}
          onChange={(e) => setDataForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <label>
        Description
        <textarea
          rows={2}
          value={dataForm.description}
          onChange={(e) => setDataForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          Class
          <select
            value={dataForm.dataClass}
            onChange={(e) => setDataForm((f) => ({ ...f, dataClass: e.target.value }))}
          >
            {['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'].map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
        <label>
          Retention days
          <input
            value={dataForm.retentionDays}
            onChange={(e) => setDataForm((f) => ({ ...f, retentionDays: e.target.value }))}
          />
        </label>
      </div>
      <PendingContentComposer
        package="digital"
        resource="Data"
        pending={pendingDataContent}
        onChange={setPendingDataContent}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('digital.createData')}
      </button>
    </form>
  )

  const solCreatePanel = (
    <form
      className="form-grid"
      onSubmit={(e) => {
        e.preventDefault()
        run('sol', async () => {
          const s = await digitalApi.solutions.create({
            ...solForm,
            systemId: solForm.systemId || undefined,
          })
          if (pendingSolutionContent.length && s?.id != null) {
            await attachPendingContent('digital', 'Solution', s.id, pendingSolutionContent)
            setPendingSolutionContent([])
          }
          setInfo(
            t('toast.namedStatus', {
              ns: 'common',
              what: t('labels.solution', { ns: 'common' }),
              name: s.name,
              status: s.lifecycleStatus,
            }),
          )
          setSolForm({
            name: '',
            description: '',
            integrationType: 'OTHER',
            systemId: '',
            versionLabel: '',
          })
          setNewExpanded(false)
        })
      }}
    >
      <label>
        Name *
        <input
          required
          value={solForm.name}
          onChange={(e) => setSolForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <label>
        Description
        <textarea
          rows={2}
          value={solForm.description}
          onChange={(e) => setSolForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          Type
          <select
            value={solForm.integrationType}
            onChange={(e) => setSolForm((f) => ({ ...f, integrationType: e.target.value }))}
          >
            {['BTCPAY', 'NOSTR', 'RELAY', 'WEBHOOK', 'CUSTOM', 'OTHER'].map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
        <label>
          Version
          <input
            value={solForm.versionLabel}
            onChange={(e) => setSolForm((f) => ({ ...f, versionLabel: e.target.value }))}
          />
        </label>
      </div>
      <label>
        Platform system (optional)
        <select
          value={solForm.systemId}
          onChange={(e) => setSolForm((f) => ({ ...f, systemId: e.target.value }))}
        >
          <option value="">— none —</option>
          {systemOptions.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </label>
      <PendingContentComposer
        package="digital"
        resource="Solution"
        pending={pendingSolutionContent}
        onChange={setPendingSolutionContent}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('digital.createSolution')}
      </button>
    </form>
  )

  const chgCreatePanel = (
    <form
      className="form-grid"
      onSubmit={(e) => {
        e.preventDefault()
        run('chg', async () => {
          const c = await digitalApi.changes.create(chgForm)
          if (pendingChangeContent.length) {
            await attachPendingContent('digital', 'ITChange', c.id, pendingChangeContent)
            setPendingChangeContent([])
          }
          setInfo(
            t('toast.namedStatus', {
              ns: 'common',
              what: t('labels.change', { ns: 'common' }),
              name: c.name,
              status: c.changeKind,
            }),
          )
          setChgForm({
            name: '',
            description: '',
            changeKind: 'CHANGE',
            riskLevel: 'LOW',
            cabNote: '',
          })
          setNewExpanded(false)
          setSelectedId(String(c.id))
        })
      }}
    >
      <label>
        Name *
        <input
          required
          value={chgForm.name}
          onChange={(e) => setChgForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <label>
        Description
        <textarea
          rows={2}
          value={chgForm.description}
          onChange={(e) => setChgForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          Kind
          <select
            value={chgForm.changeKind}
            onChange={(e) => setChgForm((f) => ({ ...f, changeKind: e.target.value }))}
          >
            {['CHANGE', 'BACKUP', 'DR', 'SECURITY', 'OTHER'].map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
        <label>
          Risk
          <select
            value={chgForm.riskLevel}
            onChange={(e) => setChgForm((f) => ({ ...f, riskLevel: e.target.value }))}
          >
            {['LOW', 'MED', 'HIGH'].map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label>
        CAB / notes
        <textarea
          rows={2}
          value={chgForm.cabNote}
          onChange={(e) => setChgForm((f) => ({ ...f, cabNote: e.target.value }))}
        />
      </label>
      <PendingContentComposer
        package="digital"
        resource="ITChange"
        pending={pendingChangeContent}
        onChange={setPendingChangeContent}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('digital.createChange')}
      </button>
    </form>
  )

  const ciCreatePanel = (
    <form
      className="form-grid"
      onSubmit={(e) => {
        e.preventDefault()
        run('ci', async () => {
          const c = await digitalApi.cis.create({
            ...ciForm,
            systemId: ciForm.systemId || undefined,
            solutionId: ciForm.solutionId || undefined,
          })
          if (pendingCiContent.length && c?.id != null) {
            await attachPendingContent('digital', 'ConfigItem', c.id, pendingCiContent)
            setPendingCiContent([])
          }
          setInfo(
            t('toast.namedStatus', {
              ns: 'common',
              what: t('labels.ci', { ns: 'common' }),
              name: c.name,
              status: c.ciKind,
            }),
          )
          setCiForm({
            name: '',
            description: '',
            ciKind: 'HOST',
            hostname: '',
            systemId: '',
            solutionId: '',
          })
          setNewExpanded(false)
        })
      }}
    >
      <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
        Manual configuration items only — no discovery (DIG0).
      </p>
      <label>
        Name *
        <input
          required
          value={ciForm.name}
          onChange={(e) => setCiForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <label>
        Description
        <textarea
          rows={2}
          value={ciForm.description}
          onChange={(e) => setCiForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          Kind
          <select
            value={ciForm.ciKind}
            onChange={(e) => setCiForm((f) => ({ ...f, ciKind: e.target.value }))}
          >
            {['HOST', 'APP', 'INTEGRATION', 'NETWORK', 'OTHER'].map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
        <label>
          Hostname
          <input
            value={ciForm.hostname}
            onChange={(e) => setCiForm((f) => ({ ...f, hostname: e.target.value }))}
          />
        </label>
      </div>
      <label>
        System
        <select
          value={ciForm.systemId}
          onChange={(e) => setCiForm((f) => ({ ...f, systemId: e.target.value }))}
        >
          <option value="">—</option>
          {systemOptions.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Solution
        <select
          value={ciForm.solutionId}
          onChange={(e) => setCiForm((f) => ({ ...f, solutionId: e.target.value }))}
        >
          <option value="">—</option>
          {solutionOptions.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </label>
      <PendingContentComposer
        package="digital"
        resource="ConfigItem"
        pending={pendingCiContent}
        onChange={setPendingCiContent}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('digital.createCi')}
      </button>
    </form>
  )

  const tixCreatePanel = (
    <form
      className="form-grid"
      onSubmit={(e) => {
        e.preventDefault()
        run('tix', async () => {
          const ticket = await digitalApi.tickets.create({
            ...tixForm,
            configItemId: tixForm.configItemId || undefined,
            solutionId: tixForm.solutionId || undefined,
          })
          if (pendingTicketContent.length) {
            await attachPendingContent('digital', 'ItTicket', ticket.id, pendingTicketContent)
            setPendingTicketContent([])
          }
          setInfo(
            t('toast.numberOpened', {
              ns: 'common',
              what: t('labels.ticket', { ns: 'common' }),
              number: ticket.ticketNumber,
            }),
          )
          setTixForm({
            name: '',
            ticketKind: 'INCIDENT',
            priority: 'NORMAL',
            configItemId: '',
            solutionId: '',
          })
          setNewExpanded(false)
          setSelectedId(String(ticket.id))
        })
      }}
    >
      <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
        Internal IT only (DIG3). Not customer after-sales.{' '}
        <Link to="/help/it-support">Help → IT Support</Link> is the dedicated help hub for the same
        tickets.
      </p>
      <label>
        Subject *
        <input
          required
          value={tixForm.name}
          onChange={(e) => setTixForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          Kind
          <select
            value={tixForm.ticketKind}
            onChange={(e) => setTixForm((f) => ({ ...f, ticketKind: e.target.value }))}
          >
            {['INCIDENT', 'REQUEST'].map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
        <label>
          Priority
          <select
            value={tixForm.priority}
            onChange={(e) => setTixForm((f) => ({ ...f, priority: e.target.value }))}
          >
            {['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label>
        CI
        <select
          value={tixForm.configItemId}
          onChange={(e) => setTixForm((f) => ({ ...f, configItemId: e.target.value }))}
        >
          <option value="">—</option>
          {ciOptions.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      <PendingContentComposer
        package="digital"
        resource="ItTicket"
        pending={pendingTicketContent}
        onChange={setPendingTicketContent}
      />
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> Open ticket
      </button>
    </form>
  )

  const newPanel =
    tab === 'systems'
      ? sysCreatePanel
      : tab === 'data'
        ? dataCreatePanel
        : tab === 'solutions'
          ? solCreatePanel
          : tab === 'changes'
            ? chgCreatePanel
            : tab === 'cmdb'
              ? ciCreatePanel
              : tab === 'support'
                ? tixCreatePanel
                : null

  const systemsList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Systems ({sysPage.total || systems.length})
      </h2>
      <ResponsiveDataList
        items={systems}
        empty="—"
        sortFields={SYS_SORT_MENU}
        sort={sysSort}
        order={sysOrder}
        onSortChange={onSysSortChange}
        fields={[
          { id: 'name', primary: true, render: (s) => s.name },
          {
            id: 'key',
            secondary: true,
            render: (s) => <span className="mono">{s.systemKey || '—'}</span>,
          },
          {
            id: 'env',
            meta: true,
            render: (s) => s.environment,
          },
        ]}
        actions={(s) => <ViewButton pkg="digital" resource="System" record={s} />}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={sysSort}
                  order={sysOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onSysSortChange}
                />
                <ListSortHeader
                  field="systemKey"
                  label={t('digital.key')}
                  sort={sysSort}
                  order={sysOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onSysSortChange}
                  className="col-priority-low"
                />
                <ListSortHeader
                  field="environment"
                  label={t('digital.env')}
                  sort={sysSort}
                  order={sysOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onSysSortChange}
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {systems.map((s) => (
                <tr key={s.id}>
                  <td>{s.name}</td>
                  <td className="mono col-priority-low">{s.systemKey || '—'}</td>
                  <td>{s.environment}</td>
                  <td className="col-priority-low">
                    <ContentChannelIcons summary={systemContentMap[s.id]} />
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    <ViewButton pkg="digital" resource="System" record={s} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={sysPage.total}
        max={sysPage.max}
        offset={sysPage.offset}
        disabled={loading}
        onOffset={(o) => setSysPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setSysPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const dataList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Data assets ({dataPage.total || dataItems.length})
      </h2>
      <ResponsiveDataList
        items={dataItems}
        empty="—"
        sortFields={DATA_SORT_MENU}
        sort={dataSort}
        order={dataOrder}
        onSortChange={onDataSortChange}
        fields={[
          { id: 'name', primary: true, render: (d) => d.name },
          {
            id: 'class',
            secondary: true,
            render: (d) => d.dataClass,
          },
          {
            id: 'retention',
            meta: true,
            render: (d) => (d.retentionDays != null ? `${d.retentionDays}d` : '—'),
          },
        ]}
        actions={(d) => <ViewButton pkg="digital" resource="Data" record={d} />}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={dataSort}
                  order={dataOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onDataSortChange}
                />
                <ListSortHeader
                  field="dataClass"
                  label={t('wb.class', { ns: 'common', defaultValue: 'Class' })}
                  sort={dataSort}
                  order={dataOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onDataSortChange}
                />
                <ListSortHeader
                  field="retentionDays"
                  label="Retention"
                  sort={dataSort}
                  order={dataOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onDataSortChange}
                  className="col-priority-low"
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {dataItems.map((d) => (
                <tr key={d.id}>
                  <td>{d.name}</td>
                  <td>{d.dataClass}</td>
                  <td className="col-priority-low">
                    {d.retentionDays != null ? `${d.retentionDays}d` : '—'}
                  </td>
                  <td className="col-priority-low">
                    <ContentChannelIcons summary={dataContentMap[d.id]} />
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    <ViewButton pkg="digital" resource="Data" record={d} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={dataPage.total}
        max={dataPage.max}
        offset={dataPage.offset}
        disabled={loading}
        onOffset={(o) => setDataPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setDataPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const solutionsList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Solutions ({solPage.total || solutions.length})
      </h2>
      <p className="muted" style={{ fontSize: '0.85rem' }}>
        <strong>{t('digital.systemsList')}</strong> = platform/runtime.{' '}
        <strong>{t('digital.solutionsList')}</strong> = capability or integration (DIG1). Optional
        system link (DIG4). No money fields (DIG7).
      </p>
      <ResponsiveDataList
        items={solutions}
        empty="—"
        sortFields={SOL_SORT_MENU}
        sort={solSort}
        order={solOrder}
        onSortChange={onSolSortChange}
        fields={[
          { id: 'name', primary: true, render: (s) => s.name },
          {
            id: 'type',
            secondary: true,
            render: (s) => s.integrationType,
          },
          {
            id: 'status',
            meta: true,
            render: (s) => (
              <span className={statusClass(s.lifecycleStatus)}>{s.lifecycleStatus}</span>
            ),
          },
        ]}
        actions={(s) => (
          <>
            {s.lifecycleStatus === 'DRAFT' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('act', async () => {
                    await digitalApi.solutions.activate(s.id)
                    setInfo(t('toast.activated', { ns: 'common', name: s.name }))
                  })
                }
              >
                Activate
              </button>
            )}
            {s.lifecycleStatus === 'ACTIVE' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('ret', async () => {
                    await digitalApi.solutions.retire(s.id)
                    setInfo(t('toast.retired', { ns: 'common', name: s.name }))
                  })
                }
              >
                Retire
              </button>
            )}
            <ViewButton pkg="digital" resource="Solution" record={s} />
          </>
        )}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={solSort}
                  order={solOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onSolSortChange}
                />
                <ListSortHeader
                  field="integrationType"
                  label={t('wb.type', { ns: 'common' })}
                  sort={solSort}
                  order={solOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onSolSortChange}
                  className="col-priority-low"
                />
                <ListSortHeader
                  field="lifecycleStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={solSort}
                  order={solOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onSolSortChange}
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {solutions.map((s) => (
                <tr key={s.id}>
                  <td>{s.name}</td>
                  <td className="col-priority-low">{s.integrationType}</td>
                  <td>
                    <span className={statusClass(s.lifecycleStatus)}>{s.lifecycleStatus}</span>
                  </td>
                  <td className="col-priority-low">
                    <ContentChannelIcons summary={solutionContentMap[s.id]} />
                  </td>
                  <td>
                    {s.lifecycleStatus === 'DRAFT' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('act', async () => {
                            await digitalApi.solutions.activate(s.id)
                            setInfo(t('toast.activated', { ns: 'common', name: s.name }))
                          })
                        }
                      >
                        Activate
                      </button>
                    )}
                    {s.lifecycleStatus === 'ACTIVE' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('ret', async () => {
                            await digitalApi.solutions.retire(s.id)
                            setInfo(t('toast.retired', { ns: 'common', name: s.name }))
                          })
                        }
                      >
                        Retire
                      </button>
                    )}
                    <ViewButton pkg="digital" resource="Solution" record={s} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={solPage.total}
        max={solPage.max}
        offset={solPage.offset}
        disabled={loading}
        onOffset={(o) => setSolPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setSolPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const changesList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Changes ({chgPage.total || changes.length})
      </h2>
      <ResponsiveDataList
        items={changes}
        empty="—"
        sortFields={CHG_SORT_MENU}
        sort={chgSort}
        order={chgOrder}
        onSortChange={onChgSortChange}
        selectedKey={selectedChange?.id}
        onSelect={(c) => setSelectedId(String(c.id))}
        fields={[
          { id: 'name', primary: true, render: (c) => c.name },
          {
            id: 'kind',
            secondary: true,
            render: (c) => c.changeKind,
          },
          {
            id: 'status',
            meta: true,
            render: (c) => (
              <span className={statusClass(c.changeStatus)}>{c.changeStatus}</span>
            ),
          },
        ]}
        actions={(c) => <ViewButton pkg="digital" resource="ITChange" record={c} />}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={chgSort}
                  order={chgOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onChgSortChange}
                />
                <ListSortHeader
                  field="changeKind"
                  label={t('wb.kind', { ns: 'common' })}
                  sort={chgSort}
                  order={chgOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onChgSortChange}
                  className="col-priority-low"
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                <ListSortHeader
                  field="changeStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={chgSort}
                  order={chgOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onChgSortChange}
                />
                <th />
              </tr>
            </thead>
            <tbody>
              {changes.map((c) => (
                <tr
                  key={c.id}
                  data-selected={selectedChange?.id === c.id ? 'true' : undefined}
                  className={selectedChange?.id === c.id ? 'row-selected' : 'row-click'}
                  onClick={() => setSelectedId(String(c.id))}
                >
                  <td>{c.name}</td>
                  <td className="col-priority-low">{c.changeKind}</td>
                  <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                    <ContentChannelIcons summary={changeContentMap[c.id]} />
                  </td>
                  <td>
                    <span className={statusClass(c.changeStatus)}>{c.changeStatus}</span>
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                    <ViewButton pkg="digital" resource="ITChange" record={c} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={chgPage.total}
        max={chgPage.max}
        offset={chgPage.offset}
        disabled={loading}
        onOffset={(o) => setChgPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setChgPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const cmdbList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Configuration items ({ciPage.total || cis.length})
      </h2>
      <ResponsiveDataList
        items={cis}
        empty="—"
        sortFields={CI_SORT_MENU}
        sort={ciSort}
        order={ciOrder}
        onSortChange={onCiSortChange}
        fields={[
          { id: 'name', primary: true, render: (c) => c.name },
          {
            id: 'kind',
            secondary: true,
            render: (c) => c.ciKind,
          },
          {
            id: 'status',
            meta: true,
            render: (c) => <span className={statusClass(c.ciStatus)}>{c.ciStatus}</span>,
          },
          {
            id: 'host',
            priority: 'low',
            render: (c) => c.hostname || '—',
          },
        ]}
        actions={(c) => <ViewButton pkg="digital" resource="ConfigItem" record={c} />}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={ciSort}
                  order={ciOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onCiSortChange}
                />
                <ListSortHeader
                  field="ciKind"
                  label={t('wb.kind', { ns: 'common' })}
                  sort={ciSort}
                  order={ciOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onCiSortChange}
                  className="col-priority-low"
                />
                <ListSortHeader
                  field="hostname"
                  label={t('digital.host')}
                  sort={ciSort}
                  order={ciOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onCiSortChange}
                  className="col-priority-low"
                />
                <ListSortHeader
                  field="ciStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={ciSort}
                  order={ciOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onCiSortChange}
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {cis.map((c) => (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td className="col-priority-low">{c.ciKind}</td>
                  <td className="mono col-priority-low">{c.hostname || '—'}</td>
                  <td>
                    <span className={statusClass(c.ciStatus)}>{c.ciStatus}</span>
                  </td>
                  <td className="col-priority-low">
                    <ContentChannelIcons summary={ciContentMap[c.id]} />
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    <ViewButton pkg="digital" resource="ConfigItem" record={c} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={ciPage.total}
        max={ciPage.max}
        offset={ciPage.offset}
        disabled={loading}
        onOffset={(o) => setCiPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setCiPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const ticketsList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Tickets ({tixPage.total || tickets.length})
      </h2>
      <ResponsiveDataList
        items={tickets}
        empty="—"
        sortFields={TIX_SORT_MENU}
        sort={tixSort}
        order={tixOrder}
        onSortChange={onTixSortChange}
        selectedKey={selectedTicket?.id}
        onSelect={(row) => setSelectedId(String(row.id))}
        fields={[
          {
            id: 'number',
            primary: true,
            render: (row) => (
              <>
                <span className="mono">{row.ticketNumber}</span>{' '}
                <span className={statusClass(row.ticketStatus)}>{row.ticketStatus}</span>
              </>
            ),
          },
          {
            id: 'subject',
            secondary: true,
            render: (row) => row.name,
          },
        ]}
        actions={(row) => <ViewButton pkg="digital" resource="ItTicket" record={row} />}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="ticketNumber"
                  label={t('wb.number', { ns: 'common' })}
                  sort={tixSort}
                  order={tixOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onTixSortChange}
                />
                <ListSortHeader
                  field="name"
                  label={t('wb.subject', { ns: 'common' })}
                  sort={tixSort}
                  order={tixOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onTixSortChange}
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                <ListSortHeader
                  field="ticketStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={tixSort}
                  order={tixOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onTixSortChange}
                />
                <th />
              </tr>
            </thead>
            <tbody>
              {tickets.map((row) => (
                <tr
                  key={row.id}
                  data-selected={selectedTicket?.id === row.id ? 'true' : undefined}
                  className={selectedTicket?.id === row.id ? 'row-selected' : 'row-click'}
                  onClick={() => setSelectedId(String(row.id))}
                >
                  <td className="mono">{row.ticketNumber}</td>
                  <td>{row.name}</td>
                  <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                    <ContentChannelIcons summary={ticketContentMap[row.id]} />
                  </td>
                  <td>
                    <span className={statusClass(row.ticketStatus)}>{row.ticketStatus}</span>
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                    <ViewButton pkg="digital" resource="ItTicket" record={row} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={tixPage.total}
        max={tixPage.max}
        offset={tixPage.offset}
        disabled={loading}
        onOffset={(o) => setTixPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setTixPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const list =
    tab === 'systems'
      ? systemsList
      : tab === 'data'
        ? dataList
        : tab === 'solutions'
          ? solutionsList
          : tab === 'changes'
            ? changesList
            : tab === 'cmdb'
              ? cmdbList
              : tab === 'support'
                ? ticketsList
                : null

  let detail = null
  let detailFooter = null

  if (tab === 'changes' && selectedChange) {
    detail = (
      <>
        <div className="inv-detail-hero">
          <div>
            <div className="about-display" style={{ fontSize: '1.35rem' }}>
              {selectedChange.name}
            </div>
            <div className="muted">
              {selectedChange.changeKind} · risk {selectedChange.riskLevel} ·{' '}
              <span className={statusClass(selectedChange.changeStatus)}>
                {selectedChange.changeStatus}
              </span>
            </div>
          </div>
        </div>
        {collapsedContentRail('digital', 'ITChange', selectedChange.id, async () =>
          setChangeContentMap(await fetchContentMap('digital', 'ITChange', changes)),
        )}
      </>
    )
    detailFooter = (
      <>
        {selectedChange.changeStatus === 'PLANNED' && (
          <button
            type="button"
            className="btn"
            disabled={!!busy}
            onClick={() =>
              run('st', async () => {
                const c = await digitalApi.changes.start(selectedChange.id)
                setSelectedChange(c)
                setInfo(t('toast.started', { ns: 'common', name: c.name }))
              })
            }
          >
            <Play size={16} /> Start
          </button>
        )}
        {['PLANNED', 'IN_PROGRESS'].includes(selectedChange.changeStatus) && (
          <button
            type="button"
            className="btn btn-primary"
            disabled={!!busy}
            onClick={() =>
              run('done', async () => {
                const c = await digitalApi.changes.complete(selectedChange.id)
                setSelectedChange(c)
                setInfo(t('toast.completed', { ns: 'common', name: c.name }))
              })
            }
          >
            <Check size={16} /> Complete
          </button>
        )}
      </>
    )
  }

  if (tab === 'support' && selectedTicket) {
    detail = (
      <>
        <div className="inv-detail-hero">
          <div>
            <div className="about-display" style={{ fontSize: '1.35rem' }}>
              {selectedTicket.ticketNumber}
            </div>
            <div className="muted">
              {selectedTicket.name} · {selectedTicket.ticketKind} ·{' '}
              <span className={statusClass(selectedTicket.ticketStatus)}>
                {selectedTicket.ticketStatus}
              </span>
            </div>
          </div>
        </div>
        {collapsedContentRail('digital', 'ItTicket', selectedTicket.id, async () =>
          setTicketContentMap(await fetchContentMap('digital', 'ItTicket', tickets)),
        )}
      </>
    )
    detailFooter = (
      <>
        {selectedTicket.ticketStatus === 'OPEN' && (
          <button
            type="button"
            className="btn"
            disabled={!!busy}
            onClick={() =>
              run('ts', async () => {
                const ticket = await digitalApi.tickets.start(selectedTicket.id)
                setSelectedTicket(ticket)
              })
            }
          >
            {t('digital.start')}
          </button>
        )}
        {['OPEN', 'IN_PROGRESS'].includes(selectedTicket.ticketStatus) && (
          <button
            type="button"
            className="btn"
            disabled={!!busy}
            onClick={() =>
              run('tr', async () => {
                const ticket = await digitalApi.tickets.resolve(selectedTicket.id)
                setSelectedTicket(ticket)
              })
            }
          >
            {t('digital.resolve')}
          </button>
        )}
        {selectedTicket.ticketStatus !== 'CLOSED' &&
          selectedTicket.ticketStatus !== 'CANCELLED' && (
            <button
              type="button"
              className="btn btn-primary"
              disabled={!!busy}
              onClick={() =>
                run('tc', async () => {
                  const ticket = await digitalApi.tickets.close(selectedTicket.id)
                  setSelectedTicket(ticket)
                })
              }
            >
              {t('actions.close', { ns: 'common' })}
            </button>
          )}
      </>
    )
  }

  const emptyTitle =
    tab === 'changes'
      ? t('digital.selectChange')
      : tab === 'support'
        ? t('digital.selectTicket')
        : t('desk.emptyTitle', { ns: 'common', defaultValue: 'Select an item' })

  const emptyBody =
    tab === 'changes' || tab === 'support'
      ? t('desk.emptyBody', { ns: 'common' })
      : t('desk.emptyBodyMasters', {
          ns: 'common',
          defaultValue: 'Use + New to create, or View on a row for the full record.',
        })

  return (
    <div className="page digital-desk-page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}
      {loading && !error && (
        <p className="muted" style={{ marginBottom: '0.75rem' }}>
          {t('actions.loading', { ns: 'common' })}
        </p>
      )}

      <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <Link className="btn btn-secondary" to="/help/it-support">
          IT Support help
        </Link>
      </div>

      <DeskShell
        // ADR-050 Wave C: keep split — ITChange/ItTicket need persistent process detail;
        // master tabs already gate empty col2 via hasSelection=false.
        layout="split"
        title={
          <>
            <Server size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('digital.title')}
          </>
        }
        tabs={tabs}
        activeTab={tab}
        onTabChange={requestTabChange}
        newLabel={newLabel}
        newExpanded={newExpanded}
        onToggleNew={requestToggleNew}
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
