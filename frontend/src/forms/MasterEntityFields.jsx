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
import CustomerSelect from '../components/CustomerSelect'
import CurrentUserOwnerField from '../components/CurrentUserOwnerField'
import TaskAssigneeSelect from '../components/TaskAssigneeSelect'

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

  const setFields = (patch) => {
    if (typeof onChange !== 'function' || !patch) return
    Object.entries(patch).forEach(([name, value]) => {
      onChange({ target: { name, value, type: 'text' } })
    })
  }

  const fields = showDescription
    ? spec.fields
    : spec.fields.filter((f) => f.name !== 'description')

  const isFullWidth = (f) =>
    f.type === 'textarea' ||
    f.type === 'checkbox' ||
    f.type === 'assignee' ||
    !!f.fullWidth

  // Pair layout: consecutive non-textarea/checkbox/assignee fields in a row when small
  const blocks = []
  let i = 0
  while (i < fields.length) {
    const f = fields[i]
    if (isFullWidth(f)) {
      blocks.push([f])
      i += 1
    } else if (i + 1 < fields.length && !isFullWidth(fields[i + 1])) {
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
              {pair.map((field) =>
                renderField(field, form, handle, setField, setFields, disabled),
              )}
            </div>
          )
        }
        return renderField(pair[0], form, handle, setField, setFields, disabled)
      })}
    </>
  )
}

function renderField(field, form, handle, setField, setFields, disabled) {
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

  if (field.type === 'customer') {
    return (
      <label key={field.name}>
        {field.label}
        {field.required ? ' *' : ''}
        <CustomerSelect
          id={id}
          value={val ?? ''}
          disabled={disabled}
          onChange={(customerId) => setField(field.name, customerId || '')}
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

  if (field.type === 'currentUser') {
    return (
      <CurrentUserOwnerField
        key={field.name}
        id={id}
        label={`${field.label || 'Owner'}${field.required ? ' *' : ''}`}
        value={val ?? ''}
        disabled={disabled}
        onChange={(next) => setField(field.name, next)}
      />
    )
  }

  if (field.type === 'assignee') {
    return (
      <TaskAssigneeSelect
        key={field.name}
        id={id}
        label={field.label || undefined}
        assigneeHumanNpub={form.assigneeHumanNpub || ''}
        assigneeAgentId={form.assigneeAgentId || ''}
        defaultToMe={false}
        disabled={disabled}
        onChange={({ assigneeHumanNpub, assigneeAgentId }) => {
          setFields({
            assigneeHumanNpub: assigneeHumanNpub || '',
            assigneeAgentId: assigneeAgentId || '',
          })
        }}
      />
    )
  }

  return (
    <label key={field.name}>
      {field.label}
      {field.required ? ' *' : ''}
      <input
        id={id}
        name={field.name}
        type={
          field.type === 'email'
            ? 'email'
            : field.type === 'date'
              ? 'date'
              : field.type === 'number'
                ? 'text'
                : 'text'
        }
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
