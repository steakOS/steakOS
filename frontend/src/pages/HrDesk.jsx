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
  Users,
  RefreshCw,
  Plus,
  Check,
  UserPlus,
  Clock,
  Wallet,
  Target,
  FileText,
  Briefcase,
  UserCog,
} from 'lucide-react'
import { hrApi } from '../api/client'
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

const TAB_DEFS = [
  { id: 'files', labelKey: 'hr.tabs.files', icon: Users },
  { id: 'candidates', labelKey: 'hr.tabs.candidates', icon: UserPlus },
  { id: 'time', labelKey: 'hr.tabs.time', icon: Clock },
  { id: 'payroll', labelKey: 'hr.tabs.payroll', icon: Wallet },
  { id: 'plans', labelKey: 'hr.tabs.plans', icon: Briefcase },
  { id: 'goals', labelKey: 'hr.tabs.goals', icon: Target },
  { id: 'policies', labelKey: 'hr.tabs.policies', icon: FileText },
  { id: 'parties', labelKey: 'hr.tabs.parties', icon: UserCog },
]

/** Current calendar month as YYYY-MM (payroll period key). */
function currentPayrollPeriod() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return `${d.getFullYear()}-${m}`
}

/**
 * HR desk — same UI shell as Delivery desk (HR0–HR8).
 */
