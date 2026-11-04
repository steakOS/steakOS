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

package steakos.configuration

import grails.core.GrailsApplication
import groovy.util.logging.Slf4j

/**
 * Global lab/dev harness flag ({@code steakos.developmentMode} /
 * {@code STEAKOS_DEVELOPMENT_MODE}). Default {@code false} for installs;
 * enables Coder agent + AI:* task workflow when true.
 */
@Slf4j
class DevelopmentModeService {

    static final String CODER_AGENT_KEY = 'coder'

    /** Task.status values used by the Coder / Hermes / Grok Build pickup loop. */
    static final List<String> AI_TASK_STATUSES = [
            'AI:new',
            'AI:conceptualizing',
            'AI:analyzing',
            'AI:fixing',
            'AI:waiting-go',
            'AI:done',
    ].asImmutable()

    /** Statuses that still count as open work (My Work open filter). */
    static final List<String> AI_OPEN_STATUSES = [
            'AI:new',
            'AI:conceptualizing',
            'AI:analyzing',
            'AI:fixing',
            'AI:waiting-go',
    ].asImmutable()

    /** Human-only Task.status values (operators confirm completion with DONE). */
    static final List<String> HUMAN_TASK_STATUSES = [
            'OPEN',
            'IN_PROGRESS',
            'DONE',
            'CANCELLED',
    ].asImmutable()

    static final String AGENT_TASK_STATUS_CODE = 'AGENT_TASK_STATUS_FORBIDDEN'

    GrailsApplication grailsApplication

    boolean isEnabled() {
        try {
            def v = grailsApplication?.config?.getProperty('steakos.developmentMode', Boolean, Boolean.FALSE)
            return v == Boolean.TRUE
        } catch (ignored) {
            return false
        }
    }

    static boolean isAiTaskStatus(String status) {
        status != null && AI_TASK_STATUSES.contains(status.toString().trim())
    }

    static boolean isHumanTaskStatus(String status) {
        status != null && HUMAN_TASK_STATUSES.contains(status.toString().trim())
    }

    /**
     * Agent-only principals may set Task.status to an AI:* value only.
     * DONE / CANCELLED / OPEN / IN_PROGRESS are reserved for humans.
     * @return normalized AI:* status, or null when status is blank (leave unchanged on update)
     */
    static String requireAgentAiTaskStatus(Object status, boolean required) {
        String raw = status?.toString()?.trim()
        if (!raw) {
            if (required) {
                throw new SecurityException(
                        "${AGENT_TASK_STATUS_CODE}: agents must set an AI:* Task status (never DONE)")
            }
            return null
        }
        if (isAiTaskStatus(raw)) {
            return raw
        }
        throw new SecurityException(
                "${AGENT_TASK_STATUS_CODE}: agents may only set AI:* Task statuses " +
                        "(got '${raw}'); DONE is reserved for humans")
    }
}
