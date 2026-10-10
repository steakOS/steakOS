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

import { Link2, Paperclip, FileText } from 'lucide-react'
import { useTranslation } from 'react-i18next'

/**
 * Compact icons for desk list rows when companion content exists.
 * @param {{ hasLink?: boolean, hasFile?: boolean, hasBody?: boolean, linkCount?: number, fileCount?: number, bodyCount?: number }} summary
 */
export default function ContentChannelIcons({ summary, className = '' }) {
  const { t } = useTranslation('content')
  if (!summary) return <span className={`content-channel-icons empty ${className}`}>—</span>
  const hasLink = summary.hasLink || summary.linkCount > 0
  const hasFile = summary.hasFile || summary.fileCount > 0
  const hasBody = summary.hasBody || summary.bodyCount > 0
  if (!hasLink && !hasFile && !hasBody) {
    return <span className={`content-channel-icons empty ${className}`}>—</span>
  }
  return (
    <span className={`content-channel-icons ${className}`} title={t('icons.companion')}>
      {hasLink && (
        <span className="content-ch-icon" title={t('icons.links', { count: summary.linkCount || 1 })}>
          <Link2 size={13} />
        </span>
      )}
      {hasFile && (
        <span className="content-ch-icon" title={t('icons.files', { count: summary.fileCount || 1 })}>
          <Paperclip size={13} />
        </span>
      )}
      {hasBody && (
        <span className="content-ch-icon" title={t('icons.notes', { count: summary.bodyCount || 1 })}>
          <FileText size={13} />
        </span>
      )}
    </span>
  )
}
