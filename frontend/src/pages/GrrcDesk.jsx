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
import { useTranslation } from 'react-i18next'
import { ShieldAlert, RefreshCw, Plus, Eye, Trash2 } from 'lucide-react'
import { grrcApi } from '../api/client'
import useRecordView from '../hooks/useRecordView'
import RowActionSlots from '../components/RowActionSlots'
import DeskShell from '../components/desk/DeskShell'
import DeskProcessSlot from '../components/desk/DeskProcessSlot'
import DeskNewFab from '../components/DeskNewFab'
import ConfirmDialog from '../components/ConfirmDialog'
import ContentRail from '../components/ContentRail'
import ContentChannelIcons from '../components/ContentChannelIcons'
import PendingContentComposer, {
  attachPendingContent,
} from '../components/PendingContentComposer'
import { fetchContentMap } from '../utils/contentSummary'
import useDeskSelection, { scrollSelectedIntoView } from '../hooks/useDeskSelection'
import useDeskMemory from '../hooks/useDeskMemory'
import useDeskKeyboard from '../hooks/useDeskKeyboard'
import useNarrowViewport from '../hooks/useNarrowViewport'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'
import ListSortHeader from '../components/ListSortHeader'
import CurrentUserOwnerField from '../components/CurrentUserOwnerField'
import { resolveCurrentUserOwnerLabel } from '../utils/currentUserOwner'

const PKG = 'grrc'
const RISK_RESOURCE = 'RiskItem'
const COMP_RESOURCE = 'ComplianceItem'

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
 * GRRCBasics desk (G7) — DeskShell master–detail (ADR-047 / Nielsen).
 */
const ROW_ACTION_CLASS = 'btn btn-ghost btn-danger'
const ACTION_TH = (t) => t('wb.action', { ns: 'common', defaultValue: 'Action' })

