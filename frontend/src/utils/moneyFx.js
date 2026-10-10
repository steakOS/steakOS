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

/** PDR-023: rate = quote currency per 1 BTC */

export const SATS_PER_BTC = 100_000_000

export function fiatToSats(fiatAmount, ratePerBtc) {
  const fiat = Number(fiatAmount)
  const rate = Number(ratePerBtc)
  if (!Number.isFinite(fiat) || !Number.isFinite(rate) || rate <= 0) return null
  return Math.round((fiat / rate) * SATS_PER_BTC)
}

export function satsToFiat(sats, ratePerBtc) {
  const s = Number(sats)
  const rate = Number(ratePerBtc)
  if (!Number.isFinite(s) || !Number.isFinite(rate) || rate <= 0) return null
  return (s / SATS_PER_BTC) * rate
}

/**
 * Compact sats for tooltips (D11): 850 sats | 128k sats | 1.34M sats
 */
export function formatCompactSats(sats) {
  const n = Math.abs(Number(sats))
  if (!Number.isFinite(n)) return ''
  const sign = Number(sats) < 0 ? '-' : ''
  if (n < 1000) return `${sign}${Math.round(n)} sats`
  if (n < 1_000_000) {
    const k = n / 1000
    const t = k >= 100 ? Math.round(k) : k >= 10 ? k.toFixed(1) : k.toFixed(2)
    return `${sign}${stripTrailingZeros(t)}k sats`
  }
  const m = n / 1_000_000
  const t = m >= 100 ? Math.round(m) : m >= 10 ? m.toFixed(1) : m.toFixed(2)
  return `${sign}${stripTrailingZeros(t)}M sats`
}

function stripTrailingZeros(s) {
  return String(s).replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1')
}

export function formatFiatHint(amount, currency) {
  if (amount == null || !Number.isFinite(Number(amount))) return ''
  const cur = currency || 'EUR'
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: cur,
      maximumFractionDigits: 2,
    }).format(Number(amount))
  } catch {
    return `${Number(amount).toFixed(2)} ${cur}`
  }
}
