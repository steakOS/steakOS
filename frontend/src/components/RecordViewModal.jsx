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
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { X, Pencil } from 'lucide-react'
import { agentsApi, domainsApi } from '../api/client'
import { statusBadgeClass } from '../utils/humanize'
import { domainGroupLabel, domainResourceLabel } from '../utils/domainLabels'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'
import {
  filterViewEntries,
  isEmptyViewValue,
  readShowEmptyFieldsPref,
  writeShowEmptyFieldsPref,
} from '../utils/viewFieldEmpty'
import {
  TECHNICAL_VIEW_KEYS,
  chromeDuplicateKeys,
  viewFieldLabel,
  recordStatusValue,
  ensureKnownFields,
} from '../utils/viewFieldRules'

const HIDDEN_KEYS = new Set([
  'id',
  ...TECHNICAL_VIEW_KEYS,
])

function formatValue(value) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value, null, 2)
    } catch {
      return String(value)
    }
  }
  return String(value)
}

function sortedEntries(record, { pkg, resource } = {}) {
  if (!record || typeof record !== 'object') return []
  const chromeDup = chromeDuplicateKeys(pkg, resource)
  const keys = Object.keys(record).filter(
    (k) => !HIDDEN_KEYS.has(k) && !chromeDup.has(k) && !k.startsWith('_'),
  )
  // Merge assigneeAgentId + assigneeHumanNpub into a single logical assignee later
  const preferred = [
    'description',
    'deadlineAt',
    'urgency',
    'importance',
    'assigneeAgentId',
    'assigneeHumanNpub',
    'npubOwner',
    'startAt',
    'endAt',
  ]
  keys.sort((a, b) => {
    const ia = preferred.indexOf(a)
    const ib = preferred.indexOf(b)
    if (ia >= 0 || ib >= 0) {
      if (ia < 0) return 1
      if (ib < 0) return -1
      return ia - ib
    }
    return a.localeCompare(b)
  })
  return keys.map((k) => [k, record[k]])
}

/**
 * Read-only modal for a domain row (master data tables).
 * Chrome: breadcrumb + show-all + close | title + status | fields (no chrome repeats).
 */
