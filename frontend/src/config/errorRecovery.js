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
 * API error code → UX recovery copy + actions (ADR-046 / Nielsen WP-7).
 * Keys are i18n paths under the `common` namespace (`errors.*`).
 */
export const ERROR_RECOVERY = {
  MATRIX_DESK_FORBIDDEN: {
    titleKey: 'errors.matrixDesk.title',
    bodyKey: 'errors.matrixDesk.body',
    actions: [{ labelKey: 'errors.matrixDesk.action', href: '/finance/invoices' }],
  },
  MATRIX_SYSTEM_FORBIDDEN: {
    titleKey: 'errors.matrixSystem.title',
    bodyKey: 'errors.matrixSystem.body',
    actions: [{ labelKey: 'errors.matrixSystem.action', href: '/roles' }],
  },
  MATRIX_UNMAPPED: {
    titleKey: 'errors.matrixUnmapped.title',
    bodyKey: 'errors.matrixUnmapped.body',
    actions: [{ labelKey: 'errors.matrixUnmapped.action', href: '/help' }],
  },
  SESSION_EXPIRED: {
    titleKey: 'errors.sessionExpired.title',
    bodyKey: 'errors.sessionExpired.body',
    actions: [{ labelKey: 'errors.sessionExpired.action', href: '/login/auth' }],
  },
  UNAUTHORIZED: {
    titleKey: 'errors.sessionExpired.title',
    bodyKey: 'errors.sessionExpired.body',
    actions: [{ labelKey: 'errors.sessionExpired.action', href: '/login/auth' }],
  },
  VALIDATION_FAILED: {
    titleKey: 'errors.validation.title',
    bodyKey: 'errors.validation.body',
    actions: [],
  },
  BAD_REQUEST: {
    titleKey: 'errors.validation.title',
    bodyKey: 'errors.validation.body',
    actions: [],
  },
  NOT_FOUND: {
    titleKey: 'errors.notFound.title',
    bodyKey: 'errors.notFound.body',
    actions: [{ labelKey: 'errors.notFound.action', href: '/' }],
  },
  CONFLICT: {
    titleKey: 'errors.conflict.title',
    bodyKey: 'errors.conflict.body',
    actions: [{ labelKey: 'errors.conflict.action', href: null }],
  },
  READ_ONLY_FORBIDDEN: {
    titleKey: 'errors.readOnly.title',
    bodyKey: 'errors.readOnly.body',
    actions: [{ labelKey: 'errors.readOnly.action', href: '/' }],
  },
  POSTING_RULES_UNBALANCED: {
    titleKey: 'errors.postingUnbalanced.title',
    bodyKey: 'errors.postingUnbalanced.body',
    actions: [{ labelKey: 'errors.postingUnbalanced.action', href: '/finance/posting-rules' }],
  },
  POSTING_RULES_INVALID: {
    titleKey: 'errors.postingInvalid.title',
    bodyKey: 'errors.postingInvalid.body',
    actions: [{ labelKey: 'errors.postingInvalid.action', href: '/finance/posting-rules' }],
  },
  TOTP_REQUIRED: {
    titleKey: 'errors.totpRequired.title',
    bodyKey: 'errors.totpRequired.body',
    actions: [{ labelKey: 'errors.totpRequired.action', href: '/totp' }],
  },
  COMPANY_FORBIDDEN: {
    titleKey: 'errors.companyForbidden.title',
    bodyKey: 'errors.companyForbidden.body',
    actions: [{ labelKey: 'errors.companyForbidden.action', href: '/settings' }],
  },
  DOCUMENT_CHAIN_LOCKED: {
    titleKey: 'errors.documentChainLocked.title',
    bodyKey: 'errors.documentChainLocked.body',
    actions: [{ labelKey: 'errors.documentChainLocked.action', href: null }],
  },
}

/**
 * @param {object|string|null|undefined} error
 * @returns {typeof ERROR_RECOVERY[string]|null}
 */
export function resolveErrorRecovery(error) {
  if (!error) return null
  const code =
    (typeof error === 'string' && error) ||
    error.code ||
    error.errorCode ||
    (error.status === 401 ? 'UNAUTHORIZED' : null) ||
    (error.status === 404 ? 'NOT_FOUND' : null) ||
    (error.status === 409 ? 'CONFLICT' : null)
  if (!code) return null
  return ERROR_RECOVERY[code] || null
}
