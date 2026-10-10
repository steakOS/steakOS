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

/**
 * IR-08 — honest label for incomplete / stub features on Iron.
 * @param {string} [label] short badge text
 * @param {string} [hint] title tooltip
 */
export default function StubBadge({
  label,
  hint,
  className = '',
}) {
  const { t } = useTranslation('common')
  return (
    <span
      className={`badge badge-muted stub-badge ${className}`.trim()}
      title={
        hint ||
        t('stub.hint', {
          defaultValue: 'Kernel / stub — not fully implemented on Iron alpha',
        })
      }
      style={{ fontWeight: 600, marginLeft: 6, verticalAlign: 'middle' }}
    >
      {label || t('stub.label', { defaultValue: 'Stub' })}
    </span>
  )
}
