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

import { Plus, Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import CatalogueItemSelect from './CatalogueItemSelect'

/** Empty line draft for SO / quote / PO / service-order create forms */
export function emptyDocLine(overrides = {}) {
  return {
    key: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    catalogueItemId: '',
    productRefId: '',
    serviceRefId: '',
    sourceType: '',
    name: '',
    quantity: '1',
    unitPriceEur: '0',

    ...overrides,
  }
}

/**
 * Multi-line editor for commercial documents (quote, sales order, PO, service order).
 * @param {'sale'|'purchase'} forChannel — catalogue filter
 * @param {string} [nature] GOODS | SERVICE | '' for all
 * @param {Array} lines — line drafts (need stable key)
 * @param {(lines: Array) => void} onChange
 */
export default function DocumentLinesEditor({
  lines = [],
  onChange,
  forChannel = 'sale',
  nature = '',
  disabled = false,
}) {
  const { t } = useTranslation('common')

  const updateLine = (key, patch) => {
    onChange(lines.map((ln) => (ln.key === key ? { ...ln, ...patch } : ln)))
  }

  const removeLine = (key) => {
    if (lines.length <= 1) return
    onChange(lines.filter((ln) => ln.key !== key))
  }

  const addLine = () => {
    onChange([...lines, emptyDocLine()])
  }

  const totalEur = lines.reduce((sum, ln) => {
    const q = Number(ln.quantity) || 0
    const p = Number(ln.unitPriceEur) || 0
    return sum + q * p
  }, 0)

  return (
    <div className="doc-lines-editor">
      <div className="doc-lines-header">
        <span className="doc-lines-title">
          {t('docLines.title', { defaultValue: 'Lines' })} ({lines.length})
        </span>
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          disabled={disabled}
          onClick={addLine}
        >
          <Plus size={14} /> {t('docLines.add', { defaultValue: 'Add line' })}
        </button>
      </div>

      <div className="doc-lines-list">
        {lines.map((ln, idx) => (
          <div key={ln.key} className="doc-line-card">
            <div className="doc-line-top">
              <span className="doc-line-no">#{idx + 1}</span>
              <button
                type="button"
                className="btn btn-ghost btn-sm doc-line-remove"
                disabled={disabled || lines.length <= 1}
                onClick={() => removeLine(ln.key)}
                title={t('docLines.remove', { defaultValue: 'Remove line' })}
                aria-label={t('docLines.remove', { defaultValue: 'Remove line' })}
              >
                <Trash2 size={14} />
              </button>
            </div>

            <label>
              {t('catalogue.lineItem', { defaultValue: 'Catalogue item' })}
              <CatalogueItemSelect
                forChannel={forChannel}
                nature={nature}
                value={ln.catalogueItemId}
                disabled={disabled}
                id={`cat-${ln.key}`}
                onChange={(id, item) => {
                  const isService = item?.nature === 'SERVICE'
                  updateLine(ln.key, {
                    catalogueItemId: id,
                    name: item?.name || ln.name,
                    unitPriceEur:
                      item?.listPrice != null ? String(item.listPrice) : ln.unitPriceEur,
                    // productRef*/serviceRef* store catalogue ids (legacy masters removed)
                    sourceType: isService ? 'SERVICE' : 'PRODUCT',
                    productRefId: !isService && id ? String(id) : '',
                    serviceRefId: isService && id ? String(id) : '',
                  })
                }}
              />
            </label>

            <label>
              {t('docLines.description', { defaultValue: 'Description' })}
              <input
                value={ln.name}
                disabled={disabled}
                onChange={(e) => updateLine(ln.key, { name: e.target.value })}
                placeholder={t('docLines.descriptionPh', { defaultValue: 'Line description' })}
              />
            </label>

            <div className="form-row-2">
              <label>
                {t('docLines.qty', { defaultValue: 'Qty' })}
                <input
                  value={ln.quantity}
                  disabled={disabled}
                  inputMode="decimal"
                  onChange={(e) => updateLine(ln.key, { quantity: e.target.value })}
                />
              </label>
              <label>
                {t('docLines.unitPrice', { defaultValue: 'Unit price' })} *
                <input
                  required
                  value={ln.unitPriceEur}
                  disabled={disabled}
                  inputMode="decimal"
                  onChange={(e) => updateLine(ln.key, { unitPriceEur: e.target.value })}
                />
              </label>
            </div>

            <div className="doc-line-subtotal muted">
              {t('docLines.lineTotal', { defaultValue: 'Line total' })}:{' '}
              <strong>
                {((Number(ln.quantity) || 0) * (Number(ln.unitPriceEur) || 0)).toFixed(2)}
              </strong>
            </div>
          </div>
        ))}
      </div>

      <div className="doc-lines-footer">
        <span className="muted">{t('docLines.docTotal', { defaultValue: 'Document total' })}</span>
        <strong>{totalEur.toFixed(2)}</strong>
      </div>
    </div>
  )
}

/**
 * Map editor lines → API payload for quote / SO / PO.
 * Delivery service orders can pass { includeServiceRefs: true }.
 */
export function linesToApiPayload(lines, opts = {}) {
  const { includeServiceRefs = false } = opts
  return (lines || [])
    .filter((ln) => (ln.name || '').trim() || ln.catalogueItemId)
    .map((ln) => {
      const payload = {
        name: (ln.name || 'Line item').trim() || 'Line item',
        quantity: ln.quantity || '1',
        unitPriceEur: ln.unitPriceEur || '0',
        catalogueItemId: ln.catalogueItemId || null,
      }
      if (ln.productRefId && ln.sourceType !== 'SERVICE') {
        payload.productRefId = ln.productRefId
        payload.productRefType = 'PRODUCT'
      }
      if (ln.serviceRefId || ln.sourceType === 'SERVICE' || includeServiceRefs) {
        payload.productRefType = payload.productRefType || 'SERVICE'
        if (ln.serviceRefId) {
          payload.serviceRefId = ln.serviceRefId
          // Some backends resolve service via productRefId + productRefType=SERVICE
          payload.productRefId = payload.productRefId || ln.serviceRefId
        }
      }
      return payload
    })
}
