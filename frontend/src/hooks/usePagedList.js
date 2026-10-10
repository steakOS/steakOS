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

import { useCallback, useEffect, useRef, useState } from 'react'

/** LIST-PAGINATION-CONCEPT default page size */
export const DEFAULT_PAGE_SIZE = 25
export const ALLOWED_PAGE_SIZES = [10, 25, 50, 100]

/**
 * Server-driven paged list state.
 * Filters/sort live in `filters` and always go to the fetcher (full dataset on server).
 *
 * fetcher({ max, offset, ...filters }) → { items, total, max?, offset? }
 */
export default function usePagedList({
  fetcher,
  defaultMax = DEFAULT_PAGE_SIZE,
  filters = {},
  enabled = true,
  deps = [],
}) {
  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [max, setMaxState] = useState(defaultMax)
  const [offset, setOffsetState] = useState(0)
  const [loading, setLoading] = useState(!!enabled)
  const [error, setError] = useState(null)
  const filtersRef = useRef(filters)
  filtersRef.current = filters
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher

  const load = useCallback(
    async (next = {}) => {
      if (!enabled) return null
      const nextMax = next.max != null ? next.max : max
      let nextOffset = next.offset != null ? next.offset : offset
      if (next.resetOffset) nextOffset = 0
      setLoading(true)
      setError(null)
      try {
        const data = await fetcherRef.current({
          max: nextMax,
          offset: nextOffset,
          ...filtersRef.current,
        })
        const list = data?.items || []
        const tot = data?.total ?? list.length
        let appliedOffset = data?.offset != null ? data.offset : nextOffset
        let appliedMax = data?.max != null ? data.max : nextMax
        // clamp empty past-end page
        if (list.length === 0 && tot > 0 && appliedOffset > 0) {
          const last = Math.max(0, Math.floor((tot - 1) / appliedMax) * appliedMax)
          const retry = await fetcherRef.current({
            max: appliedMax,
            offset: last,
            ...filtersRef.current,
          })
          setItems(retry?.items || [])
          setTotal(retry?.total ?? tot)
          setMaxState(retry?.max ?? appliedMax)
          setOffsetState(retry?.offset ?? last)
          return retry
        }
        setItems(list)
        setTotal(tot)
        setMaxState(appliedMax)
        setOffsetState(appliedOffset)
        return data
      } catch (e) {
        setError(e)
        throw e
      } finally {
        setLoading(false)
      }
    },
    [enabled, max, offset],
  )

  const reload = useCallback(() => load({}), [load])

  const setOffset = useCallback(
    (o) => {
      const next = typeof o === 'function' ? o(offset) : o
      return load({ offset: Math.max(0, next) })
    },
    [load, offset],
  )

  const setMax = useCallback(
    (m) => {
      const next = typeof m === 'function' ? m(max) : m
      const clamped = Math.min(100, Math.max(1, next))
      return load({ max: clamped, offset: 0 })
    },
    [load, max],
  )

  const setPage = useCallback(
    (page1Based) => {
      const p = Math.max(1, page1Based)
      return load({ offset: (p - 1) * max })
    },
    [load, max],
  )

  // Initial + when filters/deps change → reset to page 0
  const filterKey = JSON.stringify(filters)
  useEffect(() => {
    if (!enabled) {
      setItems([])
      setTotal(0)
      setLoading(false)
      return
    }
    load({ offset: 0, resetOffset: true }).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, filterKey, defaultMax, ...deps])

  const from = total === 0 ? 0 : offset + 1
  const to = Math.min(offset + items.length, total)
  const page = max > 0 ? Math.floor(offset / max) + 1 : 1
  const pageCount = max > 0 ? Math.max(1, Math.ceil(total / max)) : 1
  const hasPrev = offset > 0
  const hasNext = offset + max < total

  return {
    items,
    total,
    max,
    offset,
    loading,
    error,
    from,
    to,
    page,
    pageCount,
    hasPrev,
    hasNext,
    setOffset,
    setMax,
    setPage,
    reload: load,
    refresh: reload,
    nextPage: () => (hasNext ? setOffset(offset + max) : Promise.resolve()),
    prevPage: () => (hasPrev ? setOffset(Math.max(0, offset - max)) : Promise.resolve()),
  }
}
