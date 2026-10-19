// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { authApi } from '../api/client'
import AuthAppGuide from '../components/AuthAppGuide'
import { logoutHref } from '../i18n'

/**
 * AUTH01 — after password login, enter authenticator (or recovery) code.
 */
export default function TotpGatePage() {
  const { t } = useTranslation(['common'])
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [showHelp, setShowHelp] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await authApi.totpVerify(code.trim())
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setError(err.message || t('actions.error'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="setup-loading-screen" style={{ maxWidth: 520, margin: '3rem auto', padding: 24 }}>
      <div className="card" style={{ padding: '1.5rem' }}>
        <h1 style={{ marginTop: 0 }}>{t('pages.totpGate.title')}</h1>
        <p className="muted">{t('pages.totpGate.subtitle')}</p>
        <form onSubmit={submit} className="form-grid">
          <label>
            {t('pages.totpGate.codeLabel')}
            <input
              autoFocus
              inputMode="numeric"
              autoComplete="one-time-code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="123456"
            />
          </label>
          {error && <p style={{ color: 'var(--danger, #b00020)' }}>{error}</p>}
          <button type="submit" className="btn btn-primary" disabled={busy || !code.trim()}>
            {busy ? t('actions.loading') : t('pages.totpGate.continue')}
          </button>
        </form>

        <p style={{ marginTop: '1rem', marginBottom: 0 }}>
          <button
            type="button"
            className="btn btn-ghost"
            style={{ paddingLeft: 0 }}
            onClick={() => setShowHelp((v) => !v)}
          >
            {t('pages.totpGate.help')}
          </button>
        </p>
        {showHelp && (
          <AuthAppGuide
            compact
            title="If you never set up an app, or lost your phone"
          />
        )}
        {showHelp && (
          <p className="muted" style={{ fontSize: '0.85rem' }}>
            If you lost the phone and still have a <strong>recovery code</strong> from setup, enter
            that code above instead of the 6-digit app code. If you have neither, an existing Admin
            who can still sign in must reset authenticator under System → Security, or restore from
            backup — steakOS cannot invent a new code for you.
          </p>
        )}

        <p className="muted" style={{ fontSize: '0.85rem', marginTop: '1rem' }}>
          <a
            href={logoutHref()}
            onClick={() => {
              try {
                sessionStorage.clear()
              } catch {
                /* ignore */
              }
            }}
          >
            {t('pages.totpGate.signOut')}
          </a>
        </p>
      </div>
    </div>
  )
}
