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

import { useTranslation } from 'react-i18next'
import SimpleStatusSelect from '../components/SimpleStatusSelect'
import CurrencySelect from '../components/CurrencySelect'
import LocaleSelect from '../components/LocaleSelect'
import CatalogueItemSelect from '../components/CatalogueItemSelect'
import CustomerSelect from '../components/CustomerSelect'
import { humanizeLabel } from '../utils/humanize'

/**
 * Generic field renderer from domain schema API (P2).
 * schema: { fields: [{ name, type, required, maxSize, inList, scale, refPackage, refResource, label }] }
 *
 * Free-text Classifiable `status` (no inList) → SimpleStatus catalog dropdown.
 * Domain-specific status enums (invoiceStatus, etc.) keep schema enum rendering.
 * currency / currencyCode → CurrencySelect (OI-CUR01).
 */
export default function SchemaFormFields({
  schema,
  form,
  onChange,
  disabled = false,
  hideNames = ['id', 'class', 'npubOwner', 'revision', 'revisionNote', 'createdByNpub', 'lastUpdatedByNpub', 'dateCreated', 'lastUpdated', 'protection', 'classification', 'contentTypeCode', 'languageCode'],
}) {
  const { t } = useTranslation('common')
  const fields = (schema?.fields || []).filter(
    (f) => f.name && !hideNames.includes(f.name) && f.type !== 'readonly',
  )

  if (!fields.length) {
    return <p className="muted">{t('schemaForm.noEditableFields')}</p>
  }

  // Prefer name first, then description, key FKs / qty, then rest
  const ordered = [...fields].sort((a, b) => {
    const rank = (n) => {
      if (n === 'name') return 0
      if (n === 'description') return 1
      if (n === 'catalogueItemId' || n === 'productId') return 2
      if (n === 'qtyDelta' || n === 'quantity') return 3
      if (n === 'reason') return 4
      if (n === 'status') return 90
      return 50
    }
    return rank(a.name) - rank(b.name) || a.name.localeCompare(b.name)
  })

  const labelFor = (field) => schemaFieldLabel(t, field)

  const emitField = (name, value) => {
    onChange({
      target: { name, value, type: 'text' },
    })
  }

  return (
    <>
      {ordered.map((field) => {
        const label = labelFor(field)
        const val = form[field.name]
        const isRequired = !!field.required && field.name !== 'description'
        const common = {
          name: field.name,
          disabled,
          required: isRequired,
          onChange,
        }

        if (field.type === 'boolean') {
          return (
            <label key={field.name} className="checkbox-label">
              <input
                type="checkbox"
                name={field.name}
                checked={!!val}
                disabled={disabled}
                onChange={(e) =>
                  onChange({
                    target: {
                      name: field.name,
                      type: 'checkbox',
                      checked: e.target.checked,
                      value: e.target.checked,
                    },
                  })
                }
              />
              {label}
            </label>
          )
        }

        if (field.name === 'currencyCode' || field.name === 'currency') {
          const ccyLabel =
            field.name === 'currencyCode'
              ? t('schemaForm.preferredCurrency')
              : t('schemaForm.priceCurrency')
          return (
            <label key={field.name}>
              {ccyLabel}
              {field.required ? ' *' : ''}
              <CurrencySelect
                name={field.name}
                value={val ?? ''}
                onChange={onChange}
                disabled={disabled}
                required={!!field.required}
              />
            </label>
          )
        }

        if (field.name === 'communicationLocale') {
          return (
            <label key={field.name}>
              {t('fields.communicationLocale')}
              {field.required ? ' *' : ''}
              <LocaleSelect
                name={field.name}
                value={val ?? ''}
                onChange={onChange}
                disabled={disabled}
                required={!!field.required}
                allowEmpty={!field.required}
              />
            </label>
          )
        }

        // Classifiable lifecycle status → catalog (not domain-specific *Status enums)
        if (
          field.name === 'status' &&
          !(field.type === 'enum' && field.inList?.length)
        ) {
          return (
            <label key={field.name}>
              {label}
              {field.required ? ' *' : ''}
              <SimpleStatusSelect
                name="status"
                value={val ?? ''}
                onChange={onChange}
                disabled={disabled}
                required={!!field.required}
              />
            </label>
          )
        }

        if (field.type === 'enum' && field.inList?.length) {
          return (
            <label key={field.name}>
              {label}
              {field.required ? ' *' : ''}
              <select {...common} value={val ?? field.defaultValue ?? ''}>
                {!field.required && <option value="">—</option>}
                {field.inList.map((opt) => (
                  <option key={opt} value={opt}>
                    {opt}
                  </option>
                ))}
              </select>
            </label>
          )
        }

        if (field.type === 'text' && (field.maxSize || 0) > 500) {
          return (
            <label key={field.name}>
              {label}
              {field.required ? ' *' : ''}
              <textarea
                {...common}
                value={val ?? ''}
                rows={3}
                maxLength={field.maxSize || undefined}
              />
            </label>
          )
        }

        if (field.type === 'date') {
          return (
            <label key={field.name}>
              {label}
              {field.required ? ' *' : ''}
              <input
                {...common}
                type="date"
                value={val ? String(val).slice(0, 10) : ''}
              />
            </label>
          )
        }

        if (field.type === 'ref') {
          const refName = field.name
          const isCatalogueRef =
            refName === 'catalogueItemId' ||
            refName === 'productId' ||
            refName === 'componentProductId' ||
            refName === 'componentItemId' ||
            field.refResource === 'CatalogueItem'
          const isCustomerRef =
            refName === 'customerId' || field.refResource === 'Customer'

          if (isCatalogueRef) {
            return (
              <label key={field.name}>
                {label}
                {isRequired ? ' *' : ''}
                <CatalogueItemSelect
                  id={field.name}
                  value={val ?? ''}
                  disabled={disabled}
                  forChannel="sale"
                  onChange={(id) => emitField(field.name, id ?? '')}
                />
                {isRequired && (
                  <input
                    tabIndex={-1}
                    aria-hidden="true"
                    required
                    value={val ? String(val) : ''}
                    onChange={() => {}}
                    style={{
                      opacity: 0,
                      height: 0,
                      width: 0,
                      position: 'absolute',
                      pointerEvents: 'none',
                    }}
                  />
                )}
              </label>
            )
          }

          if (isCustomerRef) {
            return (
              <label key={field.name}>
                {label}
                {isRequired ? ' *' : ''}
                <CustomerSelect
                  id={field.name}
                  value={val ?? ''}
                  disabled={disabled}
                  onChange={(id) => emitField(field.name, id ?? '')}
                />
                {isRequired && (
                  <input
                    tabIndex={-1}
                    aria-hidden="true"
                    required
                    value={val ? String(val) : ''}
                    onChange={() => {}}
                    style={{
                      opacity: 0,
                      height: 0,
                      width: 0,
                      position: 'absolute',
                      pointerEvents: 'none',
                    }}
                  />
                )}
              </label>
            )
          }

          return (
            <label key={field.name}>
              {label}
              {isRequired ? ' *' : ''}
              <input
                {...common}
                value={val ?? ''}
                placeholder={
                  field.refResource
                    ? `${field.refPackage || ''}/${field.refResource} id`
                    : 'id'
                }
                inputMode="numeric"
              />
              <span className="muted" style={{ fontSize: '0.75rem', fontWeight: 500 }}>
                {t('schemaForm.enterRelatedId')}
                {field.refResource ? ` (${field.refResource})` : ''}
              </span>
            </label>
          )
        }

        const inputMode =
          field.type === 'decimal' || field.type === 'integer' || field.type === 'long'
            ? 'decimal'
            : undefined

        return (
          <label key={field.name}>
            {label}
            {field.required ? ' *' : ''}
            <input
              {...common}
              type="text"
              value={val ?? ''}
              inputMode={inputMode}
              maxLength={field.maxSize || undefined}
              placeholder={field.defaultValue != null ? String(field.defaultValue) : undefined}
            />
          </label>
        )
      })}
    </>
  )
}