export default function RecordViewModal({
  open,
  onClose,
  package: pkg,
  resource,
  recordId,
  seed,
  editPath,
  /** When set, Edit opens this callback (modal edit) instead of navigating via editPath. */
  onEdit,
  /** Optional nodes after the field grid (e.g. PO lines, ContentRail). */
  belowFields = null,
  /** Optional footer actions before Edit/Close (e.g. Send / Confirm / Receive). */
  footerExtra = null,
}) {
  const { t } = useTranslation(['common', 'domain'])
  const [record, setRecord] = useState(seed || null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [showAllFields, setShowAllFields] = useState(() => readShowEmptyFieldsPref())
  const [assigneeLabel, setAssigneeLabel] = useState(null)

  useEffect(() => {
    if (!open) return undefined
    setError(null)
    if (seed && seed.id === recordId) {
      setRecord(seed)
    }
    if (!pkg || !resource || !recordId) return undefined
    let cancelled = false
    setLoading(true)
    domainsApi
      .get(pkg, resource, recordId)
      .then((data) => {
        if (!cancelled) setRecord(data)
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e.message || String(e))
          if (seed) setRecord(seed)
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open, pkg, resource, recordId, seed])

  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => {
      if (e.key === 'Escape') onClose?.()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  // Resolve assignee agent display name (Task)
  useEffect(() => {
    if (!open || !record) {
      setAssigneeLabel(null)
      return undefined
    }
    const agentId = record.assigneeAgentId
    if (agentId == null || agentId === '') {
      setAssigneeLabel(null)
      return undefined
    }
    let cancelled = false
    agentsApi.agents
      .get(agentId)
      .then((agent) => {
        if (cancelled) return
        const label =
          entityDisplayLabel(agent, {
            keys: ['name', 'displayName', 'agentKey'],
            fallback: null,
          }) || null
        setAssigneeLabel(label)
      })
      .catch(() => {
        if (!cancelled) setAssigneeLabel(null)
      })
    return () => {
      cancelled = true
    }
  }, [open, record])

  const recordForFields = useMemo(
    () => ensureKnownFields(record, pkg, resource, { showAll: showAllFields }),
    [record, pkg, resource, showAllFields],
  )

  const rawEntries = useMemo(
    () => sortedEntries(recordForFields, { pkg, resource }),
    [recordForFields, pkg, resource],
  )

  /** Build display rows: merge assignees into one friendly “Assignee” value. */
  const displayEntries = useMemo(() => {
    const rows = []
    let assigneeEmitted = false
    const agentId = recordForFields?.assigneeAgentId
    const human = recordForFields?.assigneeHumanNpub
    const hasAgent = agentId != null && agentId !== ''
    const hasHuman = human != null && String(human).trim() !== ''

    for (const [key, value] of rawEntries) {
      if (key === 'assigneeAgentId' || key === 'assigneeHumanNpub') {
        if (assigneeEmitted) continue
        assigneeEmitted = true
        let display = null
        if (hasAgent) {
          if (assigneeLabel) display = assigneeLabel
          else if (showAllFields) display = `Agent #${agentId}`
          else continue // wait for name resolve
        } else if (hasHuman) {
          display = String(human).trim()
        } else if (showAllFields) {
          display = null // empty assignee row
        } else {
          continue
        }
        rows.push(['assignee', display])
        continue
      }
      rows.push([key, value])
    }
    return rows
  }, [rawEntries, recordForFields, assigneeLabel, showAllFields])

  const entries = useMemo(
    () => filterViewEntries(displayEntries, { showEmpty: showAllFields }),
    [displayEntries, showAllFields],
  )

  const hiddenEmptyCount = useMemo(() => {
    if (showAllFields) return 0
    return displayEntries.filter(([, v]) => isEmptyViewValue(v)).length
  }, [displayEntries, showAllFields])

  const onToggleShowAll = (e) => {
    const next = e.target.checked
    setShowAllFields(next)
    writeShowEmptyFieldsPref(next)
  }

  if (!open) return null

  const title = entityDisplayLabel(record, {
    fallback: t('view.untitled', { defaultValue: 'Untitled' }),
  })
  const statusVal = recordStatusValue(record)
  const crumbPkg = domainGroupLabel((keys, opts) => t(keys, { ns: 'common', ...opts }), pkg)
  const crumbRes = domainResourceLabel((keys, opts) => t(keys, { ns: 'domain', ...opts }), pkg, resource)

  return (
    <div
      className="error-dialog-backdrop content-view-backdrop"
      role="presentation"
      onClick={onClose}
    >
      <div
        className="content-view-dialog record-view-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="record-view-title"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 1) Top chrome: breadcrumb | show all fields + close */}
        <header className="content-view-header record-view-top">
          <nav className="record-view-breadcrumb" aria-label="Breadcrumb">
            <span className="record-view-crumb">{crumbPkg}</span>
            <span className="record-view-crumb-sep" aria-hidden="true">
              /
            </span>
            <span className="record-view-crumb">{crumbRes}</span>
          </nav>
          <div className="content-view-actions">
            <label className="record-view-show-empty">
              <input
                type="checkbox"
                checked={showAllFields}
                onChange={onToggleShowAll}
              />
              <span>
                {t('view.showAllFields', {
                  defaultValue: 'Show all fields',
                })}
              </span>
            </label>
            <button
              type="button"
              className="btn btn-ghost"
              title={t('actions.close')}
              onClick={onClose}
            >
              <X size={16} />
            </button>
          </div>
        </header>

        {/* 2) Identity: name + status pill */}
        <div className="record-view-identity">
          <h2 id="record-view-title" className="content-view-title">
            {title}
          </h2>
          {statusVal ? (
            <span className={statusBadgeClass(statusVal)}>{statusVal}</span>
          ) : null}
        </div>

        <div className="content-view-body">
          {loading && <p className="muted">{t('actions.loading')}</p>}
          {error && <div className="banner-error">{error}</div>}
          {!loading && record && (
            <>
              {!showAllFields && hiddenEmptyCount > 0 ? (
                <p className="muted record-view-empty-hint">
                  {t('view.hiddenEmptyHint', {
                    defaultValue: '{{count}} empty field(s) hidden',
                    count: hiddenEmptyCount,
                  })}
                </p>
              ) : null}
              <dl className="record-view-grid">
                {entries.map(([key, value]) => {
                  const display = formatValue(value)
                  const isJsonObject = value != null && typeof value === 'object'
                  const isMultilineText =
                    !isJsonObject &&
                    typeof display === 'string' &&
                    (display.includes('\n') || display.length > 80)
                  const isStatusKey = key === 'status' || /status/i.test(key)
                  return (
                    <div key={key} className="record-view-row">
                      <dt>{viewFieldLabel(key, (k, o) => t(k, { ns: 'common', ...o }))}</dt>
                      <dd
                        className={
                          isJsonObject
                            ? 'mono record-view-pre'
                            : isMultilineText
                              ? 'record-view-multiline'
                              : undefined
                        }
                      >
                        {isStatusKey && typeof value === 'string' ? (
                          <span className={statusBadgeClass(value)}>{value}</span>
                        ) : isJsonObject ? (
                          <pre className="record-view-pre-inner">{display}</pre>
                        ) : isMultilineText ? (
                          <div className="record-view-multiline-inner">{display}</div>
                        ) : (
                          display
                        )}
                      </dd>
                    </div>
                  )
                })}
              </dl>
              {entries.length === 0 ? (
                <p className="muted">
                  {t('view.allFieldsEmpty', {
                    defaultValue:
                      'No filled fields — enable “Show all fields” to see blanks.',
                  })}
                </p>
              ) : null}
              {belowFields}
            </>
          )}
          {!loading && !record && !error && (
            <p className="muted">{t('view.empty')}</p>
          )}
        </div>

        <footer className="content-view-footer">
          {footerExtra}
          {onEdit && (
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => {
                onEdit(record)
                onClose?.()
              }}
            >
              <Pencil size={14} /> {t('actions.edit')}
            </button>
          )}
          {!onEdit && editPath && (
            <Link className="btn btn-secondary" to={editPath} onClick={onClose}>
              <Pencil size={14} /> {t('actions.edit')}
            </Link>
          )}
          <button type="button" className="btn btn-primary" onClick={onClose}>
            {t('actions.close')}
          </button>
        </footer>
      </div>
    </div>
  )
}
