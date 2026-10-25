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
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { motion } from 'framer-motion'
import { ArrowRight, Plus } from 'lucide-react'
import { dashboardApi } from '../api/client'
import KpiRing from '../components/KpiRing'
import EisenhowerMini from '../components/EisenhowerMini'
import { primaryRoleLabel } from '../utils/humanize'
import { entityDisplayLabel } from '../utils/entityDisplayLabel'

function formatDeadline(iso) {
  if (!iso) return null
  try {
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return null
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
  } catch {
    return null
  }
}

function isOverdue(iso, status) {
  if (!iso || status === 'DONE' || status === 'CANCELLED') return false
  try {
    return new Date(iso) < new Date()
  } catch {
    return false
  }
}

export default function DashboardCeo() {
  const { t } = useTranslation('common')
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    dashboardApi
      .ceo()
      .then((d) => {
        if (!cancelled) setData(d)
      })
      .catch((e) => {
        if (!cancelled) setError(e.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (loading) {
    return <p className="muted">{t('actions.loading')}</p>
  }

  if (error) {
    return (
      <div className="banner-error">
        Could not load dashboard: {error}. Ensure you are logged in via{' '}
        <a href="/login/auth">/login/auth</a> on this origin (port 9001 proxy).
      </div>
    )
  }

  const user = data?.user || {}
  const role = primaryRoleLabel(user.authorities || [], {
    isAdmin: !!user.isAdmin,
    isCeo: !!user.isCeo,
  }) || user.displayRole || 'User'
  const myWork = data?.myWork || {}
  const tasks = myWork.tasks || {}
  const eisenhowerCells = myWork.eisenhower?.cells || []
  const groups = data?.apqcGroups || []
  const taskRows = (tasks.items || []).slice(0, 5)

  return (
    <div>
      <div className="dashboard-hero">
        <div>
          <h1 className="page-title">
            {t('pages.dashboard.greeting', { role })}
          </h1>
          <p className="page-sub">
            {t('pages.dashboard.subtitle', { role })}
          </p>
        </div>
      </div>

      <div className="section-label">{t('pages.dashboard.myWork')}</div>
      <div className="two-col dashboard-mywork">
        <motion.div
          className="card dashboard-work-card"
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
            <h3 style={{ margin: 0 }}>{t('pages.dashboard.tasks')}</h3>
            <div style={{ display: 'flex', gap: 6 }}>
              <span className="badge">
                {tasks.open ?? 0} {t('pages.dashboard.open')}
              </span>
              {(tasks.overdue ?? 0) > 0 && (
                <span className="badge badge-warn">
                  {tasks.overdue} {t('pages.dashboard.overdue', { defaultValue: 'overdue' })}
                </span>
              )}
            </div>
          </div>
          <div className="dashboard-task-list">
            {taskRows.length === 0 ? (
              <p className="muted" style={{ marginTop: '0.5rem', marginBottom: 0 }}>
                {t('pages.dashboard.noTasks', { defaultValue: 'No open tasks. Capture what needs doing.' })}
              </p>
            ) : (
              <ul className="dashboard-task-ul">
                {taskRows.map((row) => {
                  const due = formatDeadline(row.deadlineAt)
                  const late = isOverdue(row.deadlineAt, row.status)
                  return (
                    <li key={row.id}>
                      <Link to={`/domains/core/Task/${row.id}`}>
                        {entityDisplayLabel(row, { fallback: 'Untitled task' })}
                      </Link>
                      {due && (
                        <span
                          className={late ? 'badge badge-warn' : 'badge badge-muted'}
                          style={{ marginLeft: 6 }}
                        >
                          {due}
                        </span>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
          <div className="dashboard-work-footer">
            <Link className="btn btn-primary" to="/domains/core/Task/new">
              <Plus size={16} /> {t('pages.dashboard.newTask', { defaultValue: 'New task' })}
            </Link>
            <Link className="btn" to="/work">
              {t('pages.work.nav', { defaultValue: 'My work' })}
            </Link>
          </div>
        </motion.div>

        <motion.div
          className="card dashboard-work-card dashboard-eisenhower-card"
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.05 }}
        >
          <h3 style={{ margin: 0 }}>{t('pages.work.eisenhower', { defaultValue: 'Action - NOW' })}</h3>
          <div className="dashboard-eisenhower-body">
            <EisenhowerMini cells={eisenhowerCells} size={128} />
          </div>
          <div className="dashboard-work-footer dashboard-work-footer-end">
            <Link className="btn btn-primary" to="/work">
              {t('pages.work.openBoard', { defaultValue: 'Open board' })} <ArrowRight size={16} />
            </Link>
          </div>
        </motion.div>
      </div>

      <div className="section-label">
        {t('pages.dashboard.apqcPulse', { defaultValue: 'APQC pulse' })}
      </div>
      <div className="card-grid">
        {groups.map((g, i) => {
          const max = Math.max(...(g.metrics || []).map((m) => m.value), 5)
          return (
            <motion.div
              key={g.id}
              className="card pulse-card"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.03 * i }}
            >
              <h3>{t(`groups.${g.id}`, { defaultValue: g.label })}</h3>
              <div className="ring-wrap">
                <KpiRing value={g.total} max={max * (g.metrics?.length || 1)} />
                <div>
                  <div className="kpi-value">{g.total}</div>
                  <div className="muted" style={{ fontSize: '0.8rem' }}>
                    {t('pages.dashboard.totalRecords', { defaultValue: 'total records' })}
                  </div>
                </div>
              </div>
              {(g.metrics || []).slice(0, 3).map((m) => (
                <div className="metric-row" key={m.key}>
                  <span>{m.label}</span>
                  <strong>{m.value}</strong>
                </div>
              ))}
              <div className="actions">
                {(g.actions || []).map((a) => (
                  <Link key={a.route} className="btn btn-primary" to={a.route} style={{ fontSize: '0.8rem' }}>
                    {t(`pages.dashboard.actionByLabel.${a.label}`, {
                      defaultValue: a.label,
                    })}
                  </Link>
                ))}
              </div>
            </motion.div>
          )
        })}
      </div>
    </div>
  )
}
