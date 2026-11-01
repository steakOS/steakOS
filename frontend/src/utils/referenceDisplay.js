// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
/**
 * Operator-facing reference display: Name (uniqueId).
 * Used by record view/edit modals and entity pickers (ADR-048).
 */

import { entityDisplayLabel } from './entityDisplayLabel'

/** FK keys handled elsewhere (assignee merge, etc.). */
const SPECIAL_FK_KEYS = new Set(['assigneeAgentId', 'assigneeHumanNpub'])

/**
 * Business keys that end in `Id` but are identifiers, not foreign keys to resolve.
 * (Shown as Unique ID / business codes — not Name (id) references.)
 */
const NON_FK_ID_KEYS = new Set(['profileId'])

/**
 * Map foreign-key field → domain get target for async name resolve when the
 * parent row has no companion `*Name` denormalized field.
 */
export const FK_RESOLVE_TARGETS = {
  vendorId: { package: 'procurement', resource: 'Vendor' },
  customerId: { package: 'sales', resource: 'Customer' },
  financePayableId: { package: 'finance', resource: 'Payable' },
  financeInvoiceId: { package: 'finance', resource: 'Invoice' },
  journalEntryId: { package: 'finance', resource: 'JournalEntry' },
  catalogueItemId: { package: 'catalogue', resource: 'CatalogueItem' },
  productId: { package: 'catalogue', resource: 'CatalogueItem' },
  componentProductId: { package: 'catalogue', resource: 'CatalogueItem' },
  componentItemId: { package: 'catalogue', resource: 'CatalogueItem' },
  resultingOrderId: { package: 'sales', resource: 'SalesOrder' },
  sourceQuoteId: { package: 'sales', resource: 'CustomerQuote' },
  purchaseOrderId: { package: 'procurement', resource: 'PurchaseOrder' },
  payableId: { package: 'finance', resource: 'Payable' },
  salesOrderId: { package: 'sales', resource: 'SalesOrder' },
  partyId: { package: 'stakeholder', resource: 'Party' },
  configItemId: { package: 'itsupport', resource: 'ConfigItem' },
  serviceLevelId: { package: 'aftersales', resource: 'ServiceLevel' },
  hrFileId: { package: 'human', resource: 'HrFile' },
  resourceId: { package: 'delivery', resource: 'Resource' },
}

/**
 * @param {unknown} name
 * @param {unknown} id
 * @returns {string}
 */
export function formatReferenceDisplay(name, id) {
  const idStr = id == null || id === '' ? '' : String(id).trim()
  const nameStr = name != null && String(name).trim() !== '' ? String(name).trim() : ''
  if (nameStr && idStr) return `${nameStr} (${idStr})`
  if (nameStr) return nameStr
  if (idStr) return `(${idStr})`
  return '—'
}

/**
 * Label for an entity row including its unique id in brackets.
 * @param {object|null|undefined} row
 * @param {{ keys?: string[], fallback?: string }} [opts]
 */
export function entityReferenceLabel(row, opts = {}) {
  if (row == null || typeof row !== 'object') {
    return opts.fallback != null ? String(opts.fallback) : '—'
  }
  const name = entityDisplayLabel(row, { keys: opts.keys, fallback: '' })
  return formatReferenceDisplay(name || null, row.id)
}

export function isForeignKeyField(key) {
  if (!key || key === 'id' || key === 'uniqueId') return false
  if (SPECIAL_FK_KEYS.has(key) || NON_FK_ID_KEYS.has(key)) return false
  return /Id$/.test(key)
}

/** vendorId → vendorName */
export function companionNameKey(idKey) {
  if (!idKey || !idKey.endsWith('Id')) return null
  return `${idKey.slice(0, -2)}Name`
}

/**
 * Resolve a display name for a FK value from the parent record and optional
 * async-resolved label map (`${key}:${id}` → name string).
 * @param {string} key
 * @param {unknown} value
 * @param {object|null|undefined} record
 * @param {Record<string, string>} [resolvedNames]
 */
