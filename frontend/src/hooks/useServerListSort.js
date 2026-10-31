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

/**
 * Server-driven list sort state (Task 7640 / F3).
 * Never sort items in the browser — pass sort/order to the list API.
 */
export default function useServerListSort(defaultSort = 'id', defaultOrder = 'desc') {
  const [sort, setSort] = useState(defaultSort)
  const [order, setOrder] = useState(defaultOrder)

  const onSortChange = useCallback(
    ({ sort: nextSort, order: nextOrder }) => {
      setSort(nextSort || defaultSort)
      setOrder(nextOrder === 'asc' ? 'asc' : 'desc')
    },
    [defaultSort],
  )

  return { sort, order, onSortChange, setSort, setOrder, defaultSort, defaultOrder }
}
