// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { authApi, meApi } from '../api/client'

/**
 * AUTH01 enrol + NIP-07 link + admin TOTP bypass (testing).
 */
export default function SecuritySettingsPage() {
  const { t } = useTranslation(['common'])
  const [me, setMe] = useState(null)
  const [enrol, setEnrol] = useState(null)
  const [code, setCode] = useState('')
  const [recovery, setRecovery] = useState(null)
  const [msg, setMsg] = useState(null)
  const [err, setErr] = useState(null)
  const [busy, setBusy] = useState(false)
  const [bypass, setBypass] = useState(false)
  const [bypassWarning, setBypassWarning] = useState('')

  const refresh = () => meApi.get().then(setMe).catch((e) => setErr(e.message))

  const refreshBypass = () => {
    if (!me?.isAdmin) return
    authApi
      .adminTotpBypassGet()
      .then((r) => {
        setBypass(!!r.enabled)
        setBypassWarning(r.warning || '')
      })
      .catch(() => {
        /* non-admin or API not yet rebuilt */
      })
  }

  useEffect(() => {
    refresh()
  }, [])

  useEffect(() => {
    if (me?.isAdmin) refreshBypass()
  }, [me?.isAdmin])

  const beginTotp = async () => {
    setBusy(true)
    setErr(null)
    setMsg(null)
    try {
      const r = await authApi.totpBegin()
      setEnrol(r)
      setRecovery(null)
    } catch (e) {
      setErr(e.message)
    } finally {
      setBusy(false)
    }
  }

  const confirmTotp = async (e) => {
    e.preventDefault()
    setBusy(true)
    setErr(null)
    try {
      const r = await authApi.totpConfirm(code.trim())
      setRecovery(r.recoveryCodes || [])
      setEnrol(null)
      setCode('')
      setMsg(t('pages.security.totpEnabledMsg'))
      refresh()
    } catch (ex) {
      setErr(ex.message)
    } finally {
      setBusy(false)
    }
  }

  const toggleBypass = async (next) => {
    setBusy(true)
    setErr(null)
    setMsg(null)
    try {
      const r = await authApi.adminTotpBypassPut(next)
      setBypass(!!r.enabled)
      setMsg(
        r.enabled
          ? t('pages.security.bypassOnMsg')
          : t('pages.security.bypassOffMsg'),
      )
      refresh()
    } catch (e) {
      setErr(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const linkNostr = async () => {
    setBusy(true)
    setErr(null)
    setMsg(null)
    try {
      if (!window.nostr?.signEvent) {
        setErr(t('pages.security.noExtension'))
        return
      }
      const { challenge } = await authApi.nostrChallenge()
      const event = {
        kind: 22242,
        created_at: Math.floor(Date.now() / 1000),
        tags: [['challenge', challenge]],
        content: 'steakOS link',
      }
      const signed = await window.nostr.signEvent(event)
      await authApi.nostrLink({ challenge, event: signed })
      setMsg(t('pages.security.extensionLinked'))
      refresh()
    } catch (e) {
      setErr(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>{t('pages.security.title')}</h1>
      <p className="muted">{t('pages.security.subtitle')}</p>

      {err && <p style={{ color: 'var(--danger,#b00020)' }}>{err}</p>}
      {msg && <p className="muted">{msg}</p>}

      <section className="card" style={{ marginTop: '1rem', padding: '1rem' }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {t('pages.security.totpTitle')}
        </h2>
        <p>
          {t('pages.security.status')}:{' '}
          <strong>
            {me?.totpEnabled
              ? t('pages.security.enabled')
              : t('pages.security.notEnabled')}
          </strong>
        </p>
        {!me?.totpEnabled && !enrol && (
          <button type="button" className="btn btn-primary" disabled={busy} onClick={beginTotp}>
            {t('pages.security.setupTotp')}
          </button>
        )}
        {enrol && (
          <form onSubmit={confirmTotp} className="form-grid">
            <p>{t('pages.security.totpStep1')}</p>
            <p>{t('pages.security.totpStep2')}</p>
            {enrol.qrDataUrl && (
              <img src={enrol.qrDataUrl} alt="Authenticator QR" width={200} height={200} />
            )}
            <p className="mono" style={{ wordBreak: 'break-all' }}>
              {enrol.secret}
            </p>
            <label>
              {t('pages.security.totpStep3')}
              <input value={code} onChange={(e) => setCode(e.target.value)} required />
            </label>
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {t('actions.confirm')}
            </button>
          </form>
        )}
        {recovery && (
          <div>
            <p>
              <strong>{t('pages.security.recoveryCodes')}</strong>
            </p>
            <ul className="mono">
              {recovery.map((c) => (
                <li key={c}>{c}</li>
              ))}
            </ul>
          </div>
        )}
      </section>

      {me?.isAdmin && (
        <section className="card" style={{ marginTop: '1rem', padding: '1rem' }}>
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('pages.security.bypassTitle')}
          </h2>
          <p className="muted">{t('pages.security.bypassBlurb')}</p>
          {(bypassWarning || bypass) && (
            <p className="banner-info" role="status" style={{ marginBottom: 12 }}>
              {bypassWarning || t('pages.security.bypassWarning')}
            </p>
          )}
          <label className="setup-check" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <input
              type="checkbox"
              checked={bypass}
              disabled={busy}
              onChange={(e) => toggleBypass(e.target.checked)}
            />
            {t('pages.security.bypassCheckbox')}
          </label>
        </section>
      )}

      <section className="card" style={{ marginTop: '1rem', padding: '1rem' }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {t('pages.security.extensionTitle')}
        </h2>
        <p className="muted">{t('pages.security.extensionBlurb')}</p>
        <p>
          {t('pages.security.linked')}:{' '}
          <strong>{me?.hasNpub ? t('pages.security.yes') : t('pages.security.no')}</strong>
        </p>
        <button type="button" className="btn btn-ghost" disabled={busy} onClick={linkNostr}>
          {me?.hasNpub ? t('pages.security.relinkExtension') : t('pages.security.linkExtension')}
        </button>
      </section>
    </div>
  )
}
