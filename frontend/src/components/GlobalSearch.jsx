// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Search, X } from 'lucide-react'
import { searchApi } from '../api/client'
import { withReturnTo } from '../config/domainRegistry'

const DEBOUNCE_MS = 300
const MIN_LEN = 2

const TYPE_ORDER = [
  'customer',
  'vendor',
  'catalogueItem',
  'customerQuote',
  'salesOrder',
  'invoice',
  'purchaseOrder',
  'task',
  'legalEntity',
]

function isEditableTarget(el) {
  if (!el || !(el instanceof Element)) return false
  const tag = el.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
  if (el.isContentEditable) return true
  return !!el.closest('[contenteditable="true"]')
}

/**
 * Top-bar record search (Task 7642) — distinct from ⌘K CommandPalette (nav).
 */
export default function GlobalSearch({ narrow = false }) {
  const { t } = useTranslation(['common'])
  const navigate = useNavigate()
  const location = useLocation()
  const inputRef = useRef(null)
  const rootRef = useRef(null)
  const [expanded, setExpanded] = useState(!narrow)
  const [q, setQ] = useState('')
  const [debounced, setDebounced] = useState('')
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [items, setItems] = useState([])
  const [truncated, setTruncated] = useState(false)
  const [activeIdx, setActiveIdx] = useState(-1)

  useEffect(() => {
    if (!narrow) setExpanded(true)
  }, [narrow])

  useEffect(() => {
    const id = setTimeout(() => setDebounced(q.trim()), DEBOUNCE_MS)
    return () => clearTimeout(id)
  }, [q])

  useEffect(() => {
    if (debounced.length < MIN_LEN) {
      setItems([])
      setTruncated(false)
      setError(null)
      setLoading(false)
      return undefined
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    searchApi
      .search({ q: debounced })
      .then((res) => {
        if (cancelled) return
        setItems(Array.isArray(res?.items) ? res.items : [])
        setTruncated(!!res?.truncated)
        setOpen(true)
        setActiveIdx(-1)
      })
      .catch((err) => {
        if (cancelled) return
        setItems([])
        setError(err?.message || t('shell.search.error', { defaultValue: 'Search failed' }))
        setOpen(true)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [debounced, t])

  const close = useCallback(() => {
    setOpen(false)
    setActiveIdx(-1)
    if (narrow) {
      setExpanded(false)
      setQ('')
    }
  }, [narrow])

  const focusSearch = useCallback(() => {
    setExpanded(true)
    setOpen(true)
    requestAnimationFrame(() => inputRef.current?.focus())
  }, [])

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === '/' && !e.metaKey && !e.ctrlKey && !e.altKey) {
        if (isEditableTarget(e.target)) return
        e.preventDefault()
        focusSearch()
      }
      if (e.key === 'Escape' && open) {
        e.preventDefault()
        close()
        inputRef.current?.blur()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [close, focusSearch, open])

  useEffect(() => {
    const onDoc = (e) => {
      if (!rootRef.current?.contains(e.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  const groups = useMemo(() => {
    const byType = new Map()
    for (const it of items) {
      const type = it.type || 'other'
      if (!byType.has(type)) byType.set(type, [])
      byType.get(type).push(it)
    }
    const ordered = []
    for (const type of TYPE_ORDER) {
      if (byType.has(type)) ordered.push([type, byType.get(type)])
    }
    for (const [type, list] of byType) {
      if (!TYPE_ORDER.includes(type)) ordered.push([type, list])
    }
    return ordered
  }, [items])

  const flat = useMemo(() => groups.flatMap(([, list]) => list), [groups])

  const go = useCallback(
    (item) => {
      if (!item?.href) return
      // Stamp origin so DomainForm SAVE/cancel returns here (not domain list).
      // Skip when already on a /domains/… surface — list session restore handles that.
      const onDomainSurface = /^\/domains\/[^/]+\/[^/]+/.test(location.pathname)
      const origin = onDomainSurface ? null : `${location.pathname}${location.search || ''}`
      navigate(withReturnTo(item.href, origin))
      close()
    },
    [close, location.pathname, location.search, navigate],
  )

  const typeLabel = (type) =>
    t(`shell.search.types.${type}`, {
      defaultValue: type,
    })

  const onKeyDownInput = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      if (!flat.length) return
      setOpen(true)
      setActiveIdx((i) => (i + 1) % flat.length)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      if (!flat.length) return
      setOpen(true)
      setActiveIdx((i) => (i <= 0 ? flat.length - 1 : i - 1))
    } else if (e.key === 'Enter') {
      if (activeIdx >= 0 && flat[activeIdx]) {
        e.preventDefault()
        go(flat[activeIdx])
      } else if (flat.length === 1) {
        e.preventDefault()
        go(flat[0])
      }
    } else if (e.key === 'Escape') {
      e.preventDefault()
      close()
      inputRef.current?.blur()
    }
  }

  if (narrow && !expanded) {
    return (
      <div className="global-search global-search--compact" ref={rootRef}>
        <button
          type="button"
          className="btn btn-ghost global-search-icon-btn"
          title={t('shell.search.placeholder', {
            defaultValue: 'Search customers, invoices, tasks…',
          })}
          aria-label={t('shell.search.placeholder', {
            defaultValue: 'Search customers, invoices, tasks…',
          })}
          onClick={focusSearch}
        >
          <Search size={16} />
        </button>
      </div>
    )
  }

  const showPanel = open && (loading || error || debounced.length >= MIN_LEN)

  return (
    <div className={`global-search${narrow ? ' global-search--narrow' : ''}`} ref={rootRef}>
      <div className="global-search-field">
        <Search size={16} className="global-search-leading" aria-hidden="true" />
        <input
          ref={inputRef}
          type="text"
          className="global-search-input"
          value={q}
          placeholder={t('shell.search.placeholder', {
            defaultValue: 'Search customers, invoices, tasks…',
          })}
          aria-label={t('shell.search.aria', { defaultValue: 'Global search' })}
          aria-expanded={showPanel}
          aria-controls="global-search-results"
          autoComplete="off"
          onChange={(e) => {
            setQ(e.target.value)
            setOpen(true)
          }}
          onFocus={() => {
            if (debounced.length >= MIN_LEN || error) setOpen(true)
          }}
          onKeyDown={onKeyDownInput}
        />
        {/* One clear control only when the box has text (Task 7657). type=text
            avoids the native webkit search cancel that stacked with this button. */}
        {!!q && (
          <button
            type="button"
            className="btn btn-ghost global-search-clear"
            aria-label={t('shell.search.clear', { defaultValue: 'Clear search' })}
            onClick={() => {
              setQ('')
              setItems([])
              if (narrow) close()
              else inputRef.current?.focus()
            }}
          >
            <X size={14} />
          </button>
        )}
      </div>
      {showPanel && (
        <div className="global-search-panel" id="global-search-results" role="listbox">
          {loading && (
            <div className="global-search-status">
              {t('actions.loading', { defaultValue: 'Loading…' })}
            </div>
          )}
          {!loading && error && <div className="global-search-status is-error">{error}</div>}
          {!loading && !error && debounced.length >= MIN_LEN && items.length === 0 && (
            <div className="global-search-status">
              {t('shell.search.empty', { defaultValue: 'No matches' })}
            </div>
          )}
          {!loading && !error && groups.length > 0 && (
            <ul className="global-search-list">
              {groups.map(([type, list]) => (
                <li key={type} className="global-search-group">
                  <div className="global-search-group-label">{typeLabel(type)}</div>
                  <ul>
                    {list.map((item) => {
                      const idx = flat.indexOf(item)
                      return (
                        <li key={`${item.type}-${item.id}`}>
                          <button
                            type="button"
                            role="option"
                            aria-selected={idx === activeIdx}
                            className={`global-search-item${idx === activeIdx ? ' is-active' : ''}`}
                            onMouseEnter={() => setActiveIdx(idx)}
                            onClick={() => go(item)}
                          >
                            <span className="global-search-item-title">{item.title}</span>
                            <span className="global-search-item-meta">
                              {item.status ? (
                                <span className="global-search-pill">{item.status}</span>
                              ) : null}
                              {item.subtitle ? (
                                <span className="global-search-subtitle">{item.subtitle}</span>
                              ) : null}
                            </span>
                          </button>
                        </li>
                      )
                    })}
                  </ul>
                </li>
              ))}
            </ul>
          )}
          {!loading && truncated && (
            <div className="global-search-footer">
              {t('shell.search.truncated', { defaultValue: 'Showing partial results' })}
            </div>
          )}
          <div className="global-search-hint">
            {t('shell.search.hint', {
              defaultValue: 'Press / to focus · Esc to close · ⌘K jumps to pages',
            })}
          </div>
        </div>
      )}
    </div>
  )
}
