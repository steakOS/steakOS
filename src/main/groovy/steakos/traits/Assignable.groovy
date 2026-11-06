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

package steakos.traits

import groovy.transform.CompileStatic

/**
 * Optional work assignee — same pair as {@code steakos.core.Task}.
 * Human ({@code assigneeHumanNpub}) and agent ({@code assigneeAgentId}) are mutually exclusive;
 * both null means unassigned.
 */
@CompileStatic
trait Assignable {

	/** Human assignee (npub or {@code user:username} convention). */
	String assigneeHumanNpub

	/** Optional agent id (loose Long → agents.Agent). */
	Long assigneeAgentId

	boolean hasHumanAssignee() {
		assigneeHumanNpub != null && !assigneeHumanNpub.trim().isEmpty()
	}

	boolean hasAgentAssignee() {
		assigneeAgentId != null
	}

	void clearAssignee() {
		assigneeHumanNpub = null
		assigneeAgentId = null
	}
}
