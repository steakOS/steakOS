// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { meApi } from '../api/client'
import { logoutHref } from '../i18n'

/**
 * PDR-026 — pick active company when the user has 2+ memberships.
 */
export default function SelectCompanyPage() {
  const { t } = useTranslation(['common'])
  const navigate = useNavigate()
  const [companies, setCompanies] = useState([])
  const [home, setHome] = useState('/dashboard')
  const [err, setErr] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    meApi
      .get()
      .then((me) => {
        if (cancelled) return
        const list = me.companies || []
        setCompanies(list)
        // Always land on dashboard after login / company pick (not Settings).
        const homePath = '/dashboard'
        setHome(homePath)
        if (!me.needCompanyPick && me.activeCompanyId) {
          navigate(homePath, { replace: true })
        }
        if (list.length === 1) {
          meApi
            .selectCompany(list[0].id)
            .then(() => navigate(homePath, { replace: true }))
            .catch((e) => setErr(e.message))
        }
      })
      .catch((e) => {
        if (!cancelled) setErr(e.message || String(e))
      })
    return () => {
      cancelled = true
    }
  }, [navigate])

  const choose = async (companyId) => {
    setBusy(true)
    setErr(null)
    try {
      await meApi.selectCompany(companyId)
      navigate(home, { replace: true })
    } catch (e) {
      setErr(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const badgeFor = (c) => {
    if (c.demo || c.kind === 'DEMO') {
      if (c.code === 'heard-cook-architects') return `${t('pages.selectCompany.demo')} · Architecture`
      if (c.code === 'orange-steak-house') return `${t('pages.selectCompany.demo')} · Restaurant`
      return t('pages.selectCompany.demo')
    }
    return t('pages.selectCompany.live')
  }

  return (
    <div className="setup-loading-screen" style={{ maxWidth: 520, margin: '3rem auto', padding: 24 }}>
      <div className="card" style={{ padding: '1.5rem' }}>
        <h1 style={{ marginTop: 0 }}>{t('pages.selectCompany.title')}</h1>
        <p className="muted">{t('pages.selectCompany.subtitle')}</p>
        {err && <p style={{ color: 'var(--danger, #b00020)' }}>{err}</p>}
        <ul className="setup-family-list" style={{ listStyle: 'none', padding: 0 }}>
          {companies.map((c) => (
            <li key={c.id} style={{ marginBottom: 10 }}>
              <button
                type="button"
                className="btn btn-primary"
                style={{ width: '100%', justifyContent: 'space-between' }}
                disabled={busy}
                onClick={() => choose(c.id)}
              >
                <span>{c.name}</span>
                <span className="muted" style={{ fontSize: 12 }}>
                  {badgeFor(c)}
                </span>
              </button>
            </li>
          ))}
        </ul>
        {!companies.length && !err && (
          <p className="muted">{t('pages.selectCompany.loading')}</p>
        )}
        <p className="muted" style={{ fontSize: 13, marginTop: '1rem' }}>
          <a href={logoutHref()}>{t('pages.selectCompany.signOut')}</a>
        </p>
      </div>
    </div>
  )
}
