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

import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { GitBranch, X } from 'lucide-react'
import { capabilityApi } from '../api/client'
import ProcessDiagram from './ProcessDiagram'
import { localizeProcess, processButtonLabel } from '../utils/localizeProcess'

/**
 * Desk drawer: load SYSTEM process by processCode and show mermaid + steps (D7).
 */
export default function ProcessHowItWorks({ processCode, label }) {
  const { t, i18n } = useTranslation(['common', 'desks', 'processes'])
  const [open, setOpen] = useState(false)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const triggerRef = useRef(null)

  const closeOverlay = () => {
    setOpen(false)
    // WP-1: Esc/close returns focus to “How it works”
    queueMicrotask(() => triggerRef.current?.focus?.())
  }

  const buttonLabel =
    label ||
    processButtonLabel(
      (key, opts) => t(key, { ns: 'processes', ...opts }),
      processCode,
      t('process.howItWorks', { defaultValue: 'How this works' }),
    )

  useEffect(() => {
    if (!open || !processCode) return
    let cancelled = false
    setLoading(true)
    setError(null)
    capabilityApi.processes
      .byCode(processCode)
      .then((d) => {
        if (!cancelled) setData(d)
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e.message || String(e))
          setData(null)
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open, processCode])

  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        closeOverlay()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  const localized = useMemo(() => {
    if (!data) return null
    return localizeProcess(
      data,
      (key, opts) => t(key, { ns: 'processes', ...opts }),
      processCode,
    )
  }, [data, processCode, t, i18n.language])

  if (!processCode) return null

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className="btn btn-ghost"
        onClick={() => setOpen(true)}
        title={t('process.howItWorks', { defaultValue: 'How this works' })}
      >
        <GitBranch size={16} />
        {buttonLabel}
      </button>

      {open && (
        <div className="process-how-overlay" role="dialog" aria-modal="true">
          <div className="process-how-panel card">
            <div className="process-how-header">
              <h2 style={{ margin: 0, fontSize: '1.1rem' }}>
                {localized?.name || processCode}
              </h2>
              <button type="button" className="btn btn-ghost" onClick={closeOverlay}>
                <X size={16} />
              </button>
            </div>
            <p className="muted" style={{ marginTop: 4, fontSize: '0.85rem' }}>
              <span className="badge badge-muted">{localized?.processKind || data?.processKind || 'SYSTEM'}</span>{' '}
              <span className="badge">{localized?.processStatus || data?.processStatus || '—'}</span>{' '}
              <code className="mono">{processCode}</code>
            </p>
            {loading && <p className="muted">{t('actions.loading')}</p>}
            {error && (
              <div className="banner-error">
                {error}
                <p className="muted" style={{ marginTop: 8 }}>
                  {t('ui.seedHint', { ns: 'processes' })}
                </p>
              </div>
            )}
            {localized && (
              <>
                {localized.description && (
                  <p style={{ fontSize: '0.9rem' }}>{localized.description}</p>
                )}
                <ProcessDiagram
                  processCode={processCode}
                  steps={localized.steps || []}
                  mermaid={data?.mermaid || ''}
                />
                <div className="toolbar" style={{ marginTop: 12, marginBottom: 0 }}>
                  {(localized.id || data?.id) && (
                    <Link
                      className="btn"
                      to={`/capability/processes?code=${encodeURIComponent(processCode)}`}
                      onClick={closeOverlay}
                    >
                      {t('ui.openBrowser', { ns: 'processes' })}
                    </Link>
                  )}
                  <button type="button" className="btn btn-primary" onClick={closeOverlay}>
                    {t('actions.close', { defaultValue: 'Close' })}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </>
  )
}
