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

import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { ALLOWED_PAGE_SIZES } from '../hooks/usePagedList'

/**
 * Shared pager under tables / ResponsiveDataList cards.
 * total = full filtered set size (LIST-PAGINATION-CONCEPT).
 */
export default function ListPager({
  total = 0,
  max = 25,
  offset = 0,
  onOffset,
  onMax,
  pageSizes = ALLOWED_PAGE_SIZES,
  disabled = false,
  className = '',
}) {
  const { t } = useTranslation('common')
  const from = total === 0 ? 0 : offset + 1
  const to = total === 0 ? 0 : Math.min(offset + max, total)
  const hasPrev = offset > 0
  const hasNext = offset + max < total

  if (total === 0 && !disabled) {
    return (
      <div className={`list-pager list-pager-empty ${className}`.trim()}>
        <span className="list-pager-label muted">
          {t('pager.zero', { defaultValue: '0 items' })}
        </span>
      </div>
    )
  }

  return (
    <div className={`list-pager ${className}`.trim()} role="navigation" aria-label={t('pager.nav', { defaultValue: 'Pagination' })}>
      <span className="list-pager-label">
        {t('pager.showing', {
          from,
          to,
          total,
          defaultValue: 'Showing {{from}}–{{to}} of {{total}}',
        })}
      </span>
      <div className="list-pager-controls">
        <label className="list-pager-size">
          <span className="muted">{t('pager.perPage', { defaultValue: 'Per page' })}</span>
          <select
            className="filter-select"
            value={max}
            disabled={disabled}
            onChange={(e) => onMax?.(Number(e.target.value))}
          >
            {pageSizes.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
            {!pageSizes.includes(max) && <option value={max}>{max}</option>}
          </select>
        </label>
        <button
          type="button"
          className="btn btn-ghost"
          disabled={disabled || !hasPrev}
          onClick={() => onOffset?.(Math.max(0, offset - max))}
          aria-label={t('pager.prev', { defaultValue: 'Previous page' })}
        >
          <ChevronLeft size={16} /> {t('pager.prevShort', { defaultValue: 'Prev' })}
        </button>
        <button
          type="button"
          className="btn btn-ghost"
          disabled={disabled || !hasNext}
          onClick={() => onOffset?.(offset + max)}
          aria-label={t('pager.next', { defaultValue: 'Next page' })}
        >
          {t('pager.nextShort', { defaultValue: 'Next' })} <ChevronRight size={16} />
        </button>
      </div>
    </div>
  )
}
