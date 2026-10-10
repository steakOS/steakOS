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

package steakos.finance

/**
 * One double-entry line for {@link PostJournalCommand}.
 * Provide {@code accountCode} and/or {@code accountId}.
 * Exactly one of debitEur / creditEur should be positive (service enforces).
 */
class PostJournalLineCommand {

    String accountCode
    Long accountId

    BigDecimal debitEur = BigDecimal.ZERO
    BigDecimal creditEur = BigDecimal.ZERO

    /** Optional sats audit (FS2) */
    Long amountSats

    String memo
    String name

    static PostJournalLineCommand fromMap(Map m) {
        if (!m) {
            return null
        }
        new PostJournalLineCommand(
                accountCode: m.accountCode as String,
                accountId  : m.accountId != null && m.accountId != '' ? m.accountId as Long : null,
                debitEur   : toBd(m.debitEur),
                creditEur  : toBd(m.creditEur),
                amountSats : m.amountSats != null && m.amountSats != '' ? m.amountSats as Long : null,
                memo       : m.memo as String,
                name       : m.name as String
        )
    }

    private static BigDecimal toBd(Object v) {
        if (v == null || v == '') {
            return BigDecimal.ZERO
        }
        if (v instanceof BigDecimal) {
            return v
        }
        return new BigDecimal(v.toString())
    }
}
