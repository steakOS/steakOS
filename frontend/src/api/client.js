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

/**
 * Structured API error for UI dialogs (code, message, detail, stack).
 */
export class ApiError extends Error {
  constructor({ message, code, status, detail, stackTrace, body, unlockHint, reasons } = {}) {
    super(message || 'Request failed')
    this.name = 'ApiError'
    this.code = code || 'REQUEST_FAILED'
    this.status = status ?? null
    this.detail = detail || null
    this.stackTrace = stackTrace || null
    this.body = body || null
    this.unlockHint = unlockHint || body?.unlockHint || null
    this.reasons = reasons || body?.reasons || null
  }
}

function isHtmlBody(text) {
  if (!text || typeof text !== 'string') return false
  const t = text.trimStart().slice(0, 32).toLowerCase()
  return t.startsWith('<!doctype') || t.startsWith('<html') || t.startsWith('<head')
}

/** Avoid hammering /login when many parallel 401s fire (setup gate + shell). */
let loginRedirectScheduled = false

function goLogin() {
  if (window.location.pathname.startsWith('/login')) return
  if (loginRedirectScheduled) return
  loginRedirectScheduled = true
  window.location.href = '/login/auth'
}

function looksLikeLoginHtml(text) {
  if (!text || typeof text !== 'string') return false
  const t = text.toLowerCase()
  return (
    t.includes('login/auth') ||
    t.includes('name="username"') ||
    t.includes('j_username') ||
    t.includes('spring_security') ||
    t.includes('sign in') ||
    t.includes('anmelden')
  )
}

/**
 * API helper — relative URLs so Vite proxy attaches session cookie (F2).
 * Uses redirect:'manual' so Spring Security 302→login is not followed into HTML 200.
 *
 * Important: Grails 500 error pages are HTML too — do NOT treat those as auth
 * failure or the SPA will full-reload into a login↔dashboard loop.
 */
export async function api(path, options = {}) {
  const { redirect: _ignored, ...rest } = options
  const opts = {
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(rest.body && !(rest.body instanceof FormData)
        ? { 'Content-Type': 'application/json' }
        : {}),
      ...rest.headers,
    },
    ...rest,
    redirect: 'manual',
  }

  if (opts.body && typeof opts.body === 'object' && !(opts.body instanceof FormData)) {
    opts.body = JSON.stringify(opts.body)
  }

  const res = await fetch(path.startsWith('/') ? path : `/${path}`, opts)

  if (
    res.type === 'opaqueredirect' ||
    res.status === 301 ||
    res.status === 302 ||
    res.status === 303 ||
    res.status === 307 ||
    res.status === 308 ||
    res.status === 401
  ) {
    goLogin()
    throw new ApiError({
      message: 'Unauthorized — please log in',
      code: 'UNAUTHORIZED',
      status: res.status || 302,
    })
  }

  if (res.status === 204) {
    return null
  }

  const text = await res.text()

  // Only treat HTML as session loss when it looks like a login page (or 403).
  // Server 500/error HTML must surface as a normal error — not a redirect loop.
  if (isHtmlBody(text)) {
    const authish = res.status === 403 || looksLikeLoginHtml(text)
    if (authish) {
      goLogin()
      throw new ApiError({
        message: 'Session expired or not logged in',
        code: 'UNAUTHORIZED',
        status: res.status,
        detail: 'API returned HTML login page instead of JSON.',
      })
    }
    throw new ApiError({
      message: `Server returned HTML instead of JSON (HTTP ${res.status})`,
      code: `HTTP_${res.status || 'HTML'}`,
      status: res.status,
      detail: text.slice(0, 400),
    })
  }

  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = { raw: text }
  }

  if (!res.ok) {
    if (res.status === 403 && data?.code === 'TOTP_REQUIRED') {
      const path = window.location.pathname || ''
      // Stay on setup wizard — redirecting to /totp abandons Finish with no feedback
      if (!path.startsWith('/totp') && !path.startsWith('/setup')) {
        window.location.href = '/totp'
      }
      throw new ApiError({
        message: data?.error || 'Authenticator code required',
        code: 'TOTP_REQUIRED',
        status: 403,
        body: data,
      })
    }
    throw new ApiError({
      message: data?.error || data?.message || res.statusText || 'Request failed',
      code: data?.code || `HTTP_${res.status}`,
      status: res.status,
      detail: data?.detail || null,
      stackTrace: data?.stackTrace || null,
      body: data,
      unlockHint: data?.unlockHint || null,
      reasons: data?.reasons || null,
    })
  }
  return data
}

export const domainsApi = {
  list: (pkg, resource, params = {}) => {
    const q = new URLSearchParams()
    if (params.max != null) q.set('max', String(params.max))
    if (params.offset != null) q.set('offset', String(params.offset))
    if (params.page != null) q.set('page', String(params.page))
    if (params.sort) q.set('sort', params.sort)
    if (params.order) q.set('order', params.order)
    if (params.q) q.set('q', params.q)
    if (params.status) q.set('status', params.status)
    if (params.view) q.set('view', params.view)
    // L3 — parent FK filters for nested line tables (quoteId, salesOrderId, …)
    Object.keys(params).forEach((key) => {
      if (key.endsWith('Id') && key !== 'id' && params[key] != null && params[key] !== '') {
        q.set(key, String(params[key]))
      }
    })
    const qs = q.toString()
    return api(`/api/domains/${pkg}/${resource}${qs ? `?${qs}` : ''}`)
  },
  get: (pkg, resource, id) => api(`/api/domains/${pkg}/${resource}/${id}`),
  /** P2 — field metadata for schema-driven forms */
  schema: (pkg, resource) => api(`/api/domains/${pkg}/${resource}/schema`),
  create: (pkg, resource, body) =>
    api(`/api/domains/${pkg}/${resource}`, { method: 'POST', body }),
  update: (pkg, resource, id, body) =>
    api(`/api/domains/${pkg}/${resource}/${id}`, { method: 'PUT', body }),
  remove: (pkg, resource, id) =>
    api(`/api/domains/${pkg}/${resource}/${id}`, { method: 'DELETE' }),
}

/** Top-bar global search S0 (Task 7642) — GET /api/search */
export const searchApi = {
  search: ({ q, types, maxPerType } = {}) => {
    const qs = new URLSearchParams()
    if (q != null && q !== '') qs.set('q', String(q))
    if (types != null && types !== '') {
      qs.set('types', Array.isArray(types) ? types.join(',') : String(types))
    }
    if (maxPerType != null) qs.set('maxPerType', String(maxPerType))
    const s = qs.toString()
    return api(`/api/search${s ? `?${s}` : ''}`)
  },
}

