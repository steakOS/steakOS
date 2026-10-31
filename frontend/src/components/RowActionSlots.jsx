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

/**
 * Fixed-order row action slots: 1 view · 2 edit · 3 delete · 4+ others.
 * Missing actions leave an invisible same-size placeholder so icons stay
 * column-aligned across rows.
 */
export default function RowActionSlots({ view = null, edit = null, delete: del = null, others = null }) {
  return (
    <div className="row-action-slots" role="group">
      <span className="row-action-slot">{view || <span className="row-action-slot-ph" aria-hidden="true" />}</span>
      <span className="row-action-slot">{edit || <span className="row-action-slot-ph" aria-hidden="true" />}</span>
      <span className="row-action-slot">{del || <span className="row-action-slot-ph" aria-hidden="true" />}</span>
      {others != null && others !== false ? (
        <span className="row-action-slot row-action-slot-others">{others}</span>
      ) : null}
    </div>
  )
}
