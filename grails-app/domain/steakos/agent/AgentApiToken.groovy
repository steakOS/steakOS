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

package steakos.agent

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import steakos.User

/**
 * Opaque bearer token for Hermes agents (ADR-037).
 * Plaintext secret is shown once at mint; only {@link #tokenHash} is stored.
 */
@GrailsCompileStatic
@EqualsAndHashCode(includes = 'tokenPrefix')
@ToString(includes = 'tokenPrefix,label,revoked', includeNames = true, includePackage = false)
class AgentApiToken implements Serializable {

    private static final long serialVersionUID = 1L

    /** First 8 hex chars of secret for list UI (not secret) */
    String tokenPrefix
    /** SHA-256 hex of full secret */
    String tokenHash
    /** Human label e.g. "olympus hiveA bookkeeper" */
    String label
    /** Agent principal */
    User user
    /**
     * Tenant key the agent acts within (npub or fallback like user:admin).
     * Linked-tenant model (ADR-037 D2).
     */
    String linkedTenantKey
    /** Optional source host label e.g. olympus */
    String hostLabel
    Boolean revoked = Boolean.FALSE
    Date dateCreated
    Date lastUsed
    String createdBy

    static constraints = {
        tokenPrefix nullable: false, blank: false, maxSize: 16
        tokenHash nullable: false, blank: false, maxSize: 128, unique: true
        label nullable: false, blank: false, maxSize: 255
        user nullable: false
        linkedTenantKey nullable: false, blank: false, maxSize: 255
        hostLabel nullable: true, blank: true, maxSize: 128
        revoked nullable: false
        dateCreated nullable: true
        lastUsed nullable: true
        createdBy nullable: true, maxSize: 128
    }

    static mapping = {
        table 'agent_api_token'
        tokenHash index: 'agent_api_token_hash_idx'
        user index: 'agent_api_token_user_idx'
        autoTimestamp true
    }
}
