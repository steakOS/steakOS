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

package steakos.human

import grails.plugin.springsecurity.SpringSecurityService
import groovy.util.logging.Slf4j
import steakos.User

/**
 * Shared User create/link for HR (HR1): HR-created users start deactivated.
 * Named *Service so Grails registers and injects the bean.
 */
@Slf4j
class HrUserSupportService {

    SpringSecurityService springSecurityService

    /**
     * @return [user: User, created: boolean]
     */
    Map resolveOrCreateUser(Map fields) {
        Long userId = longOrNull(fields.userId)
        if (userId) {
            User u = User.get(userId)
            if (!u) throw new IllegalArgumentException("user not found: ${userId}")
            return [user: u, created: false]
        }
        String username = fields.username?.toString()?.trim()
        if (username) {
            User existing = User.findByUsername(username)
            if (existing) {
                return [user: existing, created: false]
            }
        } else {
            String base = (fields.name ?: fields.email ?: 'hr-user').toString()
                    .toLowerCase()
                    .replaceAll(/[^a-z0-9]+/, '.')
                    .replaceAll(/^\.|\.$/, '')
            if (!base) base = 'hr.user'
            username = base
            int n = 1
            while (User.findByUsername(username)) {
                username = "${base}.${n++}"
            }
        }
        String password = fields.password?.toString()?.trim() ?: randomPassword()
        boolean enabled = fields.containsKey('enabled') ? fields.enabled as Boolean : false
        User u = new User(
                username: username,
                password: password,
                enabled: enabled
        )
        u.springSecurityService = springSecurityService
        u.save(failOnError: true, flush: true)
        log.info('Created User {} enabled={} (HR)', username, enabled)
        return [user: u, created: true]
    }

    /** Meets {@link steakos.security.PasswordPolicy}. */
    private static String randomPassword() {
        // Upper + lower + digits + special; length > 12
        "Hr-${UUID.randomUUID().toString().replace('-', '').substring(0, 16)}!aA1"
    }

    private static Long longOrNull(Object v) {
        if (v == null || v == '') return null
        return v as Long
    }
}
