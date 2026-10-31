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
import { Link, useNavigate, useParams, useSearchParams, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  Bot,
  Network,
  ShieldCheck,
  Brain,
  RefreshCw,
  Plus,
  Play,
  Pause,
  Check,
  X,
  Sparkles,
  Eye,
  Pencil,
} from 'lucide-react'
import { agentsApi, agentTokenApi } from '../api/client'
import DeskProcessSlot from '../components/desk/DeskProcessSlot'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import { hasMasterFormSpec } from '../forms/masterFormSpecs'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import { labelFromList } from '../utils/entityDisplayLabel'
import ResponsiveDataList from '../components/ResponsiveDataList'
import RowActionSlots from '../components/RowActionSlots'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'
import DeskShell from '../components/desk/DeskShell'
import DeskNewFab from '../components/DeskNewFab'
import ConfirmDialog from '../components/ConfirmDialog'
import useDeskKeyboard from '../hooks/useDeskKeyboard'
import useNarrowViewport from '../hooks/useNarrowViewport'
import useDeskMemory from '../hooks/useDeskMemory'
import { scrollSelectedIntoView } from '../hooks/useDeskSelection'

const SECTION_DEFS = [
  { id: 'agents', labelKey: 'agents.tabs.agents', icon: Bot },
  { id: 'hives', labelKey: 'agents.tabs.hives', icon: Network },
  { id: 'hitl', labelKey: 'agents.tabs.hitl', icon: ShieldCheck },
  { id: 'memory', labelKey: 'agents.tabs.memory', icon: Brain },
]

/**
 * Agentic Resources — DeskShell master–detail (ADR-047 / Nielsen).
 * Route sections: /agents/:section — agents · hives · hitl · memory. Selection via ?id=.
 */
const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'
const ACTION_TH = (t) => t('wb.action', { ns: 'common', defaultValue: 'Action' })

