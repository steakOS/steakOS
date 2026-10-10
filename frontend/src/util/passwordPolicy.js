// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
/** Mirrors backend PasswordPolicy — human account passwords. */
export const MIN_LENGTH = 12

export const RULES_HINT =
  'at least 12 characters, with 1 lowercase, 1 uppercase, 1 number, and 1 special character'

export const DEV_DEFAULT_PASSWORD = 'Schn1tz3l3ck3$'

/**
 * @param {string} raw
 * @returns {string|null} error message or null if ok
 */
export function validatePassword(raw) {
  if (raw == null || String(raw).length === 0) {
    return `password is required (${RULES_HINT})`
  }
  const s = String(raw)
  if (s.length < MIN_LENGTH) {
    return `password must be at least ${MIN_LENGTH} characters (${RULES_HINT})`
  }
  if (!/[a-z]/.test(s)) {
    return `password must include a lowercase letter (${RULES_HINT})`
  }
  if (!/[A-Z]/.test(s)) {
    return `password must include an uppercase letter (${RULES_HINT})`
  }
  if (!/[0-9]/.test(s)) {
    return `password must include a number (${RULES_HINT})`
  }
  if (!/[^A-Za-z0-9]/.test(s)) {
    return `password must include a special character (${RULES_HINT})`
  }
  return null
}

export function isValidPassword(raw) {
  return validatePassword(raw) == null
}
