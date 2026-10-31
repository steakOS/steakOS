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
  FolderKanban,
  GitBranch,
  BadgeCheck,
  RefreshCw,
  BookOpen,
  Layers,
  Briefcase,
  Plus,
  Play,
  Check,
  X,
  Eye,
  Pencil,
} from 'lucide-react'
import { capabilityApi } from '../api/client'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'
import ProcessDiagram from '../components/ProcessDiagram'
import { hasMasterFormSpec } from '../forms/masterFormSpecs'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import { localizeProcess } from '../utils/localizeProcess'
import ResponsiveDataList from '../components/ResponsiveDataList'
import RowActionSlots from '../components/RowActionSlots'
import useDeskKeyboard from '../hooks/useDeskKeyboard'
import DeskShell from '../components/desk/DeskShell'
import DeskNewFab from '../components/DeskNewFab'
import ConfirmDialog from '../components/ConfirmDialog'
import useNarrowViewport from '../hooks/useNarrowViewport'
import useDeskMemory from '../hooks/useDeskMemory'
import { labelFromList } from '../utils/entityDisplayLabel'

const SECTION_DEFS = [
  { id: 'portfolios', labelKey: 'capability.tabs.portfolios', icon: Layers },
  { id: 'programs', labelKey: 'capability.tabs.programs', icon: Briefcase },
  { id: 'projects', labelKey: 'capability.tabs.projects', icon: FolderKanban },
  { id: 'processes', labelKey: 'capability.tabs.processes', icon: GitBranch },
  { id: 'quality', labelKey: 'capability.tabs.quality', icon: BadgeCheck },
  { id: 'changes', labelKey: 'capability.tabs.changes', icon: RefreshCw },
  { id: 'knowledge', labelKey: 'capability.tabs.knowledge', icon: BookOpen },
]

/**
 * Capability ops — DeskShell master–detail (ADR-047 / Nielsen WP-3).
 * Route sections: /capability/:section — portfolios · programs · projects · processes · quality · changes · knowledge.
 */
const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'
const ACTION_TH = (t) => t('wb.action', { ns: 'common', defaultValue: 'Action' })

