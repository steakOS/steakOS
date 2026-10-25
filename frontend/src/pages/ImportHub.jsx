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

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { importApi } from '../api/client'

/**
 * Data & import — CSV packs with column mapping → dry-run summary → confirm.
 */
const SAMPLE_CSV = {
  CUSTOMERS:
    'name,email,phone\nAcme Corp,hello@acme.test,+491111\nBeta LLC,beta@example.com,',
  VENDORS: 'name,email,phone\nSteel Supply,buy@steel.test,+49222\nParts Co,parts@example.com,',
  CATALOGUE_ITEMS: `name,code,type,unit,listPrice,currency,canBeSold,canBeBought,active,description,status,classification,subtype,tags
Widget A,WGT-A,product,EA,12.50,EUR,true,true,true,Standard widget,ACTIVE,hardware,finished,stock;retail
Consulting hour,CONS-H,service,HOUR,100,EUR,true,false,true,Billable hour,ACTIVE,professional,,billable
Raw steel,STL-1,goods,KG,0.80,EUR,false,true,true,Buy-only material,ACTIVE,raw-material,bulk,purchase`,
  COA: 'code,name,accountType\n1000,Cash,ASSET\n2000,Payables,LIABILITY',
  OPENING_BALANCES: 'code,debitEur,creditEur,memo\n1000,1000,,Opening cash',
}

const packFileName = (code) => {
  const map = {
    CUSTOMERS: 'customers.csv',
    VENDORS: 'vendors.csv',
    CATALOGUE_ITEMS: 'catalogue-items.csv',
    COA: 'coa.csv',
    OPENING_BALANCES: 'opening-balances.csv',
  }
  return map[code] || 'import.csv'
}

/** Backend mapping is steakOS-field → CSV header. */
function toBackendMapping(csvToSteak) {
  const out = {}
  Object.entries(csvToSteak || {}).forEach(([csv, steak]) => {
    if (steak) out[steak] = csv
  })
  return out
}

/** UI mapping is CSV header → steakOS field (or ''). */
function toUiMapping(headers, backendMapping) {
  const inv = {}
  Object.entries(backendMapping || {}).forEach(([steak, csv]) => {
    if (csv) inv[csv] = steak
  })
  const out = {}
  ;(headers || []).forEach((h) => {
    out[h] = inv[h] || ''
  })
  return out
}

