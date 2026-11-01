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
import { Plus, RefreshCw } from 'lucide-react'
import { agentsApi, deliveryApi, domainsApi, meApi } from '../api/client'
import ResponsiveDataList from '../components/ResponsiveDataList'
import ListPager from '../components/ListPager'
import ListSortHeader from '../components/ListSortHeader'
import usePagedList from '../hooks/usePagedList'
import {
  buildAssigneeOptions,
  filterAssigneeOptionsForDevMode,
  formatAssigneeDisplay,
  humanNpubForUsername,
  splitPersonName,
} from '../utils/taskAssignee'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'

const LEVELS = [1, 2, 3]
const LEVEL_LABEL = { 1: 'Low', 2: 'Medium', 3: 'High' }
const OPEN_STATUSES = new Set([
  'OPEN',
  'IN_PROGRESS',
  '',
  null,
  undefined,
  'AI:new',
  'AI:conceptualizing',
  'AI:waiting-go',
  'AI:analyzing',
  'AI:fixing',
])

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
  if (task.status === 'DONE' || task.status === 'CANCELLED' || task.status === 'AI:done') return false
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
  const [sort, setSort] = useState('deadlineAt')
  const [order, setOrder] = useState('asc')
  const [assigneeOptions, setAssigneeOptions] = useState([])
  const [meUsername, setMeUsername] = useState(null)

  const TASK_SORT_MENU = useMemo(
    () => [
      { value: 'id', label: 'Id' },
      { value: 'name', label: 'Name' },
      { value: 'status', label: 'Status' },
      { value: 'deadlineAt', label: 'Deadline' },
      { value: 'urgency', label: 'Urgency' },
      { value: 'importance', label: 'Importance' },
      { value: 'assigneeHumanNpub', label: 'Assignee' },
    ],
    [],
  )

  const onSortChange = useCallback(({ sort: nextSort, order: nextOrder }) => {
    setSort(nextSort || 'id')
    setOrder(nextOrder === 'asc' ? 'asc' : 'desc')
  }, [])

  useEffect(() => {
    let cancelled = false
    Promise.all([
      meApi.get().catch(() => null),
      deliveryApi.resources.list({ assignees: true, max: 500, offset: 0 }).catch(() => ({ items: [] })),
      agentsApi.agents.list({ max: 200 }).catch(() => ({ items: [] })),
    ]).then(([me, res, ag]) => {
      if (cancelled) return
      const username = me?.username || null
      setMeUsername(username)
      const humans = Array.isArray(res?.items) ? res.items : []
      const agents = Array.isArray(ag?.items) ? ag.items : []
      const meNpub = humanNpubForUsername(username)
      const match = humans.find(
        (h) => humanNpubForUsername(h.personRef || h.username) === meNpub,
      )
      const meLabel = match?.name ? splitPersonName(match.name).label : username
      setAssigneeOptions(
        filterAssigneeOptionsForDevMode(
          buildAssigneeOptions({ humans, agents, meUsername: username, meLabel }),
          !!me?.developmentMode,
        ),
      )
    })
    return () => {
      cancelled = true
    }
  }, [])

  const assigneeLabel = useCallback(
    (row) =>
      formatAssigneeDisplay(row.assigneeHumanNpub, row.assigneeAgentId, {
        options: assigneeOptions,
        meUsername,
      }),
    [assigneeOptions, meUsername],
  )

  const filters = useMemo(() => {
    // Queue chips only — no page search (header FTS / ADR-050)
    const f = {
      sort: sort || 'deadlineAt',
      order: order === 'asc' ? 'asc' : 'desc',
    }
    if (filter && filter !== 'all') f.view = filter
    return f
  }, [filter, sort, order])

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

  // Board uses open tasks from current page (Eisenhower is a summary, not the paged list)
  const openForBoard = useMemo(() => items.filter(isOpen), [items])

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
        <Link className="btn btn-primary" to="/domains/core/Task/new?returnTo=%2Fwork">
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
          items={items}
          empty={t('pages.work.empty', { defaultValue: 'No tasks match this filter.' })}
          loading={loading}
          sortFields={TASK_SORT_MENU}
          sort={sort}
          order={order}
          onSortChange={onSortChange}
          defaultSort="deadlineAt"
          defaultOrder="asc"
          fields={[
            {
              id: 'name',
              primary: true,
              render: (row) => (
                <Link to={`/domains/core/Task/${row.id}?returnTo=%2Fwork`}>
                  {entityDisplayLabel(row, { fallback: 'Untitled task' })}
                </Link>
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
              render: (row) => assigneeLabel(row),
            },
          ]}
        >
          <div className="table-wrap card" style={{ padding: 0 }}>
            <table className="data">
              <thead>
                <tr>
                  <ListSortHeader
                    field="name"
                    label={t('wb.name', { defaultValue: 'Name' })}
                    sort={sort}
                    order={order}
                    defaultSort="deadlineAt"
                    defaultOrder="asc"
                    onSortChange={onSortChange}
                  />
                  <ListSortHeader
                    field="status"
                    label={t('wb.status', { defaultValue: 'Status' })}
                    sort={sort}
                    order={order}
                    defaultSort="deadlineAt"
                    defaultOrder="asc"
                    onSortChange={onSortChange}
                  />
                  <ListSortHeader
                    field="deadlineAt"
                    label={t('pages.work.deadline', { defaultValue: 'Deadline' })}
                    sort={sort}
                    order={order}
                    defaultSort="deadlineAt"
                    defaultOrder="asc"
                    onSortChange={onSortChange}
                  />
                  <ListSortHeader
                    field="urgency"
                    label={t('pages.work.urgency', { defaultValue: 'Urgency' })}
                    sort={sort}
                    order={order}
                    defaultSort="deadlineAt"
                    defaultOrder="asc"
                    onSortChange={onSortChange}
                    className="col-priority-low"
                  />
                  <ListSortHeader
                    field="importance"
                    label={t('pages.work.importance', { defaultValue: 'Importance' })}
                    sort={sort}
                    order={order}
                    defaultSort="deadlineAt"
                    defaultOrder="asc"
                    onSortChange={onSortChange}
                    className="col-priority-low"
                  />
                  <ListSortHeader
                    field="assigneeHumanNpub"
                    label={t('pages.work.assignee', { defaultValue: 'Assignee' })}
                    sort={sort}
                    order={order}
                    defaultSort="deadlineAt"
                    defaultOrder="asc"
                    onSortChange={onSortChange}
                    className="col-priority-low"
                  />
                </tr>
              </thead>
              <tbody>
                {items.length === 0 && (
                  <tr>
                    <td colSpan={6} className="muted">
                      {t('pages.work.empty', { defaultValue: 'No tasks match this filter.' })}
                    </td>
                  </tr>
                )}
                {items.map((row) => {
                  const due = formatDeadline(row.deadlineAt)
                  const late = isOverdue(row)
                  return (
                    <tr key={row.id}>
                      <td>
                        <Link to={`/domains/core/Task/${row.id}?returnTo=%2Fwork`}>
                          {entityDisplayLabel(row, { fallback: 'Untitled task' })}
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
                        {assigneeLabel(row)}
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
                        <Link to={`/domains/core/Task/${task.id}?returnTo=%2Fwork`}>
                          {entityDisplayLabel(task, { fallback: 'Untitled task' })}
                        </Link>
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
