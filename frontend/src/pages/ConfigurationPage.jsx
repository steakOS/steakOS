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

import { useEffect, useMemo, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { setupApi, capabilityApi, meApi, authApi } from '../api/client'
import { allowsSilverSurfaces, normalizeMetalTier } from '../utils/silverSurfaces'

/**
 * System → Configuration (danger zone).
 * CEO / Admin only: Silver pack options, SYSTEM process catalogue,
 * disabled-user purge, admin setup reset.
 */
export default function ConfigurationPage() {
  const { t } = useTranslation('common')
  const [status, setStatus] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [wipeMode, setWipeMode] = useState('keep')
  const [resetConfirmOpen, setResetConfirmOpen] = useState(false)
  const [wipeAllConfirmOpen, setWipeAllConfirmOpen] = useState(false)
  const [resetError, setResetError] = useState(null)
  const [seedMsg, setSeedMsg] = useState(null)
  const [allowed, setAllowed] = useState(null)
  const [packMsg, setPackMsg] = useState(null)
  const [packBusy, setPackBusy] = useState(false)
  const [messagingDraft, setMessagingDraft] = useState({
    emailEnabled: false,
    telegramEnabled: false,
  })
  const [upgradeConfirmOpen, setUpgradeConfirmOpen] = useState(false)
  const [upgradeTarget, setUpgradeTarget] = useState(null)
  const [upgradeError, setUpgradeError] = useState(null)
  const [purgeDisabledBusy, setPurgeDisabledBusy] = useState(false)
  const [purgeDisabledConfirmOpen, setPurgeDisabledConfirmOpen] = useState(false)
  const [purgeDisabledMsg, setPurgeDisabledMsg] = useState(null)
  const [purgeDisabledError, setPurgeDisabledError] = useState(null)
  const [disabledUserCount, setDisabledUserCount] = useState(null)

  const load = async () => {
    try {
      const me = await meApi.get()
      const auths = Array.isArray(me?.authorities) ? me.authorities : []
      const admin = !!(me?.isAdmin || auths.includes('ROLE_ADMIN'))
      const ceo = !!(me?.isCeo || auths.includes('ROLE_CEO'))
      const sys = me?.permissions?.system || {}
      const can =
        !!(sys.configuration || sys.setup) ||
        admin ||
        ceo ||
        me?.roleProfileId === 'admin' ||
        me?.roleProfileId === 'ceo'
      setAllowed(can)
      if (!can) return

      const st = await setupApi.status()
      setStatus(st)
      setMessagingDraft({
        emailEnabled: !!st?.messaging?.emailEnabled,
        telegramEnabled: !!st?.messaging?.telegramEnabled,
      })
      try {
        const ul = await authApi.usersList()
        const items = Array.isArray(ul?.items) ? ul.items : []
        setDisabledUserCount(items.filter((u) => u && u.enabled === false).length)
      } catch {
        setDisabledUserCount(null)
      }
    } catch (e) {
      setError(e.message || String(e))
      setAllowed(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const currentTier = useMemo(
    () => normalizeMetalTier(status?.templateTier),
    [status?.templateTier],
  )
  const isSilverTenant = allowsSilverSurfaces(currentTier)
  const openMetals = useMemo(() => {
    const tiers = status?.tiers
    if (!Array.isArray(tiers)) return ['BRONZE']
    return tiers.filter((x) => x?.openForMetalSelection).map((x) => String(x.code).toUpperCase())
  }, [status?.tiers])
  const silverOpen = openMetals.includes('SILVER')
  const pluginDepth = status?.pluginDepth || {}

  const savePackOptions = async () => {
    setPackBusy(true)
    setPackMsg(null)
    setError(null)
    try {
      const r = await setupApi.packOptions({
        messaging: {
          emailEnabled: !!messagingDraft.emailEnabled,
          telegramEnabled: !!messagingDraft.telegramEnabled,
        },
      })
      if (r?.status) {
        setStatus(r.status)
        setMessagingDraft({
          emailEnabled: !!r.status.messaging?.emailEnabled,
          telegramEnabled: !!r.status.messaging?.telegramEnabled,
        })
      }
      setPackMsg(
        t('pages.settings.packOptionsSaved', {
          defaultValue: 'Pack options saved.',
        }),
      )
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setPackBusy(false)
    }
  }

  const beginUpgrade = (tierCode) => {
    setUpgradeTarget(String(tierCode || 'SILVER').toUpperCase())
    setUpgradeError(null)
    setUpgradeConfirmOpen(true)
  }

  const cancelUpgrade = () => {
    if (packBusy) return
    setUpgradeConfirmOpen(false)
    setUpgradeTarget(null)
    setUpgradeError(null)
  }

  const confirmUpgrade = async () => {
    if (!upgradeTarget) return
    setPackBusy(true)
    setUpgradeError(null)
    setError(null)
    try {
      const r = await setupApi.templateTier({
        templateTier: upgradeTarget,
        messaging: { emailEnabled: false, telegramEnabled: false },
      })
      if (r?.status) {
        setStatus(r.status)
        setMessagingDraft({
          emailEnabled: !!r.status.messaging?.emailEnabled,
          telegramEnabled: !!r.status.messaging?.telegramEnabled,
        })
      }
      setUpgradeConfirmOpen(false)
      setUpgradeTarget(null)
      setPackMsg(
        t('pages.settings.templateUpgradeDone', {
          tier: upgradeTarget,
          defaultValue: 'Template upgraded to {{tier}}.',
        }),
      )
    } catch (e) {
      const msg = e.message || String(e)
      setUpgradeError(msg)
      setError(msg)
    } finally {
      setPackBusy(false)
    }
  }

  const beginReset = () => {
    setResetError(null)
    setError(null)
    setWipeAllConfirmOpen(false)
    setResetConfirmOpen(true)
  }

  const cancelReset = () => {
    if (busy) return
    setResetConfirmOpen(false)
    setWipeAllConfirmOpen(false)
    setResetError(null)
  }

  const confirmReset = () => {
    setResetError(null)
    setError(null)
    if (wipeMode === 'all') {
      setWipeAllConfirmOpen(true)
      return
    }
    runReset()
  }

  const runReset = async () => {
    setBusy(true)
    setResetError(null)
    setError(null)
    try {
      const result = await setupApi.reset({
        wipeMode,
        wipeSeed: wipeMode === 'seed' || wipeMode === 'all',
        wipeAll: wipeMode === 'all',
      })
      if (result?.status?.setupComplete === true) {
        throw new Error(
          t('pages.settings.resetStillComplete', {
            defaultValue: 'Reset did not reopen setup — server still reports setup complete.',
          }),
        )
      }
      try {
        sessionStorage.removeItem('steakos.setupCelebration')
      } catch {
        /* ignore */
      }
      setResetConfirmOpen(false)
      setWipeAllConfirmOpen(false)
      window.location.assign('/setup')
    } catch (e) {
      const msg =
        e?.code === 'TOTP_REQUIRED'
          ? t('pages.settings.resetNeedsTotp', {
              defaultValue:
                'Authenticator code required. Open /totp, enter a code, return here, and confirm reset again.',
            })
          : e.message || String(e)
      setResetError(msg)
      setError(msg)
    } finally {
      setBusy(false)
    }
  }

  if (allowed === null) {
    return (
      <div>
        <h1 className="page-title">
          {t('nav.configuration', { defaultValue: 'Configuration' })}
        </h1>
        <p className="muted">{t('actions.loading')}</p>
      </div>
    )
  }

  if (!allowed) {
    return <Navigate to="/settings" replace />
  }

  return (
    <div>
      <h1 className="page-title">
        {t('nav.configuration', { defaultValue: 'Configuration' })}
      </h1>
      <p className="page-sub">
        {t('pages.configuration.subtitle', {
          defaultValue: 'Danger-zone controls for pack, catalogue, accounts, and setup reset.',
        })}
      </p>

      <div className="banner-error" role="alert">
        <strong>
          {t('pages.configuration.dangerTitle', { defaultValue: 'Danger zone' })}
        </strong>
        <p style={{ margin: '0.35rem 0 0' }}>
          {t('pages.configuration.dangerBody', {
            defaultValue:
              'Changing configuration without consideration for existing processes and data can have disastrous effects on the company.',
          })}
        </p>
      </div>

      {error && <div className="setup-error">{error}</div>}

      <div className="card" style={{ marginTop: 16 }}>
        <h2>
          {t('pages.settings.templateUpgradeTitle', {
            defaultValue: 'Template pack',
          })}
        </h2>
        <p className="page-sub">
          {t('pages.settings.templateUpgradeBlurb', {
            defaultValue:
              'Current metal and open packs. Upgrading to Silver adds Governance and Stakeholder desks, Sales/Finance Advanced depth, and optional email/Telegram. Existing data stays.',
          })}
        </p>
        <p>
          {t('pages.settings.template')}: <strong>{currentTier}</strong>
          {openMetals.length > 0 && (
            <span className="muted" style={{ marginLeft: 8 }}>
              (
              {t('pages.settings.openMetals', {
                list: openMetals.join(', '),
                defaultValue: 'Open: {{list}}',
              })}
              )
            </span>
          )}
        </p>
        {!isSilverTenant && (
          <div style={{ marginTop: 12 }}>
            {silverOpen ? (
              !upgradeConfirmOpen ? (
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={packBusy}
                  onClick={() => beginUpgrade('SILVER')}
                >
                  {t('pages.settings.templateUpgradeSilver', {
                    defaultValue: 'Upgrade to Silver',
                  })}
                </button>
              ) : (
                <div
                  className="setup-error"
                  role="alertdialog"
                  aria-labelledby="upgrade-confirm-title"
                  style={{
                    marginTop: 8,
                    padding: 12,
                    border: '1px solid var(--border, #c45)',
                    borderRadius: 8,
                    background: 'var(--surface-warn, #fff8f6)',
                  }}
                >
                  <p id="upgrade-confirm-title" style={{ fontWeight: 600, marginTop: 0 }}>
                    {t('pages.settings.templateUpgradeConfirmTitle', {
                      tier: upgradeTarget || 'SILVER',
                      defaultValue: 'Upgrade to {{tier}}?',
                    })}
                  </p>
                  <p>
                    {t('pages.settings.templateUpgradeConfirmBody', {
                      defaultValue:
                        'Adds Governance and Stakeholder desks, Sales/Finance Advanced depth, and optional email/Telegram (off by default). Existing Bronze data stays.',
                    })}
                  </p>
                  {upgradeError && (
                    <p className="setup-error" style={{ marginTop: 8 }}>
                      {upgradeError}
                    </p>
                  )}
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 12 }}>
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={packBusy}
                      onClick={confirmUpgrade}
                    >
                      {packBusy
                        ? t('actions.loading')
                        : t('pages.settings.templateUpgradeConfirm', {
                            defaultValue: 'Yes, upgrade',
                          })}
                    </button>
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={packBusy}
                      onClick={cancelUpgrade}
                    >
                      {t('actions.cancel', { defaultValue: 'Cancel' })}
                    </button>
                  </div>
                </div>
              )
            ) : (
              <p className="muted" style={{ fontSize: 13 }}>
                {t('pages.settings.templateUpgradeSilverLocked', {
                  defaultValue:
                    'Silver is not open for metal selection yet. Pack options appear here once this tenant is on Silver.',
                })}
              </p>
            )}
          </div>
        )}
        {packMsg && !isSilverTenant && (
          <p className="muted" style={{ marginTop: 8 }}>
            {packMsg}
          </p>
        )}
      </div>

      {isSilverTenant && (
        <div className="card setup-silver-options" style={{ marginTop: 16 }}>
          <h2>
            {t('pages.settings.packOptionsTitle', {
              defaultValue: 'Silver pack options',
            })}
          </h2>
          <p className="page-sub">
            {t('pages.settings.packOptionsBlurb', {
              defaultValue:
                'Sales and Finance Advanced depth is pack-mandatory. Messaging stays off until you enable it and add credentials under Integrations.',
            })}
          </p>
          <p className="muted" style={{ fontSize: '0.9rem' }}>
            {t('pages.settings.depthBlurb', {
              defaultValue:
                'Sales and Finance run at Advanced depth on Silver (pack-mandatory). Not toggled back to Basics here.',
            })}
          </p>
          <div className="setup-depth-badges">
            <span className="setup-depth-badge">
              <strong>{t('pages.settings.depthSales', { defaultValue: 'Sales' })}</strong>
              <span className="badge badge-ok">{pluginDepth.sales || 'ADVANCED'}</span>
              <span className="muted" style={{ fontSize: 12, marginLeft: 6 }}>
                {t('pages.settings.packFixed', { defaultValue: 'Pack — required' })}
              </span>
            </span>
            <span className="setup-depth-badge">
              <strong>{t('pages.settings.depthFinance', { defaultValue: 'Finance' })}</strong>
              <span className="badge badge-ok">{pluginDepth.finance || 'ADVANCED'}</span>
              <span className="muted" style={{ fontSize: 12, marginLeft: 6 }}>
                {t('pages.settings.packFixed', { defaultValue: 'Pack — required' })}
              </span>
            </span>
          </div>
          <p className="muted" style={{ fontSize: '0.9rem', marginTop: '0.85rem' }}>
            {t('pages.settings.messagingBlurb', {
              defaultValue:
                'Optional outbound messaging. Leave off until you add credentials under System → Integrations.',
            })}
          </p>
          <label className="setup-check">
            <input
              type="checkbox"
              checked={!!messagingDraft.emailEnabled}
              disabled={packBusy}
              onChange={(e) =>
                setMessagingDraft((d) => ({ ...d, emailEnabled: e.target.checked }))
              }
            />
            {t('pages.settings.emailEnabled', {
              defaultValue: 'Enable outbound email (configure later)',
            })}
          </label>
          <label className="setup-check">
            <input
              type="checkbox"
              checked={!!messagingDraft.telegramEnabled}
              disabled={packBusy}
              onChange={(e) =>
                setMessagingDraft((d) => ({ ...d, telegramEnabled: e.target.checked }))
              }
            />
            {t('pages.settings.telegramEnabled', {
              defaultValue: 'Enable Telegram notifies (configure later)',
            })}
          </label>
          <div style={{ marginTop: 12, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            <button
              type="button"
              className="btn btn-primary"
              disabled={packBusy}
              onClick={savePackOptions}
            >
              {packBusy
                ? t('actions.loading')
                : t('pages.settings.savePackOptions', { defaultValue: 'Save pack options' })}
            </button>
            <Link to="/system/integrations" className="btn btn-ghost">
              {t('pages.settings.openIntegrations', { defaultValue: 'Open Integrations' })}
            </Link>
          </div>
          {packMsg && (
            <p className="muted" style={{ marginTop: 8 }}>
              {packMsg}
            </p>
          )}
        </div>
      )}

      <div className="card" style={{ marginTop: 16 }}>
        <h2>{t('pages.settings.processCatalogueTitle')}</h2>
        <p className="page-sub">{t('pages.settings.processCatalogueBlurb')}</p>
        <button
          type="button"
          className="btn btn-primary"
          disabled={busy}
          onClick={async () => {
            setBusy(true)
            setError(null)
            setSeedMsg(null)
            try {
              const r = await capabilityApi.processes.reseed()
              setSeedMsg(
                t('pages.settings.reseededDetail', {
                  created: r.created || 0,
                  updated: r.updated || 0,
                  steps: r.stepsUpserted || 0,
                  catalogSize: r.catalogSize || 0,
                }),
              )
            } catch (e) {
              setError(e.message || String(e))
            } finally {
              setBusy(false)
            }
          }}
        >
          {busy ? t('actions.loading') : t('pages.settings.reseedProcesses')}
        </button>
        {seedMsg && (
          <p className="muted" style={{ marginTop: 8 }}>
            {seedMsg}
          </p>
        )}
      </div>

      <div className="card" style={{ marginTop: 16 }} id="admin-purge-disabled-users">
        <h2>
          {t('pages.settings.purgeDisabledTitle', {
            defaultValue: 'Disabled users',
          })}
        </h2>
        <p className="page-sub">
          {t('pages.settings.purgeDisabledBlurb', {
            defaultValue:
              'Permanently delete login accounts that are turned off. Roles, company memberships, and agent tokens for those users are removed too. Enabled accounts are kept.',
          })}
        </p>
        <p className="muted" style={{ fontSize: 13 }}>
          {disabledUserCount == null
            ? t('pages.settings.purgeDisabledCounting', {
                defaultValue: 'Counting disabled accounts…',
              })
            : t('pages.settings.purgeDisabledCount', {
                count: disabledUserCount,
                defaultValue:
                  disabledUserCount === 1
                    ? '1 disabled account'
                    : '{{count}} disabled accounts',
              })}
        </p>
        {!purgeDisabledConfirmOpen ? (
          <div style={{ marginTop: 12 }}>
            <button
              type="button"
              className="btn btn-primary"
              disabled={purgeDisabledBusy || disabledUserCount === 0}
              onClick={() => {
                setPurgeDisabledError(null)
                setPurgeDisabledMsg(null)
                setPurgeDisabledConfirmOpen(true)
              }}
            >
              {t('pages.settings.purgeDisabledOpen', {
                defaultValue: 'Delete all disabled users',
              })}
            </button>
          </div>
        ) : (
          <div
            className="setup-error"
            role="alertdialog"
            aria-labelledby="purge-disabled-confirm-title"
            style={{
              marginTop: 16,
              padding: 12,
              border: '1px solid var(--border, #c45)',
              borderRadius: 8,
              background: 'var(--surface-warn, #fff8f6)',
            }}
          >
            <p id="purge-disabled-confirm-title" style={{ fontWeight: 600, marginTop: 0 }}>
              {t('pages.settings.purgeDisabledConfirmTitle', {
                defaultValue: 'Delete all disabled users?',
              })}
            </p>
            <p>
              {t('pages.settings.purgeDisabledConfirmBody', {
                count: disabledUserCount ?? 0,
                defaultValue:
                  'This permanently removes every disabled login ({{count}}). It cannot be undone.',
              })}
            </p>
            {purgeDisabledError && (
              <p className="setup-error" style={{ marginTop: 8 }}>
                {purgeDisabledError}
              </p>
            )}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 12 }}>
              <button
                type="button"
                className="btn btn-primary"
                disabled={purgeDisabledBusy}
                onClick={async () => {
                  setPurgeDisabledBusy(true)
                  setPurgeDisabledError(null)
                  setPurgeDisabledMsg(null)
                  try {
                    const r = await authApi.usersPurgeDisabled()
                    const n = r?.count ?? (Array.isArray(r?.deleted) ? r.deleted.length : 0)
                    setPurgeDisabledMsg(
                      t('pages.settings.purgeDisabledDone', {
                        count: n,
                        defaultValue:
                          n === 1
                            ? 'Deleted 1 disabled user.'
                            : 'Deleted {{count}} disabled users.',
                      }),
                    )
                    setDisabledUserCount(0)
                    setPurgeDisabledConfirmOpen(false)
                  } catch (e) {
                    setPurgeDisabledError(e.message || String(e))
                  } finally {
                    setPurgeDisabledBusy(false)
                  }
                }}
              >
                {purgeDisabledBusy
                  ? t('actions.loading', { defaultValue: 'Working…' })
                  : t('pages.settings.purgeDisabledConfirm', {
                      defaultValue: 'Yes, delete disabled users',
                    })}
              </button>
              <button
                type="button"
                className="btn btn-ghost"
                disabled={purgeDisabledBusy}
                onClick={() => setPurgeDisabledConfirmOpen(false)}
              >
                {t('actions.cancel', { defaultValue: 'Cancel' })}
              </button>
            </div>
          </div>
        )}
        {purgeDisabledMsg && (
          <p className="muted" style={{ marginTop: 8 }}>
            {purgeDisabledMsg}
          </p>
        )}
      </div>

      <div className="card" style={{ marginTop: 16 }} id="admin-setup-reset">
        <h2>{t('pages.settings.adminReset')}</h2>
        <p className="page-sub">{t('pages.settings.adminBlurb')}</p>
        <fieldset
          disabled={busy || resetConfirmOpen}
          style={{ border: 'none', margin: 0, padding: 0 }}
        >
          <legend className="muted" style={{ fontSize: 13, marginBottom: 8 }}>
            {t('pages.settings.wipeModeLegend', {
              defaultValue: 'What should this reset remove?',
            })}
          </legend>
          <label className="setup-check">
            <input
              type="radio"
              name="wipeMode"
              value="keep"
              checked={wipeMode === 'keep'}
              onChange={() => setWipeMode('keep')}
            />
            {t('pages.settings.wipeKeep', {
              defaultValue: 'Keep company data (re-open wizard only)',
            })}
          </label>
          <label className="setup-check">
            <input
              type="radio"
              name="wipeMode"
              value="seed"
              checked={wipeMode === 'seed'}
              onChange={() => setWipeMode('seed')}
            />
            {t('pages.settings.wipeSeed')}
          </label>
          <label className="setup-check">
            <input
              type="radio"
              name="wipeMode"
              value="all"
              checked={wipeMode === 'all'}
              onChange={() => setWipeMode('all')}
            />
            {t('pages.settings.wipeAll', {
              defaultValue: 'Wipe seed identity and all data',
            })}
          </label>
        </fieldset>

        {!resetConfirmOpen ? (
          <div style={{ marginTop: 12 }}>
            <button type="button" className="btn btn-primary" disabled={busy} onClick={beginReset}>
              {t('pages.settings.resetOpen')}
            </button>
          </div>
        ) : (
          <div
            className="setup-error"
            role="alertdialog"
            aria-labelledby="reset-confirm-title"
            style={{
              marginTop: 16,
              padding: 12,
              border: '1px solid var(--border, #c45)',
              borderRadius: 8,
              background: 'var(--surface-warn, #fff8f6)',
            }}
          >
            <p id="reset-confirm-title" style={{ fontWeight: 600, marginTop: 0 }}>
              {t('pages.settings.confirmTitle', { defaultValue: 'Confirm setup reset' })}
            </p>
            <p>
              {wipeMode === 'all'
                ? t('pages.settings.confirmWipeAll', {
                    defaultValue:
                      'Reset setup and permanently delete all business data? A database backup is written first. Login accounts are kept so you can run the wizard again.',
                  })
                : wipeMode === 'seed'
                  ? t('pages.settings.confirmWipe')
                  : t('pages.settings.confirmReopen')}
            </p>
            <p className="muted" style={{ fontSize: 13 }}>
              {t('pages.settings.confirmRedirectHint', {
                defaultValue: 'After success you will be taken to /setup to run the wizard again.',
              })}
            </p>
            {resetError && (
              <p className="setup-error" style={{ marginTop: 8 }}>
                {resetError}
              </p>
            )}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 12 }}>
              <button
                type="button"
                className="btn btn-primary"
                disabled={busy}
                onClick={confirmReset}
              >
                {busy
                  ? t('pages.settings.resetting')
                  : wipeMode === 'all'
                    ? t('pages.settings.confirmResetContinue', {
                        defaultValue: 'Continue…',
                      })
                    : t('pages.settings.confirmReset', {
                        defaultValue: 'Yes, reset and open wizard',
                      })}
              </button>
              <button type="button" className="btn btn-ghost" disabled={busy} onClick={cancelReset}>
                {t('actions.cancel', { defaultValue: 'Cancel' })}
              </button>
            </div>
          </div>
        )}

        {wipeAllConfirmOpen && (
          <div
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="wipe-all-confirm-title"
            style={{
              position: 'fixed',
              inset: 0,
              zIndex: 80,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: 'rgba(0,0,0,0.45)',
              padding: 16,
            }}
          >
            <div
              className="card"
              style={{
                maxWidth: 480,
                width: '100%',
                border: '2px solid var(--danger, #b33)',
                boxShadow: '0 8px 32px rgba(0,0,0,0.25)',
              }}
            >
              <h2 id="wipe-all-confirm-title" style={{ marginTop: 0 }}>
                {t('pages.settings.wipeAllPopupTitle', {
                  defaultValue: 'Delete all data?',
                })}
              </h2>
              <p>
                {t('pages.settings.wipeAllPopupBody', {
                  defaultValue:
                    'All database data will be deleted. steakOS will first write a PostgreSQL backup into the backup folder (var/backups/db), then truncate application tables. Login accounts (user / role) are kept so you can sign in and run the setup wizard again. This cannot be undone without restoring the backup.',
                })}
              </p>
              {resetError && (
                <p className="setup-error" style={{ marginTop: 8 }}>
                  {resetError}
                </p>
              )}
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 16 }}>
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={busy}
                  onClick={runReset}
                >
                  {busy
                    ? t('pages.settings.resetting')
                    : t('pages.settings.wipeAllConfirm', {
                        defaultValue: 'Backup, then delete all data',
                      })}
                </button>
                <button
                  type="button"
                  className="btn btn-ghost"
                  disabled={busy}
                  onClick={() => {
                    if (busy) return
                    setWipeAllConfirmOpen(false)
                  }}
                >
                  {t('actions.cancel', { defaultValue: 'Cancel' })}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
