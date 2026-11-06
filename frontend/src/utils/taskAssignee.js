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

/** Task.assigneeHumanNpub value for a login username. */
export function humanNpubForUsername(username) {
  const u = String(username || '').trim()
  if (!u) return ''
  if (u.startsWith('user:') || u.startsWith('npub')) return u
  return `user:${u}`
}

/**
 * Split a display name into first / last for labels and sort.
 * HR only stores a single name string today.
 */
export function splitPersonName(name) {
  const raw = String(name || '').trim().replace(/\s+/g, ' ')
  if (!raw) return { firstName: '', lastName: '', label: '', sortKey: '' }
  const parts = raw.split(' ')
  const firstName = parts[0] || ''
  const lastName = parts.slice(1).join(' ')
  const label = lastName ? `${firstName} ${lastName}` : firstName
  const sortKey = `${lastName.toLowerCase()}\u0000${firstName.toLowerCase()}`
  return { firstName, lastName, label, sortKey }
}

/** Encode dropdown value from the two Task fields. */
export function encodeAssigneeValue(assigneeHumanNpub, assigneeAgentId) {
  if (assigneeAgentId != null && String(assigneeAgentId).trim() !== '') {
    return `agent:${assigneeAgentId}`
  }
  const h = String(assigneeHumanNpub || '').trim()
  if (h) return `human:${h}`
  return ''
}

/** Decode dropdown value → Task field pair (mutually exclusive). */
export function decodeAssigneeValue(value) {
  const v = String(value || '').trim()
  if (!v) return { assigneeHumanNpub: '', assigneeAgentId: '' }
  if (v.startsWith('agent:')) {
    return { assigneeHumanNpub: '', assigneeAgentId: v.slice('agent:'.length) }
  }
  if (v.startsWith('human:')) {
    return { assigneeHumanNpub: v.slice('human:'.length), assigneeAgentId: '' }
  }
  // Legacy bare npub / user:… pasted values
  return { assigneeHumanNpub: v, assigneeAgentId: '' }
}

/**
 * Build select options: Me, then humans (first last), then [agent] ….
 * @returns {{ value: string, label: string, kind: 'me'|'human'|'agent' }[]}
 */
export function buildAssigneeOptions({ humans = [], agents = [], meUsername, meLabel } = {}) {
  const meNpub = humanNpubForUsername(meUsername)
  const options = []
  const seen = new Set()

  if (meNpub) {
    const value = `human:${meNpub}`
    seen.add(value)
    const suffix = meLabel && meLabel !== meUsername ? ` — ${meLabel}` : meUsername ? ` — ${meUsername}` : ''
    options.push({
      value,
      label: `Me${suffix}`,
      kind: 'me',
    })
  }

  const humanOpts = (humans || [])
    .map((h) => {
      const username = h.personRef || h.username || ''
      const npub = humanNpubForUsername(username)
      if (!npub) return null
      const { label, sortKey } = splitPersonName(h.name || username)
      const base = label || npub
      const withId =
        h.id != null && h.id !== '' ? `${base} (${h.id})` : base
      return {
        value: `human:${npub}`,
        label: withId,
        kind: 'human',
        sortKey,
      }
    })
    .filter(Boolean)
    .filter((o) => {
      if (seen.has(o.value)) return false
      seen.add(o.value)
      return true
    })
    .sort((a, b) => a.sortKey.localeCompare(b.sortKey))

  options.push(...humanOpts)

  const agentOpts = (agents || [])
    .filter((a) => {
      const st = String(a.agentStatus || a.status || '').toUpperCase()
      return !st || st === 'ACTIVE'
    })
    .map((a) => {
      const base = a.name || a.agentKey || a.displayName || 'Agent'
      const withId =
        a.id != null && a.id !== '' ? `${base} (${a.id})` : base
      return {
        value: `agent:${a.id}`,
        label: `[agent] ${withId}`,
        kind: 'agent',
        agentKey: a.agentKey || '',
        developmentOnly: !!a.developmentOnly || a.agentKey === 'coder',
        sortKey: String(a.name || a.agentKey || a.id).toLowerCase(),
      }
    })
    .sort((a, b) => a.sortKey.localeCompare(b.sortKey))

  options.push(...agentOpts)
  return options
}

/** Drop lab-only agents (Coder) unless developmentMode is on. */
export function filterAssigneeOptionsForDevMode(options, developmentMode) {
  if (developmentMode) return options
  return (options || []).filter((o) => !o.developmentOnly && o.agentKey !== 'coder')
}

/** Normalize form assignee pair for API body (human XOR agent; empty → null). */
export function assigneePairForBody(form = {}) {
  const agentRaw = form.assigneeAgentId
  let assigneeAgentId = null
  if (agentRaw !== '' && agentRaw != null) {
    const n = Number(agentRaw)
    assigneeAgentId = Number.isFinite(n) ? n : null
  }
  const human = String(form.assigneeHumanNpub || '').trim() || null
  if (assigneeAgentId != null) {
    return { assigneeHumanNpub: null, assigneeAgentId }
  }
  return { assigneeHumanNpub: human, assigneeAgentId: null }
}

/** Load assignee fields into form state (agent id as string). */
export function assigneePairFromRow(row = {}) {
  return {
    assigneeHumanNpub: row.assigneeHumanNpub || '',
    assigneeAgentId: row.assigneeAgentId != null ? String(row.assigneeAgentId) : '',
  }
}

/** Friendly one-line label for list/board cells. */
export function formatAssigneeDisplay(assigneeHumanNpub, assigneeAgentId, { options = [], meUsername } = {}) {
  const value = encodeAssigneeValue(assigneeHumanNpub, assigneeAgentId)
  if (!value) return '—'
  const hit = options.find((o) => o.value === value)
  if (hit) return hit.label
  if (value.startsWith('agent:')) return '[agent]'
  const npub = value.startsWith('human:') ? value.slice('human:'.length) : value
  if (meUsername && npub === humanNpubForUsername(meUsername)) return 'Me'
  if (npub.startsWith('user:')) return npub.slice('user:'.length)
  return npub
}
