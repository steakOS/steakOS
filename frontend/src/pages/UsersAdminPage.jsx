// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { authApi, meApi } from '../api/client'
import { RULES_HINT, validatePassword } from '../util/passwordPolicy'

/**
 * AUTH04 + PDR-025 — CEO/Admin manage users via role profiles.
 */
export default function UsersAdminPage() {
  const { t } = useTranslation('common')
  const [me, setMe] = useState(null)
  const [items, setItems] = useState([])
  const [profiles, setProfiles] = useState([])
  const [err, setErr] = useState(null)
  const [info, setInfo] = useState(null)
  const [form, setForm] = useState({
    username: '',
    password: '',
    displayName: '',
    roleProfileId: 'user',
  })

  const load = useCallback(async () => {
    setErr(null)
    try {
      const m = await meApi.get()
      setMe(m)
      if (!m.isAdmin && !m.isCeo && !m.permissions?.system?.users) {
        setErr(t('pages.users.adminRequired', { defaultValue: 'Admin or CEO role required' }))
        return
      }
      const [r, p] = await Promise.all([authApi.usersList(), authApi.roleProfiles()])
      setItems(r.items || [])
      const list = p.items || []
      // CEO cannot assign admin profile
      const trulyAdmin = m.roleProfileId === 'admin' || (m.isAdmin && !m.isCeo)
      setProfiles(trulyAdmin ? list : list.filter((x) => x.profileId !== 'admin'))
    } catch (e) {
      setErr(e.message)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const create = async (e) => {
    e.preventDefault()
    setInfo(null)
    setErr(null)
    try {
      const pwErr = validatePassword(form.password)
      if (pwErr) {
        setErr(pwErr)
        return
      }
      await authApi.usersCreate({
        username: form.username.trim().toLowerCase(),
        password: form.password,
        displayName: form.displayName || null,
        roleProfileId: form.roleProfileId,
      })
      setInfo(t('pages.users.created', { username: form.username, defaultValue: 'Created {{username}}' }))
      setForm({ username: '', password: '', displayName: '', roleProfileId: 'user' })
      load()
    } catch (ex) {
      setErr(ex.message)
    }
  }

  const toggle = async (username, enabled) => {
    try {
      await authApi.usersUpdate({ username, enabled: !enabled })
      load()
    } catch (e) {
      setErr(e.message)
    }
  }

  const clearTotp = async (username) => {
    if (
      !window.confirm(
        t('pages.users.confirmClearTotp', {
          username,
          defaultValue: 'Clear authenticator for {{username}}?',
        }),
      )
    ) {
      return
    }
    try {
      await authApi.totpClear(username)
      setInfo(
        t('pages.users.totpCleared', {
          username,
          defaultValue: 'Authenticator cleared for {{username}}',
        }),
      )
      load()
    } catch (e) {
      setErr(e.message)
    }
  }

  const changeProfile = async (username, roleProfileId) => {
    try {
      await authApi.usersUpdate({ username, roleProfileId })
      setInfo(
        t('pages.users.roleUpdated', {
          username,
          defaultValue: 'Updated role for {{username}}',
        }),
      )
      load()
    } catch (e) {
      setErr(e.message)
    }
  }

  if (err && !me) {
    return (
      <div className="page">
        <p style={{ color: 'var(--danger)' }}>{err}</p>
      </div>
    )
  }

  return (
    <div className="page">
      <h1>{t('pages.users.title', { defaultValue: 'Users' })}</h1>
      <p className="muted">
        {t('pages.users.subtitle', {
          defaultValue:
            'Invite and manage a small team (about 2–5 people). Start with a role profile (Office, Ops, Finance…). Only open extra desks if they need them.',
        })}
      </p>
      <p className="muted" style={{ fontSize: 13 }}>
        <Link to="/roles">{t('pages.users.manageRoles', { defaultValue: 'Manage role defaults' })}</Link>
        {' · '}
        <Link to="/help">{t('pages.users.helpCenter', { defaultValue: 'Help center' })}</Link>
        {' · '}
        {t('pages.users.helpHintBefore', { defaultValue: 'Press' })} <kbd>?</kbd>{' '}
        {t('pages.users.helpHintAfter', { defaultValue: 'for invite & safety help' })}
      </p>
      <div
        className="card"
        style={{
          padding: '0.75rem 1rem',
          marginBottom: '1rem',
          borderLeft: '3px solid var(--accent, #3b82f6)',
          fontSize: 14,
        }}
      >
        <strong>{t('pages.users.tip', { defaultValue: 'Tip:' })}</strong>{' '}
        {t('pages.users.tipBody', {
          defaultValue:
            'Keep Admin and CEO as separate logins. Prefer Disable over delete. CEO cannot assign the Admin profile. See Help: Invite a teammate and Safety: Admin, CEO & disable.',
        })}
      </div>
      {err && <p style={{ color: 'var(--danger)' }}>{err}</p>}
      {info && <p className="muted">{info}</p>}

      <section className="card" style={{ padding: '1rem', marginBottom: '1rem' }}>
        <h2 className="section-label" style={{ marginTop: 0 }}>
          {t('pages.users.addUser', { defaultValue: 'Add user' })}
        </h2>
        <form className="form-grid" onSubmit={create}>
          <label>
            {t('pages.users.username', { defaultValue: 'Username' })}
            <input
              required
              value={form.username}
              onChange={(e) => setForm((f) => ({ ...f, username: e.target.value }))}
            />
          </label>
          <label>
            {t('pages.users.tempPassword', { defaultValue: 'Temporary password' })}
            <input
              required
              type="password"
              value={form.password}
              onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
              placeholder={RULES_HINT}
            />
          </label>
          <p className="muted" style={{ fontSize: 12, margin: 0 }}>
            {RULES_HINT}
          </p>
          <label>
            {t('pages.users.displayName', { defaultValue: 'Display name' })}
            <input
              value={form.displayName}
              onChange={(e) => setForm((f) => ({ ...f, displayName: e.target.value }))}
            />
          </label>
          <label>
            {t('pages.users.roleProfile', { defaultValue: 'Role profile' })}
            <select
              value={form.roleProfileId}
              onChange={(e) => setForm((f) => ({ ...f, roleProfileId: e.target.value }))}
            >
              {profiles.map((p) => (
                <option key={p.profileId} value={p.profileId}>
                  {p.label}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className="btn btn-primary">
            {t('pages.users.create', { defaultValue: 'Create' })}
          </button>
        </form>
      </section>

      <section className="card" style={{ padding: '1rem' }}>
        <table className="data">
          <thead>
            <tr>
              <th>{t('pages.users.colUser', { defaultValue: 'User' })}</th>
              <th>{t('pages.users.colProfile', { defaultValue: 'Profile' })}</th>
              <th>{t('pages.users.col2fa', { defaultValue: '2FA' })}</th>
              <th>{t('pages.users.colExtension', { defaultValue: 'Extension' })}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {items.map((u) => (
              <tr key={u.username}>
                <td>
                  <strong>{u.username}</strong>
                  {u.displayName ? <span className="muted"> · {u.displayName}</span> : null}
                  {!u.enabled && (
                    <span className="muted">
                      {' '}
                      {t('pages.users.disabled', { defaultValue: '(disabled)' })}
                    </span>
                  )}
                </td>
                <td>
                  <select
                    value={u.roleProfileId || 'user'}
                    onChange={(e) => changeProfile(u.username, e.target.value)}
                    disabled={u.username === me?.username && u.roleProfileId === 'admin'}
                  >
                    {profiles.map((p) => (
                      <option key={p.profileId} value={p.profileId}>
                        {p.label}
                      </option>
                    ))}
                    {u.roleProfileId &&
                      !profiles.some((p) => p.profileId === u.roleProfileId) && (
                        <option value={u.roleProfileId}>{u.roleProfileId}</option>
                      )}
                  </select>
                </td>
                <td>
                  {u.totpEnabled
                    ? t('pages.users.on', { defaultValue: 'On' })
                    : t('pages.users.off', { defaultValue: 'Off' })}
                </td>
                <td>{u.hasNpub ? t('pages.users.linked', { defaultValue: 'Linked' }) : '—'}</td>
                <td style={{ display: 'flex', gap: 8 }}>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    onClick={() => toggle(u.username, u.enabled)}
                  >
                    {u.enabled
                      ? t('pages.users.disable', { defaultValue: 'Disable' })
                      : t('pages.users.enable', { defaultValue: 'Enable' })}
                  </button>
                  {u.totpEnabled && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      onClick={() => clearTotp(u.username)}
                    >
                      {t('pages.users.reset2fa', { defaultValue: 'Reset 2FA' })}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  )
}
