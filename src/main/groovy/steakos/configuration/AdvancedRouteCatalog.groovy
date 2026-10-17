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

/**
 * Known Sales/Finance Advanced URI prefixes (Wave 2). Used by gate interceptor
 * before controllers exist; keep in sync with AppShell Advanced child routes (PR-9).
 */
final class AdvancedRouteCatalog {

    private AdvancedRouteCatalog() {}

    /** API + SPA path prefixes that are Advanced-only. */
    static final List<String> PATH_PREFIXES = [
            '/api/sales/pricelists',
            '/api/sales/crm',
            '/api/sales/leads',
            '/api/sales/opportunities',
            '/api/finance/collections',
            '/api/finance/match',
            '/sales/pricing',
            '/sales/crm',
            '/finance/collections',
            '/finance/match'
    ].asImmutable()

    static boolean isAdvancedPath(String path) {
        if (!path) return false
        String p = path
        // strip query
        int q = p.indexOf('?')
        if (q >= 0) p = p.substring(0, q)
        PATH_PREFIXES.any { String prefix ->
            p == prefix || p.startsWith(prefix + '/')
        }
    }
}
