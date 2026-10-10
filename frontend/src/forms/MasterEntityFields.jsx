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

import CurrencySelect from '../components/CurrencySelect'
import LocaleSelect from '../components/LocaleSelect'

/**
 * Renders fields from a master form spec.
 * @param {object} spec — from getMasterFormSpec
 * @param {object} form — current values
 * @param {(e: Event) => void | (name: string, value: any) => void} onChange
 *   Accepts either a DOM event (name/value/checked) or (name, value).
 * @param {boolean} [disabled]
 * @param {boolean} [showDescription=true]
 */
export default function MasterEntityFields({
  spec,
  form,
  onChange,
  disabled = false,
  showDescription = true,
}) {
  if (!spec?.fields) return null

  const handle = (e) => {
    if (typeof onChange === 'function' && e?.target) {
      onChange(e)
    }
  }

  const setField = (name, value) => {
    if (typeof onChange !== 'function') return
    // Synthetic event-compatible for desk setState((f)=>({...f,[name]:value}))
    onChange({ target: { name, value, type: 'text' } })
  }

  const fields = showDescription
    ? spec.fields
    : spec.fields.filter((f) => f.name !== 'description')

  // Pair layout: consecutive non-textarea/checkbox fields in a row when small
  const blocks = []
  let i = 0
  while (i < fields.length) {
    const f = fields[i]
    if (f.type === 'textarea' || f.type === 'checkbox' || f.fullWidth) {
      blocks.push([f])
      i += 1
    } else if (
      i + 1 < fields.length &&
      fields[i + 1].type !== 'textarea' &&
      fields[i + 1].type !== 'checkbox' &&
      !fields[i + 1].fullWidth
    ) {
      blocks.push([f, fields[i + 1]])
      i += 2
    } else {
      blocks.push([f])
      i += 1
    }
  }

  return (
    <>
      {blocks.map((pair, idx) => {
        if (pair.length === 2) {
          return (
            <div key={idx} className="form-row-2">
              {pair.map((field) => renderField(field, form, handle, setField, disabled))}
            </div>
          )
        }
        return renderField(pair[0], form, handle, setField, disabled)
      })}
    </>
  )
}

function renderField(field, form, handle, setField, disabled) {
  const val = form[field.name]
  const id = `mf-${field.name}`

  if (field.type === 'checkbox') {
    return (
      <label key={field.name} className="checkbox-label">
        <input
          id={id}
          name={field.name}
          type="checkbox"
          checked={!!val}
          disabled={disabled}
          onChange={(e) =>
            handle({
              target: {
                name: field.name,
                value: e.target.checked,
                type: 'checkbox',
                checked: e.target.checked,
              },
            })
          }
        />
        {field.label}
      </label>
    )
  }

  if (field.type === 'textarea') {
    return (
      <label key={field.name}>
        {field.label}
        <textarea
          id={id}
          name={field.name}
          value={val ?? ''}
          disabled={disabled}
          rows={3}
          onChange={handle}
          placeholder={field.placeholder}
        />
      </label>
    )
  }

  if (field.type === 'select') {
    return (
      <label key={field.name}>
        {field.label}
        {field.required ? ' *' : ''}
        <select
          id={id}
          name={field.name}
          value={val ?? ''}
          disabled={disabled}
          required={!!field.required}
          onChange={handle}
        >
          {(field.options || []).map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </label>
    )
  }

  if (field.type === 'currency') {
    return (
      <label key={field.name}>
        {field.label}
        {field.required ? ' *' : ''}
        <CurrencySelect
          id={id}
          name={field.name}
          value={val ?? ''}
          disabled={disabled}
          required={!!field.required}
          onChange={handle}
        />
      </label>
    )
  }

  if (field.type === 'locale') {
    return (
      <label key={field.name}>
        {field.label}
        {field.required ? ' *' : ''}
        <LocaleSelect
          id={id}
          name={field.name}
          value={val ?? ''}
          disabled={disabled}
          required={!!field.required}
          allowEmpty={!field.required}
          onChange={handle}
        />
      </label>
    )
  }

  return (
    <label key={field.name}>
      {field.label}
      {field.required ? ' *' : ''}
      <input
        id={id}
        name={field.name}
        type={field.type === 'email' ? 'email' : field.type === 'number' ? 'text' : 'text'}
        value={val ?? ''}
        disabled={disabled}
        required={!!field.required}
        inputMode={field.inputMode}
        placeholder={field.placeholder}
        onChange={handle}
      />
    </label>
  )
}
