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

package steakos.helper

import groovy.transform.CompileStatic

/**
 * Bind / emit optional {@code assigneeHumanNpub} + {@code assigneeAgentId} on domain instances.
 * When either side is set, the other is cleared (human XOR agent).
 */
@CompileStatic
final class AssignableSupport {

	private AssignableSupport() {}

	/** Apply from a request map. Keys may be absent (leave unchanged) or null/'' (clear). */
	static void apply(Object instance, Map fields) {
		if (instance == null || fields == null) return
		boolean hasHumanKey = fields.containsKey('assigneeHumanNpub')
		boolean hasAgentKey = fields.containsKey('assigneeAgentId')
		if (!hasHumanKey && !hasAgentKey) return

		String human = hasHumanKey ? blankToNull(fields.assigneeHumanNpub) : null
		Long agent = hasAgentKey ? toLongOrNull(fields.assigneeAgentId) : null

		if (hasHumanKey && hasAgentKey) {
			if (agent != null) {
				instance['assigneeAgentId'] = agent
				instance['assigneeHumanNpub'] = null
			} else if (human != null) {
				instance['assigneeHumanNpub'] = human
				instance['assigneeAgentId'] = null
			} else {
				instance['assigneeHumanNpub'] = null
				instance['assigneeAgentId'] = null
			}
			return
		}
		if (hasAgentKey) {
			instance['assigneeAgentId'] = agent
			if (agent != null) instance['assigneeHumanNpub'] = null
			return
		}
		instance['assigneeHumanNpub'] = human
		if (human != null) instance['assigneeAgentId'] = null
	}

	/** Values for create() constructors when fields may omit assignee. */
	static Map initialPair(Map fields) {
		if (fields == null) return [assigneeHumanNpub: null, assigneeAgentId: null]
		Long agent = toLongOrNull(fields.assigneeAgentId)
		if (agent != null) {
			return [assigneeHumanNpub: null, assigneeAgentId: agent]
		}
		return [assigneeHumanNpub: blankToNull(fields.assigneeHumanNpub), assigneeAgentId: null]
	}

	static Map toMapFragment(Object instance) {
		if (instance == null) return [:]
		[
				assigneeHumanNpub: instance.hasProperty('assigneeHumanNpub') ? instance['assigneeHumanNpub'] : null,
				assigneeAgentId  : instance.hasProperty('assigneeAgentId') ? instance['assigneeAgentId'] : null
		]
	}

	private static String blankToNull(Object v) {
		if (v == null) return null
		String s = v.toString().trim()
		s.isEmpty() ? null : s
	}

	private static Long toLongOrNull(Object v) {
		if (v == null || v == '') return null
		if (v instanceof Number) return ((Number) v).longValue()
		String s = v.toString().trim()
		if (s.isEmpty()) return null
		try {
			return Long.valueOf(s)
		} catch (NumberFormatException ignored) {
			return null
		}
	}
}