export default function ImportHub() {
  const { t } = useTranslation('common')
  const [packs, setPacks] = useState([])
  const [jobs, setJobs] = useState([])
  const [packType, setPackType] = useState('CUSTOMERS')
  const [csvText, setCsvText] = useState(SAMPLE_CSV.CUSTOMERS)
  const [fileName, setFileName] = useState('customers.csv')
  const [preview, setPreview] = useState(null)
  /** 'map' | 'review' after upload */
  const [step, setStep] = useState(null)
  /** CSV header → steakOS field code */
  const [csvMapping, setCsvMapping] = useState({})
  /** Pack-wide nature for catalogue: '' | GOODS | SERVICE */
  const [defaultNature, setDefaultNature] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const packLabel = (p) => {
    if (!p?.code) return p?.label || ''
    const key = {
      CUSTOMERS: 'pages.import.customers',
      VENDORS: 'pages.import.vendors',
      CATALOGUE_ITEMS: 'pages.import.catalogueItems',
      COA: 'pages.import.coa',
      OPENING_BALANCES: 'pages.import.openingBalances',
    }[p.code]
    return key ? t(key, { defaultValue: p.label || p.code }) : p.label || p.code
  }

  const targetFields = useMemo(() => {
    if (preview?.targetFields?.length) return preview.targetFields
    const pack = packs.find((p) => p.code === (preview?.packType || packType))
    return pack?.fields || []
  }, [preview, packs, packType])

  const duplicateTargets = useMemo(() => {
    const counts = {}
    Object.values(csvMapping).forEach((steak) => {
      if (!steak) return
      counts[steak] = (counts[steak] || 0) + 1
    })
    return Object.keys(counts).filter((k) => counts[k] > 1)
  }, [csvMapping])

  const missingRequired = useMemo(() => {
    const mapped = new Set(Object.values(csvMapping).filter(Boolean))
    return targetFields.filter((f) => f.required && !mapped.has(f.code))
  }, [csvMapping, targetFields])

  const reload = useCallback(async () => {
    try {
      const [p, j] = await Promise.all([importApi.packs(), importApi.list()])
      setPacks(p.packs || [])
      setJobs(j.items || [])
    } catch (e) {
      setError(e.message || String(e))
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  const onPackChange = (code) => {
    setPackType(code)
    setPreview(null)
    setStep(null)
    setCsvMapping({})
    setDefaultNature('')
    setResult(null)
    if (SAMPLE_CSV[code]) {
      setCsvText(SAMPLE_CSV[code])
      setFileName(packFileName(code))
    }
  }

  /** Guess pack from filename / header row so catalogue CSVs are not committed as customers. */
  const detectPack = (name, text) => {
    const lower = (name || '').toLowerCase()
    if (lower.includes('catalogue') || lower.includes('catalog') || lower.includes('product')) {
      return 'CATALOGUE_ITEMS'
    }
    if (lower.includes('vendor') || lower.includes('supplier')) return 'VENDORS'
    if (lower.includes('customer')) return 'CUSTOMERS'
    if (lower.includes('coa') || lower.includes('account')) return 'COA'
    const header =
      (text || '').split(/\r?\n/).find((l) => l.trim() && !l.trim().startsWith('#')) || ''
    const h = header.toLowerCase()
    if (
      h.includes('nature') ||
      h.includes('listprice') ||
      h.includes('canbesold') ||
      h.includes('sku') ||
      (h.includes('type') && (h.includes('product') || h.includes('service') || h.includes('unit')))
    ) {
      return 'CATALOGUE_ITEMS'
    }
    if (h.includes('accounttype') || (h.includes('code') && h.includes('account'))) return 'COA'
    if (h.includes('customerkind')) return 'CUSTOMERS'
    if (h.includes('vendor') && !h.includes('customer')) return 'VENDORS'
    return null
  }

  const onFile = async (e) => {
    const f = e.target.files?.[0]
    if (!f) return
    setFileName(f.name)
    const text = await f.text()
    setCsvText(text)
    const guessed = detectPack(f.name, text)
    if (guessed && guessed !== packType) {
      setPackType(guessed)
      setPreview(null)
      setStep(null)
      setResult(null)
    }
  }

  const uploadPreview = async () => {
    setBusy(true)
    setError(null)
    setResult(null)
    try {
      const guessed = detectPack(fileName, csvText)
      const pack = guessed || packType
      if (guessed && guessed !== packType) setPackType(guessed)
      const res = await importApi.upload({ packType: pack, fileName, csvText })
      const job = res.job || res
      setPreview(job)
      setDefaultNature(job.defaultNature || '')
      const uiMap =
        job.csvMapping && Object.keys(job.csvMapping).length
          ? job.csvMapping
          : toUiMapping(job.headers || [], job.mapping || {})
      setCsvMapping(uiMap)
      setStep('map')
      await reload()
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const cancelPreview = () => {
    setPreview(null)
    setStep(null)
    setCsvMapping({})
    setDefaultNature('')
    setError(null)
  }

  const setColumnMap = (csvHeader, steakField) => {
    setCsvMapping((prev) => ({ ...prev, [csvHeader]: steakField || '' }))
  }

  const applyMappingAndReview = async () => {
    if (!preview?.id) {
      setError(t('pages.import.noJob', { defaultValue: 'No import job — upload a CSV first.' }))
      return
    }
    if (missingRequired.length) {
      setError(
        t('pages.import.missingRequired', {
          fields: missingRequired.map((f) => f.label || f.code).join(', '),
          defaultValue: 'Map required fields: {{fields}}',
        }),
      )
      return
    }
    if (duplicateTargets.length) {
      setError(
        t('pages.import.duplicateTargets', {
          fields: duplicateTargets.join(', '),
          defaultValue: 'Each steakOS field can only be mapped once (duplicates: {{fields}})',
        }),
      )
      return
    }
    setBusy(true)
    setError(null)
    try {
      const mapping = toBackendMapping(csvMapping)
      const job = await importApi.preview(preview.id, {
        mapping,
        defaultNature: defaultNature || undefined,
      })
      const next = job?.id ? job : job?.job || job
      if (!next?.id) {
        throw new Error(
          t('pages.import.previewFailed', {
            defaultValue: 'Preview did not return a job — check the API response.',
          }),
        )
      }
      setPreview(next)
      setCsvMapping(next.csvMapping || toUiMapping(next.headers || [], next.mapping || {}))
      if (next.defaultNature != null) setDefaultNature(next.defaultNature || '')
      setStep('review')
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const backToMap = () => {
    setStep('map')
    setError(null)
  }

  const confirmCommit = async () => {
    if (!preview?.id) return
    const analysis = preview.analysis || {}
    if (analysis.validCount === 0) {
      setError(t('pages.import.nothingValid', { defaultValue: 'No valid rows to import.' }))
      return
    }
    const summary =
      preview.summary ||
      analysis.summaryMessage ||
      t('pages.import.confirmFallback', {
        valid: analysis.validCount,
        total: analysis.totalRows ?? preview.rowCount,
        defaultValue: 'Import {{valid}} of {{total}} valid rows?',
      })
    const ok = window.confirm(
      `${summary}\n\n${t('pages.import.proceedYn', { defaultValue: 'Proceed? (OK = Yes, Cancel = No)' })}`,
    )
    if (!ok) return

    setBusy(true)
    setError(null)
    try {
      const mapping = toBackendMapping(csvMapping)
      const res = await importApi.commit(preview.id, {
        confirmed: true,
        mapping,
        defaultNature: defaultNature || undefined,
      })
      setResult(res.job || res)
      setPreview(null)
      setStep(null)
      setCsvMapping({})
      setDefaultNature('')
      await reload()
    } catch (e) {
      setError(e.message || String(e))
    } finally {
      setBusy(false)
    }
  }

  const analysis = preview?.analysis
  const packMismatch =
    preview?.packType === 'CUSTOMERS' &&
    /nature|type|listprice|canbesold|sku/i.test(csvText.split('\n')[0] || '')

  const sampleForHeader = (header) => {
    const row = (preview?.sample || [])[0]
    if (!row) return ''
    const v = row[header]
    if (v == null || v === '') return ''
    const s = String(v)
    return s.length > 40 ? `${s.slice(0, 40)}…` : s
  }

  return (
    <div>
      <h1 className="page-title">{t('pages.import.title')}</h1>
      <p className="page-sub">{t('pages.import.subtitle')}</p>
      {error && <div className="setup-error">{error}</div>}

      <div className="card" style={{ marginTop: 16 }}>
        <h2>{t('pages.import.newImport')}</h2>
        <label>
          {t('pages.import.pack')}
          <select value={packType} onChange={(e) => onPackChange(e.target.value)} disabled={!!preview}>
            {(packs.length
              ? packs
              : [
                  { code: 'CUSTOMERS', label: t('pages.import.customers') },
                  { code: 'VENDORS', label: t('pages.import.vendors') },
                  { code: 'CATALOGUE_ITEMS', label: t('pages.import.catalogueItems') },
                  { code: 'COA', label: t('pages.import.coa') },
                ]
            ).map((p) => (
              <option key={p.code} value={p.code}>
                {packLabel(p)}
              </option>
            ))}
          </select>
        </label>
        <label style={{ display: 'block', marginTop: 12 }}>
          {t('pages.import.csvFile')}
          <input
            type="file"
            accept=".csv,text/csv,text/plain"
            onChange={onFile}
            disabled={!!preview}
          />
        </label>
        <label style={{ display: 'block', marginTop: 12 }}>
          {t('pages.import.orPaste')}
          <textarea
            rows={8}
            value={csvText}
            onChange={(e) => setCsvText(e.target.value)}
            disabled={!!preview}
            style={{ width: '100%', fontFamily: 'monospace', fontSize: 13 }}
          />
        </label>
        <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {!preview && (
            <button type="button" className="btn btn-primary" disabled={busy} onClick={uploadPreview}>
              {busy ? t('pages.import.working') : t('pages.import.uploadPreview')}
            </button>
          )}
        </div>
      </div>

      {preview && step === 'map' && (
        <div className="card" style={{ marginTop: 16 }}>
          <h2>
            {t('pages.import.mapTitle', { defaultValue: 'Map CSV columns' })}
            {preview.fileName ? ` · ${preview.fileName}` : ''}
          </h2>
          <p className="page-sub">
            Pack <strong className="mono">{preview.packType || packType}</strong> ·{' '}
            {preview.rowCount} data rows ·{' '}
            {t('pages.import.mapHint', {
              defaultValue:
                'Match each CSV column (left) to a steakOS field (right). Leave as “— ignore —” to skip.',
            })}
          </p>

          {packMismatch && (
            <p className="setup-error">
              {t('pages.import.packMismatch', {
                defaultValue:
                  'Warning: this CSV looks like catalogue items, but the pack is CUSTOMERS. Cancel and re-upload with Catalogue items.',
              })}
            </p>
          )}

          {(preview.packType || packType) === 'CATALOGUE_ITEMS' && (
            <label
              style={{
                display: 'block',
                marginTop: 12,
                padding: '0.75rem 1rem',
                border: '1px solid var(--steak-border, #e7e5e4)',
                borderRadius: 8,
                background: '#fafaf9',
              }}
            >
              <strong>
                {t('pages.import.defaultNature', {
                  defaultValue: 'Nature for all records',
                })}
              </strong>
              <div className="muted" style={{ fontSize: '0.85rem', margin: '0.25rem 0 0.5rem' }}>
                {t('pages.import.defaultNatureHint', {
                  defaultValue:
                    'Optional override applied to every row. Use “From CSV” to keep values from a mapped type/nature column.',
                })}
              </div>
              <select
                value={defaultNature}
                onChange={(e) => setDefaultNature(e.target.value)}
                style={{ maxWidth: 360, width: '100%' }}
              >
                <option value="">
                  {t('pages.import.natureFromCsv', {
                    defaultValue: 'From CSV / mapped type column',
                  })}
                </option>
                <option value="GOODS">
                  {t('pages.import.natureProduct', {
                    defaultValue: 'Product (GOODS) — all rows',
                  })}
                </option>
                <option value="SERVICE">
                  {t('pages.import.natureService', {
                    defaultValue: 'Service — all rows',
                  })}
                </option>
              </select>
            </label>
          )}

          {missingRequired.length > 0 && (
            <p className="setup-error" style={{ marginTop: 8 }}>
              {t('pages.import.requiredHint', {
                fields: missingRequired.map((f) => f.label || f.code).join(', '),
                defaultValue: 'Required steakOS fields still unmapped: {{fields}}',
              })}
            </p>
          )}
          {duplicateTargets.length > 0 && (
            <p className="setup-error" style={{ marginTop: 8 }}>
              {t('pages.import.duplicateTargets', {
                fields: duplicateTargets.join(', '),
                defaultValue: 'Each steakOS field can only be mapped once (duplicates: {{fields}})',
              })}
            </p>
          )}

          <div className="table-wrap" style={{ marginTop: 12 }}>
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: '32%' }}>
                    {t('pages.import.csvColumn', { defaultValue: 'CSV column' })}
                  </th>
                  <th style={{ width: '28%' }}>
                    {t('pages.import.sampleValue', { defaultValue: 'Sample value' })}
                  </th>
                  <th style={{ width: '40%' }}>
                    {t('pages.import.steakosField', { defaultValue: 'steakOS field' })}
                  </th>
                </tr>
              </thead>
              <tbody>
                {(preview.headers || []).map((h, idx) => {
                  const display =
                    (preview.rawHeaders && preview.rawHeaders[idx]) || h
                  return (
                    <tr key={h}>
                      <td>
                        <code className="mono">{display}</code>
                      </td>
                      <td className="muted" style={{ fontSize: '0.85rem' }}>
                        {sampleForHeader(h) || '—'}
                      </td>
                      <td>
                        <select
                          value={csvMapping[h] || ''}
                          onChange={(e) => setColumnMap(h, e.target.value)}
                          style={{ width: '100%', maxWidth: 320 }}
                        >
                          <option value="">
                            {t('pages.import.ignoreColumn', { defaultValue: '— ignore —' })}
                          </option>
                          {targetFields.map((f) => (
                            <option key={f.code} value={f.code}>
                              {f.label || f.code}
                              {f.required ? ' *' : ''}
                            </option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <div style={{ marginTop: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy}
              onClick={applyMappingAndReview}
            >
              {busy
                ? t('pages.import.working')
                : t('pages.import.continueReview', {
                    defaultValue: 'Continue — review import',
                  })}
            </button>
            <button type="button" className="btn" disabled={busy} onClick={cancelPreview}>
              {t('pages.import.proceedNo', { defaultValue: 'Cancel' })}
            </button>
          </div>
        </div>
      )}

      {preview && step === 'review' && (
        <div className="card" style={{ marginTop: 16 }}>
          <h2>
            {t('pages.import.reviewTitle', { defaultValue: 'Review before import' })}
            {preview.fileName ? ` · ${preview.fileName}` : ''}
          </h2>
          <p className="page-sub">
            Pack <strong className="mono">{preview.packType || packType}</strong> ·{' '}
            {preview.rowCount} data rows · status {preview.status}
            {defaultNature ? (
              <>
                {' '}
                ·{' '}
                {t('pages.import.natureOverrideActive', {
                  nature: defaultNature === 'SERVICE' ? 'Service' : 'Product (GOODS)',
                  defaultValue: 'Nature override: {{nature}}',
                })}
              </>
            ) : null}
          </p>

          <div
            className="banner-info"
            style={{
              marginTop: 12,
              padding: '1rem 1.1rem',
              border: '1px solid var(--steak-border, #e7e5e4)',
              borderRadius: 8,
              background: '#fff7ed',
            }}
          >
            <p style={{ margin: 0, fontWeight: 600, fontSize: '1.05rem', lineHeight: 1.45 }}>
              {preview.summary ||
                analysis?.summaryMessage ||
                t('pages.import.summaryMissing', {
                  defaultValue: 'Analysis unavailable — re-upload to refresh summary.',
                })}
            </p>
            {analysis && (
              <ul className="muted" style={{ margin: '0.75rem 0 0', paddingLeft: '1.2rem' }}>
                <li>
                  {t('pages.import.statTotal', {
                    n: analysis.totalRows,
                    defaultValue: 'Total rows: {{n}}',
                  })}
                </li>
                <li>
                  {t('pages.import.statValid', {
                    n: analysis.validCount,
                    defaultValue: 'Valid (will import): {{n}}',
                  })}
                </li>
                <li>
                  {t('pages.import.statInvalid', {
                    n: analysis.invalidCount,
                    defaultValue: 'Invalid (skipped): {{n}}',
                  })}
                </li>
                {analysis.willCreate != null && (
                  <li>
                    {t('pages.import.statCreateUpdate', {
                      create: analysis.willCreate,
                      update: analysis.willUpdate,
                      defaultValue: 'Approx. new: {{create}} · updates: {{update}}',
                    })}
                  </li>
                )}
                {preview.packType === 'CATALOGUE_ITEMS' && (
                  <>
                    <li>
                      {t('pages.import.statGoods', {
                        active: analysis.goodsActive,
                        inactive: analysis.goodsInactive,
                        defaultValue:
                          'Products (GOODS): {{active}} active, {{inactive}} inactive',
                      })}
                    </li>
                    <li>
                      {t('pages.import.statServices', {
                        active: analysis.serviceActive,
                        inactive: analysis.serviceInactive,
                        defaultValue:
                          'Services: {{active}} active, {{inactive}} inactive',
                      })}
                    </li>
                  </>
                )}
              </ul>
            )}
          </div>

          {analysis?.invalidSamples?.length > 0 && (
            <div style={{ marginTop: 12 }}>
              <h3 className="section-label" style={{ marginTop: 0 }}>
                {t('pages.import.invalidSamples', { defaultValue: 'Invalid rows (sample)' })}
              </h3>
              <ul className="setup-family-list" style={{ fontSize: '0.9rem' }}>
                {analysis.invalidSamples.map((s) => (
                  <li key={s.row}>
                    <span className="mono">#{s.row}</span> {s.message}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <p className="muted" style={{ marginTop: 12 }}>
            {t('pages.import.mappingSummary', { defaultValue: 'Applied mapping' })}:{' '}
            <code>{JSON.stringify(preview.mapping)}</code>
          </p>
          <div className="table-wrap" style={{ marginTop: 8 }}>
            <table className="data">
              <thead>
                <tr>
                  {(preview.headers || []).map((h) => (
                    <th key={h}>
                      {h}
                      {csvMapping[h] ? (
                        <div className="muted" style={{ fontWeight: 400, fontSize: '0.75rem' }}>
                          → {csvMapping[h]}
                        </div>
                      ) : null}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {(preview.sample || []).map((row, i) => (
                  <tr key={i}>
                    {(preview.headers || []).map((h) => (
                      <td key={h}>{row[h]}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div style={{ marginTop: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy || packMismatch || analysis?.canProceed === false}
              onClick={confirmCommit}
            >
              {busy
                ? t('pages.import.working')
                : t('pages.import.proceedYes', { defaultValue: 'Proceed — import valid rows' })}
            </button>
            <button type="button" className="btn" disabled={busy} onClick={backToMap}>
              {t('pages.import.backToMap', { defaultValue: 'Back to mapping' })}
            </button>
            <button type="button" className="btn" disabled={busy} onClick={cancelPreview}>
              {t('pages.import.proceedNo', { defaultValue: 'Cancel' })}
            </button>
          </div>
        </div>
      )}

      {result && (
        <div className="card" style={{ marginTop: 16 }}>
          <h2>{t('pages.import.commitResult')}</h2>
          <p>
            {t('pages.import.commitStats', {
              created: result.createdCount,
              updated: result.updatedCount,
              skipped: result.skippedCount,
              errors: result.errorCount,
            })}
          </p>
          {result.importedCount != null && (
            <p className="muted">
              {t('pages.import.importedTotal', {
                n: result.importedCount,
                defaultValue: 'Imported {{n}} row(s) (created + updated).',
              })}
            </p>
          )}
          {result.errorSummary && <p className="setup-error">{result.errorSummary}</p>}
        </div>
      )}

      <div className="card" style={{ marginTop: 16 }}>
        <h2>{t('pages.import.recentJobs')}</h2>
        {(jobs || []).length === 0 ? (
          <p className="muted">{t('pages.import.empty')}</p>
        ) : (
          <ul className="setup-family-list">
            {jobs.map((j) => (
              <li key={j.id}>
                {j.fileName || j.packType} · {j.packType} · {j.status} · +{j.createdCount}/~
                {j.updatedCount}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
