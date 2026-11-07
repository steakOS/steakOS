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
import {
  setupApi,
  agentTokenApi,
  currenciesApi,
  taxCodesApi,
  meApi,
  financeOpsApi,
} from '../api/client'
import { useLocaleBootstrap } from '../hooks/useLocaleBootstrap'

const L2_WARN_DISMISS_KEY = 'steakos.btcLevel2WarnDismissed'

/**
 * System → Settings: locale, company, demos, currencies/tax, setup status.
 * Danger-zone controls live under System → Configuration.
 */
export default function SettingsPage() {
  const { t } = useTranslation('common')
  const { locale, locales, changeLocale } = useLocaleBootstrap()
  const [status, setStatus] = useState(null)
  const [check, setCheck] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
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
      setCheck(await setupApi.check())
      try {
        const me = await meApi.get()
        setIsAdmin(!!me?.isAdmin)
        setCompanies(Array.isArray(me?.companies) ? me.companies : [])
        setActiveCompanyId(me?.activeCompanyId ?? me?.activeCompany?.id ?? null)
      } catch {
        setIsAdmin(false)
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

  const pluginDepth = status?.pluginDepth || {}

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
                      'Reset Orange Steak House demo data? Live company and other demos are not touched.',
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
                      'Reset Heard & Cook Architects demo data? Live company and other demos are not touched.',
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

        <h3 className="section-label">
          {t('pages.settings.demoToysTitle', { defaultValue: '21 Toys Co.' })}
        </h3>
        <p className="muted" style={{ fontSize: 13 }}>
          {t('pages.settings.demoToysBlurb', {
            defaultValue:
              'Austin, TX — Bitcoin-themed kids toys and playsets (pure product catalogue).',
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
                  t('pages.settings.demoConfirmResetToys', {
                    defaultValue:
                      'Reset 21 Toys Co. demo data? Live company and other demos are not touched.',
                  }),
                )
              ) {
                return
              }
              setBusy(true)
              setError(null)
              setSeedMsg(null)
              try {
                const r = await setupApi.twentyOneToysReset()
                setSeedMsg(
                  r.message ||
                    t('pages.settings.demoToysResetDone', {
                      defaultValue: '21 Toys Co. reset complete',
                    }),
                )
              } catch (e) {
                setError(e.message || String(e))
              } finally {
                setBusy(false)
              }
            }}
          >
            {t('pages.settings.demoResetToys', { defaultValue: 'Reset 21 Toys Co.' })}
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
                const r = await setupApi.twentyOneToysSeed()
                setSeedMsg(
                  r.message ||
                    (r.skipped
                      ? t('pages.settings.demoToysAlready', {
                          defaultValue: '21 Toys Co. already seeded',
                        })
                      : t('pages.settings.demoToysSeeded', {
                          defaultValue: '21 Toys Co. seeded',
                        })),
                )
              } catch (e) {
                setError(e.message || String(e))
              } finally {
                setBusy(false)
              }
            }}
          >
            {t('pages.settings.demoSeedToys', { defaultValue: 'Seed 21 Toys Co. (if empty)' })}
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

      <p className="muted" style={{ marginTop: 16 }}>
        {t('pages.configuration.settingsMovedHint', {
          defaultValue:
            'Pack options, process catalogue, disabled users, and admin setup reset live under System → Configuration.',
        })}{' '}
        <Link to="/system/configuration">
          {t('nav.configuration', { defaultValue: 'Configuration' })}
        </Link>
      </p>
    </div>
  )
}
