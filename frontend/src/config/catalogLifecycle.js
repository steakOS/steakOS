// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors

/**
 * Admin / reference table lifecycle for SystemCatalogs + DomainList badges.
 *
 * in_use  — desks or day-to-day config use this table
 * reserve — seeded / present for later metals; not in current Bronze desk flows
 * system  — shared global catalogue (no tenant npub)
 */

/** @type {Record<string, 'in_use' | 'reserve' | 'system'>} */
export const CATALOG_LIFECYCLE = {
  // —— helper (reference) ——
  'helper/Countries': 'system',
  'helper/Currencies': 'system',
  'helper/SupportedLocale': 'system',
  'helper/Address': 'in_use',
  'helper/Classification': 'reserve',
  'helper/SimpleClass': 'reserve',
  'helper/SimpleStatus': 'reserve',
  'helper/SimpleType': 'reserve',
  'helper/SimpleUnit': 'in_use',
  'helper/Tag': 'reserve',

  // —— configuration ——
  'configuration/GlobalConfig': 'reserve',
  'configuration/UserConfig': 'reserve',
  'configuration/CompanySetting': 'in_use',
  'configuration/LegalEntity': 'in_use',
  'configuration/Site': 'in_use',
  'configuration/BankAccount': 'in_use',

  // —— generic content ——
  'generic/Content': 'in_use',
  'generic/ContentType': 'system',
  'generic/ContentTypeConfig': 'system',

  // —— catalogue ——
  'catalogue/CatalogueItem': 'in_use',

  // —— core ——
  'core/Task': 'in_use',
}

/**
 * @param {string} pkg
 * @param {string} resourceName
 * @returns {'in_use' | 'reserve' | 'system'}
 */
export function catalogLifecycle(pkg, resourceName) {
  const key = `${pkg}/${resourceName}`
  if (CATALOG_LIFECYCLE[key]) return CATALOG_LIFECYCLE[key]
  // Admin-nav packages default to reserve unless listed
  if (pkg === 'helper' || pkg === 'configuration' || pkg === 'generic') return 'reserve'
  return 'in_use'
}

export function lifecycleBadgeClass(lifecycle) {
  if (lifecycle === 'in_use') return 'badge badge-kind-human'
  if (lifecycle === 'system') return 'badge badge-muted'
  return 'badge badge-warn'
}