/** Object Companion / Content Rail (CT-S0–S2) — /api/content/* */
export const contentApi = {
  types: () => api('/api/content/types'),
  profile: (pkg, resource) => api(`/api/content/profiles/${pkg}/${resource}`),
  summary: (pkg, resource, ids = []) => {
    const q = new URLSearchParams()
    q.set('package', pkg)
    q.set('resource', resource)
    if (ids?.length) q.set('ids', ids.join(','))
    return api(`/api/content/summary?${q.toString()}`)
  },
  rail: (pkg, resource, id, params = {}) => {
    const q = new URLSearchParams()
    if (params.channel) q.set('channel', params.channel)
    const qs = q.toString()
    return api(`/api/content/on/${pkg}/${resource}/${id}${qs ? `?${qs}` : ''}`)
  },
  addLink: (pkg, resource, id, body) =>
    api(`/api/content/on/${pkg}/${resource}/${id}/links`, { method: 'POST', body }),
  addBody: (pkg, resource, id, body) =>
    api(`/api/content/on/${pkg}/${resource}/${id}/bodies`, { method: 'POST', body }),
  addFile: async (pkg, resource, id, file) => {
    const fd = new FormData()
    fd.append('file', file)
    const res = await fetch(`/api/content/on/${pkg}/${resource}/${id}/files`, {
      method: 'POST',
      credentials: 'include',
      redirect: 'manual',
      headers: { Accept: 'application/json' },
      body: fd,
    })
    if (
      res.type === 'opaqueredirect' ||
      [301, 302, 303, 307, 308, 401].includes(res.status)
    ) {
      goLogin()
      throw new ApiError({ message: 'Unauthorized', code: 'UNAUTHORIZED', status: res.status })
    }
    const text = await res.text()
    if (isHtmlBody(text) && (res.status === 403 || looksLikeLoginHtml(text))) {
      goLogin()
      throw new ApiError({ message: 'Unauthorized', code: 'UNAUTHORIZED', status: res.status })
    }
    let data = null
    try {
      data = text && !isHtmlBody(text) ? JSON.parse(text) : null
    } catch {
      data = null
    }
    if (isHtmlBody(text) && !data) {
      const tooLarge =
        /MaxUploadSize|Maximum upload size|SizeLimitExceeded|FileSizeLimit/i.test(text)
      throw new ApiError({
        message: tooLarge
          ? 'File is too large for upload (max 25 MB)'
          : `Server returned HTML instead of JSON (HTTP ${res.status})`,
        code: tooLarge ? 'UPLOAD_TOO_LARGE' : `HTTP_${res.status || 'HTML'}`,
        status: res.status,
      })
    }
    if (!res.ok) {
      throw new ApiError({
        message: data?.error || res.statusText || `Upload failed (HTTP ${res.status})`,
        code: data?.code || `HTTP_${res.status}`,
        status: res.status,
        body: data,
      })
    }
    return data
  },
  update: (id, body) => api(`/api/content/entry/${id}`, { method: 'PUT', body }),
  pin: (id, pinned = true) =>
    api(`/api/content/entry/${id}/pin`, { method: 'POST', body: { pinned } }),
  remove: (id) => api(`/api/content/entry/${id}`, { method: 'DELETE' }),
  downloadUrl: (id) => `/api/content/entry/${id}/download`,
}

export const dashboardApi = {
  ceo: () => api('/api/dashboard/ceo'),
}

/** Silver KPI fabric (PR-2/13 registry + feeds; PR-14 Analytics desk) */
export const kpiApi = {
  catalogue: () => api('/api/kpi/catalogue'),
  dashboardHero: () => api('/api/kpi/dashboard-hero'),
  /** @param {string} id e.g. KPI-S01 */
  show: (id, params = {}) => {
    const q = new URLSearchParams()
    if (params.from) q.set('from', params.from)
    if (params.to) q.set('to', params.to)
    const qs = q.toString()
    return api(`/api/kpi/${encodeURIComponent(id)}${qs ? `?${qs}` : ''}`)
  },
  arAging: (params = {}) => {
    const q = new URLSearchParams()
    if (params.asOf) q.set('asOf', params.asOf)
    const qs = q.toString()
    return api(`/api/kpi/ar-aging${qs ? `?${qs}` : ''}`)
  },
}

export const versionApi = {
  get: () => api('/api/version'),
  /** Live + inventory system vitals (About page) */
  stats: () => api('/api/version/stats'),
}

/** PDR-023 BTC FX hints (quote = EUR|USD per 1 BTC) */
export const fxApi = {
  btc: (quote = 'EUR') =>
    api(`/api/fx/btc?quote=${encodeURIComponent(quote || 'EUR')}`),
}

/** AUTH01–04 + NIP-07 */
export const authApi = {
  totpStatus: () => api('/api/auth/totp/status'),
  totpBegin: () => api('/api/auth/totp/begin', { method: 'POST', body: {} }),
  totpConfirm: (code) => api('/api/auth/totp/confirm', { method: 'POST', body: { code } }),
  totpVerify: (code) => api('/api/auth/totp/verify', { method: 'POST', body: { code } }),
  totpClear: (username) =>
    api('/api/auth/totp/clear', { method: 'POST', body: { username } }),
  totpBeginFor: (username) =>
    api('/api/auth/totp/begin-for', { method: 'POST', body: { username } }),
  totpConfirmFor: (username, code) =>
    api('/api/auth/totp/confirm-for', { method: 'POST', body: { username, code } }),
  adminTotpBypassGet: () => api('/api/auth/admin-totp-bypass'),
  adminTotpBypassPut: (enabled) =>
    api('/api/auth/admin-totp-bypass', { method: 'PUT', body: { enabled: !!enabled } }),
  nostrChallenge: () => api('/api/auth/nostr/challenge', { method: 'POST', body: {} }),
  nostrVerify: (body) => api('/api/auth/nostr/verify', { method: 'POST', body }),
  nostrLink: (body) => api('/api/auth/nostr/link', { method: 'POST', body }),
  usersList: () => api('/api/auth/users'),
  usersCreate: (body) => api('/api/auth/users', { method: 'POST', body }),
  usersUpdate: (body) => api('/api/auth/users', { method: 'PUT', body }),
  roleProfiles: () => api('/api/auth/role-profiles'),
  roleProfile: (profileId) => api(`/api/auth/role-profiles/${encodeURIComponent(profileId)}`),
  roleProfileUpdate: (profileId, body) =>
    api(`/api/auth/role-profiles/${encodeURIComponent(profileId)}`, { method: 'PUT', body }),
  roleProfileReset: (profileId) =>
    api(`/api/auth/role-profiles/${encodeURIComponent(profileId)}/reset`, {
      method: 'POST',
      body: {},
    }),
  roleProfileApply: (profileId) =>
    api(`/api/auth/role-profiles/${encodeURIComponent(profileId)}/apply`, {
      method: 'POST',
      body: {},
    }),
}

export const setupApi = {
  status: () => api('/api/setup/status'),
  getDraft: () => api('/api/setup/draft'),
  saveDraft: (body) => api('/api/setup/draft', { method: 'PUT', body }),
  apply: (body) => api('/api/setup/apply', { method: 'POST', body }),
  complete: (body = {}) => api('/api/setup/complete', { method: 'POST', body }),
  reset: (body = {}) => api('/api/setup/reset', { method: 'POST', body }),
  check: () => api('/api/setup/check'),
  /** Read-only tenant company profile (System → Company profile) */
  profile: () => api('/api/setup/profile'),
  /** Iron demo seed (IR-05) — customers, vendors, catalogue, task */
  demoSeed: (body = {}) => api('/api/setup/demo-seed', { method: 'POST', body }),
  /** PDR-026 — wipe+reseed Orange Steak House (DEMO only) */
  demoReset: (body = {}) => api('/api/setup/demo-reset', { method: 'POST', body }),
  heardCookSeed: (body = {}) => api('/api/setup/heard-cook-seed', { method: 'POST', body }),
  heardCookReset: (body = {}) => api('/api/setup/heard-cook-reset', { method: 'POST', body }),
  /** Bronze BR-R6 — acknowledge HR / people-data privacy duties before enabling human family */
  privacyHrAck: (body = {}) => api('/api/setup/privacy/hr-ack', { method: 'POST', body }),
  /** Switch metal pack + families (open metals only). Body: { templateTier, enabledFamilies?, pluginDepth?, messaging? } */
  templateTier: (body) => api('/api/setup/template-tier', { method: 'POST', body }),
  /** Settings pack options — messaging toggles / pluginDepth without tier change */
  packOptions: (body) => api('/api/setup/pack-options', { method: 'POST', body }),
}

