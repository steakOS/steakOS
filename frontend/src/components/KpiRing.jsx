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

import { useEffect, useRef } from 'react'
import * as d3 from 'd3'

/** Simple D3 donut ring for count metrics (F6). */
export default function KpiRing({ value = 0, max = 10, size = 56 }) {
  const ref = useRef(null)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    el.innerHTML = ''

    const v = Math.max(0, Number(value) || 0)
    const m = Math.max(max, v, 1)
    const radius = size / 2
    const thickness = 7

    const svg = d3
      .select(el)
      .append('svg')
      .attr('width', size)
      .attr('height', size)

    const g = svg.append('g').attr('transform', `translate(${radius},${radius})`)
    const arc = d3
      .arc()
      .innerRadius(radius - thickness)
      .outerRadius(radius)
      .startAngle(0)

    g.append('path')
      .datum({ endAngle: Math.PI * 2 })
      .attr('d', arc)
      .attr('fill', '#f5f5f4')

    g.append('path')
      .datum({ endAngle: (v / m) * Math.PI * 2 })
      .attr('d', arc)
      .attr('fill', '#e85d04')

    g.append('text')
      .attr('text-anchor', 'middle')
      .attr('dy', '0.35em')
      .attr('font-size', 14)
      .attr('font-weight', 800)
      .attr('fill', '#1a1a1a')
      .text(v)
  }, [value, max, size])

  return <div className="ring-wrap" ref={ref} />
}
