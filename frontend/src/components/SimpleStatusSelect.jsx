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
import { domainsApi } from '../api/client'

/**
 * Classifiable status dropdown backed by helper/SimpleStatus catalog.
 * Stores status **code** (e.g. WORKING, COMPLETED); labels use catalog name (e.g. Done).
 */
export default function SimpleStatusSelect({
  name = 'status',
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
    domainsApi
      .list('helper', 'SimpleStatus')
      .then((r) => {
        if (cancelled) return
        const rows = (r.items || [])
          .filter((s) => s.enabled !== false)
          .slice()
          .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || (a.name || '').localeCompare(b.name || ''))
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

  const known = new Set(items.flatMap((s) => [s.code, s.name].filter(Boolean)))
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
              : emptyLabel || t('wb.optional', { defaultValue: '— optional —' })}
          </option>
        )}
        {orphan && (
          <option value={orphan}>
            {orphan} ({t('wb.custom', { defaultValue: 'current' })})
          </option>
        )}
        {items.map((s) => (
          <option key={s.id || s.code} value={s.code || s.name}>
            {s.name || s.code}
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
