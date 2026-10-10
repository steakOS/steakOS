// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
/**
 * Resolve data-product labels when API package ids differ from locale trees
 * (e.g. supplychain/StockMove vs production.StockMove).
 */
import { humanizeLabel } from './humanize'

/** Alternate locale packages to try when `${pkg}.${resource}` is missing. */
export const DOMAIN_PACKAGE_ALIASES = {
  supplychain: ['production', 'procurement'],
  agent: ['agents'],
  scm: ['procurement', 'supplychain', 'production'],
}

/**
 * @param {Function} tDomain — i18n `t` bound to ns `domain` (or with ns option)
 * @param {string} pkg
 * @param {string} resource
 */
export function domainResourceLabel(tDomain, pkg, resource) {
  const keys = [`${pkg}.${resource}`]
  for (const alt of DOMAIN_PACKAGE_ALIASES[pkg] || []) {
    keys.push(`${alt}.${resource}`)
  }
  return tDomain(keys, { defaultValue: humanizeLabel(resource) })
}

/**
 * @param {Function} tCommon — i18n `t` bound to ns `common`
 * @param {string} pkg
 * @param {string} [fallbackLabel]
 */
export function domainGroupLabel(tCommon, pkg, fallbackLabel) {
  const keys = [`groups.${pkg}`]
  for (const alt of DOMAIN_PACKAGE_ALIASES[pkg] || []) {
    keys.push(`groups.${alt}`)
  }
  return tCommon(keys, {
    defaultValue: humanizeLabel(fallbackLabel || pkg),
  })
}
