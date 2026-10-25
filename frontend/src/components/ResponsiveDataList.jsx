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

import useNarrowViewport from '../hooks/useNarrowViewport'
import ListSortMenu from './ListSortMenu'

/**
 * RESPONSIVE-UI R3: wide = existing table (children); narrow = card list.
 *
 * fields: {
 *   id, label?,
 *   primary?: boolean,   // card title line
 *   secondary?: boolean, // second line
 *   meta?: boolean,      // muted trailing / amount line
 *   priority?: 'low',    // skip on cards (same as col-priority-low)
 *   render: (item) => ReactNode
 * }
 *
 * Optional sortFields / sort / order / onSortChange → ListSortMenu above cards
 * (server-driven; never sorts items locally).
 */
export default function ResponsiveDataList({
  items = [],
  getKey = (item) => item?.id,
  fields = [],
  actions,
  onSelect,
  selectedKey,
  empty = 'No rows',
  loading = false,
  children,
  sortFields,
  sort,
  order,
  onSortChange,
  defaultSort = 'id',
  defaultOrder = 'desc',
}) {
  const narrow = useNarrowViewport()

  if (!narrow) {
    return children
  }

  const visible = fields.filter((f) => f.priority !== 'low')
  const primary = visible.find((f) => f.primary) || visible[0]
  const secondary = visible.filter((f) => f !== primary && f.secondary)
  const rest = visible.filter((f) => f !== primary && !f.secondary)
  const showSort =
    Array.isArray(sortFields) && sortFields.length > 0 && typeof onSortChange === 'function'

  return (
    <div className="data-card-list" role="list">
      {showSort && (
        <ListSortMenu
          fields={sortFields}
          sort={sort}
          order={order}
          defaultSort={defaultSort}
          defaultOrder={defaultOrder}
          onSortChange={onSortChange}
        />
      )}
      {loading && <p className="muted data-card-list-msg">Loading…</p>}
      {!loading && items.length === 0 && (
        <p className="muted data-card-list-msg">{empty}</p>
      )}
      {items.map((item) => {
        const key = getKey(item)
        const selected = selectedKey != null && selectedKey === key
        const clickable = typeof onSelect === 'function'
        return (
          <div
            key={key}
            role="listitem"
            data-selected={selected ? 'true' : undefined}
            className={`data-card${selected ? ' is-selected' : ''}${clickable ? ' is-clickable' : ''}`}
            onClick={clickable ? () => onSelect(item) : undefined}
            onKeyDown={
              clickable
                ? (e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault()
                      onSelect(item)
                    }
                  }
                : undefined
            }
            tabIndex={clickable ? 0 : undefined}
          >
            <div className="data-card-body">
              {primary && (
                <div className="data-card-title">{primary.render(item)}</div>
              )}
              {secondary.map((f) => (
                <div key={f.id} className="data-card-secondary">
                  {f.render(item)}
                </div>
              ))}
              {rest.length > 0 && (
                <div className="data-card-meta-row">
                  {rest.map((f) => {
                    const node = f.render(item)
                    if (node == null || node === false || node === '') return null
                    return (
                      <span key={f.id} className="data-card-meta">
                        {f.label ? (
                          <span className="data-card-meta-label">{f.label} </span>
                        ) : null}
                        {node}
                      </span>
                    )
                  })}
                </div>
              )}
            </div>
            {actions && (
              <div
                className="data-card-actions"
                onClick={(e) => e.stopPropagation()}
                onKeyDown={(e) => e.stopPropagation()}
              >
                {actions(item)}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
