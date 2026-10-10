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
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { domainsApi } from '../api/client'

/**
 * Nested line-item table for a parent document on DomainList (L3 residual).
 */
export default function DomainParentLines({ lineChild, parentId, colSpan }) {
  const { t } = useTranslation('common')
  const [lines, setLines] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    const params = { max: 100, offset: 0, [lineChild.parentFk]: parentId }
    domainsApi
      .list(lineChild.package, lineChild.resource, params)
      .then((d) => {
        if (!cancelled) setLines(d.items || [])
      })
      .catch((e) => {
        if (!cancelled) setError(e?.message || String(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [lineChild.package, lineChild.resource, lineChild.parentFk, parentId])

  return (
    <tr className="domain-parent-lines">
      <td colSpan={colSpan} style={{ background: 'var(--surface-muted, #f7f7f9)', padding: '0.5rem 0.75rem' }}>
        <div style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: 6 }}>
          {t('masterData.linesHeading', {
            defaultValue: 'Lines',
          })}
          {lineChild.deskPath && (
            <>
              {' · '}
              <Link to={lineChild.deskPath} className="btn-link-title">
                {t('masterData.editOnDesk', { defaultValue: 'Edit on desk' })}
              </Link>
            </>
          )}
        </div>
        {loading && <p className="muted" style={{ margin: 0 }}>{t('actions.loading')}</p>}
        {error && <p className="bad-text" style={{ margin: 0 }}>{error}</p>}
        {!loading && !error && lines.length === 0 && (
          <p className="muted" style={{ margin: 0 }}>
            {t('masterData.noLines', { defaultValue: 'No lines on this document.' })}
          </p>
        )}
        {!loading && !error && lines.length > 0 && (
          <table className="data" style={{ margin: 0, fontSize: '0.9rem' }}>
            <thead>
              <tr>
                <th style={{ width: 48 }}>#</th>
                <th>{t('wb.name')}</th>
                <th className="col-priority-low">{t('wb.qty', { defaultValue: 'Qty' })}</th>
                <th>{t('wb.amountEur', { defaultValue: 'Amount (EUR)' })}</th>
                <th className="col-priority-low">{t('wb.status')}</th>
              </tr>
            </thead>
            <tbody>
              {lines.map((line) => (
                <tr key={line.id}>
                  <td className="mono">{line.lineNo ?? '—'}</td>
                  <td>
                    {line.name || line.productName || `#${line.id}`}
                    {line.productSku ? (
                      <span className="muted" style={{ marginLeft: 6 }}>
                        {line.productSku}
                      </span>
                    ) : null}
                  </td>
                  <td className="mono col-priority-low">
                    {line.quantity != null ? line.quantity : '—'}
                  </td>
                  <td className="mono">
                    {line.lineTotalEur != null
                      ? line.lineTotalEur
                      : line.debitEur != null || line.creditEur != null
                        ? `${line.debitEur ?? 0} / ${line.creditEur ?? 0}`
                        : '—'}
                  </td>
                  <td className="col-priority-low muted">{line.status || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </td>
    </tr>
  )
}
