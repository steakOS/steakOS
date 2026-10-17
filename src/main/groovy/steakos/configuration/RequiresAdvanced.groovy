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

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Marks a controller or action as Sales/Finance Advanced (OI-SI05).
 * Bronze and Copper tenants must be refused; Silver/Gold/Iron-eval allowed.
 * Enforced by {@link AdvancedGateInterceptor} and/or {@link AdvancedGateService}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD])
@interface RequiresAdvanced {
    /** Optional family hint: sales | finance (documentation / future depth checks). */
    String family() default ''
}
