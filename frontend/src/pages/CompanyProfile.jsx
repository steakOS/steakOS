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

import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Copy, Check, ExternalLink } from 'lucide-react'
import { setupApi } from '../api/client'

function dash(v) {
  if (v === null || v === undefined || v === '') return '—'
  if (typeof v === 'boolean') return v ? 'yes' : 'no'
  return String(v)
}

function formatTier(tier) {
  if (!tier) return '—'
  const s = String(tier)
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase()
}

/** P1: human labels for template family codes */
function familyLabel(t, code) {
  if (!code) return '—'
  const key = String(code).toLowerCase()
  return t(`pages.company.families.${key}`, {
    defaultValue: t(`groups.${key}`, { defaultValue: code }),
  })
}

function Field({ label, value, mono, copyable, onCopy, copied }) {
  const show = dash(value)
  const canCopy = copyable && value != null && value !== ''
  return (
    <div>
      <dt>{label}</dt>
      <dd className={mono ? 'mono' : undefined} style={canCopy ? { display: 'flex', alignItems: 'center', gap: 8 } : undefined}>
        <span title={canCopy ? String(value) : undefined}>{show}</span>
        {canCopy && (
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => onCopy?.(String(value))}
            aria-label={copied ? 'Copied' : 'Copy'}
            style={{ padding: '0.15rem 0.4rem', flexShrink: 0 }}
          >
            {copied ? <Check size={14} /> : <Copy size={14} />}
          </button>
        )}
      </dd>
    </div>
  )
}

function SectionEditLink({ to, label }) {
  if (!to) return null
  return (
    <p style={{ margin: '0.75rem 0 0' }}>
      <Link to={to} className="btn btn-ghost btn-sm" style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
        <ExternalLink size={14} /> {label}
      </Link>
    </p>
  )
}

/**
 * System → Company profile — read-only tenant config from setup.
 * P0 display · P1 polish · P2 edit bridge
 * Concept: docs/services/steakos app/setup/TENANT-PROFILE-DISPLAY-CONCEPT.md
 */
