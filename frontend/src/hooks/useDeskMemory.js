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

import { useCallback, useMemo } from 'react'

/**
 * Persist light desk UI state (tab, filters) in localStorage per company.
 *
 * @param {{ deskKey: string, companyId?: string|number|null }} opts
 */
export default function useDeskMemory({ deskKey, companyId } = {}) {
  const storageKey = useMemo(() => {
    const co = companyId != null && companyId !== '' ? String(companyId) : 'default'
    const desk = deskKey || 'desk'
    return `steakos.desk.${co}.${desk}`
  }, [companyId, deskKey])

  const loadMemory = useCallback(() => {
    if (typeof window === 'undefined' || !window.localStorage) return null
    try {
      const raw = window.localStorage.getItem(storageKey)
      if (!raw) return null
      const parsed = JSON.parse(raw)
      if (!parsed || typeof parsed !== 'object') return null
      return {
        tab: parsed.tab != null ? String(parsed.tab) : undefined,
        filters: parsed.filters && typeof parsed.filters === 'object' ? parsed.filters : undefined,
      }
    } catch {
      return null
    }
  }, [storageKey])

  const saveMemory = useCallback(
    ({ tab, filters } = {}) => {
      if (typeof window === 'undefined' || !window.localStorage) return
      try {
        const prev = loadMemory() || {}
        const next = {
          tab: tab !== undefined ? tab : prev.tab,
          filters: filters !== undefined ? filters : prev.filters,
        }
        window.localStorage.setItem(storageKey, JSON.stringify(next))
      } catch {
        /* quota / private mode */
      }
    },
    [loadMemory, storageKey],
  )

  const clearMemory = useCallback(() => {
    if (typeof window === 'undefined' || !window.localStorage) return
    try {
      window.localStorage.removeItem(storageKey)
    } catch {
      /* ignore */
    }
  }, [storageKey])

  return { storageKey, loadMemory, saveMemory, clearMemory }
}
