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
import { scmApi } from '../api/client'
import AsyncEntitySelect, { PICKER_MAX } from './AsyncEntitySelect'
import { formatReferenceDisplay } from '../utils/referenceDisplay'

/**
 * Typeahead vendor picker (LIST-PAGINATION P4) — server `q` on /api/scm/vendors.
 */
export default function VendorSelect({
  value,
  onChange,
  disabled = false,
  id = 'vendorId',
  placeholder,
}) {
  const { t } = useTranslation(['desks', 'common'])

  const search = useCallback(
    ({ q, max }) => scmApi.vendors.list({ q: q || undefined, max: max || PICKER_MAX, offset: 0 }),
    [],
  )

  const resolveById = useCallback((idVal) => scmApi.vendors.get(idVal), [])

  return (
    <AsyncEntitySelect
      id={id}
      value={value}
      onChange={onChange}
      search={search}
      resolveById={resolveById}
      getLabel={(v) => formatReferenceDisplay(v?.name, v?.id)}
      disabled={disabled}
      placeholder={
        placeholder ||
        t('scm.selectVendor', { defaultValue: '— search vendor —' })
      }
      emptyHint={t('picker.noVendors', {
        ns: 'common',
        defaultValue: 'No vendors match — create one first.',
      })}
    />
  )
}
