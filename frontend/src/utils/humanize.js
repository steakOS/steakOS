// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors

/** Tokens kept fully uppercase after split. */
const UPPER_TOKENS = new Set([
  'id',
  'url',
  'eur',
  'usd',
  'btc',
  'api',
  'npub',
  'apqc',
  'sku',
  'bom',
  'rma',
  'hr',
  'it',
  'ci',
  'gl',
  'coa',
  'hitl',
  'pdf',
  'csv',
  'po',
  'so',
  'ui',
])

/**
 * Humanize camelCase / PascalCase / snake_case identifiers for UI labels.
 * StrategyItem → Strategy Item · hiveMasterAgentId → Hive Master Agent ID
 */
export function humanizeLabel(key) {
  if (key == null || key === '') return ''
  const raw = String(key).trim()
  if (raw.toLowerCase() === 'id') return 'ID'

  let s = raw.replace(/[_-]+/g, ' ')
  s = s.replace(/([a-z\d])([A-Z])/g, '$1 $2')
  s = s.replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')

  return s
    .split(/\s+/)
    .filter(Boolean)
    .map((word) => {
      const lower = word.toLowerCase()
      if (UPPER_TOKENS.has(lower)) return lower.toUpperCase()
      return lower.charAt(0).toUpperCase() + lower.slice(1)
    })
    .join(' ')
}

/**
 * ADR-039 status pill classes.
 * active/ok → green; inactive/draft → gray; blocked/error → red; pending/warn → amber.
 */
export function statusBadgeClass(status) {
  if (status == null || status === '') return 'badge badge-muted'
  const s = String(status).trim().toUpperCase().replace(/[\s-]+/g, '_')

  const ok = new Set([
    'ACTIVE',
    'ENABLED',
    'OK',
    'SUCCESS',
    'DONE',
    'RESOLVED',
    'CLOSED',
    'COMPLETED',
    'CONFIRMED',
    'APPROVED',
    'PAID',
    'RECEIVED',
    'RELEASED',
    'PASS',
    'HIRED',
    'SUBMITTED',
    'POSTED',
    'READY',
    'OPEN',
    'IN_FORCE',
    'LIVE',
  ])
  const bad = new Set([
    'BLOCKED',
    'ERROR',
    'FAILED',
    'FAILURE',
    'REJECTED',
    'DENIED',
    'CANCELLED',
    'CANCELED',
    'OVERDUE',
    'CRITICAL',
    'DANGER',
  ])
  const warn = new Set([
    'PENDING',
    'REVIEW',
    'WARN',
    'WARNING',
    'DRAFT',
    'IN_PROGRESS',
    'PROCESSING',
    'QUEUED',
    'HOLD',
    'PARTIAL',
  ])
  const muted = new Set([
    'INACTIVE',
    'DISABLED',
    'RETIRED',
    'ARCHIVED',
    'NONE',
    'DEFERRED',
    'UNKNOWN',
  ])

  if (ok.has(s)) return 'badge badge-ok'
  if (bad.has(s)) return 'badge badge-danger'
  if (warn.has(s)) return 'badge badge-warn'
  if (muted.has(s)) return 'badge badge-muted'
  // Heuristic fallbacks
  if (s.includes('ERROR') || s.includes('FAIL') || s.includes('BLOCK')) return 'badge badge-danger'
  if (s.includes('ACTIVE') || s.includes('SUCCESS')) return 'badge badge-ok'
  if (s.includes('PENDING') || s.includes('WARN')) return 'badge badge-warn'
  return 'badge badge-muted'
}

/** Primary role label for dashboard titles (Admin > CEO > Sales > …). */
export function primaryRoleLabel(authorities = [], { isAdmin, isCeo } = {}) {
  const auths = (authorities || []).map((a) => String(a).toUpperCase())
  const has = (r) => auths.includes(r) || auths.includes(`ROLE_${r}`)
  // True Admin (not merely CEO who also carries ROLE_ADMIN)
  if (isCeo) return 'CEO'
  if (isAdmin || has('ROLE_ADMIN')) return 'Admin'
  if (has('ROLE_SALES')) return 'Sales'
  if (has('ROLE_SCM') || has('ROLE_SUPPLY')) return 'SCM'
  if (has('ROLE_HR')) return 'HR'
  if (has('ROLE_FINANCE')) return 'Finance'
  if (has('ROLE_DELIVERY')) return 'Delivery'
  if (has('ROLE_DIGITAL')) return 'Digital'
  if (has('ROLE_READ_ONLY')) return 'Read-only'
  if (auths.length) {
    const raw = auths[0].replace(/^ROLE_/, '')
    return humanizeLabel(raw)
  }
  return 'User'
}