/** Opaque bearer tokens for Hermes agents (human admin session) */
export const agentTokenApi = {
  list: (includeRevoked = false) =>
    api(`/api/agent/tokens${includeRevoked ? '?includeRevoked=true' : ''}`),
  mint: (body) => api('/api/agent/tokens', { method: 'POST', body }),
  revoke: (id) => api(`/api/agent/tokens/${id}/revoke`, { method: 'POST', body: {} }),
  ensureUser: (body) => api('/api/agent/users', { method: 'POST', body }),
}

/** Agent gateway API (Bearer or session) */
export const agentGatewayApi = {
  me: () => api('/api/agent/me'),
  l0Summary: () => api('/api/agent/l0/summary'),
  capabilities: () => api('/api/agent/capabilities'),
  proposals: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.decision) q.set('decision', params.decision)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/agent/proposals${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/agent/proposals/${id}`),
    create: (body) => api('/api/agent/proposals', { method: 'POST', body }),
  },
}

/** Unified catalogue (CATALOGUE-ITEM-CONCEPT) */
export const catalogueApi = {
  list: (params = {}) => {
    const q = new URLSearchParams()
    if (params.q) q.set('q', params.q)
    if (params.for) q.set('for', params.for)
    if (params.nature) q.set('nature', params.nature)
    if (params.activeOnly === false) q.set('activeOnly', 'false')
    if (params.max != null) q.set('max', String(params.max))
    if (params.offset != null) q.set('offset', String(params.offset))
    if (params.sort) q.set('sort', params.sort)
    if (params.order) q.set('order', params.order)
    const qs = q.toString()
    return api(`/api/catalogue/items${qs ? `?${qs}` : ''}`)
  },
  get: (id) => api(`/api/catalogue/items/${id}`),
  create: (body) => api('/api/catalogue/items', { method: 'POST', body }),
  update: (id, body) => api(`/api/catalogue/items/${id}`, { method: 'PUT', body }),
}

export const financeLedgerApi = {
  seed: (npubOwner) =>
    api('/api/finance/ledger/seed', {
      method: 'POST',
      body: npubOwner ? { npubOwner } : {},
    }),
  accounts: (params = {}) => {
    const q = new URLSearchParams()
    if (params.npubOwner) q.set('npubOwner', params.npubOwner)
    if (params.activeOnly === false) q.set('activeOnly', 'false')
    if (params.max != null) q.set('max', String(params.max))
    if (params.offset != null) q.set('offset', String(params.offset))
    if (params.sort) q.set('sort', params.sort)
    if (params.order) q.set('order', params.order)
    const qs = q.toString()
    return api(`/api/finance/ledger/accounts${qs ? `?${qs}` : ''}`)
  },
  postEntry: (body) =>
    api('/api/finance/ledger/entries', { method: 'POST', body }),
  reverse: (id, body = {}) =>
    api(`/api/finance/ledger/entries/${id}/reverse`, { method: 'POST', body }),
  trialBalance: (period, npubOwner) => {
    const q = new URLSearchParams({ period })
    if (npubOwner) q.set('npubOwner', npubOwner)
    return api(`/api/finance/ledger/trial-balance?${q}`)
  },
  balance: (accountId, params = {}) => {
    const q = new URLSearchParams()
    if (params.npubOwner) q.set('npubOwner', params.npubOwner)
    if (params.asOf) q.set('asOf', params.asOf)
    const qs = q.toString()
    return api(`/api/finance/ledger/accounts/${accountId}/balance${qs ? `?${qs}` : ''}`)
  },
  closePeriod: (period, body = {}) =>
    api(`/api/finance/ledger/periods/${period}/close`, { method: 'POST', body }),
  periodStatus: (period, npubOwner) => {
    const q = new URLSearchParams()
    if (npubOwner) q.set('npubOwner', npubOwner)
    const qs = q.toString()
    return api(`/api/finance/ledger/periods/${period}${qs ? `?${qs}` : ''}`)
  },
  /** PDR-027 — posting ruleset for active company */
  postingRules: () => api('/api/finance/posting-rules'),
  postingRulesSeed: (body = {}) =>
    api('/api/finance/posting-rules/seed', { method: 'POST', body }),
  postingRulesSave: (body) =>
    api('/api/finance/posting-rules', { method: 'PUT', body }),
  postingRulesReseed: (body = {}) =>
    api('/api/finance/posting-rules/reseed', { method: 'POST', body }),
  postingRulesChanges: (params = {}) => {
    const q = new URLSearchParams()
    if (params.max != null) q.set('max', String(params.max))
    const qs = q.toString()
    return api(`/api/finance/posting-rules/changes${qs ? `?${qs}` : ''}`)
  },
}

export const financeRevenueApi = {
  list: (params = {}) => {
    const q = new URLSearchParams()
    if (params.npubOwner) q.set('npubOwner', params.npubOwner)
    if (params.status) q.set('status', params.status)
    if (params.max != null) q.set('max', String(params.max))
    if (params.offset != null) q.set('offset', String(params.offset))
    if (params.sort) q.set('sort', params.sort)
    if (params.order) q.set('order', params.order)
    if (params.q) q.set('q', params.q)
    const qs = q.toString()
    return api(`/api/finance/revenue/invoices${qs ? `?${qs}` : ''}`)
  },
  open: (npubOwner) => {
    const q = new URLSearchParams()
    if (npubOwner) q.set('npubOwner', npubOwner)
    const qs = q.toString()
    return api(`/api/finance/revenue/invoices/open${qs ? `?${qs}` : ''}`)
  },
  get: (id) => api(`/api/finance/revenue/invoices/${id}`),
  create: (body) =>
    api('/api/finance/revenue/invoices', { method: 'POST', body }),
  issue: (id, body = {}) =>
    api(`/api/finance/revenue/invoices/${id}/issue`, { method: 'POST', body }),
  addOption: (id, body) =>
    api(`/api/finance/revenue/invoices/${id}/options`, { method: 'POST', body }),
  settle: (id, body) =>
    api(`/api/finance/revenue/invoices/${id}/settle`, { method: 'POST', body }),
  /** Copper CU-F7 — open invoice PDF in new tab (session cookie). */
  pdfUrl: (id) => `/api/finance/revenue/invoices/${id}/pdf`,
  voidInvoice: (id, body = {}) =>
    api(`/api/finance/revenue/invoices/${id}/void`, { method: 'POST', body }),
}

function financeListQs(params = {}) {
  const q = new URLSearchParams()
  if (params.status) q.set('status', params.status)
  if (params.period) q.set('period', params.period)
  if (params.max != null) q.set('max', String(params.max))
  if (params.offset != null) q.set('offset', String(params.offset))
  if (params.sort) q.set('sort', params.sort)
  if (params.order) q.set('order', params.order)
  if (params.npubOwner) q.set('npubOwner', params.npubOwner)
  const qs = q.toString()
  return qs ? `?${qs}` : ''
}

export const financeOpsApi = {
  treasurySeed: () => api('/api/finance/treasury/seed', { method: 'POST', body: {} }),
  treasuryList: (params = {}) => api(`/api/finance/treasury/positions${financeListQs(params)}`),
  treasuryRegister: (body) =>
    api('/api/finance/treasury/positions', { method: 'POST', body }),
  treasuryReconcile: (id, body) =>
    api(`/api/finance/treasury/positions/${id}/reconcile`, { method: 'POST', body }),
  btcpayWebhook: (body) =>
    api('/api/finance/treasury/webhook/btcpay', { method: 'POST', body }),

  paymentsSettingsGet: () => api('/api/finance/payments/settings'),
  paymentsSettingsPut: (body) =>
    api('/api/finance/payments/settings', { method: 'PUT', body }),
  btcLevelGet: () => api('/api/finance/payments/btc-level'),
  btcLevelPut: (body) => api('/api/finance/payments/btc-level', { method: 'PUT', body }),
  paymentsProbe: () => api('/api/finance/payments/probe', { method: 'POST', body: {} }),
  paymentsProviderStatus: () => api('/api/finance/payments/provider'),
  paymentsInboundCreate: (body) =>
    api('/api/finance/payments/inbound', { method: 'POST', body }),
  paymentsReconcile: () =>
    api('/api/finance/payments/reconcile', { method: 'POST', body: {} }),

  payableList: (statusOrParams) => {
    const params =
      statusOrParams && typeof statusOrParams === 'object'
        ? statusOrParams
        : { status: statusOrParams }
    return api(`/api/finance/payables${financeListQs(params)}`)
  },
  payableCreate: (body) => api('/api/finance/payables', { method: 'POST', body }),
  payablePay: (id, body = {}) =>
    api(`/api/finance/payables/${id}/pay`, { method: 'POST', body }),
  payableVoid: (id) =>
    api(`/api/finance/payables/${id}/void`, { method: 'POST', body: {} }),

  budgetList: (periodOrParams) => {
    const params =
      periodOrParams && typeof periodOrParams === 'object'
        ? periodOrParams
        : periodOrParams
          ? { period: periodOrParams }
          : {}
    return api(`/api/finance/budgets${financeListQs(params)}`)
  },
  budgetCreate: (body) => api('/api/finance/budgets', { method: 'POST', body }),
  budgetVariance: (period) =>
    api(`/api/finance/budgets/variance?period=${encodeURIComponent(period)}`),

  taxList: (params = {}) => api(`/api/finance/tax${financeListQs(params)}`),
  taxEstimate: (body) =>
    api('/api/finance/tax/estimate', { method: 'POST', body }),

  payrollList: (params = {}) => api(`/api/finance/payroll${financeListQs(params)}`),
  payrollCreate: (body) =>
    api('/api/finance/payroll', { method: 'POST', body }),
  /** Recognize payroll run (payroll.recognize) — alias path /post */
  payrollPost: (period) =>
    api('/api/finance/payroll/post', { method: 'POST', body: { period } }),
  payrollRecognize: (period) =>
    api('/api/finance/payroll/post', { method: 'POST', body: { period } }),
  /** Pay net wages (payroll.pay) — requires prior recognize */
  payrollPay: (period, body = {}) =>
    api('/api/finance/payroll/pay', { method: 'POST', body: { period, ...body } }),

  controlAudit: (params = {}) => api(`/api/finance/control/audit${financeListQs(params)}`),
  controlPeriod: (period) => api(`/api/finance/control/periods/${period}`),
  controlClose: (period) =>
    api(`/api/finance/control/periods/${period}/close`, { method: 'POST', body: { period } }),

  // FinanceAdvanced three-way match (OI-F21 / PR-5b) — SILVER+
  matchList: (params = {}) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    const qs = q.toString()
    return api(`/api/finance/match${qs ? `?${qs}` : ''}`)
  },
  matchCandidates: () => api('/api/finance/match/candidates'),
  matchGet: (id) => api(`/api/finance/match/${id}`),
  matchPropose: (body) => api('/api/finance/match', { method: 'POST', body }),
  matchConfirm: (id, body = {}) =>
    api(`/api/finance/match/${id}/confirm`, { method: 'POST', body }),
  matchReject: (id, body = {}) =>
    api(`/api/finance/match/${id}/reject`, { method: 'POST', body }),
  matchCancel: (id) =>
    api(`/api/finance/match/${id}/cancel`, { method: 'POST', body: {} }),

  // FinanceAdvanced collections (PR-5a) — SILVER+
  collectionsOverdue: (params = {}) => {
    const q = new URLSearchParams()
    if (params.asOf) q.set('asOf', params.asOf)
    const qs = q.toString()
    return api(`/api/finance/collections${qs ? `?${qs}` : ''}`)
  },
  collectionsOpen: (params = {}) => {
    const q = new URLSearchParams()
    if (params.asOf) q.set('asOf', params.asOf)
    const qs = q.toString()
    return api(`/api/finance/collections/open${qs ? `?${qs}` : ''}`)
  },
  collectionsGet: (id, params = {}) => {
    const q = new URLSearchParams()
    if (params.asOf) q.set('asOf', params.asOf)
    const qs = q.toString()
    return api(`/api/finance/collections/${id}${qs ? `?${qs}` : ''}`)
  },
  collectionsListActivities: (invoiceId) =>
    api(`/api/finance/collections/${invoiceId}/activities`),
  collectionsCreateActivity: (invoiceId, body) =>
    api(`/api/finance/collections/${invoiceId}/activities`, { method: 'POST', body }),
  collectionsCreatePromise: (invoiceId, body) =>
    api(`/api/finance/collections/${invoiceId}/promise`, { method: 'POST', body }),
  collectionsUpdatePromiseStatus: (activityId, body) =>
    api(`/api/finance/collections/activities/${activityId}`, { method: 'PUT', body }),
}

/** OI-NOSTR01 — Nostr integration settings / probe */
export const nostrApi = {
  settingsGet: () => api('/api/nostr/settings'),
  settingsPut: (body) => api('/api/nostr/settings', { method: 'PUT', body }),
  probe: () => api('/api/nostr/probe', { method: 'POST', body: {} }),
  relayStatus: () => api('/api/nostr/relay/status'),
  relayEnsure: () => api('/api/nostr/relay/ensure', { method: 'POST', body: {} }),
  publishStub: (body) => api('/api/nostr/publish-stub', { method: 'POST', body }),
  lastIntent: () => api('/api/nostr/last-intent'),
  identity: () => api('/api/nostr/identity'),
}

export const salesApi = {
  customers: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.npubOwner) q.set('npubOwner', params.npubOwner)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.q) q.set('q', params.q)
      const qs = q.toString()
      return api(`/api/sales/customers${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/sales/customers/${id}`),
    create: (body) => api('/api/sales/customers', { method: 'POST', body }),
    update: (id, body) => api(`/api/sales/customers/${id}`, { method: 'PUT', body }),
  },
  quotes: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.npubOwner) q.set('npubOwner', params.npubOwner)
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.q) q.set('q', params.q)
      const qs = q.toString()
      return api(`/api/sales/quotes${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/sales/quotes/${id}`),
    create: (body) => api('/api/sales/quotes', { method: 'POST', body }),
    setLines: (id, lines) =>
      api(`/api/sales/quotes/${id}/lines`, { method: 'PUT', body: { lines } }),
    send: (id, body = {}) =>
      api(`/api/sales/quotes/${id}/send`, { method: 'POST', body }),
    accept: (id, body = {}) =>
      api(`/api/sales/quotes/${id}/accept`, { method: 'POST', body }),
    reject: (id, body = {}) =>
      api(`/api/sales/quotes/${id}/reject`, { method: 'POST', body }),
    /** ISO 6422-1 A4 quote PDF (session cookie). */
    pdfUrl: (id) => `/api/sales/quotes/${id}/pdf`,
  },
  orders: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.npubOwner) q.set('npubOwner', params.npubOwner)
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.q) q.set('q', params.q)
      const qs = q.toString()
      return api(`/api/sales/orders${qs ? `?${qs}` : ''}`)
    },
    open: (params = {}) => {
      const q = new URLSearchParams()
      if (params.npubOwner) q.set('npubOwner', params.npubOwner)
      const qs = q.toString()
      return api(`/api/sales/orders/open${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/sales/orders/${id}`),
    /** ISO 6422-1 A4 sales order PDF (session cookie). */
    pdfUrl: (id) => `/api/sales/orders/${id}/pdf`,
    create: (body) => api('/api/sales/orders', { method: 'POST', body }),
    setLines: (id, lines) =>
      api(`/api/sales/orders/${id}/lines`, { method: 'PUT', body: { lines } }),
    confirm: (id, body = {}) =>
      api(`/api/sales/orders/${id}/confirm`, { method: 'POST', body }),
    cancel: (id, body = {}) =>
      api(`/api/sales/orders/${id}/cancel`, { method: 'POST', body }),
  },

  /** SalesAdvanced price lists (OI-S04 / PR-4a) — SILVER+ */
  pricelists: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.q) q.set('q', params.q)
      if (params.listKind) q.set('listKind', params.listKind)
      if (params.customerId != null) q.set('customerId', String(params.customerId))
      if (params.active === false) q.set('active', 'false')
      const qs = q.toString()
      return api(`/api/sales/pricelists${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/sales/pricelists/${id}`),
    create: (body) => api('/api/sales/pricelists', { method: 'POST', body }),
    update: (id, body) => api(`/api/sales/pricelists/${id}`, { method: 'PUT', body }),
    delete: (id) => api(`/api/sales/pricelists/${id}`, { method: 'DELETE' }),
    setLines: (id, lines) =>
      api(`/api/sales/pricelists/${id}/lines`, { method: 'PUT', body: { lines } }),
    resolve: (params = {}) => {
      const q = new URLSearchParams()
      Object.entries(params).forEach(([k, v]) => {
        if (v != null && v !== '') q.set(k, String(v))
      })
      const qs = q.toString()
      return api(`/api/sales/pricelists/resolve${qs ? `?${qs}` : ''}`)
    },
  },

  /** SalesAdvanced light CRM (OI-S08 / PR-4b) — SILVER+ */
  crm: {
    listOpportunities: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.q) q.set('q', params.q)
      if (params.stage) q.set('stage', params.stage)
      if (params.customerId != null) q.set('customerId', String(params.customerId))
      if (params.active === false) q.set('active', 'false')
      if (params.open) q.set('open', 'true')
      const qs = q.toString()
      return api(`/api/sales/crm/opportunities${qs ? `?${qs}` : ''}`)
    },
    getOpportunity: (id) => api(`/api/sales/crm/opportunities/${id}`),
    createOpportunity: (body) =>
      api('/api/sales/crm/opportunities', { method: 'POST', body }),
    updateOpportunity: (id, body) =>
      api(`/api/sales/crm/opportunities/${id}`, { method: 'PUT', body }),
    deleteOpportunity: (id) =>
      api(`/api/sales/crm/opportunities/${id}`, { method: 'DELETE' }),
    markLost: (id, body = {}) =>
      api(`/api/sales/crm/opportunities/${id}/lost`, { method: 'POST', body }),
    convert: (id, body = {}) =>
      api(`/api/sales/crm/opportunities/${id}/convert`, { method: 'POST', body }),
    listActivities: (opportunityId) =>
      api(`/api/sales/crm/opportunities/${opportunityId}/activities`),
    createActivity: (opportunityId, body) =>
      api(`/api/sales/crm/opportunities/${opportunityId}/activities`, {
        method: 'POST',
        body,
      }),
  },
}

/** GRRCBasics (G7 / PR-6) — SILVER+ / Iron eval */
export const grrcApi = {
  risks: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.status) q.set('status', params.status)
      const qs = q.toString()
      return api(`/api/grrc/risks${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/grrc/risks/${id}`),
    create: (body) => api('/api/grrc/risks', { method: 'POST', body }),
    update: (id, body) => api(`/api/grrc/risks/${id}`, { method: 'PUT', body }),
    delete: (id) => api(`/api/grrc/risks/${id}`, { method: 'DELETE' }),
    close: (id, body = {}) =>
      api(`/api/grrc/risks/${id}/close`, { method: 'POST', body }),
  },
  compliance: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.status) q.set('status', params.status)
      const qs = q.toString()
      return api(`/api/grrc/compliance${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/grrc/compliance/${id}`),
    create: (body) => api('/api/grrc/compliance', { method: 'POST', body }),
    update: (id, body) => api(`/api/grrc/compliance/${id}`, { method: 'PUT', body }),
    delete: (id) => api(`/api/grrc/compliance/${id}`, { method: 'DELETE' }),
  },
}

/** StakeholderBasics (G7 / PR-7) — SILVER+ / Iron eval */
export const stakeholderApi = {
  parties: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.status) q.set('status', params.status)
      if (params.partyKind) q.set('partyKind', params.partyKind)
      const qs = q.toString()
      return api(`/api/stakeholder/parties${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/stakeholder/parties/${id}`),
    create: (body) => api('/api/stakeholder/parties', { method: 'POST', body }),
    update: (id, body) => api(`/api/stakeholder/parties/${id}`, { method: 'PUT', body }),
    delete: (id) => api(`/api/stakeholder/parties/${id}`, { method: 'DELETE' }),
  },
  comms: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.status) q.set('status', params.status)
      if (params.partyId != null) q.set('partyId', String(params.partyId))
      const qs = q.toString()
      return api(`/api/stakeholder/comms${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/stakeholder/comms/${id}`),
    create: (body) => api('/api/stakeholder/comms', { method: 'POST', body }),
    update: (id, body) => api(`/api/stakeholder/comms/${id}`, { method: 'PUT', body }),
    delete: (id) => api(`/api/stakeholder/comms/${id}`, { method: 'DELETE' }),
  },
}

export const aftersalesApi = {
  serviceLevels: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.activeOnly === false) q.set('activeOnly', 'false')
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/aftersales/service-levels${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/aftersales/service-levels/${id}`),
    create: (body) => api('/api/aftersales/service-levels', { method: 'POST', body }),
    update: (id, body) => api(`/api/aftersales/service-levels/${id}`, { method: 'PUT', body }),
  },
  inquiries: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.kind) q.set('kind', params.kind)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/aftersales/inquiries${qs ? `?${qs}` : ''}`)
    },
    open: () => api('/api/aftersales/inquiries/open'),
    get: (id) => api(`/api/aftersales/inquiries/${id}`),
    create: (body) => api('/api/aftersales/inquiries', { method: 'POST', body }),
    update: (id, body) => api(`/api/aftersales/inquiries/${id}`, { method: 'PUT', body }),
    start: (id) => api(`/api/aftersales/inquiries/${id}/start`, { method: 'POST', body: {} }),
    resolve: (id, body = {}) =>
      api(`/api/aftersales/inquiries/${id}/resolve`, { method: 'POST', body }),
    close: (id) => api(`/api/aftersales/inquiries/${id}/close`, { method: 'POST', body: {} }),
    cancel: (id) => api(`/api/aftersales/inquiries/${id}/cancel`, { method: 'POST', body: {} }),
  },
  warranties: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.coverStatus) q.set('coverStatus', params.coverStatus)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/aftersales/warranties${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/aftersales/warranties/${id}`),
    create: (body) => api('/api/aftersales/warranties', { method: 'POST', body }),
    update: (id, body) => api(`/api/aftersales/warranties/${id}`, { method: 'PUT', body }),
    activate: (id) => api(`/api/aftersales/warranties/${id}/activate`, { method: 'POST', body: {} }),
    void: (id) => api(`/api/aftersales/warranties/${id}/void`, { method: 'POST', body: {} }),
  },
  claims: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/aftersales/claims${qs ? `?${qs}` : ''}`)
    },
    open: () => api('/api/aftersales/claims/open'),
    get: (id) => api(`/api/aftersales/claims/${id}`),
    create: (body) => api('/api/aftersales/claims', { method: 'POST', body }),
    validate: (id) => api(`/api/aftersales/claims/${id}/validate`, { method: 'POST', body: {} }),
    approve: (id, body = {}) =>
      api(`/api/aftersales/claims/${id}/approve`, { method: 'POST', body }),
    reject: (id, body = {}) =>
      api(`/api/aftersales/claims/${id}/reject`, { method: 'POST', body }),
    close: (id) => api(`/api/aftersales/claims/${id}/close`, { method: 'POST', body: {} }),
  },
  rmas: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/aftersales/rmas${qs ? `?${qs}` : ''}`)
    },
    open: () => api('/api/aftersales/rmas/open'),
    get: (id) => api(`/api/aftersales/rmas/${id}`),
    /** ISO 6422-1 A4 return / RMA PDF (session cookie). */
    pdfUrl: (id) => `/api/aftersales/rmas/${id}/pdf`,
    create: (body) => api('/api/aftersales/rmas', { method: 'POST', body }),
    confirm: (id, body = {}) =>
      api(`/api/aftersales/rmas/${id}/confirm`, { method: 'POST', body }),
    close: (id) => api(`/api/aftersales/rmas/${id}/close`, { method: 'POST', body: {} }),
    cancel: (id) => api(`/api/aftersales/rmas/${id}/cancel`, { method: 'POST', body: {} }),
  },
}

