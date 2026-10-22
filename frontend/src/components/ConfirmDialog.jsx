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

import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { AlertTriangle, X } from 'lucide-react'

/**
 * Reusable confirm / destructive confirm modal.
 * Esc and backdrop click cancel; primary action receives initial focus.
 */
export default function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  cancelLabel,
  tone = 'default',
  onConfirm,
  onCancel,
}) {
  const { t } = useTranslation('common')
  const primaryRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => {
      if (e.key === 'Escape') onCancel?.()
    }
    window.addEventListener('keydown', onKey)
    const id = window.requestAnimationFrame(() => {
      primaryRef.current?.focus?.()
    })
    return () => {
      window.removeEventListener('keydown', onKey)
      window.cancelAnimationFrame(id)
    }
  }, [open, onCancel])

  if (!open) return null

  const heading =
    title ||
    t('confirm.title', { defaultValue: t('actions.confirm', { defaultValue: 'Confirm' }) })
  const confirmText =
    confirmLabel || t('actions.confirm', { defaultValue: 'Confirm' })
  const cancelText = cancelLabel || t('actions.cancel', { defaultValue: 'Cancel' })
  const danger = tone === 'danger'

  return (
    <div className="error-dialog-backdrop confirm-dialog-backdrop" role="presentation" onClick={onCancel}>
      <div
        className={`error-dialog confirm-dialog${danger ? ' confirm-dialog-danger' : ''}`}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="error-dialog-header">
          <h2 id="confirm-dialog-title" className="error-dialog-title">
            {danger ? <AlertTriangle size={20} aria-hidden /> : null} {heading}
          </h2>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={onCancel}
            aria-label={t('actions.close', { defaultValue: 'Close' })}
          >
            <X size={18} />
          </button>
        </header>

        {body != null && body !== '' && (
          <div className="error-dialog-body">
            {typeof body === 'string' ? <p className="error-dialog-message">{body}</p> : body}
          </div>
        )}

        <footer className="error-dialog-footer">
          <button type="button" className="btn" onClick={onCancel}>
            {cancelText}
          </button>
          <button
            ref={primaryRef}
            type="button"
            className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`}
            onClick={onConfirm}
          >
            {confirmText}
          </button>
        </footer>
      </div>
    </div>
  )
}
