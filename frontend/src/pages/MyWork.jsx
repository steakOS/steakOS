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

import { useCallback, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Plus, RefreshCw } from 'lucide-react'
import { domainsApi } from '../api/client'
import ResponsiveDataList from '../components/ResponsiveDataList'
import ListPager from '../components/ListPager'
import usePagedList from '../hooks/usePagedList'

const LEVELS = [1, 2, 3]
const LEVEL_LABEL = { 1: 'Low', 2: 'Medium', 3: 'High' }
const OPEN_STATUSES = new Set(['OPEN', 'IN_PROGRESS', '', null, undefined])

function levelKey(n) {
  const v = Number(n)
  if (v === 1 || v === 2 || v === 3) return v
  return 2
}

function formatDeadline(iso) {
  if (!iso) return null
  try {
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return null
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
  } catch {
    return null
  }
}

function isOverdue(task) {
  if (!task?.deadlineAt) return false
  if (task.status === 'DONE' || task.status === 'CANCELLED') return false
  try {
    return new Date(task.deadlineAt) < new Date()
  } catch {
    return false
  }
}

function isOpen(task) {
  return OPEN_STATUSES.has(task?.status)
}

export default function MyWork() {
  const { t } = useTranslation(['common', 'domain'])
  const [error, setError] = useState(null)
  // Server-side view filter (LIST-PAGINATION F1): open | overdue | mine | all
  const [filter, setFilter] = useState('open')
  const [q, setQ] = useState('')
  const [qDraft, setQDraft] = useState('')

  const filters = useMemo(() => {
    const f = {}
    if (filter && filter !== 'all') f.view = filter
    if (q.trim()) f.q = q.trim()
    return f
  }, [filter, q])

  const fetcher = useCallback(
    ({ max, offset, ...rest }) =>
      domainsApi.list('core', 'Task', { max, offset, ...rest }),
    [],
  )
  const {
    items,
    total,
    max,
    offset,
    loading,
    setOffset,
    setMax,
    reload,
  } = usePagedList({ fetcher, filters })

  const load = useCallback(() => {
    setError(null)
    reload().catch((e) => setError(e.message || String(e)))
  }, [reload])

  // Server returns filtered set; only client-sort for deadline/urgency display order.
  const filtered = useMemo(() => {
    return [...items].sort((a, b) => {
      const da = a.deadlineAt ? new Date(a.deadlineAt).getTime() : Number.MAX_SAFE_INTEGER
      const db = b.deadlineAt ? new Date(b.deadlineAt).getTime() : Number.MAX_SAFE_INTEGER
      if (da !== db) return da - db
      return levelKey(b.urgency) - levelKey(a.urgency)
    })
  }, [items])

  // Board uses open tasks from a parallel fetch when not already on open view
  const openForBoard = useMemo(() => {
    if (filter === 'open' && !q.trim()) return items.filter(isOpen)
    return items.filter(isOpen)
  }, [items, filter, q])

  const cellMap = useMemo(() => {
    const map = {}
    for (const u of [3, 2, 1]) {
      for (const i of LEVELS) {
        map[`${u}-${i}`] = []
      }
    }
    openForBoard.forEach((task) => {
      const key = `${levelKey(task.urgency)}-${levelKey(task.importance)}`
      if (map[key]) map[key].push(task)
    })
    return map
  }, [openForBoard])

  return (
    <div>
      <h1 className="page-title">{t('pages.work.title', { defaultValue: 'My work' })}</h1>
      <p className="page-sub">
        {t('pages.work.subtitle', {
          defaultValue: 'Open tasks, deadlines, and the Eisenhower board (urgency × importance).',
        })}
      </p>

      {error && <div className="banner-error">{error}</div>}

      <div className="toolbar">
        <Link className="btn btn-primary" to="/domains/core/Task/new">
          <Plus size={16} /> {t('pages.dashboard.newTask', { defaultValue: 'New task' })}
        </Link>
        <button type="button" className="btn" onClick={load} disabled={loading}>
          <RefreshCw size={16} /> {t('actions.refresh', { defaultValue: 'Refresh' })}
        </button>
        <Link className="btn" to="/domains/core/Task">
          {t('core.Task', { ns: 'domain' })} {t('pages.dashboard.viewAll', { defaultValue: 'list' })}
        </Link>
      </div>

      <div className="toolbar" style={{ gap: 8, flexWrap: 'wrap' }}>
        {[
          ['open', t('pages.work.filterOpen', { defaultValue: 'Open' })],
          ['overdue', t('pages.work.filterOverdue', { defaultValue: 'Overdue' })],
          ['mine', t('pages.work.filterAssigned', { defaultValue: 'Assigned' })],
          ['all', t('pages.work.filterAll', { defaultValue: 'All' })],
        ].map(([key, label]) => (
          <button
            key={key}
            type="button"
            className={filter === key ? 'btn btn-primary' : 'btn'}
            onClick={() => setFilter(key)}
          >
            {label}
          </button>
        ))}
        <form
          style={{ display: 'flex', gap: 6, alignItems: 'center', marginLeft: 'auto' }}
          onSubmit={(e) => {
            e.preventDefault()
            setQ(qDraft)
          }}
        >
          <input
            type="search"
            value={qDraft}
            onChange={(e) => setQDraft(e.target.value)}
            placeholder={t('pages.work.search', { defaultValue: 'Search tasks…' })}
            style={{ minWidth: 160 }}
          />
          <button type="submit" className="btn" disabled={loading}>
            {t('actions.search', { defaultValue: 'Search' })}
          </button>
          {(q || qDraft) && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setQDraft('')
                setQ('')
              }}
            >
              {t('actions.clear', { defaultValue: 'Clear' })}
            </button>
          )}
        </form>
      </div>

      <div className="section-label">
        {t('pages.work.list', { defaultValue: 'Task list' })}
        {total != null ? ` (${total})` : ''}
      </div>
      {loading && items.length === 0 ? (
        <p className="muted">{t('actions.loading')}</p>
      ) : (
        <>
        <ResponsiveDataList
          items={filtered}
          empty={t('pages.work.empty', { defaultValue: 'No tasks match this filter.' })}
          loading={loading}
          fields={[
            {
              id: 'name',
              primary: true,
              render: (row) => (
                <Link to={`/domains/core/Task/${row.id}`}>{row.name || `#${row.id}`}</Link>
              ),
            },
            {
              id: 'status',
              secondary: true,
              render: (row) => {
                const due = formatDeadline(row.deadlineAt)
                const late = isOverdue(row)
                return (
                  <>
                    {row.status || 'OPEN'}
                    {due ? (
                      <>
                        {' · '}
                        <span className={late ? 'badge badge-warn' : undefined}>{due}</span>
                      </>
                    ) : null}
                  </>
                )
              },
            },
            {
              id: 'eisenhower',
              meta: true,
              render: (row) =>
                `U ${LEVEL_LABEL[levelKey(row.urgency)]} · I ${LEVEL_LABEL[levelKey(row.importance)]}`,
            },
            {
              id: 'assignee',
              priority: 'low',
              render: (row) =>
                [row.assigneeHumanNpub, row.assigneeAgentId != null ? `agent:${row.assigneeAgentId}` : null]
                  .filter(Boolean)
                  .join(' · ') || '—',
            },
          ]}
        >
          <div className="table-wrap card" style={{ padding: 0 }}>
            <table className="data">
              <thead>
                <tr>
                  <th>{t('wb.name', { defaultValue: 'Name' })}</th>
                  <th>{t('wb.status', { defaultValue: 'Status' })}</th>
                  <th>{t('pages.work.deadline', { defaultValue: 'Deadline' })}</th>
                  <th className="col-priority-low">
                    {t('pages.work.urgency', { defaultValue: 'Urgency' })}
                  </th>
                  <th className="col-priority-low">
                    {t('pages.work.importance', { defaultValue: 'Importance' })}
                  </th>
                  <th className="col-priority-low">
                    {t('pages.work.assignee', { defaultValue: 'Assignee' })}
                  </th>
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={6} className="muted">
                      {t('pages.work.empty', { defaultValue: 'No tasks match this filter.' })}
                    </td>
                  </tr>
                )}
                {filtered.map((row) => {
                  const due = formatDeadline(row.deadlineAt)
                  const late = isOverdue(row)
                  return (
                    <tr key={row.id}>
                      <td>
                        <Link to={`/domains/core/Task/${row.id}`}>
                          {row.name || `#${row.id}`}
                        </Link>
                      </td>
                      <td>{row.status || 'OPEN'}</td>
                      <td>
                        {due ? (
                          <span className={late ? 'badge badge-warn' : undefined}>{due}</span>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="col-priority-low">{LEVEL_LABEL[levelKey(row.urgency)]}</td>
                      <td className="col-priority-low">
                        {LEVEL_LABEL[levelKey(row.importance)]}
                      </td>
                      <td className="muted col-priority-low" style={{ fontSize: '0.85rem' }}>
                        {[
                          row.assigneeHumanNpub,
                          row.assigneeAgentId != null ? `agent:${row.assigneeAgentId}` : null,
                        ]
                          .filter(Boolean)
                          .join(' · ') || '—'}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </ResponsiveDataList>
        <ListPager
          total={total}
          max={max}
          offset={offset}
          disabled={loading}
          onOffset={(o) => setOffset(o).catch((e) => setError(e.message || String(e)))}
          onMax={(m) => setMax(m).catch((e) => setError(e.message || String(e)))}
        />
        </>
      )}

      <div className="section-label" style={{ marginTop: '1.5rem' }}>
        {t('pages.work.eisenhower', { defaultValue: 'Action - NOW' })}
      </div>
      <p className="muted" style={{ marginTop: 0 }}>
        {t('pages.work.eisenhowerAxes', {
          defaultValue: 'Rows = urgency (high → low). Columns = importance (low → high). Open tasks only.',
        })}
      </p>

      <div className="eisenhower-grid">
        <div className="eisenhower-corner" />
        {LEVELS.map((i) => (
          <div key={`col-${i}`} className="eisenhower-axis">
            {t('pages.work.importance', { defaultValue: 'Importance' })}: {LEVEL_LABEL[i]}
          </div>
        ))}
        {[3, 2, 1].flatMap((u) => [
          <div key={`row-${u}`} className="eisenhower-axis">
            {t('pages.work.urgency', { defaultValue: 'Urgency' })}: {LEVEL_LABEL[u]}
          </div>,
          ...LEVELS.map((i) => {
            const cell = cellMap[`${u}-${i}`] || []
            const hot = u === 3 && i === 3
            return (
              <div
                key={`cell-${u}-${i}`}
                className={`eisenhower-cell card${hot ? ' eisenhower-hot' : ''}`}
              >
                <div className="muted" style={{ fontSize: '0.7rem', fontWeight: 700, marginBottom: 6 }}>
                  U{u} · I{i}
                  <span style={{ float: 'right' }}>{cell.length}</span>
                </div>
                {cell.length === 0 ? (
                  <div className="muted" style={{ fontSize: '0.8rem' }}>
                    —
                  </div>
                ) : (
                  <ul className="eisenhower-list">
                    {cell.map((task) => (
                      <li key={task.id}>
                        <Link to={`/domains/core/Task/${task.id}`}>{task.name || `#${task.id}`}</Link>
                        {isOverdue(task) && <span className="badge badge-warn">!</span>}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )
          }),
        ])}
      </div>
    </div>
  )
}
