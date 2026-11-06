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

import { Paperclip } from 'lucide-react'
import { useTranslation } from 'react-i18next'

/** True when companion summary has any LINK / FILE / BODY. */
export function hasCompanionContent(summary) {
  if (!summary) return false
  return Boolean(
    summary.hasLink ||
      summary.hasFile ||
      summary.hasBody ||
      summary.linkCount > 0 ||
      summary.fileCount > 0 ||
      summary.bodyCount > 0 ||
      summary.total > 0 ||
      summary.totalCount > 0,
  )
}

/**
 * List “Content” cell — paperclip pill when the host has Object Companion items.
 * Empty when none (column still reserved left of Actions).
 */
export default function ContentChannelIcons({ summary, className = '' }) {
  const { t } = useTranslation(['content', 'common'])
  if (!hasCompanionContent(summary)) {
    return <span className={`content-presence empty ${className}`} aria-hidden="true" />
  }
  const total =
    (summary.total != null
      ? Number(summary.total)
      : summary.totalCount != null
        ? Number(summary.totalCount)
        : (Number(summary.linkCount) || 0) +
          (Number(summary.fileCount) || 0) +
          (Number(summary.bodyCount) || 0)) || 1
  const label = t('icons.companion', {
    ns: 'content',
    defaultValue: 'Has content',
  })
  return (
    <span
      className={`content-presence-pill ${className}`}
      title={label}
      aria-label={label}
    >
      <Paperclip size={13} aria-hidden />
      {total > 1 ? <span className="content-presence-count">{total}</span> : null}
    </span>
  )
}
