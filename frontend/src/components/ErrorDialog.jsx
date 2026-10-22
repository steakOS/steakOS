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

import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { X, AlertTriangle, Copy } from 'lucide-react'
import { resolveErrorRecovery } from '../config/errorRecovery'

/**
 * Modal for structured API errors (code, message, detail, optional stack).
 * Optional `recovery` prop overrides catalog lookup via resolveErrorRecovery(error).
 */
export default function ErrorDialog({ error, onClose, title = 'Request failed', recovery }) {
  const { t } = useTranslation('common')

  useEffect(() => {
    if (!error) return undefined
    const onKey = (e) => {
      if (e.key === 'Escape') onClose?.()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [error, onClose])

  if (!error) return null

  const code = error.code || error.errorCode || error.status || 'ERROR'
  const message = error.message || error.error || String(error)
  const detail = error.detail || null
  const stack = error.stackTrace || error.stack || null
  const status = error.status || null

  const resolved = recovery !== undefined ? recovery : resolveErrorRecovery(error)
  const recoveryTitle = resolved?.titleKey
    ? t(resolved.titleKey, { defaultValue: title })
    : title
  const recoveryBody = resolved?.bodyKey
    ? t(resolved.bodyKey, { defaultValue: '' })
    : ''
  const recoveryActions = Array.isArray(resolved?.actions) ? resolved.actions : []

  const copyAll = async () => {
    const text = [
      `code: ${code}`,
      status != null ? `http: ${status}` : null,
      `message: ${message}`,
      detail ? `detail: ${detail}` : null,
      stack ? `\n${stack}` : null,
    ]
      .filter(Boolean)
      .join('\n')
    try {
      await navigator.clipboard.writeText(text)
    } catch {
      /* ignore */
    }
  }

  return (
    <div className="error-dialog-backdrop" role="presentation" onClick={onClose}>
      <div
        className="error-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="error-dialog-title"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="error-dialog-header">
          <h2 id="error-dialog-title" className="error-dialog-title">
            <AlertTriangle size={20} /> {recoveryTitle}
          </h2>
          <button type="button" className="btn btn-ghost" onClick={onClose} aria-label="Close">
            <X size={18} />
          </button>
        </header>

        <div className="error-dialog-body">
          <div className="error-dialog-meta">
            <span className="error-code mono">{code}</span>
            {status != null && <span className="muted mono">HTTP {status}</span>}
          </div>
          {recoveryBody ? <p className="error-dialog-message">{recoveryBody}</p> : null}
          <p className={`error-dialog-message${recoveryBody ? ' muted' : ''}`}>{message}</p>
          {detail && (
            <pre className="error-dialog-detail mono">{detail}</pre>
          )}
          {stack && (
            <details className="error-dialog-stack">
              <summary>Stack trace</summary>
              <pre className="mono">{stack}</pre>
            </details>
          )}
          {recoveryActions.length > 0 && (
            <div className="error-dialog-recovery">
              {recoveryActions.map((action, idx) => {
                const label = t(action.labelKey, { defaultValue: action.labelKey })
                if (action.href) {
                  const external = String(action.href).startsWith('/login')
                  if (external) {
                    return (
                      <a
                        key={`${action.labelKey}-${idx}`}
                        className="btn btn-secondary"
                        href={action.href}
                      >
                        {label}
                      </a>
                    )
                  }
                  return (
                    <Link
                      key={`${action.labelKey}-${idx}`}
                      className="btn btn-secondary"
                      to={action.href}
                      onClick={onClose}
                    >
                      {label}
                    </Link>
                  )
                }
                return (
                  <button
                    key={`${action.labelKey}-${idx}`}
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => {
                      if (action.onClick) action.onClick()
                      else onClose?.()
                    }}
                  >
                    {label}
                  </button>
                )
              })}
            </div>
          )}
        </div>

        <footer className="error-dialog-footer">
          <button type="button" className="btn" onClick={copyAll}>
            <Copy size={14} /> Copy details
          </button>
          <button type="button" className="btn btn-primary" onClick={onClose}>
            Close
          </button>
        </footer>
      </div>
    </div>
  )
}
