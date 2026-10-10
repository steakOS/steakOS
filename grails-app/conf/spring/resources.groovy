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

// Place your Spring DSL code here
import steakos.security.AgentBearerTokenFilter
import steakos.security.ReadOnlyAccessFilter
import steakos.security.MatrixAccessFilter
import steakos.security.TotpGateFilter
import steakos.security.NostrAuthService
import steakos.security.TotpService

beans = {
    // ADR-037 — registered into Spring Security chain from BootStrap via clientRegisterFilter
    agentBearerTokenFilter(AgentBearerTokenFilter) {
        agentTokenService = ref('agentTokenService')
        userDetailsService = ref('userDetailsService')
    }
    // Demo / visitor accounts — block writes after authentication
    readOnlyAccessFilter(ReadOnlyAccessFilter)

    // PDR-025 PR3 — role matrix write enforcement
    matrixAccessFilter(MatrixAccessFilter) {
        roleProfileService = ref('roleProfileService')
        authAccountService = ref('authAccountService')
        companyConfigService = ref('companyConfigService')
    }

    totpService(TotpService)
    nostrAuthService(NostrAuthService)
    // AUTH01 — after form login, require authenticator when enabled
    totpGateFilter(TotpGateFilter) {
        authAccountService = ref('authAccountService')
        companyConfigService = ref('companyConfigService')
    }
}
