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

import { useEffect } from 'react'

/**
 * Nielsen WP-9 — desk list keyboard navigation.
 * j/k or ArrowUp/Down move selection; Enter focuses detail; n toggles new; / focuses filter; ? opens help.
 *
 * @param {{
 *   enabled?: boolean,
 *   itemIds: Array<string|number>,
 *   selectedId: string|number|null|undefined,
 *   setSelectedId: (id: string|number|null) => void,
 *   onToggleNew?: () => void,
 *   onHelp?: () => void,
 *   filterSelector?: string,
 *   detailSelector?: string,
 * }} opts
 */
export default function useDeskKeyboard({
  enabled = true,
  itemIds = [],
  selectedId,
  setSelectedId,
  onToggleNew,
  onHelp,
  filterSelector = '.desk-shell-col1 select.filter-select, .desk-shell-col1 input[type="search"], .desk-shell-col1 .filter-select',
  detailSelector = '.desk-shell-col2',
}) {
  useEffect(() => {
    if (!enabled) return undefined

    const onKey = (e) => {
      const tag = (e.target?.tagName || '').toLowerCase()
      const editable =
        tag === 'input' ||
        tag === 'textarea' ||
        tag === 'select' ||
        e.target?.isContentEditable
      if (editable && e.key !== 'Escape') return

      const ids = itemIds.map((id) => String(id))
      const cur = selectedId != null ? String(selectedId) : null
      const idx = cur ? ids.indexOf(cur) : -1

      if (e.key === 'j' || e.key === 'ArrowDown') {
        e.preventDefault()
        if (!ids.length) return
        const next = idx < 0 ? 0 : Math.min(ids.length - 1, idx + 1)
        setSelectedId(ids[next])
      } else if (e.key === 'k' || e.key === 'ArrowUp') {
        e.preventDefault()
        if (!ids.length) return
        const next = idx < 0 ? 0 : Math.max(0, idx - 1)
        setSelectedId(ids[next])
      } else if (e.key === 'Enter' && cur) {
        const detail = document.querySelector(detailSelector)
        const focusable = detail?.querySelector?.(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
        )
        focusable?.focus?.()
      } else if (e.key === 'n' && typeof onToggleNew === 'function') {
        e.preventDefault()
        onToggleNew()
      } else if (e.key === '/' && !e.ctrlKey && !e.metaKey) {
        e.preventDefault()
        const el = document.querySelector(filterSelector)
        el?.focus?.()
      } else if (e.key === '?' && typeof onHelp === 'function') {
        e.preventDefault()
        onHelp()
      }
    }

    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [
    enabled,
    itemIds,
    selectedId,
    setSelectedId,
    onToggleNew,
    onHelp,
    filterSelector,
    detailSelector,
  ])
}
