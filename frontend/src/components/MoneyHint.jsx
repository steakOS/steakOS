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

import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fxApi } from '../api/client'
import {
  fiatToSats,
  formatCompactSats,
  formatFiatHint,
  satsToFiat,
} from '../utils/moneyFx'

/**
 * Quiet conversion tip for money fields (PDR-023).
 *
 * @param {object} props
 * @param {'EUR'|'USD'|'BTC'} props.primaryRail
 * @param {string|number} props.amount — primary amount (fiat major or sats if unit=SATS)
 * @param {'SATS'|'BTC'} [props.bitcoinDisplayUnit]
 * @param {'EUR'|'USD'} [props.displayFiat] — when primary is BTC
 */
export default function MoneyHint({
  primaryRail = 'EUR',
  amount,
  bitcoinDisplayUnit = 'SATS',
  displayFiat = 'EUR',
}) {
  const { t } = useTranslation(['common'])
  const [fx, setFx] = useState(null)
  const [err, setErr] = useState(null)

  const rail = String(primaryRail || 'EUR').toUpperCase()
  const quote = rail === 'BTC' ? String(displayFiat || 'EUR').toUpperCase() : rail

  useEffect(() => {
    let cancelled = false
    if (amount === '' || amount == null || Number(amount) === 0) {
      setFx(null)
      setErr(null)
      return undefined
    }
    setErr(null)
    fxApi
      .btc(quote)
      .then((r) => {
        if (!cancelled) setFx(r)
      })
      .catch((e) => {
        if (!cancelled) {
          setFx(null)
          setErr(e?.message || 'rate unavailable')
        }
      })
    return () => {
      cancelled = true
    }
  }, [quote, amount])

  if (amount === '' || amount == null || Number(amount) === 0) return null

  let tip = null
  if (fx?.rate) {
    if (rail === 'BTC') {
      let sats = Number(amount)
      if (String(bitcoinDisplayUnit).toUpperCase() === 'BTC') {
        sats = Math.round(Number(amount) * 100_000_000)
      }
      const fiat = satsToFiat(sats, fx.rate)
      tip = formatFiatHint(fiat, quote)
    } else {
      const sats = fiatToSats(amount, fx.rate)
      tip = formatCompactSats(sats)
    }
  }

  if (!tip && !err) {
    return <span className="muted money-hint">…</span>
  }
  if (!tip && err) {
    return (
      <span className="muted money-hint" title={err}>
        {t('money.rateUnavailable', { defaultValue: 'Rate unavailable' })}
      </span>
    )
  }

  return (
    <span
      className={`muted money-hint${fx?.stale ? ' money-hint--stale' : ''}`}
      title={
        fx?.stale
          ? t('money.staleRate', {
              defaultValue: 'Price may be outdated ({{age}}s)',
              age: fx.ageSeconds ?? '?',
            })
          : `${fx.source || ''} · ${quote}/BTC`
      }
    >
      ≈ {tip}
      {fx?.stale ? ` · ${t('money.stale', { defaultValue: 'stale' })}` : ''}
    </span>
  )
}
