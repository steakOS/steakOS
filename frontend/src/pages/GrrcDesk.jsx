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

import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ShieldAlert, RefreshCw, Plus } from 'lucide-react'
import { grrcApi } from '../api/client'

const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const LIKELIHOODS = ['LOW', 'MEDIUM', 'HIGH']
const ITEM_KINDS = ['POLICY', 'CONTROL', 'OBLIGATION']
const COMP_STATUSES = ['DRAFT', 'ACTIVE', 'RETIRED']

const emptyRisk = () => ({
  name: '',
  description: '',
  severity: 'MEDIUM',
  likelihood: 'MEDIUM',
  ownerName: '',
  treatmentNote: '',
  status: 'OPEN',
})
const emptyCompliance = () => ({
  name: '',
  description: '',
  itemKind: 'POLICY',
  status: 'DRAFT',
  ownerName: '',
  effectiveFrom: '',
})

function isSilverGate(e) {
  if (!e || e.status !== 403) return false
  const code = e.code || e.body?.code || ''
  return code === 'GRRC_REQUIRES_SILVER' || /REQUIRES_SILVER/i.test(code)
}

function toDateInput(v) {
  if (v == null || v === '') return ''
  const d = typeof v === 'number' ? new Date(v) : new Date(v)
  if (Number.isNaN(d.getTime())) return String(v).slice(0, 10)
  return d.toISOString().slice(0, 10)
}

function statusClass(s) {
  if (['ACTIVE', 'CLOSED', 'ACCEPTED'].includes(s)) return 'badge badge-ok'
  if (['RETIRED', 'INACTIVE'].includes(s)) return 'badge badge-muted'
  if (['OPEN', 'MITIGATING', 'DRAFT'].includes(s)) return 'badge badge-warn'
  return 'badge badge-draft'
}

/**
 * GRRCBasics desk (G7) — risk + compliance registers. Silver pack / Iron eval.
 */
