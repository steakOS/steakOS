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

import { useCallback, useState } from 'react'
import { fetchContentMap } from '../utils/contentSummary'
import { attachPendingContent } from '../components/PendingContentComposer'

/**
 * Host content maps + pending queues for desk create flows.
 * specs: [{ key: 'quote', package: 'sales', resource: 'CustomerQuote' }, ...]
 */
export function useContentIntegration(specs = []) {
  const [maps, setMaps] = useState(() => {
    const init = {}
    specs.forEach((s) => {
      init[s.key] = {}
    })
    return init
  })
  const [pending, setPending] = useState(() => {
    const init = {}
    specs.forEach((s) => {
      init[s.key] = []
    })
    return init
  })

  const refreshMaps = useCallback(
    async (rowsByKey) => {
      const next = { ...maps }
      await Promise.all(
        specs.map(async (s) => {
          const rows = rowsByKey?.[s.key]
          if (rows) {
            next[s.key] = await fetchContentMap(s.package, s.resource, rows)
          }
        }),
      )
      setMaps(next)
      return next
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [specs.map((s) => s.key).join('|')],
  )

  const setPendingFor = useCallback((key, value) => {
    setPending((prev) => ({
      ...prev,
      [key]: typeof value === 'function' ? value(prev[key] || []) : value,
    }))
  }, [])

  const flushPending = useCallback(
    async (key, hostId) => {
      const spec = specs.find((s) => s.key === key)
      const items = pending[key] || []
      if (!spec || !hostId || !items.length) return { ok: 0, errors: [] }
      const result = await attachPendingContent(spec.package, spec.resource, hostId, items)
      if (!result.errors?.length || result.ok > 0) {
        setPendingFor(key, [])
      }
      return result
    },
    [pending, specs, setPendingFor],
  )

  const refreshOne = useCallback(
    async (key, rows) => {
      const spec = specs.find((s) => s.key === key)
      if (!spec) return
      const map = await fetchContentMap(spec.package, spec.resource, rows)
      setMaps((prev) => ({ ...prev, [key]: map }))
      return map
    },
    [specs],
  )

  return { maps, pending, setPendingFor, refreshMaps, refreshOne, flushPending, specs }
}