export default function GrrcDesk() {
  const { t } = useTranslation(['desks', 'common'])
  const narrow = useNarrowViewport()
  const getBelowFieldsRef = useRef(null)
  const { viewModal, openView } = useRecordView({
    modalEdit: true,
    canModalEdit: () => false,
    getBelowFields: (ctx) => getBelowFieldsRef.current?.(ctx) ?? null,
  })

  const [tab, setTab] = useState(() => {
    if (typeof window === 'undefined') return 'risks'
    const urlTab = new URLSearchParams(window.location.search).get('tab')
    return urlTab === 'compliance' ? 'compliance' : 'risks'
  })
  const [selectedId, setSelectedId] = useState(() => {
    if (typeof window === 'undefined') return null
    return new URLSearchParams(window.location.search).get('id') || null
  })
  const [newExpanded, setNewExpanded] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [pendingNav, setPendingNav] = useState(null)
  const [companyId] = useState('default')

  const { saveMemory } = useDeskMemory({ deskKey: 'grrc', companyId })

  useDeskSelection({
    deskKey: 'grrc',
    defaultTab: 'risks',
    tab,
    setTab,
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

  const [risks, setRisks] = useState([])
  const [compliance, setCompliance] = useState([])
  const [riskContentMap, setRiskContentMap] = useState({})
  const [compContentMap, setCompContentMap] = useState({})
  const [riskTotal, setRiskTotal] = useState(0)
  const [compTotal, setCompTotal] = useState(0)
  const [selected, setSelected] = useState(null)
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState({})
  const [riskForm, setRiskForm] = useState(emptyRisk)
  const [compForm, setCompForm] = useState(emptyCompliance)
  const [pendingRiskContent, setPendingRiskContent] = useState([])
  const [pendingCompContent, setPendingCompContent] = useState([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)
  const [info, setInfo] = useState(null)
  const [silverGate, setSilverGate] = useState(false)
  const [closeNote, setCloseNote] = useState('')
  const [riskSort, setRiskSort] = useState('id')
  const [riskOrder, setRiskOrder] = useState('desc')
  const [compSort, setCompSort] = useState('id')
  const [compOrder, setCompOrder] = useState('desc')
  const risksRef = useRef([])
  const complianceRef = useRef([])

  const onRiskSortChange = useCallback(({ sort: s, order: o }) => {
    setRiskSort(s || 'id')
    setRiskOrder(o === 'asc' ? 'asc' : 'desc')
  }, [])
  const onCompSortChange = useCallback(({ sort: s, order: o }) => {
    setCompSort(s || 'id')
    setCompOrder(o === 'asc' ? 'asc' : 'desc')
  }, [])

  const tabs = useMemo(
    () => [
      { id: 'risks', label: t('grrc.risks', { defaultValue: 'Risks' }) },
      { id: 'compliance', label: t('grrc.compliance', { defaultValue: 'Compliance' }) },
    ],
    [t],
  )

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [r, c] = await Promise.all([
        grrcApi.risks.list({ max: 50, offset: 0, sort: riskSort, order: riskOrder }),
        grrcApi.compliance.list({ max: 50, offset: 0, sort: compSort, order: compOrder }),
      ])
      const riskItems = r?.items || []
      const compItems = c?.items || []
      setRisks(riskItems)
      setRiskTotal(r?.total ?? riskItems.length)
      setCompliance(compItems)
      setCompTotal(c?.total ?? compItems.length)
      risksRef.current = riskItems
      complianceRef.current = compItems
      const [rMap, cMap] = await Promise.all([
        fetchContentMap(PKG, RISK_RESOURCE, riskItems),
        fetchContentMap(PKG, COMP_RESOURCE, compItems),
      ])
      setRiskContentMap(rMap)
      setCompContentMap(cMap)
      setSilverGate(false)
    } catch (e) {
      setRisks([])
      setCompliance([])
      setRiskTotal(0)
      setCompTotal(0)
      risksRef.current = []
      complianceRef.current = []
      setRiskContentMap({})
      setCompContentMap({})
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
  }, [riskSort, riskOrder, compSort, compOrder])

  useEffect(() => {
    load()
  }, [load])

  const items = tab === 'risks' ? risks : compliance

  useEffect(() => {
    setEditing(false)
    setCloseNote('')
    if (!selectedId) {
      setSelected(null)
      return
    }
    const row = items.find((x) => String(x.id) === String(selectedId))
    if (row) {
      setSelected(row)
      setCloseNote(row.treatmentNote || '')
    } else if (!loading) {
      setSelected(null)
    }
  }, [tab, selectedId, items, loading])

  useEffect(() => {
    if (!selectedId) return
    const id = window.requestAnimationFrame(() => {
      scrollSelectedIntoView('.desk-shell-list')
    })
    return () => window.cancelAnimationFrame(id)
  }, [selectedId, selected?.id, tab])

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

  const confirmDelete = (label) =>
    window.confirm(
      t('actions.confirmDelete', { ns: 'common', label, defaultValue: `Delete ${label}?` }),
    )

  const onRowDelete = useCallback(
    (kind, record) => {
      const label = record?.name || t('view.untitled', { ns: 'common', defaultValue: 'this record' })
      if (!confirmDelete(label)) return
      run(async () => {
        if (kind === 'risks') await grrcApi.risks.delete(record.id)
        else await grrcApi.compliance.delete(record.id)
        if (selectedId && String(selectedId) === String(record.id)) setSelectedId(null)
        setInfo(
          t('grrc.deleted', {
            defaultValue: 'Deleted {{name}}',
            name: label,
          }),
        )
      })
    },
    // run / selectedId stable enough via closure; load refreshes via run side-effects
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [selectedId, t],
  )

  const renderRowActions = useCallback(
    (kind, record) => {
      if (!record?.id && record?.id !== 0) return null
      const pkg = 'grrc'
      const resource = kind === 'risks' ? 'RiskItem' : 'ComplianceItem'
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
          edit={null}
          delete={
            <button
              type="button"
              className={ROW_ACTION_CLASS}
              title={t('actions.delete', { ns: 'common' })}
              aria-label={t('actions.delete', { ns: 'common' })}
              onClick={(e) => {
                e.stopPropagation()
                onRowDelete(kind, record)
              }}
            >
              <Trash2 size={14} />
            </button>
          }
        />
      )
    },
    [onRowDelete, openView, t],
  )

  const resetCreateForms = useCallback(() => {
    setRiskForm(emptyRisk())
    setCompForm(emptyCompliance())
    setPendingRiskContent([])
    setPendingCompContent([])
  }, [])

  const collapsedContentRail = (resource, id, onChanged) => (
    <details className="content-rail-collapse" style={{ marginTop: '1.25rem' }}>
      <summary className="btn btn-ghost" style={{ cursor: 'pointer', listStyle: 'inside' }}>
        {t('desk.attachmentsNotes', { ns: 'common', defaultValue: 'Attachments & notes' })}
      </summary>
      <ContentRail package={PKG} resource={resource} id={id} onChanged={onChanged} />
    </details>
  )

  getBelowFieldsRef.current = ({ resource, record }) => {
    if (!record?.id) return null
    if (resource === RISK_RESOURCE) {
      return collapsedContentRail(resource, record.id, async () =>
        setRiskContentMap(await fetchContentMap(PKG, RISK_RESOURCE, risksRef.current)),
      )
    }
    if (resource === COMP_RESOURCE) {
      return collapsedContentRail(resource, record.id, async () =>
        setCompContentMap(await fetchContentMap(PKG, COMP_RESOURCE, complianceRef.current)),
      )
    }
    return null
  }

  const createDirty = useMemo(() => {
    if (tab === 'risks') {
      const d = emptyRisk()
      return Boolean(
        (riskForm.name || '').trim() ||
          (riskForm.description || '').trim() ||
          riskForm.severity !== d.severity ||
          riskForm.likelihood !== d.likelihood ||
          (riskForm.treatmentNote || '').trim() ||
          pendingRiskContent.length,
      )
    }
    const d = emptyCompliance()
    return Boolean(
      (compForm.name || '').trim() ||
        (compForm.description || '').trim() ||
        compForm.itemKind !== d.itemKind ||
        compForm.status !== d.status ||
        compForm.effectiveFrom ||
        pendingCompContent.length,
    )
  }, [tab, riskForm, compForm, pendingRiskContent, pendingCompContent])

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
      setSelectedId(null)
      setTab(nextTab)
    },
    [tab, newExpanded, createDirty, resetCreateForms],
  )

  const keyboardIds = useMemo(() => items.map((i) => i.id), [items])

  useDeskKeyboard({
    enabled: !newExpanded && !silverGate,
    itemIds: keyboardIds,
    selectedId,
    setSelectedId,
    onToggleNew: requestToggleNew,
  })

  const confirmDiscard = useCallback(() => {
    const nav = pendingNav
    setDiscardOpen(false)
    setPendingNav(null)
    applyCollapseCreate()
    if (nav?.type === 'tab' && nav.tab) {
      setSelectedId(null)
      setTab(nav.tab)
    }
  }, [pendingNav, applyCollapseCreate])

  const cancelDiscard = useCallback(() => {
    setDiscardOpen(false)
    setPendingNav(null)
  }, [])

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
      <CurrentUserOwnerField
        id="grrc-risk-owner"
        label={t('grrc.fieldOwner', { defaultValue: 'Owner' })}
        value={form.ownerName || ''}
        onChange={(ownerName) => setForm((f) => ({ ...f, ownerName }))}
      />
      <label>
        {t('grrc.fieldDescription', { defaultValue: 'Description' })}
        <textarea
          rows={2}
          value={form.description || ''}
          onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
        />
      </label>
      <label>
        {t('grrc.fieldTreatment', { defaultValue: 'Mitigation plan' })}
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
        <CurrentUserOwnerField
          id="grrc-comp-owner"
          label={t('grrc.fieldOwner', { defaultValue: 'Owner' })}
          value={form.ownerName || ''}
          onChange={(ownerName) => setForm((f) => ({ ...f, ownerName }))}
        />
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

  const hasSelection = Boolean(selected?.id)

  const processCode = tab === 'risks' ? 'GRC_RISK_TREAT' : 'GRC_COMPLIANCE_ITEM'
  const processStatus = selected?.status ?? null
  const processSlot = (
    <DeskProcessSlot processCode={processCode} status={processStatus} />
  )

  const helpContext = useMemo(() => {
    const base = { desk: 'grrc', tab, processCode }
    if (selected) {
      return {
        ...base,
        resource: tab === 'risks' ? RISK_RESOURCE : COMP_RESOURCE,
        objectId: selected.id,
        status: selected.status,
      }
    }
    return base
  }, [tab, selected, processCode])

  const newLabel =
    tab === 'risks'
      ? t('grrc.newRisk', { defaultValue: 'New risk' })
      : t('grrc.newCompliance', { defaultValue: 'New compliance item' })

  const newPanel =
    tab === 'risks' ? (
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run(async () => {
            const ownerName = await resolveCurrentUserOwnerLabel(riskForm.ownerName)
            const created = await grrcApi.risks.create({ ...riskForm, ownerName: ownerName || null })
            if (pendingRiskContent.length && created?.id != null) {
              await attachPendingContent(PKG, RISK_RESOURCE, created.id, pendingRiskContent)
              setPendingRiskContent([])
            }
            setInfo(
              t('toast.namedCreated', {
                ns: 'common',
                what: t('grrc.riskLabel', { defaultValue: 'Risk' }),
                name: created.name,
              }),
            )
            resetCreateForms()
            setNewExpanded(false)
            setSelectedId(String(created.id))
          })
        }}
      >
        {riskFields(riskForm, setRiskForm)}
        <PendingContentComposer
          package={PKG}
          resource={RISK_RESOURCE}
          pending={pendingRiskContent}
          onChange={setPendingRiskContent}
        />
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} /> {t('grrc.createRisk', { defaultValue: 'Create risk' })}
        </button>
      </form>
    ) : (
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault()
          run(async () => {
            const ownerName = await resolveCurrentUserOwnerLabel(compForm.ownerName)
            const created = await grrcApi.compliance.create({
              ...compForm,
              ownerName: ownerName || null,
              effectiveFrom: compForm.effectiveFrom || null,
            })
            if (pendingCompContent.length && created?.id != null) {
              await attachPendingContent(PKG, COMP_RESOURCE, created.id, pendingCompContent)
              setPendingCompContent([])
            }
            setInfo(
              t('toast.namedCreated', {
                ns: 'common',
                what: t('grrc.complianceLabel', { defaultValue: 'Compliance item' }),
                name: created.name,
              }),
            )
            resetCreateForms()
            setNewExpanded(false)
            setSelectedId(String(created.id))
          })
        }}
      >
        {complianceFields(compForm, setCompForm)}
        <PendingContentComposer
          package={PKG}
          resource={COMP_RESOURCE}
          pending={pendingCompContent}
          onChange={setPendingCompContent}
        />
        <button type="submit" className="btn btn-primary" disabled={!!busy}>
          <Plus size={16} />{' '}
          {t('grrc.createCompliance', { defaultValue: 'Create compliance item' })}
        </button>
      </form>
    )

  const listTable = (cols, emptyKey, emptyDefault, actionKind, sortProps = null, contentMap = {}) =>
    items.length === 0 ? (
      <p className="muted">{t(emptyKey, { defaultValue: emptyDefault })}</p>
    ) : (
      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              {cols.map((c) =>
                sortProps && c.sortField ? (
                  <ListSortHeader
                    key={c.key}
                    field={c.sortField}
                    label={c.label}
                    sort={sortProps.sort}
                    order={sortProps.order}
                    onSortChange={sortProps.onSortChange}
                  />
                ) : (
                  <th key={c.key}>{c.label}</th>
                ),
              )}
              <th className="col-priority-low">{t('wb.content', { ns: 'common', defaultValue: 'Content' })}</th>
              <th>{ACTION_TH(t)}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((row) => (
              <tr
                key={row.id}
                data-selected={selected?.id === row.id ? 'true' : undefined}
                className={selected?.id === row.id ? 'row-selected' : 'row-click'}
                onClick={() => setSelectedId(String(row.id))}
              >
                {cols.map((c) => (
                  <td key={c.key}>{c.render(row)}</td>
                ))}
                <td className="col-priority-low" onClick={(e) => e.stopPropagation()}>
                  <ContentChannelIcons summary={contentMap[row.id]} />
                </td>
                <td style={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                  {renderRowActions(actionKind, row)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )

  const list =
    tab === 'risks' ? (
      <>
        {loading && !error && !silverGate ? (
          <p className="muted">{t('grrc.loading', { defaultValue: 'Loading…' })}</p>
        ) : (
          listTable(
            [
              {
                key: 'name',
                sortField: 'name',
                label: t('grrc.colName', { defaultValue: 'Name' }),
                render: (r) => entityDisplayLabel(r),
              },
              {
                key: 'status',
                sortField: 'status',
                label: t('grrc.colStatus', { defaultValue: 'Status' }),
                render: (r) => <span className={statusClass(r.status)}>{r.status || '—'}</span>,
              },
              {
                key: 'sev',
                sortField: 'severity',
                label: t('grrc.colSeverity', { defaultValue: 'Severity' }),
                render: (r) => r.severity || '—',
              },
            ],
            'grrc.emptyRisks',
            'No risks yet. Add one with New to start the register.',
            'risks',
            { sort: riskSort, order: riskOrder, onSortChange: onRiskSortChange },
            riskContentMap,
          )
        )}
      </>
    ) : (
      <>
        {loading && !error && !silverGate ? (
          <p className="muted">{t('grrc.loading', { defaultValue: 'Loading…' })}</p>
        ) : (
          listTable(
            [
              {
                key: 'name',
                sortField: 'name',
                label: t('grrc.colName', { defaultValue: 'Name' }),
                render: (r) => entityDisplayLabel(r),
              },
              {
                key: 'status',
                sortField: 'status',
                label: t('grrc.colStatus', { defaultValue: 'Status' }),
                render: (r) => <span className={statusClass(r.status)}>{r.status || '—'}</span>,
              },
              {
                key: 'kind',
                sortField: 'itemKind',
                label: t('grrc.colKind', { defaultValue: 'Kind' }),
                render: (r) => r.itemKind || '—',
              },
            ],
            'grrc.emptyCompliance',
            'No compliance items yet. Register a policy or control with New.',
            'compliance',
            { sort: compSort, order: compOrder, onSortChange: onCompSortChange },
            compContentMap,
          )
        )}
      </>
    )

  let detail = null
  if (selected && tab === 'risks') {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        {!editing && (
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
                {t('grrc.fieldTreatment', { defaultValue: 'Mitigation plan' })}
                <textarea
                  rows={2}
                  value={closeNote}
                  onChange={(e) => setCloseNote(e.target.value)}
                />
              </label>
            )}
            {selected.status === 'CLOSED' && selected.treatmentNote && (
              <p className="muted">
                <strong>{t('grrc.fieldTreatment', { defaultValue: 'Mitigation plan' })}:</strong>{' '}
                {selected.treatmentNote}
              </p>
            )}
            {collapsedContentRail(RISK_RESOURCE, selected.id, async () =>
              setRiskContentMap(await fetchContentMap(PKG, RISK_RESOURCE, risksRef.current)),
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
                      setSelectedId(String(closed.id))
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
                    setSelectedId(null)
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
        {editing && (
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault()
              run(async () => {
                const ownerName = await resolveCurrentUserOwnerLabel(editForm.ownerName)
                const updated = await grrcApi.risks.update(selected.id, {
                  ...editForm,
                  ownerName: ownerName || null,
                })
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
      </div>
    )
  } else if (selected && tab === 'compliance') {
    detail = (
      <div className="card" style={{ boxShadow: 'none', border: 'none', padding: 0 }}>
        {!editing && (
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
            {collapsedContentRail(COMP_RESOURCE, selected.id, async () =>
              setCompContentMap(await fetchContentMap(PKG, COMP_RESOURCE, complianceRef.current)),
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
                    setSelectedId(null)
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
        {editing && (
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault()
              run(async () => {
                const ownerName = await resolveCurrentUserOwnerLabel(editForm.ownerName)
                const updated = await grrcApi.compliance.update(selected.id, {
                  ...editForm,
                  ownerName: ownerName || null,
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
      </div>
    )
  }

  return (
    <div className="page">
      {error && <div className="banner-error">{error}</div>}
      {info && <div className="banner-info">{info}</div>}
      {viewModal}

      {silverGate && (
        <section className="card" style={{ marginBottom: '1rem' }}>
          <strong>{t('grrc.silverRequired', { defaultValue: 'Silver pack required' })}</strong>
          <p className="muted" style={{ margin: '0.5rem 0 0' }}>
            {t('grrc.silverRequiredBody', {
              defaultValue:
                'Governance is part of the Silver pack. Upgrade this company to Silver (or use Iron eval) to open the risk and compliance registers.',
            })}
          </p>
        </section>
      )}

      <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
        <button type="button" className="btn" onClick={load} disabled={loading || !!busy}>
          <RefreshCw size={16} /> {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
      </div>

      {!silverGate && (
        <>
          <DeskShell
            // ADR-050 Wave D: split for risk/compliance detail + close/edit workspace.
            layout="split"
            title={
              <>
                <ShieldAlert size={28} style={{ verticalAlign: 'middle', marginRight: 8 }} />
                {t('grrc.title', { defaultValue: 'Governance' })}
              </>
            }
            subtitle={t('grrc.subtitle', {
              defaultValue:
                'Risk, Resilience, and Compliance supporting Accountability, Transparency, Fairness, and Independence. Basics register. Silver pack / Iron eval.',
            })}
            tabs={tabs}
            activeTab={tab}
            onTabChange={requestTabChange}
            newLabel={newLabel}
            newExpanded={newExpanded}
            onToggleNew={requestToggleNew}
            newPanel={newPanel}
            list={list}
            hasSelection={hasSelection}
            emptyTitle={
              tab === 'risks'
                ? t('grrc.selectRisk', { defaultValue: 'Select a risk from the list.' })
                : t('grrc.selectCompliance', {
                    defaultValue: 'Select a compliance item from the list.',
                  })
            }
            emptyBody={t('desk.emptyBody', {
              ns: 'common',
              defaultValue: 'Choose a row from the list to see details.',
            })}
            detail={detail}
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
        </>
      )}
    </div>
  )
}
