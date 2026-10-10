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
import useNarrowViewport from '../hooks/useNarrowViewport'

/**
 * D3 sticky “New” control on narrow — jumps to create block (`#desk-create` by default).
 * Hidden on wide viewports.
 */
export default function DeskNewFab({ href = '#desk-create', label }) {
  const { t } = useTranslation('common')
  const narrow = useNarrowViewport()
  if (!narrow) return null
  const text = label || t('actions.new')
  return (
    <a className="desk-new-fab" href={href}>
      + {text}
    </a>
  )
}
