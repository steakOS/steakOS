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
import { deliveryApi } from '../api/client'
import AsyncEntitySelect, { PICKER_MAX } from './AsyncEntitySelect'
import { formatReferenceDisplay } from '../utils/referenceDisplay'

/**
 * Typeahead assignee picker — active employed humans from HR (synced to delivery resources).
 * Pass assigneesOnly={false} for the generic resource master (agents / other).
 */
export default function ResourceSelect({
  value,
  onChange,
  disabled = false,
  id = 'resourceId',
  assigneesOnly = true,
}) {
  const { t } = useTranslation(['desks', 'common'])
  const search = useCallback(
    ({ q, max }) =>
      deliveryApi.resources.list({
        q: q || undefined,
        max: max || PICKER_MAX,
        offset: 0,
        assignees: assigneesOnly || undefined,
      }),
    [assigneesOnly],
  )
  const resolveById = useCallback((idVal) => deliveryApi.resources.get(idVal), [])
  return (
    <AsyncEntitySelect
      id={id}
      value={value}
      onChange={onChange}
      search={search}
      resolveById={resolveById}
      getLabel={(r) => {
        if (!r) return ''
        const kind = r.hrFileId ? '' : r.resourceKind ? ` · ${r.resourceKind}` : ''
        const email = r.email ? ` · ${r.email}` : ''
        return formatReferenceDisplay(`${r.name || ''}${kind}${email}`, r.id)
      }}
      disabled={disabled}
      placeholder={
        assigneesOnly
          ? t('delivery.selectAssignee', { defaultValue: '— search employee —' })
          : t('delivery.selectResource', { defaultValue: '— search resource —' })
      }
      emptyHint={
        assigneesOnly
          ? t('delivery.emptyAssignees', {
              defaultValue: 'No active employees — add people in HR first.',
            })
          : undefined
      }
    />
  )
}
