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
 * Record view modal field rules (pending ADR — see steakOS-dev docs/adr draft).
 * Chrome shows title + status; body omits duplicates and non-business technical fields.
 */

/** Always omit from the body — security/technical, no business process value. */
export const TECHNICAL_VIEW_KEYS = new Set([
  'protection',
  'class',
  'metaClass',
  'constraints',
  'errors',
  'attached',
  'dirty',
  'version',
  'npubCreatedBy',
  'npubUpdatedBy',
  'dateCreated',
  'lastUpdated',
])

/**
 * Keys already represented in modal chrome (title / status pill).
 * Resource-specific; Task omits name + status from the body.
 */
export function chromeDuplicateKeys(pkg, resource) {
  const r = String(resource || '')
  // Title + status live in chrome identity row — never repeat in the body.
  const keys = ['name', 'title', 'status', 'invoiceStatus', 'hiveStatus']
  if (r === 'Task') {
    return new Set(keys)
  }
  return new Set(keys)
}

/** Friendly labels for common keys (override humanize). */
export const VIEW_FIELD_LABELS = {
  npubOwner: 'Owner',
  assignee: 'Assignee',
  assigneeAgentId: 'Assignee',
  assigneeHumanNpub: 'Assignee',
  deadlineAt: 'Deadline',
  startAt: 'Start',
  endAt: 'End',
  urgency: 'Urgency',
  importance: 'Importance',
  description: 'Description',
  status: 'Status',
  name: 'Name',
  nature: 'Kind',
  code: 'Code',
  listPrice: 'List price',
}

/** Friendly display for stored enum-ish values in the view modal. */
export function viewFieldDisplayValue(key, value) {
  if (value == null || value === '') return value
  if (key === 'nature') {
    const v = String(value)
    if (v === 'GOODS') return 'Product'
    if (v === 'SERVICE') return 'Service'
  }
  return value
}

export function viewFieldLabel(key, t) {
  const mapped = VIEW_FIELD_LABELS[key]
  if (mapped) {
    return t ? t(`view.fields.${key}`, { defaultValue: mapped }) : mapped
  }
  // camelCase → words
  const human = String(key)
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/\bid\b/gi, '')
    .replace(/\s+/g, ' ')
    .trim()
  return human.charAt(0).toUpperCase() + human.slice(1)
}

export function recordStatusValue(record) {
  if (!record || typeof record !== 'object') return null
  const v = record.status || record.invoiceStatus || record.hiveStatus || null
  if (v == null || String(v).trim() === '') return null
  return String(v)
}

/**
 * Known business fields per resource — used when “Show all fields” is on so
 * nulls omitted by the API still appear as empty rows.
 */
export const KNOWN_BUSINESS_FIELDS = {
  Task: [
    'description',
    'deadlineAt',
    'startAt',
    'endAt',
    'urgency',
    'importance',
    'assigneeAgentId',
    'assigneeHumanNpub',
    'npubOwner',
  ],
  CatalogueItem: [
    'code',
    'nature',
    'unit',
    'listPrice',
    'currency',
    'description',
    'active',
    'canBeSold',
    'canBeBought',
    'defaultTaxCode',
    'npubOwner',
  ],
}

export function ensureKnownFields(record, pkg, resource, { showAll = false } = {}) {
  if (!showAll || !record || typeof record !== 'object') return record
  const known = KNOWN_BUSINESS_FIELDS[String(resource || '')]
  if (!known || !known.length) return record
  const out = { ...record }
  for (const k of known) {
    if (!(k in out)) out[k] = null
  }
  return out
}
