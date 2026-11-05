// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { authApi, meApi } from '../api/client'

const DESK_KEYS = [
  'core',
  'sales',
  'scm',
  'delivery',
  'aftersales',
  'finance',
  'human',
  'digital',
  'capability',
  'agents',
  'strategy',
  'development',
  'asset',
]

const SYSTEM_TOGGLE_KEYS = [
  'users',
  'securityOthers',
  'import',
  'catalogs',
  'agents',
  'setup',
  'settle',
]

const VERBS = ['see', 'create', 'change', 'delete']

const VERB_I18N = {
  see: 'verbSee',
  create: 'verbCreate',
  change: 'verbChange',
  delete: 'verbDelete',
}

const DESK_DEFAULTS = {
  core: 'My work',
  sales: 'Sales',
  scm: 'SCM / Procurement',
  delivery: 'Delivery',
  aftersales: 'After-sales',
  finance: 'Finance',
  human: 'HR',
  digital: 'Digital / IT',
  capability: 'Capability',
  agents: 'Agents',
  strategy: 'Strategy',
  development: 'Development',
  asset: 'Assets',
}

const SYSTEM_TOGGLE_DEFAULTS = {
  users: 'Users / invite',
  securityOthers: 'Reset others’ 2FA',
  import: 'Data & import',
  catalogs: 'Admin catalogs',
  agents: 'Agents hub / mint',
  setup: 'Setup / metal switch',
  settle: 'Settle money',
}

function emptyDesk() {
  return { see: false, create: false, change: false, delete: false, work: false }
}

function normalizeMatrix(matrix) {
  const m = matrix && typeof matrix === 'object' ? structuredClone(matrix) : { version: 1 }
  if (!m.desks) m.desks = {}
  if (!m.system) m.system = {}
  if (!m.specials) m.specials = {}
  DESK_KEYS.forEach((fam) => {
    if (!m.desks[fam]) m.desks[fam] = emptyDesk()
    const d = m.desks[fam]
    d.work = !!(d.create || d.change)
  })
  SYSTEM_TOGGLE_KEYS.forEach((key) => {
    if (m.system[key] == null) m.system[key] = false
  })
  if (m.system.securitySelf == null) m.system.securitySelf = true
  return m
}

/**
 * PDR-025 — System → Roles: simple desk toggles + full permission matrix.
 */
