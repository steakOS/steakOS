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

import { useEffect, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'

/**
 * Scroll the selected list row into view.
 * @param {Element|string|null|undefined} containerOrSelector
 * @param {string} [selectedAttr='[data-selected="true"]']
 */
export function scrollSelectedIntoView(
  containerOrSelector,
  selectedAttr = '[data-selected="true"]',
) {
  if (typeof document === 'undefined') return
  let root = null
  if (typeof containerOrSelector === 'string') {
    root = document.querySelector(containerOrSelector)
  } else if (containerOrSelector && containerOrSelector.nodeType === 1) {
    root = containerOrSelector
  } else {
    root = document
  }
  const el = root?.querySelector?.(selectedAttr) || document.querySelector(selectedAttr)
  if (el && typeof el.scrollIntoView === 'function') {
    el.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  }
}

/**
 * Sync desk tab + selected id with URL search params `tab` and `id`.
 * Preserves other query params. Clears of `id` use replace:true.
 *
 * @param {{
 *   deskKey?: string,
 *   defaultTab: string,
 *   tab: string,
 *   setTab: (tab: string) => void,
 *   selectedId: string|number|null|undefined,
 *   setSelectedId: (id: string|null) => void,
 * }} opts
 */
export default function useDeskSelection({
  deskKey: _deskKey,
  defaultTab,
  tab,
  setTab,
  selectedId,
  setSelectedId,
}) {
  const [searchParams, setSearchParams] = useSearchParams()
  const hydrated = useRef(false)

  // Read URL → state once on mount (and when URL is edited externally before hydrate).
  useEffect(() => {
    if (hydrated.current) return
    hydrated.current = true
    const urlTab = searchParams.get('tab')
    const urlId = searchParams.get('id')
    if (urlTab && urlTab !== tab) {
      setTab(urlTab)
    } else if (!urlTab && defaultTab && !tab) {
      setTab(defaultTab)
    }
    if (urlId != null && urlId !== '' && String(urlId) !== String(selectedId ?? '')) {
      setSelectedId(urlId)
    }
    // intentionally mount-only hydrate
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Write state → URL
  useEffect(() => {
    if (!hydrated.current) return
    const next = new URLSearchParams(searchParams)
    const curTab = searchParams.get('tab')
    const curId = searchParams.get('id')
    const wantTab = tab || defaultTab || ''
    const wantId = selectedId != null && selectedId !== '' ? String(selectedId) : ''

    let changed = false
    if (wantTab) {
      if (curTab !== wantTab) {
        next.set('tab', wantTab)
        changed = true
      }
    } else if (curTab != null) {
      next.delete('tab')
      changed = true
    }

    const clearingId = !wantId && curId != null
    if (wantId) {
      if (curId !== wantId) {
        next.set('id', wantId)
        changed = true
      }
    } else if (curId != null) {
      next.delete('id')
      changed = true
    }

    if (!changed) return
    setSearchParams(next, { replace: clearingId })
  }, [tab, selectedId, defaultTab, searchParams, setSearchParams])

  return {
    searchParams,
    scrollSelectedIntoView,
  }
}
