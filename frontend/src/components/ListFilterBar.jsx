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

/**
 * Debounced free-text q + optional children (status chips, etc.). Task 7640.
 * onQChange(qString) — parent should put q into usePagedList filters (server-side).
 */
export default function ListFilterBar({
  q = '',
  onQChange,
  debounceMs = 300,
  placeholder = 'Search…',
  ariaLabel = 'Search',
  children,
  className,
}) {
  const [draft, setDraft] = useState(q || '')

  useEffect(() => {
    setDraft(q || '')
  }, [q])

  useEffect(() => {
    if (typeof onQChange !== 'function') return undefined
    const handle = window.setTimeout(() => {
      const next = (draft || '').trim()
      const cur = (q || '').trim()
      if (next !== cur) onQChange(next)
    }, debounceMs)
    return () => window.clearTimeout(handle)
  }, [draft, debounceMs, onQChange, q])

  return (
    <div className={`list-filter-bar${className ? ` ${className}` : ''}`}>
      <input
        type="search"
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        placeholder={placeholder}
        aria-label={ariaLabel}
        className="list-filter-q"
      />
      {(draft || q) && (
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => {
            setDraft('')
            if (typeof onQChange === 'function') onQChange('')
          }}
        >
          Clear
        </button>
      )}
      {children}
    </div>
  )
}
