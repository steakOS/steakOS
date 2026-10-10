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

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Company settings access (ADR-029). Merge: system template &lt; company.
 */
@Transactional
class CompanyConfigService {

    /** Install-wide settings (not tenant-scoped), e.g. security.adminTotpBypass. */
    static final String SYSTEM_NPUB = 'SYSTEM'

    /** When true, ROLE_ADMIN skips TOTP gate (testing only). */
    static final String KEY_ADMIN_TOTP_BYPASS = 'security.adminTotpBypass'

    boolean isAdminTotpBypassEnabled() {
        getBoolean(SYSTEM_NPUB, KEY_ADMIN_TOTP_BYPASS, false)
    }

    CompanySetting setAdminTotpBypass(boolean enabled) {
        put(SYSTEM_NPUB, KEY_ADMIN_TOTP_BYPASS, enabled ? 'true' : 'false', 'general', 'boolean')
    }

    String getString(String npubOwner, String key, String defaultValue = null) {
        CompanySetting row = find(npubOwner, key)
        row?.settingValue ?: defaultValue
    }

    Boolean getBoolean(String npubOwner, String key, Boolean defaultValue = false) {
        String v = getString(npubOwner, key, null)
        if (v == null) return defaultValue
        return v.equalsIgnoreCase('true') || v == '1'
    }

    Object getJson(String npubOwner, String key, Object defaultValue = null) {
        String v = getString(npubOwner, key, null)
        if (!v) return defaultValue
        try {
            return new JsonSlurper().parseText(v)
        } catch (Exception ignored) {
            return defaultValue
        }
    }

    CompanySetting put(String npubOwner, String key, String value,
                       String category = 'general', String dataType = 'string') {
        CompanySetting row = find(npubOwner, key)
        if (!row) {
            row = new CompanySetting(npubOwner: npubOwner, settingKey: key)
        }
        row.settingValue = value
        row.category = category
        row.dataType = dataType
        row.save(failOnError: true)
        row
    }

    CompanySetting putJson(String npubOwner, String key, Object value, String category = 'general') {
        put(npubOwner, key, JsonOutput.toJson(value), category, 'json')
    }

    List<String> enabledFamilies(String npubOwner) {
        Object raw = getJson(npubOwner, 'template.enabledFamilies', null)
        if (raw instanceof List) {
            return (raw as List).collect { it?.toString() }.findAll { it }
        }
        String tier = getString(npubOwner, 'template.tier', TemplateTiers.DEFAULT_OPERATOR_TIER)
        return TemplateTiers.defaultFamilies(tier)
    }

    private CompanySetting find(String npubOwner, String key) {
        CompanySetting.findByNpubOwnerAndSettingKey(npubOwner, key)
    }
}