export default function RolesPage() {
  const { t } = useTranslation('common')
  const [me, setMe] = useState(null)
  const [profiles, setProfiles] = useState([])
  const [selectedId, setSelectedId] = useState('office')
  const [draft, setDraft] = useState(null)
  const [advanced, setAdvanced] = useState(false)
  const [err, setErr] = useState(null)
  const [info, setInfo] = useState(null)
  const [busy, setBusy] = useState(false)
  const [dirty, setDirty] = useState(false)

  const deskLabel = (key) =>
    t(`pages.roles.desksMap.${key}`, { defaultValue: DESK_DEFAULTS[key] || key })
  const systemToggleLabel = (key) =>
    t(`pages.roles.systemToggles.${key}`, {
      defaultValue: SYSTEM_TOGGLE_DEFAULTS[key] || key,
    })
  const verbLabel = (v) =>
    t(`pages.roles.${VERB_I18N[v]}`, { defaultValue: v })

  const trulyAdmin = useMemo(() => {
    if (!me) return false
    return me.roleProfileId === 'admin' || (me.isAdmin && !me.isCeo)
  }, [me])

  const load = useCallback(async () => {
    setErr(null)
    try {
      const m = await meApi.get()
      setMe(m)
      if (!m.permissions?.system?.users && !m.isAdmin && !m.isCeo) {
        setErr(
          t('pages.roles.adminRequired', {
            defaultValue: 'Admin or CEO required to manage roles',
          }),
        )
        return
      }
      const isAdminProfile =
        m.roleProfileId === 'admin' || (m.isAdmin && !m.isCeo)
      const r = await authApi.roleProfiles()
      const items = r.items || []
      // CEO cannot edit Admin profile — hide from list unless viewer is Admin profile
      const list = isAdminProfile ? items : items.filter((p) => p.profileId !== 'admin')
      setProfiles(list)
      const pick =
        list.find((p) => p.profileId === selectedId) ||
        list.find((p) => p.profileId === 'office') ||
        list[0]
      if (pick) {
        setSelectedId(pick.profileId)
        setDraft({
          label: pick.label,
          defaultHome: pick.defaultHome,
          matrix: normalizeMatrix(pick.matrix),
          matrixVersion: pick.matrixVersion,
          systemPreset: pick.systemPreset,
          userCount: pick.userCount ?? 0,
        })
        setDirty(false)
      }
    } catch (e) {
      setErr(e.message || String(e))
    }
  }, [selectedId, t])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- initial load only
  }, [])

  const selectProfile = async (profileId) => {
    if (
      dirty &&
      !window.confirm(
        t('pages.roles.discardUnsaved', {
          defaultValue: 'Discard unsaved changes?',
        }),
      )
    ) {
      return
    }
    setSelectedId(profileId)
    setInfo(null)
    setErr(null)
    try {
      const p = await authApi.roleProfile(profileId)
      setDraft({
        label: p.label,
        defaultHome: p.defaultHome,
        matrix: normalizeMatrix(p.matrix),
        matrixVersion: p.matrixVersion,
        systemPreset: p.systemPreset,
        userCount: profiles.find((x) => x.profileId === profileId)?.userCount ?? 0,
      })
      setDirty(false)
    } catch (e) {
      setErr(e.message)
    }
  }

  const patchDesk = (fam, field, value) => {
    setDraft((d) => {
      if (!d) return d
      const matrix = normalizeMatrix(d.matrix)
      const cell = { ...emptyDesk(), ...(matrix.desks[fam] || {}) }
      if (field === 'work') {
        cell.work = value
        cell.create = value
        cell.change = value
        if (value) cell.see = true
      } else if (field === 'see') {
        cell.see = value
        if (!value) {
          cell.work = false
          cell.create = false
          cell.change = false
          cell.delete = false
        }
      } else {
        cell[field] = value
        if (value) cell.see = true
        cell.work = !!(cell.create || cell.change)
      }
      matrix.desks[fam] = cell
      return { ...d, matrix }
    })
    setDirty(true)
  }

  const patchSystem = (key, value) => {
    setDraft((d) => {
      if (!d) return d
      const matrix = normalizeMatrix(d.matrix)
      matrix.system = { ...matrix.system, [key]: value }
      return { ...d, matrix }
    })
    setDirty(true)
  }

  const save = async () => {
    if (!draft) return
    if (selectedId === 'admin' && !trulyAdmin) {
      setErr(
        t('pages.roles.adminOnlyEdit', {
          defaultValue: 'Only Admin can edit the Admin profile',
        }),
      )
      return
    }
    setBusy(true)
    setErr(null)
    setInfo(null)
    try {
      const matrix = normalizeMatrix(draft.matrix)
      // sync settle special with system.settle
      matrix.specials = { ...matrix.specials, settle: !!matrix.system.settle }
      await authApi.roleProfileUpdate(selectedId, {
        label: draft.label,
        defaultHome: draft.defaultHome,
        matrix,
      })
      setInfo(
        t('pages.roles.saved', {
          label: draft.label,
          defaultValue: 'Saved «{{label}}»',
        }),
      )
      setDirty(false)
      const r = await authApi.roleProfiles()
      setProfiles(
        (r.items || []).filter((p) => trulyAdmin || p.profileId !== 'admin'),
      )
    } catch (e) {
      setErr(e.message)
    } finally {
      setBusy(false)
    }
  }

  const resetDefaults = async () => {
    if (!draft?.systemPreset) return
    if (
      !window.confirm(
        t('pages.roles.confirmResetDefaults', {
          label: draft.label,
          defaultValue: 'Reset «{{label}}» to steakOS defaults?',
        }),
      )
    ) {
      return
    }
    setBusy(true)
    setErr(null)
    try {
      const p = await authApi.roleProfileReset(selectedId)
      setDraft({
        label: p.label,
        defaultHome: p.defaultHome,
        matrix: normalizeMatrix(p.matrix),
        matrixVersion: p.matrixVersion,
        systemPreset: p.systemPreset,
        userCount: draft.userCount,
      })
      setDirty(false)
      setInfo(
        t('pages.roles.resetDefaults', {
          defaultValue: 'Reset to steakOS defaults',
        }),
      )
    } catch (e) {
      setErr(e.message)
    } finally {
      setBusy(false)
    }
  }

  const applyToUsers = async () => {
    const n = draft?.userCount ?? 0
    if (
      !window.confirm(
        t('pages.roles.confirmApply', {
          label: draft?.label,
          count: n,
          defaultValue:
            'Apply current Spring roles from «{{label}}» to all {{count}} user(s) on this profile? (R6)',
        }),
      )
    ) {
      return
    }
    setBusy(true)
    setErr(null)
    try {
      if (dirty) await save()
      const r = await authApi.roleProfileApply(selectedId)
      setInfo(
        t('pages.roles.applied', {
          count: r.updated ?? 0,
          defaultValue: 'Applied to {{count}} user(s)',
        }),
      )
      load()
    } catch (e) {
      setErr(e.message)
    } finally {
      setBusy(false)
    }
  }

  const readOnlyProfile = selectedId === 'admin' && !trulyAdmin

  if (err && !me) {
    return (
      <div className="page">
        <p style={{ color: 'var(--danger, #b00020)' }}>{err}</p>
      </div>
    )
  }

  return (
    <div className="page">
      <h1>{t('pages.roles.title', { defaultValue: 'Roles' })}</h1>
      <p className="muted">
        {t('pages.roles.subtitle', {
          defaultValue:
            'Role profiles set what people can see and do. Start with a preset; only change desks they need. Full detail is under Advanced.',
        })}
      </p>
      <p className="muted" style={{ fontSize: 13 }}>
        <Link to="/users">{t('pages.roles.usersLink', { defaultValue: 'Users' })}</Link>
        {' · '}
        <Link to="/help">{t('pages.roles.helpCenter', { defaultValue: 'Help center' })}</Link>
        {' · '}
        {t('pages.roles.helpHintBefore', { defaultValue: 'Press' })} <kbd>?</kbd>{' '}
        {t('pages.roles.helpHintAfter', {
          defaultValue: 'for Roles help (presets, matrix, safety)',
        })}
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
        <strong>{t('pages.roles.tip', { defaultValue: 'Tip:' })}</strong>{' '}
        {t('pages.roles.tipBodyBefore', {
          defaultValue: 'Start with a preset. Only add desks if they need them. Open',
        })}{' '}
        <Link to="/help">{t('pages.roles.tipHelp', { defaultValue: 'Help' })}</Link>{' '}
        {t('pages.roles.tipBodyAfter', {
          defaultValue:
            'articles Roles for a small team and Which role to pick before inventing custom grants.',
        })}
      </div>
      {err && <p style={{ color: 'var(--danger, #b00020)' }}>{err}</p>}
      {info && <p className="muted">{info}</p>}

      <div className="two-col finance-tx-grid">
        <section className="card" style={{ padding: '1rem' }}>
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('pages.roles.profiles', { defaultValue: 'Profiles' })}
          </h2>
          <ul className="setup-family-list" style={{ listStyle: 'none', padding: 0 }}>
            {profiles.map((p) => (
              <li key={p.profileId} style={{ marginBottom: 6 }}>
                <button
                  type="button"
                  className={`btn ${p.profileId === selectedId ? 'btn-primary' : 'btn-ghost'}`}
                  style={{ width: '100%', justifyContent: 'space-between' }}
                  onClick={() => selectProfile(p.profileId)}
                >
                  <span>
                    {t(`pages.roles.profileLabels.${p.profileId}`, {
                      defaultValue: p.label,
                    })}
                  </span>
                  <span className="muted" style={{ fontSize: 12 }}>
                    {t('pages.roles.userCount', {
                      count: p.userCount ?? 0,
                      defaultValue: '{{count}} user(s)',
                    })}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>

        <section className="card" style={{ padding: '1rem' }}>
          {!draft ? (
            <p className="muted">
              {t('pages.roles.selectProfile', { defaultValue: 'Select a profile' })}
            </p>
          ) : (
            <>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
                <h2 className="section-label" style={{ margin: 0, flex: 1 }}>
                  {draft.label}
                  {dirty
                    ? t('pages.roles.unsaved', { defaultValue: ' · unsaved' })
                    : ''}
                </h2>
                <label className="setup-check" style={{ margin: 0 }}>
                  <input
                    type="checkbox"
                    checked={advanced}
                    onChange={(e) => setAdvanced(e.target.checked)}
                  />
                  {t('pages.roles.advancedMatrix', { defaultValue: 'Advanced matrix' })}
                </label>
              </div>
              {readOnlyProfile && (
                <p className="muted">
                  {t('pages.roles.adminOnlyEdit', {
                    defaultValue: 'Only Admin can edit the Admin profile.',
                  })}
                </p>
              )}

              <label style={{ display: 'block', marginTop: 12 }}>
                {t('pages.roles.displayName', { defaultValue: 'Display name' })}
                <input
                  value={draft.label}
                  disabled={readOnlyProfile || busy}
                  onChange={(e) => {
                    setDraft((d) => ({ ...d, label: e.target.value }))
                    setDirty(true)
                  }}
                />
              </label>
              <label style={{ display: 'block', marginTop: 8 }}>
                {t('pages.roles.defaultHome', { defaultValue: 'Default home' })}
                <input
                  value={draft.defaultHome}
                  disabled={readOnlyProfile || busy}
                  onChange={(e) => {
                    setDraft((d) => ({ ...d, defaultHome: e.target.value }))
                    setDirty(true)
                  }}
                />
              </label>

              {!advanced && (
                <>
                  <h3 className="section-label">
                    {t('pages.roles.desks', { defaultValue: 'Desks' })}
                  </h3>
                  <p className="muted" style={{ fontSize: 13 }}>
                    {t('pages.roles.desksHint', {
                      defaultValue: 'See = appear in nav. Work = create and change records.',
                    })}
                  </p>
                  <table className="data">
                    <thead>
                      <tr>
                        <th>{t('pages.roles.colDesk', { defaultValue: 'Desk' })}</th>
                        <th>{t('pages.roles.colSee', { defaultValue: 'See' })}</th>
                        <th>{t('pages.roles.colWork', { defaultValue: 'Work' })}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {DESK_KEYS.map((fam) => {
                        const cell = draft.matrix.desks[fam] || emptyDesk()
                        return (
                          <tr key={fam}>
                            <td>{deskLabel(fam)}</td>
                            <td>
                              <input
                                type="checkbox"
                                checked={!!cell.see}
                                disabled={readOnlyProfile || busy || fam === 'core'}
                                onChange={(e) => patchDesk(fam, 'see', e.target.checked)}
                              />
                            </td>
                            <td>
                              <input
                                type="checkbox"
                                checked={!!(cell.create || cell.change || cell.work)}
                                disabled={readOnlyProfile || busy}
                                onChange={(e) => patchDesk(fam, 'work', e.target.checked)}
                              />
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>

                  <h3 className="section-label">
                    {t('pages.roles.specials', { defaultValue: 'Specials' })}
                  </h3>
                  <ul className="setup-family-list" style={{ listStyle: 'none', padding: 0 }}>
                    {SYSTEM_TOGGLE_KEYS.map((key) => (
                      <li key={key}>
                        <label className="setup-check">
                          <input
                            type="checkbox"
                            checked={!!draft.matrix.system[key]}
                            disabled={readOnlyProfile || busy}
                            onChange={(e) => patchSystem(key, e.target.checked)}
                          />
                          {systemToggleLabel(key)}
                        </label>
                      </li>
                    ))}
                  </ul>
                </>
              )}

              {advanced && (
                <>
                  <h3 className="section-label">
                    {t('pages.roles.permissionMatrix', { defaultValue: 'Permission matrix' })}
                  </h3>
                  <p className="muted" style={{ fontSize: 13 }}>
                    {t('pages.roles.matrixHint', {
                      defaultValue: 'See / Create / Change / Delete per desk. Deny by unchecking.',
                    })}
                  </p>
                  <div className="table-wrap">
                    <table className="data">
                      <thead>
                        <tr>
                          <th>{t('pages.roles.colDesk', { defaultValue: 'Desk' })}</th>
                          {VERBS.map((v) => (
                            <th key={v}>{verbLabel(v)}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {DESK_KEYS.map((fam) => {
                          const cell = draft.matrix.desks[fam] || emptyDesk()
                          return (
                            <tr key={fam}>
                              <td>{deskLabel(fam)}</td>
                              {VERBS.map((v) => (
                                <td key={v}>
                                  <input
                                    type="checkbox"
                                    checked={!!cell[v]}
                                    disabled={
                                      readOnlyProfile || busy || (fam === 'core' && v === 'see')
                                    }
                                    onChange={(e) => patchDesk(fam, v, e.target.checked)}
                                  />
                                </td>
                              ))}
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                  <h3 className="section-label">
                    {t('pages.roles.system', { defaultValue: 'System' })}
                  </h3>
                  <ul className="setup-family-list" style={{ listStyle: 'none', padding: 0 }}>
                    {SYSTEM_TOGGLE_KEYS.map((key) => (
                      <li key={key}>
                        <label className="setup-check">
                          <input
                            type="checkbox"
                            checked={!!draft.matrix.system[key]}
                            disabled={readOnlyProfile || busy}
                            onChange={(e) => patchSystem(key, e.target.checked)}
                          />
                          {systemToggleLabel(key)}
                        </label>
                      </li>
                    ))}
                  </ul>
                </>
              )}

              <div className="toolbar" style={{ marginTop: 16, flexWrap: 'wrap', gap: 8 }}>
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={busy || readOnlyProfile || !dirty}
                  onClick={save}
                >
                  {t('pages.roles.save', { defaultValue: 'Save' })}
                </button>
                {draft.systemPreset && (
                  <button
                    type="button"
                    className="btn btn-ghost"
                    disabled={busy || readOnlyProfile}
                    onClick={resetDefaults}
                  >
                    {t('pages.roles.resetDefaults', {
                      defaultValue: 'Reset to steakOS defaults',
                    })}
                  </button>
                )}
                <button
                  type="button"
                  className="btn btn-ghost"
                  disabled={busy || readOnlyProfile}
                  onClick={applyToUsers}
                >
                  {t('pages.roles.applyToUsers', {
                    count: draft.userCount ?? 0,
                    defaultValue: 'Apply to {{count}} user(s)',
                  })}
                </button>
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  )
}
