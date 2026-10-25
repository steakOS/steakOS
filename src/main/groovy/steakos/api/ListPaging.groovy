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

package steakos.api

/**
 * Shared list pagination contract (LIST-PAGINATION-CONCEPT).
 * Pipeline: tenant → filter → sort → count/total → offset/max → items.
 */
class ListPaging {

    static final int DEFAULT_MAX = 25
    static final int HARD_MAX = 100
    /** Legacy dump default until a client sends max (pickers / unmigrated UIs). */
    static final int LEGACY_DEFAULT_MAX = 200

    /**
     * Parse max/offset from request params.
     * @param defaultMax used when max is omitted
     */
    static Map pageParams(def params, int defaultMax = DEFAULT_MAX, int hardMax = HARD_MAX) {
        Integer maxRaw = readInt(params, 'max')
        int max = Math.min(hardMax, Math.max(1, maxRaw != null ? maxRaw : defaultMax))

        Integer offsetRaw = readInt(params, 'offset')
        int offset = Math.max(0, offsetRaw != null ? offsetRaw : 0)

        if (offsetRaw == null) {
            Integer pageRaw = readInt(params, 'page')
            if (pageRaw != null) {
                int page = Math.max(1, pageRaw)
                offset = (page - 1) * max
            }
        }

        [max: max, offset: offset]
    }

    /**
     * Allowlisted sort; unknown keys fall back to defaults (legacy soft path).
     */
    static Map sortParams(def params, String defaultSort, String defaultOrder, Collection allowedSorts) {
        Set allowed = (allowedSorts ?: []) as Set
        String sortIn = params?.sort?.toString()?.trim()
        String sort = (sortIn && sortIn in allowed) ? sortIn : defaultSort
        String orderRaw = params?.order?.toString()?.toLowerCase()
        String order = orderRaw in ['asc', 'desc'] ? orderRaw : defaultOrder
        [sort: sort, order: order]
    }

    /**
     * Allowlisted sort (Task 7640 D1): blank → defaults; unknown → IllegalArgumentException.
     */
    static Map requireSortParams(def params, String defaultSort, String defaultOrder, Collection allowedSorts) {
        Set allowed = (allowedSorts ?: []) as Set
        String sortIn = params?.sort?.toString()?.trim()
        String sort
        if (!sortIn) {
            sort = defaultSort
        } else if (sortIn in allowed) {
            sort = sortIn
        } else {
            throw new IllegalArgumentException("Unknown sort field: ${sortIn}")
        }
        String orderRaw = params?.order?.toString()?.trim()?.toLowerCase()
        String order
        if (!orderRaw) {
            order = defaultOrder
        } else if (orderRaw in ['asc', 'desc']) {
            order = orderRaw
        } else {
            throw new IllegalArgumentException("Invalid order: ${orderRaw} (use asc or desc)")
        }
        [sort: sort, order: order]
    }

    /**
     * Neutralize LIKE wildcards in free-text {@code q} and wrap as contains pattern.
     * Returns null when empty after sanitize.
     */
    static String likeContains(String q) {
        if (q == null) return null
        String safe = q.toString().trim()
                .replace('\\', '')
                .replace('%', '')
                .replace('_', '')
                .trim()
        if (!safe) return null
        "%${safe}%"
    }

    static Map envelope(List items, int total, int max, int offset, Map extra = [:]) {
        Map body = [
                items : items ?: [],
                total : total,
                max   : max,
                offset: offset
        ]
        if (extra) {
            body.putAll(extra)
        }
        body
    }

    private static Integer readInt(def params, String key) {
        if (params == null) return null
        try {
            if (params.respondsTo('int', String)) {
                Integer v = params.int(key)
                if (v != null) return v
            }
        } catch (ignored) {
        }
        def raw = params[key]
        if (raw == null || raw.toString().trim() == '') return null
        try {
            return Integer.parseInt(raw.toString().trim())
        } catch (NumberFormatException ignored) {
            return null
        }
    }
}