export default function GrrcDesk() {
  const { t } = useTranslation(['desks', 'common'])
  const [tab, setTab] = useState('risks')
  const [risks, setRisks] = useState([])
  const [compliance, setCompliance] = useState([])
  const [riskTotal, setRiskTotal] = useState(0)
  const [compTotal, setCompTotal] = useState(0)
  const [selected, setSelected] = useState(null)
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState({})
  const [riskForm, setRiskForm] = useState(emptyRisk)
  const [compForm, setCompForm] = useState(emptyCompliance)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [silverGate, setSilverGate] = useState(false)
  const [closeNote, setCloseNote] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [r, c] = await Promise.all([
        grrcApi.risks.list({ max: 50, offset: 0 }),
        grrcApi.compliance.list({ max: 50, offset: 0 }),
      ])
      setRisks(r?.items || [])
      setRiskTotal(r?.total ?? (r?.items || []).length)
      setCompliance(c?.items || [])
      setCompTotal(c?.total ?? (c?.items || []).length)
      setSilverGate(false)
    } catch (e) {
      setRisks([])
      setCompliance([])
      setRiskTotal(0)
      setCompTotal(0)
      if (isSilverGate(e)) {
        setSilverGate(true)
        setError(null)
      } else {
        setSilverGate(false)
        setError(e.message || String(e))
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])
  useEffect(() => {
    setSelected(null)
    setEditing(false)
    setCloseNote('')
  }, [tab])

  const run = async (fn) => {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      await fn()
      await load()
    } catch (e) {
      if (isSilverGate(e)) {
        setSilverGate(true)
        setError(null)
      } else setError(e.message || String(e))
    } finally {
      setBusy(null)
    }
  }

  const selectRow = (row) => {
    setSelected(row)
    setEditing(false)
    setCloseNote(row.treatmentNote || '')
  }

  const confirmDelete = (label) =>
    window.confirm(
      t('actions.confirmDelete', { ns: 'common', label, defaultValue: `Delete ${label}?` }),
    )

  const riskFields = (form, setForm) => (
    <>
      <label>
        {t('grrc.fieldName', { defaultValue: 'Name' })} *
        <input
          required
          value={form.name || ''}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          {t('grrc.fieldSeverity', { defaultValue: 'Severity' })}
          <select
            value={form.severity || 'MEDIUM'}
            onChange={(e) => setForm((f) => ({ ...f, severity: e.target.value }))}
          >
            {SEVERITIES.map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('grrc.fieldLikelihood', { defaultValue: 'Likelihood' })}
          <select
            value={form.likelihood || 'MEDIUM'}
            onChange={(e) => setForm((f) => ({ ...f, likelihood: e.target.value }))}
          >
            {LIKELIHOODS.map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label>
        {t('grrc.fieldOwner', { defaultValue: 'Owner' })}
        <input
          value={form.ownerName || ''}
          onChange={(e) => setForm((f) => ({ ...f, ownerName: e.target.value }))}
        />
      </label>
      <label>
        {t('grrc.fieldDescription', { defaultValue: 'Description' })}
        <textarea
          rows={2}
          value={form.description || ''}
          onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
      <label>
        {t('grrc.fieldTreatment', { defaultValue: 'Treatment note' })}
        <textarea
          rows={2}
          value={form.treatmentNote || ''}
          onChange={(e) => setForm((f) => ({ ...f, treatmentNote: e.target.value }))}
        />
      </label>
    </>
  )

  const complianceFields = (form, setForm) => (
    <>
      <label>
        {t('grrc.fieldName', { defaultValue: 'Name' })} *
        <input
          required
          value={form.name || ''}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          {t('grrc.fieldKind', { defaultValue: 'Kind' })}
          <select
            value={form.itemKind || 'POLICY'}
            onChange={(e) => setForm((f) => ({ ...f, itemKind: e.target.value }))}
          >
            {ITEM_KINDS.map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('grrc.fieldStatus', { defaultValue: 'Status' })}
          <select
            value={form.status || 'DRAFT'}
            onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
          >
            {COMP_STATUSES.map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="form-row-2">
        <label>
          {t('grrc.fieldOwner', { defaultValue: 'Owner' })}
          <input
            value={form.ownerName || ''}
            onChange={(e) => setForm((f) => ({ ...f, ownerName: e.target.value }))}
          />
        </label>
        <label>
          {t('grrc.fieldEffectiveFrom', { defaultValue: 'Effective from' })}
          <input
            type="date"
            value={form.effectiveFrom || ''}
            onChange={(e) => setForm((f) => ({ ...f, effectiveFrom: e.target.value }))}
          />
        </label>
      </div>
      <label>
        {t('grrc.fieldDescription', { defaultValue: 'Description' })}
        <textarea
          rows={2}
          value={form.description || ''}
          onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
    </>
  )

  const items = tab === 'risks' ? risks : compliance
  const total = tab === 'risks' ? riskTotal : compTotal

  const listTable = (cols, emptyKey, emptyDefault) =>
    items.length === 0 ? (
      <p className="muted">{t(emptyKey, { defaultValue: emptyDefault })}</p>
    ) : (
      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              {cols.map((c) => (
                <th key={c.key}>{c.label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {items.map((row) => (
              <tr
                key={row.id}
                className={selected?.id === row.id ? 'row-selected' : 'row-click'}
                onClick={() => selectRow(row)}
              >
                {cols.map((c) => (
                  <td key={c.key}>{c.render(row)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )

  return (
    <div className="page">
      <header className="page-header" style={{ marginBottom: '1.25rem' }}>
        <h1 className="page-title">
          <ShieldAlert size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('grrc.title', { defaultValue: 'GRRC' })}
        </h1>
        <p className="page-sub">
          {t('grrc.subtitle', {
            defaultValue:
              'Governance, risk, remediation, and compliance — Basics register. Silver pack / Iron eval.',
          })}
        </p>
      </header>

      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      {silverGate && (
        <section className="card" style={{ marginBottom: '1rem' }}>
          <strong>{t('grrc.silverRequired', { defaultValue: 'Silver pack required' })}</strong>
          <p className="muted" style={{ margin: '0.5rem 0 0' }}>
            {t('grrc.silverRequiredBody', {
              defaultValue:
                'GRRC is part of the Silver pack. Upgrade this company to Silver (or use Iron eval) to open the risk and compliance registers.',
            })}
          </p>
        </section>
      )}

      <div className="toolbar">
        <button type="button" className="btn" onClick={load} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          {['risks', 'compliance'].map((id) => (
            <button
              key={id}
              type="button"
              className={`btn ${tab === id ? 'btn-primary' : 'btn-ghost'}`}
              onClick={() => setTab(id)}
            >
              {t(`grrc.${id}`, { defaultValue: id === 'risks' ? 'Risks' : 'Compliance' })}
            </button>
          ))}
        </div>
      </div>

      {loading && !error && !silverGate && (
        <p className="muted">{t('grrc.loading', { defaultValue: 'Loading…' })}</p>
      )}

      {!silverGate && tab === 'risks' && (
        <div className="two-col finance-tx-grid">
          <div>
            <section className="card">
              <h2 className="section-label" style={{ marginTop: 0 }}>
                {t('grrc.newRisk', { defaultValue: 'New risk' })}
              </h2>
              <form
                className="form-grid"
                onSubmit={(e) => {
                  e.preventDefault()
                  run(async () => {
                    const created = await grrcApi.risks.create(riskForm)
                    setInfo(
                      t('toast.namedCreated', {
                        ns: 'common',
                        what: t('grrc.riskLabel', { defaultValue: 'Risk' }),
                        name: created.name,
                      }),
                    )
                    setRiskForm(emptyRisk())
                    setSelected(created)
                  })
                }}
              >
                {riskFields(riskForm, setRiskForm)}
                <button type="submit" className="btn btn-primary" disabled={!!busy}>
                  <Plus size={16} /> {t('grrc.createRisk', { defaultValue: 'Create risk' })}
                </button>
              </form>
            </section>
            <section className="card">
              <h2 className="section-label" style={{ marginTop: 0 }}>
                {t('grrc.risksList', { defaultValue: 'Risks' })} ({total})
              </h2>
              {listTable(
                [
                  {
                    key: 'name',
                    label: t('grrc.colName', { defaultValue: 'Name' }),
                    render: (r) => r.name || `#${r.id}`,
                  },
                  {
                    key: 'status',
                    label: t('grrc.colStatus', { defaultValue: 'Status' }),
                    render: (r) => (
                      <span className={statusClass(r.status)}>{r.status || '—'}</span>
                    ),
                  },
                  {
                    key: 'sev',
                    label: t('grrc.colSeverity', { defaultValue: 'Severity' }),
                    render: (r) => r.severity || '—',
                  },
                ],
                'grrc.emptyRisks',
                'No risks yet. Add one above to start the register.',
              )}
            </section>
          </div>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('grrc.riskDetail', { defaultValue: 'Risk detail' })}
            </h2>
            {!selected && (
              <p className="muted">
                {t('grrc.selectRisk', { defaultValue: 'Select a risk from the list.' })}
              </p>
            )}
            {selected && !editing && (
              <>
                <div className="inv-detail-hero">
                  <div>
                    <div className="about-display" style={{ fontSize: '1.25rem' }}>
                      {selected.name}
                    </div>
                    <div className="muted">
                      <span className={statusClass(selected.status)}>{selected.status}</span>
                      {' · '}
                      {selected.severity} / {selected.likelihood}
                      {selected.ownerName ? ` · ${selected.ownerName}` : ''}
                    </div>
                  </div>
                </div>
                {selected.description && <p>{selected.description}</p>}
                {selected.status !== 'CLOSED' && (
                  <label>
                    {t('grrc.fieldTreatment', { defaultValue: 'Treatment note' })}
                    <textarea
                      rows={2}
                      value={closeNote}
                      onChange={(e) => setCloseNote(e.target.value)}
                    />
                  </label>
                )}
                {selected.status === 'CLOSED' && selected.treatmentNote && (
                  <p className="muted">
                    <strong>{t('grrc.fieldTreatment', { defaultValue: 'Treatment note' })}:</strong>{' '}
                    {selected.treatmentNote}
                  </p>
                )}
                <div className="toolbar">
                  <button
                    type="button"
                    className="btn"
                    disabled={!!busy}
                    onClick={() => {
                      setEditForm({
                        name: selected.name || '',
                        description: selected.description || '',
                        severity: selected.severity || 'MEDIUM',
                        likelihood: selected.likelihood || 'MEDIUM',
                        ownerName: selected.ownerName || '',
                        treatmentNote: selected.treatmentNote || '',
                        status: selected.status || 'OPEN',
                      })
                      setEditing(true)
                    }}
                  >
                    {t('actions.edit', { ns: 'common', defaultValue: 'Edit' })}
                  </button>
                  {selected.status !== 'CLOSED' && (
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={!!busy}
                      onClick={() =>
                        run(async () => {
                          const closed = await grrcApi.risks.close(selected.id, {
                            treatmentNote: closeNote,
                          })
                          setInfo(
                            t('toast.closedName', {
                              ns: 'common',
                              name: closed.name || selected.name,
                            }),
                          )
                          setSelected(closed)
                        })
                      }
                    >
                      {t('grrc.closeRisk', { defaultValue: 'Close risk' })}
                    </button>
                  )}
                  <button
                    type="button"
                    className="btn btn-ghost"
                    disabled={!!busy}
                    onClick={() => {
                      if (!confirmDelete(selected.name)) return
                      run(async () => {
                        await grrcApi.risks.delete(selected.id)
                        setSelected(null)
                        setInfo(
                          t('grrc.deleted', {
                            defaultValue: 'Deleted {{name}}',
                            name: selected.name,
                          }),
                        )
                      })
                    }}
                  >
                    {t('actions.delete', { ns: 'common', defaultValue: 'Delete' })}
                  </button>
                </div>
              </>
            )}
            {selected && editing && (
              <form
                className="form-grid"
                onSubmit={(e) => {
                  e.preventDefault()
                  run(async () => {
                    const updated = await grrcApi.risks.update(selected.id, editForm)
                    setSelected(updated)
                    setEditing(false)
                    setInfo(
                      t('toast.namedStatus', {
                        ns: 'common',
                        what: t('grrc.riskLabel', { defaultValue: 'Risk' }),
                        name: updated.name,
                        status: updated.status,
                      }),
                    )
                  })
                }}
              >
                {riskFields(editForm, setEditForm)}
                <div className="toolbar">
                  <button type="submit" className="btn btn-primary" disabled={!!busy}>
                    {t('actions.save', { ns: 'common', defaultValue: 'Save' })}
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    onClick={() => setEditing(false)}
                    disabled={!!busy}
                  >
                    {t('actions.cancel', { ns: 'common', defaultValue: 'Cancel' })}
                  </button>
                </div>
              </form>
            )}
          </section>
        </div>
      )}

      {!silverGate && tab === 'compliance' && (
        <div className="two-col finance-tx-grid">
          <div>
            <section className="card">
              <h2 className="section-label" style={{ marginTop: 0 }}>
                {t('grrc.newCompliance', { defaultValue: 'New compliance item' })}
              </h2>
              <form
                className="form-grid"
                onSubmit={(e) => {
                  e.preventDefault()
                  run(async () => {
                    const created = await grrcApi.compliance.create({
                      ...compForm,
                      effectiveFrom: compForm.effectiveFrom || null,
                    })
                    setInfo(
                      t('toast.namedCreated', {
                        ns: 'common',
                        what: t('grrc.complianceLabel', { defaultValue: 'Compliance item' }),
                        name: created.name,
                      }),
                    )
                    setCompForm(emptyCompliance())
                    setSelected(created)
                  })
                }}
              >
                {complianceFields(compForm, setCompForm)}
                <button type="submit" className="btn btn-primary" disabled={!!busy}>
                  <Plus size={16} />{' '}
                  {t('grrc.createCompliance', { defaultValue: 'Create compliance item' })}
                </button>
              </form>
            </section>
            <section className="card">
              <h2 className="section-label" style={{ marginTop: 0 }}>
                {t('grrc.complianceList', { defaultValue: 'Compliance' })} ({total})
              </h2>
              {listTable(
                [
                  {
                    key: 'name',
                    label: t('grrc.colName', { defaultValue: 'Name' }),
                    render: (r) => r.name || `#${r.id}`,
                  },
                  {
                    key: 'status',
                    label: t('grrc.colStatus', { defaultValue: 'Status' }),
                    render: (r) => (
                      <span className={statusClass(r.status)}>{r.status || '—'}</span>
                    ),
                  },
                  {
                    key: 'kind',
                    label: t('grrc.colKind', { defaultValue: 'Kind' }),
                    render: (r) => r.itemKind || '—',
                  },
                ],
                'grrc.emptyCompliance',
                'No compliance items yet. Register a policy or control above.',
              )}
            </section>
          </div>
          <section className="card">
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('grrc.complianceDetail', { defaultValue: 'Compliance detail' })}
            </h2>
            {!selected && (
              <p className="muted">
                {t('grrc.selectCompliance', {
                  defaultValue: 'Select a compliance item from the list.',
                })}
              </p>
            )}
            {selected && !editing && (
              <>
                <div className="inv-detail-hero">
                  <div>
                    <div className="about-display" style={{ fontSize: '1.25rem' }}>
                      {selected.name}
                    </div>
                    <div className="muted">
                      <span className={statusClass(selected.status)}>{selected.status}</span>
                      {' · '}
                      {selected.itemKind}
                      {selected.ownerName ? ` · ${selected.ownerName}` : ''}
                      {selected.effectiveFrom ? ` · ${toDateInput(selected.effectiveFrom)}` : ''}
                    </div>
                  </div>
                </div>
                {selected.description && <p>{selected.description}</p>}
                <div className="toolbar">
                  <button
                    type="button"
                    className="btn"
                    disabled={!!busy}
                    onClick={() => {
                      setEditForm({
                        name: selected.name || '',
                        description: selected.description || '',
                        itemKind: selected.itemKind || 'POLICY',
                        status: selected.status || 'DRAFT',
                        ownerName: selected.ownerName || '',
                        effectiveFrom: toDateInput(selected.effectiveFrom),
                      })
                      setEditing(true)
                    }}
                  >
                    {t('actions.edit', { ns: 'common', defaultValue: 'Edit' })}
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    disabled={!!busy}
                    onClick={() => {
                      if (!confirmDelete(selected.name)) return
                      run(async () => {
                        await grrcApi.compliance.delete(selected.id)
                        setSelected(null)
                        setInfo(
                          t('grrc.deleted', {
                            defaultValue: 'Deleted {{name}}',
                            name: selected.name,
                          }),
                        )
                      })
                    }}
                  >
                    {t('actions.delete', { ns: 'common', defaultValue: 'Delete' })}
                  </button>
                </div>
              </>
            )}
            {selected && editing && (
              <form
                className="form-grid"
                onSubmit={(e) => {
                  e.preventDefault()
                  run(async () => {
                    const updated = await grrcApi.compliance.update(selected.id, {
                      ...editForm,
                      effectiveFrom: editForm.effectiveFrom || null,
                    })
                    setSelected(updated)
                    setEditing(false)
                    setInfo(
                      t('toast.namedStatus', {
                        ns: 'common',
                        what: t('grrc.complianceLabel', { defaultValue: 'Compliance item' }),
                        name: updated.name,
                        status: updated.status,
                      }),
                    )
                  })
                }}
              >
                {complianceFields(editForm, setEditForm)}
                <div className="toolbar">
                  <button type="submit" className="btn btn-primary" disabled={!!busy}>
                    {t('actions.save', { ns: 'common', defaultValue: 'Save' })}
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    onClick={() => setEditing(false)}
                    disabled={!!busy}
                  >
                    {t('actions.cancel', { ns: 'common', defaultValue: 'Cancel' })}
                  </button>
                </div>
              </form>
            )}
          </section>
        </div>
      )}
    </div>
  )
}