export default function CompanyProfile() {
  const { t } = useTranslation('common')
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [copiedKey, setCopiedKey] = useState(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const p = await setupApi.profile()
        if (!cancelled) {
          setData(p)
          setError(null)
        }
      } catch (e) {
        if (!cancelled) setError(e.message || String(e))
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const copyValue = useCallback(async (key, text) => {
    try {
      await navigator.clipboard.writeText(text)
      setCopiedKey(key)
      window.setTimeout(() => setCopiedKey((k) => (k === key ? null : k)), 2000)
    } catch {
      /* ignore clipboard failures */
    }
  }, [])

  const setup = data?.setup
  const le = data?.legalEntity
  const sites = data?.sites || []
  const banks = data?.bankAccounts || []
  const template = data?.template
  const finance = data?.finance
  const prefs = data?.preferences
  const incomplete = setup && !setup.setupComplete
  const companyTitle = le?.name || t('pages.company.unnamed')
  const editLegal = le?.id
    ? `/domains/configuration/LegalEntity/${le.id}`
    : '/domains/configuration/LegalEntity'
  const editLabel = t('pages.company.editInCatalog', { defaultValue: 'Edit in catalog' })
  const editSettings = t('pages.company.editInSettings', { defaultValue: 'Edit in Settings' })

  return (
    <div className="page about-page">
      <header className="page-header">
        <h1>{t('pages.company.title')}</h1>
        <p className="page-sub">{t('pages.company.subtitle')}</p>
      </header>

      {loading && <p className="muted">{t('actions.loading')}</p>}
      {error && <div className="banner-error">{error}</div>}

      {data && (
        <>
          {incomplete && (
            <div className="banner-error" style={{ marginBottom: '1rem' }}>
              {t('pages.company.incompleteBanner')}{' '}
              <Link to="/setup">{t('pages.company.openSetup')}</Link>
            </div>
          )}

          <div className="card about-card">
            <div className="about-hero">
              <div className="about-display">{companyTitle}</div>
              <div className="about-milestone">
                <span className="badge badge-ok">{t('pages.company.template')}</span>
                <span>
                  {formatTier(template?.tier || setup?.templateTier)}
                  {setup?.setupComplete
                    ? ` · ${t('pages.company.setupComplete')}`
                    : ` · ${t('pages.company.setupIncomplete')}`}
                </span>
              </div>
              {setup?.completedAt && (
                <p className="muted" style={{ marginTop: '0.5rem', marginBottom: 0 }}>
                  {t('pages.company.completedMeta', {
                    when: new Date(setup.completedAt).toLocaleString(),
                    who: setup.completedBy || '—',
                  })}
                </p>
              )}
            </div>
          </div>

          <div className="card about-card" style={{ marginTop: '1rem' }}>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('pages.company.sectionLegal')}
            </h2>
            {!le ? (
              <p className="muted">{t('pages.company.notConfigured')}</p>
            ) : (
              <dl className="about-grid">
                <Field label={t('pages.company.name')} value={le.name} />
                <Field label={t('pages.company.legalForm')} value={le.legalForm} />
                <Field
                  label={t('pages.company.taxId')}
                  value={le.taxId}
                  mono
                  copyable
                  copied={copiedKey === 'taxId'}
                  onCopy={(v) => copyValue('taxId', v)}
                />
                <Field label={t('pages.company.country')} value={le.countryCode} />
                <Field label={t('pages.company.currency')} value={le.functionalCurrency} />
                <Field label={t('pages.company.address')} value={le.registeredAddressText} />
                <Field label={t('pages.company.description')} value={le.description} />
              </dl>
            )}
            <SectionEditLink to={editLegal} label={editLabel} />
          </div>

          <div className="card about-card" style={{ marginTop: '1rem' }}>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('pages.company.sectionSites')}
            </h2>
            {sites.length === 0 ? (
              <p className="muted">{t('pages.company.notConfigured')}</p>
            ) : (
              <div className="inv-list" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                {sites.map((site) => (
                  <div key={site.id || site.name} className="card" style={{ margin: 0, padding: '0.75rem 1rem' }}>
                    <strong>{site.name}</strong>
                    <span className="muted" style={{ marginLeft: 8 }}>
                      {site.siteType}
                      {site.active === false ? ` · ${t('pages.company.inactive')}` : ''}
                    </span>
                    {site.addressText && (
                      <div className="muted" style={{ marginTop: 4 }}>
                        {site.addressText}
                      </div>
                    )}
                    {site.id && (
                      <div style={{ marginTop: 6 }}>
                        <Link
                          to={`/domains/configuration/Site/${site.id}`}
                          className="btn btn-ghost btn-sm"
                          style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
                        >
                          <ExternalLink size={12} /> {editLabel}
                        </Link>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
            <SectionEditLink to="/domains/configuration/Site" label={t('pages.company.manageSites', { defaultValue: 'Manage sites' })} />
          </div>

          <div className="card about-card" style={{ marginTop: '1rem' }}>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('pages.company.sectionBanking')}
            </h2>
            {banks.length === 0 ? (
              <p className="muted">{t('pages.company.notConfigured')}</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                {banks.map((ba) => (
                  <div key={ba.id || ba.name} className="card" style={{ margin: 0, padding: '0.75rem 1rem' }}>
                    <strong>{ba.name}</strong>
                    <span className="muted" style={{ marginLeft: 8 }}>
                      {ba.accountKind}
                      {ba.paymentScheme ? ` · ${ba.paymentScheme}` : ''}
                      {ba.currencyCode ? ` · ${ba.currencyCode}` : ''}
                      {ba.active === false ? ` · ${t('pages.company.inactive')}` : ''}
                    </span>
                    <dl className="about-grid" style={{ marginTop: '0.5rem' }}>
                      {(ba.paymentScheme || 'SEPA') === 'ACH' ? (
                        <>
                          <Field
                            label={t('pages.company.branch', { defaultValue: 'Branch' })}
                            value={ba.branchName}
                          />
                          <Field
                            label={t('pages.company.accountNumber', {
                              defaultValue: 'Account number',
                            })}
                            value={ba.accountNumber}
                            mono
                            copyable
                            copied={copiedKey === `acct-${ba.id}`}
                            onCopy={(v) => copyValue(`acct-${ba.id}`, v)}
                          />
                          <Field
                            label={t('pages.company.routingNumber', {
                              defaultValue: 'Routing number',
                            })}
                            value={ba.routingNumber}
                            mono
                            copyable
                            copied={copiedKey === `rtn-${ba.id}`}
                            onCopy={(v) => copyValue(`rtn-${ba.id}`, v)}
                          />
                        </>
                      ) : (
                        <>
                          <Field
                            label={t('pages.company.iban')}
                            value={ba.iban}
                            mono
                            copyable
                            copied={copiedKey === `iban-${ba.id}`}
                            onCopy={(v) => copyValue(`iban-${ba.id}`, v)}
                          />
                          <Field
                            label={t('pages.company.bic')}
                            value={ba.bic}
                            mono
                            copyable
                            copied={copiedKey === `bic-${ba.id}`}
                            onCopy={(v) => copyValue(`bic-${ba.id}`, v)}
                          />
                        </>
                      )}
                      <Field label={t('pages.company.externalRef')} value={ba.externalRef} mono />
                    </dl>
                    {ba.id && (
                      <div style={{ marginTop: 6 }}>
                        <Link
                          to={`/domains/configuration/BankAccount/${ba.id}`}
                          className="btn btn-ghost btn-sm"
                          style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
                        >
                          <ExternalLink size={12} /> {editLabel}
                        </Link>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
            <SectionEditLink
              to="/domains/configuration/BankAccount"
              label={t('pages.company.manageBanking', { defaultValue: 'Manage bank accounts' })}
            />
          </div>

          <div className="card about-card" style={{ marginTop: '1rem' }}>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('pages.company.sectionTemplate')}
            </h2>
            <dl className="about-grid">
              <Field
                label={t('pages.company.templateTier')}
                value={formatTier(template?.tier || setup?.templateTier)}
              />
              <div>
                <dt>{t('pages.company.enabledFamilies')}</dt>
                <dd>
                  {(template?.enabledFamilies || []).length === 0 ? (
                    '—'
                  ) : (
                    <span className="about-milestone" style={{ flexWrap: 'wrap', gap: 6, display: 'flex' }}>
                      {(template.enabledFamilies || []).map((f) => (
                        <span key={f} className="badge badge-ok" title={f}>
                          {familyLabel(t, f)}
                        </span>
                      ))}
                    </span>
                  )}
                </dd>
              </div>
              <Field label={t('pages.company.wizardVersion')} value={setup?.wizardVersion} />
            </dl>
            <SectionEditLink to="/settings" label={editSettings} />
            {setup?.setupComplete && (
              <p className="muted" style={{ fontSize: '0.85rem', marginTop: '0.5rem', marginBottom: 0 }}>
                {t('pages.company.reopenSetupHint', {
                  defaultValue: 'To change template or re-run the wizard, use Settings → reopen / reset setup.',
                })}
              </p>
            )}
          </div>

          <div className="card about-card" style={{ marginTop: '1rem' }}>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('pages.company.sectionFinance')}
            </h2>
            <dl className="about-grid">
              <Field
                label={t('pages.company.primaryRail')}
                value={finance?.primaryValueRail || finance?.functionalCurrency}
              />
              <Field label={t('pages.company.currency')} value={finance?.functionalCurrency} />
              {finance?.primaryValueRail === 'BTC' && (
                <>
                  <Field label={t('pages.company.bitcoinUnit')} value={finance?.bitcoinDisplayUnit} />
                  <Field label={t('pages.company.displayFiat')} value={finance?.displayFiat} />
                </>
              )}
              <Field label={t('pages.company.defaultTaxRate')} value={finance?.defaultTaxRate} />
              <Field
                label={t('pages.company.taxRegime', { defaultValue: 'Tax regime' })}
                value={finance?.taxRegime}
              />
              <Field
                label={t('pages.company.defaultTaxCode', { defaultValue: 'Default tax code' })}
                value={finance?.defaultTaxCode}
              />
            </dl>
            <SectionEditLink to="/settings" label={editSettings} />
            <SectionEditLink
              to="/finance/transactions"
              label={t('pages.company.openLedger', { defaultValue: 'Open general ledger' })}
            />
          </div>

          <div className="card about-card" style={{ marginTop: '1rem' }}>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('pages.company.sectionLocale')}
            </h2>
            <dl className="about-grid">
              <Field label={t('pages.company.locale')} value={prefs?.locale} />
            </dl>
            <SectionEditLink to="/settings" label={editSettings} />
          </div>

          <div className="card about-card about-links" style={{ marginTop: '1rem' }}>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('pages.company.related')}
            </h2>
            <ul className="inv-list">
              <li>
                <Link to="/about">{t('nav.about')}</Link>
                <span className="muted"> — {t('pages.company.relatedAbout')}</span>
              </li>
              <li>
                <Link to="/settings">{t('nav.settings')}</Link>
                <span className="muted"> — {t('pages.company.relatedSettings')}</span>
              </li>
              <li>
                <Link to="/system/catalogs">{t('nav.catalogs')}</Link>
                <span className="muted">
                  {' '}
                  — {t('pages.company.relatedCatalogs', { defaultValue: 'power-user domain lists' })}
                </span>
              </li>
            </ul>
          </div>
        </>
      )}
    </div>
  )
}
