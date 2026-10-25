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
import { useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Eye } from 'lucide-react'
import RecordViewModal from '../components/RecordViewModal'
import { withReturnTo } from '../config/domainRegistry'

/**
 * Shared Eye + RecordViewModal for desk tables and other list UIs.
 * Usage:
 *   const { viewModal, ViewButton, openView } = useRecordView()
 *   <ViewButton pkg="sales" resource="Customer" record={c} />
 *   {viewModal}
 */
export default function useRecordView() {
  const { t } = useTranslation('common')
  const location = useLocation()
  const [viewing, setViewing] = useState(null)
  // DomainList already restores via sessionStorage; only stamp returnTo from desks/hubs.
  const onDomainMasterList = /^\/domains\/[^/]+\/[^/]+\/?$/.test(location.pathname)
  const originPath = onDomainMasterList
    ? null
    : `${location.pathname}${location.search || ''}`

  const openView = useCallback((pkg, resource, record) => {
    if (!record?.id && record?.id !== 0) return
    setViewing({ pkg, resource, record })
  }, [])

  const closeView = useCallback(() => setViewing(null), [])

  const ViewButton = useCallback(
    function ViewButton({ pkg, resource, record, className = 'btn btn-ghost', size = 14 }) {
      if (!record?.id && record?.id !== 0) return null
      return (
        <button
          type="button"
          className={className}
          title={t('view.open')}
          aria-label={t('view.open')}
          onClick={(e) => {
            e.stopPropagation()
            openView(pkg, resource, record)
          }}
        >
          <Eye size={size} />
        </button>
      )
    },
    [openView, t],
  )

  const viewModal = (
    <RecordViewModal
      open={!!viewing}
      onClose={closeView}
      package={viewing?.pkg}
      resource={viewing?.resource}
      recordId={viewing?.record?.id}
      seed={viewing?.record}
      editPath={
        viewing?.record?.id != null
          ? withReturnTo(
              `/domains/${viewing.pkg}/${viewing.resource}/${viewing.record.id}`,
              originPath,
            )
          : undefined
      }
    />
  )

  return { openView, closeView, viewModal, ViewButton, viewing }
}
