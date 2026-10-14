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
import { taxCodesApi } from '../api/client'

const SUPPRESS_REGIMES = new Set(['NONE', 'DE_SMALL_BUSINESS'])

/**
 * Tax-code dropdown from company in-use list (OI-F23 / L14).
 * Kleinunternehmer / NONE: no picker (VAT suppressed).
 */
export default function TaxCodeSelect({
  name = 'taxCode',
  value = '',
  onChange,
  disabled = false,
  required = false,
  id,
  allowEmpty = true,
  emptyLabel,
}) {
  const { t } = useTranslation('common')
  const [items, setItems] = useState([])
  const [regime, setRegime] = useState(null)
  const [defaultCode, setDefaultCode] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    taxCodesApi
      .inUse()
      .then((r) => {
        if (cancelled) return
        const rows = (r.items || []).slice().sort((a, b) =>
          (a.code || '').localeCompare(b.code || ''),
        )
        setItems(rows)
        setRegime(r.taxRegime || null)
        setDefaultCode(r.defaultTaxCode || null)
        setError(null)
      })
      .catch((e) => {
        if (!cancelled) setError(e.message || String(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const suppressed = SUPPRESS_REGIMES.has((regime || '').toUpperCase())

  useEffect(() => {
    if (loading || suppressed || value || !defaultCode || !onChange) return
    if (items.some((c) => c.code === defaultCode)) {
      onChange({ target: { name, value: defaultCode } })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, suppressed, defaultCode, items])

  if (suppressed) {
    return (
      <span className="muted" style={{ fontSize: '0.85rem' }}>
        {t('docLines.taxSuppressed', {
          defaultValue: 'No VAT (Kleinunternehmer / not registered)',
        })}
      </span>
    )
  }

  const known = new Set(items.map((c) => c.code).filter(Boolean))
  const orphan = value && !known.has(value) ? value : null

  return (
    <>
      <select
        id={id || name}
        name={name}
        value={value || ''}
        disabled={disabled || loading}
        required={required && !allowEmpty}
        onChange={onChange}
        style={{ width: '100%' }}
      >
        {allowEmpty && (
          <option value="">
            {loading
              ? t('actions.loading')
              : emptyLabel ||
                t('docLines.taxDefault', { defaultValue: '— company default —' })}
          </option>
        )}
        {orphan && (
          <option value={orphan}>
            {orphan} ({t('wb.custom', { defaultValue: 'current' })})
          </option>
        )}
        {items.map((c) => (
          <option key={c.code} value={c.code}>
            {c.label || `${c.code} — ${c.name || c.code} (${c.ratePercent ?? 0}%)`}
          </option>
        ))}
      </select>
      {error && (
        <span className="muted" style={{ fontSize: '0.75rem', color: '#b91c1c' }}>
          {error}
        </span>
      )}
    </>
  )
}
