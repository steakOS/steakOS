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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import * as d3 from 'd3'
import { BarChart3, Lock } from 'lucide-react'
import { kpiApi } from '../api/client'

const AREA_ORDER = ['sales', 'delivery', 'stock', 'finance', 'people', 'quality', 'work']

const AGING_KEYS = ['CURRENT', '1-30', '31-60', '61+']
const AGING_COLORS = {
  CURRENT: '#22c55e',
  '1-30': '#eab308',
  '31-60': '#f97316',
  '61+': '#dc2626',
}

function formatRatio(ratio) {
  if (ratio == null || Number.isNaN(Number(ratio))) return '—'
  return `${(Number(ratio) * 100).toFixed(1)}%`
}

function formatMoney(amount) {
  if (amount == null || amount === '') return '—'
  const n = Number(amount)
  if (Number.isNaN(n)) return String(amount)
  return n.toLocaleString(undefined, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })
}

function formatCount(count) {
  if (count == null) return '—'
  return Number(count).toLocaleString()
}

function formatPrimaryValue(value, unit) {
  if (!value) return '—'
  const u = String(unit || value.unit || '').toLowerCase()
  if (value.status === 'insufficient' || value.status === 'deferred' || value.status === 'planned') {
    return '—'
  }
  if (u === 'ratio' || value.ratio != null) return formatRatio(value.ratio)
  if (u === 'money' || (value.amount != null && value.ratio == null && u !== 'count' && u !== 'hours')) {
    if (value.amount != null) return formatMoney(value.amount)
  }
  if (u === 'hours' && value.amount != null) {
    return `${Number(value.amount).toLocaleString(undefined, { maximumFractionDigits: 1 })} h`
  }
  if (u === 'months' && value.amount != null) {
    return `${Number(value.amount).toLocaleString(undefined, { maximumFractionDigits: 1 })} mo`
  }
  if (value.count != null) return formatCount(value.count)
  if (value.amount != null) return formatMoney(value.amount)
  return '—'
}

function statusLabel(status, t) {
  const s = String(status || '').toLowerCase()
  if (s === 'ready') return t('analytics.statusReady', { defaultValue: 'Ready' })
  if (s === 'insufficient') {
    return t('analytics.statusInsufficient', { defaultValue: 'Not enough data yet' })
  }
  if (s === 'deferred') return t('analytics.statusDeferred', { defaultValue: 'Deferred' })
  if (s === 'planned') return t('analytics.statusPlanned', { defaultValue: 'Planned' })
  return status || '—'
}

function ArAgingBars({ buckets = {}, height = 120 }) {
  const ref = useRef(null)
  const bucketKey = AGING_KEYS.map((k) => `${k}:${buckets[k] ?? 0}`).join('|')

  useEffect(() => {
    const el = ref.current
    if (!el) return
    el.innerHTML = ''
    const amounts = AGING_KEYS.map((k) => Number(buckets[k] || 0))
    const width = el.clientWidth || 360
    const margin = { top: 8, right: 8, bottom: 28, left: 8 }
    const innerW = Math.max(40, width - margin.left - margin.right)
    const innerH = height - margin.top - margin.bottom

    const svg = d3
      .select(el)
      .append('svg')
      .attr('width', width)
      .attr('height', height)
      .attr('role', 'img')
      .attr('aria-label', 'AR aging buckets')

    const g = svg.append('g').attr('transform', `translate(${margin.left},${margin.top})`)
    const x = d3.scaleBand().domain(AGING_KEYS).range([0, innerW]).padding(0.2)
    const maxY = Math.max(d3.max(amounts) || 0, 1)
    const y = d3.scaleLinear().domain([0, maxY]).nice().range([innerH, 0])

    g.selectAll('rect')
      .data(AGING_KEYS)
      .enter()
      .append('rect')
      .attr('x', (d) => x(d))
      .attr('y', (d) => y(Number(buckets[d] || 0)))
      .attr('width', x.bandwidth())
      .attr('height', (d) => innerH - y(Number(buckets[d] || 0)))
      .attr('fill', (d) => AGING_COLORS[d])
      .attr('rx', 3)

    g.append('g')
      .attr('transform', `translate(0,${innerH})`)
      .call(d3.axisBottom(x).tickSize(0))
      .selectAll('text')
      .attr('font-size', 11)
      .attr('fill', '#57534e')

    g.select('.domain').attr('stroke', '#e7e5e4')
  }, [bucketKey, buckets, height])

  return <div className="analytics-aging-chart" ref={ref} style={{ width: '100%', minHeight: height }} />
}

