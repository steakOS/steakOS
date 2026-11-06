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
 * Human-readable descriptions for BODY ContentType catalog codes (D10 / CT1).
 * Used by BootStrap seed/upsert when a row has no description yet.
 */
class ContentTypeDescriptions {

    static final Map<String, String> BY_CODE = [
            Alignment     : 'How work or decisions line up with strategy, goals, or other records.',
            Assessment    : 'Evaluation of fitness, readiness, risk, or quality.',
            Audit         : 'Audit finding, evidence note, or audit trail commentary.',
            Baseline      : 'Agreed starting point for scope, cost, schedule, or configuration.',
            Communication : 'Message, meeting note, or correspondence summary.',
            Control       : 'Control design, operating note, or control evidence.',
            Document      : 'General document or specification note (body text; files use File).',
            Evaluation    : 'Scored or qualitative evaluation of a person, option, or outcome.',
            Feedback      : 'Feedback from a customer, teammate, or review cycle.',
            Governance    : 'Governance decision, committee note, or oversight remark.',
            Help          : 'In-product help or operator guidance attached to a record.',
            Issue         : 'Problem, defect, or blocker that needs attention.',
            Lifecycle     : 'Lifecycle stage note or transition rationale.',
            Metric        : 'Measure, KPI note, or metric interpretation.',
            Note          : 'Free-form note — default BODY type for any host.',
            Organization  : 'Org structure, role, or responsibility note.',
            Plan          : 'Plan, approach, or next-step outline.',
            Policy        : 'Policy statement, interpretation, or applicability note.',
            Relation      : 'How this record relates to another party or object.',
            Report        : 'Report summary or narrative excerpt.',
            Requirement   : 'Requirement, acceptance criterion, or obligation text.',
            Resource      : 'People, capacity, or resource allocation note.',
            Risk          : 'Risk statement, exposure, or risk treatment detail.',
            Schedule      : 'Schedule, milestone, or timing note.',
            Standard      : 'Standard, norm, or compliance reference note.',
            Strategy      : 'Strategic intent, option, or direction note.',
            Target        : 'Target, goal, or success criterion.',
            Tool          : 'Tool, system, or method usage note.',
            Training      : 'Training record, curriculum note, or learning outcome.',
            Transaction   : 'Commercial or financial transaction commentary.',
            Validation    : 'Validation, verification, or test result note.',
    ].asImmutable()

    static String forCode(String code) {
        if (!code) return null
        BY_CODE[code] ?: "Content type ${code}."
    }

    /** True when description is blank or still the old BootStrap placeholder. */
    static boolean needsDescription(String description, String code) {
        if (description == null || description.trim().isEmpty()) return true
        if (code && description.trim() == "System content type: ${code}") return true
        return description.trim().startsWith('System content type:')
    }
}
