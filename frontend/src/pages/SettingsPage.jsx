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

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { setupApi, capabilityApi, agentTokenApi, currenciesApi, meApi, financeOpsApi } from '../api/client'
import { useLocaleBootstrap } from '../hooks/useLocaleBootstrap'

const L2_WARN_DISMISS_KEY = 'steakos.btcLevel2WarnDismissed'

/**
 * System → Settings: locale, setup status + admin full reset + Hermes agent tokens.
 */
export default function SettingsPage() {
  const { t } = useTranslation('common')
  const { locale, locales, changeLocale } = useLocaleBootstrap()
  const [status, setStatus] = useState(null)
  const [check, setCheck] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [wipe, setWipe] = useState(false)
  /** In-page confirm for live-company reset (window.confirm is easy to miss / block). */
  const [resetConfirmOpen, setResetConfirmOpen] = useState(false)
  const [resetError, setResetError] = useState(null)
  const [localeMsg, setLocaleMsg] = useState(null)
  const [seedMsg, setSeedMsg] = useState(null)
  const [agentTokens, setAgentTokens] = useState([])
  const [agentUsername, setAgentUsername] = useState('HiveA:bookkeeper')
  const [agentLabel, setAgentLabel] = useState('olympus primary')
  const [agentHost, setAgentHost] = useState('olympus')
  const [mintedToken, setMintedToken] = useState(null)
  const [agentMsg, setAgentMsg] = useState(null)
  const [isAdmin, setIsAdmin] = useState(false)
  const [isCeo, setIsCeo] = useState(false)
  const [catalogCurrencies, setCatalogCurrencies] = useState([])
  const [inUseCodes, setInUseCodes] = useState([])
  const [companyCurrency, setCompanyCurrency] = useState(null)
  const [currencyMsg, setCurrencyMsg] = useState(null)
  const [currencyBusy, setCurrencyBusy] = useState(false)
  const [btcLevel, setBtcLevel] = useState(null)
  const [btcMsg, setBtcMsg] = useState(null)
  const [btcBusy, setBtcBusy] = useState(false)
  const [btcForm, setBtcForm] = useState({
    level: 'DEFERRED',
    receiveAddress: '',
    receiveAddressLabel: '',
    ceoTrainingAck: false,
    confirmDowngrade: false,
  })
  const [l2WarnDismissed, setL2WarnDismissed] = useState(() => {
    try {
      return sessionStorage.getItem(L2_WARN_DISMISS_KEY) === '1'
    } catch {
      return false
    }
  })

  const loadCurrencies = async () => {
    try {
      const [cat, iu] = await Promise.all([currenciesApi.list(), currenciesApi.inUse()])
      setCatalogCurrencies(cat?.items || [])
      setInUseCodes((iu?.items || []).map((c) => c.code).filter(Boolean))
      setCompanyCurrency(iu?.companyCurrency || null)
    } catch {
      setCatalogCurrencies([])
      setInUseCodes([])
    }
  }

  const loadBtcLevel = async () => {
    try {
      const lvl = await financeOpsApi.btcLevelGet()
      setBtcLevel(lvl)
      setBtcForm((f) => ({
        ...f,
        level: lvl?.level || 'DEFERRED',
        receiveAddress: lvl?.receiveAddress || '',
        receiveAddressLabel: lvl?.receiveAddressLabel || '',
        ceoTrainingAck: false,
        confirmDowngrade: false,
      }))
    } catch {
      setBtcLevel(null)
    }
  }

  const load = async () => {
    try {
      setStatus(await setupApi.status())
      setCheck(await setupApi.check())
      try {
        const me = await meApi.get()
        setIsAdmin(!!me?.isAdmin)
        setIsCeo(!!me?.isCeo)
      } catch {
        setIsAdmin(false)
        setIsCeo(false)
      }
      try {
        const tok = await agentTokenApi.list(false)
        setAgentTokens(tok?.tokens || [])
      } catch {
        setAgentTokens([])
      }
      await loadCurrencies()
      await loadBtcLevel()
    } catch (e) {
      setError(e.message || String(e))
    }
  }

  useEffect(() => {
    load()
  }, [])

  const onLocaleChange = async (e) => {
    const code = e.target.value
    setLocaleMsg(null)
    try {
      await changeLocale(code)
      setLocaleMsg(t('locale.saved'))
    } catch (err) {
      setError(err.message || String(err))
    }
  }

  const beginReset = () => {
    setResetError(null)
    setError(null)
    setResetConfirmOpen(true)
  }

  const cancelReset = () => {
    if (busy) return
    setResetConfirmOpen(false)
    setResetError(null)
  }

  /**
   * Admin reset of the current (live) company setup.
   * Hard-navigate to /setup so SetupGate remounts with setupComplete=false
   * (soft navigate left stale gate state / missed celebration-clear).
   */
  const confirmReset = async () => {
    setBusy(true)
    setResetError(null)
    setError(null)
    try {
      const result = await setupApi.reset({ wipeSeed: wipe })
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
      // Full load: remount SetupGate + wizard with fresh status
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

  return (
    <div>
      <h1 className="page-title">{t('nav.settings')}</h1>
      <p className="page-sub">{t('pages.settings.subtitle')}</p>
      {error && <div className="setup-error">{error}</div>}

      <div className="card" style={{ marginTop: 16 }}>
        <h2>{t('locale.label')}</h2>
        <p className="page-sub">{t('locale.hint')}</p>
        <label>
          {t('pages.settings.localeField')}
          <select value={locale || 'en_US'} onChange={onLocaleChange}>
            {(locales.length
              ? locales
              : [
                  { code: 'en_US', name: 'English (United States)' },
                  { code: 'en_SG', name: 'English (Singapore)' },
                  { code: 'de_DE', name: 'Deutsch (Deutschland)' },
                  { code: 'de_AT', name: 'Deutsch (Österreich)' },
                  { code: 'de_CH', name: 'Deutsch (Schweiz)' },
                  { code: 'es_ES', name: 'Español (España)' },
                ]
            ).map((l) => (
              <option key={l.code} value={l.code}>
                {l.name} ({l.code})
              </option>
            ))}
          </select>
        </label>
        {localeMsg && <p className="muted" style={{ marginTop: 8 }}>{localeMsg}</p>}
      </div>

      {/* Company profile, Integrations hub, Bitcoin level, Hermes tokens → System → Integrations / Company */}

      {isAdmin && (
        <div className="card" style={{ marginTop: 16 }}>
          <h2>
            {t('pages.settings.currenciesWeUse', { defaultValue: 'Currencies we use' })}
          </h2>
          <p className="page-sub">
            {t('pages.settings.currenciesWeUseHint', {
              defaultValue:
                'Short list for dropdowns. Company currency stays enabled. Enable a code before writing it on partners, catalogue, or documents.',
            })}
          </p>
          {companyCurrency && (
            <p className="muted" style={{ fontSize: 13 }}>
              {t('pages.settings.companyCurrencyLabel', { defaultValue: 'Company currency:' })}{' '}
              <strong className="mono">{companyCurrency}</strong>
            </p>
          )}
          {currencyMsg && <p className="muted">{currencyMsg}</p>}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
              gap: 8,
              marginTop: 8,
            }}
          >
            {catalogCurrencies.map((c) => {
              const checked = inUseCodes.includes(c.code)
              const locked = c.code === companyCurrency
              return (
                <label key={c.code} className="checkbox-label" style={{ alignItems: 'flex-start' }}>
                  <input
                    type="checkbox"
                    checked={checked || locked}
                    disabled={currencyBusy || locked}
                    onChange={(e) => {
                      const on = e.target.checked
                      setInUseCodes((prev) => {
                        const set = new Set(prev)
                        if (on) set.add(c.code)
                        else set.delete(c.code)
                        if (companyCurrency) set.add(companyCurrency)
                        return [...set]
                      })
                    }}
                  />
                  <span>
                    <span className="mono">{c.code}</span> — {c.name}
                    {locked
                      ? t('pages.settings.companyLockSuffix', { defaultValue: ' (company)' })
                      : ''}
                  </span>
                </label>
              )
            })}
          </div>
          <button
            type="button"
            className="btn btn-primary"
            style={{ marginTop: 12 }}
            disabled={currencyBusy}
            onClick={async () => {
              setCurrencyBusy(true)
              setCurrencyMsg(null)
              setError(null)
              try {
                const codes = [...new Set([...inUseCodes, companyCurrency].filter(Boolean))]
                const r = await currenciesApi.setInUse(codes)
                setInUseCodes((r?.items || []).map((c) => c.code).filter(Boolean))
                setCompanyCurrency(r?.companyCurrency || companyCurrency)
                setCurrencyMsg(
                  t('pages.settings.currenciesSaved', { defaultValue: 'Currencies we use saved.' }),
                )
              } catch (e) {
                setError(e.message || String(e))
              } finally {
                setCurrencyBusy(false)
              }
            }}
          >
            {currencyBusy
              ? '…'
              : t('pages.settings.saveCurrencies', { defaultValue: 'Save currencies' })}
          </button>
        </div>
      )}

      <div className="card" style={{ marginTop: 16 }}>
        <h2>{t('pages.settings.demoCompanies', { defaultValue: 'Demo companies' })}</h2>
        <p className="page-sub">
          {t('pages.settings.demoBlurb', {
            defaultValue:
              'Each demo is isolated from your live company. Reset only wipes that demo’s showcase data.',
          })}
        </p>
        {seedMsg && <p className="muted">{seedMsg}</p>}

        <h3 className="section-label">
          {t('pages.settings.demoOrangeTitle', { defaultValue: 'Orange Steak House' })}
        </h3>
        <p className="muted" style={{ fontSize: 13 }}>
          {t('pages.settings.demoOrangeBlurb', {
            defaultValue: 'Restaurant / hospitality showcase.',
          })}
        </p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 16 }}>
          <button
            type="button"
            className="btn btn-primary"
            disabled={busy}
            onClick={async () => {
              if (
                !window.confirm(
                  t('pages.settings.demoConfirmResetOrange', {
                    defaultValue:
                      'Reset Orange Steak House demo data? Live company and Heard & Cook are not touched.',
                  }),
                )
              ) {
                return
              }
              setBusy(true)
              setError(null)
              setSeedMsg(null)
              try {
                const r = await setupApi.demoReset()
                setSeedMsg(
                  r.message ||
                    t('pages.settings.demoOrangeResetDone', {
                      defaultValue: 'Orange Steak House reset complete',
                    }),
                )
              } catch (e) {
                setError(e.message || String(e))
              } finally {
                setBusy(false)
              }
            }}
          >
            {t('pages.settings.demoResetOrange', { defaultValue: 'Reset Orange Steak House' })}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={busy}
            onClick={async () => {
              setBusy(true)
              setError(null)
              setSeedMsg(null)
              try {
                const r = await setupApi.demoSeed()
                setSeedMsg(
                  r.message ||
                    (r.skipped
                      ? t('pages.settings.demoOrangeAlready', {
                          defaultValue: 'Orange already seeded',
                        })
                      : t('pages.settings.demoOrangeSeeded', { defaultValue: 'Orange seeded' })),
                )
              } catch (e) {
                setError(e.message || String(e))
              } finally {
                setBusy(false)
              }
            }}
          >
            {t('pages.settings.demoSeedOrange', { defaultValue: 'Seed Orange (if empty)' })}
          </button>
        </div>

        <h3 className="section-label">
          {t('pages.settings.demoHeardTitle', { defaultValue: 'Heard & Cook Architects' })}
        </h3>
        <p className="muted" style={{ fontSize: 13 }}>
          {t('pages.settings.demoHeardBlurb', {
            defaultValue:
              'Indian Rocks Beach, FL — residential/commercial design, plan DVDs, kitchen studio.',
          })}
        </p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          <button
            type="button"
            className="btn btn-primary"
            disabled={busy}
            onClick={async () => {
              if (
                !window.confirm(
                  t('pages.settings.demoConfirmResetHeard', {
                    defaultValue:
                      'Reset Heard & Cook Architects demo data? Live company and Orange Steak House are not touched.',
                  }),
                )
              ) {
                return
              }
              setBusy(true)
              setError(null)
              setSeedMsg(null)
              try {
                const r = await setupApi.heardCookReset()
                setSeedMsg(
                  r.message ||
                    t('pages.settings.demoHeardResetDone', {
                      defaultValue: 'Heard & Cook reset complete',
                    }),
                )
              } catch (e) {
                setError(e.message || String(e))
              } finally {
                setBusy(false)
              }
            }}
          >
            {t('pages.settings.demoResetHeard', { defaultValue: 'Reset Heard & Cook' })}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={busy}
            onClick={async () => {
              setBusy(true)
              setError(null)
              setSeedMsg(null)
              try {
                const r = await setupApi.heardCookSeed()
                setSeedMsg(
                  r.message ||
                    (r.skipped
                      ? t('pages.settings.demoHeardAlready', {
                          defaultValue: 'Heard & Cook already seeded',
                        })
                      : t('pages.settings.demoHeardSeeded', {
                          defaultValue: 'Heard & Cook seeded',
                        })),
                )
              } catch (e) {
                setError(e.message || String(e))
              } finally {
                setBusy(false)
              }
            }}
          >
            {t('pages.settings.demoSeedHeard', { defaultValue: 'Seed Heard & Cook (if empty)' })}
          </button>
        </div>
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>{t('pages.settings.setupStatus')}</h2>
        {!status ? (
          <p>{t('actions.loading')}</p>
        ) : (
          <dl className="setup-summary">
            <dt>{t('pages.settings.complete')}</dt>
            <dd>
              {status.setupComplete
                ? t('pages.settings.completeYes')
                : t('pages.settings.completeNo')}
            </dd>
            <dt>{t('pages.settings.template')}</dt>
            <dd>{status.templateTier}</dd>
            <dt>{t('pages.settings.enabledFamilies')}</dt>
            <dd>{(status.enabledFamilies || []).join(', ')}</dd>
            <dt>{t('pages.settings.completed')}</dt>
            <dd>
              {status.completedAt
                ? `${new Date(status.completedAt).toLocaleString()} by ${status.completedBy || '—'}`
                : '—'}
            </dd>
          </dl>
        )}
        {check && (
          <p className="page-sub">
            {t('pages.settings.consistency', {
              status: check.ok ? t('pages.settings.ok') : check.issues?.map((i) => i.message).join('; '),
              count: check.counts?.accounts ?? 0,
            })}
          </p>
        )}
      </div>

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
        {seedMsg && <p className="muted" style={{ marginTop: 8 }}>{seedMsg}</p>}
      </div>

      <div className="card" style={{ marginTop: 16 }} id="admin-setup-reset">
        <h2>{t('pages.settings.adminReset')}</h2>
        <p className="page-sub">{t('pages.settings.adminBlurb')}</p>
        <label className="setup-check">
          <input
            type="checkbox"
            checked={wipe}
            disabled={busy || resetConfirmOpen}
            onChange={(e) => setWipe(e.target.checked)}
          />
          {t('pages.settings.wipeSeed')}
        </label>

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
              {wipe
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
                  : t('pages.settings.confirmReset', { defaultValue: 'Yes, reset and open wizard' })}
              </button>
              <button type="button" className="btn btn-ghost" disabled={busy} onClick={cancelReset}>
                {t('actions.cancel', { defaultValue: 'Cancel' })}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
