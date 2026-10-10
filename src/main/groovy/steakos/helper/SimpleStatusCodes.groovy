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

/**
 * System lifecycle statuses for Classifiable / workflow control (system-wide catalog).
 */
class SimpleStatusCodes {

    /**
     * Rows: code, name, description
     */
    static final List<Map<String, String>> ALL = [
            [
                    code       : 'NEW',
                    name       : 'New',
                    description: 'Default when the object is newly created.'
            ],
            [
                    code       : 'WORKING',
                    name       : 'Working',
                    description: 'Active, operational, or in progress.'
            ],
            [
                    code       : 'CHECKED',
                    name       : 'Checked',
                    description: 'Passed quality assurance or review.'
            ],
            [
                    code       : 'COMPLETED',
                    name       : 'Done',
                    description: 'Work is finished.'
            ],
            [
                    code       : 'CANCELLED',
                    name       : 'Cancelled',
                    description: 'Cancelled or no longer in use.'
            ],
    ].asImmutable()

    static boolean isValid(String code) {
        code != null && ALL.any { it.code == code }
    }
}