export const capabilityApi = {
  processes: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.kind) q.set('kind', params.kind)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/capability/processes${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/capability/processes/${id}`),
    byCode: (code) => api(`/api/capability/processes/by-code/${encodeURIComponent(code)}`),
    create: (body) => api('/api/capability/processes', { method: 'POST', body }),
    update: (id, body) => api(`/api/capability/processes/${id}`, { method: 'PUT', body }),
    activate: (id) => api(`/api/capability/processes/${id}/activate`, { method: 'POST', body: {} }),
    retire: (id) => api(`/api/capability/processes/${id}/retire`, { method: 'POST', body: {} }),
    steps: (id) => api(`/api/capability/processes/${id}/steps`),
    addStep: (id, body) =>
      api(`/api/capability/processes/${id}/steps`, { method: 'POST', body }),
    mermaid: (id) => api(`/api/capability/processes/${id}/mermaid`),
    reseed: () => api('/api/capability/processes/reseed', { method: 'POST', body: {} }),
  },
  procedures: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.processId) q.set('processId', params.processId)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/capability/procedures${qs ? `?${qs}` : ''}`)
    },
    create: (body) => api('/api/capability/procedures', { method: 'POST', body }),
  },
  portfolios: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/capability/portfolios${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/capability/portfolios/${id}`),
    create: (body) => api('/api/capability/portfolios', { method: 'POST', body }),
    close: (id) => api(`/api/capability/portfolios/${id}/close`, { method: 'POST', body: {} }),
  },
  programs: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/capability/programs${qs ? `?${qs}` : ''}`)
    },
    create: (body) => api('/api/capability/programs', { method: 'POST', body }),
  },
  projects: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/capability/projects${qs ? `?${qs}` : ''}`)
    },
    open: () => api('/api/capability/projects/open'),
    get: (id) => api(`/api/capability/projects/${id}`),
    create: (body) => api('/api/capability/projects', { method: 'POST', body }),
    start: (id) => api(`/api/capability/projects/${id}/start`, { method: 'POST', body: {} }),
    complete: (id) =>
      api(`/api/capability/projects/${id}/complete`, { method: 'POST', body: {} }),
    cancel: (id) => api(`/api/capability/projects/${id}/cancel`, { method: 'POST', body: {} }),
    items: (id) => api(`/api/capability/projects/${id}/items`),
    addItem: (id, body) =>
      api(`/api/capability/projects/${id}/items`, { method: 'POST', body }),
  },
  qualities: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/capability/qualities${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/capability/qualities/${id}`),
    create: (body) => api('/api/capability/qualities', { method: 'POST', body }),
    archive: (id) =>
      api(`/api/capability/qualities/${id}/archive`, { method: 'POST', body: {} }),
    items: (id) => api(`/api/capability/qualities/${id}/items`),
    addItem: (id, body) =>
      api(`/api/capability/qualities/${id}/items`, { method: 'POST', body }),
  },
  qualityItems: {
    close: (id) =>
      api(`/api/capability/quality-items/${id}/close`, { method: 'POST', body: {} }),
  },
  changes: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/capability/changes${qs ? `?${qs}` : ''}`)
    },
    open: () => api('/api/capability/changes/open'),
    get: (id) => api(`/api/capability/changes/${id}`),
    create: (body) => api('/api/capability/changes', { method: 'POST', body }),
    implement: (id) =>
      api(`/api/capability/changes/${id}/implement`, { method: 'POST', body: {} }),
    close: (id) => api(`/api/capability/changes/${id}/close`, { method: 'POST', body: {} }),
    cancel: (id) => api(`/api/capability/changes/${id}/cancel`, { method: 'POST', body: {} }),
    items: (id) => api(`/api/capability/changes/${id}/items`),
    addItem: (id, body) =>
      api(`/api/capability/changes/${id}/items`, { method: 'POST', body }),
  },
  knowledge: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.activeOnly) q.set('activeOnly', 'true')
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/capability/knowledge${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/capability/knowledge/${id}`),
    create: (body) => api('/api/capability/knowledge', { method: 'POST', body }),
    children: (id) => api(`/api/capability/knowledge/${id}/children`),
  },
  standards: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/capability/standards${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/capability/standards/${id}`),
    create: (body) => api('/api/capability/standards', { method: 'POST', body }),
    update: (id, body) => api(`/api/capability/standards/${id}`, { method: 'PUT', body }),
  },
}


/** Supply chain (SC kernel) — /api/scm/* */
export const scmApi = {
  vendors: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.q) q.set('q', params.q)
      const qs = q.toString()
      return api(`/api/scm/vendors${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/scm/vendors/${id}`),
    create: (body) => api('/api/scm/vendors', { method: 'POST', body }),
    update: (id, body) => api(`/api/scm/vendors/${id}`, { method: 'PUT', body }),
  },
  products: {
    list: () => api('/api/scm/products'),
    get: (id) => api(`/api/scm/products/${id}`),
    create: (body) => api('/api/scm/products', { method: 'POST', body }),
    update: (id, body) => api(`/api/scm/products/${id}`, { method: 'PUT', body }),
  },
  purchaseOrders: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.q) q.set('q', params.q)
      const qs = q.toString()
      return api(`/api/scm/purchase-orders${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/scm/purchase-orders/${id}`),
    /** ISO 6422-1 A4 purchase order PDF (session cookie). */
    pdfUrl: (id) => `/api/scm/purchase-orders/${id}/pdf`,
    create: (body) => api('/api/scm/purchase-orders', { method: 'POST', body }),
    setLines: (id, body) =>
      api(`/api/scm/purchase-orders/${id}/lines`, { method: 'PUT', body }),
    send: (id) => api(`/api/scm/purchase-orders/${id}/send`, { method: 'POST', body: {} }),
    confirm: (id) => api(`/api/scm/purchase-orders/${id}/confirm`, { method: 'POST', body: {} }),
    receive: (id, body = {}) =>
      api(`/api/scm/purchase-orders/${id}/receive`, { method: 'POST', body }),
    cancel: (id) => api(`/api/scm/purchase-orders/${id}/cancel`, { method: 'POST', body: {} }),
  },
  production: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/scm/production-orders${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/scm/production-orders/${id}`),
    /** ISO 6422-1 A4 production order PDF (session cookie). */
    pdfUrl: (id) => `/api/scm/production-orders/${id}/pdf`,
    create: (body) => api('/api/scm/production-orders', { method: 'POST', body }),
    fromSales: (salesOrderId) =>
      api('/api/scm/production-orders/from-sales', {
        method: 'POST',
        body: { salesOrderId },
      }),
    release: (id) =>
      api(`/api/scm/production-orders/${id}/release`, { method: 'POST', body: {} }),
    start: (id) => api(`/api/scm/production-orders/${id}/start`, { method: 'POST', body: {} }),
    complete: (id) =>
      api(`/api/scm/production-orders/${id}/complete`, { method: 'POST', body: {} }),
    cancel: (id) =>
      api(`/api/scm/production-orders/${id}/cancel`, { method: 'POST', body: {} }),
  },
  lots: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/scm/lots${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/scm/lots/${id}`),
    create: (body) => api('/api/scm/lots', { method: 'POST', body }),
    check: (id, body) => api(`/api/scm/lots/${id}/check`, { method: 'POST', body }),
  },
  inventory: {
    onHand: () => api('/api/scm/inventory/on-hand'),
    moves: () => api('/api/scm/inventory/moves'),
    adjust: (body) => api('/api/scm/inventory/adjust', { method: 'POST', body }),
  },
}

/** Delivery (D-S1–D-S3) — /api/delivery/* */
export const deliveryApi = {
  services: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.activeOnly === false) q.set('activeOnly', 'false')
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/delivery/services${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/delivery/services/${id}`),
    create: (body) => api('/api/delivery/services', { method: 'POST', body }),
    update: (id, body) => api(`/api/delivery/services/${id}`, { method: 'PUT', body }),
  },
  resources: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.activeOnly === false) q.set('activeOnly', 'false')
      if (params.assignees === true || params.fromHr === true) q.set('assignees', 'true')
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      if (params.q) q.set('q', params.q)
      const qs = q.toString()
      return api(`/api/delivery/resources${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/delivery/resources/${id}`),
    create: (body) => api('/api/delivery/resources', { method: 'POST', body }),
    update: (id, body) => api(`/api/delivery/resources/${id}`, { method: 'PUT', body }),
  },
  orders: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/delivery/orders${qs ? `?${qs}` : ''}`)
    },
    open: () => api('/api/delivery/orders/open'),
    get: (id) => api(`/api/delivery/orders/${id}`),
    /** ISO 6422-1 A4 delivery / service delivery note (session cookie). */
    pdfUrl: (id) => `/api/delivery/orders/${id}/pdf`,
    create: (body) => api('/api/delivery/orders', { method: 'POST', body }),
    fromSales: (salesOrderId) =>
      api('/api/delivery/orders/from-sales', {
        method: 'POST',
        body: { salesOrderId },
      }),
    setLines: (id, body) =>
      api(`/api/delivery/orders/${id}/lines`, { method: 'PUT', body }),
    assign: (id, resourceId) =>
      api(`/api/delivery/orders/${id}/assign`, {
        method: 'POST',
        body: { resourceId },
      }),
    schedule: (id, body) =>
      api(`/api/delivery/orders/${id}/schedule`, { method: 'POST', body }),
    start: (id) => api(`/api/delivery/orders/${id}/start`, { method: 'POST', body: {} }),
    complete: (id) =>
      api(`/api/delivery/orders/${id}/complete`, { method: 'POST', body: {} }),
    cancel: (id) => api(`/api/delivery/orders/${id}/cancel`, { method: 'POST', body: {} }),
  },
}

/** HR (HR-S0–HR-S3) — /api/hr/* */
export const hrApi = {
  files: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/hr/files${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/hr/files/${id}`),
    create: (body) => api('/api/hr/files', { method: 'POST', body }),
    update: (id, body) => api(`/api/hr/files/${id}`, { method: 'PUT', body }),
    exit: (id, body = {}) => api(`/api/hr/files/${id}/exit`, { method: 'POST', body }),
  },
  candidates: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.stage) q.set('stage', params.stage)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/hr/candidates${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/hr/candidates/${id}`),
    create: (body) => api('/api/hr/candidates', { method: 'POST', body }),
    advance: (id, stage) =>
      api(`/api/hr/candidates/${id}/advance`, { method: 'POST', body: { stage } }),
    hire: (id, body = {}) => api(`/api/hr/candidates/${id}/hire`, { method: 'POST', body }),
    reject: (id, body = {}) =>
      api(`/api/hr/candidates/${id}/reject`, { method: 'POST', body }),
  },
  time: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.hrFileId) q.set('hrFileId', String(params.hrFileId))
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/hr/time${qs ? `?${qs}` : ''}`)
    },
    create: (body) => api('/api/hr/time', { method: 'POST', body }),
  },
  payrolls: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/hr/payrolls${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/hr/payrolls/${id}`),
    create: (body) => api('/api/hr/payrolls', { method: 'POST', body }),
    setLines: (id, lines) =>
      api(`/api/hr/payrolls/${id}/lines`, { method: 'PUT', body: { lines } }),
    ready: (id) => api(`/api/hr/payrolls/${id}/ready`, { method: 'POST', body: {} }),
    submit: (id) => api(`/api/hr/payrolls/${id}/submit`, { method: 'POST', body: {} }),
    close: (id) => api(`/api/hr/payrolls/${id}/close`, { method: 'POST', body: {} }),
  },
  plans: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/hr/plans${qs ? `?${qs}` : ''}`)
    },
    create: (body) => api('/api/hr/plans', { method: 'POST', body }),
  },
  goals: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/hr/goals${qs ? `?${qs}` : ''}`)
    },
    create: (body) => api('/api/hr/goals', { method: 'POST', body }),
    progress: (id, body) => api(`/api/hr/goals/${id}/progress`, { method: 'POST', body }),
    complete: (id) => api(`/api/hr/goals/${id}/complete`, { method: 'POST', body: {} }),
  },
  policies: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.activeOnly === false) q.set('activeOnly', 'false')
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/hr/policies${qs ? `?${qs}` : ''}`)
    },
    create: (body) => api('/api/hr/policies', { method: 'POST', body }),
  },
  parties: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.partyKind) q.set('partyKind', params.partyKind)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/hr/parties${qs ? `?${qs}` : ''}`)
    },
    create: (body) => api('/api/hr/parties', { method: 'POST', body }),
  },
}

/** Digital Technology (DIG-S0–DIG-S2) — /api/digital/* */
export const digitalApi = {
  systems: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.activeOnly) q.set('activeOnly', 'true')
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/digital/systems${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/digital/systems/${id}`),
    create: (body) => api('/api/digital/systems', { method: 'POST', body }),
    update: (id, body) => api(`/api/digital/systems/${id}`, { method: 'PUT', body }),
  },
  data: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.dataClass) q.set('dataClass', params.dataClass)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/digital/data${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/digital/data/${id}`),
    create: (body) => api('/api/digital/data', { method: 'POST', body }),
  },
  solutions: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/digital/solutions${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/digital/solutions/${id}`),
    create: (body) => api('/api/digital/solutions', { method: 'POST', body }),
    activate: (id) =>
      api(`/api/digital/solutions/${id}/activate`, { method: 'POST', body: {} }),
    retire: (id) => api(`/api/digital/solutions/${id}/retire`, { method: 'POST', body: {} }),
  },
  changes: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/digital/changes${qs ? `?${qs}` : ''}`)
    },
    open: () => api('/api/digital/changes/open'),
    create: (body) => api('/api/digital/changes', { method: 'POST', body }),
    start: (id) => api(`/api/digital/changes/${id}/start`, { method: 'POST', body: {} }),
    complete: (id) =>
      api(`/api/digital/changes/${id}/complete`, { method: 'POST', body: {} }),
    cancel: (id) => api(`/api/digital/changes/${id}/cancel`, { method: 'POST', body: {} }),
  },
  cis: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.systemId) q.set('systemId', params.systemId)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/digital/cis${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/digital/cis/${id}`),
    create: (body) => api('/api/digital/cis', { method: 'POST', body }),
  },
  tickets: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/digital/tickets${qs ? `?${qs}` : ''}`)
    },
    open: () => api('/api/digital/tickets/open'),
    create: (body) => api('/api/digital/tickets', { method: 'POST', body }),
    start: (id) => api(`/api/digital/tickets/${id}/start`, { method: 'POST', body: {} }),
    resolve: (id, body = {}) =>
      api(`/api/digital/tickets/${id}/resolve`, { method: 'POST', body }),
    close: (id) => api(`/api/digital/tickets/${id}/close`, { method: 'POST', body: {} }),
  },
}

/** Agentic Resources (AR-S0–AR-S3) — /api/agents/* */
export const agentsApi = {
  agents: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/agents/agents${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/agents/agents/${id}`),
    create: (body) => api('/api/agents/agents', { method: 'POST', body }),
    update: (id, body) => api(`/api/agents/agents/${id}`, { method: 'PUT', body }),
    seedCore: () => api('/api/agents/agents/seed-core', { method: 'POST', body: {} }),
    activate: (id) =>
      api(`/api/agents/agents/${id}/activate`, { method: 'POST', body: {} }),
    pause: (id) => api(`/api/agents/agents/${id}/pause`, { method: 'POST', body: {} }),
    retire: (id) => api(`/api/agents/agents/${id}/retire`, { method: 'POST', body: {} }),
  },
  hives: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/agents/hives${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/agents/hives/${id}`),
    create: (body) => api('/api/agents/hives', { method: 'POST', body }),
    setMaster: (id, body) =>
      api(`/api/agents/hives/${id}/master`, { method: 'POST', body }),
    assign: (id, agentId) =>
      api(`/api/agents/hives/${id}/assign`, { method: 'POST', body: { agentId } }),
    members: (id) => api(`/api/agents/hives/${id}/members`),
    activate: (id) =>
      api(`/api/agents/hives/${id}/activate`, { method: 'POST', body: {} }),
    close: (id) => api(`/api/agents/hives/${id}/close`, { method: 'POST', body: {} }),
  },
  approvals: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.decision) q.set('decision', params.decision)
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/agents/approvals${qs ? `?${qs}` : ''}`)
    },
    open: () => api('/api/agents/approvals/open'),
    get: (id) => api(`/api/agents/approvals/${id}`),
    submit: (body) => api('/api/agents/approvals', { method: 'POST', body }),
    approve: (id, body = {}) =>
      api(`/api/agents/approvals/${id}/approve`, { method: 'POST', body }),
    reject: (id, body = {}) =>
      api(`/api/agents/approvals/${id}/reject`, { method: 'POST', body }),
    cancel: (id) =>
      api(`/api/agents/approvals/${id}/cancel`, { method: 'POST', body: {} }),
  },
  memories: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.kind) q.set('kind', params.kind)
      if (params.includeArchived) q.set('includeArchived', 'true')
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/agents/memories${qs ? `?${qs}` : ''}`)
    },
    get: (id) => api(`/api/agents/memories/${id}`),
    record: (body) => api('/api/agents/memories', { method: 'POST', body }),
    archive: (id) =>
      api(`/api/agents/memories/${id}/archive`, { method: 'POST', body: {} }),
    byAgent: (agentId) => api(`/api/agents/agents/${agentId}/memories`),
  },
  links: {
    list: (params = {}) => {
      const q = new URLSearchParams()
      if (params.max != null) q.set('max', String(params.max))
      if (params.offset != null) q.set('offset', String(params.offset))
      if (params.sort) q.set('sort', params.sort)
      if (params.order) q.set('order', params.order)
      const qs = q.toString()
      return api(`/api/agents/links${qs ? `?${qs}` : ''}`)
    },
    create: (body) => api('/api/agents/links', { method: 'POST', body }),
    unlink: (id) => api(`/api/agents/links/${id}/unlink`, { method: 'POST', body: {} }),
  },
}

/** CSV import packs (Wave B) */
export const importApi = {
  packs: () => api('/api/import/packs'),
  list: () => api('/api/import/jobs'),
  get: (id) => api(`/api/import/jobs/${id}`),
  upload: (body) => api('/api/import/jobs', { method: 'POST', body }),
  /** Re-analyze with optional mapping + pack-wide defaultNature. */
  preview: (id, { mapping, defaultNature } = {}) =>
    api(`/api/import/jobs/${id}/preview`, {
      method: 'POST',
      body: {
        ...(mapping ? { mapping } : {}),
        ...(defaultNature ? { defaultNature } : {}),
      },
    }),
  /** Requires confirmed: true after user reviews analysis summary. */
  commit: (id, { mapping, defaultNature, confirmed = true } = {}) =>
    api(`/api/import/jobs/${id}/commit`, {
      method: 'POST',
      body: {
        confirmed: !!confirmed,
        ...(mapping ? { mapping } : {}),
        ...(defaultNature ? { defaultNature } : {}),
      },
    }),
}

/** Contextual help (Wave B + ADR-044 HelpContext) */
export const helpApi = {
  topics: () => api('/api/help/topics'),
  /**
   * @param {string|object} routeOrContext pathname string OR HelpContext
   *   { path, desk, tab, resource, objectId, status, processCode }
   */
  resolve: (routeOrContext, lang) => {
    const q = new URLSearchParams()
    if (routeOrContext && typeof routeOrContext === 'object') {
      const c = routeOrContext
      q.set('route', c.path || c.route || '/')
      if (c.desk) q.set('desk', c.desk)
      if (c.tab) q.set('tab', c.tab)
      if (c.resource) q.set('resource', c.resource)
      if (c.objectId != null && c.objectId !== '') q.set('objectId', String(c.objectId))
      if (c.status) q.set('status', c.status)
      if (c.processCode) q.set('processCode', c.processCode)
    } else {
      q.set('route', routeOrContext || '/')
    }
    if (lang) q.set('lang', lang)
    return api(`/api/help/resolve?${q.toString()}`)
  },
  get: (helpKey, lang) => {
    const q = lang ? `?lang=${encodeURIComponent(lang)}` : ''
    return api(`/api/help/topics/${encodeURIComponent(helpKey)}${q}`)
  },
}

/** Locales + current user (i18n proof) */
export const localesApi = {
  list: () => api('/api/locales'),
}

/** OI-CUR01 — system catalog + company in-use */
export const currenciesApi = {
  list: () => api('/api/currencies'),
  inUse: () => api('/api/currencies/in-use'),
  setInUse: (codes) =>
    api('/api/currencies/in-use', { method: 'PUT', body: { codes } }),
}

/** OI-F23 / L14 — tax-code catalog + company in-use */
export const taxCodesApi = {
  list: (params = {}) => {
    const q = new URLSearchParams()
    if (params.jurisdiction) q.set('jurisdiction', params.jurisdiction)
    const qs = q.toString()
    return api(`/api/tax-codes${qs ? `?${qs}` : ''}`)
  },
  inUse: () => api('/api/tax-codes/in-use'),
  setInUse: (body) =>
    api('/api/tax-codes/in-use', {
      method: 'PUT',
      body: Array.isArray(body) ? { codes: body } : body,
    }),
}

export const meApi = {
  selectCompany: (companyId) =>
    api('/api/me/company', { method: 'POST', body: { companyId } }),
  get: (params = {}) => {
    const q = new URLSearchParams()
    if (params.locale) q.set('locale', params.locale)
    const qs = q.toString()
    return api(`/api/me${qs ? `?${qs}` : ''}`)
  },
  preferences: () => api('/api/me/preferences'),
  updatePreferences: (body) => api('/api/me/preferences', { method: 'PUT', body }),
}
