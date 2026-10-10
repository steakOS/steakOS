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

import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

const DEBOUNCE_MS = 250
/** Default result cap for typeahead (LIST-PAGINATION P4 pickers). */
export const PICKER_MAX = 25

/**
 * Debounced async typeahead for entity pickers (LIST-PAGINATION P4).
 *
 * @param {string|number|null} value selected id
 * @param {(id: string, item: object|null) => void} onChange
 * @param {(args: { q: string, max: number }) => Promise<{ items?: any[] }>} search
 * @param {(item: object) => string} getLabel
 * @param {(id: string|number) => Promise<object|null>} [resolveById] load selected when not in results
 * @param {string} [placeholder]
 * @param {boolean} [disabled]
 * @param {string} [id]
 * @param {string} [emptyHint]
 * @param {number} [max]
 */
export default function AsyncEntitySelect({
  value,
  onChange,
  search,
  getLabel = (it) => it?.name || String(it?.id ?? ''),
  resolveById,
  placeholder,
  disabled = false,
  id,
  emptyHint,
  max = PICKER_MAX,
  className = '',
}) {
  const { t } = useTranslation('common')
  const listId = useId()
  const rootRef = useRef(null)
  const timerRef = useRef(null)
  const reqRef = useRef(0)

  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [selectedLabel, setSelectedLabel] = useState('')
  const [highlight, setHighlight] = useState(0)

  const runSearch = useCallback(
    async (q) => {
      const req = ++reqRef.current
      setLoading(true)
      setError(null)
      try {
        const data = await search({ q: q || '', max })
        if (req !== reqRef.current) return
        setItems(data?.items || [])
        setHighlight(0)
      } catch (e) {
        if (req !== reqRef.current) return
        setError(e.message || String(e))
        setItems([])
      } finally {
        if (req === reqRef.current) setLoading(false)
      }
    },
    [search, max],
  )

  // Resolve selected label when value changes
  useEffect(() => {
    if (value == null || value === '') {
      setSelectedLabel('')
      return
    }
    const inList = items.find((it) => String(it.id) === String(value))
    if (inList) {
      setSelectedLabel(getLabel(inList))
      return
    }
    if (!resolveById) return
    let cancelled = false
    resolveById(value)
      .then((row) => {
        if (!cancelled && row) setSelectedLabel(getLabel(row))
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, resolveById])

  // Debounced search while open
  useEffect(() => {
    if (!open) return undefined
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => runSearch(query), DEBOUNCE_MS)
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [open, query, runSearch])

  // Close on outside click
  useEffect(() => {
    if (!open) return undefined
    const onDoc = (e) => {
      if (rootRef.current && !rootRef.current.contains(e.target)) {
        setOpen(false)
        setQuery('')
      }
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [open])

  const pick = (item) => {
    const idVal = item?.id != null ? String(item.id) : ''
    setSelectedLabel(item ? getLabel(item) : '')
    setOpen(false)
    setQuery('')
    onChange?.(idVal, item || null)
  }

  const clear = () => {
    setSelectedLabel('')
    setQuery('')
    setItems([])
    onChange?.('', null)
  }

  const onKeyDown = (e) => {
    if (!open) {
      if (e.key === 'ArrowDown' || e.key === 'Enter') {
        e.preventDefault()
        setOpen(true)
      }
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      setOpen(false)
      setQuery('')
      return
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setHighlight((h) => Math.min(h + 1, Math.max(0, items.length - 1)))
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHighlight((h) => Math.max(0, h - 1))
      return
    }
    if (e.key === 'Enter' && items[highlight]) {
      e.preventDefault()
      pick(items[highlight])
    }
  }

  const display = open ? query : selectedLabel
  const ph =
    placeholder ||
    t('picker.search', { defaultValue: 'Type to search…' })

  return (
    <div className={`async-entity-select ${className}`.trim()} ref={rootRef}>
      <div className="async-entity-select-row">
        <input
          id={id}
          type="text"
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          autoComplete="off"
          disabled={disabled}
          value={display}
          placeholder={value && !open ? selectedLabel || ph : ph}
          onChange={(e) => {
            if (!open) setOpen(true)
            setQuery(e.target.value)
            if (value) {
              // typing replaces selection until pick
              onChange?.('', null)
              setSelectedLabel('')
            }
          }}
          onFocus={() => {
            setOpen(true)
            setQuery('')
          }}
          onKeyDown={onKeyDown}
          style={{ width: '100%' }}
        />
        {value != null && value !== '' && !disabled && (
          <button
            type="button"
            className="btn btn-ghost async-entity-select-clear"
            aria-label={t('actions.clear', { defaultValue: 'Clear' })}
            onClick={clear}
          >
            ×
          </button>
        )}
      </div>
      {open && (
        <ul id={listId} className="async-entity-select-list" role="listbox">
          {loading && (
            <li className="muted async-entity-select-meta" role="presentation">
              {t('actions.loading')}
            </li>
          )}
          {error && (
            <li className="async-entity-select-meta" role="presentation" style={{ color: '#b91c1c' }}>
              {error}
            </li>
          )}
          {!loading && !error && items.length === 0 && (
            <li className="muted async-entity-select-meta" role="presentation">
              {emptyHint ||
                t('picker.empty', { defaultValue: 'No matches — try another search.' })}
            </li>
          )}
          {items.map((it, idx) => (
            <li
              key={it.id}
              role="option"
              aria-selected={String(it.id) === String(value)}
              className={
                idx === highlight ? 'async-entity-select-opt active' : 'async-entity-select-opt'
              }
              onMouseEnter={() => setHighlight(idx)}
              onMouseDown={(e) => {
                e.preventDefault()
                pick(it)
              }}
            >
              {getLabel(it)}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
