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

import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Compact 3×3 Eisenhower heat for CEO dashboard.
 * Rows = urgency 3→1 (top most urgent); cols = importance 1→3 (right most important).
 * Hover a quadrant → urgency + importance labels (e.g. "Urgency: high, importance: medium").
 */
export default function EisenhowerMini({ cells = [], size = 132 }) {
  const { t } = useTranslation('common')
  const uid = useId().replace(/:/g, '')
  const [hover, setHover] = useState(null) // { u, i, n, x, y } in SVG coords

  const map = {}
  for (const c of cells) {
    const u = Number(c.urgency) || 2
    const i = Number(c.importance) || 2
    map[`${u}-${i}`] = Number(c.count) || 0
  }
  const max = Math.max(1, ...Object.values(map), 1)

  const cell = size / 3
  const pad = 3
  const r = Math.max(5, cell * 0.28)

  const rows = [3, 2, 1]
  const cols = [1, 2, 3]

  const levelLabel = (n) =>
    t(`pages.work.level.${n}`, {
      defaultValue: n === 3 ? 'high' : n === 1 ? 'low' : 'medium',
    })

  const tipText = (u, i) =>
    t('pages.work.eisenhowerCellTip', {
      urgency: levelLabel(u),
      importance: levelLabel(i),
      defaultValue: `Urgency: ${levelLabel(u)}, importance: ${levelLabel(i)}`,
    })

  const hotId = `em-hot-${uid}`
  const softId = `em-soft-${uid}`

  return (
    <div className="eisenhower-mini-wrap">
      <svg
        className="eisenhower-mini"
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        role="img"
        aria-label={t('pages.work.eisenhower', { defaultValue: 'Action - NOW' })}
      >
        <defs>
          <linearGradient id={hotId} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#f48c06" />
            <stop offset="100%" stopColor="#e85d04" />
          </linearGradient>
          <filter id={softId} x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="0.5" stdDeviation="0.8" floodOpacity="0.12" />
          </filter>
        </defs>

        <rect
          x={0.5}
          y={0.5}
          width={size - 1}
          height={size - 1}
          rx={10}
          fill="#fafaf9"
          stroke="#e7e5e4"
          strokeWidth={1}
        />

        {rows.map((u, ri) =>
          cols.map((i, ci) => {
            const n = map[`${u}-${i}`] || 0
            const cx = ci * cell + cell / 2
            const cy = ri * cell + cell / 2
            const hot = u === 3 && i === 3
            const empty = n === 0
            const intensity = empty ? 0 : 0.22 + 0.78 * Math.min(1, n / max)
            const fill = empty
              ? '#f5f5f4'
              : hot
                ? `url(#${hotId})`
                : `rgba(232, 93, 4, ${intensity.toFixed(3)})`
            const stroke = empty ? '#e7e5e4' : hot ? '#c2410c' : '#ea580c'
            const dots = empty ? 0 : Math.min(n, 5)
            const isHover = hover && hover.u === u && hover.i === i
            const label = tipText(u, i)

            return (
              <g
                key={`${u}-${i}`}
                filter={empty ? undefined : `url(#${softId})`}
                className="eisenhower-mini-cell"
                onMouseEnter={() => setHover({ u, i, n, cx, cy })}
                onMouseLeave={() => setHover(null)}
                onFocus={() => setHover({ u, i, n, cx, cy })}
                onBlur={() => setHover(null)}
                tabIndex={0}
                role="group"
                aria-label={`${label}${n ? ` — ${n}` : ''}`}
              >
                {/* No SVG <title> — native browser tip stacks on the custom .eisenhower-mini-tip (Task 7673). */}

                <rect
                  x={ci * cell + pad}
                  y={ri * cell + pad}
                  width={cell - pad * 2}
                  height={cell - pad * 2}
                  rx={7}
                  fill={isHover ? '#fff7ed' : '#fff'}
                  stroke={isHover ? '#fdba74' : '#f0efee'}
                  strokeWidth={isHover ? 1.5 : 1}
                />
                <circle cx={cx} cy={cy} r={r + 2.5} fill="none" stroke="#f5f5f4" strokeWidth={3.5} />
                <circle
                  cx={cx}
                  cy={cy}
                  r={r}
                  fill={fill}
                  stroke={stroke}
                  strokeWidth={empty ? 1 : 0}
                  opacity={empty ? 1 : 0.95}
                />
                {!empty && (
                  <text
                    x={cx}
                    y={cy}
                    textAnchor="middle"
                    dominantBaseline="central"
                    fontSize={11}
                    fontWeight={800}
                    fill={intensity > 0.55 || hot ? '#fff' : '#1a1a1a'}
                    style={{ pointerEvents: 'none' }}
                  >
                    {n}
                  </text>
                )}
                {dots > 0 && dots <= 3 && (
                  <g style={{ pointerEvents: 'none' }}>
                    {Array.from({ length: dots }).map((_, d) => {
                      const dx = cx + (d - (dots - 1) / 2) * 5
                      return (
                        <circle
                          key={d}
                          cx={dx}
                          cy={cy + r + 6}
                          r={1.4}
                          fill={hot ? '#e85d04' : '#fb923c'}
                          opacity={0.85}
                        />
                      )
                    })}
                  </g>
                )}
                {/* Full-cell hit target so hover is easy on small grid */}
                <rect
                  x={ci * cell}
                  y={ri * cell}
                  width={cell}
                  height={cell}
                  fill="transparent"
                  className="eisenhower-mini-hit"
                />
              </g>
            )
          }),
        )}

        <text
          x={size - 6}
          y={10}
          textAnchor="end"
          fontSize={7}
          fill="#a8a29e"
          fontWeight={700}
          style={{ pointerEvents: 'none' }}
        >
          I→
        </text>
        <text
          x={8}
          y={size - 5}
          textAnchor="start"
          fontSize={7}
          fill="#a8a29e"
          fontWeight={700}
          style={{ pointerEvents: 'none' }}
        >
          U↑
        </text>
      </svg>

      {hover && (
        <div className="eisenhower-mini-tip" role="tooltip">
          {tipText(hover.u, hover.i)}
          {hover.n > 0 ? (
            <span className="eisenhower-mini-tip-count">
              {' · '}
              {t('pages.work.eisenhowerCellCount', {
                count: hover.n,
                defaultValue: `${hover.n} open`,
              })}
            </span>
          ) : null}
        </div>
      )}
    </div>
  )
}
