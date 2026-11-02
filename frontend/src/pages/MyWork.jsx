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
import { Bot, Plus, RefreshCw } from 'lucide-react'
import { domainsApi } from '../api/client'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'
import { statusBadgeClass } from '../utils/humanize'

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

/** Enough tasks for a full Eisenhower board (list UI removed). */
const BOARD_MAX = 500

function levelKey(n) {
  const v = Number(n)
  if (v === 1 || v === 2 || v === 3) return v
  return 2
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
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  /** ADR-051 mode toggle — agentic AI:* queue vs human open board */
  const [agenticMode, setAgenticMode] = useState(false)

  const load = useCallback(() => {
    setError(null)
    setLoading(true)
    const view = agenticMode ? 'agentic' : 'open'
    domainsApi
      .list('core', 'Task', {
        max: BOARD_MAX,
        offset: 0,
        view,
        sort: 'deadlineAt',
        order: 'asc',
      })
      .then((body) => {
        const rows = Array.isArray(body?.items) ? body.items : Array.isArray(body) ? body : []
        setItems(rows)
      })
      .catch((e) => setError(e.message || String(e)))
      .finally(() => setLoading(false))
  }, [agenticMode])

  useEffect(() => {
    load()
  }, [load])

  const boardTasks = useMemo(
    () => (agenticMode ? items : items.filter(isOpen)),
    [items, agenticMode],
  )

  const cellMap = useMemo(() => {
    const map = {}
    for (const u of [3, 2, 1]) {
      for (const i of LEVELS) {
        map[`${u}-${i}`] = []
      }
    }
    boardTasks.forEach((task) => {
      const key = `${levelKey(task.urgency)}-${levelKey(task.importance)}`
      if (map[key]) map[key].push(task)
    })
    return map
  }, [boardTasks])

  return (
    <div>
      <h1 className="page-title">{t('pages.work.title', { defaultValue: 'My work' })}</h1>
      <p className="page-sub">
        {agenticMode
          ? t('pages.work.subtitleAgentic', {
              defaultValue:
                'Agentic tasks (AI:* statuses) awaiting human confirmation — Eisenhower board.',
            })
          : t('pages.work.subtitle', {
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
        <button
          type="button"
          className={`btn btn-toggle btn-toggle-agentic${agenticMode ? ' is-on' : ''}`}
          aria-pressed={agenticMode}
          title={
            agenticMode
              ? t('pages.work.agenticToggleHintOn', {
                  defaultValue:
                    'Showing agentic AI:* queue. Click to return to the human open board.',
                })
              : t('pages.work.agenticToggleHintOff', {
                  defaultValue:
                    'Show agentic AI:* tasks not yet confirmed DONE by a human. Click to switch the board.',
                })
          }
          onClick={() => setAgenticMode((v) => !v)}
        >
          <Bot size={16} aria-hidden="true" />
          {t('pages.work.agenticTasks', { defaultValue: 'Agentic Tasks' })}
        </button>
        <Link className="btn" to="/domains/core/Task">
          {t('core.Task', { ns: 'domain' })} {t('pages.dashboard.viewAll', { defaultValue: 'list' })}
        </Link>
      </div>

      {loading && items.length === 0 ? (
        <p className="muted">{t('actions.loading')}</p>
      ) : null}

      <div className="section-label" style={{ marginTop: '1.5rem' }}>
        {agenticMode
          ? t('pages.work.eisenhowerAgentic', { defaultValue: 'Agentic — Action NOW' })
          : t('pages.work.eisenhower', { defaultValue: 'Action - NOW' })}
      </div>

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
                className={`eisenhower-cell card${hot ? ' eisenhower-hot' : ''}${
                  agenticMode ? ' eisenhower-cell-agentic' : ''
                }`}
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
                        {agenticMode && task.status ? (
                          <span className={statusBadgeClass(task.status)} style={{ marginLeft: 4 }}>
                            {task.status}
                          </span>
                        ) : null}
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
