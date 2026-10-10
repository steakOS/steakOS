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
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  FolderKanban,
  GitBranch,
  BadgeCheck,
  RefreshCw,
  BookOpen,
  Plus,
  Play,
  Check,
  X,
} from 'lucide-react'
import { capabilityApi } from '../api/client'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import ListPager from '../components/ListPager'
import ProcessDiagram from '../components/ProcessDiagram'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import { localizeProcess } from '../utils/localizeProcess'
import ResponsiveDataList from '../components/ResponsiveDataList'

const SECTION_DEFS = [
  { id: 'projects', labelKey: 'capability.tabs.projects', icon: FolderKanban },
  { id: 'processes', labelKey: 'capability.tabs.processes', icon: GitBranch },
  { id: 'quality', labelKey: 'capability.tabs.quality', icon: BadgeCheck },
  { id: 'changes', labelKey: 'capability.tabs.changes', icon: RefreshCw },
  { id: 'knowledge', labelKey: 'capability.tabs.knowledge', icon: BookOpen },
]

/**
 * Capability ops — split by section (not a single desk).
 * Projects · Processes · Quality · Org Changes · Knowledge (+ Standards).
 */
export default function CapabilityHub() {
  const { t, i18n } = useTranslation(['desks', 'common', 'content', 'processes'])
  const { viewModal, ViewButton } = useRecordView()
  const SECTIONS = useMemo(
    () => SECTION_DEFS.map((d) => ({ ...d, label: t(d.labelKey) })),
    [t],
  )
  const { section } = useParams()
  const [searchParams] = useSearchParams()
  const tab = SECTIONS.some((s) => s.id === section) ? section : 'projects'

  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)

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
  const [selectedProject, setSelectedProject] = useState(null)
  const [projectContentMap, setProjectContentMap] = useState({})
  const [processContentMap, setProcessContentMap] = useState({})
  const [changeContentMap, setChangeContentMap] = useState({})
  const [pendingProjectContent, setPendingProjectContent] = useState([])
  const [projectItems, setProjectItems] = useState([])
  const [selectedProcess, setSelectedProcess] = useState(null)
  const [processSteps, setProcessSteps] = useState([])
  const [selectedChange, setSelectedChange] = useState(null)
  const [changeItems, setChangeItems] = useState([])
  const [selectedQuality, setSelectedQuality] = useState(null)

  const [projPage, setProjPage] = useState({ max: 25, offset: 0, total: 0 })
  const [procPage, setProcPage] = useState({ max: 25, offset: 0, total: 0 })
  const [procedurePage, setProcedurePage] = useState({ max: 25, offset: 0, total: 0 })
  const [qualPage, setQualPage] = useState({ max: 25, offset: 0, total: 0 })
  const [chgPage, setChgPage] = useState({ max: 25, offset: 0, total: 0 })
  const [knowPage, setKnowPage] = useState({ max: 25, offset: 0, total: 0 })
  const [stdPage, setStdPage] = useState({ max: 25, offset: 0, total: 0 })

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
      if (tab === 'projects') {
        const [portPick, progPick, proj] = await Promise.all([
          capabilityApi.portfolios.list({ max: 100, offset: 0 }),
          capabilityApi.programs.list({ max: 100, offset: 0 }),
          capabilityApi.projects.list({ max: projPage.max, offset: projPage.offset }),
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
        })
        setQualities(q.items || [])
        setQualPage((pg) => ({
          ...pg,
          max: q.max ?? pg.max,
          offset: q.offset ?? pg.offset,
          total: q.total ?? (q.items || []).length,
        }))
        setQualityItems([])
        setSelectedQuality(null)
      } else if (tab === 'changes') {
        const c = await capabilityApi.changes.list({
          max: chgPage.max,
          offset: chgPage.offset,
        })
        const ch = c.items || []
        setChanges(ch)
        setChgPage((pg) => ({
          ...pg,
          max: c.max ?? pg.max,
          offset: c.offset ?? pg.offset,
          total: c.total ?? ch.length,
        }))
        setSelectedChange(null)
        setChangeItems([])
        setChangeContentMap(await fetchContentMap('capability', 'Change', ch))
      } else if (tab === 'knowledge') {
        const [k, s] = await Promise.all([
          capabilityApi.knowledge.list({ max: knowPage.max, offset: knowPage.offset }),
          capabilityApi.standards.list({ max: stdPage.max, offset: stdPage.offset }),
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
  ])

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

  const selectProject = async (p) => {
    setSelectedProject(p)
    try {
      const items = await capabilityApi.projects.items(p.id)
      setProjectItems(items.items || [])
    } catch (e) {
      setError(e.message)
    }
  }

  const selectProcess = async (p) => {
    setSelectedProcess(p)
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
    try {
      const items = await capabilityApi.changes.items(c.id)
      setChangeItems(items.items || [])
    } catch (e) {
      setError(e.message)
    }
  }

  const selectQuality = async (q) => {
    setSelectedQuality(q)
    try {
      const items = await capabilityApi.qualities.items(q.id)
      setQualityItems(items.items || [])
    } catch (e) {
      setError(e.message)
    }
  }

  const sectionMeta = SECTIONS.find((s) => s.id === tab)
  const SectionIcon = sectionMeta?.icon || FolderKanban

  return (
    <div className="page delivery-desk-page">
      <header className="page-header" style={{ marginBottom: '1rem' }}>
        <h1 className="page-title">
          <SectionIcon size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {sectionMeta?.label || t('capability.title')}
        </h1>
        <p className="page-sub">
          {t('capability.subtitle')} ·{' '}
          {SECTIONS.map((s, i) => (
            <span key={s.id}>
              {i > 0 ? ' · ' : ''}
              <Link to={`/capability/${s.id}`}>{s.label}</Link>
            </span>
          ))}
        </p>
      </header>

      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      {viewModal}
      {loading && !error && (
        <p className="muted" style={{ marginBottom: '0.75rem' }}>
          Loading…
        </p>
      )}

      <div className="toolbar">
        <button type="button" className="btn" onClick={() => loadSection()} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          {SECTIONS.map((s) => {
            const Icon = s.icon
            return (
              <Link
                key={s.id}
                to={`/capability/${s.id}`}
                className={`btn ${tab === s.id ? 'btn-primary' : 'btn-ghost'}`}
              >
                <Icon size={14} /> {s.label}
              </Link>
            )
          })}
        </div>
      </div>

      {tab === 'projects' && (
        <>
          <div className="two-col finance-tx-grid">
            <section className="card">
              <h2 className="section-label" style={{ marginTop: 0 }}>
                New portfolio
              </h2>
              <form
                className="form-grid"
                onSubmit={(e) => {
                  e.preventDefault()
                  run('port', async () => {
                    const p = await capabilityApi.portfolios.create(portForm)
                    setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.portfolio', { ns: 'common' }), name: p.name }))
                    setPortForm({ name: '', ownerRef: '' })
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

              <h2 className="section-label">{t('capability.newProgram')}</h2>
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
                    setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.program', { ns: 'common' }), name: p.name }))
                    setProgForm({ name: '', portfolioId: '' })
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

              <h2 className="section-label">{t('capability.newProject')}</h2>
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
                      onChange={(e) =>
                        setProjForm((f) => ({ ...f, portfolioId: e.target.value }))
                      }
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
            </section>

            <section className="card">
              <h2 className="section-label" style={{ marginTop: 0 }}>
                Projects ({projPage.total || projects.length})
              </h2>
              <ResponsiveDataList
                items={projects}
                empty="No projects yet"
                selectedKey={selectedProject?.id}
                onSelect={(p) => selectProject(p)}
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
                    <ViewButton pkg="capability" resource="Project" record={p} />
                  </>
                )}
              >
                <div className="table-wrap">
                  <table className="data">
                    <thead>
                      <tr>
                        <th>{t('wb.name', { ns: 'common' })}</th>
                        <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                        <th>{t('wb.status', { ns: 'common' })}</th>
                        <th>{t('wb.actions', { ns: 'common' })}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {projects.map((p) => (
                        <tr
                          key={p.id}
                          style={{
                            cursor: 'pointer',
                            background:
                              selectedProject?.id === p.id
                                ? 'var(--surface-2, #f0f0f0)'
                                : undefined,
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
                          <td
                            style={{ whiteSpace: 'nowrap' }}
                            onClick={(e) => e.stopPropagation()}
                          >
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
                            <ViewButton pkg="capability" resource="Project" record={p} />
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

              {selectedProject && (
                <>
                  <h2 className="section-label">
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
                      setProjectContentMap(
                        await fetchContentMap('capability', 'Project', projects),
                      )
                    }
                  />
                </>
              )}

              <h2 className="section-label">{t('capability.portfolios')}</h2>
              <p className="muted" style={{ margin: 0 }}>
                Portfolios: {portfolios.map((p) => p.name).join(', ') || '—'}
              </p>
              <p className="muted" style={{ margin: '4px 0 0' }}>
                Programs: {programs.map((p) => p.name).join(', ') || '—'}
              </p>
            </section>
          </div>
        </>
      )}

      {tab === 'processes' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('capability.newProcess')}
            </h2>
            <p className="muted" style={{ fontSize: '0.85rem' }}>
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
                  setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.process', { ns: 'common' }), name: p.name }))
                  setProcForm({ name: '', processCode: '', processKind: 'HUMAN' })
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
                  onChange={(e) =>
                    setProcedureForm((f) => ({ ...f, processId: e.target.value }))
                  }
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
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('capability.processesCount', {
                count: procPage.total || visibleProcesses.length,
              })}
            </h2>
            <div className="banner-info" role="status" style={{ marginBottom: 8 }}>
              <strong>{t('capability.processBrowserTitle')}</strong>
              <p style={{ margin: '0.25rem 0 0' }}>{t('capability.processBrowserHint')}</p>
            </div>
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
            <ResponsiveDataList
              items={visibleProcesses}
              empty={t('capability.emptyProcesses')}
              selectedKey={selectedProcess?.id}
              onSelect={(p) => selectProcess(p)}
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
                  <ViewButton pkg="capability" resource="Process" record={p} />
                </>
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.code', { ns: 'common' })}</th>
                      <th>{t('wb.kind', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {visibleProcesses.map((p) => (
                      <tr
                        key={p.id}
                        style={{
                          cursor: 'pointer',
                          background:
                            selectedProcess?.id === p.id
                              ? 'var(--surface-2, #f0f0f0)'
                              : undefined,
                        }}
                        onClick={() => selectProcess(p)}
                      >
                        <td>{processDisplayName(p)}</td>
                        <td className="mono col-priority-low">{p.processCode || '—'}</td>
                        <td>
                          <span className={kindBadge(p.processKind)}>
                            {p.processKind || 'HUMAN'}
                          </span>
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
                          <ViewButton pkg="capability" resource="Process" record={p} />
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

            {selectedProcess && (
              <>
                <ContentRail
                  package="capability"
                  resource="Process"
                  id={selectedProcess.id}
                  onChanged={async () =>
                    setProcessContentMap(
                      await fetchContentMap('capability', 'Process', processes),
                    )
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
                        onChange={(e) =>
                          setStepForm((f) => ({ ...f, predecessorId: e.target.value }))
                        }
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
              </>
            )}

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
          </section>
        </div>
      )}

      {tab === 'quality' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              New quality register
            </h2>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault()
                run('qual', async () => {
                  const q = await capabilityApi.qualities.create(qualForm)
                  setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.quality', { ns: 'common' }), name: q.name }))
                  setQualForm({ name: '', qualityCode: '' })
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
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Quality ({qualPage.total || qualities.length})
            </h2>
            <ResponsiveDataList
              items={qualities}
              empty="—"
              selectedKey={selectedQuality?.id}
              onSelect={(q) => selectQuality(q)}
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
                  <ViewButton pkg="capability" resource="Quality" record={q} />
                </>
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.code', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {qualities.map((q) => (
                      <tr
                        key={q.id}
                        style={{
                          cursor: 'pointer',
                          background:
                            selectedQuality?.id === q.id
                              ? 'var(--surface-2, #f0f0f0)'
                              : undefined,
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
                          <ViewButton pkg="capability" resource="Quality" record={q} />
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

            {selectedQuality && (
              <>
                <h2 className="section-label">Items · {selectedQuality.name}</h2>
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
                        onChange={(e) =>
                          setQItemForm((f) => ({ ...f, itemKind: e.target.value }))
                        }
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
              </>
            )}
          </section>
        </div>
      )}

      {tab === 'changes' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              New org change
            </h2>
            <p className="muted" style={{ marginTop: 0 }}>
              Organizational change only — IT/tech change lives under Digital.
            </p>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault()
                run('chg', async () => {
                  const c = await capabilityApi.changes.create(chgForm)
                  setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.change', { ns: 'common' }), name: c.name }))
                  setChgForm({ name: '', impactNote: '' })
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
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Org changes ({chgPage.total || changes.length})
            </h2>
            <ResponsiveDataList
              items={changes}
              empty="—"
              selectedKey={selectedChange?.id}
              onSelect={(c) => selectChange(c)}
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
                  <ViewButton pkg="capability" resource="Change" record={c} />
                </>
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th>{t('wb.actions', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {changes.map((c) => (
                      <tr
                        key={c.id}
                        style={{
                          cursor: 'pointer',
                          background:
                            selectedChange?.id === c.id
                              ? 'var(--surface-2, #f0f0f0)'
                              : undefined,
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
                          <ViewButton pkg="capability" resource="Change" record={c} />
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

            {selectedChange && (
              <>
                <h2 className="section-label">Lines · {selectedChange.name}</h2>
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
              </>
            )}
          </section>
        </div>
      )}

      {tab === 'knowledge' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
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
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Knowledge areas ({knowPage.total || knowledge.length})
            </h2>
            <ResponsiveDataList
              items={knowledge}
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
                  render: (k) => (
                    <span className={statusClass(k.areaStatus)}>{k.areaStatus}</span>
                  ),
                },
                {
                  id: 'parent',
                  priority: 'low',
                  render: (k) => k.parentId || '—',
                },
              ]}
              actions={(k) => (
                <ViewButton pkg="capability" resource="KnowledgeArea" record={k} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.code', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('capability.parent')}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
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
                          <ViewButton pkg="capability" resource="KnowledgeArea" record={k} />
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
              actions={(s) => (
                <ViewButton pkg="capability" resource="Standard" record={s} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th>{t('capability.metric')}</th>
                      <th>{t('capability.target')}</th>
                      <th className="col-priority-low">{t('wb.unit', { ns: 'common' })}</th>
                      <th />
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
                          <ViewButton pkg="capability" resource="Standard" record={s} />
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
          </section>
        </div>
      )}
    </div>
  )
}