/**
 * Schema API often returns label === property name ("name", "itemKind").
 * Prefer i18n; otherwise Title Case via humanizeLabel — never raw camelCase.
 */
export function schemaFieldLabel(t, field) {
  const name = field?.name || ''
  const raw = field?.label != null ? String(field.label).trim() : ''
  const isRawProp =
    !raw ||
    raw === name ||
    raw.toLowerCase() === name.toLowerCase() ||
    /^[a-z][a-zA-Z0-9]*$/.test(raw)
  const fallback = isRawProp
    ? humanizeLabel(name)
    : /\s/.test(raw)
      ? raw
      : humanizeLabel(raw)
  return t([`fields.${name}`, `wb.${name}`, `domainForm.${name}`], {
    defaultValue: fallback,
  })
}

export function emptyFromSchema(schema) {
  const form = {}
  for (const f of schema?.fields || []) {
    if (f.type === 'boolean') {
      form[f.name] = f.defaultValue === true || f.defaultValue === 'true'
    } else if (f.defaultValue != null && f.defaultValue !== '') {
      form[f.name] = String(f.defaultValue)
    } else if (f.type === 'enum' && f.inList?.length) {
      form[f.name] = f.required ? f.inList[0] : ''
    } else {
      form[f.name] = ''
    }
  }
  if (form.name === undefined) form.name = ''
  if (form.description === undefined) form.description = ''
  return form
}

export function bodyFromSchemaForm(schema, form) {
  const body = {}
  for (const f of schema?.fields || []) {
    if (!Object.prototype.hasOwnProperty.call(form, f.name)) continue
    let v = form[f.name]
    if (f.type === 'boolean') {
      body[f.name] = !!v
      continue
    }
    if (v === '' || v == null) {
      if (f.required) body[f.name] = v
      continue
    }
    if (f.type === 'integer' || f.type === 'long') {
      const n = parseInt(String(v), 10)
      if (!Number.isNaN(n)) body[f.name] = n
      continue
    }
    if (f.type === 'decimal') {
      const n = Number(v)
      if (!Number.isNaN(n)) body[f.name] = n
      continue
    }
    if (f.type === 'ref') {
      const n = parseInt(String(v), 10)
      if (!Number.isNaN(n)) body[f.name] = n
      continue
    }
    body[f.name] = v
  }
  return body
}