function HeroTile({ tile, onSelect }) {
  const { t } = useTranslation(['desks', 'common'])
  const value = tile.value || {}
  const status = value.status || tile.status
  const primary = formatPrimaryValue(value, tile.unit)
  const insufficient = status === 'insufficient'
  const deferred = status === 'deferred' || tile.deferred

  return (
    <button
      type="button"
      className={`analytics-hero-tile card ${insufficient || deferred ? 'is-muted' : ''}`}
      onClick={() => onSelect?.(tile.id)}
    >
      <div className="analytics-hero-id">{tile.id}</div>
      <div className="analytics-hero-name">{tile.name}</div>
      <div className="analytics-hero-value">{primary}</div>
      <div className="muted analytics-hero-status">{statusLabel(status, t)}</div>
      {value.message && (
        <div className="muted analytics-hero-msg" title={value.message}>
          {value.message}
        </div>
      )}
    </button>
  )
}

function CatalogueRow({ item, selected, onSelect }) {
  const { t } = useTranslation(['desks'])

  return (
    <tr
      className={selected ? 'is-selected' : ''}
      onClick={() => onSelect?.(item.id)}
      style={{ cursor: 'pointer' }}
    >
      <td>
        <code>{item.id}</code>
      </td>
      <td>{item.name}</td>
      <td>{item.area}</td>
      <td>
        <span className="analytics-badge analytics-badge-ship">
          {t('analytics.badgeLive', { defaultValue: 'Live' })}
        </span>
      </td>
      <td className="muted">{item.unit}</td>
    </tr>
  )
}

function DetailPanel({ detail, loading, error }) {
  const { t } = useTranslation(['desks', 'common'])
  if (loading) {
    return <p className="muted">{t('analytics.loadingDetail', { defaultValue: 'Loading KPI…' })}</p>
  }
  if (error) {
    return (
      <div className="banner-error" style={{ margin: 0 }}>
        {error}
      </div>
    )
  }
  if (!detail) {
    return (
      <p className="muted">
        {t('analytics.pickKpi', {
          defaultValue: 'Select a KPI from the catalogue to see its live value.',
        })}
      </p>
    )
  }

  const value = detail.value || {}
  const status = value.status || detail.status
  const primary = formatPrimaryValue(value, detail.unit)
  const drill = detail.drillRoute

  return (
    <div className="analytics-detail">
      <div className="analytics-detail-head">
        <div>
          <div className="muted">
            <code>{detail.id}</code> · {detail.area}
          </div>
          <h3 style={{ margin: '0.25rem 0' }}>{detail.name}</h3>
        </div>
        <div className="analytics-detail-primary">{primary}</div>
      </div>

      <p className="muted" style={{ marginTop: 0 }}>
        {statusLabel(status, t)}
        {value.message ? ` — ${value.message}` : ''}
      </p>

      {(status === 'insufficient' || status === 'deferred' || status === 'planned') && (
        <div className="card analytics-honest" style={{ marginBottom: '0.75rem' }}>
          <strong>
            {status === 'insufficient'
              ? t('analytics.honestInsufficient', {
                  defaultValue: 'Not enough history yet',
                })
              : status === 'deferred'
                ? t('analytics.honestDeferred', {
                    defaultValue: 'Not claimed for Silver v1',
                  })
                : t('analytics.honestPlanned', {
                    defaultValue: 'Feed not wired yet',
                  })}
          </strong>
          <p className="muted" style={{ margin: '0.35rem 0 0' }}>
            {detail.blocker ||
              value.message ||
              t('analytics.honestHint', {
                defaultValue: 'We show an empty state instead of inventing a number.',
              })}
          </p>
        </div>
      )}

      <dl className="analytics-dl">
        <div>
          <dt>{t('analytics.formula', { defaultValue: 'Formula' })}</dt>
          <dd>{detail.formula || '—'}</dd>
        </div>
        <div>
          <dt>{t('analytics.unit', { defaultValue: 'Unit' })}</dt>
          <dd>{detail.unit || '—'}</dd>
        </div>
        {value.count != null && (
          <div>
            <dt>{t('analytics.sample', { defaultValue: 'Sample / count' })}</dt>
            <dd>{formatCount(value.count)}</dd>
          </div>
        )}
        {value.amount != null && String(detail.unit).toLowerCase() !== 'money' && (
          <div>
            <dt>{t('analytics.amount', { defaultValue: 'Amount' })}</dt>
            <dd>{String(value.amount)}</dd>
          </div>
        )}
        {value.fiatEur != null && (
          <div>
            <dt>{t('analytics.fiat', { defaultValue: 'Fiat cash' })}</dt>
            <dd>{formatMoney(value.fiatEur)}</dd>
          </div>
        )}
        {value.btcSats != null && (
          <div>
            <dt>{t('analytics.btc', { defaultValue: 'Bitcoin rails' })}</dt>
            <dd>{formatCount(value.btcSats)} sats</dd>
          </div>
        )}
        {value.overdueAmount != null && (
          <div>
            <dt>{t('analytics.arOverdue', { defaultValue: 'Overdue AR' })}</dt>
            <dd>{formatMoney(value.overdueAmount)}</dd>
          </div>
        )}
      </dl>

      {value.buckets && (
        <div style={{ marginTop: '0.75rem' }}>
          <h4 className="section-label" style={{ marginBottom: '0.5rem' }}>
            {t('analytics.agingBuckets', { defaultValue: 'Aging buckets' })}
          </h4>
          <ArAgingBars buckets={value.buckets} />
        </div>
      )}

      {value.breakdown && typeof value.breakdown === 'object' && !Array.isArray(value.breakdown) && (
        <div style={{ marginTop: '0.75rem' }}>
          <h4 className="section-label">{t('analytics.breakdown', { defaultValue: 'Breakdown' })}</h4>
          <ul className="muted" style={{ margin: '0.35rem 0 0', paddingLeft: '1.1rem' }}>
            {Object.entries(value.breakdown).map(([k, v]) => (
              <li key={k}>
                <strong>{k}</strong>: {typeof v === 'number' ? formatMoney(v) : String(v)}
              </li>
            ))}
          </ul>
        </div>
      )}

      {drill && (
        <p style={{ marginTop: '1rem' }}>
          <Link to={drill} className="btn btn-ghost">
            {t('analytics.drill', { defaultValue: 'Open related desk →' })}
          </Link>
        </p>
      )}
    </div>
  )
}

