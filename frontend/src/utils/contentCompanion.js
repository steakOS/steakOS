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

/** Content meta resources — not content *hosts*. */
const CONTENT_META_RESOURCES = new Set([
  'ContentType',
  'ContentTypeConfig',
  'ObjectContent',
  'ContentBlob',
  'ObjectContentProfile',
])

/**
 * Whether this domain resource can host Object Companion content
 * (PendingContentComposer on create / ContentRail on view+edit).
 * Matches DomainForm companionEligible.
 */
export function isContentCompanionHost(pkg, resource) {
  if (!pkg || !resource) return false
  if (CONTENT_META_RESOURCES.has(resource)) return false
  return true
}
