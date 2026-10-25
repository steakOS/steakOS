// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
/**
 * Soft (non-blocking) checks for company / legal-entity display names.
 * Flags HTML/script-like and unusual punctuation that polluted a LIVE primary name.
 */

/** Letters (incl. common Latin extras), digits, spaces, and typical legal-name punctuation. */
const SAFE =
  /^[\p{L}\p{M}\p{N}\s.&'’()\-\/,+@°#]+$/u

/**
 * @param {string|null|undefined} name
 * @returns {{ warn: boolean, code?: 'html'|'control'|'unusual', message?: string }}
 */
export function companyNameSpecialCharWarn(name) {
  const raw = name == null ? '' : String(name)
  const s = raw.trim()
  if (!s) return { warn: false }

  const lower = s.toLowerCase()
  if (
    /[<>]/.test(s) ||
    /<\s*\/?\s*script\b/i.test(s) ||
    /javascript\s*:/i.test(lower) ||
    /\bon\w+\s*=/.test(lower) ||
    /&lt;|&gt;|&#x?0*3[ce];/i.test(s)
  ) {
    return {
      warn: true,
      code: 'html',
      message:
        'This name looks like it includes HTML or script markup. Prefer a plain legal name — you can continue, but it may display oddly.',
    }
  }

  if (/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/.test(s)) {
    return {
      warn: true,
      code: 'control',
      message:
        'This name includes control characters. Prefer printable letters and normal punctuation.',
    }
  }

  if (!SAFE.test(s)) {
    return {
      warn: true,
      code: 'unusual',
      message:
        'This name includes unusual special characters. Letters, digits, and common legal punctuation (e.g. & . , - / ( )) are safest.',
    }
  }

  return { warn: false }
}
