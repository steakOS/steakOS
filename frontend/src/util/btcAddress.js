// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
/**
 * Client-side Bitcoin receive-address checks aligned with
 * BtcIntegrationLevelService.validateReceiveAddress (OI-BTC01).
 */

const MAINNET = /^(bc1|[13])[a-zA-HJ-NP-Z0-9]{14,90}$/
const TESTNET = /^(tb1|bcrt1)[a-zA-HJ-NP-Z0-9]{14,90}$/

/**
 * @param {string|null|undefined} addr
 * @returns {{ ok: boolean, code?: 'empty'|'seed'|'format', message?: string }}
 */
export function validateBtcReceiveAddress(addr) {
  const a = (addr || '').trim()
  if (!a) {
    return {
      ok: false,
      code: 'empty',
      message: 'Level 2 requires a Bitcoin receive address (bc1… / 1… / 3…).',
    }
  }
  const lower = a.toLowerCase()
  if (
    lower.includes('xprv') ||
    lower.includes('xpub') ||
    lower.includes('mnemonic') ||
    a.split(/\s+/).length >= 12
  ) {
    return {
      ok: false,
      code: 'seed',
      message: 'Do not enter seeds, xprv, or mnemonics — paste a receive address only.',
    }
  }
  if (!MAINNET.test(a) && !TESTNET.test(a)) {
    return {
      ok: false,
      code: 'format',
      message: 'Expected a Bitcoin address (bc1… / 1… / 3…), not a seed.',
    }
  }
  return { ok: true }
}
