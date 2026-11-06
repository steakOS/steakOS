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

import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { X } from 'lucide-react'
import { catalogueApi, domainsApi, salesApi, scmApi } from '../api/client'
import TaskAssigneeSelect from './TaskAssigneeSelect'
import DocumentLinesEditor, {
  emptyDocLine,
  linesToApiPayload,
} from './DocumentLinesEditor'
import ContentRail from './ContentRail'
import MasterEntityFields from '../forms/MasterEntityFields'
import { getMasterFormSpec } from '../forms/masterFormSpecs'
import { domainGroupLabel, domainResourceLabel } from '../utils/domainLabels'
import { isContentCompanionHost } from '../utils/contentCompanion'
import { statusBadgeClass } from '../utils/humanize'

/** Human-selectable Task statuses only. AI:* is set by Coder / Hermes via API. */
const TASK_STATUSES = ['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED']

const EISENHOWER_LEVELS = [
  { value: '1', label: '1 · Low' },
  { value: '2', label: '2 · Medium' },
  { value: '3', label: '3 · High' },
]

const EMPTY_TASK = {
  name: '',
  description: '',
  status: 'OPEN',
  assigneeHumanNpub: '',
  assigneeAgentId: '',
  startAt: '',
  endAt: '',
  deadlineAt: '',
  urgency: '2',
  importance: '2',
}

function isAiTaskStatus(status) {
  return typeof status === 'string' && status.startsWith('AI:')
}

function humanTaskStatusForForm(status) {
  if (!status || isAiTaskStatus(status)) return 'OPEN'
  return TASK_STATUSES.includes(status) ? status : 'OPEN'
}

function dateInputValue(iso) {
  if (!iso) return ''
  const s = String(iso)
  return s.length >= 10 ? s.slice(0, 10) : s
}

function formFromTaskRow(row) {
  const rawStatus = row?.status || 'OPEN'
  return {
    ...EMPTY_TASK,
    name: row?.name || '',
    description: row?.description || '',
    status: humanTaskStatusForForm(rawStatus),
    assigneeHumanNpub: row?.assigneeHumanNpub || '',
    assigneeAgentId: row?.assigneeAgentId != null ? String(row.assigneeAgentId) : '',
    startAt: dateInputValue(row?.startAt),
    endAt: dateInputValue(row?.endAt),
    deadlineAt: dateInputValue(row?.deadlineAt),
    urgency: row?.urgency != null ? String(row.urgency) : '2',
    importance: row?.importance != null ? String(row.importance) : '2',
  }
}

function taskBodyFromForm(form) {
  return {
    name: form.name,
    description: form.description || null,
    status: humanTaskStatusForForm(form.status),
    assigneeHumanNpub: form.assigneeHumanNpub || null,
    assigneeAgentId: form.assigneeAgentId === '' ? null : form.assigneeAgentId,
    startAt: form.startAt || null,
    endAt: form.endAt || null,
    deadlineAt: form.deadlineAt || null,
    urgency: form.urgency === '' ? 2 : Number(form.urgency),
    importance: form.importance === '' ? 2 : Number(form.importance),
  }
}

function linesFromRecord(row) {
  const src = row?.lines
  if (!Array.isArray(src) || src.length === 0) {
    return [emptyDocLine()]
  }
  return src.map((l) =>
    emptyDocLine({
      catalogueItemId: l.catalogueItemId != null ? String(l.catalogueItemId) : '',
      productRefId: l.productRefId != null ? String(l.productRefId) : '',
      name: l.name || '',
      quantity: l.quantity != null ? String(l.quantity) : '1',
      unitPriceEur: l.unitPriceEur != null ? String(l.unitPriceEur) : '0',
      taxCode: l.taxCode || '',
      sourceType: l.productRefType || '',
    }),
  )
}

function poStatusOf(row) {
  return row?.poStatus || row?.status || null
}

function soStatusOf(row) {
  return row?.orderStatus || row?.status || null
}

/**
 * Edit modal for domain records — same chrome behaviour as RecordViewModal
 * (backdrop, Escape, click-outside, breadcrumb top bar, identity row).
 * Supports Task (core), master-form resources (Vendor, CatalogueItem, …),
 * DRAFT PurchaseOrder and DRAFT SalesOrder (header + lines).
 */
