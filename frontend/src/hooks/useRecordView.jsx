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
import RecordEditModal from '../components/RecordEditModal'
import { withReturnTo } from '../config/domainRegistry'

/**
 * Shared Eye + RecordViewModal for desk tables and other list UIs.
 *
 * @param {object} [options]
 * @param {boolean} [options.modalEdit] — Edit opens RecordEditModal (Task-style) instead of domain page
 * @param {() => void} [options.onSaved] — after modal edit save
 * @param {(ctx: {pkg, resource, record}) => ReactNode} [options.getBelowFields]
 * @param {(ctx: {pkg, resource, record}) => ReactNode} [options.getFooterExtra]
 * @param {(ctx: {pkg, resource, record}) => boolean} [options.canModalEdit] — default true when modalEdit
 */
export default function useRecordView(options = {}) {
  const {
    modalEdit = false,
    onSaved,
    getBelowFields,
    getFooterExtra,
    canModalEdit,
  } = options
  const { t } = useTranslation('common')
  const location = useLocation()
  const [viewing, setViewing] = useState(null)
  const [editing, setEditing] = useState(null)
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

  const updateViewingRecord = useCallback((record) => {
    setViewing((prev) => (prev ? { ...prev, record } : prev))
  }, [])

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

  const ctx = viewing
    ? { pkg: viewing.pkg, resource: viewing.resource, record: viewing.record }
    : null
  const useModalEdit =
    modalEdit &&
    ctx &&
    (typeof canModalEdit === 'function' ? canModalEdit(ctx) : true)

  const viewModal = (
    <>
      <RecordViewModal
        open={!!viewing}
        onClose={closeView}
        package={viewing?.pkg}
        resource={viewing?.resource}
        recordId={viewing?.record?.id}
        seed={viewing?.record}
        onEdit={
          useModalEdit
            ? (rec) => {
                setEditing({
                  pkg: viewing.pkg,
                  resource: viewing.resource,
                  record: rec || viewing.record,
                })
              }
            : undefined
        }
        editPath={
          useModalEdit
            ? undefined
            : viewing?.record?.id != null
              ? withReturnTo(
                  `/domains/${viewing.pkg}/${viewing.resource}/${viewing.record.id}`,
                  originPath,
                )
              : undefined
        }
        belowFields={ctx && getBelowFields ? getBelowFields(ctx) : null}
        footerExtra={ctx && getFooterExtra ? getFooterExtra(ctx) : null}
      />
      {modalEdit && (
        <RecordEditModal
          open={!!editing}
          onClose={() => setEditing(null)}
          package={editing?.pkg}
          resource={editing?.resource}
          recordId={editing?.record?.id}
          seed={editing?.record}
          onSaved={() => {
            if (typeof onSaved === 'function') onSaved()
          }}
        />
      )}
    </>
  )

  return {
    openView,
    closeView,
    updateViewingRecord,
    viewModal,
    ViewButton,
    viewing,
    setViewing,
  }
}