export function resolveReferenceName(key, value, record, resolvedNames = {}) {
  if (value != null && typeof value === 'object' && !Array.isArray(value)) {
    return entityDisplayLabel(value, { fallback: '' }) || null
  }
  const id = value
  if (id == null || id === '') return null
  const cacheKey = `${key}:${id}`
  if (resolvedNames[cacheKey]) return resolvedNames[cacheKey]

  const nameKey = companionNameKey(key)
  if (nameKey && record && record[nameKey] != null && String(record[nameKey]).trim() !== '') {
    return String(record[nameKey]).trim()
  }
  // Nested association: record.vendor = { id, name }
  const nestKey = key.endsWith('Id') ? key.slice(0, -2) : null
  if (nestKey && record && record[nestKey] != null && typeof record[nestKey] === 'object') {
    const nested = record[nestKey]
    const label = entityDisplayLabel(nested, { fallback: '' })
    if (label) return label
  }
  return null
}

/**
 * Extract numeric/string id from a FK value (scalar or nested object).
 */
export function referenceIdOf(value) {
  if (value == null || value === '') return null
  if (typeof value === 'object' && !Array.isArray(value)) {
    return value.id != null ? value.id : null
  }
  return value
}

/**
 * Transform raw view entries into operator-facing rows:
 * - Unique ID first
 * - FK fields as Name (id); companion *Name keys suppressed
 * - Nested association objects collapsed to Name (id)
 *
 * @param {object|null|undefined} record
 * @param {Array<[string, unknown]>} rawEntries
 * @param {{ resolvedNames?: Record<string, string> }} [opts]
 * @returns {Array<[string, unknown]>}
 */
export function buildReferenceAwareViewEntries(record, rawEntries, opts = {}) {
  const resolvedNames = opts.resolvedNames || {}
  const suppressed = new Set()
  const rows = []

  // Collect companion *Name keys to hide when the matching *Id is present
  for (const [key] of rawEntries || []) {
    if (isForeignKeyField(key)) {
      const nk = companionNameKey(key)
      if (nk) suppressed.add(nk)
      // Also suppress nested object duplicate if we have vendorId + vendor
      if (key.endsWith('Id')) suppressed.add(key.slice(0, -2))
    }
  }

  const uniqueId = record?.id
  rows.push(['uniqueId', uniqueId != null && uniqueId !== '' ? uniqueId : null])

  for (const [key, value] of rawEntries || []) {
    if (key === 'id' || key === 'uniqueId') continue
    if (suppressed.has(key)) continue

    if (isForeignKeyField(key)) {
      const refId = referenceIdOf(value)
      if (refId == null || refId === '') {
        rows.push([key, null])
        continue
      }
      const name = resolveReferenceName(key, value, record, resolvedNames)
      rows.push([key, formatReferenceDisplay(name, refId)])
      continue
    }

    // Bare association object without parallel *Id (rare)
    if (
      value != null &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      value.id != null &&
      (value.name != null || value.title != null || value.code != null)
    ) {
      rows.push([key, entityReferenceLabel(value)])
      continue
    }

    rows.push([key, value])
  }

  return rows
}

/**
 * FK fields on a record that still need an async name resolve.
 * @param {object|null|undefined} record
 * @returns {Array<{ key: string, id: string|number, package: string, resource: string }>}
 */
export function pendingFkResolves(record) {
  if (!record || typeof record !== 'object') return []
  const out = []
  for (const [key, value] of Object.entries(record)) {
    if (!isForeignKeyField(key)) continue
    const refId = referenceIdOf(value)
    if (refId == null || refId === '') continue
    if (resolveReferenceName(key, value, record, {})) continue
    const target = FK_RESOLVE_TARGETS[key]
    if (!target) continue
    out.push({ key, id: refId, package: target.package, resource: target.resource })
  }
  return out
}
