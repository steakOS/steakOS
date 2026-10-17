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
 * Frontend mirror of AdvancedMetalPolicy / GrrcMetalPolicy / StakeholderMetalPolicy.
 * Show Sales/Finance Advanced + GRRC/Stakeholder desks when metal ≥ SILVER or Iron eval.
 * Bronze/Copper hide Advanced child routes (family pack already hides grrc/stakeholder).
 */
const ADVANCED_ALLOWED = new Set(['IRON', 'SILVER', 'GOLD'])

export function normalizeMetalTier(tier) {
  if (!tier) return 'BRONZE'
  return String(tier).trim().toUpperCase() || 'BRONZE'
}

/** True when Advanced SPA routes and GRRC/Stakeholder desks may appear in nav. */
export function allowsSilverSurfaces(tier) {
  return ADVANCED_ALLOWED.has(normalizeMetalTier(tier))
}
