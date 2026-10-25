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
import { PROCESS_STATUS_MAPS, resolveProcessCode } from '../config/processStatusMaps'
import { humanizeLabel } from '../utils/humanize'

/**
 * Inline desk process continuum (Nielsen WP-1). Highlights the step for the
 * selected record status. Uses process-green tokens — not status-pill orange.
 */
export default function ProcessStatusStrip({ processCode, activeIndex = -1, status }) {
  const { t } = useTranslation(['processes', 'common'])
  const canonical = resolveProcessCode(processCode)
  const map = PROCESS_STATUS_MAPS[processCode] || PROCESS_STATUS_MAPS[canonical]
  if (!map?.steps?.length) return null

  const title =
    t(map.titleKey, { ns: 'processes', defaultValue: null }) ||
    t(`labels.${canonical}`, { ns: 'processes', defaultValue: humanizeLabel(canonical) })

  return (
    <div className="process-status-strip" role="list" aria-label={title}>
      <div className="process-status-strip-title">
        <span className="badge badge-process">{title}</span>
        {status ? <span className="muted process-status-strip-status">{status}</span> : null}
      </div>
      <div className="process-step-strip process-step-strip-inline">
        {map.steps.map((step, i) => {
          const done = activeIndex >= 0 && i < activeIndex
          const active = activeIndex >= 0 && i === activeIndex
          const label =
            t(`strip.${canonical}.${step.id}`, {
              ns: 'processes',
              defaultValue: humanizeLabel(step.id),
            }) || humanizeLabel(step.id)
          return (
            <div
              key={step.id || i}
              role="listitem"
              className={[
                'process-step-node',
                done ? 'is-done' : '',
                active ? 'is-active' : '',
              ]
                .filter(Boolean)
                .join(' ')}
              aria-current={active ? 'step' : undefined}
            >
              <div className="process-step-no">
                {t('desk.processStep', {
                  ns: 'common',
                  defaultValue: 'Step {{n}}',
                  n: i + 1,
                })}
              </div>
              <div className="process-step-name">{label}</div>
              {i < map.steps.length - 1 ? (
                <span className="process-step-arrow" aria-hidden>
                  →
                </span>
              ) : null}
            </div>
          )
        })}
      </div>
    </div>
  )
}
