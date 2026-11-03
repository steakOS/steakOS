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
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  setupApi,
  capabilityApi,
  agentTokenApi,
  currenciesApi,
  taxCodesApi,
  meApi,
  financeOpsApi,
} from '../api/client'
import { useLocaleBootstrap } from '../hooks/useLocaleBootstrap'
import { allowsSilverSurfaces, normalizeMetalTier } from '../utils/silverSurfaces'

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
  const [companies, setCompanies] = useState([])
  const [activeCompanyId, setActiveCompanyId] = useState(null)
  const [companyBusy, setCompanyBusy] = useState(false)
  const [companyMsg, setCompanyMsg] = useState(null)
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
  const [catalogTaxCodes, setCatalogTaxCodes] = useState([])
  const [taxInUseCodes, setTaxInUseCodes] = useState([])
  const [taxRegime, setTaxRegime] = useState('DE_VAT')
  const [defaultTaxCode, setDefaultTaxCode] = useState('')
  const [taxMsg, setTaxMsg] = useState(null)
  const [taxBusy, setTaxBusy] = useState(false)
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
  const [packMsg, setPackMsg] = useState(null)
  const [packBusy, setPackBusy] = useState(false)
  const [messagingDraft, setMessagingDraft] = useState({
    emailEnabled: false,
    telegramEnabled: false,
  })
  const [upgradeConfirmOpen, setUpgradeConfirmOpen] = useState(false)
  const [upgradeTarget, setUpgradeTarget] = useState(null)
  const [upgradeError, setUpgradeError] = useState(null)

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

  const loadTaxCodes = async () => {
    try {
      const [cat, iu] = await Promise.all([taxCodesApi.list(), taxCodesApi.inUse()])
      setCatalogTaxCodes(cat?.items || [])
      setTaxInUseCodes((iu?.items || []).map((c) => c.code).filter(Boolean))
      setTaxRegime(iu?.taxRegime || 'DE_VAT')
      setDefaultTaxCode(iu?.defaultTaxCode || '')
    } catch {
      setCatalogTaxCodes([])
      setTaxInUseCodes([])
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
      const st = await setupApi.status()
      setStatus(st)
      setMessagingDraft({
        emailEnabled: !!st?.messaging?.emailEnabled,
        telegramEnabled: !!st?.messaging?.telegramEnabled,
      })
      setCheck(await setupApi.check())
      try {
        const me = await meApi.get()
        setIsAdmin(!!me?.isAdmin)
        setIsCeo(!!me?.isCeo)
        setCompanies(Array.isArray(me?.companies) ? me.companies : [])
        setActiveCompanyId(me?.activeCompanyId ?? me?.activeCompany?.id ?? null)
      } catch {
        setIsAdmin(false)
        setIsCeo(false)
        setCompanies([])
        setActiveCompanyId(null)
      }
      try {
        const tok = await agentTokenApi.list(false)
        setAgentTokens(tok?.tokens || [])
      } catch {
        setAgentTokens([])
      }
      await loadCurrencies()
      await loadTaxCodes()
      await loadBtcLevel()
    } catch (e) {
      setError(e.message || String(e))
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

  const onCompanyChange = async (e) => {
    const id = Number(e.target.value)
    if (!id || id === Number(activeCompanyId)) return
    setCompanyBusy(true)
    setCompanyMsg(null)
    setError(null)
    try {
      const r = await meApi.selectCompany(id)
      setActiveCompanyId(r?.activeCompanyId ?? id)
      setCompanyMsg(
        t('pages.settings.companySwitched', {
          defaultValue: 'Company saved. Reloading…',
        }),
      )
      window.location.reload()
    } catch (err) {
      setError(err.message || String(err))
      setCompanyBusy(false)
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
              : ['en_US', 'en_SG', 'de_DE', 'de_AT', 'de_CH', 'es_ES'].map((code) => ({
                  code,
                  name: code,
                }))
            ).map((l) => (
              <option key={l.code} value={l.code}>
                {t(`locale.names.${l.code}`, { defaultValue: l.name || l.code })} ({l.code})
              </option>
            ))}
          </select>
        </label>
        {localeMsg && <p className="muted" style={{ marginTop: 8 }}>{localeMsg}</p>}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>
          {t('pages.settings.activeCompany', { defaultValue: 'Active company' })}
        </h2>
        <p className="page-sub">
          {t('pages.settings.activeCompanyHint', {
            defaultValue:
              'Choose which company you work in. Saved for your user and used on every login.',
          })}
        </p>
        {companies.length === 0 ? (
          <p className="muted">{t('actions.loading')}</p>
        ) : (
          <label>
            {t('pages.settings.companyField', { defaultValue: 'Company' })}
            <select
              value={activeCompanyId ?? ''}
              disabled={companyBusy || companies.length < 2}
              onChange={onCompanyChange}
              aria-label={t('pages.settings.activeCompany', {
                defaultValue: 'Active company',
              })}
            >
              {companies.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                  {c.demo || c.kind === 'DEMO'
                    ? t('shell.demoSuffix', { defaultValue: ' (Demo)' })
                    : ''}
                </option>
              ))}
            </select>
          </label>
        )}
        {companies.length === 1 && (
          <p className="muted" style={{ marginTop: 8 }}>
            {t('pages.settings.onlyOneCompany', {
              defaultValue: 'You only have access to one company.',
            })}
          </p>
        )}
        {companyMsg && (
          <p className="muted" style={{ marginTop: 8 }}>
            {companyMsg}
          </p>
        )}
      </div>

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
                    <span className="mono">{c.code}</span> —{' '}
                    {t(`currencies.names.${c.code}`, { defaultValue: c.name || c.code })}
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

      {isAdmin && (
        <div className="card" style={{ marginTop: 16 }}>
          <h2>
            {t('pages.settings.taxCodesWeUse', { defaultValue: 'Tax codes we use' })}
          </h2>
          <p className="page-sub">
            {t('pages.settings.taxCodesWeUseHint', {
              defaultValue:
                'Short list for Sales / PO tax pickers. Default code stays enabled when VAT is on. Kleinunternehmer / None suppresses VAT on sales docs.',
            })}
          </p>
          <div className="form-row-2" style={{ marginTop: 8, gap: 12 }}>
            <label>
              {t('pages.settings.taxRegime', { defaultValue: 'Tax regime' })}
              <select
                value={taxRegime || 'DE_VAT'}
                disabled={taxBusy}
                onChange={(e) => setTaxRegime(e.target.value)}
              >
                <option value="DE_VAT">
                  {t('pages.settings.taxRegimeDe', { defaultValue: 'Germany VAT' })}
                </option>
                <option value="AT_VAT">
                  {t('pages.settings.taxRegimeAt', { defaultValue: 'Austria VAT' })}
                </option>
                <option value="US_SALES">
                  {t('pages.settings.taxRegimeUs', { defaultValue: 'US sales tax (stub)' })}
                </option>
                <option value="DE_SMALL_BUSINESS">
                  {t('pages.settings.taxRegimeKlein', {
                    defaultValue: 'Kleinunternehmer / small business (no VAT)',
                  })}
                </option>
                <option value="NONE">
                  {t('pages.settings.taxRegimeNone', {
                    defaultValue: 'None / not registered',
                  })}
                </option>
              </select>
            </label>
            <label>
              {t('pages.settings.defaultTaxCode', { defaultValue: 'Default tax code' })}
              <select
                value={defaultTaxCode || ''}
                disabled={
                  taxBusy ||
                  taxRegime === 'NONE' ||
                  taxRegime === 'DE_SMALL_BUSINESS'
                }
                onChange={(e) => setDefaultTaxCode(e.target.value)}
              >
                <option value="">
                  {t('pages.settings.defaultTaxCodeNone', {
                    defaultValue: '— none —',
                  })}
                </option>
                {catalogTaxCodes.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.label || `${c.code} — ${c.name} (${c.ratePercent ?? 0}%)`}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {taxMsg && <p className="muted">{taxMsg}</p>}
          {taxRegime !== 'NONE' && taxRegime !== 'DE_SMALL_BUSINESS' && (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
                gap: 8,
                marginTop: 8,
              }}
            >
              {catalogTaxCodes.map((c) => {
                const checked = taxInUseCodes.includes(c.code)
                const locked = c.code === defaultTaxCode
                return (
                  <label
                    key={c.code}
                    className="checkbox-label"
                    style={{ alignItems: 'flex-start' }}
                  >
                    <input
                      type="checkbox"
                      checked={checked || locked}
                      disabled={taxBusy || locked}
                      onChange={(e) => {
                        const on = e.target.checked
                        setTaxInUseCodes((prev) => {
                          const set = new Set(prev)
                          if (on) set.add(c.code)
                          else set.delete(c.code)
                          if (defaultTaxCode) set.add(defaultTaxCode)
                          return [...set]
                        })
                      }}
                    />
                    <span>
                      <span className="mono">{c.code}</span> — {c.name}{' '}
                      <span className="muted">({c.ratePercent ?? 0}%)</span>
                      {locked
                        ? t('pages.settings.defaultLockSuffix', {
                            defaultValue: ' (default)',
                          })
                        : ''}
                    </span>
                  </label>
                )
              })}
            </div>
          )}
          <button
            type="button"
            className="btn btn-primary"
            style={{ marginTop: 12 }}
            disabled={taxBusy}
            onClick={async () => {
              setTaxBusy(true)
              setTaxMsg(null)
              setError(null)
              try {
                const suppress =
                  taxRegime === 'NONE' || taxRegime === 'DE_SMALL_BUSINESS'
                const codes = suppress
                  ? []
                  : [...new Set([...taxInUseCodes, defaultTaxCode].filter(Boolean))]
                const r = await taxCodesApi.setInUse({
                  codes,
                  taxRegime,
                  defaultTaxCode: suppress ? null : defaultTaxCode || null,
                })
                setTaxInUseCodes((r?.items || []).map((c) => c.code).filter(Boolean))
                setTaxRegime(r?.taxRegime || taxRegime)
                setDefaultTaxCode(r?.defaultTaxCode || '')
                setTaxMsg(
                  t('pages.settings.taxCodesSaved', {
                    defaultValue: 'Tax codes we use saved.',
                  }),
                )
              } catch (e) {
                setError(e.message || String(e))
              } finally {
                setTaxBusy(false)
              }
            }}
          >
            {taxBusy
              ? '…'
              : t('pages.settings.saveTaxCodes', { defaultValue: 'Save tax codes' })}
          </button>
        </div>
      )}

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
        {status?.pluginDepth && (
          <dl className="setup-summary" style={{ marginTop: 12 }}>
            <dt>{t('pages.settings.pluginDepth', { defaultValue: 'Plugin depth' })}</dt>
            <dd>
              {t('pages.settings.depthSales', { defaultValue: 'Sales' })}:{' '}
              <span className="badge badge-ok">{pluginDepth.sales || '—'}</span>
              {' · '}
              {t('pages.settings.depthFinance', { defaultValue: 'Finance' })}:{' '}
              <span className="badge badge-ok">{pluginDepth.finance || '—'}</span>
            </dd>
            <dt>{t('pages.settings.messagingLabel', { defaultValue: 'Messaging' })}</dt>
            <dd>
              {t('pages.settings.emailShort', { defaultValue: 'Email' })}:{' '}
              {status.messaging?.emailEnabled
                ? t('pages.settings.on', { defaultValue: 'On' })
                : t('pages.settings.off', { defaultValue: 'Off' })}
              {' · '}
              {t('pages.settings.telegramShort', { defaultValue: 'Telegram' })}:{' '}
              {status.messaging?.telegramEnabled
                ? t('pages.settings.on', { defaultValue: 'On' })
                : t('pages.settings.off', { defaultValue: 'Off' })}
            </dd>
          </dl>
        )}
      </div>

      {isAdmin && (
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
      )}

      {isAdmin && isSilverTenant && (
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
