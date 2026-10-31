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
import { ContactRound, Plus, RefreshCw, Ban, Check } from 'lucide-react'
import ResponsiveDataList from '../components/ResponsiveDataList'
import ListFilterBar from '../components/ListFilterBar'
import { salesApi } from '../api/client'
import CustomerSelect from '../components/CustomerSelect'
import CurrencySelect from '../components/CurrencySelect'
import DeskShell from '../components/desk/DeskShell'
import DeskNewFab from '../components/DeskNewFab'
import ConfirmDialog from '../components/ConfirmDialog'
import useDeskSelection, { scrollSelectedIntoView } from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'
import useNarrowViewport from '../hooks/useNarrowViewport'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'

const STAGES = ['LEAD', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST']
const OPEN = new Set(['LEAD', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION'])
const ACT_KINDS = ['NOTE', 'CALL', 'EMAIL', 'MEETING', 'TASK']
const SOURCES = ['WEB', 'REFERRAL', 'COLD', 'PARTNER', 'OTHER']

const emptyOpp = () => ({
  name: '',
  stage: 'LEAD',
  customerId: '',
  contactName: '',
  contactEmail: '',
  expectedAmountEur: '',
  currencyCode: 'EUR',
  expectedCloseDate: '',
  ownerName: '',
  source: '',
  description: '',
})

function toDateInput(v) {
  if (v == null || v === '') return ''
  if (typeof v === 'number') return new Date(v).toISOString().slice(0, 10)
  const s = String(v)
  return s.length >= 10 ? s.slice(0, 10) : s
}

function friendlyErr(e, t) {
  if (e?.status === 403 || e?.code === 'ADVANCED_REQUIRES_SILVER') {
    return t('crm.silverOnly', { defaultValue: 'Available on Silver (or Iron eval)' })
  }
  return e?.message || String(e)
}

function stageBadge(stage) {
  if (stage === 'WON') return 'badge badge-ok'
  if (stage === 'LOST') return 'badge badge-danger'
  if (OPEN.has(stage)) return 'badge badge-warn'
  return 'badge badge-draft'
}

function oppBody(src) {
  const body = {
    name: src.name.trim(),
    stage: src.stage || 'LEAD',
    currencyCode: src.currencyCode || 'EUR',
    contactName: src.contactName.trim() || undefined,
    contactEmail: src.contactEmail.trim() || undefined,
    ownerName: src.ownerName.trim() || undefined,
    source: src.source || undefined,
    description: src.description.trim() || undefined,
    expectedCloseDate: src.expectedCloseDate || undefined,
  }
  if (src.customerId) body.customerId = Number(src.customerId)
  if (src.expectedAmountEur !== '') body.expectedAmountEur = Number(src.expectedAmountEur)
  return body
}

/**
 * SalesAdvanced Opportunities desk (OI-S08) — DeskShell master–detail (ADR-047).
 * Route: /sales/opportunities (legacy /sales/crm redirects).
 */
export default function SalesCrmPage() {
  const { t } = useTranslation(['desks', 'common', 'sales'])
  const narrow = useNarrowViewport()

  const [tab] = useState('opportunities')
  const [selectedId, setSelectedId] = useState(() => {
    if (typeof window === 'undefined') return null
    return new URLSearchParams(window.location.search).get('id') || null
  })
  const [newExpanded, setNewExpanded] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [pendingNav, setPendingNav] = useState(null)
  const [companyId] = useState('default')

  const { saveMemory } = useDeskMemory({ deskKey: 'sales-crm', companyId })

  useDeskSelection({
    deskKey: 'sales-crm',
    defaultTab: 'opportunities',
    tab,
    setTab: () => {},
    selectedId,
    setSelectedId,
  })

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

  const [openOnly, setOpenOnly] = useState(true)
  const [listQ, setListQ] = useState('')
  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [selected, setSelected] = useState(null)
  const [activities, setActivities] = useState([])
  const [form, setForm] = useState(emptyOpp())
  const [stageDraft, setStageDraft] = useState('LEAD')
  const [actForm, setActForm] = useState({ kind: 'NOTE', description: '', dueDate: '' })
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const params = { max: 50, offset: 0 }
      if (openOnly) params.open = true
      if (listQ.trim()) params.q = listQ.trim()
      const data = await salesApi.crm.listOpportunities(params)
      setItems(data?.items || [])
      setTotal(data?.total ?? (data?.items || []).length)
    } catch (e) {
      setItems([])
      setTotal(0)
      setError(friendlyErr(e, t))
    } finally {
      setLoading(false)
    }
  }, [openOnly, listQ, t])

  useEffect(() => {
    load()
  }, [load])

  const selectOpp = useCallback(
    async (id) => {
      if (!id) {
        setSelected(null)
        setActivities([])
        return
      }
      setBusy('select')
      setError(null)
      try {
        const full = await salesApi.crm.getOpportunity(id)
        setSelected(full)
        setStageDraft(full.stage || 'LEAD')
        setActivities(full.activities || [])
        try {
          const acts = await salesApi.crm.listActivities(id)
          setActivities(acts?.items || full.activities || [])
        } catch {
          /* keep embedded */
        }
      } catch (e) {
        setError(friendlyErr(e, t))
      } finally {
        setBusy(null)
      }
    },
    [t],
  )

  useEffect(() => {
    selectOpp(selectedId)
  }, [selectedId, selectOpp])

  useEffect(() => {
    if (!selectedId) return
    const id = window.requestAnimationFrame(() => {
      scrollSelectedIntoView('.desk-shell-list')
    })
    return () => window.cancelAnimationFrame(id)
  }, [selectedId, selected?.id])

  const resetCreateForms = useCallback(() => {
    setForm(emptyOpp())
  }, [])

  const createDirty = useMemo(() => {
    const d = emptyOpp()
    return Boolean(
      (form.name || '').trim() ||
        form.stage !== d.stage ||
        form.customerId ||
        (form.contactName || '').trim() ||
        (form.contactEmail || '').trim() ||
        form.expectedAmountEur !== '' ||
        form.currencyCode !== d.currencyCode ||
        form.expectedCloseDate ||
        (form.ownerName || '').trim() ||
        form.source ||
        (form.description || '').trim(),
    )
  }, [form])

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

  const keyboardIds = useMemo(() => items.map((i) => i.id), [items])

  useDeskKeyboard({
    enabled: !newExpanded,
    itemIds: keyboardIds,
    selectedId,
    setSelectedId,
    onToggleNew: requestToggleNew,
  })

  const confirmDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
    applyCollapseCreate()
    void pendingNav
  }, [pendingNav, applyCollapseCreate])

  const cancelDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
  }, [])

  const onCreate = async (e) => {
    e.preventDefault()
    if (!form.name.trim()) return
    setBusy('create')
    setError(null)
    setInfo(null)
    try {
      const opp = await salesApi.crm.createOpportunity(oppBody(form))
      setInfo(t('crm.created', { defaultValue: 'Opportunity “{{name}}” created.', name: opp.name }))
      resetCreateForms()
      setNewExpanded(false)
      await load()
      setSelectedId(String(opp.id))
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  const runAction = async (label, fn, okMsg) => {
    if (!selected?.id) return
    setBusy(label)
    setError(null)
    setInfo(null)
    try {
      await fn()
      if (okMsg) setInfo(okMsg)
      await load()
      await selectOpp(selected.id)
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  const onConvert = async () => {
    if (!selected?.id) return
    if (!selected.customerId) {
      setError(
        t('crm.convertNeedsCustomer', {
          defaultValue: 'Link a customer before converting to a quote.',
        }),
      )
      return
    }
    setBusy('convert')
    setError(null)
    setInfo(null)
    try {
      const result = await salesApi.crm.convert(selected.id, {})
      const qLabel = result?.quote?.quoteNumber || '—'
      setInfo(
        result?.alreadyConverted
          ? t('crm.alreadyConverted', {
              defaultValue: 'Already converted — quote {{quote}}.',
              quote: qLabel,
            })
          : t('crm.converted', {
              defaultValue: 'Converted to draft quote {{quote}}.',
              quote: qLabel,
            }),
      )
      await load()
      await selectOpp(selected.id)
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  const onAddActivity = async (e) => {
    e.preventDefault()
    if (!selected?.id || !actForm.description.trim()) return
    setBusy('act')
    setError(null)
    setInfo(null)
    try {
      await salesApi.crm.createActivity(selected.id, {
        kind: actForm.kind || 'NOTE',
        description: actForm.description.trim(),
        dueDate: actForm.dueDate || undefined,
      })
      setActForm({ kind: 'NOTE', description: '', dueDate: '' })
      setInfo(t('crm.activityAdded', { defaultValue: 'Activity added.' }))
      await selectOpp(selected.id)
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  const isTerminal = selected?.stage === 'WON' || selected?.stage === 'LOST'
  const hasSelection = Boolean(selected?.id)

  const helpContext = useMemo(() => {
    const base = { desk: 'sales-crm', tab: 'opportunities' }
    if (selected) {
      return {
        ...base,
        resource: 'Opportunity',
        objectId: selected.id,
        status: selected.stage,
      }
    }
    return base
  }, [selected])

  const newPanel = (
    <form className="form-grid" onSubmit={onCreate}>
      <label>
        {t('crm.fieldName', { defaultValue: 'Name' })} *
        <input
          required
          value={form.name}
          disabled={!!busy}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          {t('crm.fieldStage', { defaultValue: 'Stage' })}
          <select
            value={form.stage}
            disabled={!!busy}
            onChange={(e) => setForm((f) => ({ ...f, stage: e.target.value }))}
          >
            {STAGES.filter((s) => s !== 'WON' && s !== 'LOST').map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('crm.fieldSource', { defaultValue: 'Source' })}
          <select
            value={form.source}
            disabled={!!busy}
            onChange={(e) => setForm((f) => ({ ...f, source: e.target.value }))}
          >
            <option value="">—</option>
            {SOURCES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label>
        {t('crm.fieldCustomer', { defaultValue: 'Customer' })}
        <CustomerSelect
          id="crm-new-customer"
          value={form.customerId}
          disabled={!!busy}
          onChange={(id) => setForm((f) => ({ ...f, customerId: id || '' }))}
        />
      </label>
      <div className="form-row-2">
        <label>
          {t('crm.fieldContactName', { defaultValue: 'Contact name' })}
          <input
            value={form.contactName}
            disabled={!!busy}
            onChange={(e) => setForm((f) => ({ ...f, contactName: e.target.value }))}
          />
        </label>
        <label>
          {t('crm.fieldContactEmail', { defaultValue: 'Contact email' })}
          <input
            type="email"
            value={form.contactEmail}
            disabled={!!busy}
            onChange={(e) => setForm((f) => ({ ...f, contactEmail: e.target.value }))}
          />
        </label>
      </div>
      <div className="form-row-2">
        <label>
          {t('crm.fieldAmount', { defaultValue: 'Expected amount' })}
          <input
            type="number"
            step="0.01"
            min="0"
            value={form.expectedAmountEur}
            disabled={!!busy}
            onChange={(e) => setForm((f) => ({ ...f, expectedAmountEur: e.target.value }))}
          />
        </label>
        <label>
          {t('crm.fieldCurrency', { defaultValue: 'Currency' })}
          <CurrencySelect
            id="crm-new-ccy"
            value={form.currencyCode}
            disabled={!!busy}
            onChange={(e) => setForm((f) => ({ ...f, currencyCode: e.target.value }))}
          />
        </label>
      </div>
      <div className="form-row-2">
        <label>
          {t('crm.fieldCloseDate', { defaultValue: 'Expected close' })}
          <input
            type="date"
            value={form.expectedCloseDate}
            disabled={!!busy}
            onChange={(e) => setForm((f) => ({ ...f, expectedCloseDate: e.target.value }))}
          />
        </label>
        <label>
          {t('crm.fieldOwner', { defaultValue: 'Owner' })}
          <input
            value={form.ownerName}
            disabled={!!busy}
            onChange={(e) => setForm((f) => ({ ...f, ownerName: e.target.value }))}
          />
        </label>
      </div>
      <label>
        {t('crm.fieldDescription', { defaultValue: 'Description' })}
        <textarea
          rows={2}
          value={form.description}
          disabled={!!busy}
          onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
      <button type="submit" className="btn btn-primary" disabled={!!busy}>
        <Plus size={16} /> {t('crm.create', { defaultValue: 'Create opportunity' })}
      </button>
    </form>
  )

  const toolbar = (
    <>
      <ListFilterBar
        q={listQ}
        onQChange={setListQ}
        placeholder={t('crm.search', { defaultValue: 'Search opportunities…' })}
        ariaLabel={t('actions.search', { ns: 'common', defaultValue: 'Search' })}
      />
      <button
        type="button"
        className={`btn ${openOnly ? 'btn-primary' : 'btn-ghost'} filter-select`}
        onClick={() => setOpenOnly(true)}
        disabled={!!busy}
      >
        {t('crm.filterOpen', { defaultValue: 'Open' })}
      </button>
      <button
        type="button"
        className={`btn ${!openOnly ? 'btn-primary' : 'btn-ghost'}`}
        onClick={() => setOpenOnly(false)}
        disabled={!!busy}
      >
        {t('crm.filterAll', { defaultValue: 'All' })}
      </button>
    </>
  )

  const list = (
    <>
      <h2 className="section-label" style={{ marginTop: 0 }}>
        {openOnly
          ? t('crm.openOpps', { defaultValue: 'Open opportunities' })
          : t('crm.allOpps', { defaultValue: 'All opportunities' })}
        {!loading && (
          <span className="muted" style={{ fontWeight: 400, marginLeft: 8 }}>
            ({total})
          </span>
        )}
      </h2>
      {loading && <p className="muted">{t('crm.loading', { defaultValue: 'Loading…' })}</p>}
      <ResponsiveDataList
        items={items}
        empty={
          openOnly
            ? t('crm.empty', {
                defaultValue: 'No open opportunities. Create one with New.',
              })
            : t('crm.emptyAll', {
                defaultValue: 'No opportunities yet. Create one with New.',
              })
        }
        loading={loading}
        selectedKey={selected?.id}
        onSelect={(row) => setSelectedId(String(row.id))}
        fields={[
          {
            id: 'name',
            primary: true,
            render: (row) => entityDisplayLabel(row, { fallback: '—' }),
          },
          {
            id: 'stage',
            secondary: true,
            render: (row) => (
              <span className={stageBadge(row.stage)}>{row.stage || '—'}</span>
            ),
          },
          {
            id: 'customer',
            secondary: true,
            render: (row) => row.customerName || row.contactName || '—',
          },
          {
            id: 'amount',
            meta: true,
            render: (row) =>
              row.expectedAmountEur != null
                ? `${Number(row.expectedAmountEur).toFixed(2)} ${row.currencyCode || ''}`
                : '—',
          },
        ]}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('crm.colName', { defaultValue: 'Name' })}</th>
                <th>{t('crm.colStage', { defaultValue: 'Stage' })}</th>
                <th>{t('crm.colCustomer', { defaultValue: 'Customer' })}</th>
                <th>{t('crm.colAmount', { defaultValue: 'Amount' })}</th>
              </tr>
            </thead>
            <tbody>
              {items.length === 0 && (
                <tr>
                  <td colSpan={4} className="muted">
                    {openOnly
                      ? t('crm.empty', {
                          defaultValue: 'No open opportunities. Create one with New.',
                        })
                      : t('crm.emptyAll', {
                          defaultValue: 'No opportunities yet. Create one with New.',
                        })}
                  </td>
                </tr>
              )}
              {items.map((row) => (
                <tr
                  key={row.id}
                  data-selected={selected?.id === row.id ? 'true' : undefined}
                  className={selected?.id === row.id ? 'row-selected' : 'row-click'}
                  onClick={() => setSelectedId(String(row.id))}
                >
                  <td>{entityDisplayLabel(row, { fallback: '—' })}</td>
                  <td>
                    <span className={stageBadge(row.stage)}>{row.stage || '—'}</span>
                  </td>
                  <td>{row.customerName || row.contactName || '—'}</td>
                  <td>
                    {row.expectedAmountEur != null
                      ? `${Number(row.expectedAmountEur).toFixed(2)} ${row.currencyCode || ''}`
                      : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
    </>
  )

  let detail = null
  if (selected) {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        <div className="inv-detail-hero">
          <div>
            <div className="about-display" style={{ fontSize: '1.25rem' }}>
              {selected.name}
            </div>
            <div className="muted">
              <span className={stageBadge(selected.stage)}>{selected.stage}</span>
              {selected.contactName ? ` · ${selected.contactName}` : ''}
              {selected.contactEmail ? ` · ${selected.contactEmail}` : ''}
              {selected.ownerName ? ` · ${selected.ownerName}` : ''}
              {selected.expectedCloseDate
                ? ` · ${t('crm.closes', { defaultValue: 'closes' })} ${toDateInput(selected.expectedCloseDate)}`
                : ''}
              {selected.resultingQuoteId
                ? ` · ${t('crm.quoteId', { defaultValue: 'quote' })}`
                : ''}
            </div>
          </div>
        </div>
        {selected.description && <p>{selected.description}</p>}

        {!isTerminal && (
          <div
            style={{
              display: 'flex',
              gap: 8,
              flexWrap: 'wrap',
              alignItems: 'end',
              marginBottom: '1rem',
            }}
          >
            <label style={{ margin: 0 }}>
              {t('crm.fieldStage', { defaultValue: 'Stage' })}
              <select
                value={stageDraft}
                disabled={!!busy}
                onChange={(e) => setStageDraft(e.target.value)}
              >
                {STAGES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              className="btn"
              disabled={!!busy}
              onClick={() =>
                runAction(
                  'stage',
                  () => salesApi.crm.updateOpportunity(selected.id, { stage: stageDraft }),
                  t('crm.stageUpdated', { defaultValue: 'Stage updated.' }),
                )
              }
            >
              {t('crm.updateStage', { defaultValue: 'Update stage' })}
            </button>
            <button
              type="button"
              className="btn"
              disabled={!!busy}
              onClick={() =>
                runAction(
                  'lost',
                  () => salesApi.crm.markLost(selected.id, {}),
                  t('crm.markedLost', { defaultValue: 'Opportunity marked lost.' }),
                )
              }
            >
              <Ban size={16} /> {t('crm.markLost', { defaultValue: 'Mark lost' })}
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={onConvert}
              disabled={!!busy}
            >
              <Check size={16} />{' '}
              {t('crm.convert', { defaultValue: 'Convert to customer / quote' })}
            </button>
          </div>
        )}

        <h3 className="section-label">{t('crm.activities', { defaultValue: 'Activities' })}</h3>
        {activities.length === 0 ? (
          <p className="muted">
            {t('crm.activitiesEmpty', { defaultValue: 'No activities yet. Add a note below.' })}
          </p>
        ) : (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t('crm.colKind', { defaultValue: 'Kind' })}</th>
                  <th>{t('crm.colDescription', { defaultValue: 'Description' })}</th>
                  <th>{t('crm.colDue', { defaultValue: 'Due' })}</th>
                </tr>
              </thead>
              <tbody>
                {activities.map((a) => (
                  <tr key={a.id}>
                    <td>{a.kind || '—'}</td>
                    <td>{a.description || a.name || '—'}</td>
                    <td>{a.dueDate ? toDateInput(a.dueDate) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {!isTerminal && (
          <form className="form-grid" style={{ marginTop: '0.75rem' }} onSubmit={onAddActivity}>
            <div className="form-row-2">
              <label>
                {t('crm.fieldActKind', { defaultValue: 'Kind' })}
                <select
                  value={actForm.kind}
                  disabled={!!busy}
                  onChange={(e) => setActForm((f) => ({ ...f, kind: e.target.value }))}
                >
                  {ACT_KINDS.map((k) => (
                    <option key={k} value={k}>
                      {k}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                {t('crm.fieldDueDate', { defaultValue: 'Due date' })}
                <input
                  type="date"
                  value={actForm.dueDate}
                  disabled={!!busy}
                  onChange={(e) => setActForm((f) => ({ ...f, dueDate: e.target.value }))}
                />
              </label>
            </div>
            <label>
              {t('crm.fieldActDescription', { defaultValue: 'Note / description' })} *
              <textarea
                required
                rows={2}
                value={actForm.description}
                disabled={!!busy}
                onChange={(e) => setActForm((f) => ({ ...f, description: e.target.value }))}
              />
            </label>
            <button type="submit" className="btn btn-primary" disabled={!!busy}>
              <Plus size={16} /> {t('crm.addActivity', { defaultValue: 'Add activity' })}
            </button>
          </form>
        )}
      </div>
    )
  }

  return (
    <div className="page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}

      <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
        <button type="button" className="btn" onClick={load} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
        <Link className="btn btn-secondary" to="/sales">
          {t('crm.backSales', { defaultValue: '← Sales desk' })}
        </Link>
      </div>

      <DeskShell
        title={
          <>
            <ContactRound size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('crm.title', { defaultValue: 'Opportunities' })}
          </>
        }
        subtitle={t('crm.subtitle', {
          defaultValue: 'Pipeline of opportunities and activities. Silver pack / Iron eval.',
        })}
        newLabel={t('crm.newOpp', { defaultValue: 'New opportunity' })}
        newExpanded={newExpanded}
        onToggleNew={requestToggleNew}
        newPanel={newPanel}
        toolbar={toolbar}
        list={list}
        hasSelection={hasSelection}
        emptyTitle={t('wb.selectItem', { ns: 'common', defaultValue: 'Select an item' })}
        emptyBody={t('desk.emptyBody', {
          ns: 'common',
          defaultValue: 'Choose a row from the list to see details.',
        })}
        detail={detail}
        helpContext={helpContext}
        narrowSheetOpen={narrow && hasSelection}
        onCloseSheet={() => setSelectedId(null)}
      />

      <DeskNewFab
        label={t('crm.newOpp', { defaultValue: 'New opportunity' })}
        onClick={() => !newExpanded && requestToggleNew()}
      />

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
