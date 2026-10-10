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

package steakos.generic

/**
 * Official ContentType codes for BODY channel (D10 + CT1).
 * Attachment removed — files use ObjectContent FILE channel.
 * Note added — universal free-form BODY type.
 */
class ContentTypeCodes {

    static final String NOTE = 'Note'

    static final List<String> ALL = [
            'Alignment',
            'Assessment',
            // Attachment removed (CT1) — FILE channel
            'Audit',
            'Baseline',
            'Communication',
            'Control',
            'Document',
            'Evaluation',
            'Feedback',
            'Governance',
            'Help', // Wave B — system help BODY type (OI-H01)
            'Issue',
            'Lifecycle',
            'Metric',
            'Note', // CT1 — universal
            'Organization',
            'Plan',
            'Policy',
            'Relation',
            'Report',
            'Requirement',
            'Resource',
            'Risk',
            'Schedule',
            'Standard',
            'Strategy',
            'Target',
            'Tool',
            'Training',
            'Transaction',
            'Validation'
    ].asImmutable()

    /** Codes removed from catalog; BootStrap deletes rows if present. */
    static final List<String> REMOVED = ['Attachment'].asImmutable()

    static boolean isValid(String code) {
        code != null && ALL.contains(code)
    }
}
