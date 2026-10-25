// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
/**
 * Prefer business keys over raw Postgres primary keys in operator-facing chrome.
 * Keep numeric ids in URLs / route params / API calls — not in titles, lists, or toasts.
 */

const BUSINESS_KEYS = [
  'name',
  'title',
  'subject',
  'label',
  'code',
  'sku',
  'username',
  'quoteNumber',
  'orderNumber',
  'salesOrderNumber',
  'purchaseOrderNumber',
  'poNumber',
  'invoiceNumber',
  'payableNumber',
  'deliveryNumber',
  'rmaNumber',
  'claimNumber',
  'inquiryNumber',
  'warrantyNumber',
  'lotNumber',
  'period',
  'profileId',
  'agentKey',
  'displayName',
]

/**
 * @param {object|null|undefined} row
 * @param {{ fallback?: string, keys?: string[] }} [opts]
 * @returns {string}
 */
export function entityDisplayLabel(row, opts = {}) {
  if (row == null || typeof row !== 'object') {
    return opts.fallback != null ? String(opts.fallback) : '—'
  }
  const keys = opts.keys || BUSINESS_KEYS
  for (const k of keys) {
    const v = row[k]
    if (v != null && String(v).trim() !== '') return String(v).trim()
  }
  return opts.fallback != null ? String(opts.fallback) : '—'
}

/**
 * Resolve a related entity label from a list by id without falling back to `#id`.
 * @param {Array<object>} list
 * @param {string|number|null|undefined} id
 * @param {{ fallback?: string }} [opts]
 */
export function labelFromList(list, id, opts = {}) {
  if (id == null || id === '') return opts.fallback != null ? String(opts.fallback) : '—'
  const hit = (list || []).find((x) => String(x?.id) === String(id))
  if (hit) return entityDisplayLabel(hit, opts)
  return opts.fallback != null ? String(opts.fallback) : '—'
}
