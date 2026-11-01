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
  ContactRound,
  Plus,
  RefreshCw,
  Ban,
  Check,
  Eye,
  Pencil,
  Trash2,
} from 'lucide-react'
import ResponsiveDataList from '../components/ResponsiveDataList'
import ListSortHeader from '../components/ListSortHeader'
import useServerListSort from '../hooks/useServerListSort'
import { salesApi, domainsApi } from '../api/client'
import CustomerSelect from '../components/CustomerSelect'
import CurrencySelect from '../components/CurrencySelect'
import DeskShell from '../components/desk/DeskShell'
import DeskNewFab from '../components/DeskNewFab'
import ConfirmDialog from '../components/ConfirmDialog'
import RowActionSlots from '../components/RowActionSlots'
import useRecordView from '../hooks/useRecordView'
import useDeskSelection from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'

const PKG = 'sales'
const RESOURCE = 'Opportunity'
const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'

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
 * SalesAdvanced Opportunities desk (OI-S08) — Buyers UI 2.0 (ADR-050):
 * single-column list + modal view/edit; CRM workspace via getBelowFields / getFooterExtra.
 * Route: /sales/opportunities (legacy /sales/crm redirects).
 */
export default function SalesCrmPage() {
  const { t } = useTranslation(['desks', 'common', 'sales'])
  const loadRef = useRef(async () => {})
  const getBelowFieldsRef = useRef(null)
  const getFooterExtraRef = useRef(null)
  const deepLinkOpened = useRef(null)
  const selectedRef = useRef(null)
  const activitiesRef = useRef([])
  const busyRef = useRef(null)
  const stageDraftRef = useRef('LEAD')
  const actFormRef = useRef({ kind: 'NOTE', description: '', dueDate: '' })
  const onUpdateStageRef = useRef(async () => {})
  const onMarkLostRef = useRef(async () => {})
  const onConvertRef = useRef(async () => {})
  const onAddActivityRef = useRef(async () => {})

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

  const { sort, order, onSortChange } = useServerListSort('id', 'desc')
  const OPP_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'stage', label: 'Stage' },
      { value: 'contactName', label: 'Contact' },
      { value: 'expectedAmountEur', label: 'Amount' },
      { value: 'expectedCloseDate', label: 'Close date' },
    ],
    [],
  )
  const [items, setItems] = useState([])
  const [selected, setSelected] = useState(null)
  const [activities, setActivities] = useState([])
  const [form, setForm] = useState(emptyOpp())
  const [stageDraft, setStageDraft] = useState('LEAD')
  const [actForm, setActForm] = useState({ kind: 'NOTE', description: '', dueDate: '' })
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)

  selectedRef.current = selected
  activitiesRef.current = activities
  busyRef.current = busy
  stageDraftRef.current = stageDraft
  actFormRef.current = actForm

  const {
    viewModal,
    openView,
    openEdit,
    updateViewingRecord,
  } = useRecordView({
    modalEdit: true,
    canModalEdit: () => true,
    onSaved: (updated) => {
      loadRef.current().catch(() => {})
      if (!updated?.id) return
      setSelected((prev) =>
        prev && String(prev.id) === String(updated.id) ? { ...prev, ...updated } : prev,
      )
    },
    getBelowFields: (ctx) => getBelowFieldsRef.current?.(ctx) ?? null,
    getFooterExtra: (ctx) => getFooterExtraRef.current?.(ctx) ?? null,
  })

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // Full list — no page search / Open·All chips (ADR-050); use top-bar FTS.
      const data = await salesApi.crm.listOpportunities({ max: 50, offset: 0, sort, order })
      setItems(data?.items || [])
    } catch (e) {
      setItems([])
      setError(friendlyErr(e, t))
    } finally {
      setLoading(false)
    }
  }, [sort, order, t])

  loadRef.current = load

  useEffect(() => {
    load()
  }, [load])

  const hydrateOpp = useCallback(
    async (id) => {
      if (!id) {
        setSelected(null)
        setActivities([])
        return null
      }
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
      return full
    },
    [],
  )

  const openOppView = useCallback(
    async (row) => {
      if (!row?.id && row?.id !== 0) return
      setBusy('select')
      setError(null)
      try {
        const full = await hydrateOpp(row.id)
        if (!full) return
        deepLinkOpened.current = String(full.id)
        setSelectedId(String(full.id))
        openView(PKG, RESOURCE, full)
      } catch (e) {
        setError(friendlyErr(e, t))
      } finally {
        setBusy(null)
      }
    },
    [hydrateOpp, openView, t],
  )

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      if (!selectedId) return
      if (deepLinkOpened.current === String(selectedId)) return
      setBusy('select')
      setError(null)
      try {
        const full = await hydrateOpp(selectedId)
        if (cancelled || !full) return
        openView(PKG, RESOURCE, full)
        deepLinkOpened.current = String(selectedId)
      } catch (e) {
        if (!cancelled) setError(friendlyErr(e, t))
      } finally {
        if (!cancelled) setBusy(null)
      }
    }
    run()
    return () => {
      cancelled = true
    }
  }, [selectedId, hydrateOpp, openView, t])

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
      const full = await hydrateOpp(opp.id)
      deepLinkOpened.current = String(opp.id)
      setSelectedId(String(opp.id))
      openView(PKG, RESOURCE, full || opp)
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  const onDelete = useCallback(
    async (record) => {
      const label =
        record?.name || t('view.untitled', { ns: 'common', defaultValue: 'this record' })
      if (!window.confirm(t('actions.confirmDelete', { ns: 'common', label }))) return
      setError(null)
      try {
        await domainsApi.remove(PKG, RESOURCE, record.id)
        setInfo(
          t('toast.namedDeleted', {
            ns: 'common',
            name: label,
            defaultValue: `Deleted ${label}`,
          }),
        )
        if (String(selectedId) === String(record.id)) {
          setSelectedId(null)
          deepLinkOpened.current = null
          setSelected(null)
          setActivities([])
        }
        await loadRef.current()
      } catch (err) {
        setError(friendlyErr(err, t))
      }
    },
    [selectedId, t],
  )

  const renderRowActions = useCallback(
    (record) => {
      if (!record?.id && record?.id !== 0) return null
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
                openOppView(record)
              }}
            >
              <Eye size={14} />
            </button>
          }
          edit={
            <button
              type="button"
              className={ROW_ACTION_CLASS}
              title={t('actions.edit', { ns: 'common' })}
              aria-label={t('actions.edit', { ns: 'common' })}
              onClick={(e) => {
                e.stopPropagation()
                openEdit(PKG, RESOURCE, record)
              }}
            >
              <Pencil size={14} />
            </button>
          }
          delete={
            <button
              type="button"
              className={ROW_ACTION_CLASS}
              title={t('actions.delete', { ns: 'common' })}
              aria-label={t('actions.delete', { ns: 'common' })}
              onClick={(e) => {
                e.stopPropagation()
                onDelete(record)
              }}
            >
              <Trash2 size={14} />
            </button>
          }
        />
      )
    },
    [onDelete, openEdit, openOppView, t],
  )

  const runAction = async (label, fn, okMsg) => {
    const cur = selectedRef.current
    if (!cur?.id) return
    setBusy(label)
    setError(null)
    setInfo(null)
    try {
      await fn(cur)
      if (okMsg) setInfo(okMsg)
      await load()
      const full = await hydrateOpp(cur.id)
      if (full) updateViewingRecord(full)
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  const onUpdateStage = async () => {
    const draft = stageDraftRef.current
    await runAction(
      'stage',
      (cur) => salesApi.crm.updateOpportunity(cur.id, { stage: draft }),
      t('crm.stageUpdated', { defaultValue: 'Stage updated.' }),
    )
  }

  const onMarkLost = async () => {
    await runAction(
      'lost',
      (cur) => salesApi.crm.markLost(cur.id, {}),
      t('crm.markedLost', { defaultValue: 'Opportunity marked lost.' }),
    )
  }

  const onConvert = async () => {
    const cur = selectedRef.current
    if (!cur?.id) return
    if (!cur.customerId) {
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
      const result = await salesApi.crm.convert(cur.id, {})
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
      const full = await hydrateOpp(cur.id)
      if (full) updateViewingRecord(full)
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  const onAddActivity = async (e) => {
    e?.preventDefault?.()
    const cur = selectedRef.current
    const af = actFormRef.current
    if (!cur?.id || !af.description.trim()) return
    setBusy('act')
    setError(null)
    setInfo(null)
    try {
      await salesApi.crm.createActivity(cur.id, {
        kind: af.kind || 'NOTE',
        description: af.description.trim(),
        dueDate: af.dueDate || undefined,
      })
      setActForm({ kind: 'NOTE', description: '', dueDate: '' })
      setInfo(t('crm.activityAdded', { defaultValue: 'Activity added.' }))
      const full = await hydrateOpp(cur.id)
      if (full) updateViewingRecord(full)
    } catch (err) {
      setError(friendlyErr(err, t))
    } finally {
      setBusy(null)
    }
  }

  onUpdateStageRef.current = onUpdateStage
  onMarkLostRef.current = onMarkLost
  onConvertRef.current = onConvert
  onAddActivityRef.current = onAddActivity

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

  const toolbar = null

  const list = (
    <>
      {loading && <p className="muted">{t('crm.loading', { defaultValue: 'Loading…' })}</p>}
      <ResponsiveDataList
        items={items}
        empty={t('crm.emptyAll', {
          defaultValue: 'No opportunities yet. Create one with New.',
        })}
        loading={loading}
        selectedKey={selectedId}
        onSelect={(row) => openOppView(row)}
        sortFields={OPP_SORT_MENU}
        sort={sort}
        order={order}
        onSortChange={onSortChange}
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
        actions={(row) => renderRowActions(row)}
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <ListSortHeader
                  field="name"
                  label={t('crm.colName', { defaultValue: 'Name' })}
                  sort={sort}
                  order={order}
                  onSortChange={onSortChange}
                />
                <ListSortHeader
                  field="stage"
                  label={t('crm.colStage', { defaultValue: 'Stage' })}
                  sort={sort}
                  order={order}
                  onSortChange={onSortChange}
                />
                <ListSortHeader
                  field="contactName"
                  label={t('crm.colCustomer', { defaultValue: 'Customer' })}
                  sort={sort}
                  order={order}
                  onSortChange={onSortChange}
                />
                <ListSortHeader
                  field="expectedAmountEur"
                  label={t('crm.colAmount', { defaultValue: 'Amount' })}
                  sort={sort}
                  order={order}
                  onSortChange={onSortChange}
                />
                <th>{t('wb.action', { ns: 'common', defaultValue: 'Action' })}</th>
              </tr>
            </thead>
            <tbody>
              {items.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">
                    {t('crm.emptyAll', {
                      defaultValue: 'No opportunities yet. Create one with New.',
                    })}
                  </td>
                </tr>
              )}
              {items.map((row) => (
                <tr
                  key={row.id}
                  data-selected={String(selectedId) === String(row.id) ? 'true' : undefined}
                  className={
                    String(selectedId) === String(row.id) ? 'row-selected' : 'row-click'
                  }
                  onClick={() => openOppView(row)}
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
                  <td onClick={(e) => e.stopPropagation()}>{renderRowActions(row)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ResponsiveDataList>
    </>
  )

  getBelowFieldsRef.current = ({ resource, record }) => {
    if (!record || resource !== RESOURCE) return null
    const live = selectedRef.current
    const opp = live?.id === record.id ? live : record
    const acts = live?.id === record.id ? activitiesRef.current : opp.activities || []
    const isTerminal = opp.stage === 'WON' || opp.stage === 'LOST'
    const isBusy = !!busyRef.current
    const af = actFormRef.current
    return (
      <>
        <h3 className="section-label">{t('crm.activities', { defaultValue: 'Activities' })}</h3>
        {acts.length === 0 ? (
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
                {acts.map((a) => (
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
          <form
            className="form-grid"
            style={{ marginTop: '0.75rem' }}
            onSubmit={(e) => {
              e.preventDefault()
              onAddActivityRef.current(e)
            }}
          >
            <div className="form-row-2">
              <label>
                {t('crm.fieldActKind', { defaultValue: 'Kind' })}
                <select
                  value={af.kind}
                  disabled={isBusy}
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
                  value={af.dueDate}
                  disabled={isBusy}
                  onChange={(e) => setActForm((f) => ({ ...f, dueDate: e.target.value }))}
                />
              </label>
            </div>
            <label>
              {t('crm.fieldActDescription', { defaultValue: 'Note / description' })} *
              <textarea
                required
                rows={2}
                value={af.description}
                disabled={isBusy}
                onChange={(e) => setActForm((f) => ({ ...f, description: e.target.value }))}
              />
            </label>
            <button type="submit" className="btn btn-primary" disabled={isBusy}>
              <Plus size={16} /> {t('crm.addActivity', { defaultValue: 'Add activity' })}
            </button>
          </form>
        )}
      </>
    )
  }

  getFooterExtraRef.current = ({ resource, record }) => {
    if (!record || resource !== RESOURCE) return null
    const live = selectedRef.current
    const opp = live?.id === record.id ? live : record
    const isTerminal = opp.stage === 'WON' || opp.stage === 'LOST'
    if (isTerminal) return null
    const isBusy = !!busyRef.current
    const draft = stageDraftRef.current
    return (
      <>
        <label style={{ margin: 0 }}>
          {t('crm.fieldStage', { defaultValue: 'Stage' })}
          <select
            value={draft}
            disabled={isBusy}
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
          disabled={isBusy}
          onClick={() => onUpdateStageRef.current()}
          title={t('crm.updateStageTitle', { defaultValue: 'Update stage' })}
        >
          {t('crm.updateStage', { defaultValue: 'Update' })}
        </button>
        <button
          type="button"
          className="btn"
          disabled={isBusy}
          onClick={() => onMarkLostRef.current()}
          title={t('crm.markLostTitle', { defaultValue: 'Mark lost' })}
        >
          <Ban size={16} /> {t('crm.markLost', { defaultValue: 'Lost' })}
        </button>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => onConvertRef.current()}
          disabled={isBusy}
          title={t('crm.convertTitle', {
            defaultValue: 'Convert to customer / quote',
          })}
        >
          <Check size={16} /> {t('crm.convert', { defaultValue: 'Convert' })}
        </button>
      </>
    )
  }

  return (
    <div className="page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}

      <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
        <button type="button" className="btn" onClick={load} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
        <Link className="btn btn-secondary" to="/sales">
          {t('crm.backSales', { defaultValue: '← Sales desk' })}
        </Link>
      </div>

      <DeskShell
        layout="single"
        title={
          <>
            <ContactRound size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('crm.title', { defaultValue: 'Opportunities' })}
          </>
        }
        subtitle={null}
        newLabel={t('crm.newOpp', { defaultValue: 'New opportunity' })}
        newExpanded={newExpanded}
        onToggleNew={requestToggleNew}
        newPanel={newPanel}
        toolbar={toolbar}
        list={list}
        hasSelection={false}
        detail={null}
        helpContext={helpContext}
        narrowSheetOpen={false}
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
