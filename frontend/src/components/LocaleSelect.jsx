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
import { localesApi } from '../api/client'

/**
 * SupportedLocale dropdown for partner communication language (OI-I18N02).
 * Blank = outbound falls back to en_US.
 */
export default function LocaleSelect({
  name = 'communicationLocale',
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
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    localesApi
      .list()
      .then((r) => {
        if (cancelled) return
        const rows = (r.items || []).slice().sort((a, b) =>
          (a.code || '').localeCompare(b.code || ''),
        )
        setItems(rows)
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

  const known = new Set(items.map((c) => c.code).filter(Boolean))
  const orphan = value && !known.has(value) ? value : null

  return (
    <>
      <select
        id={id || name}
        name={name}
        value={value || ''}
        disabled={disabled || loading}
        required={required}
        onChange={onChange}
        style={{ width: '100%' }}
      >
        {allowEmpty && (
          <option value="">
            {loading
              ? t('actions.loading')
              : emptyLabel ||
                t('wb.localeFallback', {
                  defaultValue: '— default (English) —',
                })}
          </option>
        )}
        {orphan && (
          <option value={orphan}>
            {orphan} ({t('wb.custom', { defaultValue: 'current' })})
          </option>
        )}
        {items.map((c) => (
          <option key={c.code} value={c.code}>
            {`${c.code} — ${t(`locale.names.${c.code}`, {
              defaultValue: c.name || c.code,
            })}`}
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
