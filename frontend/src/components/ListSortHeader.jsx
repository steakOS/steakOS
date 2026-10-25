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

/**
 * Sortable table header (Task 7640).
 * Click cycle: inactive → asc → desc → default (typically id desc).
 * Does not sort locally — calls onSortChange so the parent re-fetches.
 */
export default function ListSortHeader({
  field,
  label,
  sort,
  order,
  defaultSort = 'id',
  defaultOrder = 'desc',
  onSortChange,
  className,
}) {
  const active = sort === field
  const ariaSort = !active ? 'none' : order === 'asc' ? 'ascending' : 'descending'
  const marker = !active ? '' : order === 'asc' ? ' ↑' : ' ↓'

  const onClick = () => {
    if (typeof onSortChange !== 'function') return
    if (!active) {
      onSortChange({ sort: field, order: 'asc' })
    } else if (order === 'asc') {
      onSortChange({ sort: field, order: 'desc' })
    } else {
      onSortChange({ sort: defaultSort, order: defaultOrder })
    }
  }

  return (
    <th className={className} aria-sort={ariaSort}>
      <button
        type="button"
        className={`list-sort-header${active ? ' is-active' : ''}`}
        onClick={onClick}
      >
        {label}
        {marker ? <span className="list-sort-marker" aria-hidden="true">{marker}</span> : null}
      </button>
    </th>
  )
}
