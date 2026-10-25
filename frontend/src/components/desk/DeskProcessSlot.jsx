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

import ProcessHowItWorks from '../ProcessHowItWorks'
import ProcessStatusStrip from '../ProcessStatusStrip'
import { activeStepIndex } from '../../config/processStatusMaps'

/**
 * Standard desk processSlot: How-it-works trigger + inline status continuum strip.
 */
export default function DeskProcessSlot({ processCode, status, show = true }) {
  if (!show || !processCode) return null
  const step = activeStepIndex(processCode, status)
  return (
    <div className="desk-process-slot">
      <div className="desk-process-slot-toolbar toolbar">
        <ProcessHowItWorks processCode={processCode} status={status} />
      </div>
      <ProcessStatusStrip processCode={processCode} activeIndex={step} status={status} />
    </div>
  )
}
