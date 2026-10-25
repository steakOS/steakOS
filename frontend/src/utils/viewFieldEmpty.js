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
 * Empty detection for read-only record views (Task 7646).
 * false and 0 are meaningful and must stay visible.
 */

export const SHOW_EMPTY_FIELDS_KEY = 'steakos.view.showEmptyFields'

export function isEmptyViewValue(value) {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim() === ''
  if (typeof value === 'boolean' || typeof value === 'number') return false
  if (Array.isArray(value)) return value.length === 0
  if (typeof value === 'object') return Object.keys(value).length === 0
  return false
}

/**
 * @param {Array<[string, unknown]>} entries
 * @param {{ showEmpty?: boolean }} opts
 */
export function filterViewEntries(entries, { showEmpty = false } = {}) {
  if (showEmpty || !Array.isArray(entries)) return entries || []
  return entries.filter(([, value]) => !isEmptyViewValue(value))
}

export function readShowEmptyFieldsPref() {
  try {
    if (typeof localStorage === 'undefined') return false
    return localStorage.getItem(SHOW_EMPTY_FIELDS_KEY) === '1'
  } catch {
    return false
  }
}

export function writeShowEmptyFieldsPref(on) {
  try {
    if (typeof localStorage === 'undefined') return
    localStorage.setItem(SHOW_EMPTY_FIELDS_KEY, on ? '1' : '0')
  } catch {
    /* ignore quota / private mode */
  }
}
