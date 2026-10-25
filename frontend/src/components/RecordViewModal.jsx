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
import { domainsApi } from '../api/client'
import { humanizeLabel, statusBadgeClass } from '../utils/humanize'
import { domainGroupLabel, domainResourceLabel } from '../utils/domainLabels'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'
import {
  filterViewEntries,
  readShowEmptyFieldsPref,
  writeShowEmptyFieldsPref,
} from '../utils/viewFieldEmpty'

const HIDDEN_KEYS = new Set([
  'id',
  'class',
  'metaClass',
  'constraints',
  'errors',
  'attached',
  'dirty',
])

function formatFieldLabel(key) {
  return humanizeLabel(key)
}

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

function sortedEntries(record) {
  if (!record || typeof record !== 'object') return []
  const keys = Object.keys(record).filter((k) => !HIDDEN_KEYS.has(k) && !k.startsWith('_'))
  const preferred = ['name', 'code', 'status', 'npubOwner']
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
 * Mirrors ContentRail BODY viewer chrome.
 */
export default function RecordViewModal({
  open,
  onClose,
  package: pkg,
  resource,
  recordId,
  seed,
  editPath,
}) {
  const { t } = useTranslation(['common', 'domain'])
  const [record, setRecord] = useState(seed || null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [showEmpty, setShowEmpty] = useState(() => readShowEmptyFieldsPref())

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

  const allEntries = useMemo(() => sortedEntries(record), [record])
  const entries = useMemo(
    () => filterViewEntries(allEntries, { showEmpty }),
    [allEntries, showEmpty],
  )
  const hiddenEmptyCount = showEmpty ? 0 : Math.max(0, allEntries.length - entries.length)

  const onToggleShowEmpty = (e) => {
    const next = e.target.checked
    setShowEmpty(next)
    writeShowEmptyFieldsPref(next)
  }

  if (!open) return null

  const title = entityDisplayLabel(record, {
    fallback: t('view.untitled', { defaultValue: 'Untitled' }),
  })

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
        <header className="content-view-header">
          <div>
            <div className="content-view-meta">
              <span className="badge badge-muted">
                {domainGroupLabel((keys, opts) => t(keys, { ns: 'common', ...opts }), pkg)}
                {' / '}
                {domainResourceLabel((keys, opts) => t(keys, { ns: 'domain', ...opts }), pkg, resource)}
              </span>
              {record?.status || record?.invoiceStatus || record?.hiveStatus ? (
                <span
                  className={statusBadgeClass(
                    record.status || record.invoiceStatus || record.hiveStatus,
                  )}
                  style={{ marginLeft: 6 }}
                >
                  {record.status || record.invoiceStatus || record.hiveStatus}
                </span>
              ) : null}
            </div>
            <h2 id="record-view-title" className="content-view-title">
              {title}
            </h2>
          </div>
          <div className="content-view-actions">
            <label className="record-view-show-empty">
              <input
                type="checkbox"
                checked={showEmpty}
                onChange={onToggleShowEmpty}
              />
              <span>
                {t('view.showEmptyFields', {
                  defaultValue: 'Show empty fields',
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

        <div className="content-view-body">
          {loading && <p className="muted">{t('actions.loading')}</p>}
          {error && <div className="banner-error">{error}</div>}
          {!loading && record && (
            <>
              {!showEmpty && hiddenEmptyCount > 0 ? (
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
                  const isMultiline =
                    typeof value === 'object' ||
                    (typeof display === 'string' && (display.includes('\n') || display.length > 80))
                  const isStatusKey = /status/i.test(key)
                  return (
                    <div key={key} className="record-view-row">
                      <dt>{formatFieldLabel(key)}</dt>
                      <dd className={isMultiline ? 'mono record-view-pre' : undefined}>
                        {isStatusKey && typeof value === 'string' ? (
                          <span className={statusBadgeClass(value)}>{value}</span>
                        ) : isMultiline ? (
                          <pre className="record-view-pre-inner">{display}</pre>
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
                    defaultValue: 'No filled fields — enable “Show empty fields” to see blanks.',
                  })}
                </p>
              ) : null}
            </>
          )}
          {!loading && !record && !error && (
            <p className="muted">{t('view.empty')}</p>
          )}
        </div>

        <footer className="content-view-footer">
          {editPath && (
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
