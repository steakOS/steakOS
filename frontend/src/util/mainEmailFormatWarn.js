// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
/**
 * Soft (non-blocking) check for company mainEmail (Task 7623).
 * Empty is fine; odd shapes get a tip only — never block setup finish.
 */

/** Basic local@domain shape — not RFC-complete. */
const BASIC =
  /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * @param {string|null|undefined} email
 * @returns {{ warn: boolean, message?: string }}
 */
export function mainEmailFormatWarn(email) {
  const raw = email == null ? '' : String(email)
  const s = raw.trim()
  if (!s) return { warn: false }
  if (BASIC.test(s) && !/\s/.test(s)) return { warn: false }
  return {
    warn: true,
    message:
      'This does not look like a typical email address. You can continue — fix it later on the company profile if needed.',
  }
}