export default function HrDesk() {
  const { t } = useTranslation(['desks', 'common', 'content'])
  const { viewModal, ViewButton } = useRecordView()
  const TABS = useMemo(
    () => TAB_DEFS.map((d) => ({ ...d, label: t(d.labelKey) })),
    [t],
  )
  const [tab, setTab] = useState('files')
  const [files, setFiles] = useState([])
  const [candidates, setCandidates] = useState([])
  const [timeEntries, setTimeEntries] = useState([])
  const [payrolls, setPayrolls] = useState([])
  const [plans, setPlans] = useState([])
  const [goals, setGoals] = useState([])
  const [policies, setPolicies] = useState([])
  const [parties, setParties] = useState([])
  /** Dropdown options (not paged) — max 100 until typeahead. */
  const [fileOptions, setFileOptions] = useState([])
  const [selectedCandidate, setSelectedCandidate] = useState(null)
  const [selectedPayroll, setSelectedPayroll] = useState(null)
  const [filesPage, setFilesPage] = useState({ max: 25, offset: 0, total: 0 })
  const [candPage, setCandPage] = useState({ max: 25, offset: 0, total: 0 })
  const [timePage, setTimePage] = useState({ max: 25, offset: 0, total: 0 })
  const [payPage, setPayPage] = useState({ max: 25, offset: 0, total: 0 })
  const [planPage, setPlanPage] = useState({ max: 25, offset: 0, total: 0 })
  const [goalPage, setGoalPage] = useState({ max: 25, offset: 0, total: 0 })
  const [policyPage, setPolicyPage] = useState({ max: 25, offset: 0, total: 0 })
  const [partyPage, setPartyPage] = useState({ max: 25, offset: 0, total: 0 })
  const [candContentMap, setCandContentMap] = useState({})
  const [fileContentMap, setFileContentMap] = useState({})
  const [pendingCandContent, setPendingCandContent] = useState([])
  const [pendingFileContent, setPendingFileContent] = useState([])
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)

  const [fileForm, setFileForm] = useState({
    name: '',
    email: '',
    username: '',
    employmentKind: 'EMPLOYEE',
  })
  const [candForm, setCandForm] = useState({ name: '', email: '', roleLabel: '', source: '' })
  const [timeForm, setTimeForm] = useState({
    hrFileId: '',
    hours: '8',
    timeKind: 'WORK',
    name: '',
  })
  const [payForm, setPayForm] = useState(() => ({
    period: currentPayrollPeriod(),
    name: '',
  }))
  const [payLineForm, setPayLineForm] = useState({
    hrFileId: '',
    amountEur: '1000',
    amountSats: '',
  })
  const [planForm, setPlanForm] = useState({ name: '', plannedCount: '1', roleLabel: '' })
  const [goalForm, setGoalForm] = useState({ name: '', hrFileId: '', progress: '0' })
  const [policyForm, setPolicyForm] = useState({
    name: '',
    code: '',
    policyKind: 'OTHER',
    description: '',
  })
  const [partyForm, setPartyForm] = useState({
    name: '',
    partyKind: 'BUSINESS_PARTNER',
    username: '',
  })

  const loadAll = useCallback(async () => {
    const [f, fPick, c, t, p, pl, g, po, pa] = await Promise.all([
      hrApi.files.list({ max: filesPage.max, offset: filesPage.offset }),
      hrApi.files.list({ max: 100, offset: 0 }),
      hrApi.candidates.list({ max: candPage.max, offset: candPage.offset }),
      hrApi.time.list({ max: timePage.max, offset: timePage.offset }),
      hrApi.payrolls.list({ max: payPage.max, offset: payPage.offset }),
      hrApi.plans.list({ max: planPage.max, offset: planPage.offset }),
      hrApi.goals.list({ max: goalPage.max, offset: goalPage.offset }),
      hrApi.policies.list({ max: policyPage.max, offset: policyPage.offset }),
      hrApi.parties.list({ max: partyPage.max, offset: partyPage.offset }),
    ])
    const filesItems = f.items || []
    const candItems = c.items || []
    setFiles(filesItems)
    setFileOptions(fPick.items || [])
    setCandidates(candItems)
    setTimeEntries(t.items || [])
    setPayrolls(p.items || [])
    setPlans(pl.items || [])
    setGoals(g.items || [])
    setPolicies(po.items || [])
    setParties(pa.items || [])
    setFilesPage((pg) => ({
      ...pg,
      max: f.max ?? pg.max,
      offset: f.offset ?? pg.offset,
      total: f.total ?? filesItems.length,
    }))
    setCandPage((pg) => ({
      ...pg,
      max: c.max ?? pg.max,
      offset: c.offset ?? pg.offset,
      total: c.total ?? candItems.length,
    }))
    setTimePage((pg) => ({
      ...pg,
      max: t.max ?? pg.max,
      offset: t.offset ?? pg.offset,
      total: t.total ?? (t.items || []).length,
    }))
    setPayPage((pg) => ({
      ...pg,
      max: p.max ?? pg.max,
      offset: p.offset ?? pg.offset,
      total: p.total ?? (p.items || []).length,
    }))
    setPlanPage((pg) => ({
      ...pg,
      max: pl.max ?? pg.max,
      offset: pl.offset ?? pg.offset,
      total: pl.total ?? (pl.items || []).length,
    }))
    setGoalPage((pg) => ({
      ...pg,
      max: g.max ?? pg.max,
      offset: g.offset ?? pg.offset,
      total: g.total ?? (g.items || []).length,
    }))
    setPolicyPage((pg) => ({
      ...pg,
      max: po.max ?? pg.max,
      offset: po.offset ?? pg.offset,
      total: po.total ?? (po.items || []).length,
    }))
    setPartyPage((pg) => ({
      ...pg,
      max: pa.max ?? pg.max,
      offset: pa.offset ?? pg.offset,
      total: pa.total ?? (pa.items || []).length,
    }))
    const [fm, cm] = await Promise.all([
      fetchContentMap('human', 'HRFile', filesItems),
      fetchContentMap('human', 'Candidate', candItems),
    ])
    setFileContentMap(fm)
    setCandContentMap(cm)
  }, [
    filesPage.max,
    filesPage.offset,
    candPage.max,
    candPage.offset,
    timePage.max,
    timePage.offset,
    payPage.max,
    payPage.offset,
    planPage.max,
    planPage.offset,
    goalPage.max,
    goalPage.offset,
    policyPage.max,
    policyPage.offset,
    partyPage.max,
    partyPage.offset,
  ])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      await loadAll()
      if (selectedCandidate?.id) {
        setSelectedCandidate(await hrApi.candidates.get(selectedCandidate.id))
      }
      if (selectedPayroll?.id) {
        setSelectedPayroll(await hrApi.payrolls.get(selectedPayroll.id))
      }
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [loadAll, selectedCandidate?.id, selectedPayroll?.id])

  useEffect(() => {
    setLoading(true)
    loadAll()
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [loadAll])

  const statusClass = (s) => {
    if (['ACTIVE', 'HIRED', 'DONE', 'SUBMITTED', 'POSTED', 'READY'].includes(s)) {
      return 'badge badge-ok'
    }
    if (['EXITED', 'REJECTED', 'CANCELLED', 'CLOSED', 'WITHDRAWN'].includes(s)) {
      return 'badge badge-muted'
    }
    if (['SCREEN', 'OFFER', 'OPEN', 'ON_LEAVE'].includes(s)) return 'badge badge-warn'
    return 'badge badge-draft'
  }

  const onCreateFile = async (e) => {
    e.preventDefault()
    setBusy('file')
    setError(null)
    setInfo(null)
    try {
      const f = await hrApi.files.create({
        ...fileForm,
        username: fileForm.username || undefined,
      })
      if (pendingFileContent.length) {
        await attachPendingContent('human', 'HRFile', f.id, pendingFileContent)
        setPendingFileContent([])
      }
      setInfo(
        `HR file for ${f.name} · user ${f.username} (enabled: ${f.userEnabled ? 'yes' : 'no — deactivated'})`,
      )
      setFileForm({ name: '', email: '', username: '', employmentKind: 'EMPLOYEE' })
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateCand = async (e) => {
    e.preventDefault()
    setBusy('cand')
    setError(null)
    setInfo(null)
    try {
      const c = await hrApi.candidates.create(candForm)
      if (pendingCandContent.length) {
        await attachPendingContent('human', 'Candidate', c.id, pendingCandContent)
        setPendingCandContent([])
      }
      setInfo(t('toast.namedStatus', { ns: 'common', what: t('labels.customer', { ns: 'common' }), name: c.name, status: c.stage }))
      setCandForm({ name: '', email: '', roleLabel: '', source: '' })
      await loadAll()
      setSelectedCandidate(c)
      setTab('candidates')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runCand = async (action, stage) => {
    if (!selectedCandidate?.id) return
    setBusy(action)
    setError(null)
    setInfo(null)
    try {
      if (action === 'advance') {
        const c = await hrApi.candidates.advance(selectedCandidate.id, stage)
        setSelectedCandidate(c)
        setInfo(t('toast.namedStatus', { ns: 'common', what: '', name: c.name, status: c.stage }))
      } else if (action === 'hire') {
        const r = await hrApi.candidates.hire(selectedCandidate.id)
        setSelectedCandidate(r.candidate)
        setInfo(
          `Hired → HRFile #${r.hrFile?.id} · user ${r.hrFile?.username} (enabled: ${r.hrFile?.userEnabled ? 'yes' : 'no'})`,
        )
      } else if (action === 'reject') {
        const c = await hrApi.candidates.reject(selectedCandidate.id)
        setSelectedCandidate(c)
        setInfo(t('toast.rejected', { ns: 'common', name: c.name }))
      }
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateTime = async (e) => {
    e.preventDefault()
    setBusy('time')
    setError(null)
    setInfo(null)
    try {
      const entry = await hrApi.time.create(timeForm)
      setInfo(
        t('toast.timeLogged', {
          ns: 'common',
          hours: entry.hours,
          person: entry.hrFileName || entry.hrFileId,
        }),
      )
      setTimeForm({ hrFileId: '', hours: '8', timeKind: 'WORK', name: '' })
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreatePay = async (e) => {
    e.preventDefault()
    setBusy('pay')
    setError(null)
    setInfo(null)
    try {
      // Always send YYYY-MM — empty string must not become omitted/null on the server
      let period = (payForm.period || '').trim() || currentPayrollPeriod()
      if (period.length > 7) period = period.slice(0, 7)
      const p = await hrApi.payrolls.create({
        period,
        name: payForm.name?.trim() || undefined,
      })
      setInfo(t('toast.payrollOpened', { ns: 'common', period: p.period }))
      setPayForm({ period: currentPayrollPeriod(), name: '' })
      await loadAll()
      setSelectedPayroll(p)
      setTab('payroll')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onAddPayLine = async (e) => {
    e.preventDefault()
    if (!selectedPayroll?.id) return
    setBusy('payLine')
    setError(null)
    try {
      const existing = selectedPayroll._lines || []
      const lines = [
        ...existing,
        {
          hrFileId: payLineForm.hrFileId,
          amountEur: payLineForm.amountEur,
          amountSats: payLineForm.amountSats || undefined,
        },
      ]
      const r = await hrApi.payrolls.setLines(selectedPayroll.id, lines)
      setSelectedPayroll({ ...r.payroll, _lines: r.lines })
      setInfo(t('toast.linesSet', { ns: 'common', total: Number(r.payroll.totalEur).toFixed(2) }))
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const runPay = async (action) => {
    if (!selectedPayroll?.id) return
    setBusy(action)
    setError(null)
    setInfo(null)
    try {
      if (action === 'ready') {
        const p = await hrApi.payrolls.ready(selectedPayroll.id)
        setSelectedPayroll(p)
        setInfo(t('toast.payrollReady', { ns: 'common', period: p.period }))
      } else if (action === 'submit') {
        const r = await hrApi.payrolls.submit(selectedPayroll.id)
        setSelectedPayroll({ ...r.payroll, _lines: r.lines })
        setInfo(
          `Submitted ${r.payroll.period} → Finance (${(r.lines || []).length} lines; recognize/pay on Finance desk)`,
        )
      } else if (action === 'close') {
        const p = await hrApi.payrolls.close(selectedPayroll.id)
        setSelectedPayroll(p)
        setInfo(t('toast.closed', { ns: 'common', number: p.period }))
      }
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreatePlan = async (e) => {
    e.preventDefault()
    setBusy('plan')
    setError(null)
    try {
      const p = await hrApi.plans.create(planForm)
      setInfo(t('toast.planCreated', { ns: 'common', name: p.name, count: p.plannedCount }))
      setPlanForm({ name: '', plannedCount: '1', roleLabel: '' })
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateGoal = async (e) => {
    e.preventDefault()
    setBusy('goal')
    setError(null)
    try {
      const g = await hrApi.goals.create({
        ...goalForm,
        hrFileId: goalForm.hrFileId || undefined,
      })
      setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.goal', { ns: 'common' }), name: g.name }))
      setGoalForm({ name: '', hrFileId: '', progress: '0' })
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreatePolicy = async (e) => {
    e.preventDefault()
    setBusy('policy')
    setError(null)
    try {
      const p = await hrApi.policies.create(policyForm)
      setInfo(t('toast.namedCreated', { ns: 'common', what: t('labels.policy', { ns: 'common' }), name: p.name }))
      setPolicyForm({ name: '', code: '', policyKind: 'OTHER', description: '' })
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  const onCreateParty = async (e) => {
    e.preventDefault()
    setBusy('party')
    setError(null)
    try {
      const p = await hrApi.parties.create({
        ...partyForm,
        username: partyForm.username || undefined,
      })
      setInfo(
        `Party ${p.name} (${p.partyKind}) · user ${p.username} · no HRFile · enabled: ${p.userEnabled ? 'yes' : 'no'}`,
      )
      setPartyForm({ name: '', partyKind: 'BUSINESS_PARTNER', username: '' })
      await loadAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="page delivery-desk-page">
      <header className="page-header" style={{ marginBottom: '1.25rem' }}>
        <h1 className="page-title">
          <Users size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('hr.title')}
        </h1>
        <p className="page-sub">{t('hr.subtitle')}</p>
      </header>

      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      {viewModal}

      <div className="toolbar">
        <button type="button" className="btn" onClick={refresh} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common' })}
        </button>
        <ProcessHowItWorks processCode="HR_CANDIDATE_HIRE" />
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
        <Link className="btn btn-secondary" to="/finance/payroll">
          Finance payroll →
        </Link>
      </div>

      {tab === 'files' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('hr.newFile')}
            </h2>
            <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
              Creates/links a User (deactivated if new). Names encrypted at rest.
            </p>
            <form className="form-grid" onSubmit={onCreateFile}>
              <label>
                Name *
                <input
                  required
                  value={fileForm.name}
                  onChange={(e) => setFileForm((f) => ({ ...f, name: e.target.value }))}
                />
              </label>
              <div className="form-row-2">
                <label>
                  Email
                  <input
                    value={fileForm.email}
                    onChange={(e) => setFileForm((f) => ({ ...f, email: e.target.value }))}
                  />
                </label>
                <label>
                  Username (optional)
                  <input
                    value={fileForm.username}
                    onChange={(e) => setFileForm((f) => ({ ...f, username: e.target.value }))}
                    placeholder={t('wb.autoIfEmpty', { ns: 'common' })}
                  />
                </label>
              </div>
              <label>
                Kind
                <select
                  value={fileForm.employmentKind}
                  onChange={(e) =>
                    setFileForm((f) => ({ ...f, employmentKind: e.target.value }))
                  }
                >
                  {['EMPLOYEE', 'CONTRACTOR', 'TEMP_WITH_FILE', 'FOUNDER', 'OTHER'].map((k) => (
                    <option key={k} value={k}>
                      {k}
                    </option>
                  ))}
                </select>
              </label>
              <PendingContentComposer
                package="human"
                resource="HRFile"
                pending={pendingFileContent}
                onChange={setPendingFileContent}
              />
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('hr.createFile')}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              People ({filesPage.total})
            </h2>
            <ResponsiveDataList
              items={files}
              empty="—"
              loading={loading}
              fields={[
                { id: 'name', primary: true, render: (f) => f.name },
                {
                  id: 'user',
                  secondary: true,
                  render: (f) => (
                    <span className="mono">
                      {f.username}
                      {!f.userEnabled && (
                        <span className="badge badge-muted" style={{ marginLeft: 6 }}>
                          off
                        </span>
                      )}
                    </span>
                  ),
                },
                {
                  id: 'status',
                  meta: true,
                  render: (f) => (
                    <span className={statusClass(f.employmentStatus)}>{f.employmentStatus}</span>
                  ),
                },
                {
                  id: 'kind',
                  priority: 'low',
                  render: (f) => f.employmentKind,
                },
              ]}
              actions={(f) => <ViewButton pkg="human" resource="HRFile" record={f} />}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th>User</th>
                      <th className="col-priority-low">{t('wb.kind', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {files.map((f) => (
                      <tr key={f.id}>
                        <td>{f.name}</td>
                        <td className="mono">
                          {f.username}
                          {!f.userEnabled && (
                            <span className="badge badge-muted" style={{ marginLeft: 6 }}>
                              off
                            </span>
                          )}
                        </td>
                        <td className="col-priority-low">{f.employmentKind}</td>
                        <td className="col-priority-low">
                          <ContentChannelIcons summary={fileContentMap[f.id]} />
                        </td>
                        <td>
                          <span className={statusClass(f.employmentStatus)}>
                            {f.employmentStatus}
                          </span>
                        </td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="human" resource="HRFile" record={f} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={filesPage.total}
              max={filesPage.max}
              offset={filesPage.offset}
              disabled={loading}
              onOffset={(o) => setFilesPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setFilesPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'candidates' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('hr.newCandidate')}
            </h2>
            <form className="form-grid" onSubmit={onCreateCand}>
              <label>
                Name *
                <input
                  required
                  value={candForm.name}
                  onChange={(e) => setCandForm((f) => ({ ...f, name: e.target.value }))}
                />
              </label>
              <label>
                Email
                <input
                  value={candForm.email}
                  onChange={(e) => setCandForm((f) => ({ ...f, email: e.target.value }))}
                />
              </label>
              <div className="form-row-2">
                <label>
                  Role
                  <input
                    value={candForm.roleLabel}
                    onChange={(e) => setCandForm((f) => ({ ...f, roleLabel: e.target.value }))}
                  />
                </label>
                <label>
                  Source
                  <input
                    value={candForm.source}
                    onChange={(e) => setCandForm((f) => ({ ...f, source: e.target.value }))}
                  />
                </label>
              </div>
              <PendingContentComposer
                package="human"
                resource="Candidate"
                pending={pendingCandContent}
                onChange={setPendingCandContent}
                title={t('pending.titleCv', { ns: 'content' })}
                hint={t('pending.hintCv', { ns: 'content' })}
              />
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('hr.createCandidate')}
              </button>
            </form>
            <h2 className="section-label">Candidates ({candPage.total})</h2>
            <ResponsiveDataList
              items={candidates}
              empty="—"
              loading={loading}
              selectedKey={selectedCandidate?.id}
              onSelect={(c) => setSelectedCandidate(c)}
              fields={[
                { id: 'name', primary: true, render: (c) => c.name },
                {
                  id: 'role',
                  secondary: true,
                  render: (c) => c.roleLabel || '—',
                },
                {
                  id: 'stage',
                  meta: true,
                  render: (c) => <span className={statusClass(c.stage)}>{c.stage}</span>,
                },
              ]}
              actions={(c) => <ViewButton pkg="human" resource="Candidate" record={c} />}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th>{t('wb.role', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.content', { ns: 'common' })}</th>
                      <th>{t('hr.stage')}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {candidates.map((c) => (
                      <tr
                        key={c.id}
                        className={
                          selectedCandidate?.id === c.id ? 'row-selected' : 'row-click'
                        }
                        onClick={() => setSelectedCandidate(c)}
                      >
                        <td>{c.name}</td>
                        <td>{c.roleLabel || '—'}</td>
                        <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                          <ContentChannelIcons summary={candContentMap[c.id]} />
                        </td>
                        <td>
                          <span className={statusClass(c.stage)}>{c.stage}</span>
                        </td>
                        <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                          <ViewButton pkg="human" resource="Candidate" record={c} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={candPage.total}
              max={candPage.max}
              offset={candPage.offset}
              disabled={loading}
              onOffset={(o) => setCandPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setCandPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Candidate detail
            </h2>
            {!selectedCandidate && <p className="muted">{t('hr.selectCandidate')}</p>}
            {selectedCandidate && (
              <>
                <div className="inv-detail-hero">
                  <div>
                    <div className="about-display" style={{ fontSize: '1.35rem' }}>
                      {selectedCandidate.name}
                    </div>
                    <div className="muted">
                      <span className={statusClass(selectedCandidate.stage)}>
                        {selectedCandidate.stage}
                      </span>
                      {selectedCandidate.hrFileId
                        ? ` · HRFile #${selectedCandidate.hrFileId}`
                        : ''}
                    </div>
                  </div>
                </div>
                <div className="toolbar">
                  {selectedCandidate.stage === 'APPLIED' && (
                    <button
                      type="button"
                      className="btn"
                      disabled={!!busy}
                      onClick={() => runCand('advance', 'SCREEN')}
                    >
                      → Screen
                    </button>
                  )}
                  {['APPLIED', 'SCREEN'].includes(selectedCandidate.stage) && (
                    <button
                      type="button"
                      className="btn"
                      disabled={!!busy}
                      onClick={() => runCand('advance', 'OFFER')}
                    >
                      → Offer
                    </button>
                  )}
                  {!['HIRED', 'REJECTED', 'WITHDRAWN'].includes(selectedCandidate.stage) && (
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={!!busy}
                      onClick={() => runCand('hire')}
                    >
                      <Check size={16} /> Hire → HRFile + User
                    </button>
                  )}
                  {!['HIRED', 'REJECTED'].includes(selectedCandidate.stage) && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={!!busy}
                      onClick={() => runCand('reject')}
                    >
                      Reject
                    </button>
                  )}
                </div>
                <ContentRail
                  package="human"
                  resource="Candidate"
                  id={selectedCandidate.id}
                  onChanged={async () =>
                    setCandContentMap(await fetchContentMap('human', 'Candidate', candidates))
                  }
                />
              </>
            )}
          </section>
        </div>
      )}

      {tab === 'time' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Log time
            </h2>
            <form className="form-grid" onSubmit={onCreateTime}>
              <label>
                Person (HR file) *
                <select
                  required
                  value={timeForm.hrFileId}
                  onChange={(e) => setTimeForm((f) => ({ ...f, hrFileId: e.target.value }))}
                >
                  <option value="">— select —</option>
                  {fileOptions
                    .filter((f) => f.employmentStatus === 'ACTIVE')
                    .map((f) => (
                      <option key={f.id} value={f.id}>
                        {f.name}
                      </option>
                    ))}
                </select>
              </label>
              <div className="form-row-2">
                <label>
                  Hours *
                  <input
                    required
                    value={timeForm.hours}
                    onChange={(e) => setTimeForm((f) => ({ ...f, hours: e.target.value }))}
                  />
                </label>
                <label>
                  Kind
                  <select
                    value={timeForm.timeKind}
                    onChange={(e) => setTimeForm((f) => ({ ...f, timeKind: e.target.value }))}
                  >
                    {['WORK', 'LEAVE', 'OTHER'].map((k) => (
                      <option key={k} value={k}>
                        {k}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> Post time
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Entries ({timePage.total})
            </h2>
            <ResponsiveDataList
              items={timeEntries}
              empty="—"
              loading={loading}
              fields={[
                {
                  id: 'name',
                  primary: true,
                  render: (row) => row.hrFileName || row.hrFileId,
                },
                {
                  id: 'hours',
                  secondary: true,
                  render: (row) => row.hours,
                },
                {
                  id: 'kind',
                  meta: true,
                  render: (row) => row.timeKind,
                },
              ]}
              actions={(row) => (
                <ViewButton pkg="human" resource="TimeTrack" record={row} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th>{t('wb.hours', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.kind', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {timeEntries.map((row) => (
                      <tr key={row.id}>
                        <td>{row.hrFileName || row.hrFileId}</td>
                        <td>{row.hours}</td>
                        <td className="col-priority-low">{row.timeKind}</td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="human" resource="TimeTrack" record={row} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={timePage.total}
              max={timePage.max}
              offset={timePage.offset}
              disabled={loading}
              onOffset={(o) => setTimePage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setTimePage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'payroll' && (
        <div className="two-col finance-tx-grid">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Open period
            </h2>
            <form className="form-grid" onSubmit={onCreatePay}>
              <label>
                Period (YYYY-MM) *
                <input
                  type="month"
                  required
                  value={payForm.period}
                  onChange={(e) => setPayForm((f) => ({ ...f, period: e.target.value }))}
                />
              </label>
              <label>
                Label (optional)
                <input
                  value={payForm.name}
                  onChange={(e) => setPayForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder={`Payroll ${payForm.period || currentPayrollPeriod()}`}
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> Open payroll
              </button>
            </form>
            <h2 className="section-label">Periods ({payPage.total})</h2>
            <ResponsiveDataList
              items={payrolls}
              empty="—"
              loading={loading}
              selectedKey={selectedPayroll?.id}
              onSelect={(p) => setSelectedPayroll(p)}
              fields={[
                {
                  id: 'period',
                  primary: true,
                  render: (p) => <span className="mono">{p.period}</span>,
                },
                {
                  id: 'status',
                  secondary: true,
                  render: (p) => (
                    <span className={statusClass(p.payrollStatus)}>{p.payrollStatus}</span>
                  ),
                },
                {
                  id: 'eur',
                  meta: true,
                  render: (p) => Number(p.totalEur || 0).toFixed(2),
                },
              ]}
              actions={(p) => <ViewButton pkg="human" resource="Payroll" record={p} />}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.period', { ns: 'common' })}</th>
                      <th>EUR</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {payrolls.map((p) => (
                      <tr
                        key={p.id}
                        className={selectedPayroll?.id === p.id ? 'row-selected' : 'row-click'}
                        onClick={() => setSelectedPayroll(p)}
                      >
                        <td className="mono">{p.period}</td>
                        <td>{Number(p.totalEur || 0).toFixed(2)}</td>
                        <td>
                          <span className={statusClass(p.payrollStatus)}>{p.payrollStatus}</span>
                        </td>
                        <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                          <ViewButton pkg="human" resource="Payroll" record={p} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={payPage.total}
              max={payPage.max}
              offset={payPage.offset}
              disabled={loading}
              onOffset={(o) => setPayPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setPayPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Payroll detail
            </h2>
            {!selectedPayroll && <p className="muted">{t('hr.selectPeriod')}</p>}
            {selectedPayroll && (
              <>
                <div className="inv-detail-hero">
                  <div>
                    <div className="about-display" style={{ fontSize: '1.35rem' }}>
                      {selectedPayroll.period}
                    </div>
                    <div className="muted">
                      <span className={statusClass(selectedPayroll.payrollStatus)}>
                        {selectedPayroll.payrollStatus}
                      </span>
                    </div>
                  </div>
                  <div className="inv-amount">
                    {Number(selectedPayroll.totalEur || 0).toFixed(2)} EUR
                    {selectedPayroll.totalSats != null && (
                      <div className="muted mono" style={{ fontSize: '0.8rem' }}>
                        {selectedPayroll.totalSats} sats
                      </div>
                    )}
                  </div>
                </div>
                {['OPEN', 'READY'].includes(selectedPayroll.payrollStatus) && (
                  <form className="form-grid" onSubmit={onAddPayLine}>
                    <h3 className="section-label">{t('hr.setLines')}</h3>
                    <label>
                      Person *
                      <select
                        required
                        value={payLineForm.hrFileId}
                        onChange={(e) =>
                          setPayLineForm((f) => ({ ...f, hrFileId: e.target.value }))
                        }
                      >
                        <option value="">—</option>
                        {fileOptions.map((f) => (
                          <option key={f.id} value={f.id}>
                            {f.name}
                          </option>
                        ))}
                      </select>
                    </label>
                    <div className="form-row-2">
                      <label>
                        Amount EUR
                        <input
                          value={payLineForm.amountEur}
                          onChange={(e) =>
                            setPayLineForm((f) => ({ ...f, amountEur: e.target.value }))
                          }
                        />
                      </label>
                      <label>
                        Sats
                        <input
                          value={payLineForm.amountSats}
                          onChange={(e) =>
                            setPayLineForm((f) => ({ ...f, amountSats: e.target.value }))
                          }
                        />
                      </label>
                    </div>
                    <button type="submit" className="btn" disabled={!!busy}>
                      Set single line
                    </button>
                  </form>
                )}
                <div className="toolbar">
                  {selectedPayroll.payrollStatus === 'OPEN' && (
                    <button
                      type="button"
                      className="btn"
                      disabled={!!busy}
                      onClick={() => runPay('ready')}
                    >
                      Mark ready
                    </button>
                  )}
                  {['OPEN', 'READY'].includes(selectedPayroll.payrollStatus) && (
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={!!busy}
                      onClick={() => runPay('submit')}
                    >
                      Submit → Finance
                    </button>
                  )}
                  {selectedPayroll.payrollStatus !== 'CLOSED' && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={!!busy}
                      onClick={() => runPay('close')}
                    >
                      Close
                    </button>
                  )}
                </div>
              </>
            )}
          </section>
        </div>
      )}

      {tab === 'plans' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Headcount plan
            </h2>
            <form className="form-grid" onSubmit={onCreatePlan}>
              <label>
                Name *
                <input
                  required
                  value={planForm.name}
                  onChange={(e) => setPlanForm((f) => ({ ...f, name: e.target.value }))}
                />
              </label>
              <div className="form-row-2">
                <label>
                  Count
                  <input
                    value={planForm.plannedCount}
                    onChange={(e) =>
                      setPlanForm((f) => ({ ...f, plannedCount: e.target.value }))
                    }
                  />
                </label>
                <label>
                  Role
                  <input
                    value={planForm.roleLabel}
                    onChange={(e) => setPlanForm((f) => ({ ...f, roleLabel: e.target.value }))}
                  />
                </label>
              </div>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('hr.createPlan')}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Plans ({planPage.total})
            </h2>
            <ResponsiveDataList
              items={plans}
              empty="—"
              loading={loading}
              fields={[
                { id: 'name', primary: true, render: (p) => p.name },
                {
                  id: 'role',
                  secondary: true,
                  render: (p) => p.roleLabel || '—',
                },
                {
                  id: 'count',
                  meta: true,
                  render: (p) => p.plannedCount,
                },
              ]}
              actions={(p) => (
                <ViewButton pkg="human" resource="HeadcountPlan" record={p} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th>{t('hr.count')}</th>
                      <th className="col-priority-low">{t('wb.role', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {plans.map((p) => (
                      <tr key={p.id}>
                        <td>{p.name}</td>
                        <td>{p.plannedCount}</td>
                        <td className="col-priority-low">{p.roleLabel || '—'}</td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="human" resource="HeadcountPlan" record={p} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={planPage.total}
              max={planPage.max}
              offset={planPage.offset}
              disabled={loading}
              onOffset={(o) => setPlanPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setPlanPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'goals' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              New goal
            </h2>
            <form className="form-grid" onSubmit={onCreateGoal}>
              <label>
                Name *
                <input
                  required
                  value={goalForm.name}
                  onChange={(e) => setGoalForm((f) => ({ ...f, name: e.target.value }))}
                />
              </label>
              <label>
                Owner (HR file)
                <select
                  value={goalForm.hrFileId}
                  onChange={(e) => setGoalForm((f) => ({ ...f, hrFileId: e.target.value }))}
                >
                  <option value="">— optional —</option>
                  {fileOptions.map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.name}
                    </option>
                  ))}
                </select>
              </label>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('hr.createGoal')}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Goals ({goalPage.total})
            </h2>
            <ResponsiveDataList
              items={goals}
              empty="—"
              loading={loading}
              fields={[
                { id: 'name', primary: true, render: (g) => g.name },
                {
                  id: 'owner',
                  secondary: true,
                  render: (g) => g.hrFileName || '—',
                },
                {
                  id: 'status',
                  meta: true,
                  render: (g) => (
                    <span className={statusClass(g.goalStatus)}>{g.goalStatus}</span>
                  ),
                },
                {
                  id: 'progress',
                  priority: 'low',
                  render: (g) => g.progress,
                },
              ]}
              actions={(g) => <ViewButton pkg="human" resource="Goal" record={g} />}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('hr.owner')}</th>
                      <th className="col-priority-low">%</th>
                      <th>{t('wb.status', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {goals.map((g) => (
                      <tr key={g.id}>
                        <td>{g.name}</td>
                        <td className="col-priority-low">{g.hrFileName || '—'}</td>
                        <td className="col-priority-low">{g.progress}</td>
                        <td>
                          <span className={statusClass(g.goalStatus)}>{g.goalStatus}</span>
                        </td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="human" resource="Goal" record={g} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={goalPage.total}
              max={goalPage.max}
              offset={goalPage.offset}
              disabled={loading}
              onOffset={(o) => setGoalPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setGoalPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'policies' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              New policy
            </h2>
            <form className="form-grid" onSubmit={onCreatePolicy}>
              <label>
                Name *
                <input
                  required
                  value={policyForm.name}
                  onChange={(e) => setPolicyForm((f) => ({ ...f, name: e.target.value }))}
                />
              </label>
              <div className="form-row-2">
                <label>
                  Code
                  <input
                    value={policyForm.code}
                    onChange={(e) => setPolicyForm((f) => ({ ...f, code: e.target.value }))}
                  />
                </label>
                <label>
                  Kind
                  <select
                    value={policyForm.policyKind}
                    onChange={(e) =>
                      setPolicyForm((f) => ({ ...f, policyKind: e.target.value }))
                    }
                  >
                    {['LEAVE', 'CONDUCT', 'SAFETY', 'OTHER'].map((k) => (
                      <option key={k} value={k}>
                        {k}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <label>
                Text
                <textarea
                  rows={3}
                  value={policyForm.description}
                  onChange={(e) =>
                    setPolicyForm((f) => ({ ...f, description: e.target.value }))
                  }
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> {t('hr.createPolicy')}
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Policies ({policyPage.total})
            </h2>
            <ResponsiveDataList
              items={policies}
              empty="—"
              loading={loading}
              fields={[
                { id: 'name', primary: true, render: (p) => p.name },
                {
                  id: 'kind',
                  secondary: true,
                  render: (p) => p.policyKind,
                },
                {
                  id: 'code',
                  meta: true,
                  render: (p) => <span className="mono">{p.code || '—'}</span>,
                },
              ]}
              actions={(p) => <ViewButton pkg="human" resource="Policy" record={p} />}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.code', { ns: 'common' })}</th>
                      <th>{t('wb.kind', { ns: 'common' })}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {policies.map((p) => (
                      <tr key={p.id}>
                        <td>{p.name}</td>
                        <td className="mono col-priority-low">{p.code || '—'}</td>
                        <td>{p.policyKind}</td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="human" resource="Policy" record={p} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={policyPage.total}
              max={policyPage.max}
              offset={policyPage.offset}
              disabled={loading}
              onOffset={(o) => setPolicyPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setPolicyPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}

      {tab === 'parties' && (
        <div className="two-col finance-tx-grid list-first-narrow">
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Provision party (no HR file)
            </h2>
            <p className="muted" style={{ fontSize: '0.85rem', marginTop: 0 }}>
              Business partners, external workers, students, temps — User access without full
              employment file. New Users deactivated.
            </p>
            <form className="form-grid" onSubmit={onCreateParty}>
              <label>
                Name *
                <input
                  required
                  value={partyForm.name}
                  onChange={(e) => setPartyForm((f) => ({ ...f, name: e.target.value }))}
                />
              </label>
              <label>
                Kind
                <select
                  value={partyForm.partyKind}
                  onChange={(e) => setPartyForm((f) => ({ ...f, partyKind: e.target.value }))}
                >
                  {[
                    'BUSINESS_PARTNER',
                    'EXTERNAL_WORKER',
                    'STUDENT',
                    'TEMPORARY',
                    'OTHER',
                  ].map((k) => (
                    <option key={k} value={k}>
                      {k}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Username (optional)
                <input
                  value={partyForm.username}
                  onChange={(e) => setPartyForm((f) => ({ ...f, username: e.target.value }))}
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={!!busy}>
                <Plus size={16} /> Provision party
              </button>
            </form>
          </section>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              Parties ({partyPage.total})
            </h2>
            <ResponsiveDataList
              items={parties}
              empty="—"
              loading={loading}
              fields={[
                { id: 'name', primary: true, render: (p) => p.name },
                {
                  id: 'kind',
                  secondary: true,
                  render: (p) => p.partyKind,
                },
                {
                  id: 'user',
                  meta: true,
                  render: (p) => (
                    <span className="mono">
                      {p.username}
                      {!p.userEnabled && (
                        <span className="badge badge-muted" style={{ marginLeft: 6 }}>
                          off
                        </span>
                      )}
                    </span>
                  ),
                },
              ]}
              actions={(p) => (
                <ViewButton pkg="human" resource="PartyAccess" record={p} />
              )}
            >
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('wb.name', { ns: 'common' })}</th>
                      <th className="col-priority-low">{t('wb.kind', { ns: 'common' })}</th>
                      <th>User</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {parties.map((p) => (
                      <tr key={p.id}>
                        <td>{p.name}</td>
                        <td className="col-priority-low">{p.partyKind}</td>
                        <td className="mono">
                          {p.username}
                          {!p.userEnabled && (
                            <span className="badge badge-muted" style={{ marginLeft: 6 }}>
                              off
                            </span>
                          )}
                        </td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <ViewButton pkg="human" resource="PartyAccess" record={p} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ResponsiveDataList>
            <ListPager
              total={partyPage.total}
              max={partyPage.max}
              offset={partyPage.offset}
              disabled={loading}
              onOffset={(o) => setPartyPage((pg) => ({ ...pg, offset: o }))}
              onMax={(m) => setPartyPage((pg) => ({ ...pg, max: m, offset: 0 }))}
            />
          </section>
        </div>
      )}
    </div>
  )
}
