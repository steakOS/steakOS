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
import { setupApi, versionApi } from '../api/client'

function fmtNum(n) {
  if (n == null || n === '') return '—'
  const x = Number(n)
  if (Number.isNaN(x)) return String(n)
  return x.toLocaleString()
}

function fmtBytes(n, humanFallback) {
  if (humanFallback) return humanFallback
  if (n == null || n === '') return '—'
  const x = Number(n)
  if (Number.isNaN(x) || x < 0) return '—'
  if (x < 1024) return `${x} B`
  if (x < 1024 * 1024) return `${(x / 1024).toFixed(1)} KB`
  if (x < 1024 * 1024 * 1024) return `${(x / (1024 * 1024)).toFixed(1)} MB`
  return `${(x / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

/**
 * About — product version surface (VERSIONS.md V7) + template tier + system vitals.
 */
export default function About() {
  const { t } = useTranslation('common')
  const [data, setData] = useState(null)
  const [setup, setSetup] = useState(null)
  const [profile, setProfile] = useState(null)
  const [stats, setStats] = useState(null)
  const [statsError, setStatsError] = useState(null)
  const [health, setHealth] = useState(null)
  const [ready, setReady] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const [v, s, stResult, prof, healthRes, readyRes] = await Promise.all([
          versionApi.get(),
          setupApi.status().catch(() => null),
          versionApi.stats().then(
            (st) => ({ ok: true, st }),
            (err) => ({ ok: false, err })
          ),
          setupApi.profile().catch(() => null),
          fetch('/api/health', { credentials: 'same-origin' })
            .then((r) => r.json())
            .catch(() => null),
          fetch('/api/health/ready', { credentials: 'same-origin' })
            .then((r) => r.json())
            .catch(() => null),
        ])
        if (!cancelled) {
          setData(v)
          setSetup(s)
          setProfile(prof)
          setHealth(healthRes)
          setReady(readyRes)
          if (stResult.ok) {
            setStats(stResult.st)
            setStatsError(null)
          } else {
            setStats(null)
            setStatsError(stResult.err?.message || 'Failed to load system vitals')
          }
          setError(null)
        }
      } catch (e) {
        if (!cancelled) {
          setError(e.message || 'Failed to load version')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const semver = data?.softwareVersion || ''
  const tier = setup?.templateTier
  const tierLabel = tier
    ? tier.charAt(0).toUpperCase() + tier.slice(1).toLowerCase()
    : null
  const tenantName =
    profile?.legalEntity?.name ||
    t('pages.company.unnamed', { defaultValue: 'Company not named yet' })

  // Trunk policy: all cuts are golden — no historical GOLDEN-0.1.x footnotes.
  const milestoneBadge = t('pages.about.golden', { defaultValue: 'Golden' })
  const milestoneNote = t('pages.about.goldenTrunkNote', {
    version: data?.V || (semver ? `V${semver}` : ''),
    defaultValue:
      '{{version}} — golden trunk. Observability L1–L2: public /api/health + ready, Prometheus /api/ops/metrics, Uptime Kuma + Grafana on Umbrel.',
  })

  const obs = data?.observability || {}
  const grafanaUrl = obs.grafanaUrl || 'http://umbrel.local:3030'
  const kumaUrl = obs.uptimeKumaUrl || 'http://umbrel.local:8385'
  const overviewUrl = `${String(grafanaUrl).replace(/\/$/, '')}${obs.overviewPath || '/d/steakos-overview/steakos-overview'}`
  const statusBadge = (status) => {
    if (!status) return 'badge'
    if (status === 'UP') return 'badge badge-ok'
    if (status === 'DEGRADED') return 'badge badge-draft'
    return 'badge badge-error'
  }

  return (
    <div className="page about-page">
      <header className="page-header">
        <h1>{t('pages.about.title')}</h1>
        <p className="page-sub">{t('pages.about.subtitle')}</p>
        {(tier === 'IRON' || !tier) && (
          <p className="muted" style={{ maxWidth: 42 + 'rem' }}>
            {t('pages.about.ironNote', {
              defaultValue:
                'Iron is the alpha / MVP platform demo of the monorepo — not a commercial plugin pack. Copper+ tiers appear for orientation only until productized.',
            })}
          </p>
        )}
      </header>

      {loading && <p className="muted">{t('actions.loading')}</p>}
      {error && <div className="banner-error">{error}</div>}

      {data && (
        <>
          <div className="card about-card">
            <div className="about-hero">
              <div className="about-display">{data.displayLabel}</div>
              <div className="about-stamp mono">{data.fullStamp}</div>
              {tierLabel && (
                <div className="about-milestone">
                  <span className="badge badge-ok">{t('pages.about.template')}</span>
                  <span>
                    {tierLabel}
                    {setup?.setupComplete ? ` · ${t('pages.about.setupComplete')}` : ` · ${t('pages.about.setupIncomplete')}`}
                  </span>
                </div>
              )}
              {milestoneBadge && milestoneNote && (
                <div className="about-milestone">
                  <span className="badge badge-ok">{milestoneBadge}</span>
                  <span>{milestoneNote}</span>
                </div>
              )}
            </div>

            <dl className="about-grid">
              <div>
                <dt>{t('pages.about.productLine')}</dt>
                <dd>{data.productLine}</dd>
              </div>
              <div>
                <dt>{t('pages.about.releaseTrain')}</dt>
                <dd>{data.R}</dd>
              </div>
              <div>
                <dt>{t('pages.about.softwareV')}</dt>
                <dd>{data.V}</dd>
              </div>
              <div>
                <dt>{t('pages.about.semver')}</dt>
                <dd className="mono">{data.softwareVersion}</dd>
              </div>
              <div>
                <dt>{t('pages.about.templateTier')}</dt>
                <dd>{tierLabel || '—'}</dd>
              </div>
              <div>
                <dt>{t('pages.about.buildId')}</dt>
                <dd className="mono">{data.buildId}</dd>
              </div>
              <div>
                <dt>{t('pages.about.dockerTag')}</dt>
                <dd className="mono">{data.dockerTag}</dd>
              </div>
            </dl>

            <p className="muted about-note">{t('pages.about.semverNote')}</p>
          </div>

          {/* P1: tenant teaser — product About stays version-first */}
          <div className="card about-card" style={{ marginTop: '1rem' }}>
            <p style={{ margin: 0 }}>
              {t('pages.about.tenantTeaser', {
                name: tenantName,
                defaultValue: 'Tenant: {{name}}',
              })}{' '}
              <Link to="/company">{t('pages.about.viewCompanyProfile', { defaultValue: 'View company profile →' })}</Link>
            </p>
          </div>

          <div className="card about-card" style={{ marginTop: '1rem' }}>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('pages.about.licenseTitle', { defaultValue: 'License' })}
            </h2>
            <p style={{ marginTop: 0 }}>
              {t('pages.about.licenseBody', {
                defaultValue:
                  'steakOS core is free and open source software under the Apache License, Version 2.0. Copyright 2026 The steakOS Authors.',
              })}
            </p>
            <p className="muted about-note" style={{ marginBottom: 0 }}>
              {t('pages.about.licenseNote', {
                defaultValue:
                  'See LICENSE and NOTICE in the project repository. Optional proprietary extensions may use separate terms.',
              })}
            </p>
          </div>

          {(stats?.vitals || statsError) && (
            <div className="card about-card about-stats" style={{ marginTop: '1rem' }}>
              <h2 className="section-label" style={{ marginTop: 0 }}>
                {t('pages.about.statsTitle')}
              </h2>
              {statsError && (
                <div className="banner-error" style={{ marginBottom: '0.75rem' }}>
                  {t('pages.about.statsError', { defaultValue: 'Could not load system vitals' })}
                  : {statsError}
                </div>
              )}
              {stats?.vitals && (
              <>
              <p className="muted about-note" style={{ marginTop: 0 }}>
                {t('pages.about.statsIntro')}
              </p>
              <dl className="about-grid about-stats-grid">
                <div>
                  <dt>{t('pages.about.stats.domains')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.domains)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.services')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.services)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.controllers')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.controllers)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.dbTables')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.dbTables)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.apiEndpoints')}</dt>
                  <dd className="mono">~{fmtNum(stats.vitals.apiEndpointsApprox)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.processes')}</dt>
                  <dd className="mono">
                    {fmtNum(stats.vitals.processesSystem)}
                    <span className="muted"> / {fmtNum(stats.vitals.processSteps)} {t('pages.about.stats.steps')}</span>
                  </dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.processAreas')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.processAreas)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.helpTopics')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.helpTopics)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.catalogue')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.catalogueItems)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.openTasks')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.openTasks)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.demoCompanies')}</dt>
                  <dd className="mono">
                    {fmtNum(stats.vitals.demoCompanies)}
                    {Array.isArray(stats.vitals.demoCompanyNames) && stats.vitals.demoCompanyNames.length > 0 ? (
                      <span className="muted" style={{ display: 'block', fontSize: '0.85em', fontWeight: 400, marginTop: 4 }}>
                        {stats.vitals.demoCompanyNames.join(' · ')}
                      </span>
                    ) : null}
                  </dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.diskIncl')}</dt>
                  <dd className="mono" title={fmtNum(stats.vitals.diskBytesInclDocsDemos) + ' B'}>
                    {fmtBytes(stats.vitals.diskBytesInclDocsDemos, stats.vitals.diskHumanInclDocsDemos)}
                    <span className="muted" style={{ display: 'block', fontSize: '0.85em', fontWeight: 400, marginTop: 4 }}>
                      {t('pages.about.stats.diskBreakdown', {
                        docs: fmtBytes(stats.vitals.docsBytes, stats.vitals.docsHuman),
                        demos: fmtBytes(stats.vitals.demosBytes, stats.vitals.demosHuman),
                        defaultValue: 'docs {{docs}} · demos {{demos}}',
                      })}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.diskExcl')}</dt>
                  <dd className="mono" title={fmtNum(stats.vitals.diskBytesExclDocsDemos) + ' B'}>
                    {fmtBytes(stats.vitals.diskBytesExclDocsDemos, stats.vitals.diskHumanExclDocsDemos)}
                  </dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.docs')}</dt>
                  <dd className="mono">
                    {fmtNum(stats.vitals.docsFiles)} {t('pages.about.stats.files')}
                    <span className="muted"> · ~{fmtNum(stats.vitals.docsPagesApprox)} {t('pages.about.stats.pages')}</span>
                  </dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.docsChars')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.docsChars)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.adrs')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.adrs)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.unitTests')}</dt>
                  <dd className="mono">~{fmtNum(stats.vitals.unitTestsApprox)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.httpE2e')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.httpE2e)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.codeLoc')}</dt>
                  <dd className="mono">~{fmtNum(stats.vitals.codeLocApprox)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.pluginsInstalled')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.pluginsInstalled)}</dd>
                </div>
                <div>
                  <dt>{t('pages.about.stats.userStories')}</dt>
                  <dd className="mono">{fmtNum(stats.vitals.userStories)}</dd>
                </div>
              </dl>
              <p className="muted about-note" style={{ marginBottom: 0 }}>
                {t('pages.about.statsArch', {
                  arch: stats.vitals.architecture || stats.inventory?.architecture || '',
                })}
                {stats.inventory?.asOf
                  ? ` · ${t('pages.about.statsInventoryAsOf', { date: stats.inventory.asOf })}`
                  : null}
              </p>
              </>
              )}
            </div>
          )}

          <div className="card about-card" style={{ marginTop: '1rem' }}>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('pages.about.observabilityTitle', { defaultValue: 'Observability' })}
            </h2>
            <p className="muted" style={{ marginTop: 0 }}>
              {t('pages.about.observabilityIntro', {
                defaultValue:
                  'Local-first ops: health probes for synthetics, Prometheus metrics on this API, dashboards and uptime on Umbrel.',
              })}
            </p>
            <dl className="about-grid">
              <div>
                <dt>{t('pages.about.healthLive', { defaultValue: 'Liveness' })}</dt>
                <dd>
                  <span className={statusBadge(health?.status)}>{health?.status || '—'}</span>
                  <span className="muted mono" style={{ marginLeft: 8 }}>
                    /api/health
                  </span>
                </dd>
              </div>
              <div>
                <dt>{t('pages.about.healthReady', { defaultValue: 'Readiness' })}</dt>
                <dd>
                  <span className={statusBadge(ready?.status)}>{ready?.status || '—'}</span>
                  <span className="muted mono" style={{ marginLeft: 8 }}>
                    /api/health/ready
                  </span>
                </dd>
              </div>
              <div>
                <dt>{t('pages.about.metricsPath', { defaultValue: 'Metrics' })}</dt>
                <dd className="mono">{obs.metricsPath || '/api/ops/metrics'}</dd>
              </div>
            </dl>
            <ul className="inv-list">
              <li>
                <a href={overviewUrl} target="_blank" rel="noopener noreferrer">
                  {t('pages.about.openGrafana', { defaultValue: 'Open Grafana · steakOS Overview' })}
                </a>
              </li>
              <li>
                <a href={kumaUrl} target="_blank" rel="noopener noreferrer">
                  {t('pages.about.openKuma', { defaultValue: 'Open Uptime Kuma' })}
                </a>
              </li>
              <li>
                <a
                  href={obs.metricsPath || '/api/ops/metrics'}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {t('pages.about.openMetrics', { defaultValue: 'Prometheus text (/api/ops/metrics)' })}
                </a>
              </li>
            </ul>
          </div>

          <div className="card about-card about-links" style={{ marginTop: '1rem' }}>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('pages.about.thisBuild')}
            </h2>
            <ul className="inv-list">
              <li>
                <Link to="/company">{t('nav.company')}</Link>
              </li>
              <li>
                <Link to="/settings">{t('pages.about.settingsLink')}</Link>
              </li>
              <li>
                <Link to="/finance/transactions">{t('features.generalLedger')}</Link>
              </li>
              <li>
                <Link to="/finance/invoices">{t('features.invoices')}</Link>
              </li>
              <li>
                <Link to="/finance/treasury">{t('pages.about.treasuryHub')}</Link>
              </li>
              <li>
                <a
                  href={`mailto:${t('pages.about.securityContact', {
                    defaultValue: 'security@steakos.org',
                  })}`}
                >
                  {t('pages.about.securityReport', {
                    defaultValue: 'Report a security issue',
                  })}
                </a>
                <span className="muted">
                  {' '}
                  ({t('pages.about.securityContact', { defaultValue: 'security@steakos.org' })})
                </span>
              </li>
            </ul>
          </div>
        </>
      )}
    </div>
  )
}