/**
 * Silver Analytics desk — /analytics (route only; AppShell nav is PR-15).
 * Consumes /api/kpi/catalogue, dashboard-hero, :id, ar-aging.
 */
export default function AnalyticsDesk() {
  const { t } = useTranslation(['desks', 'common'])
  const { kpiId } = useParams()
  const navigate = useNavigate()

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [catalogue, setCatalogue] = useState(null)
  const [hero, setHero] = useState(null)
  const [aging, setAging] = useState(null)
  const [agingError, setAgingError] = useState(null)

  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState(null)

  const silverRich = !!(hero?.silverRich ?? catalogue?.meta?.silverRich)
  const templateTier = hero?.templateTier || catalogue?.meta?.templateTier || '—'

  const loadShell = useCallback(async () => {
    setLoading(true)
    setError(null)
    setAgingError(null)
    try {
      const [cat, heroBody] = await Promise.all([kpiApi.catalogue(), kpiApi.dashboardHero()])
      setCatalogue(cat)
      setHero(heroBody)
      const rich = !!(heroBody?.silverRich ?? cat?.meta?.silverRich)
      if (rich) {
        try {
          const agingBody = await kpiApi.arAging()
          setAging(agingBody)
        } catch (e) {
          setAging(null)
          setAgingError(e.message || String(e))
        }
      } else {
        setAging(null)
      }
    } catch (e) {
      setCatalogue(null)
      setHero(null)
      setAging(null)
      setError(e.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadShell()
  }, [loadShell])

  const itemsByArea = useMemo(() => {
    // Ship / live feeds only — hide deferred + planned stretch (no backend yet)
    const items = (catalogue?.items || []).filter((item) => item.ship && !item.deferred)
    const map = new Map()
    for (const item of items) {
      const area = item.area || 'other'
      if (!map.has(area)) map.set(area, [])
      map.get(area).push(item)
    }
    const ordered = []
    for (const area of AREA_ORDER) {
      if (map.has(area)) ordered.push([area, map.get(area)])
    }
    for (const [area, rows] of map) {
      if (!AREA_ORDER.includes(area)) ordered.push([area, rows])
    }
    return ordered
  }, [catalogue])

  const liveIds = useMemo(() => {
    const ids = new Set()
    for (const [, rows] of itemsByArea) {
      for (const item of rows) ids.add(item.id)
    }
    return ids
  }, [itemsByArea])

  useEffect(() => {
    if (!kpiId || !silverRich) {
      setDetail(null)
      setDetailError(null)
      return undefined
    }
    // Wait until catalogue is known so we can ignore hidden deferred/planned ids
    if (catalogue && liveIds.size > 0 && !liveIds.has(kpiId)) {
      setDetail(null)
      setDetailError(null)
      navigate('/analytics', { replace: true })
      return undefined
    }
    let cancelled = false
    setDetailLoading(true)
    setDetailError(null)
    kpiApi
      .show(kpiId)
      .then((body) => {
        if (!cancelled) setDetail(body)
      })
      .catch((e) => {
        if (!cancelled) {
          setDetail(null)
          setDetailError(e.message || String(e))
        }
      })
      .finally(() => {
        if (!cancelled) setDetailLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [kpiId, silverRich, catalogue, liveIds, navigate])

  const selectKpi = (id) => {
    if (!id) return
    navigate(id === kpiId ? '/analytics' : `/analytics/${encodeURIComponent(id)}`)
  }

  if (loading) {
    return (
      <div className="page">
        <p className="muted">{t('analytics.loading', { defaultValue: 'Loading analytics…' })}</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="page">
        <header className="page-header">
          <h1>
            <BarChart3 size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('analytics.title', { defaultValue: 'Analytics' })}
          </h1>
        </header>
        <div className="banner-error">
          {t('analytics.loadError', { defaultValue: 'Could not load KPI catalogue' })}: {error}
        </div>
        <button type="button" className="btn btn-ghost" onClick={loadShell}>
          {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
        </button>
      </div>
    )
  }

  if (!silverRich) {
    return (
      <div className="page">
        <header className="page-header">
          <h1>
            <BarChart3 size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
            {t('analytics.title', { defaultValue: 'Analytics' })}
          </h1>
          <p className="page-sub">
            {t('analytics.subtitle', {
              defaultValue: 'Light KPI catalogue and pulse — Silver pack.',
            })}
          </p>
        </header>

        <section className="card analytics-gate">
          <Lock size={28} style={{ marginBottom: 8 }} />
          <h2 style={{ marginTop: 0 }}>
            {t('analytics.gateTitle', { defaultValue: 'Silver analytics' })}
          </h2>
          <p>
            {t('analytics.gateBody', {
              defaultValue:
                'Rich KPI ratios and the Analytics desk are a Silver claim. This company is on {{tier}} — Action-NOW keeps counts-only until Silver.',
              tier: templateTier,
            })}
          </p>
          <p className="muted">
            {hero?.note ||
              t('analytics.gateHint', {
                defaultValue: 'Bronze stays honest: no fake win rates or invented runway.',
              })}
          </p>
          <p style={{ marginBottom: 0 }}>
            <Link to="/dashboard" className="btn btn-primary">
              {t('analytics.gateCta', { defaultValue: 'Open Action-NOW' })}
            </Link>
          </p>
        </section>
      </div>
    )
  }

  const agingValue = aging?.value || {}
  const agingBuckets = agingValue.buckets || {}

  return (
    <div className="page analytics-page">
      <header className="page-header">
        <h1>
          <BarChart3 size={22} style={{ verticalAlign: 'middle', marginRight: 8 }} />
          {t('analytics.title', { defaultValue: 'Analytics' })}
        </h1>
        <p className="page-sub">
          {t('analytics.subtitleRich', {
            defaultValue:
              'Pulse, catalogue, and AR aging from live tenant data. Trends stay light (4–12 weeks later).',
          })}
        </p>
        <p className="muted" style={{ marginTop: 0 }}>
          {t('analytics.metaLine', {
            defaultValue: 'Template {{tier}} · {{ship}} ship KPIs · mode {{mode}}',
            tier: templateTier,
            ship: catalogue?.meta?.shipCount ?? 16,
            mode: hero?.mode || 'rich',
          })}{' '}
          <button type="button" className="btn btn-ghost" onClick={loadShell} style={{ marginLeft: 8 }}>
            {t('actions.refresh', { ns: 'common', defaultValue: 'Refresh' })}
          </button>
        </p>
      </header>

      <section className="analytics-hero-strip" aria-label="Hero KPIs">
        <h2 className="section-label">{t('analytics.hero', { defaultValue: 'Monday pulse' })}</h2>
        {(hero?.tiles || []).length === 0 ? (
          <p className="muted">
            {t('analytics.heroEmpty', {
              defaultValue: 'No hero tiles visible for your role.',
            })}
          </p>
        ) : (
          <div className="analytics-hero-grid">
            {hero.tiles.map((tile) => (
              <HeroTile key={tile.id} tile={tile} onSelect={selectKpi} />
            ))}
          </div>
        )}
      </section>

      <section className="card" style={{ marginTop: '1rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap' }}>
          <div>
            <h2 className="section-label" style={{ marginTop: 0 }}>
              {t('analytics.arAging', { defaultValue: 'Money customers still owe (by age)' })}
            </h2>
            <p className="muted" style={{ marginTop: 0 }}>
              {agingValue.message ||
                t('analytics.arAgingHint', {
                  defaultValue: 'Open invoices by how long past due.',
                })}
            </p>
          </div>
          <div className="analytics-ar-total">
            <div className="muted">{t('analytics.openAr', { defaultValue: 'Open AR' })}</div>
            <div className="analytics-hero-value">{formatMoney(agingValue.amount)}</div>
            {agingValue.overdueAmount != null && (
              <div className="muted">
                {t('analytics.overdue', { defaultValue: 'Overdue' })}:{' '}
                {formatMoney(agingValue.overdueAmount)}
              </div>
            )}
          </div>
        </div>
        {agingError ? (
          <p className="muted">{agingError}</p>
        ) : (
          <>
            <ArAgingBars buckets={agingBuckets} />
            <div className="analytics-aging-legend muted">
              {AGING_KEYS.map((k) => (
                <span key={k}>
                  <i style={{ background: AGING_COLORS[k] }} /> {k}: {formatMoney(agingBuckets[k])}
                </span>
              ))}
            </div>
            <p style={{ marginBottom: 0 }}>
              <Link to="/finance/invoices" className="btn btn-ghost">
                {t('analytics.openReceivables', { defaultValue: 'Open receivables →' })}
              </Link>
            </p>
          </>
        )}
      </section>

      <div className="analytics-split" style={{ marginTop: '1rem' }}>
        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('analytics.catalogue', { defaultValue: 'KPI catalogue' })}
          </h2>
          <p className="muted" style={{ marginTop: 0 }}>
            {t('analytics.catalogueHint', {
              defaultValue:
                'Live KPIs with backend feeds. Deferred or planned metrics stay hidden until data exists.',
            })}
          </p>
          {itemsByArea.length === 0 ? (
            <p className="muted">
              {t('analytics.catalogueEmpty', {
                defaultValue: 'No KPIs visible for your role.',
              })}
            </p>
          ) : (
            itemsByArea.map(([area, rows]) => (
              <div key={area} style={{ marginBottom: '1rem' }}>
                <h3 className="section-label" style={{ textTransform: 'capitalize' }}>
                  {area}
                </h3>
                <div className="table-wrap">
                  <table className="data analytics-cat-table">
                    <thead>
                      <tr>
                        <th>{t('analytics.colId', { defaultValue: 'Id' })}</th>
                        <th>{t('analytics.colName', { defaultValue: 'Name' })}</th>
                        <th>{t('analytics.colArea', { defaultValue: 'Area' })}</th>
                        <th>{t('analytics.colStatus', { defaultValue: 'Set' })}</th>
                        <th>{t('analytics.colUnit', { defaultValue: 'Unit' })}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((item) => (
                        <CatalogueRow
                          key={item.id}
                          item={item}
                          selected={item.id === kpiId}
                          onSelect={selectKpi}
                        />
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ))
          )}
        </section>

        <section className="card">
          <h2 className="section-label" style={{ marginTop: 0 }}>
            {t('analytics.detail', { defaultValue: 'KPI detail' })}
          </h2>
          <DetailPanel detail={detail} loading={detailLoading} error={detailError} />
        </section>
      </div>
    </div>
  )
}