export default function RecordEditModal({
  open,
  onClose,
  package: pkg,
  resource,
  recordId,
  seed,
  onSaved,
  /** Optional nodes after the field grid (e.g. quote line preview from desks). */
  belowFields = null,
  /**
   * When true (default), show ContentRail for companion hosts so content can be
   * added/edited after create. Set false when belowFields already includes a rail.
   */
  showContentRail = true,
}) {
  const { t } = useTranslation(['common', 'domain'])
  const isTask = pkg === 'core' && resource === 'Task'
  const masterSpec = useMemo(
    () => (isTask ? null : getMasterFormSpec(pkg, resource)),
    [isTask, pkg, resource],
  )
  const isMaster = !!masterSpec
  const isPurchaseOrder = resource === 'PurchaseOrder'
  const isSalesOrder = resource === 'SalesOrder'
  const isLineDocument = isPurchaseOrder || isSalesOrder
  const isCatalogueItem = resource === 'CatalogueItem'
  const isOpportunity = pkg === 'sales' && resource === 'Opportunity'

  const [form, setForm] = useState(() =>
    isTask ? formFromTaskRow(seed || {}) : masterSpec ? masterSpec.fromRow(seed || {}, masterSpec.empty()) : {},
  )
  const [lines, setLines] = useState(() => (isLineDocument ? linesFromRecord(seed) : []))
  const [recordMeta, setRecordMeta] = useState(seed || null)
  const [agentStatusHint, setAgentStatusHint] = useState(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const docStatus = isPurchaseOrder
    ? poStatusOf(recordMeta)
    : isSalesOrder
      ? soStatusOf(recordMeta)
      : null
  const docEditable = !isLineDocument || docStatus === 'DRAFT'
  const contentRail =
    showContentRail &&
    open &&
    recordId != null &&
    isContentCompanionHost(pkg, resource) ? (
      <div className="record-edit-content-rail" style={{ marginTop: '1rem' }}>
        <ContentRail package={pkg} resource={resource} id={recordId} />
      </div>
    ) : null

  useEffect(() => {
    if (!open) return undefined
    setError(null)

    if (isTask) {
      if (!pkg || !resource || recordId == null) {
        setForm(EMPTY_TASK)
        setAgentStatusHint(null)
        return undefined
      }
      if (seed && seed.id === recordId) {
        const raw = seed.status || 'OPEN'
        setAgentStatusHint(isAiTaskStatus(raw) ? raw : null)
        setForm(formFromTaskRow(seed))
      }
      let cancelled = false
      setLoading(true)
      domainsApi
        .get(pkg, resource, recordId)
        .then((row) => {
          if (cancelled) return
          const raw = row.status || 'OPEN'
          setAgentStatusHint(isAiTaskStatus(raw) ? raw : null)
          setForm(formFromTaskRow(row))
        })
        .catch((e) => {
          if (!cancelled) {
            setError(e.message || String(e))
            if (seed) setForm(formFromTaskRow(seed))
          }
        })
        .finally(() => {
          if (!cancelled) setLoading(false)
        })
      return () => {
        cancelled = true
      }
    }

    if (!isMaster || !pkg || !resource || recordId == null) {
      setForm({})
      setLines([])
      setRecordMeta(null)
      return undefined
    }

    const spec = masterSpec
    if (seed && seed.id === recordId) {
      setForm(spec.fromRow(seed, spec.empty()))
      setRecordMeta(seed)
      if (isLineDocument) setLines(linesFromRecord(seed))
    }

    let cancelled = false
    setLoading(true)

    const loadRow = async () => {
      try {
        let row
        if (isOpportunity) {
          // CRM map includes stage, amounts, contact — domains toMap omits several fields.
          row = await salesApi.crm.getOpportunity(recordId)
        } else if (isSalesOrder) {
          // Sales API map includes lines; domains get often omits them.
          row = await salesApi.orders.get(recordId)
        } else {
          row = await domainsApi.get(pkg, resource, recordId)
        }
        if (isPurchaseOrder) {
          // domains get may omit lines — prefer SCM full map when needed
          const seedLines = seed?.id === recordId ? seed.lines : null
          if (!Array.isArray(row?.lines) || row.lines.length === 0) {
            try {
              const full = await scmApi.purchaseOrders.get(recordId)
              row = { ...row, ...full, lines: full.lines || seedLines || row.lines }
            } catch {
              if (seedLines) row = { ...row, lines: seedLines }
            }
          }
        }
        if (cancelled) return
        setRecordMeta(row)
        setForm(spec.fromRow(row, spec.empty()))
        if (isLineDocument) setLines(linesFromRecord(row))
      } catch (e) {
        if (cancelled) return
        setError(e.message || String(e))
        if (seed) {
          setRecordMeta(seed)
          setForm(spec.fromRow(seed, spec.empty()))
          if (isLineDocument) setLines(linesFromRecord(seed))
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    loadRow()
    return () => {
      cancelled = true
    }
  }, [
    open,
    pkg,
    resource,
    recordId,
    seed,
    isTask,
    isMaster,
    isPurchaseOrder,
    isSalesOrder,
    isLineDocument,
    isOpportunity,
    masterSpec,
  ])

  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => {
      if (e.key === 'Escape' && !saving) onClose?.()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose, saving])

  const onChange = (e) => {
    const { name, value, type, checked } = e.target
    setForm((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
  }

  const onSubmitTask = async (e) => {
    e.preventDefault()
    if (!isTask || recordId == null || saving) return
    setError(null)
    setSaving(true)
    try {
      const updated = await domainsApi.update(pkg, resource, recordId, taskBodyFromForm(form))
      onSaved?.(updated || { id: recordId, ...taskBodyFromForm(form) })
      onClose?.()
    } catch (err) {
      setError(err.message || String(err))
    } finally {
      setSaving(false)
    }
  }

  const onSubmitMaster = async (e) => {
    e.preventDefault()
    if (!isMaster || recordId == null || saving || !docEditable) return
    setError(null)
    setSaving(true)
    try {
      const body = masterSpec.toBody(form)
      // Status transitions for SO/PO go through confirm/cancel APIs — keep header DRAFT here.
      if (isSalesOrder && body.orderStatus) delete body.orderStatus
      if (isPurchaseOrder && body.poStatus) delete body.poStatus
      let updated
      try {
        if (isOpportunity) {
          // Prefer CRM update for stage/status sync and WON/LOST guards.
          updated = await salesApi.crm.updateOpportunity(recordId, body)
        } else {
          updated = await domainsApi.update(pkg, resource, recordId, body)
        }
      } catch (err) {
        if (isCatalogueItem) {
          updated = await catalogueApi.update(recordId, body)
        } else {
          throw err
        }
      }

      if (isLineDocument) {
        const payload = linesToApiPayload(lines)
        if (!payload.length) {
          setError(
            t('docLines.needOne', {
              ns: 'common',
              defaultValue: 'Add at least one line.',
            }),
          )
          setSaving(false)
          return
        }
        if (isPurchaseOrder) {
          updated = await scmApi.purchaseOrders.setLines(recordId, { lines: payload })
        } else if (isSalesOrder) {
          updated = await salesApi.orders.setLines(recordId, payload)
        }
      }

      onSaved?.(updated || { id: recordId, ...body })
      onClose?.()
    } catch (err) {
      setError(err.message || String(err))
    } finally {
      setSaving(false)
    }
  }

  if (!open) return null

  const crumbPkg = domainGroupLabel((keys, opts) => t(keys, { ns: 'common', ...opts }), pkg)
  const crumbRes = domainResourceLabel(
    (keys, opts) => t(keys, { ns: 'domain', ...opts }),
    pkg,
    resource,
  )
  const statusVal = isTask
    ? form.status || null
    : isLineDocument
      ? docStatus
      : null

  const fieldsSpecForRender = masterSpec
    ? {
        ...masterSpec,
        fields: (masterSpec.fields || []).filter((f) => {
          if (f.name === 'name') return false
          // Status shown as badge; confirm/cancel stay on the desk actions.
          if (isSalesOrder && f.name === 'orderStatus') return false
          if (isPurchaseOrder && (f.name === 'poStatus' || f.name === 'status')) return false
          return true
        }),
      }
    : null

  const unsupported = !isTask && !isMaster

  return (
    <div
      className="error-dialog-backdrop content-view-backdrop"
      role="presentation"
      onClick={() => {
        if (!saving) onClose?.()
      }}
    >
      <div
        className="content-view-dialog record-view-dialog record-edit-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="record-edit-title"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="content-view-header record-view-top">
          <nav className="record-view-breadcrumb" aria-label="Breadcrumb">
            <span className="record-view-crumb">{crumbPkg}</span>
            <span className="record-view-crumb-sep" aria-hidden="true">
              /
            </span>
            <span className="record-view-crumb">{crumbRes}</span>
          </nav>
          <div className="content-view-actions">
            <button
              type="button"
              className="btn btn-ghost"
              title={t('actions.close')}
              disabled={saving}
              onClick={onClose}
            >
              <X size={16} />
            </button>
          </div>
        </header>

        {unsupported ? (
          <div className="content-view-body">
            <p className="muted">
              {t('edit.unsupportedResource', {
                defaultValue: 'Modal edit is not available for this resource yet.',
              })}
            </p>
            {contentRail}
            <footer className="content-view-footer" style={{ marginTop: '1rem' }}>
              <button type="button" className="btn btn-primary" onClick={onClose}>
                {t('actions.close')}
              </button>
            </footer>
          </div>
        ) : isLineDocument && !docEditable ? (
          <div className="content-view-body">
            <p className="muted">
              {isSalesOrder
                ? t('edit.soNotDraft', {
                    defaultValue:
                      'Only DRAFT sales orders can be edited here. Status: {{status}}.',
                    status: docStatus || '—',
                  })
                : t('edit.poNotDraft', {
                    defaultValue:
                      'Only DRAFT purchase orders can be edited here. Status: {{status}}.',
                    status: docStatus || '—',
                  })}
            </p>
            {contentRail}
            <footer className="content-view-footer" style={{ marginTop: '1rem' }}>
              <button type="button" className="btn btn-primary" onClick={onClose}>
                {t('actions.close')}
              </button>
            </footer>
          </div>
        ) : isTask ? (
          <>
          <form className="record-edit-form" onSubmit={onSubmitTask}>
            <div className="record-view-identity record-edit-identity">
              <label className="record-edit-name-label" htmlFor="record-edit-name">
                <span className="sr-only">
                  {t('view.fields.name', { defaultValue: 'Name' })}
                </span>
                <input
                  id="record-edit-name"
                  name="name"
                  className="record-edit-name-input content-view-title"
                  value={form.name}
                  onChange={onChange}
                  required
                  disabled={loading || saving}
                  placeholder={t('view.untitled', { defaultValue: 'Untitled' })}
                  aria-labelledby="record-edit-title"
                />
                <span id="record-edit-title" className="sr-only">
                  {t('actions.edit')} {form.name || crumbRes}
                </span>
              </label>
              {statusVal ? (
                <span className={statusBadgeClass(statusVal)} aria-hidden="true">
                  {statusVal}
                </span>
              ) : null}
            </div>

            <div className="content-view-body">
              {loading && <p className="muted">{t('actions.loading')}</p>}
              {error && <div className="banner-error">{error}</div>}

              <div className="form-grid record-edit-fields">
                <label>
                  {t('view.uniqueId', {
                    ns: 'common',
                    defaultValue: 'Unique ID',
                  })}
                  <input
                    type="text"
                    className="mono"
                    value={recordId != null ? String(recordId) : ''}
                    readOnly
                    disabled
                    aria-readonly="true"
                    title={t('view.uniqueIdHint', {
                      ns: 'common',
                      defaultValue: 'System identifier for this record. Read-only when editing.',
                    })}
                  />
                </label>

                <label>
                  {t('pages.work.status', { ns: 'common', defaultValue: 'Status' })}
                  <select
                    name="status"
                    value={humanTaskStatusForForm(form.status)}
                    onChange={onChange}
                    disabled={loading || saving}
                  >
                    {TASK_STATUSES.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                  {agentStatusHint ? (
                    <span
                      className="muted"
                      style={{ display: 'block', fontSize: '0.75rem', marginTop: '0.25rem' }}
                    >
                      {t('pages.work.agentStatusHint', {
                        ns: 'common',
                        defaultValue:
                          'Agent status {{status}} (Coder/Hermes). Not selectable here — saving applies the human status above.',
                        status: agentStatusHint,
                      })}
                    </span>
                  ) : null}
                </label>

                <label>
                  {t('view.fields.description', { defaultValue: 'Description' })}
                  <textarea
                    name="description"
                    value={form.description}
                    onChange={onChange}
                    rows={3}
                    disabled={loading || saving}
                  />
                </label>

                <div className="form-row-2">
                  <label>
                    {t('pages.work.deadline', { ns: 'common', defaultValue: 'Deadline' })}
                    <input
                      type="date"
                      name="deadlineAt"
                      value={form.deadlineAt}
                      onChange={onChange}
                      disabled={loading || saving}
                    />
                  </label>
                  <label>
                    {t('pages.work.startAt', { ns: 'common', defaultValue: 'Start' })}
                    <input
                      type="date"
                      name="startAt"
                      value={form.startAt}
                      onChange={onChange}
                      disabled={loading || saving}
                    />
                  </label>
                </div>

                <div className="form-row-2">
                  <label>
                    {t('pages.work.endAt', { ns: 'common', defaultValue: 'End' })}
                    <input
                      type="date"
                      name="endAt"
                      value={form.endAt}
                      onChange={onChange}
                      disabled={loading || saving}
                    />
                  </label>
                  <span />
                </div>

                <div className="form-row-2">
                  <label>
                    {t('pages.work.urgency', { ns: 'common', defaultValue: 'Urgency' })}
                    <select
                      name="urgency"
                      value={form.urgency}
                      onChange={onChange}
                      disabled={loading || saving}
                    >
                      {EISENHOWER_LEVELS.map((l) => (
                        <option key={l.value} value={l.value}>
                          {l.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    {t('pages.work.importance', { ns: 'common', defaultValue: 'Importance' })}
                    <select
                      name="importance"
                      value={form.importance}
                      onChange={onChange}
                      disabled={loading || saving}
                    >
                      {EISENHOWER_LEVELS.map((l) => (
                        <option key={l.value} value={l.value}>
                          {l.label}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>

                <TaskAssigneeSelect
                  assigneeHumanNpub={form.assigneeHumanNpub}
                  assigneeAgentId={form.assigneeAgentId}
                  defaultToMe={false}
                  disabled={loading || saving}
                  onChange={({ assigneeHumanNpub, assigneeAgentId }) =>
                    setForm((f) => ({
                      ...f,
                      assigneeHumanNpub: assigneeHumanNpub || '',
                      assigneeAgentId: assigneeAgentId || '',
                    }))
                  }
                />
              </div>
              {belowFields}
            </div>

            <footer className="content-view-footer">
              <button
                type="button"
                className="btn btn-ghost"
                disabled={saving}
                onClick={onClose}
              >
                {t('actions.cancel')}
              </button>
              <button type="submit" className="btn btn-primary" disabled={loading || saving}>
                {saving
                  ? t('actions.saving', { defaultValue: 'Saving…' })
                  : t('actions.save', { defaultValue: 'Save' })}
              </button>
            </footer>
          </form>
          {/* Outside the record form — ContentRail has its own forms (no nested <form>). */}
          {contentRail}
        </>
        ) : (
          <>
          <form className="record-edit-form" onSubmit={onSubmitMaster}>
            <div className="record-view-identity record-edit-identity">
              <label className="record-edit-name-label" htmlFor="record-edit-name">
                <span className="sr-only">
                  {t('view.fields.name', { defaultValue: 'Name' })}
                </span>
                <input
                  id="record-edit-name"
                  name="name"
                  className="record-edit-name-input content-view-title"
                  value={form.name || ''}
                  onChange={onChange}
                  required
                  disabled={loading || saving}
                  placeholder={t('view.untitled', { defaultValue: 'Untitled' })}
                  aria-labelledby="record-edit-title"
                />
                <span id="record-edit-title" className="sr-only">
                  {t('actions.edit')} {form.name || crumbRes}
                </span>
              </label>
              {statusVal ? (
                <span className={statusBadgeClass(statusVal)} aria-hidden="true">
                  {statusVal}
                </span>
              ) : null}
            </div>

            <div className="content-view-body">
              {loading && <p className="muted">{t('actions.loading')}</p>}
              {error && <div className="banner-error">{error}</div>}

              <div className="form-grid record-edit-fields">
                <label>
                  {t('view.uniqueId', {
                    ns: 'common',
                    defaultValue: 'Unique ID',
                  })}
                  <input
                    type="text"
                    className="mono"
                    value={recordId != null ? String(recordId) : ''}
                    readOnly
                    disabled
                    aria-readonly="true"
                    title={t('view.uniqueIdHint', {
                      ns: 'common',
                      defaultValue: 'System identifier for this record. Read-only when editing.',
                    })}
                  />
                </label>
                <MasterEntityFields
                  spec={fieldsSpecForRender}
                  form={form}
                  onChange={onChange}
                  disabled={loading || saving}
                  showDescription
                />
              </div>

              {isLineDocument ? (
                <div style={{ marginTop: '1rem' }}>
                  <DocumentLinesEditor
                    lines={lines}
                    onChange={setLines}
                    forChannel={isSalesOrder ? 'sale' : 'purchase'}
                    disabled={loading || saving}
                  />
                </div>
              ) : null}
              {belowFields}
            </div>

            <footer className="content-view-footer">
              <button
                type="button"
                className="btn btn-ghost"
                disabled={saving}
                onClick={onClose}
              >
                {t('actions.cancel')}
              </button>
              <button type="submit" className="btn btn-primary" disabled={loading || saving}>
                {saving
                  ? t('actions.saving', { defaultValue: 'Saving…' })
                  : t('actions.save', { defaultValue: 'Save' })}
              </button>
            </footer>
          </form>
          {/* Outside the record form — ContentRail has its own forms (no nested <form>). */}
          {contentRail}
          </>
        )}
      </div>
    </div>
  )
}
