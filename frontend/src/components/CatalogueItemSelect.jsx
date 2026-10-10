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

import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { catalogueApi } from '../api/client'
import AsyncEntitySelect, { PICKER_MAX } from './AsyncEntitySelect'

/**
 * Unified goods+services typeahead for SO/PO/Quote lines (CATALOGUE-ITEM-CONCEPT + LIST-PAGINATION P4).
 * @param {'sale'|'purchase'} forChannel
 * @param {string} [nature] GOODS | SERVICE | '' for all
 */
export default function CatalogueItemSelect({
  value,
  onChange,
  forChannel = 'sale',
  nature = '',
  disabled = false,
  id = 'catalogueItemId',
}) {
  const { t } = useTranslation('common')

  const search = useCallback(
    ({ q, max }) =>
      catalogueApi.list({
        q: q || undefined,
        for: forChannel,
        nature: nature || undefined,
        activeOnly: true,
        max: max || PICKER_MAX,
        offset: 0,
      }),
    [forChannel, nature],
  )

  const resolveById = useCallback((idVal) => catalogueApi.get(idVal), [])

  const getLabel = useCallback((it) => {
    if (!it) return ''
    const icon = it.nature === 'SERVICE' ? '🔧 ' : '📦 '
    const code = it.code ? ` (${it.code})` : ''
    const price =
      it.listPrice != null
        ? ` · ${Number(it.listPrice).toFixed(2)} ${it.currency || ''}`
        : ''
    return `${icon}${it.name}${code}${price}`
  }, [])

  return (
    <AsyncEntitySelect
      id={id}
      value={value}
      onChange={onChange}
      search={search}
      resolveById={resolveById}
      getLabel={getLabel}
      disabled={disabled}
      placeholder={t('catalogue.selectItem', {
        defaultValue: '— search product or service —',
      })}
      emptyHint={t('catalogue.empty', {
        defaultValue: 'No catalogue items match — create Products or Services first.',
      })}
    />
  )
}
