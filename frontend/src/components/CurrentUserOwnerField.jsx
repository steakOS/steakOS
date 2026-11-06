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

import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { meApi } from '../api/client'

/**
 * Read-only Owner bound to the signed-in user (displayName || username).
 * Auto-fills empty values once /api/me resolves — same “current user” habit as
 * TaskAssigneeSelect defaultToMe, without free-text entry.
 */
export default function CurrentUserOwnerField({
  value = '',
  onChange,
  disabled = false,
  id = 'owner-name',
  label,
  /** When true (default), write the current-user label into value if empty. */
  autoFill = true,
}) {
  const { t } = useTranslation('common')
  const [meLabel, setMeLabel] = useState('')
  const onChangeRef = useRef(onChange)
  onChangeRef.current = onChange
  const didFillRef = useRef(false)

  useEffect(() => {
    let cancelled = false
    meApi
      .get()
      .then((me) => {
        if (cancelled) return
        const labelText = String(me?.displayName || me?.username || '').trim()
        setMeLabel(labelText)
      })
      .catch(() => {
        if (!cancelled) setMeLabel('')
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!autoFill || didFillRef.current || disabled) return
    if ((value || '').trim()) {
      didFillRef.current = true
      return
    }
    if (!meLabel) return
    onChangeRef.current?.(meLabel)
    didFillRef.current = true
  }, [autoFill, disabled, value, meLabel])

  const shown = (value || '').trim() || meLabel

  return (
    <label htmlFor={id}>
      {label || t('fields.owner', { defaultValue: 'Owner' })}
      <input
        id={id}
        name="ownerName"
        type="text"
        className="mono"
        value={shown}
        readOnly
        disabled
        aria-readonly="true"
        title={t('fields.ownerCurrentUserHint', {
          defaultValue: 'Set automatically to the signed-in user.',
        })}
      />
    </label>
  )
}
