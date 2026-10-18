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

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * JSON / form date parsing without Groovy {@code Date.parse(fmt, str)}.
 * Groovy 4 moved that extension to {@code groovy-dateutil}; missing module
 * made domain CRUD silently drop {@code yyyy-MM-dd} fields (tasks, invoices, …).
 */
final class DateParse {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ISO_LOCAL_DATE
    private static final DateTimeFormatter YMD_HMS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private static final DateTimeFormatter YMD_HMS_SPACE = DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss')

    private DateParse() {}

    /** Parse JSON/body date to {@link Date}, or null if blank / unparseable. */
    static Date toDate(Object raw) {
        if (raw == null) {
            return null
        }
        if (raw instanceof Date) {
            return (Date) raw
        }
        if (raw instanceof Number) {
            return new Date(((Number) raw).longValue())
        }
        String s = raw.toString().trim()
        if (!s) {
            return null
        }
        if (s.isLong()) {
            try {
                return new Date(Long.parseLong(s))
            } catch (NumberFormatException ignored) {
                return null
            }
        }
        // yyyy-MM-dd or ISO-8601 with time / Z
        try {
            if (s.length() == 10 && s.charAt(4) == '-' as char) {
                LocalDate ld = LocalDate.parse(s, YMD)
                return Date.from(ld.atStartOfDay(ZoneOffset.UTC).toInstant())
            }
            if (s.length() >= 19 && s.charAt(10) == 'T') {
                String head = s.substring(0, 19)
                try {
                    LocalDateTime ldt = LocalDateTime.parse(head, YMD_HMS)
                    return Date.from(ldt.toInstant(ZoneOffset.UTC))
                } catch (DateTimeParseException ignored) {
                }
                // full Instant: 2026-10-01T12:00:00Z / with offset
                try {
                    return Date.from(Instant.parse(s.endsWith('Z') || s.contains('+') || s.lastIndexOf('-') > 10
                            ? s
                            : s + 'Z'))
                } catch (DateTimeParseException ignored) {
                }
            }
            if (s.length() >= 19 && s.charAt(10) == ' ') {
                LocalDateTime ldt = LocalDateTime.parse(s.substring(0, 19), YMD_HMS_SPACE)
                return Date.from(ldt.toInstant(ZoneOffset.UTC))
            }
            if (s.length() >= 10 && s.charAt(4) == '-' as char) {
                LocalDate ld = LocalDate.parse(s.substring(0, 10), YMD)
                return Date.from(ld.atStartOfDay(ZoneOffset.UTC).toInstant())
            }
        } catch (DateTimeParseException ignored) {
            return null
        }
        return null
    }
}
