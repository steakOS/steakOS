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
import { digitalApi } from '../api/client'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import ListPager from '../components/ListPager'
import useRecordView from '../hooks/useRecordView'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import ResponsiveDataList from '../components/ResponsiveDataList'

const TAB_DEFS = [
  { id: 'systems', labelKey: 'digital.tabs.systems', icon: Server },
  { id: 'data', labelKey: 'digital.tabs.data', icon: Database },
  { id: 'solutions', labelKey: 'digital.tabs.solutions', icon: Boxes },
  { id: 'changes', labelKey: 'digital.tabs.changes', icon: GitBranch },
  { id: 'cmdb', labelKey: 'digital.tabs.cmdb', icon: HardDrive },
  { id: 'support', labelKey: 'digital.tabs.support', icon: Ticket },
]

/**
 * Digital Technology desk (DIG0–DIG8) — Delivery-style UI shell.
 */
export default function DigitalDesk() {
  const { t } = useTranslation(['desks', 'common', 'content'])
  const { viewModal, ViewButton } = useRecordView()
  const TABS = useMemo(
    () => TAB_DEFS.map((d) => ({ ...d, label: t(d.labelKey) })),
    [t],
  )
  const [tab, setTab] = useState('solutions')
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
  const [changeContentMap, setChangeContentMap] = useState({})
  const [ticketContentMap, setTicketContentMap] = useState({})
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

  const [sysForm, setSysForm] = useState({
    name: '',
    systemKey: '',
    environment: 'PROD',
  })
  const [dataForm, setDataForm] = useState({
    name: '',
    dataClass: 'INTERNAL',
    retentionDays: '365',
  })
  const [solForm, setSolForm] = useState({
    name: '',
    integrationType: 'OTHER',
    systemId: '',
    versionLabel: '',
  })
  const [chgForm, setChgForm] = useState({
    name: '',
    changeKind: 'CHANGE',
    riskLevel: 'LOW',
    cabNote: '',
  })
  const [ciForm, setCiForm] = useState({
    name: '',
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
      digitalApi.systems.list({ max: sysPage.max, offset: sysPage.offset }),
      digitalApi.data.list({ max: dataPage.max, offset: dataPage.offset }),
      digitalApi.solutions.list({ max: solPage.max, offset: solPage.offset }),
      digitalApi.changes.list({ max: chgPage.max, offset: chgPage.offset }),
      digitalApi.cis.list({ max: ciPage.max, offset: ciPage.offset }),
      digitalApi.tickets.list({ max: tixPage.max, offset: tixPage.offset }),
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
    setSystems(sys?.items || [])
    setDataItems(dat?.items || [])
    setSolutions(sol?.items || [])
    setChanges(chg)
    setCis(ciRes?.items || [])
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
      total: ciRes?.total ?? (ciRes?.items || []).length,
    }))
    setTixPage((pg) => ({
      ...pg,
      max: tixRes?.max ?? pg.max,
      offset: tixRes?.offset ?? pg.offset,
      total: tixRes?.total ?? tix.length,
    }))
    const [cm, tm] = await Promise.all([
      fetchContentMap('digital', 'ITChange', chg),
      fetchContentMap('digital', 'ItTicket', tix),
    ])
    setChangeContentMap(cm)
    setTicketContentMap(tm)
    if (failed.length) {
      setError(
        `Could not load some Digital data (${failed.join('; ')}). Is the API up on :6060?`,
      )
    } else {
      setError(null)
    }
  }, [
    sysPage.max,
    sysPage.offset,
    dataPage.max,
    dataPage.offset,
    solPage.max,
    solPage.offset,
    chgPage.max,
    chgPage.offset,
    ciPage.max,
    ciPage.offset,
    tixPage.max,
    tixPage.offset,
  ])

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

  return (
    <div className="page delivery-desk-page">
      <header className="page-header" style={{ marginBottom: '1.25rem' }}>
        <h1 className="page-title">
          <Server size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('digital.title')}
        </h1>
        <p className="page-sub">{t('digital.subtitle')}</p>
      </header>

      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      {viewModal}
      {loading && !error && (
        <p className="muted" style={{ marginBottom: '0.75rem' }}>
          {t('actions.loading', { ns: 'common' })}
        </p>
      )}

      <div className="toolbar">
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          {TABS.map((tabDef) => {
            const Icon = tabDef.icon
            return (
              <button
                key={tabDef.id}
                type="button"
                className={`btn ${tab === tabDef.id ? 'btn-primary' : 'btn-ghost'}`}
                onClick={() => setTab(tabDef.id)}
              >
                <Icon size={14} /> {tabDef.label}
              </button>
            )
          })}
        </div>
      </div>

      {tab === 'systems' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('digital.newSystem')}
            </h2>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault()
                run('sys', async () => {
                  const s = await digitalApi.systems.create(sysForm)
                  setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.system', { ns: 'common' }), name: s.name }))
                  setSysForm({ name: '', systemKey: '', environment: 'PROD' })
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
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('digital.createSystem')}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Systems ({sysPage.total || systems.length})
            </h2>
            <ResponsiveDataList
              items={systems}
              empty="—"
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
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('digital.key')}</th>
                      <th>{t('digital.env')}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {systems.map((s) => (
                      <tr key={s.id}>
                        <td>{s.name}</td>
                        <td className="mono col-priority-low">{s.systemKey || '—'}</td>
                        <td>{s.environment}</td>
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
          </section>
        </div>
      )}

      {tab === 'data' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('digital.newData')}
            </h2>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault()
                run('data', async () => {
                  const d = await digitalApi.data.create({
                    ...dataForm,
                    retentionDays: dataForm.retentionDays || null,
                  })
                  setInfo(t('toast.namedStatus', { ns: 'common', what: t('labels.data', { ns: 'common' }), name: d.name, status: d.dataClass }))
                  setDataForm({ name: '', dataClass: 'INTERNAL', retentionDays: '365' })
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
                    onChange={(e) =>
                      setDataForm((f) => ({ ...f, retentionDays: e.target.value }))
                    }
                  />
                </label>
              </div>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('digital.createData')}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Data assets ({dataPage.total || dataItems.length})
            </h2>
            <ResponsiveDataList
              items={dataItems}
              empty="—"
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
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th>{t('digital.class')}</th>
                      <th className="col-priority-low">{t('digital.retention')}</th>
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
          </section>
        </div>
      )}

      {tab === 'solutions' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('digital.newSolution')}
            </h2>
            <form
              className="form-grid"
              onSubmit={(e) => {
                e.preventDefault()
                run('sol', async () => {
                  const s = await digitalApi.solutions.create({
                    ...solForm,
                    systemId: solForm.systemId || undefined,
                  })
                  setInfo(t('toast.namedStatus', { ns: 'common', what: t('labels.solution', { ns: 'common' }), name: s.name, status: s.lifecycleStatus }))
                  setSolForm({
                    name: '',
                    integrationType: 'OTHER',
                    systemId: '',
                    versionLabel: '',
                  })
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
              <div className="form-row-2">
                <label>
                  Type
                  <select
                    value={solForm.integrationType}
                    onChange={(e) =>
                      setSolForm((f) => ({ ...f, integrationType: e.target.value }))
                    }
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
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('digital.createSolution')}
              </button>
            </form>
            <h2 className="section-label">Solutions ({solPage.total || solutions.length})</h2>
            <ResponsiveDataList
              items={solutions}
              empty="—"
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
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.type', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {solutions.map((s) => (
                      <tr key={s.id}>
                        <td>{s.name}</td>
                        <td className="col-priority-low">{s.integrationType}</td>
                        <td>
                          <span className={statusClass(s.lifecycleStatus)}>
                            {s.lifecycleStatus}
                          </span>
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
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Notes
            </h2>
            <p className="muted">
              <strong>{t('digital.systemsList')}</strong> = platform/runtime. <strong>{t('digital.solutionsList')}</strong> = capability or
              integration (DIG1). Optional system link (DIG4). No money fields (DIG7).
            </p>
          </section>
        </div>
      )}

      {tab === 'changes' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('digital.newChange')}
            </h2>
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
                  setInfo(t('toast.namedStatus', { ns: 'common', what: t('labels.change', { ns: 'common' }), name: c.name, status: c.changeKind }))
                  setChgForm({ name: '', changeKind: 'CHANGE', riskLevel: 'LOW', cabNote: '' })
                  setSelectedChange(c)
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
            <h2 className="section-label">Changes ({chgPage.total || changes.length})</h2>
            <ResponsiveDataList
              items={changes}
              empty="—"
              selectedKey={selectedChange?.id}
              onSelect={(c) => setSelectedChange(c)}
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
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.kind', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {changes.map((c) => (
                      <tr
                        key={c.id}
                        className={selectedChange?.id === c.id ? 'row-selected' : 'row-click'}
                        onClick={() => setSelectedChange(c)}
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
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Change detail
            </h2>
            {!selectedChange && <p className="muted">{t('digital.selectChange')}</p>}
            {selectedChange && (
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
                <div className="toolbar">
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
                </div>
                <ContentRail
                  package="digital"
                  resource="ITChange"
                  id={selectedChange.id}
                  onChanged={async () =>
                    setChangeContentMap(await fetchContentMap('digital', 'ITChange', changes))
                  }
                />
              </>
            )}
          </section>
        </div>
      )}

      {tab === 'cmdb' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('digital.newCi')}
            </h2>
            <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
              Manual configuration items only — no discovery (DIG0).
            </p>
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
                  setInfo(t('toast.namedStatus', { ns: 'common', what: t('labels.ci', { ns: 'common' }), name: c.name, status: c.ciKind }))
                  setCiForm({
                    name: '',
                    ciKind: 'HOST',
                    hostname: '',
                    systemId: '',
                    solutionId: '',
                  })
                })
              }}
            >
              <label>
                Name *
                <input
                  required
                  value={ciForm.name}
                  onChange={(e) => setCiForm((f) => ({ ...f, name: e.target.value }))}
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
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('digital.createCi')}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Configuration items ({ciPage.total || cis.length})
            </h2>
            <ResponsiveDataList
              items={cis}
              empty="—"
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
                  render: (c) => (
                    <span className={statusClass(c.ciStatus)}>{c.ciStatus}</span>
                  ),
                },
                {
                  id: 'host',
                  priority: 'low',
                  render: (c) => c.hostname || '—',
                },
              ]}
              actions={(c) => (
                <ViewButton pkg="digital" resource="ConfigItem" record={c} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.kind', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('digital.host')}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
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
          </section>
        </div>
      )}

      {tab === 'support' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Open internal ticket
            </h2>
            <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
              Internal IT only (DIG3). Not customer after-sales.{' '}
              <Link to="/help/it-support">Help → IT Support</Link> is the dedicated help hub for the
              same tickets.
            </p>
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
                    await attachPendingContent(
                      'digital',
                      'ItTicket',
                      ticket.id,
                      pendingTicketContent,
                    )
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
                  setSelectedTicket(ticket)
                })
              }}
            >
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
            <h2 className="section-label">Tickets ({tixPage.total || tickets.length})</h2>
            <ResponsiveDataList
              items={tickets}
              empty="—"
              selectedKey={selectedTicket?.id}
              onSelect={(row) => setSelectedTicket(row)}
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
              actions={(row) => (
                <ViewButton pkg="digital" resource="ItTicket" record={row} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.number', { ns: 'common' })}</th>
                      <th>{t('wb.subject', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {tickets.map((row) => (
                      <tr
                        key={row.id}
                        className={
                          selectedTicket?.id === row.id ? 'row-selected' : 'row-click'
                        }
                        onClick={() => setSelectedTicket(row)}
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
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Ticket detail
            </h2>
            {!selectedTicket && <p className="muted">{t('digital.selectTicket')}</p>}
            {selectedTicket && (
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
                <div className="toolbar">
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
                </div>
                <ContentRail
                  package="digital"
                  resource="ItTicket"
                  id={selectedTicket.id}
                  onChanged={async () =>
                    setTicketContentMap(await fetchContentMap('digital', 'ItTicket', tickets))
                  }
                />
              </>
            )}
          </section>
        </div>
      )}
    </div>
  )
}
