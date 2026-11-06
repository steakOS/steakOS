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

  const openEdit = useCallback(
    (pkg, resource, record) => {
      if (!modalEdit) return
      if (!record?.id && record?.id !== 0) return
      const allowed =
        typeof canModalEdit === 'function' ? canModalEdit({ pkg, resource, record }) : true
      if (!allowed) return
      setEditing({ pkg, resource, record })
    },
    [modalEdit, canModalEdit],
  )

  const closeView = useCallback(() => setViewing(null), [])

  /** Keep view + edit seeds in sync after process steps (send / confirm / …). */
  const updateViewingRecord = useCallback((record) => {
    if (!record) return
    const id = record.id
    setViewing((prev) =>
      prev && String(prev.record?.id) === String(id)
        ? { ...prev, record: { ...prev.record, ...record } }
        : prev,
    )
    setEditing((prev) =>
      prev && String(prev.record?.id) === String(id)
        ? { ...prev, record: { ...prev.record, ...record } }
        : prev,
    )
  }, [])

  const ViewButton = useCallback(
    function ViewButton({
      pkg,
      resource,
      record,
      className = 'btn btn-ghost btn-danger',
      size = 14,
    }) {
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

  const viewCtx = viewing
    ? { pkg: viewing.pkg, resource: viewing.resource, record: viewing.record }
    : null
  const editCtx = editing
    ? { pkg: editing.pkg, resource: editing.resource, record: editing.record }
    : null
  const useModalEdit =
    modalEdit &&
    viewCtx &&
    (typeof canModalEdit === 'function' ? canModalEdit(viewCtx) : true)
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
          // modalEdit desks: Edit only via modal when canModalEdit; otherwise hide Edit
          // (e.g. issued PO). Non-modal desks keep the domain-page Edit link.
          useModalEdit || modalEdit
            ? undefined
            : viewing?.record?.id != null
              ? withReturnTo(
                  `/domains/${viewing.pkg}/${viewing.resource}/${viewing.record.id}`,
                  originPath,
                )
              : undefined
        }
        belowFields={viewCtx && getBelowFields ? getBelowFields(viewCtx) : null}
        footerExtra={viewCtx && getFooterExtra ? getFooterExtra(viewCtx) : null}
      />
      {modalEdit && (
        <RecordEditModal
          // Stay open after process steps change status (confirm/send); canModalEdit
          // only gates openEdit, not an already-open session.
          open={!!editing}
          onClose={() => setEditing(null)}
          package={editing?.pkg}
          resource={editing?.resource}
          recordId={editing?.record?.id}
          seed={editing?.record}
          // Built-in ContentRail for companion hosts. Do not pass view getBelowFields
          // (CRM activities, read-only line tables, etc.) — those stay on the view modal.
          showContentRail
          // Same process steps as the view modal (send / confirm / bill / …).
          footerExtra={editCtx && getFooterExtra ? getFooterExtra(editCtx) : null}
          onSaved={(updated) => {
            if (updated) {
              updateViewingRecord(updated)
            }
            if (typeof onSaved === 'function') onSaved(updated)
          }}
        />
      )}
    </>
  )
  return {
    openView,
    openEdit,
    closeView,
    updateViewingRecord,
    viewModal,
    ViewButton,
    viewing,
    setViewing,
  }
}