export default function CapabilityHub() {
  const { t, i18n } = useTranslation(['desks', 'common', 'content', 'processes'])
  const loadAllRef = useRef(null)
  const { viewModal, openView, openEdit } = useRecordView({
    modalEdit: true,
    canModalEdit: ({ pkg, resource }) => hasMasterFormSpec(pkg, resource),
    onSaved: () => {
      loadAllRef.current?.().catch(() => {})
    },
  })
  const renderRowActions = useCallback(
    (pkg, resource, record, others = null) => {
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
          others={others}
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
  const [searchParams] = useSearchParams()
  const tab = SECTIONS.some((s) => s.id === section) ? section : 'projects'

  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)

  const [newExpanded, setNewExpanded] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [pendingNav, setPendingNav] = useState(null)
  const [companyId] = useState('default')

  const { saveMemory } = useDeskMemory({ deskKey: 'capability', companyId })

  const [portfolios, setPortfolios] = useState([])
  const [programs, setPrograms] = useState([])
  const [projects, setProjects] = useState([])
  const [processes, setProcesses] = useState([])
  const [procedures, setProcedures] = useState([])
  const [qualities, setQualities] = useState([])
  const [qualityItems, setQualityItems] = useState([])
  const [changes, setChanges] = useState([])
  const [knowledge, setKnowledge] = useState([])
  const [standards, setStandards] = useState([])
  /** Dropdown options (not paged) — max 100 until typeahead. */
  const [portfolioOptions, setPortfolioOptions] = useState([])
  const [programOptions, setProgramOptions] = useState([])
  const [processOptions, setProcessOptions] = useState([])
  const [selectedPortfolio, setSelectedPortfolio] = useState(null)
  const [selectedProgram, setSelectedProgram] = useState(null)
  const [selectedProject, setSelectedProject] = useState(null)
  const [projectContentMap, setProjectContentMap] = useState({})
  const [, setProcessContentMap] = useState({})
  const [, setChangeContentMap] = useState({})
  const [pendingProjectContent, setPendingProjectContent] = useState([])
  const [projectItems, setProjectItems] = useState([])
  const [selectedProcess, setSelectedProcess] = useState(null)
  const [processSteps, setProcessSteps] = useState([])
  const [selectedChange, setSelectedChange] = useState(null)
  const [changeItems, setChangeItems] = useState([])
  const [selectedQuality, setSelectedQuality] = useState(null)

  const [portPage, setPortPage] = useState({ max: 25, offset: 0, total: 0 })
  const [progPage, setProgPage] = useState({ max: 25, offset: 0, total: 0 })
  const [projPage, setProjPage] = useState({ max: 25, offset: 0, total: 0 })
  const [procPage, setProcPage] = useState({ max: 25, offset: 0, total: 0 })
  const [procedurePage, setProcedurePage] = useState({ max: 25, offset: 0, total: 0 })
  const [qualPage, setQualPage] = useState({ max: 25, offset: 0, total: 0 })
  const [chgPage, setChgPage] = useState({ max: 25, offset: 0, total: 0 })
  const [knowPage, setKnowPage] = useState({ max: 25, offset: 0, total: 0 })
  const [stdPage, setStdPage] = useState({ max: 25, offset: 0, total: 0 })
  const [portSort, setPortSort] = useState('id')
  const [portOrder, setPortOrder] = useState('desc')
  const [progSort, setProgSort] = useState('id')
  const [progOrder, setProgOrder] = useState('desc')
  const [projSort, setProjSort] = useState('id')
  const [projOrder, setProjOrder] = useState('desc')
  const [procSort, setProcSort] = useState('id')
  const [procOrder, setProcOrder] = useState('desc')
  const [qualSort, setQualSort] = useState('id')
  const [qualOrder, setQualOrder] = useState('desc')
  const [chgSort, setChgSort] = useState('id')
  const [chgOrder, setChgOrder] = useState('desc')
  const [knowSort, setKnowSort] = useState('name')
  const [knowOrder, setKnowOrder] = useState('asc')
  const [stdSort, setStdSort] = useState('id')
  const [stdOrder, setStdOrder] = useState('desc')

  const onPortSortChange = useCallback(({ sort: s, order: o }) => {
    setPortSort(s || 'id')
    setPortOrder(o === 'asc' ? 'asc' : 'desc')
    setPortPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onProgSortChange = useCallback(({ sort: s, order: o }) => {
    setProgSort(s || 'id')
    setProgOrder(o === 'asc' ? 'asc' : 'desc')
    setProgPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onProjSortChange = useCallback(({ sort: s, order: o }) => {
    setProjSort(s || 'id')
    setProjOrder(o === 'asc' ? 'asc' : 'desc')
    setProjPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onProcSortChange = useCallback(({ sort: s, order: o }) => {
    setProcSort(s || 'id')
    setProcOrder(o === 'asc' ? 'asc' : 'desc')
    setProcPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onQualSortChange = useCallback(({ sort: s, order: o }) => {
    setQualSort(s || 'id')
    setQualOrder(o === 'asc' ? 'asc' : 'desc')
    setQualPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onChgSortChange = useCallback(({ sort: s, order: o }) => {
    setChgSort(s || 'id')
    setChgOrder(o === 'asc' ? 'asc' : 'desc')
    setChgPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onKnowSortChange = useCallback(({ sort: s, order: o }) => {
    setKnowSort(s || 'name')
    setKnowOrder(o === 'asc' ? 'asc' : 'desc')
    setKnowPage((pg) => ({ ...pg, offset: 0 }))
  }, [])
  const onStdSortChange = useCallback(({ sort: s, order: o }) => {
    setStdSort(s || 'id')
    setStdOrder(o === 'asc' ? 'asc' : 'desc')
    setStdPage((pg) => ({ ...pg, offset: 0 }))
  }, [])

  const PORT_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'portfolioStatus', label: 'Status' },
    ],
    [],
  )
  const PROG_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'programStatus', label: 'Status' },
    ],
    [],
  )
  const PROJ_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'projectStatus', label: 'Status' },
    ],
    [],
  )
  const PROC_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'processCode', label: 'Code' },
      { value: 'processKind', label: 'Kind' },
      { value: 'processStatus', label: 'Status' },
    ],
    [],
  )
  const QUAL_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'qualityCode', label: 'Code' },
      { value: 'qualityStatus', label: 'Status' },
    ],
    [],
  )
  const CHG_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'changeStatus', label: 'Status' },
    ],
    [],
  )
  const KNOW_SORT_MENU = useMemo(
    () => [
      { value: 'name', label: 'Name' },
      { value: 'areaCode', label: 'Code' },
      { value: 'areaStatus', label: 'Status' },
      { value: 'id', label: 'Id' },
    ],
    [],
  )
  const STD_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'metricCode', label: 'Metric' },
      { value: 'standardStatus', label: 'Status' },
    ],
    [],
  )

  const [portForm, setPortForm] = useState({ name: '', ownerRef: '' })
  const [progForm, setProgForm] = useState({ name: '', portfolioId: '' })
  const [projForm, setProjForm] = useState({ name: '', portfolioId: '', programId: '' })
  const [itemForm, setItemForm] = useState({ name: '', taskId: '' })
  const [procForm, setProcForm] = useState({ name: '', processCode: '', processKind: 'HUMAN' })
  const [stepForm, setStepForm] = useState({ name: '', predecessorId: '' })
  const [procedureForm, setProcedureForm] = useState({ name: '', processId: '', versionLabel: '' })
  // A7 browser MVP — default to SYSTEM catalogue
  const [kindFilter, setKindFilter] = useState(() => {
    const k = searchParams.get('kind')
    if (k && ['ALL', 'SYSTEM', 'HUMAN', 'AGENTIC', 'OTHER'].includes(k)) return k
    return 'SYSTEM'
  })
  const [processMermaid, setProcessMermaid] = useState('')
  const deepCode = searchParams.get('code')
  const [qualForm, setQualForm] = useState({ name: '', qualityCode: '' })
  const [qItemForm, setQItemForm] = useState({ name: '', itemKind: 'REQ' })
  const [chgForm, setChgForm] = useState({ name: '', impactNote: '' })
  const [cItemForm, setCItemForm] = useState({ name: '' })
  const [knowForm, setKnowForm] = useState({ name: '', areaCode: '', parentId: '' })
  const [stdForm, setStdForm] = useState({ name: '', metricCode: '', targetValue: '', unit: '' })

  useEffect(() => {
    saveMemory({ tab })
  }, [tab, saveMemory])

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

  const statusClass = (s) => {
    if (['ACTIVE', 'DONE', 'CLOSED', 'OPEN'].includes(s)) return 'badge badge-ok'
    if (['BLOCKED', 'ERROR', 'FAILED', 'REJECTED'].includes(s)) return 'badge badge-danger'
    if (['RETIRED', 'CANCELLED', 'INACTIVE', 'ARCHIVED'].includes(s)) return 'badge badge-muted'
    if (['IN_PROGRESS', 'PLANNED', 'DRAFT'].includes(s)) return 'badge badge-warn'
    return 'badge badge-draft'
  }

  const loadSection = useCallback(async () => {
    setError(null)
    try {
      if (tab === 'portfolios') {
        const port = await capabilityApi.portfolios.list({
          max: portPage.max,
          offset: portPage.offset,
          sort: portSort,
          order: portOrder,
        })
        const items = port.items || []
        setPortfolios(items)
        setPortfolioOptions(items)
        setPortPage((pg) => ({
          ...pg,
          max: port.max ?? pg.max,
          offset: port.offset ?? pg.offset,
          total: port.total ?? items.length,
        }))
      } else if (tab === 'programs') {
        const [prog, portPick] = await Promise.all([
          capabilityApi.programs.list({
            max: progPage.max,
            offset: progPage.offset,
            sort: progSort,
            order: progOrder,
          }),
          capabilityApi.portfolios.list({ max: 100, offset: 0 }),
        ])
        const items = prog.items || []
        setPrograms(items)
        setPortfolioOptions(portPick.items || [])
        setProgPage((pg) => ({
          ...pg,
          max: prog.max ?? pg.max,
          offset: prog.offset ?? pg.offset,
          total: prog.total ?? items.length,
        }))
      } else if (tab === 'projects') {
        const [portPick, progPick, proj] = await Promise.all([
          capabilityApi.portfolios.list({ max: 100, offset: 0 }),
          capabilityApi.programs.list({ max: 100, offset: 0 }),
          capabilityApi.projects.list({
            max: projPage.max,
            offset: projPage.offset,
            sort: projSort,
            order: projOrder,
          }),
        ])
        const pj = proj.items || []
        setPortfolios(portPick.items || [])
        setPrograms(progPick.items || [])
        setPortfolioOptions(portPick.items || [])
        setProgramOptions(progPick.items || [])
        setProjects(pj)
        setProjPage((pg) => ({
          ...pg,
          max: proj.max ?? pg.max,
          offset: proj.offset ?? pg.offset,
          total: proj.total ?? pj.length,
        }))
        setProjectContentMap(await fetchContentMap('capability', 'Project', pj))
      } else if (tab === 'processes') {
        const processParams = {
          max: procPage.max,
          offset: procPage.offset,
          sort: procSort,
          order: procOrder,
          ...(kindFilter !== 'ALL' ? { kind: kindFilter } : {}),
        }
        const [procs, proceduresRes, procPick] = await Promise.all([
          capabilityApi.processes.list(processParams),
          capabilityApi.procedures.list({
            max: procedurePage.max,
            offset: procedurePage.offset,
          }),
          capabilityApi.processes.list({ max: 100, offset: 0 }),
        ])
        const pr = procs.items || []
        const proceduresItems = proceduresRes.items || []
        setProcesses(pr)
        setProcedures(proceduresItems)
        setProcessOptions(procPick.items || [])
        setProcPage((pg) => ({
          ...pg,
          max: procs.max ?? pg.max,
          offset: procs.offset ?? pg.offset,
          total: procs.total ?? pr.length,
        }))
        setProcedurePage((pg) => ({
          ...pg,
          max: proceduresRes.max ?? pg.max,
          offset: proceduresRes.offset ?? pg.offset,
          total: proceduresRes.total ?? proceduresItems.length,
        }))
        setProcessContentMap(await fetchContentMap('capability', 'Process', pr))
      } else if (tab === 'quality') {
        const q = await capabilityApi.qualities.list({
          max: qualPage.max,
          offset: qualPage.offset,
          sort: qualSort,
          order: qualOrder,
        })
        setQualities(q.items || [])
        setQualPage((pg) => ({
          ...pg,
          max: q.max ?? pg.max,
          offset: q.offset ?? pg.offset,
          total: q.total ?? (q.items || []).length,
        }))
      } else if (tab === 'changes') {
        const c = await capabilityApi.changes.list({
          max: chgPage.max,
          offset: chgPage.offset,
          sort: chgSort,
          order: chgOrder,
        })
        const ch = c.items || []
        setChanges(ch)
        setChgPage((pg) => ({
          ...pg,
          max: c.max ?? pg.max,
          offset: c.offset ?? pg.offset,
          total: c.total ?? ch.length,
        }))
        setChangeContentMap(await fetchContentMap('capability', 'Change', ch))
      } else if (tab === 'knowledge') {
        const [k, s] = await Promise.all([
          capabilityApi.knowledge.list({
            max: knowPage.max,
            offset: knowPage.offset,
            sort: knowSort,
            order: knowOrder,
          }),
          capabilityApi.standards.list({
            max: stdPage.max,
            offset: stdPage.offset,
            sort: stdSort,
            order: stdOrder,
          }),
        ])
        setKnowledge(k.items || [])
        setStandards(s.items || [])
        setKnowPage((pg) => ({
          ...pg,
          max: k.max ?? pg.max,
          offset: k.offset ?? pg.offset,
          total: k.total ?? (k.items || []).length,
        }))
        setStdPage((pg) => ({
          ...pg,
          max: s.max ?? pg.max,
          offset: s.offset ?? pg.offset,
          total: s.total ?? (s.items || []).length,
        }))
      }
    } catch (e) {
      setError(e.message || String(e))
    }
  }, [
    tab,
    kindFilter,
    portPage.max,
    portPage.offset,
    progPage.max,
    progPage.offset,
    projPage.max,
    projPage.offset,
    procPage.max,
    procPage.offset,
    procedurePage.max,
    procedurePage.offset,
    qualPage.max,
    qualPage.offset,
    chgPage.max,
    chgPage.offset,
    knowPage.max,
    knowPage.offset,
    stdPage.max,
    stdPage.offset,
    portSort,
    portOrder,
    progSort,
    progOrder,
    projSort,
    projOrder,
    procSort,
    procOrder,
    qualSort,
    qualOrder,
    chgSort,
    chgOrder,
    knowSort,
    knowOrder,
    stdSort,
    stdOrder,
  ])
  loadAllRef.current = loadSection

  useEffect(() => {
    setLoading(true)
    loadSection()
      .finally(() => setLoading(false))
  }, [loadSection])

  // A7 — deep link /capability/processes?code=FIN_INVOICE_SETTLE
  useEffect(() => {
    if (tab !== 'processes' || !deepCode) return
    let cancelled = false
    capabilityApi.processes
      .byCode(deepCode)
      .then((detail) => {
        if (cancelled || !detail) return
        setSelectedProcess(detail)
        setProcessSteps(detail.steps || [])
        setProcessMermaid(detail.mermaid || '')
        setKindFilter('SYSTEM')
      })
      .catch((e) => {
        if (!cancelled) setError(e.message || String(e))
      })
    return () => {
      cancelled = true
    }
  }, [tab, deepCode])

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

  const selectPortfolio = (p) => {
    setSelectedPortfolio(p)
    setSelectedProgram(null)
    setSelectedProject(null)
    setSelectedProcess(null)
    setSelectedQuality(null)
    setSelectedChange(null)
  }

  const selectProgram = (p) => {
    setSelectedProgram(p)
    setSelectedPortfolio(null)
    setSelectedProject(null)
    setSelectedProcess(null)
    setSelectedQuality(null)
    setSelectedChange(null)
  }

  const selectProject = async (p) => {
    setSelectedProject(p)
    setSelectedPortfolio(null)
    setSelectedProgram(null)
    setSelectedProcess(null)
    setSelectedQuality(null)
    setSelectedChange(null)
    try {
      const items = await capabilityApi.projects.items(p.id)
      setProjectItems(items.items || [])
    } catch (e) {
      setError(e.message)
    }
  }

  const selectProcess = async (p) => {
    setSelectedProcess(p)
    setSelectedPortfolio(null)
    setSelectedProgram(null)
    setSelectedProject(null)
    setSelectedQuality(null)
    setSelectedChange(null)
    setProcessMermaid('')
    try {
      const detail = await capabilityApi.processes.get(p.id)
      setSelectedProcess(detail)
      setProcessSteps(detail.steps || [])
      setProcessMermaid(detail.mermaid || '')
    } catch (e) {
      try {
        const steps = await capabilityApi.processes.steps(p.id)
        setProcessSteps(steps.items || [])
      } catch (e2) {
        setError(e2.message || e.message)
      }
    }
  }

  const kindBadge = (k) => {
    const kind = (k || 'HUMAN').toLowerCase()
    return `badge badge-kind-${kind}`
  }

  /** Server-filtered by kind; page is already scoped. */
  const visibleProcesses = processes

  const localizedSelectedProcess = useMemo(() => {
    if (!selectedProcess) return null
    return localizeProcess(
      { ...selectedProcess, steps: processSteps },
      (key, opts) => t(key, { ns: 'processes', ...opts }),
      selectedProcess.processCode,
    )
  }, [selectedProcess, processSteps, t, i18n.language])

  const processDisplayName = useCallback(
    (p) => {
      if (!p?.processCode) return p?.name || '—'
      return t(`catalogue.${p.processCode}.name`, {
        ns: 'processes',
        defaultValue: p.name || p.processCode,
      })
    },
    [t, i18n.language],
  )

  const selectChange = async (c) => {
    setSelectedChange(c)
    setSelectedPortfolio(null)
    setSelectedProgram(null)
    setSelectedProject(null)
    setSelectedProcess(null)
    setSelectedQuality(null)
    try {
      const items = await capabilityApi.changes.items(c.id)
      setChangeItems(items.items || [])
    } catch (e) {
      setError(e.message)
    }
  }

  const selectQuality = async (q) => {
    setSelectedQuality(q)
    setSelectedPortfolio(null)
    setSelectedProgram(null)
    setSelectedProject(null)
    setSelectedProcess(null)
    setSelectedChange(null)
    try {
      const items = await capabilityApi.qualities.items(q.id)
      setQualityItems(items.items || [])
    } catch (e) {
      setError(e.message)
    }
  }

  const clearSelection = useCallback(() => {
    setSelectedPortfolio(null)
    setSelectedProgram(null)
    setSelectedProject(null)
    setSelectedProcess(null)
    setSelectedQuality(null)
    setSelectedChange(null)
    setProjectItems([])
    setProcessSteps([])
    setProcessMermaid('')
    setQualityItems([])
    setChangeItems([])
  }, [])

  const resetCreateForms = useCallback(() => {
    setPortForm({ name: '', ownerRef: '' })
    setProgForm({ name: '', portfolioId: '' })
    setProjForm({ name: '', portfolioId: '', programId: '' })
    setPendingProjectContent([])
    setProcForm({ name: '', processCode: '', processKind: 'HUMAN' })
    setProcedureForm({ name: '', processId: '', versionLabel: '' })
    setQualForm({ name: '', qualityCode: '' })
    setChgForm({ name: '', impactNote: '' })
    setKnowForm({ name: '', areaCode: '', parentId: '' })
    setStdForm({ name: '', metricCode: '', targetValue: '', unit: '' })
  }, [])

  const createDirty = useMemo(() => {
    if (tab === 'portfolios') {
      return Boolean((portForm.name || '').trim() || (portForm.ownerRef || '').trim())
    }
    if (tab === 'programs') {
      return Boolean((progForm.name || '').trim() || progForm.portfolioId)
    }
    if (tab === 'projects') {
      return Boolean(
        (projForm.name || '').trim() ||
          projForm.portfolioId ||
          projForm.programId ||
          pendingProjectContent.length,
      )
    }
    if (tab === 'processes') {
      return Boolean(
        (procForm.name || '').trim() ||
          (procForm.processCode || '').trim() ||
          (procedureForm.name || '').trim() ||
          procedureForm.processId ||
          (procedureForm.versionLabel || '').trim(),
      )
    }
    if (tab === 'quality') {
      return Boolean((qualForm.name || '').trim() || (qualForm.qualityCode || '').trim())
    }
    if (tab === 'changes') {
      return Boolean((chgForm.name || '').trim() || (chgForm.impactNote || '').trim())
    }
    if (tab === 'knowledge') {
      return Boolean(
        (knowForm.name || '').trim() ||
          (knowForm.areaCode || '').trim() ||
          knowForm.parentId ||
          (stdForm.name || '').trim() ||
          (stdForm.metricCode || '').trim() ||
          (stdForm.targetValue || '').trim() ||
          (stdForm.unit || '').trim(),
      )
    }
    return false
  }, [tab, portForm, progForm, projForm, pendingProjectContent, procForm, procedureForm, qualForm, chgForm, knowForm, stdForm])

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
    if (tab === 'portfolios') return portfolios.map((p) => p.id)
    if (tab === 'programs') return programs.map((p) => p.id)
    if (tab === 'projects') return projects.map((p) => p.id)
    if (tab === 'processes') return processes.map((p) => p.id)
    if (tab === 'quality') return qualities.map((q) => q.id)
    if (tab === 'changes') return changes.map((c) => c.id)
    return []
  }, [tab, portfolios, programs, projects, processes, qualities, changes])

  const keyboardSelectedId = useMemo(() => {
    if (tab === 'portfolios') return selectedPortfolio?.id ?? null
    if (tab === 'programs') return selectedProgram?.id ?? null
    if (tab === 'projects') return selectedProject?.id ?? null
    if (tab === 'processes') return selectedProcess?.id ?? null
    if (tab === 'quality') return selectedQuality?.id ?? null
    if (tab === 'changes') return selectedChange?.id ?? null
    return null
  }, [
    tab,
    selectedPortfolio?.id,
    selectedProgram?.id,
    selectedProject?.id,
    selectedProcess?.id,
    selectedQuality?.id,
    selectedChange?.id,
  ])

  const setKeyboardSelectedId = useCallback(
    (id) => {
      if (id == null) {
        clearSelection()
        return
      }
      const sid = String(id)
      if (tab === 'portfolios') {
        const p = portfolios.find((x) => String(x.id) === sid)
        if (p) selectPortfolio(p)
      } else if (tab === 'programs') {
        const p = programs.find((x) => String(x.id) === sid)
        if (p) selectProgram(p)
      } else if (tab === 'projects') {
        const p = projects.find((x) => String(x.id) === sid)
        if (p) selectProject(p)
      } else if (tab === 'processes') {
        const p = processes.find((x) => String(x.id) === sid)
        if (p) selectProcess(p)
      } else if (tab === 'quality') {
        const q = qualities.find((x) => String(x.id) === sid)
        if (q) selectQuality(q)
      } else if (tab === 'changes') {
        const c = changes.find((x) => String(x.id) === sid)
        if (c) selectChange(c)
      }
    },
    [
      tab,
      portfolios,
      programs,
      projects,
      processes,
      qualities,
      changes,
      clearSelection,
      selectPortfolio,
      selectProgram,
      selectProject,
      selectProcess,
      selectQuality,
      selectChange,
    ],
  )

  useDeskKeyboard({
    enabled: !newExpanded && tab !== 'knowledge',
    itemIds: keyboardIds,
    selectedId: keyboardSelectedId,
    setSelectedId: setKeyboardSelectedId,
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
      setNewExpanded(false)
      resetCreateForms()
      clearSelection()
      navigate(`/capability/${nextTab}`)
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
      navigate(`/capability/${nav.tab}`)
    }
  }, [pendingNav, applyCollapseCreate, clearSelection, navigate])

  const cancelDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
  }, [])

  const sectionMeta = SECTIONS.find((s) => s.id === tab)
  const SectionIcon = sectionMeta?.icon || FolderKanban

  const newLabel = useMemo(() => {
    if (tab === 'portfolios') {
      return t('capability.newPortfolio', { defaultValue: 'New portfolio' })
    }
    if (tab === 'programs') return t('capability.newProgram')
    if (tab === 'projects') return t('capability.newProject')
    if (tab === 'processes') return t('capability.newProcess')
    if (tab === 'quality') {
      return t('capability.newQuality', { defaultValue: 'New quality register' })
    }
    if (tab === 'changes') {
      return t('capability.newChange', { defaultValue: 'New org change' })
    }
    if (tab === 'knowledge') {
      return t('capability.newKnowledge', { defaultValue: 'New knowledge / standard' })
    }
    return t('desk.new', { ns: 'common', defaultValue: 'New' })
  }, [tab, t])

  const hasSelection = useMemo(() => {
    if (tab === 'portfolios') return Boolean(selectedPortfolio?.id)
    if (tab === 'programs') return Boolean(selectedProgram?.id)
    if (tab === 'projects') return Boolean(selectedProject?.id)
    if (tab === 'processes') return Boolean(selectedProcess?.id)
    if (tab === 'quality') return Boolean(selectedQuality?.id)
    if (tab === 'changes') return Boolean(selectedChange?.id)
    return false
  }, [
    tab,
    selectedPortfolio?.id,
    selectedProgram?.id,
    selectedProject?.id,
    selectedProcess?.id,
    selectedQuality?.id,
    selectedChange?.id,
  ])

  const helpContext = useMemo(() => {
    const base = { path: location.pathname, desk: 'capability', tab }
    if (tab === 'portfolios' && selectedPortfolio) {
      return {
        ...base,
        resource: 'Portfolio',
        objectId: selectedPortfolio.id,
        status: selectedPortfolio.portfolioStatus,
      }
    }
    if (tab === 'programs' && selectedProgram) {
      return {
        ...base,
        resource: 'Program',
        objectId: selectedProgram.id,
        status: selectedProgram.programStatus,
      }
    }
    if (tab === 'projects' && selectedProject) {
      return {
        ...base,
        resource: 'Project',
        objectId: selectedProject.id,
        status: selectedProject.projectStatus,
      }
    }
    if (tab === 'processes' && selectedProcess) {
      return {
        ...base,
        resource: 'Process',
        objectId: selectedProcess.id,
        status: selectedProcess.processStatus,
        processCode: selectedProcess.processCode,
      }
    }
    if (tab === 'quality' && selectedQuality) {
      return {
        ...base,
        resource: 'Quality',
        objectId: selectedQuality.id,
        status: selectedQuality.qualityStatus,
      }
    }
    if (tab === 'changes' && selectedChange) {
      return {
        ...base,
        resource: 'Change',
        objectId: selectedChange.id,
        status: selectedChange.changeStatus,
      }
    }
    return base
  }, [
    location.pathname,
    tab,
    selectedPortfolio,
    selectedProgram,
    selectedProject,
    selectedProcess,
    selectedQuality,
    selectedChange,
  ])

  const emptyTitle = useMemo(() => {
    if (tab === 'knowledge') {
      return t('capability.knowledgeEmptyTitle', {
        defaultValue: 'Knowledge & standards',
      })
    }
    return t('desk.emptyTitle', { ns: 'common', defaultValue: 'Select an item' })
  }, [tab, t])

  const emptyBody = useMemo(() => {
    if (tab === 'knowledge') {
      return t('capability.knowledgeEmptyBody', {
        defaultValue: 'Create areas or standards with New, or browse the list.',
      })
    }
    return t('desk.emptyBody', {
      ns: 'common',
      defaultValue: 'Choose a row from the list to see details.',
    })
  }, [tab, t])

  // —— create panels ——
  const portfoliosCreatePanel = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('capability.newPortfolio', { defaultValue: 'New portfolio' })}
      </h2>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('port', async () => {
            const p = await capabilityApi.portfolios.create(portForm)
            setInfo(
              t('toast.namedCreated', {
                ns: 'common',
                what: t('labels.portfolio', { ns: 'common' }),
                name: p.name,
              }),
            )
            setPortForm({ name: '', ownerRef: '' })
            setNewExpanded(false)
          })
        }}
      >
        <label>
          Name *
          <input
            required
            value={portForm.name}
            onChange={(e) => setPortForm((f) => ({ ...f, name: e.target.value }))}
          />
        </label>
        <label>
          Owner ref
          <input
            value={portForm.ownerRef}
            onChange={(e) => setPortForm((f) => ({ ...f, ownerRef: e.target.value }))}
          />
        </label>
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('capability.createPortfolio')}
        </button>
      </form>
    </>
  )

  const programsCreatePanel = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('capability.newProgram')}
      </h2>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('prog', async () => {
            const body = {
              name: progForm.name,
              portfolioId: progForm.portfolioId || null,
            }
            const p = await capabilityApi.programs.create(body)
            setInfo(
              t('toast.namedCreated', {
                ns: 'common',
                what: t('labels.program', { ns: 'common' }),
                name: p.name,
              }),
            )
            setProgForm({ name: '', portfolioId: '' })
            setNewExpanded(false)
          })
        }}
      >
        <label>
          Name *
          <input
            required
            value={progForm.name}
            onChange={(e) => setProgForm((f) => ({ ...f, name: e.target.value }))}
          />
        </label>
        <label>
          Portfolio
          <select
            value={progForm.portfolioId}
            onChange={(e) => setProgForm((f) => ({ ...f, portfolioId: e.target.value }))}
          >
            <option value="">— optional —</option>
            {portfolioOptions.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('capability.createProgram')}
        </button>
      </form>
    </>
  )

  const projectsCreatePanel = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('capability.newProject')}
      </h2>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('proj', async () => {
            const body = {
              name: projForm.name,
              portfolioId: projForm.portfolioId || null,
              programId: projForm.programId || null,
            }
            const p = await capabilityApi.projects.create(body)
            if (pendingProjectContent.length) {
              await attachPendingContent(
                'capability',
                'Project',
                p.id,
                pendingProjectContent,
              )
              setPendingProjectContent([])
            }
            setInfo(t('toast.projectStandalone', { ns: 'common', name: p.name }))
            setProjForm({ name: '', portfolioId: '', programId: '' })
            setNewExpanded(false)
          })
        }}
      >
        <label>
          Name *
          <input
            required
            value={projForm.name}
            onChange={(e) => setProjForm((f) => ({ ...f, name: e.target.value }))}
          />
        </label>
        <div className="form-row-2">
          <label>
            Portfolio
            <select
              value={projForm.portfolioId}
              onChange={(e) => setProjForm((f) => ({ ...f, portfolioId: e.target.value }))}
            >
              <option value="">— optional —</option>
              {portfolioOptions.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Program
            <select
              value={projForm.programId}
              onChange={(e) => setProjForm((f) => ({ ...f, programId: e.target.value }))}
            >
              <option value="">— optional —</option>
              {programOptions.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <PendingContentComposer
          package="capability"
          resource="Project"
          pending={pendingProjectContent}
          onChange={setPendingProjectContent}
        />
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('capability.createProjectBtn')}
        </button>
      </form>
    </>
  )

  const processesCreatePanel = (
    <>
      <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
        {t('capability.systemSeededHint')}
      </p>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('proc', async () => {
            const p = await capabilityApi.processes.create({
              ...procForm,
              processKind: procForm.processKind === 'SYSTEM' ? 'HUMAN' : procForm.processKind,
            })
            setInfo(
              t('toast.namedCreated', {
                ns: 'common',
                what: t('labels.process', { ns: 'common' }),
                name: p.name,
              }),
            )
            setProcForm({ name: '', processCode: '', processKind: 'HUMAN' })
            setNewExpanded(false)
          })
        }}
      >
        <label>
          {t('wb.name', { ns: 'common' })} *
          <input
            required
            value={procForm.name}
            onChange={(e) => setProcForm((f) => ({ ...f, name: e.target.value }))}
          />
        </label>
        <label>
          {t('wb.code', { ns: 'common' })}
          <input
            value={procForm.processCode}
            onChange={(e) => setProcForm((f) => ({ ...f, processCode: e.target.value }))}
          />
        </label>
        <label>
          {t('wb.kind', { ns: 'common' })}
          <select
            value={procForm.processKind}
            onChange={(e) => setProcForm((f) => ({ ...f, processKind: e.target.value }))}
          >
            {['HUMAN', 'AGENTIC', 'OTHER'].map((k) => (
              <option key={k} value={k}>
                {k}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('capability.createProcess')}
        </button>
      </form>
      <button
        type="button"
        className="btn"
        style={{ marginTop: 8 }}
        disabled={!!busy}
        onClick={() =>
          run('reseed', async () => {
            const r = await capabilityApi.processes.reseed()
            setInfo(
              t('capability.reseeded', {
                created: r.created || 0,
                updated: r.updated || 0,
                steps: r.stepsUpserted || 0,
              }),
            )
          })
        }
      >
        {t('capability.reseedCatalogue')}
      </button>

      <h2 className="section-label">{t('capability.procedure')}</h2>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('procedure', async () => {
            await capabilityApi.procedures.create({
              name: procedureForm.name,
              processId: procedureForm.processId || null,
              versionLabel: procedureForm.versionLabel || null,
            })
            setInfo(t('capability.procedureCreated'))
            setProcedureForm({ name: '', processId: '', versionLabel: '' })
            setNewExpanded(false)
          })
        }}
      >
        <label>
          {t('wb.name', { ns: 'common' })} *
          <input
            required
            value={procedureForm.name}
            onChange={(e) => setProcedureForm((f) => ({ ...f, name: e.target.value }))}
          />
        </label>
        <label>
          {t('labels.process', { ns: 'common' })}
          <select
            value={procedureForm.processId}
            onChange={(e) => setProcedureForm((f) => ({ ...f, processId: e.target.value }))}
          >
            <option value="">{t('capability.processOptional')}</option>
            {processOptions.map((p) => (
              <option key={p.id} value={p.id}>
                {processDisplayName(p)}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('capability.createProcedure')}
        </button>
      </form>
    </>
  )

  const qualityCreatePanel = (
    <form
      className="form-grid"
      onSubmit={(e) => {
        e.preventDefault()
        run('qual', async () => {
          const q = await capabilityApi.qualities.create(qualForm)
          setInfo(
            t('toast.namedCreated', {
              ns: 'common',
              what: t('labels.quality', { ns: 'common' }),
              name: q.name,
            }),
          )
          setQualForm({ name: '', qualityCode: '' })
          setNewExpanded(false)
        })
      }}
    >
      <label>
        Name *
        <input
          required
          value={qualForm.name}
          onChange={(e) => setQualForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <label>
        Code
        <input
          value={qualForm.qualityCode}
          onChange={(e) => setQualForm((f) => ({ ...f, qualityCode: e.target.value }))}
        />
      </label>
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('actions.create', { ns: 'common' })}
      </button>
    </form>
  )

  const changesCreatePanel = (
    <>
      <p className="muted" style={{ marginTop: 0 }}>
        Organizational change only — IT/tech change lives under Digital.
      </p>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('chg', async () => {
            const c = await capabilityApi.changes.create(chgForm)
            setInfo(
              t('toast.namedCreated', {
                ns: 'common',
                what: t('labels.change', { ns: 'common' }),
                name: c.name,
              }),
            )
            setChgForm({ name: '', impactNote: '' })
            setNewExpanded(false)
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
          Impact note
          <textarea
            rows={2}
            value={chgForm.impactNote}
            onChange={(e) => setChgForm((f) => ({ ...f, impactNote: e.target.value }))}
          />
        </label>
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('capability.createChange')}
        </button>
      </form>
    </>
  )

  const knowledgeCreatePanel = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Knowledge area
      </h2>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('know', async () => {
            await capabilityApi.knowledge.create({
              name: knowForm.name,
              areaCode: knowForm.areaCode || null,
              parentId: knowForm.parentId || null,
            })
            setInfo(t('capability.knowledgeAreaCreated'))
            setKnowForm({ name: '', areaCode: '', parentId: '' })
            setNewExpanded(false)
          })
        }}
      >
        <label>
          Name *
          <input
            required
            value={knowForm.name}
            onChange={(e) => setKnowForm((f) => ({ ...f, name: e.target.value }))}
          />
        </label>
        <div className="form-row-2">
          <label>
            Code
            <input
              value={knowForm.areaCode}
              onChange={(e) => setKnowForm((f) => ({ ...f, areaCode: e.target.value }))}
            />
          </label>
          <label>
            Parent
            <select
              value={knowForm.parentId}
              onChange={(e) => setKnowForm((f) => ({ ...f, parentId: e.target.value }))}
            >
              <option value="">— root —</option>
              {knowledge.map((k) => (
                <option key={k.id} value={k.id}>
                  {k.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('capability.createArea')}
        </button>
      </form>

      <h2 className="section-label">{t('capability.standardKpi')}</h2>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run('std', async () => {
            await capabilityApi.standards.create(stdForm)
            setInfo(t('capability.standardCreated'))
            setStdForm({ name: '', metricCode: '', targetValue: '', unit: '' })
            setNewExpanded(false)
          })
        }}
      >
        <label>
          Name *
          <input
            required
            value={stdForm.name}
            onChange={(e) => setStdForm((f) => ({ ...f, name: e.target.value }))}
          />
        </label>
        <div className="form-row-2">
          <label>
            Metric code
            <input
              value={stdForm.metricCode}
              onChange={(e) => setStdForm((f) => ({ ...f, metricCode: e.target.value }))}
            />
          </label>
          <label>
            Target
            <input
              value={stdForm.targetValue}
              onChange={(e) => setStdForm((f) => ({ ...f, targetValue: e.target.value }))}
            />
          </label>
        </div>
        <label>
          Unit
          <input
            value={stdForm.unit}
            onChange={(e) => setStdForm((f) => ({ ...f, unit: e.target.value }))}
          />
        </label>
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('capability.createStandard')}
        </button>
      </form>
    </>
  )

  const newPanel =
    tab === 'portfolios'
      ? portfoliosCreatePanel
      : tab === 'programs'
        ? programsCreatePanel
        : tab === 'projects'
          ? projectsCreatePanel
          : tab === 'processes'
            ? processesCreatePanel
            : tab === 'quality'
              ? qualityCreatePanel
              : tab === 'changes'
                ? changesCreatePanel
                : tab === 'knowledge'
                  ? knowledgeCreatePanel
                  : null

  // —— lists (col1) ——
  const portfoliosList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('capability.portfoliosCount', {
          defaultValue: 'Portfolios ({{count}})',
          count: portPage.total || portfolios.length,
        })}
      </h2>
      <ResponsiveDataList
        items={portfolios}
        empty={t('capability.emptyPortfolios', { defaultValue: 'No portfolios yet' })}
        selectedKey={selectedPortfolio?.id}
        onSelect={(p) => selectPortfolio(p)}
        sortFields={PORT_SORT_MENU}
        sort={portSort}
        order={portOrder}
        onSortChange={onPortSortChange}
        fields={[
          { id: 'name', primary: true, render: (p) => p.name },
          {
            id: 'owner',
            secondary: true,
            render: (p) => p.ownerRef || '—',
          },
          {
            id: 'status',
            meta: true,
            render: (p) => (
              <span className={statusClass(p.portfolioStatus)}>{p.portfolioStatus}</span>
            ),
          },
        ]}
        actions={(p) => (
          <>
            {p.portfolioStatus === 'ACTIVE' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('portClose', async () => {
                    await capabilityApi.portfolios.close(p.id)
                    setInfo(
                      t('toast.closed', {
                        ns: 'common',
                        defaultValue: 'Closed {{name}}',
                        name: p.name,
                      }),
                    )
                    if (selectedPortfolio?.id === p.id) setSelectedPortfolio(null)
                  })
                }
              >
                Close
              </button>
            )}
            {renderRowActions('capability', 'Portfolio', p)}
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
                  sort={portSort}
                  order={portOrder}
                  onSortChange={onPortSortChange}
                />
                <th className="col-priority-low">Owner</th>
                <ListSortHeader
                  field="portfolioStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={portSort}
                  order={portOrder}
                  onSortChange={onPortSortChange}
                />
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {portfolios.map((p) => (
                <tr
                  key={p.id}
                  style={{
                    cursor: 'pointer',
                    background:
                      selectedPortfolio?.id === p.id
                        ? 'var(--surface-2, #f0f0f0)'
                        : undefined,
                  }}
                  onClick={() => selectPortfolio(p)}
                >
                  <td>{p.name}</td>
                  <td className="col-priority-low">{p.ownerRef || '—'}</td>
                  <td>
                    <span className={statusClass(p.portfolioStatus)}>{p.portfolioStatus}</span>
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                    {p.portfolioStatus === 'ACTIVE' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('portClose', async () => {
                            await capabilityApi.portfolios.close(p.id)
                            setInfo(
                              t('toast.closed', {
                                ns: 'common',
                                defaultValue: 'Closed {{name}}',
                                name: p.name,
                              }),
                            )
                            if (selectedPortfolio?.id === p.id) setSelectedPortfolio(null)
                          })
                        }
                      >
                        Close
                      </button>
                    )}
                    {renderRowActions('capability', 'Portfolio', p)}
                  </td>
                </tr>
              ))}
              {!portfolios.length && (
                <tr>
                  <td colSpan={4} className="muted">
                    {t('capability.emptyPortfolios', { defaultValue: 'No portfolios yet' })}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={portPage.total}
        max={portPage.max}
        offset={portPage.offset}
        disabled={loading}
        onOffset={(o) => setPortPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setPortPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const programsList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('capability.programsCount', {
          defaultValue: 'Programs ({{count}})',
          count: progPage.total || programs.length,
        })}
      </h2>
      <ResponsiveDataList
        items={programs}
        empty={t('capability.emptyPrograms', { defaultValue: 'No programs yet' })}
        selectedKey={selectedProgram?.id}
        onSelect={(p) => selectProgram(p)}
        sortFields={PROG_SORT_MENU}
        sort={progSort}
        order={progOrder}
        onSortChange={onProgSortChange}
        fields={[
          { id: 'name', primary: true, render: (p) => p.name },
          {
            id: 'portfolio',
            secondary: true,
            render: (p) => labelFromList(portfolioOptions, p.portfolioId),
          },
          {
            id: 'status',
            meta: true,
            render: (p) => (
              <span className={statusClass(p.programStatus)}>{p.programStatus}</span>
            ),
          },
        ]}
        actions={(p) => renderRowActions('capability', 'Program', p)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={progSort}
                  order={progOrder}
                  onSortChange={onProgSortChange}
                />
                <th className="col-priority-low">
                  {t('labels.portfolio', { ns: 'common' })}
                </th>
                <ListSortHeader
                  field="programStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={progSort}
                  order={progOrder}
                  onSortChange={onProgSortChange}
                />
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {programs.map((p) => (
                  <tr
                    key={p.id}
                    style={{
                      cursor: 'pointer',
                      background:
                        selectedProgram?.id === p.id
                          ? 'var(--surface-2, #f0f0f0)'
                          : undefined,
                    }}
                    onClick={() => selectProgram(p)}
                  >
                    <td>{p.name}</td>
                    <td className="col-priority-low">
                      {labelFromList(portfolioOptions, p.portfolioId)}
                    </td>
                    <td>
                      <span className={statusClass(p.programStatus)}>{p.programStatus}</span>
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                      {renderRowActions('capability', 'Program', p)}
                    </td>
                  </tr>
              ))}
              {!programs.length && (
                <tr>
                  <td colSpan={4} className="muted">
                    {t('capability.emptyPrograms', { defaultValue: 'No programs yet' })}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={progPage.total}
        max={progPage.max}
        offset={progPage.offset}
        disabled={loading}
        onOffset={(o) => setProgPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setProgPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const projectsList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Projects ({projPage.total || projects.length})
      </h2>
      <ResponsiveDataList
        items={projects}
        empty="No projects yet"
        selectedKey={selectedProject?.id}
        onSelect={(p) => selectProject(p)}
        sortFields={PROJ_SORT_MENU}
        sort={projSort}
        order={projOrder}
        onSortChange={onProjSortChange}
        fields={[
          { id: 'name', primary: true, render: (p) => p.name },
          {
            id: 'status',
            secondary: true,
            render: (p) => (
              <span className={statusClass(p.projectStatus)}>{p.projectStatus}</span>
            ),
          },
        ]}
        actions={(p) => (
          <>
            {p.projectStatus === 'DRAFT' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('start', async () => {
                    await capabilityApi.projects.start(p.id)
                    setInfo(t('toast.started', { ns: 'common', name: p.name }))
                  })
                }
              >
                <Play size={14} /> Start
              </button>
            )}
            {p.projectStatus === 'ACTIVE' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('done', async () => {
                    await capabilityApi.projects.complete(p.id)
                    setInfo(t('toast.completed', { ns: 'common', name: p.name }))
                  })
                }
              >
                <Check size={14} /> Done
              </button>
            )}
            {['DRAFT', 'ACTIVE'].includes(p.projectStatus) && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('cancel', async () => {
                    await capabilityApi.projects.cancel(p.id)
                    setInfo(t('toast.cancelled', { ns: 'common', number: p.name }))
                  })
                }
              >
                <X size={14} />
              </button>
            )}
            {renderRowActions('capability', 'Project', p)}
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
                  sort={projSort}
                  order={projOrder}
                  onSortChange={onProjSortChange}
                />
                <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                <ListSortHeader
                  field="projectStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={projSort}
                  order={projOrder}
                  onSortChange={onProjSortChange}
                />
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {projects.map((p) => (
                <tr
                  key={p.id}
                  data-selected={selectedProject?.id === p.id ? 'true' : undefined}
                  style={{
                    cursor: 'pointer',
                    background:
                      selectedProject?.id === p.id ? 'var(--surface-2, #f0f0f0)' : undefined,
                  }}
                  onClick={() => selectProject(p)}
                >
                  <td>{p.name}</td>
                  <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                    <ContentChannelIcons summary={projectContentMap[p.id]} />
                  </td>
                  <td>
                    <span className={statusClass(p.projectStatus)}>{p.projectStatus}</span>
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                    {p.projectStatus === 'DRAFT' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('start', async () => {
                            await capabilityApi.projects.start(p.id)
                            setInfo(t('toast.started', { ns: 'common', name: p.name }))
                          })
                        }
                      >
                        <Play size={14} /> Start
                      </button>
                    )}
                    {p.projectStatus === 'ACTIVE' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('done', async () => {
                            await capabilityApi.projects.complete(p.id)
                            setInfo(t('toast.completed', { ns: 'common', name: p.name }))
                          })
                        }
                      >
                        <Check size={14} /> Done
                      </button>
                    )}
                    {['DRAFT', 'ACTIVE'].includes(p.projectStatus) && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('cancel', async () => {
                            await capabilityApi.projects.cancel(p.id)
                            setInfo(t('toast.cancelled', { ns: 'common', number: p.name }))
                          })
                        }
                      >
                        <X size={14} />
                      </button>
                    )}
                    {renderRowActions('capability', 'Project', p)}
                  </td>
                </tr>
              ))}
              {!projects.length && (
                <tr>
                  <td colSpan={4} className="muted">
                    No projects yet
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={projPage.total}
        max={projPage.max}
        offset={projPage.offset}
        disabled={loading}
        onOffset={(o) => setProjPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setProjPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
      <p className="muted" style={{ marginTop: 12 }}>
        <Link to="/capability/portfolios">{t('capability.tabs.portfolios', { defaultValue: 'Portfolios' })}</Link>
        {' · '}
        <Link to="/capability/programs">{t('capability.tabs.programs', { defaultValue: 'Programs' })}</Link>
      </p>
    </>
  )

  const processesList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {t('capability.processesCount', {
          count: procPage.total || visibleProcesses.length,
        })}
      </h2>
      <div className="banner-info" role="status" style={{ marginBottom: 8 }}>
        <strong>{t('capability.processBrowserTitle')}</strong>
        <p style={{ margin: '0.25rem 0 0' }}>{t('capability.processBrowserHint')}</p>
      </div>
      <ResponsiveDataList
        items={visibleProcesses}
        empty={t('capability.emptyProcesses')}
        selectedKey={selectedProcess?.id}
        onSelect={(p) => selectProcess(p)}
        sortFields={PROC_SORT_MENU}
        sort={procSort}
        order={procOrder}
        onSortChange={onProcSortChange}
        fields={[
          { id: 'name', primary: true, render: (p) => processDisplayName(p) },
          {
            id: 'kind',
            secondary: true,
            render: (p) => (
              <span className={kindBadge(p.processKind)}>{p.processKind || 'HUMAN'}</span>
            ),
          },
          {
            id: 'status',
            meta: true,
            render: (p) => (
              <span className={statusClass(p.processStatus)}>{p.processStatus}</span>
            ),
          },
          {
            id: 'code',
            priority: 'low',
            render: (p) => p.processCode || '—',
          },
        ]}
        actions={(p) => (
          <>
            {!p.systemLocked && p.processStatus === 'DRAFT' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('act', async () => {
                    await capabilityApi.processes.activate(p.id)
                    setInfo(
                      t('toast.activated', {
                        ns: 'common',
                        name: processDisplayName(p),
                      }),
                    )
                  })
                }
              >
                {t('capability.activate')}
              </button>
            )}
            {p.processStatus === 'ACTIVE' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('ret', async () => {
                    await capabilityApi.processes.retire(p.id)
                    setInfo(
                      t('toast.retired', {
                        ns: 'common',
                        name: processDisplayName(p),
                      }),
                    )
                  })
                }
              >
                {t('capability.retire')}
              </button>
            )}
            {renderRowActions('capability', 'Process', p)}
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
                  sort={procSort}
                  order={procOrder}
                  onSortChange={onProcSortChange}
                />
                <ListSortHeader
                  className="col-priority-low"
                  field="processCode"
                  label={t('wb.code', { ns: 'common' })}
                  sort={procSort}
                  order={procOrder}
                  onSortChange={onProcSortChange}
                />
                <ListSortHeader
                  field="processKind"
                  label={t('wb.kind', { ns: 'common' })}
                  sort={procSort}
                  order={procOrder}
                  onSortChange={onProcSortChange}
                />
                <ListSortHeader
                  field="processStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={procSort}
                  order={procOrder}
                  onSortChange={onProcSortChange}
                />
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {visibleProcesses.map((p) => (
                <tr
                  key={p.id}
                  data-selected={selectedProcess?.id === p.id ? 'true' : undefined}
                  style={{
                    cursor: 'pointer',
                    background:
                      selectedProcess?.id === p.id ? 'var(--surface-2, #f0f0f0)' : undefined,
                  }}
                  onClick={() => selectProcess(p)}
                >
                  <td>{processDisplayName(p)}</td>
                  <td className="mono col-priority-low">{p.processCode || '—'}</td>
                  <td>
                    <span className={kindBadge(p.processKind)}>{p.processKind || 'HUMAN'}</span>
                  </td>
                  <td>
                    <span className={statusClass(p.processStatus)}>{p.processStatus}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {!p.systemLocked && p.processStatus === 'DRAFT' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('act', async () => {
                            await capabilityApi.processes.activate(p.id)
                            setInfo(
                              t('toast.activated', {
                                ns: 'common',
                                name: processDisplayName(p),
                              }),
                            )
                          })
                        }
                      >
                        {t('capability.activate')}
                      </button>
                    )}
                    {p.processStatus === 'ACTIVE' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('ret', async () => {
                            await capabilityApi.processes.retire(p.id)
                            setInfo(
                              t('toast.retired', {
                                ns: 'common',
                                name: processDisplayName(p),
                              }),
                            )
                          })
                        }
                      >
                        {t('capability.retire')}
                      </button>
                    )}
                    {renderRowActions('capability', 'Process', p)}
                  </td>
                </tr>
              ))}
              {!visibleProcesses.length && (
                <tr>
                  <td colSpan={5} className="muted">
                    {t('capability.emptyProcesses')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={procPage.total}
        max={procPage.max}
        offset={procPage.offset}
        disabled={loading}
        onOffset={(o) => setProcPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setProcPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
      <h2 className="section-label">
        {t('capability.proceduresCount', {
          count: procedurePage.total || procedures.length,
        })}
      </h2>
      <ul className="muted">
        {procedures.map((p) => (
          <li key={p.id}>
            {p.name}
            {p.versionLabel ? ` · ${p.versionLabel}` : ''}
          </li>
        ))}
      </ul>
      <ListPager
        total={procedurePage.total}
        max={procedurePage.max}
        offset={procedurePage.offset}
        disabled={loading}
        onOffset={(o) => setProcedurePage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setProcedurePage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const qualityList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Quality ({qualPage.total || qualities.length})
      </h2>
      <ResponsiveDataList
        items={qualities}
        empty="—"
        selectedKey={selectedQuality?.id}
        onSelect={(q) => selectQuality(q)}
        sortFields={QUAL_SORT_MENU}
        sort={qualSort}
        order={qualOrder}
        onSortChange={onQualSortChange}
        fields={[
          { id: 'name', primary: true, render: (q) => q.name },
          {
            id: 'code',
            secondary: true,
            render: (q) => <span className="mono">{q.qualityCode || '—'}</span>,
          },
          {
            id: 'status',
            meta: true,
            render: (q) => (
              <span className={statusClass(q.qualityStatus)}>{q.qualityStatus}</span>
            ),
          },
        ]}
        actions={(q) => (
          <>
            {q.qualityStatus === 'ACTIVE' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('arch', async () => {
                    await capabilityApi.qualities.archive(q.id)
                    setInfo(t('toast.archivedName', { ns: 'common', name: q.name }))
                  })
                }
              >
                Archive
              </button>
            )}
            {renderRowActions('capability', 'Quality', q)}
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
                  sort={qualSort}
                  order={qualOrder}
                  onSortChange={onQualSortChange}
                />
                <ListSortHeader
                  className="col-priority-low"
                  field="qualityCode"
                  label={t('wb.code', { ns: 'common' })}
                  sort={qualSort}
                  order={qualOrder}
                  onSortChange={onQualSortChange}
                />
                <ListSortHeader
                  field="qualityStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={qualSort}
                  order={qualOrder}
                  onSortChange={onQualSortChange}
                />
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {qualities.map((q) => (
                <tr
                  key={q.id}
                  data-selected={selectedQuality?.id === q.id ? 'true' : undefined}
                  style={{
                    cursor: 'pointer',
                    background:
                      selectedQuality?.id === q.id ? 'var(--surface-2, #f0f0f0)' : undefined,
                  }}
                  onClick={() => selectQuality(q)}
                >
                  <td>{q.name}</td>
                  <td className="mono col-priority-low">{q.qualityCode || '—'}</td>
                  <td>
                    <span className={statusClass(q.qualityStatus)}>{q.qualityStatus}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {q.qualityStatus === 'ACTIVE' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('arch', async () => {
                            await capabilityApi.qualities.archive(q.id)
                            setInfo(t('toast.archivedName', { ns: 'common', name: q.name }))
                          })
                        }
                      >
                        Archive
                      </button>
                    )}
                    {renderRowActions('capability', 'Quality', q)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={qualPage.total}
        max={qualPage.max}
        offset={qualPage.offset}
        disabled={loading}
        onOffset={(o) => setQualPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setQualPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const changesList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Org changes ({chgPage.total || changes.length})
      </h2>
      <ResponsiveDataList
        items={changes}
        empty="—"
        selectedKey={selectedChange?.id}
        onSelect={(c) => selectChange(c)}
        sortFields={CHG_SORT_MENU}
        sort={chgSort}
        order={chgOrder}
        onSortChange={onChgSortChange}
        fields={[
          { id: 'name', primary: true, render: (c) => c.name },
          {
            id: 'status',
            secondary: true,
            render: (c) => (
              <span className={statusClass(c.changeStatus)}>{c.changeStatus}</span>
            ),
          },
        ]}
        actions={(c) => (
          <>
            {c.changeStatus === 'PLANNED' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('impl', async () => {
                    await capabilityApi.changes.implement(c.id)
                    setInfo(t('toast.implementing', { ns: 'common', name: c.name }))
                  })
                }
              >
                <Play size={14} /> Implement
              </button>
            )}
            {c.changeStatus === 'IN_PROGRESS' && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('cclose', async () => {
                    await capabilityApi.changes.close(c.id)
                    setInfo(t('toast.closedName', { ns: 'common', name: c.name }))
                  })
                }
              >
                <Check size={14} /> Close
              </button>
            )}
            {['PLANNED', 'IN_PROGRESS'].includes(c.changeStatus) && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={!!busy}
                onClick={() =>
                  run('ccancel', async () => {
                    await capabilityApi.changes.cancel(c.id)
                    setInfo(t('toast.cancelled', { ns: 'common', number: c.name }))
                  })
                }
              >
                <X size={14} />
              </button>
            )}
            {renderRowActions('capability', 'Change', c)}
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
                  sort={chgSort}
                  order={chgOrder}
                  onSortChange={onChgSortChange}
                />
                <ListSortHeader
                  field="changeStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={chgSort}
                  order={chgOrder}
                  onSortChange={onChgSortChange}
                />
                <th>{ACTION_TH(t)}</th>
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {changes.map((c) => (
                <tr
                  key={c.id}
                  data-selected={selectedChange?.id === c.id ? 'true' : undefined}
                  style={{
                    cursor: 'pointer',
                    background:
                      selectedChange?.id === c.id ? 'var(--surface-2, #f0f0f0)' : undefined,
                  }}
                  onClick={() => selectChange(c)}
                >
                  <td>{c.name}</td>
                  <td>
                    <span className={statusClass(c.changeStatus)}>{c.changeStatus}</span>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {c.changeStatus === 'PLANNED' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('impl', async () => {
                            await capabilityApi.changes.implement(c.id)
                            setInfo(t('toast.implementing', { ns: 'common', name: c.name }))
                          })
                        }
                      >
                        <Play size={14} /> Implement
                      </button>
                    )}
                    {c.changeStatus === 'IN_PROGRESS' && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('cclose', async () => {
                            await capabilityApi.changes.close(c.id)
                            setInfo(t('toast.closedName', { ns: 'common', name: c.name }))
                          })
                        }
                      >
                        <Check size={14} /> Close
                      </button>
                    )}
                    {['PLANNED', 'IN_PROGRESS'].includes(c.changeStatus) && (
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('ccancel', async () => {
                            await capabilityApi.changes.cancel(c.id)
                            setInfo(t('toast.cancelled', { ns: 'common', number: c.name }))
                          })
                        }
                      >
                        <X size={14} />
                      </button>
                    )}
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                    {renderRowActions('capability', 'Change', c)}
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

  const knowledgeList = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        Knowledge areas ({knowPage.total || knowledge.length})
      </h2>
      <ResponsiveDataList
        items={knowledge}
        sortFields={KNOW_SORT_MENU}
        sort={knowSort}
        order={knowOrder}
        onSortChange={onKnowSortChange}
        defaultSort="name"
        defaultOrder="asc"
        empty="—"
        fields={[
          { id: 'name', primary: true, render: (k) => k.name },
          {
            id: 'code',
            secondary: true,
            render: (k) => <span className="mono">{k.areaCode || '—'}</span>,
          },
          {
            id: 'status',
            meta: true,
            render: (k) => <span className={statusClass(k.areaStatus)}>{k.areaStatus}</span>,
          },
          {
            id: 'parent',
            priority: 'low',
            render: (k) => k.parentId || '—',
          },
        ]}
        actions={(k) => renderRowActions('capability', 'KnowledgeArea', k)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={knowSort}
                  order={knowOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onKnowSortChange}
                />
                <ListSortHeader
                  className="col-priority-low"
                  field="areaCode"
                  label={t('wb.code', { ns: 'common' })}
                  sort={knowSort}
                  order={knowOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onKnowSortChange}
                />
                <th className="col-priority-low">{t('capability.parent')}</th>
                <ListSortHeader
                  field="areaStatus"
                  label={t('wb.status', { ns: 'common' })}
                  sort={knowSort}
                  order={knowOrder}
                  defaultSort="name"
                  defaultOrder="asc"
                  onSortChange={onKnowSortChange}
                />
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {knowledge.map((k) => (
                <tr key={k.id}>
                  <td>{k.name}</td>
                  <td className="mono col-priority-low">{k.areaCode || '—'}</td>
                  <td className="col-priority-low">{k.parentId || '—'}</td>
                  <td>
                    <span className={statusClass(k.areaStatus)}>{k.areaStatus}</span>
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {renderRowActions('capability', 'KnowledgeArea', k)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={knowPage.total}
        max={knowPage.max}
        offset={knowPage.offset}
        disabled={loading}
        onOffset={(o) => setKnowPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setKnowPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />

      <h2 className="section-label">Standards ({stdPage.total || standards.length})</h2>
      <ResponsiveDataList
        items={standards}
        sortFields={STD_SORT_MENU}
        sort={stdSort}
        order={stdOrder}
        onSortChange={onStdSortChange}
        empty="—"
        fields={[
          { id: 'name', primary: true, render: (s) => s.name },
          {
            id: 'metric',
            secondary: true,
            render: (s) => <span className="mono">{s.metricCode || '—'}</span>,
          },
          {
            id: 'target',
            meta: true,
            render: (s) => s.targetValue || '—',
          },
          {
            id: 'unit',
            priority: 'low',
            render: (s) => s.unit || '—',
          },
        ]}
        actions={(s) => renderRowActions('capability', 'Standard', s)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('wb.name', { ns: 'common' })}
                  sort={stdSort}
                  order={stdOrder}
                  onSortChange={onStdSortChange}
                />
                <ListSortHeader
                  field="metricCode"
                  label={t('capability.metric')}
                  sort={stdSort}
                  order={stdOrder}
                  onSortChange={onStdSortChange}
                />
                <th>{t('capability.target')}</th>
                <th className="col-priority-low">{t('wb.unit', { ns: 'common' })}</th>
                <th>{ACTION_TH(t)}</th>
              </tr>
            </thead>
            <tbody>
              {standards.map((s) => (
                <tr key={s.id}>
                  <td>{s.name}</td>
                  <td className="mono">{s.metricCode || '—'}</td>
                  <td>{s.targetValue || '—'}</td>
                  <td className="col-priority-low">{s.unit || '—'}</td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {renderRowActions('capability', 'Standard', s)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
      <ListPager
        total={stdPage.total}
        max={stdPage.max}
        offset={stdPage.offset}
        disabled={loading}
        onOffset={(o) => setStdPage((pg) => ({ ...pg, offset: o }))}
        onMax={(m) => setStdPage((pg) => ({ ...pg, max: m, offset: 0 }))}
      />
    </>
  )

  const list =
    tab === 'portfolios'
      ? portfoliosList
      : tab === 'programs'
        ? programsList
        : tab === 'projects'
          ? projectsList
          : tab === 'processes'
            ? processesList
            : tab === 'quality'
              ? qualityList
              : tab === 'changes'
                ? changesList
                : tab === 'knowledge'
                  ? knowledgeList
                  : null

  const processesToolbar = (
    <div className="toolbar" style={{ flexWrap: 'wrap', gap: 6 }}>
      {['ALL', 'SYSTEM', 'HUMAN', 'AGENTIC', 'OTHER'].map((k) => (
        <button
          key={k}
          type="button"
          className={kindFilter === k ? 'btn btn-primary' : 'btn'}
          onClick={() => {
            setKindFilter(k)
            setProcPage((pg) => ({ ...pg, offset: 0 }))
          }}
        >
          {k}
        </button>
      ))}
    </div>
  )

  // —— detail (col2) ——
  let detail = null
  if (tab === 'portfolios' && selectedPortfolio) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {selectedPortfolio.name}
        </h2>
        <p className="muted" style={{ marginTop: 0 }}>
          {t('labels.portfolio', { ns: 'common' })} ·{' '}
          <span className={statusClass(selectedPortfolio.portfolioStatus)}>
            {selectedPortfolio.portfolioStatus}
          </span>
          {selectedPortfolio.ownerRef ? ` · ${selectedPortfolio.ownerRef}` : ''}
        </p>
        {selectedPortfolio.description ? (
          <p style={{ fontSize: '0.9rem' }}>{selectedPortfolio.description}</p>
        ) : null}
        {selectedPortfolio.portfolioStatus === 'ACTIVE' && (
          <button
            type="button"
            className="btn"
            disabled={!!busy}
            onClick={() =>
              run('portClose', async () => {
                await capabilityApi.portfolios.close(selectedPortfolio.id)
                setInfo(
                  t('toast.closed', {
                    ns: 'common',
                    defaultValue: 'Closed {{name}}',
                    name: selectedPortfolio.name,
                  }),
                )
                setSelectedPortfolio(null)
              })
            }
          >
            Close portfolio
          </button>
        )}
        <p className="muted" style={{ marginTop: 12 }}>
          <Link to="/capability/programs">{t('capability.tabs.programs', { defaultValue: 'Programs' })}</Link>
          {' · '}
          <Link to="/capability/projects">{t('capability.tabs.projects')}</Link>
        </p>
      </div>
    )
  } else if (tab === 'programs' && selectedProgram) {
    const port = portfolioOptions.find(
      (x) => String(x.id) === String(selectedProgram.portfolioId),
    )
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {selectedProgram.name}
        </h2>
        <p className="muted" style={{ marginTop: 0 }}>
          {t('labels.program', { ns: 'common' })} ·{' '}
          <span className={statusClass(selectedProgram.programStatus)}>
            {selectedProgram.programStatus}
          </span>
        </p>
        <p style={{ fontSize: '0.9rem' }}>
          {t('labels.portfolio', { ns: 'common' })}:{' '}
          {port ? (
            <Link to="/capability/portfolios">{port.name}</Link>
          ) : (
            '—'
          )}
        </p>
        {selectedProgram.description ? (
          <p style={{ fontSize: '0.9rem' }}>{selectedProgram.description}</p>
        ) : null}
        <p className="muted" style={{ marginTop: 12 }}>
          <Link to="/capability/projects">{t('capability.tabs.projects')}</Link>
        </p>
      </div>
    )
  } else if (tab === 'projects' && selectedProject) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          Items · {selectedProject.name}
        </h2>
        <form
          className="form-grid"
          onSubmit={(e) => {
            e.preventDefault()
            run('item', async () => {
              await capabilityApi.projects.addItem(selectedProject.id, {
                name: itemForm.name,
                taskId: itemForm.taskId || null,
              })
              setInfo(t('capability.itemAdded'))
              setItemForm({ name: '', taskId: '' })
              await selectProject(selectedProject)
            })
          }}
        >
          <div className="form-row-2">
            <label>
              Item name *
              <input
                required
                value={itemForm.name}
                onChange={(e) => setItemForm((f) => ({ ...f, name: e.target.value }))}
              />
            </label>
            <label>
              Task id (optional)
              <input
                value={itemForm.taskId}
                onChange={(e) => setItemForm((f) => ({ ...f, taskId: e.target.value }))}
              />
            </label>
          </div>
          <button type="submit" className="btn btn-primary" disabled={!!busy}>
            <Plus size={16} /> {t('actions.create', { ns: 'common' })}
          </button>
        </form>
        <ul className="muted" style={{ marginTop: 8 }}>
          {projectItems.map((i) => (
            <li key={i.id}>
              #{i.itemNo} {i.name}
              {i.taskId ? ` · task ${i.taskId}` : ''} · {i.itemStatus}
            </li>
          ))}
        </ul>
        <ContentRail
          package="capability"
          resource="Project"
          id={selectedProject.id}
          onChanged={async () =>
            setProjectContentMap(await fetchContentMap('capability', 'Project', projects))
          }
        />
      </div>
    )
  } else if (tab === 'processes' && selectedProcess) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <ContentRail
          package="capability"
          resource="Process"
          id={selectedProcess.id}
          onChanged={async () =>
            setProcessContentMap(await fetchContentMap('capability', 'Process', processes))
          }
        />
        <h2 className="section-label">
          {t('ui.flow', { ns: 'processes' })} ·{' '}
          {localizedSelectedProcess?.name || selectedProcess.name}{' '}
          <span className={kindBadge(selectedProcess.processKind)}>
            {selectedProcess.processKind || 'HUMAN'}
          </span>
        </h2>
        {localizedSelectedProcess?.description ? (
          <p className="muted" style={{ fontSize: '0.9rem' }}>
            {localizedSelectedProcess.description}
          </p>
        ) : null}
        <ProcessDiagram
          processCode={selectedProcess.processCode}
          steps={localizedSelectedProcess?.steps || processSteps}
          mermaid={processMermaid}
          title={selectedProcess.processCode || undefined}
        />
        <h2 className="section-label">
          {t('ui.stepsHeading', { ns: 'processes' })} ·{' '}
          {localizedSelectedProcess?.name || selectedProcess.name}
        </h2>
        {selectedProcess.systemLocked || selectedProcess.processKind === 'SYSTEM' ? (
          <p className="muted" style={{ fontSize: '0.85rem' }}>
            {t('ui.systemReadonly', { ns: 'processes' })}
          </p>
        ) : (
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault()
              run('step', async () => {
                await capabilityApi.processes.addStep(selectedProcess.id, {
                  name: stepForm.name,
                  predecessorId: stepForm.predecessorId || null,
                })
                setInfo(t('capability.stepAdded'))
                setStepForm({ name: '', predecessorId: '' })
                await selectProcess(selectedProcess)
              })
            }}
          >
            <label>
              {t('capability.stepName')} *
              <input
                required
                value={stepForm.name}
                onChange={(e) => setStepForm((f) => ({ ...f, name: e.target.value }))}
              />
            </label>
            <label>
              {t('capability.predecessorStep')}
              <select
                value={stepForm.predecessorId}
                onChange={(e) => setStepForm((f) => ({ ...f, predecessorId: e.target.value }))}
              >
                <option value="">{t('capability.noneOption')}</option>
                {(localizedSelectedProcess?.steps || processSteps).map((s) => (
                  <option key={s.id} value={s.id}>
                    #{s.stepNo} {s.name}
                  </option>
                ))}
              </select>
            </label>
            <button type="submit" className="btn btn-primary" disabled={!!busy}>
              <Plus size={16} /> {t('actions.create', { ns: 'common' })}
            </button>
          </form>
        )}
        <ul className="muted">
          {(localizedSelectedProcess?.steps || processSteps).map((s) => (
            <li key={s.id || s.stepNo}>
              #{s.stepNo} {s.name}
              {s.outcomeStatus ? ` · ${s.outcomeStatus}` : ''}
              {s.actorHint ? ` · ${s.actorHint}` : ''}
              {s.deskHint ? ` · ${s.deskHint}` : ''}
            </li>
          ))}
        </ul>
      </div>
    )
  } else if (tab === 'quality' && selectedQuality) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          Items · {selectedQuality.name}
        </h2>
        <form
          className="form-grid"
          onSubmit={(e) => {
            e.preventDefault()
            run('qi', async () => {
              await capabilityApi.qualities.addItem(selectedQuality.id, qItemForm)
              setInfo(t('capability.itemAdded'))
              setQItemForm({ name: '', itemKind: 'REQ' })
              await selectQuality(selectedQuality)
            })
          }}
        >
          <div className="form-row-2">
            <label>
              Name *
              <input
                required
                value={qItemForm.name}
                onChange={(e) => setQItemForm((f) => ({ ...f, name: e.target.value }))}
              />
            </label>
            <label>
              Kind
              <select
                value={qItemForm.itemKind}
                onChange={(e) => setQItemForm((f) => ({ ...f, itemKind: e.target.value }))}
              >
                <option value="REQ">REQ</option>
                <option value="NCR">NCR</option>
              </select>
            </label>
          </div>
          <button type="submit" className="btn btn-primary" disabled={!!busy}>
            <Plus size={16} /> {t('actions.create', { ns: 'common' })}
          </button>
        </form>
        <ul className="muted">
          {qualityItems.map((i) => (
            <li key={i.id}>
              [{i.itemKind}] {i.name} · {i.itemStatus}{' '}
              {i.itemStatus === 'OPEN' && (
                <button
                  type="button"
                  className="btn btn-ghost"
                  disabled={!!busy}
                  onClick={() =>
                    run('qclose', async () => {
                      await capabilityApi.qualityItems.close(i.id)
                      setInfo(t('capability.itemClosed'))
                      await selectQuality(selectedQuality)
                    })
                  }
                >
                  Close
                </button>
              )}
            </li>
          ))}
        </ul>
      </div>
    )
  } else if (tab === 'changes' && selectedChange) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          Lines · {selectedChange.name}
        </h2>
        <form
          className="form-grid"
          onSubmit={(e) => {
            e.preventDefault()
            run('ci', async () => {
              await capabilityApi.changes.addItem(selectedChange.id, cItemForm)
              setInfo(t('capability.lineAdded'))
              setCItemForm({ name: '' })
              await selectChange(selectedChange)
            })
          }}
        >
          <label>
            Line name *
            <input
              required
              value={cItemForm.name}
              onChange={(e) => setCItemForm((f) => ({ ...f, name: e.target.value }))}
            />
          </label>
          <button type="submit" className="btn btn-primary" disabled={!!busy}>
            <Plus size={16} /> {t('actions.create', { ns: 'common' })}
          </button>
        </form>
        <ul className="muted">
          {changeItems.map((i) => (
            <li key={i.id}>
              #{i.itemNo} {i.name} · {i.itemStatus}
            </li>
          ))}
        </ul>
        <ContentRail
          package="capability"
          resource="Change"
          id={selectedChange.id}
          onChanged={async () =>
            setChangeContentMap(await fetchContentMap('capability', 'Change', changes))
          }
        />
      </div>
    )
  }

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

      <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
        <button
          type="button"
          className="btn"
          onClick={() => loadSection()}
          disabled={loading || !!busy}
        >
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <Link className="btn btn-secondary" to="/capability/projects">
          {t('capability.title')}
        </Link>
      </div>

      <DeskShell
        // ADR-050 Wave D: split for portfolio/project/process detail; col2 gated by hasSelection.
        layout="split"
        title={
          <>
            <SectionIcon size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {sectionMeta?.label || t('capability.title')}
          </>
        }
        subtitle={t('capability.subtitle')}
        tabs={tabs}
        activeTab={tab}
        onTabChange={requestTabChange}
        newLabel={newLabel}
        newExpanded={newExpanded}
        onToggleNew={requestToggleNew}
        newPanel={newPanel}
        toolbar={tab === 'processes' ? processesToolbar : null}
        list={list}
        hasSelection={hasSelection}
        emptyTitle={emptyTitle}
        emptyBody={emptyBody}
        detail={detail}
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
