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
import { Link, useParams } from 'react-router-dom'
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
} from 'lucide-react'
import { agentsApi, agentTokenApi } from '../api/client'
import ProcessHowItWorks from '../components/ProcessHowItWorks'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import ResponsiveDataList from '../components/ResponsiveDataList'
import ListPager from '../components/ListPager'

const SECTION_DEFS = [
  { id: 'agents', labelKey: 'agents.tabs.agents', icon: Bot },
  { id: 'hives', labelKey: 'agents.tabs.hives', icon: Network },
  { id: 'hitl', labelKey: 'agents.tabs.hitl', icon: ShieldCheck },
  { id: 'memory', labelKey: 'agents.tabs.memory', icon: Brain },
]

/**
 * Agentic Resources — split menu: Agents · Hives · HITL · Memory.
 */
export default function AgentHub() {
  const { t } = useTranslation(['desks', 'common', 'content'])
  const { viewModal, ViewButton } = useRecordView()
  const SECTIONS = useMemo(
    () => SECTION_DEFS.map((d) => ({ ...d, label: t(d.labelKey) })),
    [t],
  )
  const { section } = useParams()
  const tab = SECTIONS.some((s) => s.id === section) ? section : 'agents'

  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)

  const [agents, setAgents] = useState([])
  const [hives, setHives] = useState([])
  const [approvals, setApprovals] = useState([])
  const [memories, setMemories] = useState([])
  const [links, setLinks] = useState([])
  /** Dropdown options (not paged) — max 100 until typeahead. */
  const [agentOptions, setAgentOptions] = useState([])
  const [selectedHive, setSelectedHive] = useState(null)
  const [selectedAgent, setSelectedAgent] = useState(null)
  const [agentContentMap, setAgentContentMap] = useState({})
  const [hiveContentMap, setHiveContentMap] = useState({})
  const [pendingAgentContent, setPendingAgentContent] = useState([])
  const [hiveMembers, setHiveMembers] = useState([])
  const [agentPage, setAgentPage] = useState({ max: 25, offset: 0, total: 0 })
  const [hivePage, setHivePage] = useState({ max: 25, offset: 0, total: 0 })
  const [approvalPage, setApprovalPage] = useState({ max: 25, offset: 0, total: 0 })
  const [memoryPage, setMemoryPage] = useState({ max: 25, offset: 0, total: 0 })

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
          agentsApi.agents.list({ max: agentPage.max, offset: agentPage.offset }),
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
          agentsApi.hives.list({ max: hivePage.max, offset: hivePage.offset }),
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
        setHiveContentMap(await fetchContentMap('agents', 'Hive', hv))
      } else if (tab === 'hitl') {
        // P3 SPA queue: default PENDING (human approval queue for L1 proposals)
        const [ap, aPick] = await Promise.all([
          agentsApi.approvals.list({
            max: approvalPage.max,
            offset: approvalPage.offset,
            decision: 'PENDING',
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
          agentsApi.memories.list({ max: memoryPage.max, offset: memoryPage.offset }),
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
    hivePage.max,
    hivePage.offset,
    approvalPage.max,
    approvalPage.offset,
    memoryPage.max,
    memoryPage.offset,
  ])

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

  const selectHive = async (h) => {
    setSelectedHive(h)
    try {
      const m = await agentsApi.hives.members(h.id)
      setHiveMembers(m.items || [])
    } catch (e) {
      setError(e.message)
    }
  }

  const sectionMeta = SECTIONS.find((s) => s.id === tab)
  const SectionIcon = sectionMeta?.icon || Bot

  return (
    <div className="page delivery-desk-page">
      <header className="page-header" style={{ marginBottom: '1rem' }}>
        <h1 className="page-title">
          <SectionIcon size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {sectionMeta?.label || t('agents.title')}
        </h1>
        <p className="page-sub">
          {t('agents.subtitle')} ·{' '}
          {SECTIONS.map((s, i) => (
            <span key={s.id}>
              {i > 0 ? ' · ' : ''}
              <Link to={`/agents/${s.id}`}>{s.label}</Link>
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
            <li>{t('agents.activateStepPaste', { defaultValue: 'Paste token into agent host (Hermes / compatible)' })}</li>
            <li>{t('agents.activateStepSmoke', { defaultValue: 'Smoke read: /api/agent/me' })}</li>
            <li>
              <Link to="/agents/hitl">{t('agents.tabs.hitl')}</Link>
              {' — '}
              {t('agents.activateStepHitl', { defaultValue: 'optional propose → human approve' })}
            </li>
          </ol>
          <p className="muted" style={{ margin: 0, fontSize: '0.85rem' }}>
            {t('agents.activateDocs', {
              defaultValue: 'Guide: docs/installation/ACTIVATE-AGENT.md · Help: Activate agent (API)',
            })}{' '}
            · <Link to="/help">{t('nav.help', { ns: 'common', defaultValue: 'Help' })}</Link>
          </p>
        </div>
      )}

      <div className="toolbar">
        <button type="button" className="btn" onClick={() => loadSection()} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <ProcessHowItWorks processCode="AR_HITL_APPROVAL" />
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          {SECTIONS.map((s) => {
            const Icon = s.icon
            return (
              <Link
                key={s.id}
                to={`/agents/${s.id}`}
                className={`btn ${tab === s.id ? 'btn-primary' : 'btn-ghost'}`}
              >
                <Icon size={14} /> {s.label}
              </Link>
            )
          })}
        </div>
      </div>

      {tab === 'agents' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
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
                  setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.agent', { ns: 'common' }), name: agentForm.name || agentForm.agentKey }))
                  setAgentForm({
                    name: '',
                    agentKey: '',
                    runtimeKind: 'HERMES',
                    endpointRef: '',
                    taskContext: '',
                    soul: '',
                  })
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
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Agents ({agentPage.total})
            </h2>
            <ResponsiveDataList
              items={agents}
              empty="—"
              loading={loading}
              selectedKey={selectedAgent?.id}
              onSelect={(a) => setSelectedAgent(a)}
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
                  render: (a) => (
                    <span className={statusClass(a.agentStatus)}>{a.agentStatus}</span>
                  ),
                },
                {
                  id: 'content',
                  priority: 'low',
                  render: (a) => <ContentChannelIcons summary={agentContentMap[a.id]} />,
                },
              ]}
              actions={(a) => (
                <>
                  {a.agentStatus !== 'ACTIVE' && a.agentStatus !== 'RETIRED' && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={!!busy}
                      onClick={() =>
                        run('act', async () => {
                          await agentsApi.agents.activate(a.id)
                          setInfo(t('toast.activated', { ns: 'common', name: a.name }))
                        })
                      }
                    >
                      <Play size={14} />
                    </button>
                  )}
                  {a.agentStatus === 'ACTIVE' && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={!!busy}
                      onClick={() =>
                        run('pause', async () => {
                          await agentsApi.agents.pause(a.id)
                          setInfo(t('toast.paused', { ns: 'common', name: a.name }))
                        })
                      }
                    >
                      <Pause size={14} />
                    </button>
                  )}
                  {a.agentStatus !== 'RETIRED' && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={!!busy}
                      onClick={() =>
                        run('ret', async () => {
                          await agentsApi.agents.retire(a.id)
                          setInfo(t('toast.retired', { ns: 'common', name: a.name }))
                        })
                      }
                    >
                      <X size={14} />
                    </button>
                  )}
                  <ViewButton pkg="agents" resource="Agent" record={a} />
                </>
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.code', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('agents.runtime')}</th>
                      <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {agents.map((a) => (
                      <tr
                        key={a.id}
                        className={selectedAgent?.id === a.id ? 'row-selected' : 'row-click'}
                        onClick={() => setSelectedAgent(a)}
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
                          {a.agentStatus !== 'ACTIVE' && a.agentStatus !== 'RETIRED' && (
                            <button
                              type="button"
                              className="btn btn-ghost"
                              disabled={!!busy}
                              onClick={() =>
                                run('act', async () => {
                                  await agentsApi.agents.activate(a.id)
                                  setInfo(t('toast.activated', { ns: 'common', name: a.name }))
                                })
                              }
                            >
                              <Play size={14} />
                            </button>
                          )}
                          {a.agentStatus === 'ACTIVE' && (
                            <button
                              type="button"
                              className="btn btn-ghost"
                              disabled={!!busy}
                              onClick={() =>
                                run('pause', async () => {
                                  await agentsApi.agents.pause(a.id)
                                  setInfo(t('toast.paused', { ns: 'common', name: a.name }))
                                })
                              }
                            >
                              <Pause size={14} />
                            </button>
                          )}
                          {a.agentStatus !== 'RETIRED' && (
                            <button
                              type="button"
                              className="btn btn-ghost"
                              disabled={!!busy}
                              onClick={() =>
                                run('ret', async () => {
                                  await agentsApi.agents.retire(a.id)
                                  setInfo(t('toast.retired', { ns: 'common', name: a.name }))
                                })
                              }
                            >
                              <X size={14} />
                            </button>
                          )}
                          <ViewButton pkg="agents" resource="Agent" record={a} />
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
                  Agent #{l.agentId} · {l.linkKind} · {l.active ? 'active' : 'inactive'}
                </li>
              ))}
            </ul>
            {selectedAgent && (
              <ContentRail
                package="agents"
                resource="Agent"
                id={selectedAgent.id}
                onChanged={async () =>
                  setAgentContentMap(await fetchContentMap('agents', 'Agent', agents))
                }
              />
            )}
          </section>
        </div>
      )}

      {tab === 'hives' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('agents.newHive')}
            </h2>
            <p className="muted" style={{ marginTop: 0 }}>
              Each hive needs one <strong>{t('agents.hiveMaster')}</strong> (coordinator) to activate.
            </p>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault()
                run('hive', async () => {
                  await agentsApi.hives.create({
                    name: hiveForm.name,
                    apqcAreaCode: hiveForm.apqcAreaCode || null,
                    hiveMasterAgentId: hiveForm.hiveMasterAgentId || null,
                  })
                  setInfo(t('agents.hiveCreated'))
                  setHiveForm({ name: '', apqcAreaCode: '', hiveMasterAgentId: '' })
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
                    onChange={(e) =>
                      setHiveForm((f) => ({ ...f, hiveMasterAgentId: e.target.value }))
                    }
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
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Hives ({hivePage.total})
            </h2>
            <ResponsiveDataList
              items={hives}
              empty="—"
              loading={loading}
              selectedKey={selectedHive?.id}
              onSelect={(h) => selectHive(h)}
              fields={[
                { id: 'name', primary: true, render: (h) => h.name },
                {
                  id: 'area',
                  secondary: true,
                  render: (h) => (
                    <span className="mono">{h.apqcAreaCode || '—'}</span>
                  ),
                },
                {
                  id: 'status',
                  meta: true,
                  render: (h) => (
                    <span className={statusClass(h.hiveStatus)}>{h.hiveStatus}</span>
                  ),
                },
                {
                  id: 'master',
                  priority: 'low',
                  render: (h) => h.hiveMasterAgentId || '—',
                },
              ]}
              actions={(h) => (
                <>
                  {h.hiveStatus !== 'ACTIVE' && h.hiveStatus !== 'CLOSED' && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={!!busy}
                      onClick={() =>
                        run('hact', async () => {
                          await agentsApi.hives.activate(h.id)
                          setInfo(t('toast.activated', { ns: 'common', name: h.name }))
                        })
                      }
                    >
                      Activate
                    </button>
                  )}
                  {h.hiveStatus === 'ACTIVE' && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={!!busy}
                      onClick={() =>
                        run('hclose', async () => {
                          await agentsApi.hives.close(h.id)
                          setInfo(t('toast.closedName', { ns: 'common', name: h.name }))
                        })
                      }
                    >
                      Close
                    </button>
                  )}
                  <ViewButton pkg="agents" resource="Hive" record={h} />
                </>
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th>{t('agents.area')}</th>
                      <th className="col-priority-low">{t('agents.master')}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {hives.map((h) => (
                      <tr
                        key={h.id}
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
                          {h.hiveStatus !== 'ACTIVE' && h.hiveStatus !== 'CLOSED' && (
                            <button
                              type="button"
                              className="btn btn-ghost"
                              disabled={!!busy}
                              onClick={() =>
                                run('hact', async () => {
                                  await agentsApi.hives.activate(h.id)
                                  setInfo(t('toast.activated', { ns: 'common', name: h.name }))
                                })
                              }
                            >
                              Activate
                            </button>
                          )}
                          {h.hiveStatus === 'ACTIVE' && (
                            <button
                              type="button"
                              className="btn btn-ghost"
                              disabled={!!busy}
                              onClick={() =>
                                run('hclose', async () => {
                                  await agentsApi.hives.close(h.id)
                                  setInfo(t('toast.closedName', { ns: 'common', name: h.name }))
                                })
                              }
                            >
                              Close
                            </button>
                          )}
                          <ViewButton pkg="agents" resource="Hive" record={h} />
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

            {selectedHive && (
              <>
                <h2 className="section-label">Members · {selectedHive.name}</h2>
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
              </>
            )}
          </section>
        </div>
      )}

      {tab === 'hitl' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('agents.hitlPanelTitle', {
                defaultValue: 'Human-in-the-loop (HITL)',
              })}
            </h2>
            <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
              {t('agents.hitlPanelHint', {
                defaultValue:
                  'Pending proposals from Hermes agents and desk submissions. Approve or reject below.',
              })}
            </p>
            <h2 className="section-label">
              {t('agents.submitProposal', { defaultValue: 'Submit proposal' })}
            </h2>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault()
                run('sub', async () => {
                  await agentsApi.approvals.submit({
                    name: approvalForm.name,
                    agentId: approvalForm.agentId || null,
                    riskLevel: approvalForm.riskLevel,
                    targetFamily: approvalForm.targetFamily || null,
                    proposalJson: approvalForm.proposalJson || null,
                  })
                  setInfo(t('agents.proposalPending'))
                  setApprovalForm({
                    name: '',
                    agentId: '',
                    riskLevel: 'LOW',
                    targetFamily: '',
                    proposalJson: '',
                  })
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
                    onChange={(e) =>
                      setApprovalForm((f) => ({ ...f, riskLevel: e.target.value }))
                    }
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
                  onChange={(e) =>
                    setApprovalForm((f) => ({ ...f, targetFamily: e.target.value }))
                  }
                />
              </label>
              <label>
                Proposal JSON
                <textarea
                  rows={3}
                  value={approvalForm.proposalJson}
                  onChange={(e) =>
                    setApprovalForm((f) => ({ ...f, proposalJson: e.target.value }))
                  }
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> Submit
              </button>
            </form>
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Pending approvals ({approvalPage.total})
            </h2>
            <ResponsiveDataList
              items={approvals}
              empty="—"
              loading={loading}
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
                {
                  id: 'risk',
                  secondary: true,
                  render: (a) => a.riskLevel,
                },
                {
                  id: 'decision',
                  meta: true,
                  render: (a) => (
                    <span className={statusClass(a.decision)}>{a.decision}</span>
                  ),
                },
              ]}
              actions={(a) => (
                <>
                  {a.decision === 'PENDING' && (
                    <>
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('ok', async () => {
                            await agentsApi.approvals.approve(a.id)
                            setInfo(t('toast.approved', { ns: 'common', name: a.name }))
                          })
                        }
                      >
                        <Check size={14} />
                      </button>
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!!busy}
                        onClick={() =>
                          run('no', async () => {
                            await agentsApi.approvals.reject(a.id)
                            setInfo(t('toast.rejected', { ns: 'common', name: a.name }))
                          })
                        }
                      >
                        <X size={14} />
                      </button>
                    </>
                  )}
                  <ViewButton pkg="agents" resource="Approval" record={a} />
                </>
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.title', { ns: 'common' })}</th>
                      <th>{t('agents.risk')}</th>
                      <th>{t('agents.decision')}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {approvals.map((a) => (
                      <tr key={a.id}>
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
                        <td>
                          {a.decision === 'PENDING' && (
                            <>
                              <button
                                type="button"
                                className="btn btn-ghost"
                                disabled={!!busy}
                                onClick={() =>
                                  run('ok', async () => {
                                    await agentsApi.approvals.approve(a.id)
                                    setInfo(t('toast.approved', { ns: 'common', name: a.name }))
                                  })
                                }
                              >
                                <Check size={14} />
                              </button>
                              <button
                                type="button"
                                className="btn btn-ghost"
                                disabled={!!busy}
                                onClick={() =>
                                  run('no', async () => {
                                    await agentsApi.approvals.reject(a.id)
                                    setInfo(t('toast.rejected', { ns: 'common', name: a.name }))
                                  })
                                }
                              >
                                <X size={14} />
                              </button>
                            </>
                          )}
                          <ViewButton pkg="agents" resource="Approval" record={a} />
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
          </section>
        </div>
      )}

      {tab === 'memory' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Record learning signal
            </h2>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault()
                run('mem', async () => {
                  await agentsApi.memories.record({
                    name: memoryForm.name,
                    agentId: memoryForm.agentId || null,
                    memoryKind: memoryForm.memoryKind,
                    score: memoryForm.score || null,
                    payloadJson: memoryForm.payloadJson || null,
                  })
                  setInfo(t('agents.memoryRecorded'))
                  setMemoryForm({
                    name: '',
                    agentId: '',
                    memoryKind: 'FEEDBACK',
                    score: '',
                    payloadJson: '',
                  })
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
                    onChange={(e) =>
                      setMemoryForm((f) => ({ ...f, memoryKind: e.target.value }))
                    }
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
          </section>

          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Memory ({memoryPage.total})
            </h2>
            <ResponsiveDataList
              items={memories}
              empty="—"
              loading={loading}
              fields={[
                { id: 'name', primary: true, render: (m) => m.name },
                {
                  id: 'kind',
                  secondary: true,
                  render: (m) => m.memoryKind,
                },
                {
                  id: 'score',
                  meta: true,
                  render: (m) => (m.score != null ? m.score : '—'),
                },
                {
                  id: 'agent',
                  priority: 'low',
                  render: (m) => m.agentId || '—',
                },
              ]}
              actions={(m) => (
                <>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    disabled={!!busy}
                    onClick={() =>
                      run('arch', async () => {
                        await agentsApi.memories.archive(m.id)
                        setInfo(t('toast.archived', { ns: 'common' }))
                      })
                    }
                  >
                    Archive
                  </button>
                  <ViewButton pkg="agents" resource="Memory" record={m} />
                </>
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.title', { ns: 'common' })}</th>
                      <th>{t('wb.kind', { ns: 'common' })}</th>
                      <th>{t('agents.score')}</th>
                      <th className="col-priority-low">{t('agents.tabs.agents')}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {memories.map((m) => (
                      <tr key={m.id}>
                        <td>{m.name}</td>
                        <td>{m.memoryKind}</td>
                        <td>{m.score != null ? m.score : '—'}</td>
                        <td className="col-priority-low">{m.agentId || '—'}</td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <button
                            type="button"
                            className="btn btn-ghost"
                            disabled={!!busy}
                            onClick={() =>
                              run('arch', async () => {
                                await agentsApi.memories.archive(m.id)
                                setInfo(t('toast.archived', { ns: 'common' }))
                              })
                            }
                          >
                            Archive
                          </button>
                          <ViewButton pkg="agents" resource="Memory" record={m} />
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
          </section>
        </div>
      )}
    </div>
  )
}
