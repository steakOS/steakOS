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

import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { agentsApi, deliveryApi, meApi } from '../api/client'
import {
  buildAssigneeOptions,
  decodeAssigneeValue,
  encodeAssigneeValue,
  filterAssigneeOptionsForDevMode,
  humanNpubForUsername,
  splitPersonName,
} from '../utils/taskAssignee'

/**
 * Single assignee dropdown for Tasks: Me (default) → active employees → [agent] ….
 * Writes assigneeHumanNpub / assigneeAgentId (mutually exclusive).
 */
export default function TaskAssigneeSelect({
  assigneeHumanNpub = '',
  assigneeAgentId = '',
  onChange,
  disabled = false,
  id = 'task-assignee',
  /** When true (new task), default selection to current user once options load. */
  defaultToMe = false,
  /** Override label; default i18n pages.work.assignee / 'Assignee'. */
  label,
}) {
  const { t } = useTranslation('common')
  const [meUsername, setMeUsername] = useState(null)
  const [developmentMode, setDevelopmentMode] = useState(false)
  const [humans, setHumans] = useState([])
  const [agents, setAgents] = useState([])
  const [loadError, setLoadError] = useState(null)
  const [didDefault, setDidDefault] = useState(false)
  const onChangeRef = useRef(onChange)
  onChangeRef.current = onChange
  const displayLabel =
    label != null && label !== ''
      ? label
      : t('pages.work.assignee', { defaultValue: 'Assignee' })

  useEffect(() => {
    let cancelled = false
    Promise.all([
      meApi.get().catch(() => null),
      deliveryApi.resources.list({ assignees: true, max: 500, offset: 0 }).catch(() => ({ items: [] })),
      agentsApi.agents.list({ max: 200 }).catch(() => ({ items: [] })),
    ]).then(([me, res, ag]) => {
      if (cancelled) return
      setMeUsername(me?.username || null)
      setDevelopmentMode(!!me?.developmentMode)
      setHumans(Array.isArray(res?.items) ? res.items : [])
      setAgents(Array.isArray(ag?.items) ? ag.items : [])
      setLoadError(null)
    }).catch((e) => {
      if (!cancelled) setLoadError(e?.message || String(e))
    })
    return () => {
      cancelled = true
    }
  }, [])

  const meLabel = useMemo(() => {
    if (!meUsername) return ''
    const meNpub = humanNpubForUsername(meUsername)
    const match = humans.find(
      (h) => humanNpubForUsername(h.personRef || h.username) === meNpub,
    )
    if (match?.name) return splitPersonName(match.name).label
    return meUsername
  }, [humans, meUsername])

  const options = useMemo(
    () =>
      filterAssigneeOptionsForDevMode(
        buildAssigneeOptions({ humans, agents, meUsername, meLabel }),
        developmentMode,
      ),
    [humans, agents, meUsername, meLabel, developmentMode],
  )

  const value = encodeAssigneeValue(assigneeHumanNpub, assigneeAgentId)

  // Default new tasks to Me once we know the current user
  useEffect(() => {
    if (!defaultToMe || didDefault || disabled) return
    if (!meUsername) return
    if (assigneeHumanNpub || assigneeAgentId) {
      setDidDefault(true)
      return
    }
    const decoded = decodeAssigneeValue(`human:${humanNpubForUsername(meUsername)}`)
    onChangeRef.current?.(decoded)
    setDidDefault(true)
  }, [defaultToMe, didDefault, disabled, meUsername, assigneeHumanNpub, assigneeAgentId])

  // If stored value is missing from options (stale user), keep a fallback option
  const optionsWithFallback = useMemo(() => {
    if (!value || options.some((o) => o.value === value)) return options
    const decoded = decodeAssigneeValue(value)
    let label = value
    if (decoded.assigneeAgentId) label = '[agent]'
    else if (decoded.assigneeHumanNpub?.startsWith('user:')) {
      label = decoded.assigneeHumanNpub.slice('user:'.length)
    } else if (decoded.assigneeHumanNpub) label = decoded.assigneeHumanNpub
    return [...options, { value, label, kind: 'human' }]
  }, [options, value])

  return (
    <label htmlFor={id}>
      {displayLabel}
      <select
        id={id}
        disabled={disabled}
        value={value}
        onChange={(e) => onChange?.(decodeAssigneeValue(e.target.value))}
      >
        <option value="">
          {t('pages.work.assigneeNone', { defaultValue: '— unassigned —' })}
        </option>
        {optionsWithFallback.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      {loadError ? (
        <span className="muted" style={{ display: 'block', marginTop: 4 }}>
          {t('pages.work.assigneeLoadError', {
            defaultValue: 'Could not load assignees.',
          })}
        </span>
      ) : null}
    </label>
  )
}
