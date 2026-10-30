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

import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { X } from 'lucide-react'
import { domainsApi } from '../api/client'
import TaskAssigneeSelect from './TaskAssigneeSelect'
import { domainGroupLabel, domainResourceLabel } from '../utils/domainLabels'
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

/**
 * Edit modal for domain records — same chrome behaviour as RecordViewModal
 * (backdrop, Escape, click-outside, breadcrumb top bar, identity row).
 * Task (core) is the first supported resource; others fall back to a message.
 */
export default function RecordEditModal({
  open,
  onClose,
  package: pkg,
  resource,
  recordId,
  seed,
  onSaved,
}) {
  const { t } = useTranslation(['common', 'domain'])
  const isTask = pkg === 'core' && resource === 'Task'
  const [form, setForm] = useState(() => formFromTaskRow(seed || {}))
  const [agentStatusHint, setAgentStatusHint] = useState(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!open) return undefined
    setError(null)
    if (!isTask || !pkg || !resource || recordId == null) {
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
  }, [open, pkg, resource, recordId, seed, isTask])

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

  const onSubmit = async (e) => {
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

  if (!open) return null

  const crumbPkg = domainGroupLabel((keys, opts) => t(keys, { ns: 'common', ...opts }), pkg)
  const crumbRes = domainResourceLabel(
    (keys, opts) => t(keys, { ns: 'domain', ...opts }),
    pkg,
    resource,
  )
  const statusVal = form.status || null

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

        {!isTask ? (
          <div className="content-view-body">
            <p className="muted">
              {t('edit.unsupportedResource', {
                defaultValue: 'Modal edit is not available for this resource yet.',
              })}
            </p>
            <footer className="content-view-footer" style={{ marginTop: '1rem' }}>
              <button type="button" className="btn btn-primary" onClick={onClose}>
                {t('actions.close')}
              </button>
            </footer>
          </div>
        ) : (
          <form className="record-edit-form" onSubmit={onSubmit}>
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
        )}
      </div>
    </div>
  )
}
