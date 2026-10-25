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
 * Narrow/card sort control (Task 7640).
 * fields: [{ value, label }] — same server sort keys as ListSortHeader.
 */
export default function ListSortMenu({
  fields = [],
  sort,
  order = 'desc',
  defaultSort = 'id',
  defaultOrder = 'desc',
  onSortChange,
  label = 'Sort by',
}) {
  if (!fields.length || typeof onSortChange !== 'function') return null

  const value = sort || defaultSort
  const dir = order === 'asc' ? 'asc' : 'desc'

  return (
    <div className="list-sort-menu" role="group" aria-label={label}>
      <label className="list-sort-menu-label">
        <span className="muted">{label}</span>
        <select
          className="filter-select"
          value={value}
          onChange={(e) => {
            const next = e.target.value || defaultSort
            onSortChange({ sort: next, order: dir })
          }}
        >
          {fields.map((f) => (
            <option key={f.value} value={f.value}>
              {f.label}
            </option>
          ))}
        </select>
      </label>
      <button
        type="button"
        className="btn btn-ghost list-sort-dir"
        title={dir === 'asc' ? 'Ascending' : 'Descending'}
        aria-label={dir === 'asc' ? 'Sort ascending' : 'Sort descending'}
        onClick={() =>
          onSortChange({
            sort: value,
            order: dir === 'asc' ? 'desc' : 'asc',
          })
        }
      >
        {dir === 'asc' ? '↑' : '↓'}
      </button>
      {(value !== defaultSort || dir !== defaultOrder) && (
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => onSortChange({ sort: defaultSort, order: defaultOrder })}
        >
          Reset
        </button>
      )}
    </div>
  )
}