export default function AgentHub() {
  const { t } = useTranslation(['desks', 'common', 'content'])
  const loadAllRef = useRef(null)
  const { viewModal, ViewButton, openView, openEdit } = useRecordView({
    modalEdit: true,
    canModalEdit: ({ pkg, resource }) => hasMasterFormSpec(pkg, resource),
    onSaved: () => {
      loadAllRef.current?.().catch(() => {})
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
  const navigate = useNavigate()
  const location = useLocation()
  const narrow = useNarrowViewport()
  const SECTIONS = useMemo(
    () => SECTION_DEFS.map((d) => ({ ...d, label: t(d.labelKey), icon: d.icon })),
    [t],
  )
  const tabs = useMemo(
    () => SECTIONS.map((s) => ({ id: s.id, label: s.label, icon: s.icon })),
    [SECTIONS],
  )
  const { section } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = SECTIONS.some((s) => s.id === section) ? section : 'agents'

  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)

  const [newExpanded, setNewExpanded] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [pendingNav, setPendingNav] = useState(null)
  const [companyId] = useState('default')
  const { saveMemory } = useDeskMemory({ deskKey: 'agents', companyId })

  const [selectedId, setSelectedId] = useState(() => searchParams.get('id') || null)

  const [agents, setAgents] = useState([])
  const [hives, setHives] = useState([])
  const [approvals, setApprovals] = useState([])
  const [memories, setMemories] = useState([])
  const [links, setLinks] = useState([])
  const [agentOptions, setAgentOptions] = useState([])
  const [selectedHive, setSelectedHive] = useState(null)
  const [selectedAgent, setSelectedAgent] = useState(null)
  const [selectedApproval, setSelectedApproval] = useState(null)
  const [selectedMemory, setSelectedMemory] = useState(null)
  const [agentContentMap, setAgentContentMap] = useState({})
  const [pendingAgentContent, setPendingAgentContent] = useState([])
  const [hiveMembers, setHiveMembers] = useState([])
  const [agentPage, setAgentPage] = useState({ max: 25, offset: 0, total: 0 })
  const [hivePage, setHivePage] = useState({ max: 25, offset: 0, total: 0 })
  const [approvalPage, setApprovalPage] = useState({ max: 25, offset: 0, total: 0 })
  const [memoryPage, setMemoryPage] = useState({ max: 25, offset: 0, total: 0 })
  const [agentSort, setAgentSort] = useState('name')
  const [agentOrder, setAgentOrder] = useState('asc')
  const [hiveSort, setHiveSort] = useState('id')
  const [hiveOrder, setHiveOrder] = useState('desc')
  const [approvalSort, setApprovalSort] = useState('id')
  const [approvalOrder, setApprovalOrder] = useState('desc')
  const [memorySort, setMemorySort] = useState('id')
  const [memoryOrder, setMemoryOrder] = useState('desc')

  const AGENT_SORT_MENU = useMemo(
    () => [
      { value: 'name', label: t('wb.name', { ns: 'common' }) },
      { value: 'agentKey', label: t('wb.code', { ns: 'common' }) },
      { value: 'runtimeKind', label: t('agents.runtime') },
      { value: 'agentStatus', label: t('wb.status', { ns: 'common' }) },
      { value: 'id', label: 'Id' },
    ],
    [t],
  )
  const HIVE_SORT_MENU = useMemo(
    () => [
      { value: 'name', label: t('wb.name', { ns: 'common' }) },
      { value: 'apqcAreaCode', label: t('agents.area') },
      { value: 'hiveStatus', label: t('wb.status', { ns: 'common' }) },
      { value: 'id', label: 'Id' },
    ],
    [t],
  )
  const APPROVAL_SORT_MENU = useMemo(
    () => [
      { value: 'name', label: t('wb.title', { ns: 'common' }) },
      { value: 'riskLevel', label: t('agents.risk') },
      { value: 'decision', label: t('agents.decision') },
      { value: 'id', label: 'Id' },
    ],
    [t],
  )
  const MEMORY_SORT_MENU = useMemo(
    () => [
      { value: 'name', label: t('wb.title', { ns: 'common' }) },
      { value: 'memoryKind', label: t('wb.kind', { ns: 'common' }) },
      { value: 'score', label: t('agents.score') },
      { value: 'id', label: 'Id' },
    ],
    [t],
  )

  const onAgentSortChange = useCallback(({ sort: s, order: o }) => {
    setAgentSort(s || 'name')
    setAgentOrder(o === 'asc' ? 'asc' : 'desc')
    setAgentPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onHiveSortChange = useCallback(({ sort: s, order: o }) => {
    setHiveSort(s || 'id')
    setHiveOrder(o === 'asc' ? 'asc' : 'desc')
    setHivePage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onApprovalSortChange = useCallback(({ sort: s, order: o }) => {
    setApprovalSort(s || 'id')
    setApprovalOrder(o === 'asc' ? 'asc' : 'desc')
    setApprovalPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onMemorySortChange = useCallback(({ sort: s, order: o }) => {
    setMemorySort(s || 'id')
    setMemoryOrder(o === 'asc' ? 'asc' : 'desc')
    setMemoryPage((pg) => ({ ...pg, offset: 0 }))
  }, [])

  const [agentForm, setAgentForm] = useState({
    name: '',
    agentKey: '',
    runtimeKind: 'HERMES',
    endpointRef: '',
    taskContext: '',
    soul: '',
  })
  const [hiveForm, setHiveForm] = useState({
    name: '',
    apqcAreaCode: '',
    hiveMasterAgentId: '',
  })
  const [approvalForm, setApprovalForm] = useState({
    name: '',
    agentId: '',
    riskLevel: 'LOW',
    targetFamily: '',
    proposalJson: '',
  })
  const [memoryForm, setMemoryForm] = useState({
    name: '',
    agentId: '',
    memoryKind: 'FEEDBACK',
    score: '',
    payloadJson: '',
  })
  const [linkForm, setLinkForm] = useState({ agentId: '', linkKind: 'OWNER' })
  const [tokenCount, setTokenCount] = useState(null)

  useEffect(() => {
    saveMemory({ tab })
  }, [tab, saveMemory])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.get('new') === '1') {
      setNewExpanded(true)
      params.delete('new')
      const qs = params.toString()
      window.history.replaceState(null, '', `${window.location.pathname}${qs ? `?${qs}` : ''}`)
    }
  }, [])

  // Sync selectedId → ?id=
  useEffect(() => {
    const next = new URLSearchParams(searchParams)
    const cur = searchParams.get('id')
    const want = selectedId != null && selectedId !== '' ? String(selectedId) : ''
    if (want) {
      if (cur !== want) {
        next.set('id', want)
        setSearchParams(next, { replace: false })
      }
    } else if (cur != null) {
      next.delete('id')
      setSearchParams(next, { replace: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId])

  useEffect(() => {
    agentTokenApi
      .list()
      .then((r) => setTokenCount((r?.tokens || []).length))
      .catch(() => setTokenCount(null))
  }, [tab])

  const statusClass = (s) => {
    if (['ACTIVE', 'APPROVED', 'DONE'].includes(s)) return 'badge badge-ok'
    if (['BLOCKED', 'ERROR', 'FAILED', 'REJECTED'].includes(s)) return 'badge badge-danger'
    if (['RETIRED', 'CANCELLED', 'REJECTED', 'CLOSED', 'ARCHIVED', 'INACTIVE'].includes(s))
      return 'badge badge-muted'
    if (['PENDING', 'DRAFT', 'PAUSED'].includes(s)) return 'badge badge-warn'
    return 'badge badge-draft'
  }

  const loadSection = useCallback(async () => {
    setError(null)
    try {
      if (tab === 'agents') {
        const [a, aPick, l] = await Promise.all([
          agentsApi.agents.list({ max: agentPage.max, offset: agentPage.offset, sort: agentSort, order: agentOrder }),
          agentsApi.agents.list({ max: 100, offset: 0 }),
          agentsApi.links.list({ max: 100, offset: 0 }),
        ])
        const ag = a.items || []
        setAgents(ag)
        setAgentOptions(aPick.items || [])
        setLinks(l.items || [])
        setAgentPage((pg) => ({
          ...pg,
          max: a.max ?? pg.max,
          offset: a.offset ?? pg.offset,
          total: a.total ?? ag.length,
        }))
        setAgentContentMap(await fetchContentMap('agents', 'Agent', ag))
      } else if (tab === 'hives') {
        const [h, aPick] = await Promise.all([
          agentsApi.hives.list({ max: hivePage.max, offset: hivePage.offset, sort: hiveSort, order: hiveOrder }),
          agentsApi.agents.list({ max: 100, offset: 0 }),
        ])
        const hv = h.items || []
        setHives(hv)
        setAgentOptions(aPick.items || [])
        setHivePage((pg) => ({
          ...pg,
          max: h.max ?? pg.max,
          offset: h.offset ?? pg.offset,
          total: h.total ?? hv.length,
        }))
      } else if (tab === 'hitl') {
        const [ap, aPick] = await Promise.all([
          agentsApi.approvals.list({
            max: approvalPage.max,
            offset: approvalPage.offset,
            decision: 'PENDING',
            sort: approvalSort,
            order: approvalOrder,
          }),
          agentsApi.agents.list({ max: 100, offset: 0 }),
        ])
        const items = ap.items || []
        setApprovals(items)
        setAgentOptions(aPick.items || [])
        setApprovalPage((pg) => ({
          ...pg,
          max: ap.max ?? pg.max,
          offset: ap.offset ?? pg.offset,
          total: ap.total ?? items.length,
        }))
      } else if (tab === 'memory') {
        const [m, aPick] = await Promise.all([
          agentsApi.memories.list({ max: memoryPage.max, offset: memoryPage.offset, sort: memorySort, order: memoryOrder }),
          agentsApi.agents.list({ max: 100, offset: 0 }),
        ])
        const items = m.items || []
        setMemories(items)
        setAgentOptions(aPick.items || [])
        setMemoryPage((pg) => ({
          ...pg,
          max: m.max ?? pg.max,
          offset: m.offset ?? pg.offset,
          total: m.total ?? items.length,
        }))
      }
    } catch (e) {
      setError(e.message || String(e))
    }
  }, [
    tab,
    agentPage.max,
    agentPage.offset,
    agentSort,
    agentOrder,
    hivePage.max,
    hivePage.offset,
    hiveSort,
    hiveOrder,
    approvalPage.max,
    approvalPage.offset,
    approvalSort,
    approvalOrder,
    memoryPage.max,
    memoryPage.offset,
    memorySort,
    memoryOrder,
  ])
  loadAllRef.current = loadSection

  useEffect(() => {
    setLoading(true)
    loadSection().finally(() => setLoading(false))
  }, [loadSection])

  const run = async (label, fn) => {
    setBusy(label)
    setError(null)
    setInfo(null)
    try {
      await fn()
      await loadSection()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(null)
    }
  }

  const clearSelection = useCallback(() => {
    setSelectedId(null)
    setSelectedAgent(null)
    setSelectedHive(null)
    setSelectedApproval(null)
    setSelectedMemory(null)
    setHiveMembers([])
  }, [])

  const selectHive = useCallback(async (h) => {
    if (!h) {
      setSelectedHive(null)
      setHiveMembers([])
      return
    }
    setSelectedHive(h)
    setSelectedAgent(null)
    setSelectedApproval(null)
    setSelectedMemory(null)
    setSelectedId(String(h.id))
    try {
      const m = await agentsApi.hives.members(h.id)
      setHiveMembers(m.items || [])
    } catch (e) {
      setError(e.message)
    }
  }, [])

  const selectAgent = useCallback((a) => {
    if (!a) {
      setSelectedAgent(null)
      return
    }
    setSelectedAgent(a)
    setSelectedHive(null)
    setSelectedApproval(null)
    setSelectedMemory(null)
    setSelectedId(String(a.id))
  }, [])

  const selectApproval = useCallback((a) => {
    if (!a) {
      setSelectedApproval(null)
      return
    }
    setSelectedApproval(a)
    setSelectedAgent(null)
    setSelectedHive(null)
    setSelectedMemory(null)
    setSelectedId(String(a.id))
  }, [])

  const selectMemory = useCallback((m) => {
    if (!m) {
      setSelectedMemory(null)
      return
    }
    setSelectedMemory(m)
    setSelectedAgent(null)
    setSelectedHive(null)
    setSelectedApproval(null)
    setSelectedId(String(m.id))
  }, [])

  // Hydrate selection from selectedId after list load
  useEffect(() => {
    if (!selectedId) {
      setSelectedAgent(null)
      setSelectedHive(null)
      setSelectedApproval(null)
      setSelectedMemory(null)
      return
    }
    const sid = String(selectedId)
    if (tab === 'agents') {
      const a = agents.find((x) => String(x.id) === sid)
      if (a) setSelectedAgent(a)
      else if (!loading) setSelectedAgent(null)
    } else if (tab === 'hives') {
      const h = hives.find((x) => String(x.id) === sid)
      if (h && selectedHive?.id !== h.id) selectHive(h)
      else if (!h && !loading) setSelectedHive(null)
    } else if (tab === 'hitl') {
      const a = approvals.find((x) => String(x.id) === sid)
      if (a) setSelectedApproval(a)
      else if (!loading) setSelectedApproval(null)
    } else if (tab === 'memory') {
      const m = memories.find((x) => String(x.id) === sid)
      if (m) setSelectedMemory(m)
      else if (!loading) setSelectedMemory(null)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, selectedId, agents, hives, approvals, memories, loading])

  useEffect(() => {
    if (!selectedId) return
    const id = window.requestAnimationFrame(() => {
      scrollSelectedIntoView('.desk-shell-list')
    })
    return () => window.cancelAnimationFrame(id)
  }, [selectedId, tab, selectedAgent?.id, selectedHive?.id, selectedApproval?.id, selectedMemory?.id])

  const resetCreateForms = useCallback(() => {
    setAgentForm({
      name: '',
      agentKey: '',
      runtimeKind: 'HERMES',
      endpointRef: '',
      taskContext: '',
      soul: '',
    })
    setPendingAgentContent([])
    setHiveForm({ name: '', apqcAreaCode: '', hiveMasterAgentId: '' })
    setApprovalForm({
      name: '',
      agentId: '',
      riskLevel: 'LOW',
      targetFamily: '',
      proposalJson: '',
    })
    setMemoryForm({
      name: '',
      agentId: '',
      memoryKind: 'FEEDBACK',
      score: '',
      payloadJson: '',
    })
    setLinkForm({ agentId: '', linkKind: 'OWNER' })
  }, [])

  const createDirty = useMemo(() => {
    if (tab === 'agents') {
      return Boolean(
        (agentForm.name || '').trim() ||
          (agentForm.agentKey || '').trim() ||
          agentForm.runtimeKind !== 'HERMES' ||
          (agentForm.endpointRef || '').trim() ||
          (agentForm.taskContext || '').trim() ||
          (agentForm.soul || '').trim() ||
          pendingAgentContent.length ||
          linkForm.agentId,
      )
    }
    if (tab === 'hives') {
      return Boolean(
        (hiveForm.name || '').trim() ||
          (hiveForm.apqcAreaCode || '').trim() ||
          hiveForm.hiveMasterAgentId,
      )
    }
    if (tab === 'hitl') {
      return Boolean(
        (approvalForm.name || '').trim() ||
          approvalForm.agentId ||
          approvalForm.riskLevel !== 'LOW' ||
          (approvalForm.targetFamily || '').trim() ||
          (approvalForm.proposalJson || '').trim(),
      )
    }
    if (tab === 'memory') {
      return Boolean(
        (memoryForm.name || '').trim() ||
          memoryForm.agentId ||
          memoryForm.memoryKind !== 'FEEDBACK' ||
          memoryForm.score !== '' ||
          (memoryForm.payloadJson || '').trim(),
      )
    }
    return false
  }, [tab, agentForm, pendingAgentContent, linkForm, hiveForm, approvalForm, memoryForm])

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
      navigate(`/agents/${nextTab}`)
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
      navigate(`/agents/${nav.tab}`)
    }
  }, [pendingNav, applyCollapseCreate, clearSelection, navigate])

  const cancelDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
  }, [])

  const keyboardIds = useMemo(() => {
    if (tab === 'agents') return agents.map((a) => a.id)
    if (tab === 'hives') return hives.map((h) => h.id)
    if (tab === 'hitl') return approvals.map((a) => a.id)
    if (tab === 'memory') return memories.map((m) => m.id)
    return []
  }, [tab, agents, hives, approvals, memories])

  const keyboardSelectedId = useMemo(() => {
    if (tab === 'agents') return selectedAgent?.id ?? null
    if (tab === 'hives') return selectedHive?.id ?? null
    if (tab === 'hitl') return selectedApproval?.id ?? null
    if (tab === 'memory') return selectedMemory?.id ?? null
    return null
  }, [tab, selectedAgent?.id, selectedHive?.id, selectedApproval?.id, selectedMemory?.id])

  const setKeyboardSelectedId = useCallback(
    (id) => {
      if (id == null) {
        clearSelection()
        return
      }
      const sid = String(id)
      if (tab === 'agents') {
        const a = agents.find((x) => String(x.id) === sid)
        if (a) selectAgent(a)
      } else if (tab === 'hives') {
        const h = hives.find((x) => String(x.id) === sid)
        if (h) selectHive(h)
      } else if (tab === 'hitl') {
        const a = approvals.find((x) => String(x.id) === sid)
        if (a) selectApproval(a)
      } else if (tab === 'memory') {
        const m = memories.find((x) => String(x.id) === sid)
        if (m) selectMemory(m)
      }
    },
    [
      tab,
      agents,
      hives,
      approvals,
      memories,
      clearSelection,
      selectAgent,
      selectHive,
      selectApproval,
      selectMemory,
    ],
  )

  useDeskKeyboard({
    enabled: !newExpanded,
    itemIds: keyboardIds,
    selectedId: keyboardSelectedId,
    setSelectedId: setKeyboardSelectedId,
    onToggleNew: requestToggleNew,
  })

  const sectionMeta = SECTIONS.find((s) => s.id === tab)
  const SectionIcon = sectionMeta?.icon || Bot

  const newLabel = useMemo(() => {
    if (tab === 'agents') return t('agents.newAgent')
    if (tab === 'hives') return t('agents.newHive')
    if (tab === 'hitl') {
      return t('agents.submitProposal', { defaultValue: 'Submit proposal' })
    }
    if (tab === 'memory') {
      return t('agents.recordMemory', { defaultValue: 'Record learning signal' })
    }
    return t('desk.new', { ns: 'common', defaultValue: 'New' })
  }, [tab, t])

  const hasSelection = useMemo(() => {
    if (tab === 'agents') return Boolean(selectedAgent?.id)
    if (tab === 'hives') return Boolean(selectedHive?.id)
    if (tab === 'hitl') return Boolean(selectedApproval?.id)
    if (tab === 'memory') return Boolean(selectedMemory?.id)
    return false
  }, [tab, selectedAgent?.id, selectedHive?.id, selectedApproval?.id, selectedMemory?.id])

  const helpContext = useMemo(() => {
    const base = { path: location.pathname, desk: 'agents', tab }
    if (tab === 'agents' && selectedAgent) {
      return {
        ...base,
        resource: 'Agent',
        objectId: selectedAgent.id,
        status: selectedAgent.agentStatus,
      }
    }
    if (tab === 'hives' && selectedHive) {
      return {
        ...base,
        resource: 'Hive',
        objectId: selectedHive.id,
        status: selectedHive.hiveStatus,
      }
    }
    if (tab === 'hitl' && selectedApproval) {
      return {
        ...base,
        resource: 'Approval',
        objectId: selectedApproval.id,
        status: selectedApproval.decision,
        processCode: 'AR_HITL_APPROVAL',
      }
    }
    if (tab === 'memory' && selectedMemory) {
      return {
        ...base,
        resource: 'Memory',
        objectId: selectedMemory.id,
        status: selectedMemory.memoryKind,
      }
    }
    return base
  }, [location.pathname, tab, selectedAgent, selectedHive, selectedApproval, selectedMemory])

  // —— create panels ——
  const agentsCreatePanel = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Seed core agents
      </h2>
      <button
        type="button"
        className="btn btn-primary"
        disabled={!!busy}
        onClick={() =>
          run('seed', async () => {
            const r = await agentsApi.agents.seedCore()
            setInfo(t('toast.coreAgents', { ns: 'common', count: (r.items || []).length }))
            setNewExpanded(false)
          })
        }
      >
        <Sparkles size={16} /> Seed 5 core agents
      </button>

      <h2 className="section-label">{t('agents.newAgent')}</h2>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('create', async () => {
            const created = await agentsApi.agents.create(agentForm)
            if (pendingAgentContent.length) {
              await attachPendingContent('agents', 'Agent', created.id, pendingAgentContent)
              setPendingAgentContent([])
            }
            setInfo(
              t('toast.namedCreated', {
                ns: 'common',
                what: t('labels.agent', { ns: 'common' }),
                name: agentForm.name || agentForm.agentKey,
              }),
            )
            resetCreateForms()
            setNewExpanded(false)
            setSelectedId(String(created.id))
          })
        }}
      >
        <label>
          Name *
          <input
            required
            value={agentForm.name}
            onChange={(e) => setAgentForm((f) => ({ ...f, name: e.target.value }))}
          />
        </label>
        <div className="form-row-2">
          <label>
            Key
            <input
              placeholder={t('ph.autoFromName', { ns: 'common' })}
              value={agentForm.agentKey}
              onChange={(e) => setAgentForm((f) => ({ ...f, agentKey: e.target.value }))}
            />
          </label>
          <label>
            Runtime
            <select
              value={agentForm.runtimeKind}
              onChange={(e) => setAgentForm((f) => ({ ...f, runtimeKind: e.target.value }))}
            >
              {['HERMES', 'CUSTOM', 'EXTERNAL'].map((x) => (
                <option key={x} value={x}>
                  {x}
                </option>
              ))}
            </select>
          </label>
        </div>
        {(agentForm.runtimeKind === 'CUSTOM' || agentForm.runtimeKind === 'EXTERNAL') && (
          <label>
            Endpoint ref
            <input
              value={agentForm.endpointRef}
              onChange={(e) => setAgentForm((f) => ({ ...f, endpointRef: e.target.value }))}
            />
          </label>
        )}
        <label>
          Task context (AGENTS.md)
          <textarea
            rows={3}
            value={agentForm.taskContext}
            onChange={(e) => setAgentForm((f) => ({ ...f, taskContext: e.target.value }))}
          />
        </label>
        <label>
          Soul (SOUL.md)
          <textarea
            rows={2}
            value={agentForm.soul}
            onChange={(e) => setAgentForm((f) => ({ ...f, soul: e.target.value }))}
          />
        </label>
        <PendingContentComposer
          package="agents"
          resource="Agent"
          pending={pendingAgentContent}
          onChange={setPendingAgentContent}
        />
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('agents.createAgent')}
        </button>
      </form>

      <h2 className="section-label">{t('agents.linkHuman')}</h2>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('link', async () => {
            await agentsApi.links.create({
              agentId: linkForm.agentId,
              linkKind: linkForm.linkKind,
            })
            setInfo(t('agents.linkCreated'))
            setLinkForm({ agentId: '', linkKind: 'OWNER' })
            setNewExpanded(false)
          })
        }}
      >
        <div className="form-row-2">
          <label>
            Agent
            <select
              required
              value={linkForm.agentId}
              onChange={(e) => setLinkForm((f) => ({ ...f, agentId: e.target.value }))}
            >
              <option value="">—</option>
              {agentOptions.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Kind
            <select
              value={linkForm.linkKind}
              onChange={(e) => setLinkForm((f) => ({ ...f, linkKind: e.target.value }))}
            >
              {['OWNER', 'OPERATOR', 'REVIEWER'].map((x) => (
                <option key={x} value={x}>
                  {x}
                </option>
              ))}
            </select>
          </label>
        </div>
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> Link
        </button>
      </form>
    </>
  )

  const hivesCreatePanel = (
    <>
      <p className="muted" style={{ marginTop: 0 }}>
        Each hive needs one <strong>{t('agents.hiveMaster')}</strong> (coordinator) to activate.
      </p>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('hive', async () => {
            const created = await agentsApi.hives.create({
              name: hiveForm.name,
              apqcAreaCode: hiveForm.apqcAreaCode || null,
              hiveMasterAgentId: hiveForm.hiveMasterAgentId || null,
            })
            setInfo(t('agents.hiveCreated'))
            resetCreateForms()
            setNewExpanded(false)
            if (created?.id) setSelectedId(String(created.id))
          })
        }}
      >
        <label>
          Name *
          <input
            required
            value={hiveForm.name}
            onChange={(e) => setHiveForm((f) => ({ ...f, name: e.target.value }))}
          />
        </label>
        <div className="form-row-2">
          <label>
            APQC area
            <input
              placeholder={t('ph.areaExample', { ns: 'common' })}
              value={hiveForm.apqcAreaCode}
              onChange={(e) => setHiveForm((f) => ({ ...f, apqcAreaCode: e.target.value }))}
            />
          </label>
          <label>
            {t('agents.hiveMaster')}
            <select
              value={hiveForm.hiveMasterAgentId}
              onChange={(e) => setHiveForm((f) => ({ ...f, hiveMasterAgentId: e.target.value }))}
            >
              <option value="">— set later —</option>
              {agentOptions.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('agents.createHive')}
        </button>
      </form>
    </>
  )

  const hitlCreatePanel = (
    <>
      <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
        {t('agents.hitlPanelHint', {
          defaultValue:
            'Pending proposals from Hermes agents and desk submissions. Approve or reject in detail.',
        })}
      </p>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('sub', async () => {
            const created = await agentsApi.approvals.submit({
              name: approvalForm.name,
              agentId: approvalForm.agentId || null,
              riskLevel: approvalForm.riskLevel,
              targetFamily: approvalForm.targetFamily || null,
              proposalJson: approvalForm.proposalJson || null,
            })
            setInfo(t('agents.proposalPending'))
            resetCreateForms()
            setNewExpanded(false)
            if (created?.id) setSelectedId(String(created.id))
          })
        }}
      >
        <label>
          Title *
          <input
            required
            value={approvalForm.name}
            onChange={(e) => setApprovalForm((f) => ({ ...f, name: e.target.value }))}
          />
        </label>
        <div className="form-row-2">
          <label>
            Agent
            <select
              value={approvalForm.agentId}
              onChange={(e) => setApprovalForm((f) => ({ ...f, agentId: e.target.value }))}
            >
              <option value="">— optional —</option>
              {agentOptions.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Risk
            <select
              value={approvalForm.riskLevel}
              onChange={(e) => setApprovalForm((f) => ({ ...f, riskLevel: e.target.value }))}
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
          Target family
          <input
            placeholder={t('ph.familiesExample', { ns: 'common' })}
            value={approvalForm.targetFamily}
            onChange={(e) => setApprovalForm((f) => ({ ...f, targetFamily: e.target.value }))}
          />
        </label>
        <label>
          Proposal JSON
          <textarea
            rows={3}
            value={approvalForm.proposalJson}
            onChange={(e) => setApprovalForm((f) => ({ ...f, proposalJson: e.target.value }))}
          />
        </label>
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> Submit
        </button>
      </form>
    </>
  )

  const memoryCreatePanel = (
    <form
      className="form-grid"
      onSubmit={(e) => {
        e.preventDefault()
        run('mem', async () => {
          const created = await agentsApi.memories.record({
            name: memoryForm.name,
            agentId: memoryForm.agentId || null,
            memoryKind: memoryForm.memoryKind,
            score: memoryForm.score || null,
            payloadJson: memoryForm.payloadJson || null,
          })
          setInfo(t('agents.memoryRecorded'))
          resetCreateForms()
          setNewExpanded(false)
          if (created?.id) setSelectedId(String(created.id))
        })
      }}
    >
      <label>
        Title *
        <input
          required
          value={memoryForm.name}
          onChange={(e) => setMemoryForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          Agent
          <select
            value={memoryForm.agentId}
            onChange={(e) => setMemoryForm((f) => ({ ...f, agentId: e.target.value }))}
          >
            <option value="">— optional —</option>
            {agentOptions.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Kind
          <select
            value={memoryForm.memoryKind}
            onChange={(e) => setMemoryForm((f) => ({ ...f, memoryKind: e.target.value }))}
          >
            {['FEEDBACK', 'EVAL', 'LESSON', 'MARKETPLACE_REF'].map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label>
        Score (0–1)
        <input
          value={memoryForm.score}
          onChange={(e) => setMemoryForm((f) => ({ ...f, score: e.target.value }))}
        />
      </label>
      <label>
        Payload JSON
        <textarea
          rows={2}
          value={memoryForm.payloadJson}
          onChange={(e) => setMemoryForm((f) => ({ ...f, payloadJson: e.target.value }))}
        />
      </label>
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> Record
      </button>
    </form>
  )

  const newPanel =
    tab === 'agents'
      ? agentsCreatePanel
      : tab === 'hives'
        ? hivesCreatePanel
        : tab === 'hitl'
          ? hitlCreatePanel
          : tab === 'memory'
            ? memoryCreatePanel
            : null

  // —— lists ——
  const agentsList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Agents ({agentPage.total})
      </h2>
      <ResponsiveDataList
        items={agents}
        empty="—"
        sortFields={AGENT_SORT_MENU}
        sort={agentSort}
        order={agentOrder}
        onSortChange={onAgentSortChange}
        loading={loading}
        selectedKey={selectedAgent?.id}
        onSelect={(a) => selectAgent(a)}
        fields={[
          {
            id: 'name',
            primary: true,
            render: (a) => (
              <>
                {a.name}
                {a.taskContext ? (
                  <div className="muted" style={{ fontSize: 12, maxWidth: 220 }}>
                    {a.taskContext.slice(0, 80)}
                    {a.taskContext.length > 80 ? '…' : ''}
                  </div>
                ) : null}
              </>
            ),
          },
          {
            id: 'key',
            secondary: true,
            render: (a) => (
              <span className="mono">
                {a.agentKey} · {a.runtimeKind}
              </span>
            ),
          },
          {
            id: 'status',
            meta: true,
            render: (a) => <span className={statusClass(a.agentStatus)}>{a.agentStatus}</span>,
          },
          {
            id: 'content',
            priority: 'low',
            render: (a) => <ContentChannelIcons summary={agentContentMap[a.id]} />,
          },
        ]}
        actions={(a) => renderRowActions('agents', 'Agent', a)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={agentSort}
                  order={agentOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onAgentSortChange}
                />
                <ListSortHeader
                  field="agentKey"
                  label={t('wb.code', { ns: 'common' })}
                  sort={agentSort}
                  order={agentOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onAgentSortChange}
                  className="col-priority-low"
                />
                <ListSortHeader
                  field="runtimeKind"
                  label={t('agents.runtime')}
                  sort={agentSort}
                  order={agentOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onAgentSortChange}
                  className="col-priority-low"
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                <ListSortHeader
                  field="agentStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={agentSort}
                  order={agentOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onAgentSortChange}
                />
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {agents.map((a) => (
                <tr
                  key={a.id}
                  data-selected={selectedAgent?.id === a.id ? 'true' : undefined}
                  className={selectedAgent?.id === a.id ? 'row-selected' : 'row-click'}
                  onClick={() => selectAgent(a)}
                >
                  <td>
                    {a.name}
                    {a.taskContext ? (
                      <div className="muted" style={{ fontSize: 12, maxWidth: 220 }}>
                        {a.taskContext.slice(0, 80)}
                        {a.taskContext.length > 80 ? '…' : ''}
                      </div>
                    ) : null}
                  </td>
                  <td className="mono col-priority-low">{a.agentKey}</td>
                  <td className="col-priority-low">{a.runtimeKind}</td>
                  <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                    <ContentChannelIcons summary={agentContentMap[a.id]} />
                  </td>
                  <td>
                    <span className={statusClass(a.agentStatus)}>{a.agentStatus}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('agents', 'Agent', a)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={agentPage.total}
        max={agentPage.max}
        offset={agentPage.offset}
        disabled={loading}
        onOffset={(o) => setAgentPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setAgentPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
      <h2 className="section-label">Links ({links.filter((l) => l.active).length} active)</h2>
      <ul className="muted">
        {links.map((l) => (
          <li key={l.id}>
            {labelFromList(agents, l.agentId)} · {l.linkKind} ·{' '}
            {l.active ? 'active' : 'inactive'}
          </li>
        ))}
      </ul>
    </>
  )

  const hivesList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Hives ({hivePage.total})
      </h2>
      <ResponsiveDataList
        items={hives}
        empty="—"
        sortFields={HIVE_SORT_MENU}
        sort={hiveSort}
        order={hiveOrder}
        onSortChange={onHiveSortChange}
        loading={loading}
        selectedKey={selectedHive?.id}
        onSelect={(h) => selectHive(h)}
        fields={[
          { id: 'name', primary: true, render: (h) => h.name },
          {
            id: 'area',
            secondary: true,
            render: (h) => <span className="mono">{h.apqcAreaCode || '—'}</span>,
          },
          {
            id: 'status',
            meta: true,
            render: (h) => <span className={statusClass(h.hiveStatus)}>{h.hiveStatus}</span>,
          },
          {
            id: 'master',
            priority: 'low',
            render: (h) => h.hiveMasterAgentId || '—',
          },
        ]}
        actions={(h) => renderRowActions('agents', 'Hive', h)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={hiveSort}
                  order={hiveOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onHiveSortChange}
                />
                <ListSortHeader
                  field="apqcAreaCode"
                  label={t('agents.area')}
                  sort={hiveSort}
                  order={hiveOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onHiveSortChange}
                />
                <th className="col-priority-low">{t('agents.master')}</th>
                <ListSortHeader
                  field="hiveStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={hiveSort}
                  order={hiveOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onHiveSortChange}
                />
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {hives.map((h) => (
                <tr
                  key={h.id}
                  data-selected={selectedHive?.id === h.id ? 'true' : undefined}
                  className={selectedHive?.id === h.id ? 'row-selected' : 'row-click'}
                  onClick={() => selectHive(h)}
                >
                  <td>{h.name}</td>
                  <td className="mono">{h.apqcAreaCode || '—'}</td>
                  <td className="col-priority-low">{h.hiveMasterAgentId || '—'}</td>
                  <td>
                    <span className={statusClass(h.hiveStatus)}>{h.hiveStatus}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('agents', 'Hive', h)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={hivePage.total}
        max={hivePage.max}
        offset={hivePage.offset}
        disabled={loading}
        onOffset={(o) => setHivePage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setHivePage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const hitlList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Pending approvals ({approvalPage.total})
      </h2>
      <ResponsiveDataList
        items={approvals}
        empty="—"
        sortFields={APPROVAL_SORT_MENU}
        sort={approvalSort}
        order={approvalOrder}
        onSortChange={onApprovalSortChange}
        loading={loading}
        selectedKey={selectedApproval?.id}
        onSelect={(a) => selectApproval(a)}
        fields={[
          {
            id: 'name',
            primary: true,
            render: (a) => (
              <>
                {a.name}
                {a.targetFamily ? (
                  <div className="muted" style={{ fontSize: 12 }}>
                    → {a.targetFamily}
                  </div>
                ) : null}
              </>
            ),
          },
          { id: 'risk', secondary: true, render: (a) => a.riskLevel },
          {
            id: 'decision',
            meta: true,
            render: (a) => <span className={statusClass(a.decision)}>{a.decision}</span>,
          },
        ]}
        actions={(a) => renderRowActions('agents', 'Approval', a)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.title', { ns: 'common' })}
                  sort={approvalSort}
                  order={approvalOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onApprovalSortChange}
                />
                <ListSortHeader
                  field="riskLevel"
                  label={t('agents.risk')}
                  sort={approvalSort}
                  order={approvalOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onApprovalSortChange}
                />
                <ListSortHeader
                  field="decision"
                  label={t('agents.decision')}
                  sort={approvalSort}
                  order={approvalOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onApprovalSortChange}
                />
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {approvals.map((a) => (
                <tr
                  key={a.id}
                  data-selected={selectedApproval?.id === a.id ? 'true' : undefined}
                  className={selectedApproval?.id === a.id ? 'row-selected' : 'row-click'}
                  onClick={() => selectApproval(a)}
                >
                  <td>
                    {a.name}
                    {a.targetFamily ? (
                      <div className="muted" style={{ fontSize: 12 }}>
                        → {a.targetFamily}
                      </div>
                    ) : null}
                  </td>
                  <td>{a.riskLevel}</td>
                  <td>
                    <span className={statusClass(a.decision)}>{a.decision}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('agents', 'Approval', a)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={approvalPage.total}
        max={approvalPage.max}
        offset={approvalPage.offset}
        disabled={loading}
        onOffset={(o) => setApprovalPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setApprovalPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const memoryList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Memory ({memoryPage.total})
      </h2>
      <ResponsiveDataList
        items={memories}
        empty="—"
        sortFields={MEMORY_SORT_MENU}
        sort={memorySort}
        order={memoryOrder}
        onSortChange={onMemorySortChange}
        loading={loading}
        selectedKey={selectedMemory?.id}
        onSelect={(m) => selectMemory(m)}
        fields={[
          { id: 'name', primary: true, render: (m) => m.name },
          { id: 'kind', secondary: true, render: (m) => m.memoryKind },
          {
            id: 'score',
            meta: true,
            render: (m) => (m.score != null ? m.score : '—'),
          },
          {
            id: 'agent',
            priority: 'low',
            render: (m) => labelFromList(agents, m.agentId),
          },
        ]}
        actions={(m) => renderRowActions('agents', 'Memory', m)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.title', { ns: 'common' })}
                  sort={memorySort}
                  order={memoryOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onMemorySortChange}
                />
                <ListSortHeader
                  field="memoryKind"
                  label={t('wb.kind', { ns: 'common' })}
                  sort={memorySort}
                  order={memoryOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onMemorySortChange}
                />
                <ListSortHeader
                  field="score"
                  label={t('agents.score')}
                  sort={memorySort}
                  order={memoryOrder}
                  defaultSort="id"
                  defaultOrder="desc"
                  onSortChange={onMemorySortChange}
                />
                <th className="col-priority-low">{t('agents.tabs.agents')}</th>
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {memories.map((m) => (
                <tr
                  key={m.id}
                  data-selected={selectedMemory?.id === m.id ? 'true' : undefined}
                  className={selectedMemory?.id === m.id ? 'row-selected' : 'row-click'}
                  onClick={() => selectMemory(m)}
                >
                  <td>{m.name}</td>
                  <td>{m.memoryKind}</td>
                  <td>{m.score != null ? m.score : '—'}</td>
                  <td className="col-priority-low">{labelFromList(agents, m.agentId)}</td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('agents', 'Memory', m)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={memoryPage.total}
        max={memoryPage.max}
        offset={memoryPage.offset}
        disabled={loading}
        onOffset={(o) => setMemoryPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setMemoryPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const list =
    tab === 'agents'
      ? agentsList
      : tab === 'hives'
        ? hivesList
        : tab === 'hitl'
          ? hitlList
          : tab === 'memory'
            ? memoryList
            : null

  // —— detail ——
  let detail = null
  let detailFooter = null

  if (tab === 'agents' && selectedAgent) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <div className="inv-detail-hero">
          <div>
            <div className="about-display" style={{ fontSize: '1.25rem' }}>
              {selectedAgent.name}
            </div>
            <div className="muted">
              <span className="mono">{selectedAgent.agentKey}</span>
              {' · '}
              {selectedAgent.runtimeKind}
              {' · '}
              <span className={statusClass(selectedAgent.agentStatus)}>
                {selectedAgent.agentStatus}
              </span>
            </div>
          </div>
        </div>
        {selectedAgent.taskContext && (
          <p className="muted" style={{ whiteSpace: 'pre-wrap' }}>
            {selectedAgent.taskContext}
          </p>
        )}
        {selectedAgent.soul && (
          <p className="muted" style={{ whiteSpace: 'pre-wrap' }}>
            <strong>Soul:</strong> {selectedAgent.soul}
          </p>
        )}
        <div className="toolbar">
          {selectedAgent.agentStatus !== 'ACTIVE' && selectedAgent.agentStatus !== 'RETIRED' && (
            <button
              type="button"
              className="btn"
              disabled={!!busy}
              onClick={() =>
                run('act', async () => {
                  await agentsApi.agents.activate(selectedAgent.id)
                  setInfo(t('toast.activated', { ns: 'common', name: selectedAgent.name }))
                })
              }
            >
              <Play size={16} /> Activate
            </button>
          )}
          {selectedAgent.agentStatus === 'ACTIVE' && (
            <button
              type="button"
              className="btn"
              disabled={!!busy}
              onClick={() =>
                run('pause', async () => {
                  await agentsApi.agents.pause(selectedAgent.id)
                  setInfo(t('toast.paused', { ns: 'common', name: selectedAgent.name }))
                })
              }
            >
              <Pause size={16} /> Pause
            </button>
          )}
          {selectedAgent.agentStatus !== 'RETIRED' && (
            <button
              type="button"
              className="btn btn-ghost"
              disabled={!!busy}
              onClick={() =>
                run('ret', async () => {
                  await agentsApi.agents.retire(selectedAgent.id)
                  setInfo(t('toast.retired', { ns: 'common', name: selectedAgent.name }))
                })
              }
            >
              <X size={16} /> Retire
            </button>
          )}
          <ViewButton pkg="agents" resource="Agent" record={selectedAgent} />
        </div>
        <ContentRail
          package="agents"
          resource="Agent"
          id={selectedAgent.id}
          onChanged={async () =>
            setAgentContentMap(await fetchContentMap('agents', 'Agent', agents))
          }
        />
      </div>
    )
  } else if (tab === 'hives' && selectedHive) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <div className="inv-detail-hero">
          <div>
            <div className="about-display" style={{ fontSize: '1.25rem' }}>
              {selectedHive.name}
            </div>
            <div className="muted">
              <span className="mono">{selectedHive.apqcAreaCode || '—'}</span>
              {' · '}
              <span className={statusClass(selectedHive.hiveStatus)}>
                {selectedHive.hiveStatus}
              </span>
              {selectedHive.hiveMasterAgentId
                ? ` · master ${labelFromList(agents, selectedHive.hiveMasterAgentId)}`
                : ''}
            </div>
          </div>
        </div>
        <div className="toolbar">
          {selectedHive.hiveStatus !== 'ACTIVE' && selectedHive.hiveStatus !== 'CLOSED' && (
            <button
              type="button"
              className="btn btn-primary"
              disabled={!!busy}
              onClick={() =>
                run('hact', async () => {
                  await agentsApi.hives.activate(selectedHive.id)
                  setInfo(t('toast.activated', { ns: 'common', name: selectedHive.name }))
                })
              }
            >
              Activate
            </button>
          )}
          {selectedHive.hiveStatus === 'ACTIVE' && (
            <button
              type="button"
              className="btn"
              disabled={!!busy}
              onClick={() =>
                run('hclose', async () => {
                  await agentsApi.hives.close(selectedHive.id)
                  setInfo(t('toast.closedName', { ns: 'common', name: selectedHive.name }))
                })
              }
            >
              Close
            </button>
          )}
          <ViewButton pkg="agents" resource="Hive" record={selectedHive} />
        </div>
        <h3 className="section-label">Members · {selectedHive.name}</h3>
        <form
          className="form-grid"
          onSubmit={(e) => {
            e.preventDefault()
            const fd = new FormData(e.target)
            const agentId = fd.get('agentId')
            run('assign', async () => {
              await agentsApi.hives.assign(selectedHive.id, agentId)
              setInfo(t('toast.assigned', { ns: 'common' }))
              await selectHive(selectedHive)
            })
          }}
        >
          <label>
            Assign agent
            <select name="agentId" required>
              <option value="">—</option>
              {agentOptions.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className="btn btn-primary" disabled={!!busy}>
            <Plus size={16} /> Assign
          </button>
        </form>
        <ul className="muted">
          {hiveMembers.map((m) => (
            <li key={m.id}>
              {m.name} ({m.agentKey})
              {selectedHive.hiveMasterAgentId === m.id ? ' · Master' : ''}
            </li>
          ))}
        </ul>
      </div>
    )
  } else if (tab === 'hitl' && selectedApproval) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <div className="inv-detail-hero">
          <div>
            <div className="about-display" style={{ fontSize: '1.25rem' }}>
              {selectedApproval.name}
            </div>
            <div className="muted">
              {selectedApproval.riskLevel}
              {selectedApproval.targetFamily ? ` · → ${selectedApproval.targetFamily}` : ''}
              {' · '}
              <span className={statusClass(selectedApproval.decision)}>
                {selectedApproval.decision}
              </span>
            </div>
          </div>
        </div>
        {selectedApproval.proposalJson && (
          <pre
            className="mono"
            style={{ fontSize: 12, whiteSpace: 'pre-wrap', overflow: 'auto' }}
          >
            {typeof selectedApproval.proposalJson === 'string'
              ? selectedApproval.proposalJson
              : JSON.stringify(selectedApproval.proposalJson, null, 2)}
          </pre>
        )}
        <ViewButton pkg="agents" resource="Approval" record={selectedApproval} />
      </div>
    )
    if (selectedApproval.decision === 'PENDING') {
      detailFooter = (
        <>
          <button
            type="button"
            className="btn btn-primary"
            disabled={!!busy}
            onClick={() =>
              run('ok', async () => {
                await agentsApi.approvals.approve(selectedApproval.id)
                setInfo(t('toast.approved', { ns: 'common', name: selectedApproval.name }))
              })
            }
          >
            <Check size={16} /> Approve
          </button>
          <button
            type="button"
            className="btn"
            disabled={!!busy}
            onClick={() =>
              run('no', async () => {
                await agentsApi.approvals.reject(selectedApproval.id)
                setInfo(t('toast.rejected', { ns: 'common', name: selectedApproval.name }))
              })
            }
          >
            <X size={16} /> Reject
          </button>
        </>
      )
    }
  } else if (tab === 'memory' && selectedMemory) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <div className="inv-detail-hero">
          <div>
            <div className="about-display" style={{ fontSize: '1.25rem' }}>
              {selectedMemory.name}
            </div>
            <div className="muted">
              {selectedMemory.memoryKind}
              {selectedMemory.score != null ? ` · score ${selectedMemory.score}` : ''}
              {selectedMemory.agentId
                ? ` · agent ${labelFromList(agents, selectedMemory.agentId)}`
                : ''}
            </div>
          </div>
        </div>
        {selectedMemory.payloadJson && (
          <pre
            className="mono"
            style={{ fontSize: 12, whiteSpace: 'pre-wrap', overflow: 'auto' }}
          >
            {typeof selectedMemory.payloadJson === 'string'
              ? selectedMemory.payloadJson
              : JSON.stringify(selectedMemory.payloadJson, null, 2)}
          </pre>
        )}
        <ViewButton pkg="agents" resource="Memory" record={selectedMemory} />
      </div>
    )
    detailFooter = (
      <button
        type="button"
        className="btn"
        disabled={!!busy}
        onClick={() =>
          run('arch', async () => {
            await agentsApi.memories.archive(selectedMemory.id)
            setInfo(t('toast.archived', { ns: 'common' }))
            clearSelection()
          })
        }
      >
        Archive
      </button>
    )
  }

  const processSlot =
    tab === 'hitl' ? (
      <DeskProcessSlot processCode="AR_HITL_APPROVAL" />
    ) : null

  return (
    <div className="page delivery-desk-page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}
      {loading && !error && (
        <p className="muted" style={{ marginBottom: '0.75rem' }}>
          Loading…
        </p>
      )}

      {(tab === 'agents' || tab === 'hitl') && (
        <div className="banner-info" role="status" style={{ marginBottom: '0.75rem' }}>
          <strong>
            {t('pages.settings.activationChecklistTitle', {
              ns: 'common',
              defaultValue: 'Activate agent (API)',
            })}
          </strong>
          <p style={{ margin: '0.35rem 0' }}>
            {t('pages.settings.activationChecklistBlurb', {
              ns: 'common',
              defaultValue:
                'Agent Basics: mint a token in Settings, paste into your operator-owned agent host, prove a read, optionally propose for HITL. Agents do not settle money by default.',
            })}
          </p>
          <ol style={{ margin: '0.25rem 0 0.5rem 1.25rem' }}>
            <li>
              <Link to="/settings">Settings → Hermes agents</Link>
              {tokenCount === 0 ? (
                <span className="muted"> — {t('pages.settings.noTokens', { ns: 'common' })}</span>
              ) : tokenCount != null ? (
                <span className="muted"> — {tokenCount} token(s)</span>
              ) : null}
            </li>
            <li>
              {t('agents.activateStepPaste', {
                defaultValue: 'Paste token into agent host (Hermes / compatible)',
              })}
            </li>
            <li>
              {t('agents.activateStepSmoke', { defaultValue: 'Smoke read: /api/agent/me' })}
            </li>
            <li>
              <Link to="/agents/hitl">{t('agents.tabs.hitl')}</Link>
              {' — '}
              {t('agents.activateStepHitl', {
                defaultValue: 'optional propose → human approve',
              })}
            </li>
          </ol>
          <p className="muted" style={{ margin: 0, fontSize: '0.85rem' }}>
            {t('agents.activateDocs', {
              defaultValue:
                'Guide: docs/installation/ACTIVATE-AGENT.md · Help: Activate agent (API)',
            })}{' '}
            · <Link to="/help">{t('nav.help', { ns: 'common', defaultValue: 'Help' })}</Link>
          </p>
        </div>
      )}

      <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
        <button
          type="button"
          className="btn"
          onClick={() => loadSection()}
          disabled={loading || !!busy}
        >
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
      </div>

      <DeskShell
        // ADR-050 Wave D: split for agent/hive/HITL process detail; col2 gated by hasSelection.
        layout="split"
        title={
          <>
            <SectionIcon size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {sectionMeta?.label || t('agents.title')}
          </>
        }
        subtitle={t('agents.subtitle')}
        tabs={tabs}
        activeTab={tab}
        onTabChange={requestTabChange}
        newLabel={newLabel}
        newExpanded={newExpanded}
        onToggleNew={requestToggleNew}
        newPanel={newPanel}
        list={list}
        hasSelection={hasSelection}
        emptyTitle={t('desk.emptyTitle', { ns: 'common', defaultValue: 'Select an item' })}
        emptyBody={t('desk.emptyBody', {
          ns: 'common',
          defaultValue: 'Choose a row from the list to see details.',
        })}
        detail={detail}
        detailFooter={detailFooter}
        processSlot={processSlot}
        helpContext={helpContext}
        narrowSheetOpen={narrow && hasSelection}
        onCloseSheet={clearSelection}
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
